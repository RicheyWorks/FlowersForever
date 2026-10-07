package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.CropAreaPlan;
import com.flowerfarm.model.CropAreaStage;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.service.CropAreaPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Potato field and apple orchard planning notebooks")
class CropAreaPlanTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "crop-area");

    @ParameterizedTest(name = "{0} uses its own field language and keeps general record links")
    @CsvSource({
            "POTATO_FARM, Field plans, Potato field plans, Field name, Potato variety",
            "APPLE_ORCHARD, Orchard blocks, Apple orchard blocks, Orchard block, Apple variety"
    })
    void tailoredWorkspacesAndCompactPreview(String code, String title, String heading,
                                              String areaName, String varietyName)
            throws Exception {
        FarmWorkspace workspace = FarmWorkspace.valueOf(code);
        CropAreaPlanService service = mock(CropAreaPlanService.class);
        when(service.listByWorkspace(workspace)).thenReturn(List.of(
                saved(11L, workspace, workspace == FarmWorkspace.POTATO_FARM
                        ? "North field" : "North block")));
        CropAreaPlanTab tab = new CropAreaPlanTab(
                service, new RecordingHost(), workspace);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo(title);
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains(heading)
                .anySatisfy(text -> assertThat(text).contains("Plans do not update harvest"));
        assertThat(field(root, areaName)).isNotNull();
        assertThat(field(root, varietyName)).isNotNull();
        assertThat(table(root).getAccessibleContext().getAccessibleName())
                .contains(heading);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);

        RecordingHost host = new RecordingHost();
        CropAreaPlanTab linked = new CropAreaPlanTab(
                mock(CropAreaPlanService.class), host, workspace);
        JComponent linkedRoot = onEdt(linked::getUIComponent);
        onEdt(() -> field(linkedRoot, areaName).setText("Area A"));
        assertThat(linked.applicationExitWarning()).contains("unsaved");
        onEdt(() -> {
            button(linkedRoot, "Open Harvest Log").doClick();
            button(linkedRoot, "Open Inventory").doClick();
            button(linkedRoot, "Open Customers & Orders").doClick();
        });
        assertThat(host.selected).containsExactly(
                "Harvest Log", "Inventory", "Customers & Orders");
        assertThat(field(linkedRoot, areaName).getText()).isEqualTo("Area A");

        SwingUserHarness.on(root).layoutAt(900, 600);
        onEdt(() -> {
            JScrollPane scroll = named(root, "cropArea.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            JScrollPane tableScroll = named(root, "farm.tableScroll", JScrollPane.class);
            assertThat(tableScroll.getColumnHeader()).isNotNull();
            assertThat(tableScroll.getColumnHeader().getHeight())
                    .as("saved-plan column header height").isGreaterThan(0);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, code.toLowerCase() + "-compact.png");
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                savePreview(root, code.toLowerCase() + "-form.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsCompleteFieldDraftForRetry() throws Exception {
        CropAreaPlanService service = mock(CropAreaPlanService.class);
        when(service.create(any(CropAreaPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    CropAreaPlan saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        CropAreaPlanTab tab = new CropAreaPlanTab(service, host, FarmWorkspace.POTATO_FARM);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Field name").setText("North field");
            field(root, "Potato variety").setText("Russet");
            field(root, "Acres").setText("2.50");
            field(root, "Planting date").setText("2026-04-20");
            field(root, "Target harvest date").setText("2026-09-15");
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("Not saved · complete planning draft kept"));
        assertThat(field(root, "Field name").getText()).isEqualTo("North field");
        assertThat(field(root, "Acres").getText()).isEqualTo("2.50");
        assertThat(tab.applicationExitWarning()).contains("unsaved field plan draft");

        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("North field"));
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, times(2)).create(any(CropAreaPlan.class));
    }

    @Test
    void orchardEditProtectsUnsavedSelectionAndKeepsItsWorkspace() throws Exception {
        CropAreaPlan first = saved(11L, FarmWorkspace.APPLE_ORCHARD, "North block");
        CropAreaPlan second = saved(12L, FarmWorkspace.APPLE_ORCHARD, "South block");
        CropAreaPlanService service = mock(CropAreaPlanService.class);
        when(service.listByWorkspace(FarmWorkspace.APPLE_ORCHARD))
                .thenReturn(List.of(first, second));
        when(service.update(eq(11L), any(CropAreaPlan.class)))
                .thenAnswer(invocation -> {
                    CropAreaPlan saved = invocation.getArgument(1);
                    ReflectionTestUtils.setField(saved, "id", 11L);
                    return saved;
                });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        CropAreaPlanTab tab = new CropAreaPlanTab(
                service, new RecordingHost(), dialogs, FarmWorkspace.APPLE_ORCHARD);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        onEdt(() -> field(root, "Acres").setText("4.25"));
        onEdt(() -> table(root).setRowSelectionInterval(1, 1));
        assertThat(field(root, "Orchard block").getText()).isEqualTo("North block");
        assertThat(field(root, "Acres").getText()).isEqualTo("4.25");
        assertThat(table(root).getSelectedRow()).isZero();

        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave());
        org.mockito.ArgumentCaptor<CropAreaPlan> draft =
                org.mockito.ArgumentCaptor.forClass(CropAreaPlan.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getWorkspace()).isEqualTo(FarmWorkspace.APPLE_ORCHARD);
        assertThat(draft.getValue().getAcres()).isEqualByComparingTo("4.25");
    }

    @Test
    void draggingSavedFieldToStageUpdatesOnlyItsStageAndKeepsButtonFallback() throws Exception {
        AtomicReference<CropAreaPlan> stored = new AtomicReference<>(
                saved(21L, FarmWorkspace.POTATO_FARM, "North field"));
        CropAreaPlanService service = mock(CropAreaPlanService.class);
        when(service.listByWorkspace(FarmWorkspace.POTATO_FARM))
                .thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), eq(FarmWorkspace.POTATO_FARM),
                any(CropAreaStage.class))).thenAnswer(invocation -> {
            CropAreaPlan original = stored.get();
            CropAreaPlan updated = new CropAreaPlan(original.getWorkspace(),
                    original.getAreaName(), original.getVariety(), original.getAcres(),
                    original.getPlantingDate(), original.getTargetHarvestDate(),
                    invocation.getArgument(2), original.getNotes());
            ReflectionTestUtils.setField(updated, "id", 21L);
            stored.set(updated);
            return updated;
        });
        RecordingHost host = new RecordingHost();
        CropAreaPlanTab tab = new CropAreaPlanTab(service, host, FarmWorkspace.POTATO_FARM);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        JButton ready = named(root, "cropArea.stageTarget.HARVEST_READY", JButton.class);
        JButton harvested = named(root, "cropArea.stageTarget.HARVESTED", JButton.class);
        assertThat(ready.getToolTipText()).contains("Stage change saves immediately");

        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(ready.isEnabled()).isTrue();
        Transferable plan = onEdt(() -> {
            Clipboard clipboard = new Clipboard("crop-area-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
        assertThat(plan).isNotNull();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(ready, plan);
        assertThat(onEdt(() -> ready.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> ready.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == CropAreaStage.HARVEST_READY);
        assertThat(stored.get().getAreaName()).isEqualTo("North field");
        assertThat(stored.get().getAcres()).isEqualByComparingTo("3.00");
        assertThat(stored.get().getWorkspace()).isEqualTo(FarmWorkspace.POTATO_FARM);
        assertThat(host.lastStatus).contains("moved to Harvest ready");

        await(() -> harvested.isEnabled()
                && table(root).getValueAt(0, 5) == CropAreaStage.HARVEST_READY);
        onEdt(() -> { harvested.doClick(); });
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == CropAreaStage.HARVESTED);
        assertThat(stored.get().getAreaName()).isEqualTo("North field");
        verify(service, times(2)).updateStage(eq(21L), eq(FarmWorkspace.POTATO_FARM),
                any(CropAreaStage.class));
    }

    @Test
    void stageDropRejectsUnsavedDraftAndReadOnlyRole() throws Exception {
        CropAreaPlanService service = mock(CropAreaPlanService.class);
        when(service.listByWorkspace(FarmWorkspace.APPLE_ORCHARD))
                .thenReturn(List.of(saved(31L, FarmWorkspace.APPLE_ORCHARD, "North block")));
        CropAreaPlanTab tab = new CropAreaPlanTab(
                service, new RecordingHost(), FarmWorkspace.APPLE_ORCHARD);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton target = named(root, "cropArea.stageTarget.COMPLETE", JButton.class);
        Transferable plan = onEdt(() -> {
            Clipboard clipboard = new Clipboard("crop-area-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
        assertThat(plan).isNotNull();

        onEdt(() -> field(root, "Orchard block").setText("Draft name"));
        TransferHandler.TransferSupport dirtyDrop =
                new TransferHandler.TransferSupport(target, plan);
        assertThat(onEdt(() -> target.getTransferHandler().canImport(dirtyDrop))).isFalse();
        assertThat(onEdt(() -> target.getTransferHandler().importData(dirtyDrop))).isFalse();
        assertThat(field(root, "Orchard block").getText()).isEqualTo("Draft name");
        assertThat(tab.applicationExitWarning()).contains("unsaved");

        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(target.isEnabled()).isFalse();
        assertThat(onEdt(() -> target.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(target, plan)))).isFalse();
        verify(service, times(0)).updateStage(any(), any(), any());
    }

    private static CropAreaPlan saved(Long id, FarmWorkspace workspace, String area) {
        CropAreaPlan plan = new CropAreaPlan(workspace, area,
                workspace == FarmWorkspace.POTATO_FARM ? "Russet" : "Gala",
                new BigDecimal("3.00"), LocalDate.of(2026, 4, 20),
                LocalDate.of(2026, 9, 15), CropAreaStage.GROWING, "Planning note");
        ReflectionTestUtils.setField(plan, "id", id);
        return plan;
    }

    private static void savePreview(JComponent root, String fileName)
            throws java.io.IOException {
        BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            root.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", PREVIEWS.resolve(fileName).toFile());
    }

    private static String feedback(Component root) {
        return named(root, "cropArea.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "cropArea.planTable", JTable.class);
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

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (onEdt(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(15);
        }
        assertThat(onEdt(condition::getAsBoolean)).isTrue();
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
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
        private final List<String> selected = new ArrayList<>();
        private String lastStatus = "";

        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { selected.add(tabTitle); }
        @Override public void setStatus(String message) { lastStatus = message; }
        @Override public void runTrendAnalysis() { }
    }
}
