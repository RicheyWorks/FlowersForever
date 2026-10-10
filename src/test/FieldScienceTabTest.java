package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingFileHarness;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.FieldScienceExport;
import com.flowerfarm.model.FieldScienceExportSummary;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.reference.FieldScienceBundle;
import com.flowerfarm.reference.FieldScienceCatalog;
import com.flowerfarm.service.FieldScienceExportService;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Farm-filtered offline field science screen")
class FieldScienceTabTest {

    private static final Path PREVIEW = Path.of("target", "ui-previews",
            "field-science", "field-science-cannabis.png");

    @Test
    void cannabisScreenNamesSourcesAndExplainsSeparateStorage() throws Exception {
        onEdt(() -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        RecordingHost host = new RecordingHost();
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        var soilRecipes = FieldScienceCatalog.byId("soil-recipes").orElseThrow();
        String sampleText = "# Interface preview sample\n"
                + "Recipe notes copied from the offline tool would appear here.\n"
                + "Review the text before saving it to the farm.\n";
        byte[] sampleBytes = sampleText.getBytes(StandardCharsets.UTF_8);
        String sampleHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(sampleBytes));
        Instant sampleDate = Instant.parse("2026-10-03T00:00:00Z");
        FieldScienceExportSummary sample = new FieldScienceExportSummary(
                42L, sampleDate, soilRecipes.id(), soilRecipes.title(),
                soilRecipes.sourceUrl(), FieldScienceBundle.SOURCE_COMMIT,
                "Sample soil recipe export", "sample-recipe.txt",
                "Interface preview only", sampleBytes.length, sampleHash);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(sample), 0, false, 1));
        when(service.find(42L)).thenReturn(Optional.of(withId(42L, new FieldScienceExport(
                sampleDate, soilRecipes.id(), soilRecipes.title(), soilRecipes.sourceUrl(),
                FieldScienceBundle.SOURCE_COMMIT, sample.title(), sample.filename(),
                sample.note(), sample.byteLength(), sample.sha256(), sampleText))));
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.CANNABIS_FARM),
                host, service);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        await(() -> named(root, "fieldScience.exports.table", JTable.class).getRowCount() == 1
                && named(root, "fieldScience.exports.detail", JTextArea.class)
                .getText().equals(sampleText));
        assertThat(named(root, "fieldScience.exports.saveSelected", JButton.class).isEnabled())
                .isTrue();
        assertThat(tab.getTabTitle()).isEqualTo("Field science");
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains("Field science", "Soil recipes · Subcool’s Super Soil",
                        "Soil Bench", "Farm Scout", "Greenhouse Monitor",
                        "Breeding Bench", "Pheno Tracker");
        assertThat(descendants(root, JTextArea.class)).extracting(JTextArea::getText)
                .anySatisfy(text -> assertThat(text).contains(
                        "browser-local", "export", "H2 database", "backups",
                        "role permissions", "never writes a farm record", "pesticide"))
                .anySatisfy(text -> assertThat(text).contains(
                        "CSRBT Soil Recipes", "Subcool", "https://github.com/RicheyWorks/CSRBT/"))
                .anySatisfy(text -> assertThat(text).contains(
                        "not cannabis-specific"));
        assertThat(descendants(root, JButton.class).stream()
                .filter(button -> button.getName() != null
                        && button.getName().startsWith("fieldScience.open.")))
                .hasSize(6);
        JComponent firstCard = named(root, "fieldScience.card.soil-recipes", JComponent.class);
        JComponent archive = named(root, "fieldScience.exports.section", JComponent.class);
        assertThat(firstCard.getParent()).isSameAs(archive.getParent());
        List<Component> sections = List.of(firstCard.getParent().getComponents());
        assertThat(sections.indexOf(firstCard)).isLessThan(sections.indexOf(archive));
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(named(root, "fieldScience.open.soil-recipes", JButton.class).isEnabled())
                .isTrue();
        assertThat(named(root, "fieldScience.record.soil-recipes", JButton.class).isEnabled())
                .isFalse();
        assertThat(named(root, "fieldScience.save.soil-recipes", JButton.class).isEnabled())
                .isFalse();
        assertThat(named(root, "fieldScience.exports.searchButton", JButton.class).isEnabled())
                .isTrue();
        assertThat(named(root, "fieldScience.exports.clearSearch", JButton.class).isEnabled())
                .isFalse();
        JTextArea metadata = named(root, "fieldScience.exports.metadata", JTextArea.class);
        assertThat(metadata.isFocusable()).isTrue();
        assertThat(metadata.getAccessibleContext().getAccessibleName())
                .isEqualTo("Selected export source and note");
        JLabel pageStatus = named(root, "fieldScience.exports.pageStatus", JLabel.class);
        assertThat(pageStatus.getAccessibleContext().getAccessibleName())
                .isEqualTo(pageStatus.getText());
        assertThat(named(root, "fieldScience.exports.searchHelp", JTextArea.class).getText())
                .contains("Every role can read, search", "save selected archive text",
                        "Only HAND or OWNER can add reviewed exports",
                        "export text is not searched");
        onEdt((Runnable) () -> named(root, "fieldScience.openKnowledge",
                JButton.class).doClick());
        assertThat(host.selected).containsExactly("Knowledge Log");
        assertThat(host.lastStatus).contains("manually", "No field science content was copied");

        onEdt(() -> tab.applyRolePermissions(true));

        SwingUserHarness.on(root).layoutAt(920, 2650);
        onEdt(() -> {
            try {
                Files.createDirectories(PREVIEW.getParent());
                BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    root.printAll(graphics);
                } finally {
                    graphics.dispose();
                }
                ImageIO.write(image, "png", PREVIEW.toFile());
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void viewerCanBrowseTheCompleteLibraryWithoutWritingFarmRecords() throws Exception {
        RecordingHost host = new RecordingHost();
        List<String> materialized = new ArrayList<>();
        List<Path> opened = new ArrayList<>();
        CountDownLatch called = new CountDownLatch(1);
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.CANNABIS_FARM),
                host, fileName -> {
                    materialized.add(fileName);
                    return Path.of("offline", fileName);
                }, page -> {
                    opened.add(page);
                    called.countDown();
                });
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> tab.applyRolePermissions(false));
        JButton library = named(root, "fieldScience.library", JButton.class);
        assertThat(library.isEnabled()).isTrue();
        onEdt((Runnable) library::doClick);
        assertThat(called.await(5, TimeUnit.SECONDS)).isTrue();
        await(() -> library.isEnabled() && host.lastStatus != null);
        assertThat(materialized).containsExactly("library.html");
        assertThat(opened).containsExactly(Path.of("offline", "library.html"));
        assertThat(host.lastStatus).contains("Complete CSRBT field kit", "separate browser page");
        assertThat(host.selected).isEmpty();
    }

    @Test
    void opensOnlyTheChosenLocalPageAndReportsFailureForRetry() throws Exception {
        RecordingHost host = new RecordingHost();
        List<String> materialized = new ArrayList<>();
        List<Path> opened = new ArrayList<>();
        CountDownLatch called = new CountDownLatch(1);
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.MUSHROOM_FARM),
                host, fileName -> {
                    materialized.add(fileName);
                    return Path.of("offline", fileName);
                }, page -> {
                    opened.add(page);
                    called.countDown();
                });
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains("Collection Sheet", "Fungal Characters")
                .doesNotContain("Soil recipes · Subcool’s Super Soil");
        assertThat(descendants(root, JTextArea.class)).extracting(JTextArea::getText)
                .anySatisfy(text -> assertThat(text).contains("no edibility"));
        JButton collection = named(root, "fieldScience.open.collection-sheet", JButton.class);
        onEdt((Runnable) collection::doClick);
        assertThat(called.await(5, TimeUnit.SECONDS)).isTrue();
        await(() -> collection.isEnabled() && host.lastStatus != null);
        assertThat(materialized).containsExactly("collection-sheet.html");
        assertThat(opened).containsExactly(Path.of("offline", "collection-sheet.html"));
        assertThat(host.lastStatus).contains("separate browser page", "Export work");

        RecordingHost failingHost = new RecordingHost();
        FieldScienceTab failing = new FieldScienceTab(Set.of(FarmWorkspace.AQUACULTURE_FARM),
                failingHost, fileName -> {
                    throw new IOException("unavailable");
                }, page -> { });
        JComponent failedRoot = onEdt(failing::getUIComponent);
        JButton foodWeb = named(failedRoot, "fieldScience.open.food-web", JButton.class);
        onEdt((Runnable) foodWeb::doClick);
        await(() -> foodWeb.isEnabled() && failingHost.lastStatus != null);
        assertThat(failingHost.lastStatus).contains("Could not open Food Web Builder", "retry");
    }

    @Test
    void soilRecipeCardHandsItsExactSourceToAnEditableFarmFinding() throws Exception {
        RecordingHost host = new RecordingHost();
        List<String> materialized = new ArrayList<>();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.CANNABIS_FARM),
                host, fileName -> {
                    materialized.add(fileName);
                    return Path.of("offline", fileName);
                }, page -> { });
        JComponent root = onEdt(tab::getUIComponent);

        assertThat(descendants(root, JButton.class).stream()
                .filter(button -> button.getName() != null
                        && button.getName().startsWith("fieldScience.record.")))
                .hasSize(6);
        assertThat(named(root, "fieldScience.record.soil-recipes", JButton.class).isEnabled())
                .isTrue();
        onEdt((Runnable) () -> named(root, "fieldScience.record.soil-recipes",
                JButton.class).doClick());

        assertThat(host.findingRequests).containsExactly(new FindingRequest(
                "Soil recipes · Subcool’s Super Soil",
                "https://github.com/RicheyWorks/CSRBT/blob/"
                        + FieldScienceBundle.SOURCE_COMMIT + "/docs/soil-recipes.html"));
        assertThat(materialized).isEmpty();
    }

    @Test
    void pastedExportRequiresExplicitSaveAndUsesTheChosenInstrument() throws Exception {
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        FieldScienceExport saved = savedExport("Pinned soil recipe text", null);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(), 0, false, 0));
        when(service.page("soil", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(), 0, false, 0));
        when(service.save("soil-recipes", "Soil mix run", "", "Test plot A",
                "Pinned soil recipe text")).thenReturn(saved);
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .chooseForm("Save CSRBT export", "Save export", form -> form
                        .expectText("Soil recipes · Subcool’s Super Soil")
                        .expectText("stays a draft")
                        .type("Export title", "Soil mix run")
                        .type("Export note", "Test plot A")
                        .type("CSRBT export text", "Pinned soil recipe text"));
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.CANNABIS_FARM),
                host, fileName -> Path.of(fileName), page -> { }, service,
                dialogs, parent -> Optional.empty());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JLabel pageStatus = named(root, "fieldScience.exports.pageStatus", JLabel.class);
        await(() -> "No saved exports".equals(pageStatus.getText()));
        JTextField search = named(root, "fieldScience.exports.search", JTextField.class);
        onEdt(() -> search.setText("soil"));
        onEdt((Runnable) () -> named(root, "fieldScience.exports.searchButton",
                JButton.class).doClick());
        await(() -> pageStatus.getText().contains("No results for “soil”"));
        verify(service, never()).save(anyString(), anyString(), anyString(),
                anyString(), anyString());
        onEdt((Runnable) () -> named(root, "fieldScience.save.soil-recipes",
                JButton.class).doClick());
        await(() -> host.lastStatus != null && host.lastStatus.contains("Saved field science export"));
        await(() -> search.getText().isEmpty()
                && "No saved exports".equals(pageStatus.getText()));
        verify(service).save("soil-recipes", "Soil mix run", "", "Test plot A",
                "Pinned soil recipe text");
        assertThat(tab.applicationExitWarning()).isNull();
        dialogs.assertComplete();
    }

    @Test
    void cancelAndViewerRoleDoNotSaveAnExport() throws Exception {
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Save CSRBT export", "Cancel");
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.CANNABIS_FARM),
                host, fileName -> Path.of(fileName), page -> { }, service,
                dialogs, parent -> Optional.empty());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) () -> named(root, "fieldScience.save.soil-recipes",
                JButton.class).doClick());
        assertThat(host.lastStatus).contains("No field science export was saved");
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(named(root, "fieldScience.save.soil-recipes", JButton.class).isEnabled())
                .isFalse();
        assertThat(named(root, "fieldScience.open.soil-recipes", JButton.class).isEnabled())
                .isTrue();
        assertThat(named(root, "fieldScience.exports.refresh", JButton.class).isEnabled())
                .isTrue();
        verify(service, never()).save(anyString(), anyString(), anyString(),
                anyString(), anyString());
        dialogs.assertComplete();
    }

    @Test
    void savedExportsListLoadsExactTextForViewer() throws Exception {
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        String raw = "# scout report\ndata: observations";
        FieldScienceExportSummary summary = summary(17L, "Scout report", raw);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(summary), 0, false, 1));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(summary, raw)));
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM),
                new RecordingHost(), fileName -> Path.of(fileName), page -> { }, service,
                new SwingDialogHarness(), parent -> Optional.empty());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> tab.applyRolePermissions(false));
        onEdt((Runnable) tab::initialize);
        JTable table = named(root, "fieldScience.exports.table", JTable.class);
        JTextArea detail = named(root, "fieldScience.exports.detail", JTextArea.class);
        JTextArea metadata = named(root, "fieldScience.exports.metadata", JTextArea.class);
        await(() -> table.getRowCount() == 1 && detail.getText().contains("data: observations"));
        assertThat(detail.getText()).isEqualTo("# scout report\ndata: observations");
        assertThat(metadata.getText()).contains("Farm Scout", "Pasted text", "SHA-256");
    }

    @Test
    void reviewedSelectedExportCanStartLinkedFindingOnlyForWriter() throws Exception {
        String raw = "# reviewed scout text\nfield: north";
        FieldScienceExportSummary row = summary(17L, "Scout report", raw);
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(row), 0, false, 1));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(row, raw)));
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM),
                host, service);
        JComponent root = onEdt(tab::getUIComponent);
        JButton record = named(root, "fieldScience.exports.recordFinding", JButton.class);
        onEdt(() -> tab.applyRolePermissions(false));
        onEdt((Runnable) tab::initialize);
        await(() -> named(root, "fieldScience.exports.saveSelected", JButton.class)
                .isEnabled());
        assertThat(record.isEnabled()).isFalse();

        onEdt(() -> tab.applyRolePermissions(true));
        assertThat(record.isEnabled()).isTrue();
        onEdt((Runnable) record::doClick);

        assertThat(host.linkedFindingRequests).containsExactly(row);
        assertThat(host.findingRequests).isEmpty();
    }

    @Test
    void alteredSelectedTextCannotStartLinkedFinding() throws Exception {
        FieldScienceExportSummary row = summary(17L, "Scout report", "saved text");
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(row), 0, false, 1));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(row, "changed text")));
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM),
                host, service);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JButton record = named(root, "fieldScience.exports.recordFinding", JButton.class);
        await(record::isEnabled);
        onEdt((Runnable) record::doClick);

        assertThat(host.linkedFindingRequests).isEmpty();
        assertThat(host.lastStatus).contains("fingerprint check");
    }

    @Test
    void linkedExactExportOpensWithoutChangingArchiveAndMissingIdKeepsView()
            throws Exception {
        String archiveText = "archive page text";
        String linkedText = "linked exact text";
        FieldScienceExportSummary archive = summary(17L, "Archive row", archiveText);
        FieldScienceExportSummary linked = summary(99L, "Linked row", linkedText);
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(archive), 0, false, 1));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(archive, archiveText)));
        when(service.find(99L)).thenReturn(Optional.of(savedExport(linked, linkedText)));
        when(service.find(404L)).thenReturn(Optional.empty());
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM),
                host, service);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JTextArea archiveDetail = named(root, "fieldScience.exports.detail", JTextArea.class);
        await(() -> archiveDetail.getText().equals(archiveText));
        JTable archiveTable = named(root, "fieldScience.exports.table", JTable.class);
        JComponent linkedSection = named(root, "fieldScience.linked.section",
                JComponent.class);
        JTextArea linkedDetail = named(root, "fieldScience.linked.detail", JTextArea.class);
        JTextArea linkedMetadata = named(root, "fieldScience.linked.metadata", JTextArea.class);
        assertThat(linkedSection.isVisible()).isFalse();

        onEdt(() -> tab.applyRolePermissions(false));
        onEdt(() -> tab.showSavedExport(99L));
        await(() -> linkedSection.isVisible() && linkedDetail.getText().equals(linkedText));
        assertThat(linkedMetadata.getText()).contains("ARCHIVE ID  #99", "Linked row");
        assertThat(archiveTable.getSelectedRow()).isZero();
        assertThat(archiveDetail.getText()).isEqualTo(archiveText);

        onEdt(() -> tab.showSavedExport(404L));
        await(() -> host.lastStatus != null
                && host.lastStatus.contains("#404 is no longer available"));
        assertThat(linkedDetail.getText()).isEqualTo(linkedText);
        assertThat(archiveTable.getSelectedRow()).isZero();
        assertThat(archiveDetail.getText()).isEqualTo(archiveText);
    }

    @Test
    void viewerCanSaveExactArchivedTextOverAnApprovedFile(
            @org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        String raw = "\uFEFF# original\r\nvalue,with,commas  \r\n";
        FieldScienceExportSummary row = summary(17L, "Scout report", raw);
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(row), 0, false, 1));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(row, raw)));
        Path destination = dir.resolve("reviewed-export.txt");
        Files.writeString(destination, "previous text", StandardCharsets.UTF_8);
        SwingFileHarness files = new SwingFileHarness().saveAs(
                "Save selected field science text", "field-science-export-17.txt",
                destination.toFile());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Replace existing export?", true,
                        form -> form.expectText("reviewed-export.txt"));
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM), host,
                fileName -> Path.of(fileName), page -> { }, service, dialogs,
                parent -> Optional.empty(), files);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> tab.applyRolePermissions(false));
        onEdt((Runnable) tab::initialize);
        JButton save = named(root, "fieldScience.exports.saveSelected", JButton.class);
        await(save::isEnabled);
        onEdt((Runnable) save::doClick);
        await(() -> host.lastStatus != null
                && host.lastStatus.contains("Saved selected field science text"));
        assertThat(Files.readAllBytes(destination)).isEqualTo(raw.getBytes(StandardCharsets.UTF_8));
        assertThat(tab.hasPendingSave()).isFalse();
        files.assertComplete();
        dialogs.assertComplete();
    }

    @Test
    void selectedTextCanBeSavedToANewFileWithoutOverwritePrompt(
            @org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        String raw = "# field run\r\nresult: ready\r\n";
        FieldScienceExportSummary row = summary(17L, "Scout report", raw);
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(row), 0, false, 1));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(row, raw)));
        Path destination = dir.resolve("new-export.txt");
        SwingFileHarness files = new SwingFileHarness().saveAs(
                "Save selected field science text", "field-science-export-17.txt",
                destination.toFile());
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM), host,
                service, new SwingDialogHarness(), files);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JButton save = named(root, "fieldScience.exports.saveSelected", JButton.class);
        await(save::isEnabled);
        onEdt((Runnable) save::doClick);
        await(() -> host.lastStatus != null
                && host.lastStatus.contains("Saved selected field science text"));
        assertThat(Files.readAllBytes(destination)).isEqualTo(raw.getBytes(StandardCharsets.UTF_8));
        files.assertComplete();
    }

    @Test
    void cancelledFileChoiceKeepsExistingFile(
            @org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        String raw = "reviewed text";
        FieldScienceExportSummary row = summary(17L, "Scout report", raw);
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(row), 0, false, 1));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(row, raw)));
        Path existing = dir.resolve("reviewed-export.txt");
        Files.writeString(existing, "previous text", StandardCharsets.UTF_8);
        SwingFileHarness files = new SwingFileHarness().cancelSave(
                "Save selected field science text", "field-science-export-17.txt");
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM), host,
                fileName -> Path.of(fileName), page -> { }, service,
                new SwingDialogHarness(), parent -> Optional.empty(), files);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JButton save = named(root, "fieldScience.exports.saveSelected", JButton.class);
        await(save::isEnabled);
        onEdt((Runnable) save::doClick);
        assertThat(host.lastStatus).contains("cancelled", "No file was changed");
        assertThat(Files.readString(existing)).isEqualTo("previous text");
        files.assertComplete();
    }

    @Test
    void declinedOverwriteKeepsExistingFile(
            @org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        String raw = "reviewed text";
        FieldScienceExportSummary row = summary(17L, "Scout report", raw);
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(row), 0, false, 1));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(row, raw)));
        Path existing = dir.resolve("reviewed-export.txt");
        Files.writeString(existing, "previous text", StandardCharsets.UTF_8);
        SwingFileHarness files = new SwingFileHarness().saveAs(
                "Save selected field science text", "field-science-export-17.txt",
                existing.toFile());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirm("Replace existing export?", false);
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM), host,
                fileName -> Path.of(fileName), page -> { }, service, dialogs,
                parent -> Optional.empty(), files);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JButton save = named(root, "fieldScience.exports.saveSelected", JButton.class);
        await(save::isEnabled);
        onEdt((Runnable) save::doClick);
        assertThat(host.lastStatus).contains("cancelled", "Existing file was kept");
        assertThat(Files.readString(existing)).isEqualTo("previous text");
        files.assertComplete();
        dialogs.assertComplete();
    }

    @Test
    void failedFileWriteKeepsExistingFileAndBlocksExitWhilePending(
            @org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        String raw = "reviewed text";
        FieldScienceExportSummary row = summary(17L, "Scout report", raw);
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(row), 0, false, 1));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(row, raw)));
        Path existing = dir.resolve("reviewed-export.txt");
        Files.writeString(existing, "previous text", StandardCharsets.UTF_8);
        SwingFileHarness files = new SwingFileHarness().saveAs(
                "Save selected field science text", "field-science-export-17.txt",
                existing.toFile());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirm("Replace existing export?", true);
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM), host,
                fileName -> Path.of(fileName), page -> { }, service, dialogs,
                parent -> Optional.empty(), files, (destination, bytes, allowReplace) -> {
                    writerStarted.countDown();
                    try {
                        if (!releaseWriter.await(5, TimeUnit.SECONDS)) {
                            throw new IOException("writer timed out");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("writer interrupted", exception);
                    }
                    throw new IOException("disk full");
                });
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JButton save = named(root, "fieldScience.exports.saveSelected", JButton.class);
        await(save::isEnabled);
        onEdt((Runnable) save::doClick);
        assertThat(writerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(tab.hasPendingSave()).isTrue();
        assertThat(tab.applicationExitWarning()).contains("text file is being written");
        assertThat(save.isEnabled()).isFalse();
        releaseWriter.countDown();
        await(() -> host.lastStatus != null
                && host.lastStatus.contains("Could not save selected text"));
        assertThat(Files.readString(existing)).isEqualTo("previous text");
        assertThat(tab.hasPendingSave()).isFalse();
        files.assertComplete();
        dialogs.assertComplete();
    }

    @Test
    void changedArchiveRowCannotEnableSavingStaleLoadedText() throws Exception {
        String raw = "reviewed text";
        FieldScienceExportSummary row = summary(17L, "Scout report", raw);
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(row), 0, false, 1));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(
                summary(18L, "Scout report", raw), raw)));
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM),
                new RecordingHost(), fileName -> Path.of(fileName), page -> { }, service,
                new SwingDialogHarness(), parent -> Optional.empty(),
                new SwingFileHarness().cancelUnscripted());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JTextArea metadata = named(root, "fieldScience.exports.metadata", JTextArea.class);
        await(() -> metadata.getText().contains("Saved export details changed"));
        assertThat(named(root, "fieldScience.exports.saveSelected", JButton.class)
                .isEnabled()).isFalse();
        assertThat(named(root, "fieldScience.exports.recordFinding", JButton.class)
                .isEnabled()).isFalse();
        assertThat(named(root, "fieldScience.exports.detail", JTextArea.class).getText())
                .isEmpty();
    }

    @Test
    void viewerCanSearchAndNavigatePastTheFirstTwoHundredMatches() throws Exception {
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.find(anyLong())).thenReturn(Optional.empty());
        List<FieldScienceExportSummary> newest = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            newest.add(summary(index + 1L, "Soil export " + (index + 1)));
        }
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(summary(999L, "Latest export")), 0, false, 1));
        when(service.page("soil", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                newest, 0, true, 201));
        when(service.page("soil", 1)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(summary(201L, "Oldest soil export")), 1, false, 201));
        when(service.find(201L)).thenReturn(Optional.of(savedExport(
                summary(201L, "Oldest soil export"), "Oldest reviewed text")));
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM),
                new RecordingHost(), fileName -> Path.of(fileName), page -> { }, service,
                new SwingDialogHarness(), parent -> Optional.empty());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> tab.applyRolePermissions(false));
        onEdt((Runnable) tab::initialize);
        JTable table = named(root, "fieldScience.exports.table", JTable.class);
        JLabel pageStatus = named(root, "fieldScience.exports.pageStatus", JLabel.class);
        JButton next = named(root, "fieldScience.exports.next", JButton.class);
        JButton previous = named(root, "fieldScience.exports.previous", JButton.class);
        JTextField search = named(root, "fieldScience.exports.search", JTextField.class);
        JButton searchButton = named(root, "fieldScience.exports.searchButton", JButton.class);
        await(() -> table.getRowCount() == 1 && searchButton.isEnabled());
        onEdt(() -> search.setText("soil"));
        onEdt((Runnable) searchButton::doClick);
        await(() -> table.getRowCount() == 200 && next.isEnabled());
        assertThat(pageStatus.getText()).contains("1–200 of 201", "soil");
        onEdt((Runnable) next::doClick);
        await(() -> table.getRowCount() == 1
                && "Oldest soil export".equals(table.getValueAt(0, 1)));
        await(() -> "Oldest reviewed text".equals(named(root,
                "fieldScience.exports.detail", JTextArea.class).getText()));
        assertThat(pageStatus.getText()).contains("201–201 of 201", "soil");
        assertThat(next.isEnabled()).isFalse();
        assertThat(previous.isEnabled()).isTrue();
        onEdt((Runnable) previous::doClick);
        await(() -> table.getRowCount() == 200
                && "Soil export 1".equals(table.getValueAt(0, 1)));
        verify(service).page("soil", 1);
    }

    @Test
    void searchInputWaitsForSearchAndClearRestoresAllExports() throws Exception {
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.find(anyLong())).thenReturn(Optional.empty());
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(summary(1L, "Soil run"), summary(2L, "Scout run")),
                0, false, 2));
        when(service.page("soil", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(summary(1L, "Soil run")), 0, false, 1));
        when(service.page("scout", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(summary(2L, "Scout run")), 0, false, 1));
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM),
                new RecordingHost(), fileName -> Path.of(fileName), page -> { }, service,
                new SwingDialogHarness(), parent -> Optional.empty());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JTable table = named(root, "fieldScience.exports.table", JTable.class);
        JTextField search = named(root, "fieldScience.exports.search", JTextField.class);
        JButton searchButton = named(root, "fieldScience.exports.searchButton", JButton.class);
        JButton clear = named(root, "fieldScience.exports.clearSearch", JButton.class);
        await(() -> table.getRowCount() == 2 && searchButton.isEnabled());
        onEdt(() -> search.setText("soil"));
        assertThat(table.getRowCount()).isEqualTo(2);
        onEdt((Runnable) searchButton::doClick);
        await(() -> table.getRowCount() == 1
                && "Soil run".equals(table.getValueAt(0, 1)));
        onEdt(() -> search.setText("scout"));
        assertThat(table.getValueAt(0, 1)).isEqualTo("Soil run");
        onEdt((Runnable) search::postActionEvent);
        await(() -> "Scout run".equals(table.getValueAt(0, 1)));
        onEdt((Runnable) clear::doClick);
        await(() -> table.getRowCount() == 2 && search.getText().isEmpty());
    }

    @Test
    void failedSearchKeepsPreviousResultsAndSelectedText() throws Exception {
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        String raw = "# saved scout export\ndata: field day\n";
        FieldScienceExportSummary savedSummary = summary(17L, "Scout run", raw);
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(savedSummary), 0, false, 1));
        when(service.page("broken", 0)).thenThrow(new IllegalStateException("database busy"));
        when(service.find(17L)).thenReturn(Optional.of(savedExport(savedSummary, raw)));
        RecordingHost host = new RecordingHost();
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM),
                host, fileName -> Path.of(fileName), page -> { }, service,
                new SwingDialogHarness(), parent -> Optional.empty());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JTable table = named(root, "fieldScience.exports.table", JTable.class);
        JTextArea detail = named(root, "fieldScience.exports.detail", JTextArea.class);
        JTextField search = named(root, "fieldScience.exports.search", JTextField.class);
        JLabel pageStatus = named(root, "fieldScience.exports.pageStatus", JLabel.class);
        await(() -> table.getRowCount() == 1 && raw.equals(detail.getText()));
        onEdt(() -> search.setText("broken"));
        onEdt((Runnable) () -> named(root, "fieldScience.exports.searchButton",
                JButton.class).doClick());
        await(() -> host.lastStatus != null && host.lastStatus.contains("Previous results"));
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 1)).isEqualTo("Scout run");
        assertThat(detail.getText()).isEqualTo(raw);
        assertThat(pageStatus.getText()).contains("1–1 of 1");
        assertThat(search.getText()).isEqualTo("broken");
    }

    @Test
    void archiveIsAvailableWhenTheFarmHasNoMatchingOfflineTools() throws Exception {
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        when(service.find(anyLong())).thenReturn(Optional.empty());
        when(service.page("", 0)).thenReturn(new FieldScienceExportService.ExportPage(
                List.of(summary(17L, "Earlier farm export")), 0, false, 1));
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.FLOWER_SHOP),
                new RecordingHost(), service);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) tab::initialize);
        JTable table = named(root, "fieldScience.exports.table", JTable.class);
        await(() -> table.getRowCount() == 1);
        assertThat(named(root, "fieldScience.noTools.section", JComponent.class))
                .isNotNull();
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains("No matching offline tools")
                .anySatisfy(text -> assertThat(text).contains(
                        "Browse saved exports; matching offline tools appear"));
        assertThat(descendants(root, JButton.class).stream()
                .filter(button -> button.getName() != null
                        && button.getName().startsWith("fieldScience.open.")))
                .isEmpty();
    }

    private static FieldScienceExportSummary summary(long id, String title) {
        return summary(id, title, "Oldest reviewed text");
    }

    private static FieldScienceExportSummary summary(long id, String title, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new FieldScienceExportSummary(id, Instant.parse("2026-10-03T00:00:00Z"),
                "farm-scout", "Farm Scout", "https://example.org/scout",
                FieldScienceBundle.SOURCE_COMMIT, title, null, "", bytes.length, sha256(bytes));
    }

    @Test
    void droppedEcoFileStagesTheFullDraftWithoutSaving(@org.junit.jupiter.api.io.TempDir Path dir)
            throws Exception {
        Path source = dir.resolve("field-day.eco");
        Files.writeString(source, "# field day\ndata: potatoes\n");
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        DropInspectDialogs dialogs = new DropInspectDialogs(fileTransfer(source.toFile()));
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM),
                new RecordingHost(), fileName -> Path.of(fileName), page -> { }, service,
                dialogs, parent -> Optional.empty());
        dialogs.tab = tab;
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) () -> named(root, "fieldScience.save.farm-scout",
                JButton.class).doClick());
        assertThat(dialogs.dropAccepted).isTrue();
        assertThat(dialogs.draftText).isEqualTo("# field day\ndata: potatoes\n");
        assertThat(dialogs.filename).isEqualTo("Source file: field-day.eco");
        verify(service, never()).save(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void malformedEcoFileCannotReplaceTheDraft(@org.junit.jupiter.api.io.TempDir Path dir)
            throws Exception {
        Path source = dir.resolve("broken.eco");
        Files.write(source, new byte[]{(byte) 0xc3, (byte) 0x28});
        FieldScienceExportService service = mock(FieldScienceExportService.class);
        DropInspectDialogs dialogs = new DropInspectDialogs(fileTransfer(source.toFile()));
        FieldScienceTab tab = new FieldScienceTab(Set.of(FarmWorkspace.POTATO_FARM),
                new RecordingHost(), fileName -> Path.of(fileName), page -> { }, service,
                dialogs, parent -> Optional.empty());
        dialogs.tab = tab;
        JComponent root = onEdt(tab::getUIComponent);
        onEdt((Runnable) () -> named(root, "fieldScience.save.farm-scout",
                JButton.class).doClick());
        assertThat(dialogs.dropAccepted).isFalse();
        assertThat(dialogs.draftText).isEmpty();
        verify(service, never()).save(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    private static FieldScienceExport savedExport(String content, String filename) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new FieldScienceExport(Instant.parse("2026-10-03T00:00:00Z"),
                "farm-scout", "Farm Scout", "https://example.org/scout",
                FieldScienceBundle.SOURCE_COMMIT, "Scout report", filename, "Field 1",
                bytes.length, sha256(bytes), content);
    }

    private static FieldScienceExport savedExport(FieldScienceExportSummary summary,
                                                  String content) {
        return withId(summary.id(), new FieldScienceExport(summary.createdAt(),
                summary.instrumentId(), summary.instrumentTitle(), summary.sourceUrl(),
                summary.sourceCommit(), summary.title(), summary.filename(), summary.note(),
                summary.byteLength(), summary.sha256(), content));
    }

    private static FieldScienceExport withId(long id, FieldScienceExport saved) {
        try {
            java.lang.reflect.Field field = FieldScienceExport.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(saved, id);
            return saved;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not create a saved export fixture", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 unavailable", exception);
        }
    }

    private static Transferable fileTransfer(File file) {
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.javaFileListFlavor};
            }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.javaFileListFlavor.equals(flavor);
            }
            @Override public Object getTransferData(DataFlavor flavor)
                    throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return List.of(file);
            }
        };
    }

    private static final class DropInspectDialogs implements FarmDialogs {
        private final Transferable dropped;
        private FieldScienceTab tab;
        private boolean dropAccepted;
        private String draftText;
        private String filename;

        private DropInspectDialogs(Transferable dropped) { this.dropped = dropped; }

        @Override public int showOption(Component parent, Object message, String title,
                int optionType, int messageType, javax.swing.Icon icon,
                Object[] options, Object initialValue) {
            dropAccepted = tab.importExportDrop(dropped);
            Component root = (Component) message;
            draftText = named(root, "fieldScience.exportDraft.content", JTextArea.class)
                    .getText();
            filename = named(root, "fieldScience.exportDraft.filename", JLabel.class)
                    .getText();
            return JOptionPane.CLOSED_OPTION;
        }

        @Override public int showConfirm(Component parent, Object message, String title,
                int optionType, int messageType) { throw new AssertionError(title); }

        @Override public void showMessage(Component parent, Object message, String title,
                int messageType) { throw new AssertionError(title); }
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        return descendants(root, type).stream()
                .filter(component -> name.equals(component.getName()))
                .findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(descendants(child, type));
            }
        }
        return found;
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.call();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw new IllegalStateException(failure.get());
        }
        return result.get();
    }

    private static void onEdt(Runnable action) throws Exception {
        onEdt(() -> {
            action.run();
            return null;
        });
    }

    private static final class RecordingHost implements TabHost {
        private volatile String lastStatus;
        private final List<String> selected = new ArrayList<>();
        private final List<FindingRequest> findingRequests = new ArrayList<>();
        private final List<FieldScienceExportSummary> linkedFindingRequests =
                new ArrayList<>();

        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { selected.add(tabTitle); }
        @Override public void setStatus(String message) { lastStatus = message; }
        @Override public void runTrendAnalysis() { }
        @Override public void showFieldScienceFinding(String title, String sourceUrl) {
            findingRequests.add(new FindingRequest(title, sourceUrl));
        }
        @Override public void showFieldScienceFindingFromExport(
                FieldScienceExportSummary export) {
            linkedFindingRequests.add(export);
        }
    }

    private record FindingRequest(String title, String sourceUrl) { }
}
