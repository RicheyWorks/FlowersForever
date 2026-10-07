package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.NutBlockPlan;
import com.flowerfarm.model.NutBlockStage;
import com.flowerfarm.service.NutBlockPlanService;
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

@DisplayName("Tree nut block planning")
class NutBlockPlanTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "nut-blocks");

    @Test
    void nutBlocksShowScopeSharedLinksStagesAndScrollableForm() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        NutBlockPlanService service = mock(NutBlockPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "South orchard")));
        RecordingHost host = new RecordingHost();
        NutBlockPlanTab tab = new NutBlockPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Nut blocks");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Nut blocks");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("do not record actual yields"))).isTrue();
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("compatibility is not checked"))).isTrue();
        for (String name : List.of("Block name", "Nut crop", "Variety",
                "Pollinizer variety", "Acres", "Tree count", "Intended destination",
                "Planned bloom start", "Planned floor prep", "Planned harvest start",
                "Planned harvest end")) {
            assertThat(field(root, name)).isNotNull();
        }
        assertThat(combo(root, "nutBlock.stage").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Planning stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(4)).isEqualTo("Trees");

        onEdt(() -> {
            field(root, "Nut crop").setText("Walnut");
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Nut crop").getText()).isEqualTo("Walnut");
        assertThat(tab.applicationExitWarning()).contains("unsaved nut block plan draft");
        onEdt(() -> {
            field(root, "Nut crop").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });
        assertThat(field(root, "Block name").getText()).isEqualTo("South orchard");

        SwingUserHarness.on(root).layoutAt(920, 650);
        onEdt(() -> {
            JScrollPane scroll = named(root, "nutBlock.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "nutBlock.stageTargets", JPanel.class)
                    .getComponentCount()).isEqualTo(NutBlockStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "nut-blocks-compact.png");
                SwingUserHarness.on(root).layoutAt(920, 1450);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "nut-blocks-full.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsAllOrchardDetailsAndRetrySavesThem() throws Exception {
        NutBlockPlanService service = mock(NutBlockPlanService.class);
        when(service.create(any(NutBlockPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    NutBlockPlan saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        NutBlockPlanTab tab = new NutBlockPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Block name").setText("South orchard");
            field(root, "Nut crop").setText("Hazelnut");
            field(root, "Variety").setText("Yamhill");
            field(root, "Pollinizer variety").setText("Wepster");
            field(root, "Acres").setText("3.25");
            field(root, "Tree count").setText("240");
            field(root, "Intended destination").setText("Processor");
            field(root, "Planned bloom start").setText("2027-02-01");
            field(root, "Planned floor prep").setText("2027-08-15");
            field(root, "Planned harvest start").setText("2027-09-01");
            field(root, "Planned harvest end").setText("2027-09-20");
            combo(root, "nutBlock.stage").setSelectedItem(NutBlockStage.FLOOR_PREP);
            notes(root).setText("Check orchard floor access");
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Pollinizer variety").getText()).isEqualTo("Wepster");
        assertThat(field(root, "Planned floor prep").getText()).isEqualTo("2027-08-15");
        assertThat(tab.applicationExitWarning()).contains("unsaved nut block plan draft");

        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("South orchard"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<NutBlockPlan> draft =
                org.mockito.ArgumentCaptor.forClass(NutBlockPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getNutCrop()).isEqualTo("Hazelnut");
        assertThat(draft.getValue().getVariety()).isEqualTo("Yamhill");
        assertThat(draft.getValue().getPollinizerVariety()).isEqualTo("Wepster");
        assertThat(draft.getValue().getAcres()).isEqualByComparingTo("3.25");
        assertThat(draft.getValue().getTreeCount()).isEqualTo(240);
        assertThat(draft.getValue().getPlannedBloomStart())
                .isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(draft.getValue().getPlannedFloorPrepDate())
                .isEqualTo(LocalDate.of(2027, 8, 15));
        assertThat(draft.getValue().getPlannedHarvestEnd())
                .isEqualTo(LocalDate.of(2027, 9, 20));
        assertThat(draft.getValue().getIntendedDestination()).isEqualTo("Processor");
        assertThat(draft.getValue().getStage()).isEqualTo(NutBlockStage.FLOOR_PREP);
        assertThat(draft.getValue().getNotes()).isEqualTo("Check orchard floor access");
    }

    @Test
    void invalidDatesAndSelectionProtectDraftThenSavedEditsPersist() throws Exception {
        AtomicReference<NutBlockPlan> stored = new AtomicReference<>(
                saved(11L, "South orchard"));
        NutBlockPlanService service = mock(NutBlockPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get(),
                saved(12L, "North orchard")));
        when(service.update(eq(11L), any(NutBlockPlan.class))).thenAnswer(invocation -> {
            NutBlockPlan updated = invocation.getArgument(1);
            ReflectionTestUtils.setField(updated, "id", 11L);
            stored.set(updated.snapshot());
            return updated.snapshot();
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        NutBlockPlanTab tab = new NutBlockPlanTab(service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Planned harvest start").setText("2027-10-01");
            field(root, "Planned harvest end").setText("2027-09-01");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Block name").getText()).isEqualTo("South orchard");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Planned harvest end cannot precede start");
        verify(service, never()).update(any(), any());

        onEdt(() -> {
            field(root, "Planned harvest end").setText("2027-10-15");
            field(root, "Planned bloom start").setText("2027-10-10");
            field(root, "Tree count").setText("0");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Tree count must be greater than zero");
        onEdt(() -> {
            field(root, "Tree count").setText("245");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned bloom start cannot be after planned harvest start");
        onEdt(() -> {
            field(root, "Planned bloom start").setText("2027-02-10");
            field(root, "Planned floor prep").setText("2027-10-10");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned floor prep cannot be after planned harvest start");
        onEdt(() -> {
            field(root, "Planned floor prep").setText("2027-08-20");
            field(root, "Pollinizer variety").setText("Updated pollinizer");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave()
                && "Updated pollinizer".equals(stored.get().getPollinizerVariety()));
        assertThat(stored.get().getTreeCount()).isEqualTo(245);
        assertThat(stored.get().getPlannedFloorPrepDate())
                .isEqualTo(LocalDate.of(2027, 8, 20));
        verify(service).update(eq(11L), any(NutBlockPlan.class));
        verify(service, never()).create(any());
    }

    @Test
    void nextSeasonCopiesOrchardIdentityAndClearsSeasonDetails() throws Exception {
        NutBlockPlanService service = mock(NutBlockPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "South orchard")));
        when(service.create(any(NutBlockPlan.class))).thenAnswer(invocation -> {
            NutBlockPlan result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 22L);
            return result;
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        RecordingHost host = new RecordingHost();
        NutBlockPlanTab tab = new NutBlockPlanTab(service, host, dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Block name").setText("Unfinished edit");
            button(root, "Plan next season").doClick();
        });
        assertThat(field(root, "Block name").getText()).isEqualTo("Unfinished edit");
        assertThat(table(root).getSelectedRow()).isZero();
        verify(service, never()).create(any());

        onEdt(() -> {
            field(root, "Block name").setText("South orchard");
            button(root, "Plan next season").doClick();
        });
        assertThat(field(root, "Block name").getText()).isEqualTo("South orchard");
        assertThat(field(root, "Nut crop").getText()).isEqualTo("Hazelnut");
        assertThat(field(root, "Variety").getText()).isEqualTo("Yamhill");
        assertThat(field(root, "Pollinizer variety").getText()).isEqualTo("Wepster");
        assertThat(field(root, "Acres").getText()).isEqualTo("3.25");
        assertThat(field(root, "Tree count").getText()).isEqualTo("240");
        assertThat(field(root, "Intended destination").getText()).isEqualTo("Processor");
        assertThat(field(root, "Planned bloom start").getText()).isEmpty();
        assertThat(field(root, "Planned floor prep").getText()).isEmpty();
        assertThat(field(root, "Planned harvest start").getText()).isEmpty();
        assertThat(field(root, "Planned harvest end").getText()).isEmpty();
        assertThat(combo(root, "nutBlock.stage").getSelectedItem())
                .isEqualTo(NutBlockStage.PLANNED);
        assertThat(notes(root).getText()).isEmpty();
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved nut block plan draft");

        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a planned harvest start date");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Planned harvest start").setText("2028-09-10");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<NutBlockPlan> draft =
                org.mockito.ArgumentCaptor.forClass(NutBlockPlan.class);
        verify(service).create(draft.capture());
        assertThat(draft.getValue().getBlockName()).isEqualTo("South orchard");
        assertThat(draft.getValue().getNutCrop()).isEqualTo("Hazelnut");
        assertThat(draft.getValue().getPollinizerVariety()).isEqualTo("Wepster");
        assertThat(draft.getValue().getTreeCount()).isEqualTo(240);
        assertThat(draft.getValue().getPlannedBloomStart()).isNull();
        assertThat(draft.getValue().getPlannedFloorPrepDate()).isNull();
        assertThat(draft.getValue().getPlannedHarvestStart())
                .isEqualTo(LocalDate.of(2028, 9, 10));
    }

    @Test
    void stageDragAndClickRejectForeignStaleDirtyAndViewerActions() throws Exception {
        AtomicReference<NutBlockPlan> stored = new AtomicReference<>(
                saved(21L, "South orchard"));
        NutBlockPlanService service = mock(NutBlockPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(NutBlockStage.class)))
                .thenAnswer(invocation -> {
                    NutBlockPlan updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        RecordingHost host = new RecordingHost();
        NutBlockPlanTab tab = new NutBlockPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton pollination = named(root,
                "nutBlock.stageTarget.POLLINATION", JButton.class);
        JButton floorPrep = named(root,
                "nutBlock.stageTarget.FLOOR_PREP", JButton.class);
        assertThat(pollination.getToolTipText()).contains("Stage change saves immediately");
        Transferable payload = dragPayload(root);
        NutBlockPlanTab foreignTab = new NutBlockPlanTab(service, new RecordingHost());
        JComponent foreignRoot = onEdt(foreignTab::getUIComponent);
        onEdt(foreignTab::initialize);
        await(() -> table(foreignRoot).getRowCount() == 1);
        onEdt(() -> table(foreignRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(foreignRoot);
        assertThat(onEdt(() -> pollination.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(pollination, foreign)))).isFalse();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(pollination, payload);
        assertThat(onEdt(() -> pollination.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> pollination.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == NutBlockStage.POLLINATION);
        assertThat(stored.get().getNutCrop()).isEqualTo("Hazelnut");
        assertThat(stored.get().getTreeCount()).isEqualTo(240);
        assertThat(host.lastStatus).contains("moved to Pollination");

        await(() -> floorPrep.isEnabled()
                && "Pollination".equals(table(root).getValueAt(0, 6)));
        onEdt((Runnable) floorPrep::doClick);
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == NutBlockStage.FLOOR_PREP);
        verify(service, times(2)).updateStage(eq(21L), any(NutBlockStage.class));

        await(() -> "Floor prep".equals(table(root).getValueAt(0, 6))
                && button(root, "Refresh blocks").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        long revision = (Long) ReflectionTestUtils.getField(tab, "listRevision");
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) > revision
                && button(root, "Refresh blocks").isEnabled());
        assertThat(onEdt(() -> pollination.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(pollination, stale)))).isFalse();
        Transferable current = dragPayload(root);
        onEdt(() -> field(root, "Block name").setText("Unsaved block name"));
        assertThat(onEdt(() -> pollination.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(pollination, current)))).isFalse();
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(pollination.isEnabled()).isFalse();
        assertThat(onEdt(() -> pollination.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(pollination, current)))).isFalse();
    }

    @Test
    void viewerCanReviewButCannotSaveMoveOrPlanNextSeason() throws Exception {
        NutBlockPlanService service = mock(NutBlockPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "South orchard")));
        NutBlockPlanTab tab = new NutBlockPlanTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            tab.applyRolePermissions(false);
            tab.initialize();
        });
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(field(root, "Block name").getText()).isEqualTo("South orchard");
        assertThat(field(root, "Block name").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "New nut block plan").isEnabled()).isFalse();
        assertThat(button(root, "Plan next season").isEnabled()).isFalse();
        assertThat(button(root, "Refresh blocks").isEnabled()).isTrue();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, never()).create(any());
        verify(service, never()).updateStage(anyLong(), any());
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("nut-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static NutBlockPlan saved(Long id, String blockName) {
        NutBlockPlan plan = new NutBlockPlan(blockName, "Hazelnut",
                "Yamhill", "Wepster", new BigDecimal("3.25"), 240,
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 8, 15),
                LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 20),
                "Processor", NutBlockStage.PLANNED, "Check orchard floor");
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
        return named(root, "nutBlock.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea notes(Component root) {
        return named(root, "nutBlock.notes", JTextArea.class);
    }

    private static JComboBox<?> combo(Component root, String name) {
        return named(root, name, JComboBox.class);
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "nutBlock.blockTable", JTable.class);
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
            for (Component child : container.getComponents()) {
                found.addAll(descendants(child, type));
            }
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
