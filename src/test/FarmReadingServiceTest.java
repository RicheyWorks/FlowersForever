package com.flowerfarm.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FarmReadingServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private JdbcTemplate jdbc;
    private FarmReadingService service;
    private TransactionTemplate transaction;

    @BeforeEach void setup() {
        connect("jdbc:h2:mem:readings-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", true);
    }

    @AfterEach void close() { jdbc.execute("SHUTDOWN"); }

    private void connect(String url, boolean migrate) {
        DriverManagerDataSource source = new DriverManagerDataSource(url, "sa", "");
        if (migrate) new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/h2/V50__farm_readings.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        service = new FarmReadingService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    private FarmReadingService.IntakeResult ingest(String source, List<?> rows) {
        return transaction.execute(status -> service.ingest(source, rows));
    }

    private static Map<String, Object> row(String key, Object value) {
        return new HashMap<>(Map.of("readingKey", key, "deviceId", "greenhouse-1",
                "metric", "air.temperature", "unit", "C", "value", value,
                "observedAt", "2026-10-10T04:00:00.123456789-07:00",
                "location", "North house", "rawFrame", "{\"temp_c\":21.125}\r\n"));
    }

    @Test void preservesExactUnitsRawDataAndBothClocksThroughReplayAndNewService() {
        Map<String, Object> first = row("sample-1", new BigDecimal("21.125"));
        assertThat(ingest("ble-bridge", List.of(first)).inserted()).isEqualTo(1);
        first.put("value", new BigDecimal("21.125000"));
        FarmReadingService second = new FarmReadingService(jdbc, Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));
        assertThat(transaction.execute(status -> second.ingest("ble-bridge", List.of(first))).replayed()).isEqualTo(1);
        FarmReading saved = second.history("ble-bridge", 0, 100).readings().get(0);
        assertThat(saved.value()).isEqualByComparingTo("21.125");
        assertThat(saved.unit()).isEqualTo("C");
        assertThat(saved.receivedAt()).isEqualTo(NOW);
        assertThat(saved.observedAt()).isEqualTo(Instant.parse("2026-10-10T11:00:00.123456789Z"));
        assertThat(saved.rawFrame()).isEqualTo("{\"temp_c\":21.125}\r\n");
        assertThat(second.count()).isEqualTo(1);
    }

    @Test void changedRetryRollsBackTheEntireBatchAndSourcesStayIndependent() {
        ingest("source-a", List.of(row("same-key", 21)));
        assertThatThrownBy(() -> ingest("source-a", List.of(row("new-key", 22), row("same-key", 23))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different content");
        assertThat(service.count()).isEqualTo(1);
        assertThat(ingest("source-b", List.of(row("same-key", 23))).inserted()).isEqualTo(1);
        assertThat(service.history("source-a", 0, 100).readings()).hasSize(1);
    }

    @Test void desktopHistoryPagesAcrossSourcesAndDoesNotShiftWhenNewReceiptsArrive() {
        ingest("a", List.of(row("first", 21), row("second", 22)));
        ingest("b", List.of(row("third", 23)));
        var first = service.recent(0, 2);
        assertThat(first.readings()).extracting(FarmReading::readingKey).containsExactly("third", "second");
        assertThat(first.hasMore()).isTrue();
        ingest("c", List.of(row("new-receipt", 24)));
        var older = service.recent(first.nextAfterId(), 2);
        assertThat(older.readings()).extracting(FarmReading::readingKey).containsExactly("first");
        assertThat(older.hasMore()).isFalse();
        assertThat(service.recent(0, 1).readings().get(0).readingKey()).isEqualTo("new-receipt");
        assertThatThrownBy(() -> service.recent(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recent(0, 201)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void draftReviewUsesIntakeValidationWithoutSavingAnything() {
        service.validate("manual", row("draft", 21));
        assertThat(service.count()).isZero();
        assertThatThrownBy(() -> service.validate("manual", row("draft", "21")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate("bad source", row("draft", 21)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.count()).isZero();
    }

    @Test void latestReportedUsesObservationTimeAndNanosecondsRatherThanUploadOrderOrTextSort() {
        var precise = row("newer-nanosecond", 22);
        precise.put("observedAt", "2026-10-10T11:59:00.000000001Z");
        var delayed = row("late-upload", 19);
        delayed.put("observedAt", "2026-10-10T11:59:00Z");
        ingest("logger", List.of(precise, delayed));
        assertThat(service.recent(0, 10).readings().get(0).readingKey()).isEqualTo("late-upload");
        var latest = service.latestReported(0, 10);
        assertThat(latest.readings()).extracting(FarmReading::readingKey).containsExactly("newer-nanosecond");
        assertThat(latest.readings().get(0).observedAt()).isEqualTo(Instant.parse("2026-10-10T11:59:00.000000001Z"));
    }

    @Test void latestReportedNeverHidesNewerMissingErrorOrSuspectObservationsBehindGoodValues() {
        ingest("logger", List.of(row("good", 21)));
        var missing = row("missing", 0);
        missing.remove("value");
        missing.put("quality", "MISSING");
        missing.put("observedAt", "2026-10-10T11:30:00Z");
        ingest("logger", List.of(missing));
        assertThat(service.latestReported(0, 10).readings().get(0).value()).isNull();
        assertThat(service.latestReported(0, 10).readings().get(0).quality()).isEqualTo("MISSING");
        var error = row("error", 999);
        error.put("quality", "ERROR");
        error.put("observedAt", "2026-10-10T11:40:00Z");
        ingest("logger", List.of(error));
        assertThat(service.latestReported(0, 10).readings().get(0).quality()).isEqualTo("ERROR");
        var suspect = row("suspect", 20);
        suspect.put("quality", "SUSPECT");
        suspect.put("observedAt", "2026-10-10T11:50:00Z");
        ingest("logger", List.of(suspect));
        assertThat(service.latestReported(0, 10).readings().get(0).quality()).isEqualTo("SUSPECT");
        assertThat(service.count()).isEqualTo(4);
    }

    @Test void latestReportedSeparatesSourcesDevicesMeasurementsUnitsAndGrowingAreas() {
        ingest("a", List.of(row("base", 21), row("base-again", 22)));
        for (var change : List.of(Map.entry("deviceId", "probe-2"), Map.entry("metric", "soil.temperature"),
                Map.entry("unit", "F"), Map.entry("location", "Other house"))) {
            var distinct = row(change.getKey(), 30);
            distinct.put(change.getKey(), change.getValue());
            ingest("a", List.of(distinct));
        }
        ingest("b", List.of(row("base", 99)));
        var latest = service.latestReported(0, 200);
        assertThat(latest.readings()).hasSize(6);
        assertThat(latest.readings().stream().filter(r -> r.sourceId().equals("a") && r.readingKey().equals("base-again"))).hasSize(1);
        assertThat(latest.readings().stream().filter(r -> r.sourceId().equals("a") && r.readingKey().equals("base"))).isEmpty();
    }

    @Test void latestReportedRanksBeforePagingSoOlderPagesCannotReviveSupersededReadings() {
        var secondDevice = row("other-device", 25);
        secondDevice.put("deviceId", "probe-2");
        ingest("a", List.of(row("old", 21), secondDevice, row("new-at-same-time", 22)));
        var first = service.latestReported(0, 1);
        assertThat(first.readings()).extracting(FarmReading::readingKey).containsExactly("new-at-same-time");
        assertThat(first.hasMore()).isTrue();
        var newDevice = row("arrived-after-page", 26);
        newDevice.put("deviceId", "probe-3");
        ingest("a", List.of(newDevice));
        var older = service.latestReported(first.nextAfterId(), 2);
        assertThat(older.readings()).extracting(FarmReading::readingKey).containsExactly("other-device");
        assertThat(older.hasMore()).isFalse();
        assertThat(service.latestReported(older.nextAfterId(), 2).readings()).isEmpty();
        secondDevice.put("readingKey", "updated-other-device");
        ingest("a", List.of(secondDevice));
        assertThat(service.latestReported(first.nextAfterId(), 2).readings()).isEmpty();
        assertThat(service.latestReported(0, 200).readings()).hasSize(3);
    }

    @Test void latestReportedHonorsEmptyPagesBoundsAndMaximumPageSize() {
        assertThat(service.latestReported(0, 200).readings()).isEmpty();
        assertThat(service.latestReported(0, 200).nextAfterId()).isZero();
        for (int bad : List.of(0, -1, 201)) {
            assertThatThrownBy(() -> service.latestReported(0, bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.latestReported(-1, 200)).isInstanceOf(IllegalArgumentException.class);
        List<Map<String, Object>> devices = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            var reading = row("row-" + i, i);
            reading.put("deviceId", "probe-" + i);
            devices.add(reading);
        }
        ingest("logger", devices);
        var first = service.latestReported(0, 200);
        assertThat(first.readings()).hasSize(200);
        assertThat(first.hasMore()).isTrue();
        assertThat(service.latestReported(first.nextAfterId(), 200).readings()).hasSize(1);
    }

    @Test void delayedAndMissingReadingsRemainVisibleThroughReceiptCursorPaging() {
        Map<String, Object> missing = row("missing", 1);
        missing.remove("value");
        missing.put("quality", "MISSING");
        missing.put("observedAt", "2026-09-01T00:00:00Z");
        ingest("source", List.of(row("first", 20), missing, row("third", 22)));
        var first = service.history("source", 0, 1);
        assertThat(first.hasMore()).isTrue();
        var rest = service.history("source", first.nextAfterId(), 2);
        assertThat(rest.hasMore()).isFalse();
        assertThat(rest.readings()).extracting(FarmReading::readingKey).containsExactly("missing", "third");
        assertThat(rest.readings().get(0).value()).isNull();
        assertThat(rest.readings().get(0).quality()).isEqualTo("MISSING");
        assertThat(service.history("source", rest.nextAfterId(), 2).nextAfterId()).isEqualTo(rest.nextAfterId());
    }

    @Test void rejectsAmbiguousOrInvalidNumbersWithoutRoundingOrLosingExistingRows() {
        for (Object value : List.of("21.2", Double.NaN, Double.POSITIVE_INFINITY,
                new BigDecimal("0.0000000000001"), new BigDecimal("1e1000000"),
                new BigDecimal("1000000000000000000"))) {
            assertThatThrownBy(() -> ingest("source", List.of(row("bad", value))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        Map<String, Object> missing = row("bad", 0);
        missing.put("quality", "MISSING");
        assertThatThrownBy(() -> ingest("source", List.of(missing))).hasMessageContaining("omit value");
        Map<String, Object> good = row("bad", 0);
        good.remove("value");
        assertThatThrownBy(() -> ingest("source", List.of(good))).hasMessageContaining("require a numeric");
        assertThat(service.count()).isZero();
        assertThat(ingest("source", List.of(row("zero", 0), row("negative", -4.25))).inserted()).isEqualTo(2);
    }

    @Test void validatesTimeMetadataQualityRawBytesAndUnknownFields() {
        for (Map.Entry<String, Object> change : List.<Map.Entry<String, Object>>of(
                Map.entry("observedAt", "2026-10-10 11:00:00"),
                Map.entry("observedAt", "2026-10-10T12:10:00Z"),
                Map.entry("observedAt", "1960-01-01T00:00:00Z"), Map.entry("quality", "VALID"),
                Map.entry("deviceId", "device with spaces"), Map.entry("unit", "C\n"),
                Map.entry("unit", 12), Map.entry("location", "x".repeat(181)),
                Map.entry("rawFrame", "é".repeat(4097)), Map.entry("rawFrame", "\ud800"),
                Map.entry("rawFrame", "\udc00"), Map.entry("rawFrame", 12))) {
            Map<String, Object> bad = row("bad", 21);
            bad.put(change.getKey(), change.getValue());
            // Whitespace at the edges is metadata normalization, not a changed physical unit.
            if (change.getKey().equals("unit") && change.getValue().equals("C\n")) {
                bad.put("unit", "C\nF");
            }
            assertThatThrownBy(() -> ingest("source", List.of(bad))).isInstanceOf(IllegalArgumentException.class);
        }
        Map<String, Object> unknown = row("bad", 1);
        unknown.put("pumpCommand", "start");
        assertThatThrownBy(() -> ingest("source", List.of(unknown))).hasMessageContaining("declared reading fields");
        assertThatThrownBy(() -> ingest("", List.of(row("x", 1)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ingest("source", List.of())).hasMessageContaining("1 and 500");
        assertThatThrownBy(() -> ingest("source", List.of("bad"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.history("source", -1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.history("source", 0, 201)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void concurrentSameSampleCommitsOneRecord() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            var calls = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 2; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    return ingest("source", List.of(row("same", 21))).inserted();
                }));
            }
            start.countDown();
            int inserted = calls.get(0).get(10, TimeUnit.SECONDS) + calls.get(1).get(10, TimeUnit.SECONDS);
            assertThat(inserted).isEqualTo(1);
            assertThat(service.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void fileDatabaseReopensWithExactReadingAndDurableRetry(@TempDir Path directory) {
        jdbc.execute("SHUTDOWN");
        String url = "jdbc:h2:file:" + directory.resolve("farm").toString().replace('\\', '/');
        connect(url, true);
        ingest("source", List.of(row("sample", new BigDecimal("3.123456789012"))));
        jdbc.execute("SHUTDOWN");
        connect(url, false);
        assertThat(service.history("source", 0, 100).readings().get(0).value()).isEqualByComparingTo("3.123456789012");
        assertThat(service.latestReported(0, 100).readings().get(0).readingKey()).isEqualTo("sample");
        assertThat(ingest("source", List.of(row("sample", new BigDecimal("3.123456789012")))).replayed()).isEqualTo(1);
    }
}
