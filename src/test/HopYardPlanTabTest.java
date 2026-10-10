package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.HopYardPlan;
import com.flowerfarm.model.HopYardStage;
import com.flowerfarm.service.HopYardPlanService;
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

@DisplayName("Hop yard season planning")
class HopYardPlanTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "hop-yards");

    @Test
    void hopFieldsSharedLinksAndStagesFitScrollableWorkspace() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        HopYardPlanService service = mock(HopYardPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North yard")));
        RecordingHost host = new RecordingHost();
        HopYardPlanTab tab = new HopYardPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Hop yards");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Hop yards");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("do not record actual cone yields"))).isTrue();
        for (String name : List.of("Yard name", "Hop variety", "Acres",
                "Planned stringing date", "Planned harvest start",
                "Planned harvest end")) {
            assertThat(field(root, name)).isNotNull();
        }
        assertThat(combo(root, "hopYard.stage").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Planning stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(3)).isEqualTo("Stringing");
        assertThat(table(root).getColumnName(5)).isEqualTo("Harvest end");

        onEdt(() -> field(root, "Hop variety").setText("Centennial"));
        assertThat(tab.applicationExitWarning()).contains("unsaved hop yard plan draft");
        onEdt(() -> {
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Hop variety").getText()).isEqualTo("Centennial");
        onEdt(() -> {
            field(root, "Hop variety").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });
        assertThat(field(root, "Yard name").getText()).isEqualTo("North yard");
        assertThat(button(root, "Plan next season").isEnabled()).isTrue();

        SwingUserHarness.on(root).layoutAt(920, 630);
        onEdt(() -> {
            JScrollPane scroll = named(root, "hopYard.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "hopYard.stageTargets", JPanel.class)
                    .getComponentCount()).isEqualTo(HopYardStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "hop-yards-compact.png");
                SwingUserHarness.on(root).layoutAt(920, 1140);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "hop-yards-full.png");
                SwingUserHarness.on(root).layoutAt(920, 700);
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                savePreview(root, "hop-yard-form.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsCompleteDraftAndRetrySavesIt() throws Exception {
        HopYardPlanService service = mock(HopYardPlanService.class);
        when(service.create(any(HopYardPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    HopYardPlan saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        HopYardPlanTab tab = new HopYardPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Yard name").setText("North yard");
            field(root, "Hop variety").setText("Cascade");
            field(root, "Acres").setText("2.50");
            field(root, "Planned stringing date").setText("2027-04-15");
            field(root, "Planned harvest start").setText("2027-09-01");
            field(root, "Planned harvest end").setText("2027-09-20");
            combo(root, "hopYard.stage").setSelectedItem(HopYardStage.TRAINING);
            notes(root).setText("Keep field notes here");
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Planned harvest end").getText()).isEqualTo("2027-09-20");
        assertThat(combo(root, "hopYard.stage").getSelectedItem())
                .isEqualTo(HopYardStage.TRAINING);
        assertThat(tab.applicationExitWarning()).contains("unsaved hop yard plan draft");

        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("North yard"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<HopYardPlan> draft =
                org.mockito.ArgumentCaptor.forClass(HopYardPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getHopVariety()).isEqualTo("Cascade");
        assertThat(draft.getValue().getPlannedStringingDate())
                .isEqualTo(LocalDate.of(2027, 4, 15));
        assertThat(draft.getValue().getPlannedHarvestEnd())
                .isEqualTo(LocalDate.of(2027, 9, 20));
        assertThat(draft.getValue().getStage()).isEqualTo(HopYardStage.TRAINING);
        assertThat(draft.getValue().getNotes()).isEqualTo("Keep field notes here");
    }

    @Test
    void invalidWindowAndSelectionProtectTheEditDraft() throws Exception {
        HopYardPlanService service = mock(HopYardPlanService.class);
        when(service.list()).thenReturn(List.of(
                saved(11L, "North yard"), saved(12L, "South yard")));
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        HopYardPlanTab tab = new HopYardPlanTab(service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Planned harvest start").setText("2027-10-01");
            field(root, "Planned harvest end").setText("2027-09-01");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Yard name").getText()).isEqualTo("North yard");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Planned harvest end cannot precede start");
        verify(service, never()).update(any(), any());

        onEdt(() -> {
            field(root, "Planned harvest end").setText("2027-10-15");
            field(root, "Planned stringing date").setText("2027-10-10");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains(
                "Planned stringing date cannot be after planned harvest start");
        verify(service, never()).update(any(), any());
    }

    @Test
    void nextSeasonKeepsConflictingDraftAndRequiresNewHarvestStart() throws Exception {
        HopYardPlanService service = mock(HopYardPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North yard")));
        when(service.create(any(HopYardPlan.class))).thenAnswer(invocation -> {
            HopYardPlan result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 22L);
            return result;
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        RecordingHost host = new RecordingHost();
        HopYardPlanTab tab = new HopYardPlanTab(service, host, dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Yard name").setText("Unfinished edit");
            button(root, "Plan next season").doClick();
        });
        assertThat(field(root, "Yard name").getText()).isEqualTo("Unfinished edit");
        assertThat(table(root).getSelectedRow()).isZero();
        verify(service, never()).create(any());

        onEdt(() -> {
            field(root, "Yard name").setText("North yard");
            button(root, "Plan next season").doClick();
        });
        assertThat(field(root, "Yard name").getText()).isEqualTo("North yard");
        assertThat(field(root, "Hop variety").getText()).isEqualTo("Cascade");
        assertThat(field(root, "Acres").getText()).isEqualTo("2.50");
        assertThat(field(root, "Planned stringing date").getText()).isEmpty();
        assertThat(field(root, "Planned harvest start").getText()).isEmpty();
        assertThat(field(root, "Planned harvest end").getText()).isEmpty();
        assertThat(combo(root, "hopYard.stage").getSelectedItem())
                .isEqualTo(HopYardStage.PLANNED);
        assertThat(notes(root).getText()).isEmpty();
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved hop yard plan draft");

        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a planned harvest start date");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Planned harvest start").setText("2028-09-10");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<HopYardPlan> draft =
                org.mockito.ArgumentCaptor.forClass(HopYardPlan.class);
        verify(service).create(draft.capture());
        assertThat(draft.getValue().getYardName()).isEqualTo("North yard");
        assertThat(draft.getValue().getPlannedHarvestStart())
                .isEqualTo(LocalDate.of(2028, 9, 10));
    }

    @Test
    void dragMovesOnlyStageAndRejectsForeignStaleDirtyOrViewerPayloads()
            throws Exception {
        AtomicReference<HopYardPlan> stored = new AtomicReference<>(
                saved(21L, "North yard"));
        HopYardPlanService service = mock(HopYardPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(HopYardStage.class)))
                .thenAnswer(invocation -> {
                    HopYardPlan updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        RecordingHost host = new RecordingHost();
        HopYardPlanTab tab = new HopYardPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton stringing = named(root,
                "hopYard.stageTarget.STRINGING", JButton.class);
        JButton training = named(root,
                "hopYard.stageTarget.TRAINING", JButton.class);
        assertThat(stringing.getToolTipText()).contains("Stage change saves immediately");
        Transferable payload = dragPayload(root);
        HopYardPlanTab foreignTab = new HopYardPlanTab(service, new RecordingHost());
        JComponent foreignRoot = onEdt(foreignTab::getUIComponent);
        onEdt(foreignTab::initialize);
        await(() -> table(foreignRoot).getRowCount() == 1);
        onEdt(() -> table(foreignRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(foreignRoot);
        assertThat(onEdt(() -> stringing.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(stringing, foreign)))).isFalse();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(stringing, payload);
        assertThat(onEdt(() -> stringing.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> stringing.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == HopYardStage.STRINGING);
        assertThat(stored.get().getHopVariety()).isEqualTo("Cascade");
        assertThat(stored.get().getAcres()).isEqualByComparingTo("2.50");
        assertThat(host.lastStatus).contains("moved to Stringing");

        await(() -> training.isEnabled()
                && "Stringing".equals(table(root).getValueAt(0, 6)));
        onEdt((Runnable) training::doClick);
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == HopYardStage.TRAINING);
        verify(service, times(2)).updateStage(eq(21L), any(HopYardStage.class));

        await(() -> "Training".equals(table(root).getValueAt(0, 6))
                && button(root, "Refresh yards").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) >= 3
                && button(root, "Refresh yards").isEnabled());
        assertThat(onEdt(() -> stringing.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(stringing, stale)))).isFalse();
        Transferable current = dragPayload(root);
        onEdt(() -> field(root, "Yard name").setText("Unsaved yard name"));
        assertThat(onEdt(() -> stringing.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(stringing, current)))).isFalse();
        assertThat(field(root, "Yard name").getText()).isEqualTo("Unsaved yard name");
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(stringing.isEnabled()).isFalse();
        assertThat(onEdt(() -> stringing.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(stringing, current)))).isFalse();
    }

    @Test
    void viewerCanReviewButCannotSaveMoveOrPlanNextSeason() throws Exception {
        HopYardPlanService service = mock(HopYardPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North yard")));
        HopYardPlanTab tab = new HopYardPlanTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            tab.applyRolePermissions(false);
            tab.initialize();
        });
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(field(root, "Yard name").getText()).isEqualTo("North yard");
        assertThat(field(root, "Yard name").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "New hop yard plan").isEnabled()).isFalse();
        assertThat(button(root, "Plan next season").isEnabled()).isFalse();
        assertThat(button(root, "Refresh yards").isEnabled()).isTrue();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, never()).create(any());
        verify(service, never()).updateStage(anyLong(), any());
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("hop-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static HopYardPlan saved(Long id, String yardName) {
        HopYardPlan plan = new HopYardPlan(yardName, "Cascade",
                new BigDecimal("2.50"), LocalDate.of(2027, 4, 15),
                LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 20),
                HopYardStage.PLANNED, "Planning note");
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
        return named(root, "hopYard.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea notes(Component root) {
        return named(root, "hopYard.notes", JTextArea.class);
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
        return named(root, "hopYard.yardTable", JTable.class);
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
