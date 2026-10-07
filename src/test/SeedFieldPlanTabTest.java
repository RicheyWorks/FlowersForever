package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.SeedFieldPlan;
import com.flowerfarm.model.SeedFieldStage;
import com.flowerfarm.service.SeedFieldPlanService;
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

@DisplayName("Seed field season planning")
class SeedFieldPlanTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "seed-fields");

    @Test
    void seedFieldsSharedLinksAndStagesFitScrollableWorkspace() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        SeedFieldPlanService service = mock(SeedFieldPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North field")));
        RecordingHost host = new RecordingHost();
        SeedFieldPlanTab tab = new SeedFieldPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Seed fields");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Seed fields");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("do not record actual seed yields"))).isTrue();
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("does not check distances or certify seed"))).isTrue();
        for (String name : List.of("Field name", "Seed crop", "Variety",
                "Acres", "Previous crop", "Intended use", "Planned sowing date",
                "Planned flowering start", "Planned flowering end",
                "Planned harvest start", "Planned harvest end")) {
            assertThat(field(root, name)).isNotNull();
        }
        assertThat(isolationNotes(root).getAccessibleContext().getAccessibleName())
                .isEqualTo("Isolation notes");
        assertThat(combo(root, "seedField.stage").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Planning stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(4)).isEqualTo("Previous crop");
        assertThat(table(root).getColumnName(5)).isEqualTo("Harvest start");

        onEdt(() -> field(root, "Seed crop").setText("Onion seed"));
        assertThat(tab.applicationExitWarning()).contains("unsaved seed field plan draft");
        onEdt(() -> {
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Seed crop").getText()).isEqualTo("Onion seed");
        onEdt(() -> {
            field(root, "Seed crop").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });
        assertThat(field(root, "Field name").getText()).isEqualTo("North field");
        assertThat(button(root, "Plan next season").isEnabled()).isTrue();

        SwingUserHarness.on(root).layoutAt(920, 630);
        onEdt(() -> {
            JScrollPane scroll = named(root, "seedField.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "seedField.stageTargets", JPanel.class)
                    .getComponentCount()).isEqualTo(SeedFieldStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "seed-fields-compact.png");
                SwingUserHarness.on(root).layoutAt(920, 1450);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "seed-fields-full.png");
                SwingUserHarness.on(root).layoutAt(920, 700);
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                savePreview(root, "seed-field-form.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsCompleteDraftAndRetrySavesIt() throws Exception {
        SeedFieldPlanService service = mock(SeedFieldPlanService.class);
        when(service.create(any(SeedFieldPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    SeedFieldPlan saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        SeedFieldPlanTab tab = new SeedFieldPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Field name").setText("North field");
            field(root, "Seed crop").setText("Carrot seed");
            field(root, "Variety").setText("Nantes");
            field(root, "Acres").setText("2.50");
            field(root, "Previous crop").setText("Lettuce");
            field(root, "Intended use").setText("Vegetable seed");
            field(root, "Planned sowing date").setText("2027-04-15");
            field(root, "Planned flowering start").setText("2027-06-01");
            field(root, "Planned flowering end").setText("2027-06-20");
            field(root, "Planned harvest start").setText("2027-09-01");
            field(root, "Planned harvest end").setText("2027-09-20");
            combo(root, "seedField.stage").setSelectedItem(SeedFieldStage.FLOWERING);
            isolationNotes(root).setText("Checked neighboring crops on foot");
            notes(root).setText("Keep field notes here");
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Planned harvest end").getText()).isEqualTo("2027-09-20");
        assertThat(isolationNotes(root).getText())
                .isEqualTo("Checked neighboring crops on foot");
        assertThat(combo(root, "seedField.stage").getSelectedItem())
                .isEqualTo(SeedFieldStage.FLOWERING);
        assertThat(tab.applicationExitWarning()).contains("unsaved seed field plan draft");

        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("North field"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<SeedFieldPlan> draft =
                org.mockito.ArgumentCaptor.forClass(SeedFieldPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getSeedCrop()).isEqualTo("Carrot seed");
        assertThat(draft.getValue().getVariety()).isEqualTo("Nantes");
        assertThat(draft.getValue().getPreviousCrop()).isEqualTo("Lettuce");
        assertThat(draft.getValue().getPlannedSowingDate())
                .isEqualTo(LocalDate.of(2027, 4, 15));
        assertThat(draft.getValue().getPlannedFloweringStart())
                .isEqualTo(LocalDate.of(2027, 6, 1));
        assertThat(draft.getValue().getPlannedFloweringEnd())
                .isEqualTo(LocalDate.of(2027, 6, 20));
        assertThat(draft.getValue().getPlannedHarvestEnd())
                .isEqualTo(LocalDate.of(2027, 9, 20));
        assertThat(draft.getValue().getIntendedUse()).isEqualTo("Vegetable seed");
        assertThat(draft.getValue().getIsolationNotes())
                .isEqualTo("Checked neighboring crops on foot");
        assertThat(draft.getValue().getStage()).isEqualTo(SeedFieldStage.FLOWERING);
        assertThat(draft.getValue().getNotes()).isEqualTo("Keep field notes here");
    }

    @Test
    void invalidWindowAndSelectionProtectTheEditDraft() throws Exception {
        SeedFieldPlanService service = mock(SeedFieldPlanService.class);
        when(service.list()).thenReturn(List.of(
                saved(11L, "North field"), saved(12L, "South field")));
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        SeedFieldPlanTab tab = new SeedFieldPlanTab(service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Planned harvest start").setText("2027-10-01");
            field(root, "Planned harvest end").setText("2027-09-01");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Field name").getText()).isEqualTo("North field");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Planned harvest end cannot precede start");
        verify(service, never()).update(any(), any());

        onEdt(() -> {
            field(root, "Planned harvest end").setText("2027-10-15");
            field(root, "Planned sowing date").setText("2027-10-10");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains(
                "Planned sowing date cannot be after planned harvest start");
        verify(service, never()).update(any(), any());

        onEdt(() -> {
            field(root, "Planned sowing date").setText("2027-04-15");
            field(root, "Planned flowering start").setText("2027-09-10");
            field(root, "Planned flowering end").setText("2027-09-01");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root)).contains("Planned flowering end cannot precede start");
        verify(service, never()).update(any(), any());

        onEdt(() -> {
            field(root, "Planned flowering end").setText("2027-10-02");
            button(root, "Save plan").doClick();
        });
        assertThat(feedback(root))
                .contains("Planned flowering end cannot be after planned harvest start");
        verify(service, never()).update(any(), any());
    }

    @Test
    void savedFieldEditsPersistFloweringAndManualIsolationNotes() throws Exception {
        AtomicReference<SeedFieldPlan> stored = new AtomicReference<>(
                saved(11L, "North field"));
        SeedFieldPlanService service = mock(SeedFieldPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.update(eq(11L), any(SeedFieldPlan.class))).thenAnswer(invocation -> {
            SeedFieldPlan updated = invocation.getArgument(1);
            ReflectionTestUtils.setField(updated, "id", 11L);
            stored.set(updated.snapshot());
            return updated.snapshot();
        });
        RecordingHost host = new RecordingHost();
        SeedFieldPlanTab tab = new SeedFieldPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Intended use").setText("Foundation seed");
            field(root, "Planned flowering end").setText("2027-06-25");
            isolationNotes(root).setText("Walk neighboring fields before bloom");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave()
                && "Foundation seed".equals(stored.get().getIntendedUse()));
        assertThat(stored.get().getPlannedFloweringEnd())
                .isEqualTo(LocalDate.of(2027, 6, 25));
        assertThat(stored.get().getIsolationNotes())
                .isEqualTo("Walk neighboring fields before bloom");
        assertThat(stored.get().getFieldName()).isEqualTo("North field");
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service).update(eq(11L), any(SeedFieldPlan.class));
        verify(service, never()).create(any());
    }

    @Test
    void nextSeasonKeepsConflictingDraftAndResetsDatesAndIsolation()
            throws Exception {
        SeedFieldPlanService service = mock(SeedFieldPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North field")));
        when(service.create(any(SeedFieldPlan.class))).thenAnswer(invocation -> {
            SeedFieldPlan result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 22L);
            return result;
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        RecordingHost host = new RecordingHost();
        SeedFieldPlanTab tab = new SeedFieldPlanTab(service, host, dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Field name").setText("Unfinished edit");
            button(root, "Plan next season").doClick();
        });
        assertThat(field(root, "Field name").getText()).isEqualTo("Unfinished edit");
        assertThat(table(root).getSelectedRow()).isZero();
        verify(service, never()).create(any());

        onEdt(() -> {
            field(root, "Field name").setText("North field");
            button(root, "Plan next season").doClick();
        });
        assertThat(field(root, "Field name").getText()).isEqualTo("North field");
        assertThat(field(root, "Seed crop").getText()).isEqualTo("Carrot seed");
        assertThat(field(root, "Variety").getText()).isEqualTo("Nantes");
        assertThat(field(root, "Acres").getText()).isEqualTo("2.50");
        assertThat(field(root, "Previous crop").getText()).isEmpty();
        assertThat(field(root, "Intended use").getText()).isEmpty();
        assertThat(field(root, "Planned sowing date").getText()).isEmpty();
        assertThat(field(root, "Planned flowering start").getText()).isEmpty();
        assertThat(field(root, "Planned flowering end").getText()).isEmpty();
        assertThat(field(root, "Planned harvest start").getText()).isEmpty();
        assertThat(field(root, "Planned harvest end").getText()).isEmpty();
        assertThat(combo(root, "seedField.stage").getSelectedItem())
                .isEqualTo(SeedFieldStage.PLANNED);
        assertThat(isolationNotes(root).getText()).isEmpty();
        assertThat(notes(root).getText()).isEmpty();
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved seed field plan draft");

        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Enter a planned harvest start date");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Planned harvest start").setText("2028-09-10");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<SeedFieldPlan> draft =
                org.mockito.ArgumentCaptor.forClass(SeedFieldPlan.class);
        verify(service).create(draft.capture());
        assertThat(draft.getValue().getFieldName()).isEqualTo("North field");
        assertThat(draft.getValue().getSeedCrop()).isEqualTo("Carrot seed");
        assertThat(draft.getValue().getPreviousCrop()).isEmpty();
        assertThat(draft.getValue().getVariety()).isEqualTo("Nantes");
        assertThat(draft.getValue().getPlannedSowingDate()).isNull();
        assertThat(draft.getValue().getPlannedFloweringStart()).isNull();
        assertThat(draft.getValue().getIsolationNotes()).isEmpty();
        assertThat(draft.getValue().getPlannedHarvestStart())
                .isEqualTo(LocalDate.of(2028, 9, 10));
    }

    @Test
    void dragMovesOnlyStageAndRejectsForeignStaleDirtyOrViewerPayloads()
            throws Exception {
        AtomicReference<SeedFieldPlan> stored = new AtomicReference<>(
                saved(21L, "North field"));
        SeedFieldPlanService service = mock(SeedFieldPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(SeedFieldStage.class)))
                .thenAnswer(invocation -> {
                    SeedFieldPlan updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        RecordingHost host = new RecordingHost();
        SeedFieldPlanTab tab = new SeedFieldPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton seeded = named(root,
                "seedField.stageTarget.SOWN", JButton.class);
        JButton flowering = named(root,
                "seedField.stageTarget.FLOWERING", JButton.class);
        assertThat(seeded.getToolTipText()).contains("Stage change saves immediately");
        Transferable payload = dragPayload(root);
        SeedFieldPlanTab foreignTab = new SeedFieldPlanTab(service, new RecordingHost());
        JComponent foreignRoot = onEdt(foreignTab::getUIComponent);
        onEdt(foreignTab::initialize);
        await(() -> table(foreignRoot).getRowCount() == 1);
        onEdt(() -> table(foreignRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(foreignRoot);
        assertThat(onEdt(() -> seeded.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(seeded, foreign)))).isFalse();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(seeded, payload);
        assertThat(onEdt(() -> seeded.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> seeded.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == SeedFieldStage.SOWN);
        assertThat(stored.get().getSeedCrop()).isEqualTo("Carrot seed");
        assertThat(stored.get().getAcres()).isEqualByComparingTo("2.50");
        assertThat(host.lastStatus).contains("moved to Sown");

        await(() -> flowering.isEnabled()
                && "Sown".equals(table(root).getValueAt(0, 6)));
        onEdt((Runnable) flowering::doClick);
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == SeedFieldStage.FLOWERING);
        verify(service, times(2)).updateStage(eq(21L), any(SeedFieldStage.class));

        await(() -> "Flowering".equals(table(root).getValueAt(0, 6))
                && button(root, "Refresh fields").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) >= 3
                && button(root, "Refresh fields").isEnabled());
        assertThat(onEdt(() -> seeded.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(seeded, stale)))).isFalse();
        Transferable current = dragPayload(root);
        onEdt(() -> field(root, "Field name").setText("Unsaved field name"));
        assertThat(onEdt(() -> seeded.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(seeded, current)))).isFalse();
        assertThat(field(root, "Field name").getText()).isEqualTo("Unsaved field name");
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(seeded.isEnabled()).isFalse();
        assertThat(onEdt(() -> seeded.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(seeded, current)))).isFalse();
    }

    @Test
    void viewerCanReviewButCannotSaveMoveOrPlanNextSeason() throws Exception {
        SeedFieldPlanService service = mock(SeedFieldPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North field")));
        SeedFieldPlanTab tab = new SeedFieldPlanTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            tab.applyRolePermissions(false);
            tab.initialize();
        });
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(field(root, "Field name").getText()).isEqualTo("North field");
        assertThat(field(root, "Field name").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "New seed field plan").isEnabled()).isFalse();
        assertThat(button(root, "Plan next season").isEnabled()).isFalse();
        assertThat(button(root, "Refresh fields").isEnabled()).isTrue();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, never()).create(any());
        verify(service, never()).updateStage(anyLong(), any());
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("seed-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static SeedFieldPlan saved(Long id, String fieldName) {
        SeedFieldPlan plan = new SeedFieldPlan(fieldName, "Carrot seed",
                "Nantes", new BigDecimal("2.50"), "Lettuce",
                LocalDate.of(2027, 4, 15),
                LocalDate.of(2027, 6, 1), LocalDate.of(2027, 6, 20),
                LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 20),
                "Vegetable seed", "Neighboring crop observed",
                SeedFieldStage.PLANNED, "Planning note");
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
        return named(root, "seedField.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea notes(Component root) {
        return named(root, "seedField.notes", JTextArea.class);
    }

    private static JTextArea isolationNotes(Component root) {
        return named(root, "seedField.isolationNotes", JTextArea.class);
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
        return named(root, "seedField.fieldTable", JTable.class);
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
