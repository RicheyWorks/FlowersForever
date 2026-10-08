package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.AquaticCohortPlan;
import com.flowerfarm.model.AquaticCohortStage;
import com.flowerfarm.service.AquaticCohortPlanService;
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

@DisplayName("Aquatic cohort planning")
class AquaticCohortTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "aquatic-cohorts");

    @Test
    void aquaticWorkspaceExplainsScopeAndOffersEditablePlanAndSharedLinks() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        AquaticCohortPlanService service = mock(AquaticCohortPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "Spring oysters")));
        RecordingHost host = new RecordingHost();
        AquaticCohortTab tab = new AquaticCohortTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Aquatic cohorts");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Aquatic cohorts");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("do not record completed stocking, harvest"))).isTrue();
        for (String name : List.of("Cohort name", "Species", "Growing site",
                "Culture method", "Planned stocking date", "Planned harvest start",
                "Planned harvest end", "Intended destination")) {
            assertThat(field(root, name)).isNotNull();
        }
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(2)).isEqualTo("Growing site");
        onEdt(() -> {
            field(root, "Species").setText("Rainbow trout");
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Species").getText()).isEqualTo("Rainbow trout");
        assertThat(tab.applicationExitWarning()).contains("unsaved aquatic cohort plan draft");
        onEdt(() -> {
            field(root, "Species").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });

        SwingUserHarness.on(root).layoutAt(920, 1320);
        onEdt(() -> {
            JScrollPane scroll = named(root, "aquaticCohort.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "aquaticCohort.stageTargets", JPanel.class)
                    .getComponentCount()).isEqualTo(AquaticCohortStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "aquatic-cohorts-full.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsCohortAndRetryPersistsEveryPlanningField() throws Exception {
        AquaticCohortPlanService service = mock(AquaticCohortPlanService.class);
        when(service.create(any(AquaticCohortPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    AquaticCohortPlan saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        AquaticCohortTab tab = new AquaticCohortTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Cohort name").setText("Summer trout");
            field(root, "Species").setText("Rainbow trout");
            field(root, "Growing site").setText("Tank 2");
            field(root, "Culture method").setText("Recirculating tank");
            field(root, "Planned stocking date").setText("2027-04-01");
            field(root, "Planned harvest start").setText("2027-09-01");
            field(root, "Planned harvest end").setText("2027-09-20");
            field(root, "Intended destination").setText("Wholesale");
            combo(root).setSelectedItem(AquaticCohortStage.GROWING_OUT);
            notes(root).setText("Check water temperature");
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Growing site").getText()).isEqualTo("Tank 2");
        assertThat(tab.applicationExitWarning()).contains("unsaved aquatic cohort plan draft");

        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("Summer trout"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<AquaticCohortPlan> draft =
                org.mockito.ArgumentCaptor.forClass(AquaticCohortPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getSpecies()).isEqualTo("Rainbow trout");
        assertThat(draft.getValue().getGrowingSite()).isEqualTo("Tank 2");
        assertThat(draft.getValue().getCultureMethod()).isEqualTo("Recirculating tank");
        assertThat(draft.getValue().getPlannedStockingDate())
                .isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(draft.getValue().getPlannedHarvestEnd())
                .isEqualTo(LocalDate.of(2027, 9, 20));
        assertThat(draft.getValue().getIntendedDestination()).isEqualTo("Wholesale");
        assertThat(draft.getValue().getStage()).isEqualTo(AquaticCohortStage.GROWING_OUT);
        assertThat(draft.getValue().getNotes()).isEqualTo("Check water temperature");
    }

    @Test
    void nextCohortRequiresSavedSelectionAndPreservesUnsavedDraft() throws Exception {
        AquaticCohortPlanService service = mock(AquaticCohortPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "Spring oysters")));
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        AquaticCohortTab tab = new AquaticCohortTab(service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Growing site").setText("Unfinished site edit");
            button(root, "Plan next cohort").doClick();
        });
        assertThat(field(root, "Growing site").getText()).isEqualTo("Unfinished site edit");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> {
            field(root, "Growing site").setText("South bay lease");
            button(root, "Plan next cohort").doClick();
        });
        assertThat(field(root, "Cohort name").getText()).isEmpty();
        assertThat(field(root, "Species").getText()).isEqualTo("Pacific oyster");
        assertThat(field(root, "Growing site").getText()).isEqualTo("South bay lease");
        assertThat(field(root, "Culture method").getText()).isEqualTo("Bags");
        assertThat(field(root, "Intended destination").getText()).isEqualTo("Wholesale");
        assertThat(field(root, "Planned stocking date").getText()).isEmpty();
        assertThat(field(root, "Planned harvest start").getText()).isEmpty();
        assertThat(field(root, "Planned harvest end").getText()).isEmpty();
        assertThat(combo(root).getSelectedItem()).isEqualTo(AquaticCohortStage.PLANNED);
        assertThat(notes(root).getText()).isEmpty();
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved aquatic cohort plan draft");
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a cohort name");
        verify(service, never()).create(any());
    }

    @Test
    void validationAndStageMovesGuardDatesDirtyDraftStalePayloadAndViewer() throws Exception {
        AtomicReference<AquaticCohortPlan> stored = new AtomicReference<>(
                saved(21L, "Spring oysters"));
        AquaticCohortPlanService service = mock(AquaticCohortPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(AquaticCohortStage.class)))
                .thenAnswer(invocation -> {
                    AquaticCohortPlan updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        AquaticCohortTab tab = new AquaticCohortTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        onEdt(() -> {
            field(root, "Planned stocking date").setText("2027-12-01");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned stocking date cannot be after planned harvest start");
        verify(service, never()).update(any(), any());
        onEdt(() -> field(root, "Planned stocking date").setText("2027-03-01"));

        JButton stocking = named(root, "aquaticCohort.stageTarget.STOCKING_WINDOW", JButton.class);
        assertThat(stocking.getToolTipText()).contains("Stage change saves immediately");
        assertThat(dragPayload(root)).isNull();
        onEdt(() -> field(root, "Planned stocking date").setText("2027-02-01"));
        Transferable payload = dragPayload(root);
        AquaticCohortTab foreignTab = new AquaticCohortTab(service, new RecordingHost());
        JComponent foreignRoot = onEdt(foreignTab::getUIComponent);
        onEdt(foreignTab::initialize);
        await(() -> table(foreignRoot).getRowCount() == 1);
        onEdt(() -> table(foreignRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(foreignRoot);
        assertThat(onEdt(() -> stocking.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(stocking, foreign)))).isFalse();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(stocking, payload);
        assertThat(onEdt(() -> stocking.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> stocking.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == AquaticCohortStage.STOCKING_WINDOW);
        assertThat(stored.get().getSpecies()).isEqualTo("Pacific oyster");

        await(() -> "Stocking window".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh cohorts").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton growing = named(root, "aquaticCohort.stageTarget.GROWING_OUT", JButton.class);
        onEdt((Runnable) growing::doClick);
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == AquaticCohortStage.GROWING_OUT);
        await(() -> "Growing out".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh cohorts").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        long revision = (Long) ReflectionTestUtils.getField(tab, "listRevision");
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) > revision
                && button(root, "Refresh cohorts").isEnabled());
        JButton harvest = named(root, "aquaticCohort.stageTarget.HARVEST_WINDOW", JButton.class);
        assertThat(onEdt(() -> harvest.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(harvest, stale)))).isFalse();
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "Plan next cohort").isEnabled()).isFalse();
        assertThat(harvest.isEnabled()).isFalse();
        verify(service).updateStage(21L, AquaticCohortStage.STOCKING_WINDOW);
        verify(service).updateStage(21L, AquaticCohortStage.GROWING_OUT);
        verify(service, never()).create(any());
        verify(service, never()).update(any(), any());
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("aquatic-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static AquaticCohortPlan saved(Long id, String name) {
        AquaticCohortPlan plan = new AquaticCohortPlan(name, "Pacific oyster",
                "South bay lease", "Bags", LocalDate.of(2027, 2, 1),
                LocalDate.of(2027, 10, 1), LocalDate.of(2027, 10, 20),
                "Wholesale", AquaticCohortStage.PLANNED,
                "Check water quality plan");
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
        return named(root, "aquaticCohort.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea notes(Component root) {
        return named(root, "aquaticCohort.notes", JTextArea.class);
    }

    private static JComboBox<?> combo(Component root) {
        return named(root, "aquaticCohort.stage", JComboBox.class);
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "aquaticCohort.cohortTable", JTable.class);
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
