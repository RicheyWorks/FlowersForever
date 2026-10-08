package com.flowerfarm.scouting;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.service.FieldAttachmentService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Scouting log in the farm database, imported once from the JSON file")
class DatabaseScoutingStoreTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    @TempDir
    Path data;
    @TempDir
    Path camera;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ZoneId.of("America/Los_Angeles"));
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private ScoutingStore json;

    @BeforeEach
    void setUp() {
        String url = "jdbc:h2:mem:scouting-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2").load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        json = new ScoutingStore(data, clock);
    }

    private DatabaseScoutingStore database() {
        return new DatabaseScoutingStore(jdbc, transactions, json, clock);
    }

    private static Scouting.Photo photo(String sha, String caption) {
        return new Scouting.Photo(sha, "image/png", 1234, "IMG_" + caption + ".png", caption, TODAY.minusDays(1));
    }

    private static ScoutingStore.Log legacyLog() {
        Scouting.Finding withPhotos = ScoutingTest.finding("F-20261001-001", TODAY.minusDays(6))
                .withPhotos(List.of(photo("a".repeat(64), "leaf underside"), photo("b".repeat(64), "bloom")));
        Scouting.Finding plain = new Scouting.Finding("F-20261005-001", TODAY.minusDays(2), 41L, "Block B", "",
                "", Scouting.Category.ABIOTIC, "", "Scorched edges after the heat", Scouting.Severity.LOW, null,
                "C:\\photos\\scorch.jpg", "");
        Scouting.LabSample sample = new Scouting.LabSample("S-20261002-001", "F-20261001-001", "",
                Scouting.SampleType.INSECT, "wsu-puyallup", "WSU Puyallup", TODAY.minusDays(5), TODAY.minusDays(4),
                "1Z999", Scouting.SampleStatus.SHIPPED, new BigDecimal("40.00"), "", "", null);
        Scouting.LabSample soil = new Scouting.LabSample("S-20261003-001", "", "North field", Scouting.SampleType.SOIL,
                "", "Soil lab", TODAY.minusDays(4), null, "", Scouting.SampleStatus.COLLECTED, null, "", "", null);
        return new ScoutingStore.Log(1, 21, List.of(withPhotos, plain), List.of(sample, soil));
    }

    @Test
    @DisplayName("the JSON log is copied in once, every field and photo link intact, and the file is left as a backup")
    void importsOnceAndKeepsTheJsonFile() throws IOException {
        json.save(legacyLog());
        json.save(legacyLog()); // creates the .bak copy too
        Path bak = json.file().resolveSibling(ScoutingStore.FILE + ".bak");
        byte[] file = Files.readAllBytes(json.file());
        byte[] previous = Files.readAllBytes(bak);

        DatabaseScoutingStore store = database();
        assertThat(store.jsonImported()).isFalse();
        ScoutingStore.Log log = store.load();

        assertThat(log).isEqualTo(legacyLog());
        assertThat(store.jsonImported()).isTrue();
        assertThat(store.takeNotice()).contains("now lives in the farm database", "2 findings and 2 lab samples",
                "kept unchanged as a backup");
        assertThat(store.takeNotice()).isEmpty();
        assertThat(Files.readAllBytes(json.file())).isEqualTo(file);
        assertThat(Files.readAllBytes(bak)).isEqualTo(previous);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scouting_finding_photos", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT json_import_note FROM scouting_settings", String.class))
                .contains(json.file().toString());

        // A later change to the old file is not imported again, by this or a new store.
        json.save(new ScoutingStore.Log(1, 30, List.of(), List.of()));
        assertThat(store.load()).isEqualTo(legacyLog());
        DatabaseScoutingStore reopened = database();
        assertThat(reopened.load()).isEqualTo(legacyLog());
        assertThat(reopened.takeNotice()).isEmpty();
    }

    @Test
    @DisplayName("without a JSON log the database log starts empty and the import is recorded")
    void startsEmptyWithoutJson() throws IOException {
        DatabaseScoutingStore store = database();

        assertThat(store.load()).isEqualTo(ScoutingStore.Log.empty());
        assertThat(store.takeNotice()).isEmpty();
        assertThat(store.jsonImported()).isTrue();
        assertThat(Files.exists(json.file())).isFalse();
    }

    @Test
    @DisplayName("an unreadable JSON log blocks the import, records nothing, and imports once it is restored")
    void damagedJsonIsNotForked() throws IOException {
        Files.createDirectories(json.file().getParent());
        Files.writeString(json.file(), "{ not json", StandardCharsets.UTF_8);
        DatabaseScoutingStore store = database();

        assertThatThrownBy(store::load).isInstanceOf(ScoutingStore.DamagedLogException.class);
        assertThatThrownBy(() -> store.save(ScoutingStore.Log.empty()))
                .isInstanceOf(ScoutingStore.DamagedLogException.class);
        assertThat(store.jsonImported()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scouting_findings", Integer.class)).isZero();
        assertThat(Files.readString(json.file(), StandardCharsets.UTF_8)).isEqualTo("{ not json");

        new ScoutingStore(data, clock).save(legacyLog());
        assertThat(database().load()).isEqualTo(legacyLog());
    }

    @Test
    @DisplayName("a JSON log recovered from its last good copy is imported and the grower hears about both")
    void recoveredJsonIsImported() throws IOException {
        json.save(legacyLog());
        json.save(legacyLog());
        Files.writeString(json.file(), "damaged", StandardCharsets.UTF_8);

        DatabaseScoutingStore store = database();

        assertThat(store.load()).isEqualTo(legacyLog());
        assertThat(store.takeNotice()).contains("last good copy", "now lives in the farm database");
    }

    @Test
    @DisplayName("missing, overlong and repeated ids get new codes so no record is dropped")
    void repairsIdsOnImport() throws IOException {
        Scouting.Finding first = ScoutingTest.finding("F-20261001-001", TODAY.minusDays(6));
        Scouting.Finding repeat = ScoutingTest.finding("F-20261001-001", TODAY.minusDays(6));
        Scouting.Finding blank = ScoutingTest.finding(" ", TODAY.minusDays(1));
        String overlong = "F-" + "9".repeat(60);
        Scouting.Finding longId = ScoutingTest.finding(overlong, TODAY);
        Scouting.LabSample linked = new Scouting.LabSample("", overlong, "", Scouting.SampleType.INSECT, "",
                "Clinic", TODAY, null, "", Scouting.SampleStatus.COLLECTED, null, "", "", null);
        Scouting.LabSample undated = new Scouting.LabSample("S-1", "F-20261001-001", "", Scouting.SampleType.SOIL,
                "", "Clinic", null, null, "", Scouting.SampleStatus.COLLECTED, null, "", "", null);
        Scouting.LabSample again = new Scouting.LabSample("S-1", "", "Block A", Scouting.SampleType.SOIL,
                "", "Clinic", null, null, "", Scouting.SampleStatus.COLLECTED, null, "", "", null);
        json.save(new ScoutingStore.Log(1, 14, List.of(first, repeat, blank, longId), List.of(linked, undated, again)));

        DatabaseScoutingStore store = database();
        ScoutingStore.Log log = store.load();

        assertThat(log.findings()).extracting(Scouting.Finding::id).containsExactly("F-20261001-001",
                "F-20261001-002", "F-20261006-001", "F-20261007-001");
        assertThat(log.samples()).extracting(Scouting.LabSample::id).containsExactly("S-20261007-001", "S-1",
                "S-20261007-002");
        assertThat(log.samples().get(0).findingId()).isEqualTo("F-20261007-001");
        assertThat(log.samples().get(1).findingId()).isEqualTo("F-20261001-001");
        assertThat(store.takeNotice()).contains("5 records had a missing or repeated ID");
    }

    @Test
    @DisplayName("scouting rows already in the database are kept and the JSON log is not merged into them")
    void existingRowsWinOverJson() throws IOException {
        json.save(legacyLog());
        jdbc.update("INSERT INTO scouting_lab_samples (code, sample_type, status) VALUES ('S-X', 'SOIL', 'COLLECTED')");

        DatabaseScoutingStore store = database();

        assertThat(store.load().samples()).extracting(Scouting.LabSample::id).containsExactly("S-X");
        assertThat(store.takeNotice()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT json_import_note FROM scouting_settings", String.class))
                .contains("already had scouting records");
    }

    @Test
    @DisplayName("the scouting service keeps findings, photos, samples and settings in the database")
    void serviceRoundTrip() throws IOException {
        ScoutingService service = new ScoutingService(database(), season -> List.of(), new FarmProfile(), clock);
        service.setAttachments(new FieldAttachmentService(null, null, data.resolve("attachments").toString()));

        Scouting.Finding finding = service.saveFinding(ScoutingTest.finding(null, TODAY));
        Path picture = camera.resolve("thrips.png");
        BufferedImage image = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0x336699);
        assertThat(ImageIO.write(image, "png", picture.toFile())).isTrue();
        Scouting.Photo photo = service.attachPhoto(finding.id(), picture, "underside");
        Scouting.LabSample sample = service.saveSample(new Scouting.LabSample(null, finding.id(), "",
                Scouting.SampleType.INSECT, "", "Clinic", TODAY, null, "", Scouting.SampleStatus.COLLECTED,
                new BigDecimal("12.5"), "", "", null));
        service.saveFinding(ScoutingTest.finding(finding.id(), TODAY));
        service.saveOverdueDays(30);

        ScoutingStore.Log reopened = database().load();
        assertThat(reopened.overdueDays()).isEqualTo(30);
        assertThat(reopened.finding(finding.id()).orElseThrow().photos()).containsExactly(photo);
        assertThat(reopened.sample(sample.id()).orElseThrow().cost()).isEqualTo(new BigDecimal("12.50"));
        assertThatThrownBy(() -> service.deleteFinding(finding.id())).isInstanceOf(IllegalArgumentException.class);

        service.removePhoto(finding.id(), photo.sha256());
        service.saveSample(sample.withId(sample.id()));
        jdbc.update("DELETE FROM scouting_lab_samples");
        service.deleteFinding(finding.id());
        assertThat(database().load()).isEqualTo(new ScoutingStore.Log(1, 30, List.of(), List.of()));
        assertThat(Files.exists(json.file())).isFalse();
    }

    @Test
    @DisplayName("a database failure is reported in plain words and changes nothing")
    void databaseFailureIsPlain() throws IOException {
        DatabaseScoutingStore store = database();
        store.save(legacyLog());
        jdbc.execute("DROP TABLE scouting_finding_photos");

        assertThatThrownBy(store::load).isInstanceOf(IOException.class)
                .hasMessageContaining("could not be read from the farm database");
        assertThatThrownBy(() -> store.save(legacyLog())).isInstanceOf(IOException.class)
                .hasMessageContaining("could not be saved to the farm database");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scouting_findings", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("an import that fails in the database records nothing")
    void failedImportRecordsNothing() throws IOException {
        json.save(legacyLog());
        jdbc.execute("DROP TABLE scouting_finding_photos");

        DatabaseScoutingStore store = database();

        assertThatThrownBy(store::load).isInstanceOf(IOException.class)
                .hasMessageContaining("could not be copied into the farm database");
        assertThat(store.jsonImported()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scouting_findings", Integer.class)).isZero();
    }

    @Test
    @DisplayName("money keeps what was entered with at least two decimals")
    void moneyScale() {
        assertThat(DatabaseScoutingStore.money(null)).isNull();
        assertThat(DatabaseScoutingStore.money(new BigDecimal("40.0000"))).isEqualTo(new BigDecimal("40.00"));
        assertThat(DatabaseScoutingStore.money(new BigDecimal("7.5000"))).isEqualTo(new BigDecimal("7.50"));
        assertThat(DatabaseScoutingStore.money(new BigDecimal("12.3450"))).isEqualTo(new BigDecimal("12.345"));
    }
}
