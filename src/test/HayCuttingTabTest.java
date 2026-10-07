package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.formdev.flatlaf.FlatLightLaf;
import com.flowerfarm.model.HayCutting;
import com.flowerfarm.model.HayCuttingStage;
import com.flowerfarm.service.HayCuttingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
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

@DisplayName("Hay cutting planning notebook")
class HayCuttingTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "hay-cuttings");

    @Test
    void hayFieldsAndSharedLinksFitCompactScrollableWorkspace() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        HayCuttingService service = mock(HayCuttingService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "East slope")));
        RecordingHost host = new RecordingHost();
        HayCuttingTab tab = new HayCuttingTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Hay cuttings");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Hay cuttings");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("do not record actual yields or create harvest lots")))
                .isTrue();
        for (String name : List.of("Field name", "Forage crop", "Cutting number", "Acres",
                "Planned cut date", "Planned bale date")) {
            assertThat(field(root, name)).isNotNull();
        }
        assertThat(combo(root, "hayCutting.stage").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Cutting stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(2)).isEqualTo("Cut #");
        assertThat(table(root).getColumnName(4)).isEqualTo("Planned cut");
        assertThat(table(root).getColumnName(5)).isEqualTo("Planned bale");

        onEdt(() -> field(root, "Forage crop").setText("Alfalfa"));
        assertThat(tab.applicationExitWarning()).contains("unsaved hay cutting draft");
        onEdt(() -> {
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Forage crop").getText()).isEqualTo("Alfalfa");
        onEdt(() -> {
            field(root, "Forage crop").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });

        SwingUserHarness.on(root).layoutAt(900, 600);
        onEdt(() -> {
            JScrollPane scroll = named(root, "hayCutting.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "hayCutting.stageTargets", javax.swing.JPanel.class)
                    .getComponentCount()).isEqualTo(HayCuttingStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "hay-cuttings-compact.png");
                SwingUserHarness.on(root).layoutAt(900, 1100);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "hay-cutting-full.png");
                SwingUserHarness.on(root).layoutAt(900, 660);
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                savePreview(root, "hay-cutting-form.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsCompleteDraftAndRetrySavesIt() throws Exception {
        HayCuttingService service = mock(HayCuttingService.class);
        when(service.create(any(HayCutting.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    HayCutting saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        HayCuttingTab tab = new HayCuttingTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Field name").setText("North meadow");
            field(root, "Forage crop").setText("Alfalfa");
            field(root, "Cutting number").setText("2");
            field(root, "Acres").setText("2.50");
            field(root, "Planned cut date").setText("2026-09-01");
            field(root, "Planned bale date").setText("2026-09-20");
            combo(root, "hayCutting.stage")
                    .setSelectedItem(HayCuttingStage.CURING);
            button(root, "Save cutting").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Planned bale date").getText()).isEqualTo("2026-09-20");
        assertThat(combo(root, "hayCutting.stage").getSelectedItem())
                .isEqualTo(HayCuttingStage.CURING);
        assertThat(tab.applicationExitWarning()).contains("unsaved hay cutting draft");

        onEdt(() -> button(root, "Save cutting").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("North meadow"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<HayCutting> draft =
                org.mockito.ArgumentCaptor.forClass(HayCutting.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getForageCrop()).isEqualTo("Alfalfa");
        assertThat(draft.getValue().getCuttingNumber()).isEqualTo(2);
        assertThat(draft.getValue().getPlannedBaleDate())
                .isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(draft.getValue().getStage()).isEqualTo(HayCuttingStage.CURING);
    }

    @Test
    void invalidBaleDateAndSelectionProtectTheEditDraft() throws Exception {
        HayCuttingService service = mock(HayCuttingService.class);
        when(service.list()).thenReturn(List.of(
                saved(11L, "East slope"), saved(12L, "West slope")));
        when(service.update(eq(11L), any(HayCutting.class)))
                .thenAnswer(invocation -> {
                    HayCutting saved = invocation.getArgument(1);
                    ReflectionTestUtils.setField(saved, "id", 11L);
                    return saved;
                });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        HayCuttingTab tab = new HayCuttingTab(service,
                new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Planned cut date").setText("2026-10-01");
            field(root, "Planned bale date").setText("2026-09-01");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Field name").getText()).isEqualTo("East slope");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> button(root, "Save cutting").doClick());
        assertThat(feedback(root)).contains("Planned bale date cannot precede cut date");
        verify(service, never()).update(any(), any());

        onEdt(() -> {
            field(root, "Planned bale date").setText("2026-10-15");
            button(root, "Save cutting").doClick();
        });
        await(() -> !tab.hasPendingSave());
        org.mockito.ArgumentCaptor<HayCutting> draft =
                org.mockito.ArgumentCaptor.forClass(HayCutting.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getPlannedCutDate())
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(draft.getValue().getPlannedBaleDate())
                .isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void planNextCutKeepsConflictingDraftAndRequiresNewCutDate() throws Exception {
        HayCuttingService service = mock(HayCuttingService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North meadow")));
        when(service.create(any(HayCutting.class))).thenAnswer(invocation -> {
            HayCutting result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 22L);
            return result;
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        RecordingHost host = new RecordingHost();
        HayCuttingTab tab = new HayCuttingTab(service, host, dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Field name").setText("Unfinished edit");
            button(root, "Plan next cut").doClick();
        });
        assertThat(field(root, "Field name").getText()).isEqualTo("Unfinished edit");
        assertThat(field(root, "Cutting number").getText()).isEqualTo("1");
        assertThat(table(root).getSelectedRow()).isZero();
        verify(service, never()).create(any());

        onEdt(() -> {
            field(root, "Field name").setText("North meadow");
            button(root, "Plan next cut").doClick();
        });
        assertThat(field(root, "Field name").getText()).isEqualTo("North meadow");
        assertThat(field(root, "Forage crop").getText()).isEqualTo("Alfalfa");
        assertThat(field(root, "Cutting number").getText()).isEqualTo("2");
        assertThat(field(root, "Acres").getText()).isEqualTo("2.50");
        assertThat(field(root, "Planned cut date").getText()).isEmpty();
        assertThat(field(root, "Planned bale date").getText()).isEmpty();
        assertThat(combo(root, "hayCutting.stage").getSelectedItem())
                .isEqualTo(HayCuttingStage.PLANNED);
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved hay cutting draft");

        onEdt(() -> button(root, "Save cutting").doClick());
        assertThat(feedback(root)).contains("Enter a planned cut date");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Planned cut date").setText("2027-05-10");
            button(root, "Save cutting").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("cut 2 saved"));
        org.mockito.ArgumentCaptor<HayCutting> draft =
                org.mockito.ArgumentCaptor.forClass(HayCutting.class);
        verify(service).create(draft.capture());
        assertThat(draft.getValue().getCuttingNumber()).isEqualTo(2);
        assertThat(draft.getValue().getPlannedCutDate())
                .isEqualTo(LocalDate.of(2027, 5, 10));
    }

    @Test
    void planNextCutStopsAtNinetyNineWithoutReplacingSavedPlan() throws Exception {
        HayCuttingService service = mock(HayCuttingService.class);
        when(service.list()).thenReturn(List.of(saved(99L, "South field", 99)));
        HayCuttingTab tab = new HayCuttingTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            button(root, "Plan next cut").doClick();
        });
        assertThat(feedback(root)).contains("Cutting 99 is the last numbered cut");
        assertThat(field(root, "Cutting number").getText()).isEqualTo("99");
        assertThat(table(root).getSelectedRow()).isZero();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, never()).create(any());
    }

    @Test
    void dragChangesOnlyStageWithClickFallbackAndRejectsStaleOrDirtyPayloads()
            throws Exception {
        AtomicReference<HayCutting> stored = new AtomicReference<>(
                saved(21L, "East slope"));
        HayCuttingService service = mock(HayCuttingService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(HayCuttingStage.class)))
                .thenAnswer(invocation -> {
                    HayCutting updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        RecordingHost host = new RecordingHost();
        HayCuttingTab tab = new HayCuttingTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton cut = named(root,
                "hayCutting.stageTarget.CUT", JButton.class);
        JButton baled = named(root,
                "hayCutting.stageTarget.BALED", JButton.class);
        assertThat(cut.getToolTipText()).contains("Stage change saves immediately");
        Transferable payload = dragPayload(root);
        HayCuttingTab foreignTab = new HayCuttingTab(service, new RecordingHost());
        JComponent foreignRoot = onEdt(foreignTab::getUIComponent);
        onEdt(foreignTab::initialize);
        await(() -> table(foreignRoot).getRowCount() == 1);
        onEdt(() -> table(foreignRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(foreignRoot);
        assertThat(onEdt(() -> cut.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(cut, foreign)))).isFalse();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(cut, payload);
        assertThat(onEdt(() -> cut.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> cut.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == HayCuttingStage.CUT);
        assertThat(stored.get().getForageCrop()).isEqualTo("Alfalfa");
        assertThat(stored.get().getAcres()).isEqualByComparingTo("2.50");
        assertThat(host.lastStatus).contains("moved to Cut");

        await(() -> baled.isEnabled()
                && "Cut".equals(table(root).getValueAt(0, 6)));
        onEdt((Runnable) baled::doClick);
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == HayCuttingStage.BALED);
        verify(service, times(2)).updateStage(eq(21L), any(HayCuttingStage.class));

        await(() -> "Baled".equals(table(root).getValueAt(0, 6))
                && button(root, "Refresh cuttings").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) >= 3
                && button(root, "Refresh cuttings").isEnabled());
        assertThat(onEdt(() -> cut.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(cut, stale)))).isFalse();
        Transferable current = dragPayload(root);
        onEdt(() -> field(root, "Field name").setText("Unsaved name"));
        assertThat(onEdt(() -> cut.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(cut, current)))).isFalse();
        assertThat(field(root, "Field name").getText()).isEqualTo("Unsaved name");
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(cut.isEnabled()).isFalse();
        assertThat(onEdt(() -> cut.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(cut, current)))).isFalse();
    }

    @Test
    void viewerCanReviewButCannotSaveOrMoveCuttings() throws Exception {
        HayCuttingService service = mock(HayCuttingService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "East slope")));
        HayCuttingTab tab = new HayCuttingTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            tab.applyRolePermissions(false);
            tab.initialize();
        });
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(field(root, "Field name").getText()).isEqualTo("East slope");
        assertThat(field(root, "Field name").isEnabled()).isFalse();
        assertThat(button(root, "Save cutting").isEnabled()).isFalse();
        assertThat(button(root, "New hay cutting").isEnabled()).isFalse();
        assertThat(button(root, "Plan next cut").isEnabled()).isFalse();
        assertThat(button(root, "Refresh cuttings").isEnabled()).isTrue();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, never()).create(any());
        verify(service, never()).updateStage(anyLong(), any());
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("hay-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static HayCutting saved(Long id, String field) {
        return saved(id, field, 1);
    }

    private static HayCutting saved(Long id, String field, int cuttingNumber) {
        HayCutting plan = new HayCutting(field, "Alfalfa", cuttingNumber,
                new BigDecimal("2.50"), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 20), HayCuttingStage.PLANNED, "Planning note");
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
        return named(root, "hayCutting.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
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
        return named(root, "hayCutting.cuttingTable", JTable.class);
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
