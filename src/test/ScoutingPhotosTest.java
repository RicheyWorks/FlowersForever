package com.flowerfarm.scouting;

import com.flowerfarm.backup.LiveBackupServiceAccess;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.service.FieldAttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Scouting photos through the attachments store")
class ScoutingPhotosTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    @TempDir
    Path data;
    @TempDir
    Path camera;

    private ScoutingStore store;
    private ScoutingService service;
    private FieldAttachmentService attachments;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ZoneId.of("America/Los_Angeles"));
        store = new ScoutingStore(data, clock);
        service = new ScoutingService(store, season -> List.of(), new FarmProfile(), clock);
        attachments = new FieldAttachmentService(null, null, data.resolve("attachments").toString());
        service.setAttachments(attachments);
    }

    private Scouting.Finding savedFinding() throws IOException {
        return service.saveFinding(new Scouting.Finding(null, TODAY, null, "Bed 4", "Dahlia", "",
                Scouting.Category.INSECT_MITE, "Thrips", "Silvery streaks", Scouting.Severity.HIGH, 20.0, "", ""));
    }

    private static byte[] image(String format, int rgb) throws IOException {
        BufferedImage picture = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        picture.setRGB(1, 1, rgb);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(picture, format, out)).isTrue();
        return out.toByteArray();
    }

    private Path cameraFile(String name, byte[] bytes) throws IOException {
        return Files.write(camera.resolve(name), bytes);
    }

    @Test
    void attachedPhotoIsStoredInTheBackedUpAttachmentsFolderAndReadBackVerified() throws Exception {
        Scouting.Finding finding = savedFinding();
        byte[] jpeg = image("jpg", 0xff0000);
        Scouting.Photo photo = service.attachPhoto(finding.id(), cameraFile("IMG_2041.jpg", jpeg), "leaf underside");

        assertThat(photo.sha256()).isEqualTo(FieldAttachmentService.sha256(jpeg));
        assertThat(photo.mediaType()).isEqualTo("image/jpeg");
        assertThat(photo.byteSize()).isEqualTo(jpeg.length);
        assertThat(photo.filename()).isEqualTo("IMG_2041.jpg");
        assertThat(photo.added()).isEqualTo(TODAY);
        assertThat(photo.label()).startsWith("IMG_2041.jpg · leaf underside · 2026-10-07 · ");

        Path stored = data.resolve("attachments").resolve(photo.sha256().substring(0, 2)).resolve(photo.sha256());
        assertThat(stored).hasBinaryContent(jpeg);
        assertThat(LiveBackupServiceAccess.skipped(data, stored)).as("backups include photos").isFalse();
        assertThat(service.photoBytes(photo)).containsExactly(jpeg);
        assertThat(service.finding(finding.id()).orElseThrow().photos()).containsExactly(photo);
        assertThat(Files.readString(store.file())).contains("\"photos\"").contains(photo.sha256())
                .doesNotContain(camera.toString().replace("\\", "\\\\"));
    }

    @Test
    void sameFileTwiceKeepsOneEntryAndEditsNeverDropPhotos() throws Exception {
        Scouting.Finding finding = savedFinding();
        byte[] png = image("png", 0x00ff00);
        Scouting.Photo first = service.attachPhoto(finding.id(), cameraFile("a.png", png), "");
        Scouting.Photo again = service.attachPhoto(finding.id(), cameraFile("copy-of-a.png", png), "dup");
        assertThat(again).isEqualTo(first);
        service.attachPhoto(finding.id(), cameraFile("b.png", image("png", 0x0000ff)), "second");

        Scouting.Finding edited = new Scouting.Finding(finding.id(), TODAY, null, "Bed 5", "Dahlia", "",
                Scouting.Category.INSECT_MITE, "Thrips", "Worse", Scouting.Severity.SEVERE, 40.0, "", "Sprayed");
        service.saveFinding(edited);
        Scouting.Finding reloaded = service.finding(finding.id()).orElseThrow();
        assertThat(reloaded.bed()).isEqualTo("Bed 5");
        assertThat(reloaded.photos()).hasSize(2);

        service.removePhoto(finding.id(), first.sha256());
        assertThat(service.finding(finding.id()).orElseThrow().photos()).extracting(Scouting.Photo::caption)
                .containsExactly("second");
        assertThat(service.photoBytes(first)).as("stored copy kept for backups and re-attaching")
                .containsExactly(png);
    }

    @Test
    void nonImagesAndOversizedFilesAreRejectedWithoutTouchingTheLog() throws Exception {
        Scouting.Finding finding = savedFinding();
        String before = Files.readString(store.file());

        assertThatThrownBy(() -> service.attachPhoto(finding.id(),
                cameraFile("notes.jpg", "not a picture".getBytes(StandardCharsets.UTF_8)), ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JPEG, PNG, WebP, HEIC");

        Path huge = camera.resolve("huge.jpg");
        try (RandomAccessFile file = new RandomAccessFile(huge.toFile(), "rw")) {
            file.setLength(FieldAttachmentService.MAX_ATTACHMENT_BYTES + 1L);
        }
        assertThatThrownBy(() -> service.attachPhoto(finding.id(), huge, ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("larger than 15 MB");
        assertThatThrownBy(() -> service.attachPhoto(finding.id(), camera.resolve("missing.jpg"), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.attachPhoto("F-nope", cameraFile("x.png", image("png", 1)), ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no longer exists");

        assertThat(Files.readString(store.file())).isEqualTo(before);
    }

    @Test
    void aChangedOrMissingStoredPhotoIsReportedNotShown() throws Exception {
        Scouting.Finding finding = savedFinding();
        Scouting.Photo photo = service.attachPhoto(finding.id(), cameraFile("a.png", image("png", 7)), "");
        Path stored = data.resolve("attachments").resolve(photo.sha256().substring(0, 2)).resolve(photo.sha256());

        byte[] tampered = Files.readAllBytes(stored);
        tampered[tampered.length - 1] ^= 1;
        Files.write(stored, tampered);
        assertThatThrownBy(() -> service.photoBytes(photo)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hash does not match");

        Files.delete(stored);
        assertThatThrownBy(() -> service.photoBytes(photo)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> attachments.readContent("../../etc/passwd", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tooManyPhotosOnOneFindingAsksForANewFinding() throws Exception {
        Scouting.Finding finding = savedFinding();
        for (int i = 0; i < Scouting.MAX_PHOTOS; i++) {
            service.attachPhoto(finding.id(), cameraFile(i + ".png", image("png", i + 1)), "");
        }
        assertThatThrownBy(() -> service.attachPhoto(finding.id(), cameraFile("extra.png", image("png", 999)), ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("new finding");
        assertThat(service.finding(finding.id()).orElseThrow().photos()).hasSize(Scouting.MAX_PHOTOS);
    }

    @Test
    void logsWrittenBeforePhotosExistedStillLoad() throws Exception {
        Files.createDirectories(store.file().getParent());
        Files.writeString(store.file(), "{\"version\":1,\"overdueDays\":14,\"findings\":[{\"id\":\"F-20261001-1\","
                + "\"date\":\"2026-10-01\",\"bed\":\"Bed 1\",\"crop\":\"Zinnia\",\"category\":\"FUNGAL\","
                + "\"organism\":\"Powdery mildew\",\"severity\":\"MODERATE\",\"photoPath\":\"C:/pics/z.jpg\"}],"
                + "\"samples\":[]}");
        Scouting.Finding old = service.finding("F-20261001-1").orElseThrow();
        assertThat(old.photos()).isEmpty();
        assertThat(old.photoPath()).isEqualTo("C:/pics/z.jpg");

        service.attachPhoto(old.id(), cameraFile("z.png", image("png", 3)), "");
        assertThat(service.finding(old.id()).orElseThrow().photos()).hasSize(1);
    }

    @Test
    void withoutTheAttachmentsFeatureAttachingExplainsWhy() throws Exception {
        Scouting.Finding finding = savedFinding();
        service.setPhotoContent(null);
        assertThatThrownBy(() -> service.attachPhoto(finding.id(), cameraFile("a.png", image("png", 1)), ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not available");
    }
}
