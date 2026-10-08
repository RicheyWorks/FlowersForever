package com.flowerfarm.gui;

import com.flowerfarm.scouting.Scouting;
import com.flowerfarm.scouting.ScoutingService;
import com.flowerfarm.scouting.ScoutingTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Scouting window: Attach photo… and Photos…")
class DesktopScoutingPhotosTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    @TempDir
    Path data;
    @TempDir
    Path camera;

    private final List<String> messages = new ArrayList<>();
    private final List<Object> shown = new ArrayList<>();
    private final List<String> statuses = new ArrayList<>();
    private final Deque<Integer> answers = new ArrayDeque<>();
    private Consumer<JPanel> formEditor = panel -> { };
    private Optional<File> chosenPhoto = Optional.empty();
    private Optional<File> saveTarget = Optional.empty();

    private final FarmDialogs dialogs = new FarmDialogs() {
        @Override
        public int showOption(Component parent, Object message, String title, int optionType, int messageType,
                              Icon icon, Object[] options, Object initialValue) {
            if (message instanceof JPanel panel) {
                formEditor.accept(panel);
            } else if (message instanceof String text) {
                messages.add("ask: " + text);
            }
            return answers.isEmpty() ? -1 : answers.removeFirst();
        }

        @Override
        public int showConfirm(Component parent, Object message, String title, int optionType, int messageType) {
            return answers.isEmpty() ? -1 : answers.removeFirst();
        }

        @Override
        public void showMessage(Component parent, Object message, String title, int messageType) {
            shown.add(message);
            messages.add(String.valueOf(message));
        }
    };

    private final FarmFileChooser files = new FarmFileChooser() {
        @Override
        public Optional<File> chooseSave(Component parent, String title, File suggestedFile) {
            return saveTarget;
        }

        @Override
        public Optional<File> choosePhoto(Component parent, String title) {
            return chosenPhoto;
        }
    };

    private ScoutingService service;
    private DesktopScoutingController controller;
    private ScoutingPanel panel;
    private Runnable refresh;
    private Scouting.Finding finding;

    @BeforeEach
    void setUp() throws IOException {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ZoneId.of("America/Los_Angeles"));
        service = ScoutingTestSupport.service(data, clock);
        controller = new DesktopScoutingController(service);
        controller.attach(new DesktopScoutingController.Host(null, dialogs, files, statuses::add));
        panel = new ScoutingPanel(statuses::add);
        refresh = () -> {
            try {
                panel.showBoard(service.board());
            } catch (IOException failed) {
                throw new IllegalStateException(failed);
            }
        };
        finding = service.saveFinding(new Scouting.Finding(null, TODAY, null, "Bed 4", "Dahlia", "",
                Scouting.Category.INSECT_MITE, "Thrips", "", Scouting.Severity.HIGH, 20.0, "", ""));
        refresh.run();
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static <T extends Component> T named(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = named(container, name, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void attachPhotoAsksForACaptionAndStoresThePhotoOnTheSelectedFinding() throws Exception {
        controller.attachPhoto(panel, refresh);
        assertThat(statuses).containsExactly("Select a finding on the Findings tab first.");

        panel.selectFinding(finding.id());
        controller.attachPhoto(panel, refresh);
        assertThat(service.finding(finding.id()).orElseThrow().photos()).as("chooser cancelled").isEmpty();

        chosenPhoto = Optional.of(Files.write(camera.resolve("thrips.png"), png()).toFile());
        formEditor = form -> named(form, "scouting.photoCaption", JTextField.class).setText("flower, 10x lens");
        answers.add(0);
        controller.attachPhoto(panel, refresh);

        Scouting.Photo photo = service.finding(finding.id()).orElseThrow().photos().get(0);
        assertThat(photo.caption()).isEqualTo("flower, 10x lens");
        assertThat(statuses).last().isEqualTo("Attached thrips.png to " + finding.id() + ".");
        assertThat(panel.selectedFinding()).map(Scouting.Finding::photos).hasValueSatisfying(
                photos -> assertThat(photos).hasSize(1));
    }

    @Test
    void notAPictureIsExplainedAndNothingIsAttached() throws Exception {
        panel.selectFinding(finding.id());
        chosenPhoto = Optional.of(Files.writeString(camera.resolve("notes.png"), "hello",
                StandardCharsets.UTF_8).toFile());
        answers.add(0);
        controller.attachPhoto(panel, refresh);
        assertThat(messages).last().asString().contains("JPEG, PNG, WebP, HEIC");
        assertThat(service.finding(finding.id()).orElseThrow().photos()).isEmpty();
    }

    @Test
    void photosCanBeViewedSavedAndRemoved() throws Exception {
        panel.selectFinding(finding.id());
        controller.showPhotos(panel, refresh);
        assertThat(statuses).last().asString().contains("has no photos yet");

        byte[] png = png();
        service.attachPhoto(finding.id(), Files.write(camera.resolve("a.png"), png), "bed edge");
        refresh.run();
        panel.selectFinding(finding.id());
        File copy = data.resolve("copy.png").toFile();
        saveTarget = Optional.of(copy);
        answers.addAll(List.of(0, 1, 2, 0));
        controller.showPhotos(panel, refresh);

        assertThat(shown).first().isInstanceOfSatisfying(JLabel.class, preview -> {
            assertThat(preview.getName()).isEqualTo("scouting.photoPreview");
            assertThat(preview.getIcon().getIconWidth()).isEqualTo(40);
        });
        assertThat(copy).hasBinaryContent(png);
        assertThat(messages).anyMatch(text -> text.startsWith("ask: Remove a.png from " + finding.id()));
        assertThat(service.finding(finding.id()).orElseThrow().photos()).isEmpty();
        assertThat(statuses).contains("Saved a copy of a.png.", "Removed a.png from " + finding.id() + ".");
    }

    @Test
    void largePhotosAreScaledDownButNeverUp() {
        Dimension max = new Dimension(720, 540);
        assertThat(DesktopScoutingController.fitWithin(4032, 3024, max)).isEqualTo(new Dimension(720, 540));
        assertThat(DesktopScoutingController.fitWithin(3024, 4032, max)).isEqualTo(new Dimension(405, 540));
        assertThat(DesktopScoutingController.fitWithin(300, 200, max)).isEqualTo(new Dimension(300, 200));
        assertThat(DesktopScoutingController.fitWithin(0, 10, max)).isEqualTo(new Dimension(1, 1));
    }
}
