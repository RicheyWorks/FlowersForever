package com.flowerfarm.telemetry;

import com.flowerfarm.util.CsvSupport;
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

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class EquipmentReadingExportTest {
    @TempDir Path directory;
    private JdbcTemplate jdbc;
    private FarmReadingService service;
    private TransactionTemplate transaction;

    @BeforeEach void setup() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:reading-copy-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/h2/V50__farm_readings.sql")).execute(data);
        jdbc = new JdbcTemplate(data);
        service = new FarmReadingService(jdbc);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(data));
    }

    @AfterEach void close() { jdbc.execute("SHUTDOWN"); }

    private void ingest(String source, List<?> readings) {
        transaction.executeWithoutResult(status -> service.ingest(source, readings));
    }

    private static Map<String, Object> row(String key) {
        return new HashMap<>(Map.of("readingKey", key, "deviceId", "probe-1", "metric", "air.temperature",
                "unit", "°C", "value", new BigDecimal("-21.123456789012"),
                "observedAt", "2026-10-10T04:00:00.123456789-07:00", "location", "House, \"North\"",
                "rawFrame", "PRIVATE-ORIGINAL-EQUIPMENT-MESSAGE"));
    }

    private List<List<String>> records(Path file) throws IOException {
        String content = Files.readString(file);
        assertThat(content).startsWith("\ufeff").endsWith("\r\n");
        return CsvSupport.readAll(new StringReader(content.substring(1)), ',');
    }

    private long copy(Path target) throws IOException {
        return EquipmentReadingExport.write(EquipmentReadingExport.inspect(target), service.snapshotForCopy(), service);
    }

    @Test void preservesExactNegativeNumbersNanosecondsUnitsAndReferencesWithoutOriginalMessages() throws Exception {
        ingest("bridge", List.of(row("event-1")));
        Path file = directory.resolve("saved.csv");
        assertThat(copy(file)).isEqualTo(1);
        var records = records(file);
        assertThat(records.get(0)).containsExactlyElementsOf(EquipmentReadingExport.HEADINGS);
        assertThat(records.get(1)).containsExactly("probe-1", "House, \"North\"", "Air temperature", "-21.123456789012", "°C",
                "Measured", "2026-10-10T11:00:00.123456789Z", service.recent(0, 1).readings().get(0).receivedAt().toString(),
                "bridge", "probe-1", "air.temperature", "event-1", "1");
        assertThat(Files.readString(file)).doesNotContain("PRIVATE-ORIGINAL-EQUIPMENT-MESSAGE");
        assertThat(service.recent(0, 1).readings().get(0).rawFrame()).contains("PRIVATE-ORIGINAL");
        assertThat(service.count()).isEqualTo(1);
    }

    @Test void keepsMissingBlankAndMeasuredZeroDistinctIncludingFaultyAndUnfamiliarMeasurements() throws Exception {
        var missing = row("missing"); missing.remove("value"); missing.put("quality", "MISSING");
        var zero = row("zero"); zero.put("value", BigDecimal.ZERO); zero.put("quality", "SUSPECT");
        var error = row("fault"); error.remove("value"); error.put("quality", "ERROR"); error.put("metric", "pump.voltage");
        ingest("bridge", List.of(missing, zero, error));
        Path file = directory.resolve("conditions.csv"); copy(file);
        var records = records(file);
        assertThat(records.get(1).get(3)).isEmpty(); assertThat(records.get(1).get(5)).isEqualTo("No reading");
        assertThat(records.get(2).get(3)).isEqualTo("0"); assertThat(records.get(2).get(5)).isEqualTo("Needs checking");
        assertThat(records.get(3).get(3)).isEmpty(); assertThat(records.get(3).get(5)).isEqualTo("Equipment error");
        assertThat(records.get(3).get(2)).isEqualTo("pump.voltage");
    }

    @Test void decodesFarmerNamesAndNeutralizesFormulaTextWithoutChangingNumericMeasurements() throws Exception {
        for (String prefix : List.of("=", "+", "-", "@", "\u00a0=", "\ufeff@")) {
            var reading = ManualFarmReading.draft(UUID.randomUUID().toString(), prefix + "Probe", prefix + "Area",
                    "water.ph", prefix + "Unit", "-1.25", "GOOD", Instant.parse("2026-10-10T11:00:00Z"));
            ingest(ManualFarmReading.SOURCE, List.of(reading));
        }
        Path file = directory.resolve("safe.csv"); copy(file);
        for (var record : records(file).subList(1, 7)) {
            assertThat(record.get(0)).startsWith("'"); assertThat(record.get(1)).startsWith("'");
            assertThat(record.get(4)).startsWith("'"); assertThat(record.get(3)).isEqualTo("-1.25");
        }
    }

    @Test void preservesValidatedAdapterIdentityColumnsAndQuotesMetadata() throws Exception {
        var reading = row("event:42"); reading.put("deviceId", "probe-2"); reading.put("metric", "pump.voltage");
        ingest("source-1", List.of(reading));
        Path file = directory.resolve("identities.csv"); copy(file);
        var record = records(file).get(1);
        assertThat(record.get(0)).isEqualTo("probe-2"); assertThat(record.get(2)).isEqualTo("pump.voltage");
        assertThat(record.get(8)).isEqualTo("source-1"); assertThat(record.get(9)).isEqualTo("probe-2");
        assertThat(record.get(10)).isEqualTo("pump.voltage"); assertThat(record.get(11)).isEqualTo("event:42");
        assertThat(record.get(1)).isEqualTo("House, \"North\"");
    }

    @Test void streamsMoreThanOnePageAcrossSourcesAndExcludesArrivalsAfterReviewedFence() throws Exception {
        var batch = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 405; i++) batch.add(row("event-" + i));
        ingest("first", batch); ingest("second", List.of(row("other-source")));
        var snapshot = service.snapshotForCopy();
        var instrumented = spy(service);
        var appended = new AtomicBoolean();
        doAnswer(call -> {
            assertThat((Integer) call.getArgument(2)).isLessThanOrEqualTo(200);
            if (appended.compareAndSet(false, true)) ingest("first", List.of(row("new-arrival")));
            return service.copyPage(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        }).when(instrumented).copyPage(any(), anyLong(), anyInt());
        Path file = directory.resolve("all.csv");
        assertThat(EquipmentReadingExport.write(EquipmentReadingExport.inspect(file), snapshot, instrumented)).isEqualTo(406);
        assertThat(records(file)).hasSize(407);
        assertThat(records(file).get(406).get(8)).isEqualTo("second");
        assertThat(Files.readString(file)).doesNotContain("new-arrival");
        assertThat(service.count()).isEqualTo(407);
        assertThat(service.latestReported(0, 200).readings()).hasSize(2);
    }

    @Test void successfulReviewedReplacementPublishesCompleteFileAndRemovesStagingFile() throws Exception {
        ingest("bridge", List.of(row("event")));
        Path target = directory.resolve("replace.csv"); Files.writeString(target, "previous copy");
        assertThat(EquipmentReadingExport.inspect(target).replacesExisting()).isTrue();
        assertThat(copy(target)).isEqualTo(1);
        assertThat(records(target)).hasSize(2);
        assertNoStaging();
    }

    @Test void pageFailureRetainsExistingCopyAndCleansItsOwnTemporaryFile() throws Exception {
        ingest("bridge", List.of(row("event")));
        Path target = directory.resolve("previous.csv"); Files.writeString(target, "previous copy");
        var broken = spy(service);
        doAnswer(call -> { throw new IllegalStateException("database unavailable"); })
                .when(broken).copyPage(any(), anyLong(), anyInt());
        var destination = EquipmentReadingExport.inspect(target);
        var snapshot = service.snapshotForCopy();
        assertThatThrownBy(() -> EquipmentReadingExport.write(destination, snapshot, broken))
                .isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(target)).isEqualTo("previous copy");
        assertNoStaging();
    }

    @Test void incompleteHistoryNeverPublishesPartialCopy() throws Exception {
        ingest("bridge", List.of(row("event")));
        var broken = spy(service);
        doAnswer(call -> List.of()).when(broken).copyPage(any(), anyLong(), anyInt());
        Path target = directory.resolve("new.csv");
        assertThatThrownBy(() -> EquipmentReadingExport.write(EquipmentReadingExport.inspect(target), service.snapshotForCopy(), broken))
                .isInstanceOf(IOException.class).hasMessageContaining("Saved readings changed");
        assertThat(target).doesNotExist(); assertNoStaging();
    }

    @Test void refusesChangedExistingFileAfterReview() throws Exception {
        ingest("bridge", List.of(row("event")));
        Path target = directory.resolve("changed.csv"); Files.writeString(target, "reviewed copy");
        var destination = EquipmentReadingExport.inspect(target);
        Files.writeString(target, "another person's newer copy");
        assertThatThrownBy(() -> EquipmentReadingExport.write(destination, service.snapshotForCopy(), service))
                .isInstanceOf(IOException.class).hasMessageContaining("selected file changed");
        assertThat(Files.readString(target)).isEqualTo("another person's newer copy"); assertNoStaging();
    }

    @Test void refusesTargetThatAppearsWhilePagesAreBeingCopied() throws Exception {
        ingest("bridge", List.of(row("event")));
        Path target = directory.resolve("appeared.csv");
        var instrumented = spy(service);
        doAnswer(call -> {
            Files.writeString(target, "newly created by someone else");
            return service.copyPage(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        }).when(instrumented).copyPage(any(), anyLong(), anyInt());
        var destination = EquipmentReadingExport.inspect(target);
        assertThatThrownBy(() -> EquipmentReadingExport.write(destination, service.snapshotForCopy(), instrumented))
                .isInstanceOf(IOException.class).hasMessageContaining("selected file changed");
        assertThat(Files.readString(target)).isEqualTo("newly created by someone else"); assertNoStaging();
    }

    @Test void changedExistingDestinationDuringCopyIsKeptInsteadOfReplaced() throws Exception {
        ingest("bridge", List.of(row("event")));
        Path target = directory.resolve("changed-during-copy.csv"); Files.writeString(target, "reviewed copy");
        var instrumented = spy(service);
        doAnswer(call -> {
            Files.writeString(target, "newer destination contents");
            return service.copyPage(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        }).when(instrumented).copyPage(any(), anyLong(), anyInt());
        var destination = EquipmentReadingExport.inspect(target);
        assertThatThrownBy(() -> EquipmentReadingExport.write(destination, service.snapshotForCopy(), instrumented))
                .isInstanceOf(IOException.class).hasMessageContaining("selected file changed");
        assertThat(Files.readString(target)).isEqualTo("newer destination contents"); assertNoStaging();
    }

    @Test void brokenPageOrderNeverPublishesACompleteLookingCopy() throws Exception {
        ingest("bridge", List.of(row("one"), row("two")));
        var broken = spy(service);
        doAnswer(call -> {
            var page = service.copyPage(call.getArgument(0), call.getArgument(1), call.getArgument(2));
            return List.of(page.get(1), page.get(0));
        }).when(broken).copyPage(any(), anyLong(), anyInt());
        Path target = directory.resolve("order.csv");
        assertThatThrownBy(() -> EquipmentReadingExport.write(EquipmentReadingExport.inspect(target), service.snapshotForCopy(), broken))
                .isInstanceOf(IOException.class).hasMessageContaining("order changed");
        assertThat(target).doesNotExist(); assertNoStaging();
    }

    @Test void rejectsDirectoriesMissingParentsAndNonSpreadsheetNamesBeforeWriting() throws Exception {
        for (Path invalid : List.of(directory.resolve("plain.txt"), directory.resolve("absent/file.csv"), directory.getRoot())) {
            assertThatThrownBy(() -> EquipmentReadingExport.inspect(invalid)).isInstanceOf(IOException.class);
        }
        Path folder = Files.createDirectory(directory.resolve("folder.csv"));
        assertThatThrownBy(() -> EquipmentReadingExport.inspect(folder)).isInstanceOf(IOException.class).hasMessageContaining("regular");
        assertThat(EquipmentReadingExport.inspect(directory.resolve("UPPER.CSV")).path()).isEqualTo(directory.toRealPath().resolve("UPPER.CSV"));
        assertNoStaging();
    }

    @Test void emptySnapshotProducesOnlyHeadingsForNonGuiCallers() throws Exception {
        Path file = directory.resolve("empty.csv");
        assertThat(copy(file)).isZero(); assertThat(records(file)).hasSize(1); assertNoStaging();
    }

    private void assertNoStaging() throws IOException {
        try (var paths = Files.list(directory)) {
            assertThat(paths.filter(path -> path.getFileName().toString().endsWith(".part")).toList()).isEmpty();
        }
    }
}
