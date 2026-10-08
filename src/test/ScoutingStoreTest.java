package com.flowerfarm.scouting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Scouting log file: safe saves and recovery")
class ScoutingStoreTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

    @TempDir
    Path data;

    private ScoutingStore store;

    @BeforeEach
    void setUp() {
        store = new ScoutingStore(data, Clock.fixed(Instant.parse("2026-10-07T22:00:00Z"),
                ZoneId.of("America/Los_Angeles")));
    }

    private static ScoutingStore.Log logWith(int findings) {
        List<Scouting.Finding> list = java.util.stream.IntStream.rangeClosed(1, findings)
                .mapToObj(i -> ScoutingTest.finding("F-20261007-" + String.format("%03d", i), DAY)).toList();
        return new ScoutingStore.Log(1, 14, list, List.of(ScoutingTest.sample("S-20261007-001",
                Scouting.SampleStatus.SHIPPED, DAY.minusDays(3), DAY.minusDays(2))));
    }

    @Test
    void missingFileMeansEmptyLog() throws Exception {
        assertThat(store.load()).isEqualTo(ScoutingStore.Log.empty());
        assertThat(store.file()).isEqualTo(data.toAbsolutePath().resolve("scouting").resolve("scouting-log.json"));
    }

    @Test
    void savesAndReadsBackEveryField() throws Exception {
        ScoutingStore.Log log = logWith(2);
        store.save(log);
        assertThat(store.load()).isEqualTo(log);
        String json = Files.readString(store.file(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"2026-10-07\"").contains("Café au Lait").contains("INSECT_MITE");
    }

    @Test
    void previousVersionIsKeptAsBak() throws Exception {
        store.save(logWith(1));
        store.save(logWith(2));
        Path bak = store.file().resolveSibling("scouting-log.json.bak");
        assertThat(bak).exists();
        assertThat(new ScoutingStore(data, Clock.systemUTC()).load().findings()).hasSize(2);
        Files.copy(bak, store.file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThat(store.load().findings()).hasSize(1);
    }

    @Test
    void tornFileFallsBackToLastGoodCopyAndKeepsTheDamagedOne() throws Exception {
        store.save(logWith(1));
        store.save(logWith(2));
        byte[] full = Files.readAllBytes(store.file());
        Files.write(store.file(), Arrays.copyOf(full, full.length / 2));

        ScoutingStore.Log recovered = store.load();

        assertThat(recovered.findings()).hasSize(1);
        assertThat(store.takeNotice()).contains("last good copy").contains("scouting-log.damaged-20261007-150000");
        assertThat(store.takeNotice()).isEmpty();
        Path damaged = store.file().resolveSibling("scouting-log.damaged-20261007-150000.json");
        assertThat(Files.readAllBytes(damaged)).isEqualTo(Arrays.copyOf(full, full.length / 2));
        store.save(new ScoutingStore.Log(1, 14, recovered.findings(), List.of()));
        assertThat(store.load().findings()).hasSize(1);
        assertThat(damaged).as("the damaged copy is never deleted").exists();
    }

    @Test
    void garbageFileFallsBackToLastGoodCopy() throws Exception {
        store.save(logWith(3));
        store.save(logWith(4));
        Files.write(store.file(), new byte[] {0, 0, 0, 0, 0});
        assertThat(store.load().findings()).hasSize(3);
    }

    @Test
    void bothCopiesUnreadableRefusesToSaveAndChangesNothing() throws Exception {
        store.save(logWith(1));
        store.save(logWith(2));
        Files.writeString(store.file(), "{\"findings\": [");
        Path bak = store.file().resolveSibling("scouting-log.json.bak");
        Files.writeString(bak, "not json");

        assertThatThrownBy(store::load).isInstanceOf(ScoutingStore.DamagedLogException.class)
                .hasMessageContaining("Nothing has been changed or deleted");
        assertThatThrownBy(() -> store.save(logWith(5))).isInstanceOf(ScoutingStore.DamagedLogException.class)
                .hasMessageContaining("Nothing was overwritten");
        assertThat(Files.readString(store.file())).isEqualTo("{\"findings\": [");
        assertThat(Files.readString(bak)).isEqualTo("not json");
    }

    @Test
    void damagedFileWithoutCopyIsNotOverwritten() throws Exception {
        Files.createDirectories(store.file().getParent());
        Files.writeString(store.file(), "]");
        assertThatThrownBy(store::load).isInstanceOf(ScoutingStore.DamagedLogException.class);
        assertThatThrownBy(() -> store.save(ScoutingStore.Log.empty()))
                .isInstanceOf(ScoutingStore.DamagedLogException.class);
        assertThat(Files.readString(store.file())).isEqualTo("]");
    }

    @Test
    void unreadableCurrentFileIsNeverCopiedOverTheGoodBak() throws Exception {
        store.save(logWith(1));
        store.save(logWith(2));
        Path bak = store.file().resolveSibling("scouting-log.json.bak");
        byte[] goodBak = Files.readAllBytes(bak);
        Files.writeString(store.file(), "{broken");
        store.load();
        store.save(logWith(3));
        assertThat(Files.readAllBytes(bak)).isEqualTo(goodBak);
    }

    @Test
    void newerFieldsFromALaterVersionAreIgnored() throws Exception {
        Files.createDirectories(store.file().getParent());
        Files.writeString(store.file(), "{\"version\":2,\"overdueDays\":21,\"findings\":[],\"samples\":[],"
                + "\"photos\":[\"a.jpg\"]}");
        ScoutingStore.Log log = store.load();
        assertThat(log.overdueDays()).isEqualTo(21);
        assertThat(log.version()).isEqualTo(2);
    }

    @Test
    void noTemporaryFilesAreLeftBehind() throws Exception {
        for (int i = 1; i <= 5; i++) {
            store.save(logWith(i));
        }
        try (Stream<Path> files = Files.list(store.file().getParent())) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder("scouting-log.json", "scouting-log.json.bak");
        }
    }

    @Test
    void logCopiesListsAndClampsSettings() {
        List<Scouting.Finding> mutable = new java.util.ArrayList<>();
        ScoutingStore.Log log = new ScoutingStore.Log(0, 999, mutable, null);
        mutable.add(ScoutingTest.finding("F-1", DAY));
        assertThat(log.findings()).isEmpty();
        assertThat(log.samples()).isEmpty();
        assertThat(log.overdueDays()).isEqualTo(Scouting.MAX_OVERDUE_DAYS);
        assertThat(log.version()).isEqualTo(1);
        assertThat(new ScoutingStore.Log(1, 0, null, null).overdueDays()).isEqualTo(Scouting.DEFAULT_OVERDUE_DAYS);
    }
}
