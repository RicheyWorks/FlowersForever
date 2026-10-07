package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.ChristmasTreeBlockPlan;
import com.flowerfarm.model.ChristmasTreeBlockStage;
import com.flowerfarm.service.ChristmasTreeBlockPlanService;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Christmas tree block planning")
class ChristmasTreeBlockTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "tree-blocks");

    @Test
    void screenShowsEditableTreePlanningScopeAndSharedTools() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        ChristmasTreeBlockPlanService service = mock(ChristmasTreeBlockPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North slope")));
        RecordingHost host = new RecordingHost();
        ChristmasTreeBlockTab tab = new ChristmasTreeBlockTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Tree blocks");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Tree blocks");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("do not record actual cut counts"))).isTrue();
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("Licensing, inspection, and export"))).isTrue();
        for (String name : List.of("Block name", "Tree species", "Planting year",
                "Acres", "Planted tree count", "Target height", "Next shearing date",
                "Target harvest year", "Planned cut start", "Planned cut end",
                "Intended channel")) assertThat(field(root, name)).isNotNull();
        assertThat(combo(root).getAccessibleContext().getAccessibleName())
                .isEqualTo("Planning stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(4)).isEqualTo("Target harvest");
        onEdt(() -> {
            field(root, "Tree species").setText("Noble fir");
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Tree species").getText()).isEqualTo("Noble fir");
        assertThat(tab.applicationExitWarning()).contains("unsaved Christmas tree block plan draft");
        onEdt(() -> {
            field(root, "Tree species").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });

        SwingUserHarness.on(root).layoutAt(920, 1420);
        onEdt(() -> {
            JScrollPane scroll = named(root, "christmasTreeBlock.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "christmasTreeBlock.stageTargets", JPanel.class)
                    .getComponentCount()).isEqualTo(ChristmasTreeBlockStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "tree-blocks-full.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void validatesTreeDatesAndFailedSaveKeepsCompleteDraftForRetry() throws Exception {
        ChristmasTreeBlockPlanService service = mock(ChristmasTreeBlockPlanService.class);
        when(service.create(any(ChristmasTreeBlockPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    ChristmasTreeBlockPlan result = invocation.getArgument(0);
                    ReflectionTestUtils.setField(result, "id", 7L);
                    return result;
                });
        RecordingHost host = new RecordingHost();
        ChristmasTreeBlockTab tab = new ChristmasTreeBlockTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a block name");
        onEdt(() -> {
            field(root, "Block name").setText("West ridge");
            field(root, "Tree species").setText("Douglas fir");
            field(root, "Planting year").setText("2024");
            field(root, "Acres").setText("2.50");
            field(root, "Planted tree count").setText("1200");
            field(root, "Target height").setText("6–7 ft");
            field(root, "Next shearing date").setText("2028-07-15");
            field(root, "Target harvest year").setText("2031");
            field(root, "Planned cut start").setText("2030-11-25");
            field(root, "Planned cut end").setText("2031-12-10");
            field(root, "Intended channel").setText("Choose and cut");
            combo(root).setSelectedItem(ChristmasTreeBlockStage.SHEARING);
            notes(root).setText("Check access road");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned cut start must fall in the target harvest year");
        onEdt(() -> {
            field(root, "Planned cut start").setText("");
            field(root, "Planned cut end").setText("");
            field(root, "Next shearing date").setText("2032-07-15");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Next shearing date cannot be after the target harvest year");
        onEdt(() -> {
            field(root, "Next shearing date").setText("2028-07-15");
            field(root, "Planned cut start").setText("2031-11-25");
            field(root, "Planned cut end").setText("2031-12-10");
            field(root, "Acres").setText("2.555");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("at most two decimal places");
        onEdt(() -> {
            field(root, "Acres").setText("2.50");
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Planted tree count").getText()).isEqualTo("1200");
        assertThat(tab.applicationExitWarning()).contains("unsaved Christmas tree block plan draft");
        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("West ridge"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<ChristmasTreeBlockPlan> draft =
                org.mockito.ArgumentCaptor.forClass(ChristmasTreeBlockPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getTreeSpecies()).isEqualTo("Douglas fir");
        assertThat(draft.getValue().getPlantingYear()).isEqualTo(2024);
        assertThat(draft.getValue().getAcres()).isEqualByComparingTo("2.50");
        assertThat(draft.getValue().getPlantedTreeCount()).isEqualTo(1200);
        assertThat(draft.getValue().getTargetHeight()).isEqualTo("6–7 ft");
        assertThat(draft.getValue().getNextShearingDate())
                .isEqualTo(LocalDate.of(2028, 7, 15));
        assertThat(draft.getValue().getTargetHarvestYear()).isEqualTo(2031);
        assertThat(draft.getValue().getPlannedCutEnd())
                .isEqualTo(LocalDate.of(2031, 12, 10));
        assertThat(draft.getValue().getIntendedChannel()).isEqualTo("Choose and cut");
        assertThat(draft.getValue().getStage()).isEqualTo(ChristmasTreeBlockStage.SHEARING);
        assertThat(draft.getValue().getNotes()).isEqualTo("Check access road");
    }

    @Test
    void replacementDraftCarriesStableDetailsAndProtectsUnsavedEdits() throws Exception {
        ChristmasTreeBlockPlanService service = mock(ChristmasTreeBlockPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North slope")));
        when(service.create(any(ChristmasTreeBlockPlan.class))).thenAnswer(invocation -> {
            ChristmasTreeBlockPlan result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 22L);
            return result;
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        RecordingHost host = new RecordingHost();
        ChristmasTreeBlockTab tab = new ChristmasTreeBlockTab(service, host, dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Block name").setText("Unfinished edit");
            button(root, "Plan replacement planting").doClick();
        });
        assertThat(field(root, "Block name").getText()).isEqualTo("Unfinished edit");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> {
            field(root, "Block name").setText("North slope");
            button(root, "Plan replacement planting").doClick();
        });
        assertThat(field(root, "Block name").getText()).isEqualTo("North slope");
        assertThat(field(root, "Tree species").getText()).isEqualTo("Douglas fir");
        assertThat(field(root, "Acres").getText()).isEqualTo("3.25");
        assertThat(field(root, "Target height").getText()).isEqualTo("6–7 ft");
        assertThat(field(root, "Intended channel").getText()).isEqualTo("Choose and cut");
        for (String name : List.of("Planting year", "Planted tree count",
                "Next shearing date", "Target harvest year", "Planned cut start",
                "Planned cut end")) assertThat(field(root, name).getText()).isEmpty();
        assertThat(combo(root).getSelectedItem()).isEqualTo(ChristmasTreeBlockStage.PLANNED);
        assertThat(notes(root).getText()).isEmpty();
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved Christmas tree block plan draft");
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a target harvest year");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Planting year").setText("2032");
            field(root, "Target harvest year").setText("2040");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<ChristmasTreeBlockPlan> draft =
                org.mockito.ArgumentCaptor.forClass(ChristmasTreeBlockPlan.class);
        verify(service).create(draft.capture());
        assertThat(draft.getValue().getId()).isEqualTo(22L);
        assertThat(draft.getValue().getBlockName()).isEqualTo("North slope");
        assertThat(draft.getValue().getPlantingYear()).isEqualTo(2032);
        assertThat(draft.getValue().getPlantedTreeCount()).isNull();
        assertThat(draft.getValue().getPlannedCutStart()).isNull();
        assertThat(draft.getValue().getTargetHarvestYear()).isEqualTo(2040);
    }

    @Test
    void stageDragAndClickRejectForeignStaleDirtyAndViewerActions() throws Exception {
        AtomicReference<ChristmasTreeBlockPlan> stored = new AtomicReference<>(
                saved(21L, "North slope"));
        ChristmasTreeBlockPlanService service = mock(ChristmasTreeBlockPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(ChristmasTreeBlockStage.class)))
                .thenAnswer(invocation -> {
                    ChristmasTreeBlockPlan updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        RecordingHost host = new RecordingHost();
        ChristmasTreeBlockTab tab = new ChristmasTreeBlockTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton growing = named(root, "christmasTreeBlock.stageTarget.GROWING", JButton.class);
        JButton shearing = named(root, "christmasTreeBlock.stageTarget.SHEARING", JButton.class);
        assertThat(growing.getToolTipText()).contains("Stage change saves immediately");
        onEdt(() -> field(root, "Block name").setText("Unsaved name"));
        assertThat(dragPayload(root)).isNull();
        onEdt(() -> field(root, "Block name").setText("North slope"));
        Transferable payload = dragPayload(root);
        ChristmasTreeBlockTab foreignTab = new ChristmasTreeBlockTab(service, new RecordingHost());
        JComponent foreignRoot = onEdt(foreignTab::getUIComponent);
        onEdt(foreignTab::initialize);
        await(() -> table(foreignRoot).getRowCount() == 1);
        onEdt(() -> table(foreignRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(foreignRoot);
        assertThat(onEdt(() -> growing.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(growing, foreign)))).isFalse();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(growing, payload);
        assertThat(onEdt(() -> growing.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> growing.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave()
                && stored.get().getStage() == ChristmasTreeBlockStage.GROWING);
        assertThat(stored.get().getPlantedTreeCount()).isEqualTo(1200);
        await(() -> "Growing".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh blocks").isEnabled());
        onEdt((Runnable) shearing::doClick);
        await(() -> !tab.hasPendingSave()
                && stored.get().getStage() == ChristmasTreeBlockStage.SHEARING);
        verify(service, times(2)).updateStage(eq(21L), any(ChristmasTreeBlockStage.class));
        await(() -> "Shearing".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh blocks").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        long revision = (Long) ReflectionTestUtils.getField(tab, "listRevision");
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) > revision
                && button(root, "Refresh blocks").isEnabled());
        assertThat(onEdt(() -> growing.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(growing, stale)))).isFalse();
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(growing.isEnabled()).isFalse();
        assertThat(button(root, "Plan replacement planting").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "Refresh blocks").isEnabled()).isTrue();
        verify(service, never()).update(any(), any());
        verify(service, never()).create(any());
        verify(service, never()).updateStage(anyLong(), eq(ChristmasTreeBlockStage.HARVEST_WINDOW));
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("tree-block-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static ChristmasTreeBlockPlan saved(Long id, String name) {
        ChristmasTreeBlockPlan plan = new ChristmasTreeBlockPlan(name, "Douglas fir",
                2024, new BigDecimal("3.25"), 1200, "6–7 ft",
                LocalDate.of(2028, 7, 15), 2031,
                LocalDate.of(2031, 11, 25), LocalDate.of(2031, 12, 10),
                "Choose and cut", ChristmasTreeBlockStage.PLANNED,
                "Check access road");
        ReflectionTestUtils.setField(plan, "id", id);
        return plan;
    }

    private static void savePreview(JComponent root, String fileName)
            throws java.io.IOException {
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { root.printAll(graphics); } finally { graphics.dispose(); }
        ImageIO.write(image, "png", PREVIEWS.resolve(fileName).toFile());
    }

    private static String feedback(Component root) {
        return named(root, "christmasTreeBlock.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea notes(Component root) {
        return named(root, "christmasTreeBlock.notes", JTextArea.class);
    }

    private static JComboBox<?> combo(Component root) {
        return named(root, "christmasTreeBlock.stage", JComboBox.class);
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "christmasTreeBlock.blockTable", JTable.class);
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        return descendants(root, type).stream()
                .filter(component -> name.equals(component.getName()))
                .findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) found.add(type.cast(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) found.addAll(descendants(child, type));
        }
        return found;
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (onEdt(condition::getAsBoolean)) return;
            Thread.sleep(15);
        }
        assertThat(onEdt(condition::getAsBoolean)).isTrue();
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(action.call()); }
            catch (Exception exception) { failure.set(exception); }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }

    private static void onEdt(Runnable action) throws Exception {
        onEdt(() -> { action.run(); return null; });
    }

    private static final class RecordingHost implements TabHost {
        private final List<String> selected = new ArrayList<>();
        private String lastStatus = "";

        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { selected.add(tabTitle); }
        @Override public void setStatus(String message) { lastStatus = message; }
        @Override public void runTrendAnalysis() { }
    }
}
