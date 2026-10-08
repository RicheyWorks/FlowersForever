package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.formdev.flatlaf.FlatLightLaf;
import com.flowerfarm.model.VineyardBlockPlan;
import com.flowerfarm.model.VineyardBlockStage;
import com.flowerfarm.model.VineyardDestination;
import com.flowerfarm.service.VineyardBlockPlanService;
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

@DisplayName("Vineyard block planning notebook")
class VineyardBlockPlanTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "vineyard-blocks");

    @Test
    void vineyardFieldsAndSharedLinksFitCompactScrollableWorkspace() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        VineyardBlockPlanService service = mock(VineyardBlockPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "East slope")));
        RecordingHost host = new RecordingHost();
        VineyardBlockPlanTab tab = new VineyardBlockPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Vineyard blocks");
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains("Vineyard block plans")
                .anySatisfy(text -> assertThat(text).contains("do not create harvest lots"));
        for (String name : List.of("Block name", "Location", "Grape variety", "Acres",
                "Planned harvest start", "Planned harvest end")) {
            assertThat(field(root, name)).isNotNull();
        }
        assertThat(combo(root, "vineyardBlock.destination").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Intended destination");
        assertThat(combo(root, "vineyardBlock.stage").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Planning stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(4)).isEqualTo("Harvest start");
        assertThat(table(root).getColumnName(5)).isEqualTo("Harvest end");

        onEdt(() -> field(root, "Grape variety").setText("Pinot noir"));
        assertThat(tab.applicationExitWarning()).contains("unsaved vineyard block draft");
        onEdt(() -> {
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Grape variety").getText()).isEqualTo("Pinot noir");
        onEdt(() -> {
            field(root, "Grape variety").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });

        SwingUserHarness.on(root).layoutAt(900, 600);
        onEdt(() -> {
            JScrollPane scroll = named(root, "vineyardBlock.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "vineyardBlock.stageTargets", javax.swing.JPanel.class)
                    .getComponentCount()).isEqualTo(VineyardBlockStage.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "vineyard-compact.png");
                SwingUserHarness.on(root).layoutAt(900, 660);
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                savePreview(root, "vineyard-form.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsCompleteDraftAndRetrySavesIt() throws Exception {
        VineyardBlockPlanService service = mock(VineyardBlockPlanService.class);
        when(service.create(any(VineyardBlockPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    VineyardBlockPlan saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        VineyardBlockPlanTab tab = new VineyardBlockPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Block name").setText("East slope");
            field(root, "Location").setText("Parcel 4");
            field(root, "Grape variety").setText("Pinot noir");
            field(root, "Acres").setText("2.50");
            field(root, "Planned harvest start").setText("2026-09-01");
            field(root, "Planned harvest end").setText("2026-09-20");
            combo(root, "vineyardBlock.destination")
                    .setSelectedItem(VineyardDestination.WINERY_BUYER);
            combo(root, "vineyardBlock.stage")
                    .setSelectedItem(VineyardBlockStage.GROWING);
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Planned harvest end").getText()).isEqualTo("2026-09-20");
        assertThat(combo(root, "vineyardBlock.destination").getSelectedItem())
                .isEqualTo(VineyardDestination.WINERY_BUYER);
        assertThat(tab.applicationExitWarning()).contains("unsaved vineyard block draft");

        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("East slope"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<VineyardBlockPlan> draft =
                org.mockito.ArgumentCaptor.forClass(VineyardBlockPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getGrapeVariety()).isEqualTo("Pinot noir");
        assertThat(draft.getValue().getPlannedHarvestEnd())
                .isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(draft.getValue().getDestination())
                .isEqualTo(VineyardDestination.WINERY_BUYER);
    }

    @Test
    void invalidHarvestWindowAndSelectionProtectTheEditDraft() throws Exception {
        VineyardBlockPlanService service = mock(VineyardBlockPlanService.class);
        when(service.list()).thenReturn(List.of(
                saved(11L, "East slope"), saved(12L, "West slope")));
        when(service.update(eq(11L), any(VineyardBlockPlan.class)))
                .thenAnswer(invocation -> {
                    VineyardBlockPlan saved = invocation.getArgument(1);
                    ReflectionTestUtils.setField(saved, "id", 11L);
                    return saved;
                });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        VineyardBlockPlanTab tab = new VineyardBlockPlanTab(service,
                new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Planned harvest start").setText("2026-10-01");
            field(root, "Planned harvest end").setText("2026-09-01");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Block name").getText()).isEqualTo("East slope");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Planned harvest end cannot precede start");
        verify(service, never()).update(any(), any());

        onEdt(() -> {
            field(root, "Planned harvest end").setText("2026-10-15");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave());
        org.mockito.ArgumentCaptor<VineyardBlockPlan> draft =
                org.mockito.ArgumentCaptor.forClass(VineyardBlockPlan.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getPlannedHarvestStart())
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(draft.getValue().getPlannedHarvestEnd())
                .isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void dragChangesOnlyStageWithClickFallbackAndRejectsStaleOrDirtyPayloads()
            throws Exception {
        AtomicReference<VineyardBlockPlan> stored = new AtomicReference<>(
                saved(21L, "East slope"));
        VineyardBlockPlanService service = mock(VineyardBlockPlanService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStage(eq(21L), any(VineyardBlockStage.class)))
                .thenAnswer(invocation -> {
                    VineyardBlockPlan updated = stored.get().snapshot();
                    updated.setStage(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        RecordingHost host = new RecordingHost();
        VineyardBlockPlanTab tab = new VineyardBlockPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton harvest = named(root,
                "vineyardBlock.stageTarget.HARVEST_WINDOW", JButton.class);
        JButton postHarvest = named(root,
                "vineyardBlock.stageTarget.POST_HARVEST", JButton.class);
        assertThat(harvest.getToolTipText()).contains("Stage change saves immediately");
        Transferable payload = dragPayload(root);
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(harvest, payload);
        assertThat(onEdt(() -> harvest.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> harvest.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == VineyardBlockStage.HARVEST_WINDOW);
        assertThat(stored.get().getGrapeVariety()).isEqualTo("Pinot noir");
        assertThat(stored.get().getAcres()).isEqualByComparingTo("2.50");
        assertThat(host.lastStatus).contains("moved to Harvest window");

        await(() -> postHarvest.isEnabled()
                && "Harvest window".equals(table(root).getValueAt(0, 7)));
        onEdt((Runnable) postHarvest::doClick);
        await(() -> !tab.hasPendingSave() && stored.get().getStage()
                == VineyardBlockStage.POST_HARVEST);
        verify(service, times(2)).updateStage(eq(21L), any(VineyardBlockStage.class));

        await(() -> "Post harvest".equals(table(root).getValueAt(0, 7))
                && button(root, "Refresh blocks").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) >= 3
                && button(root, "Refresh blocks").isEnabled());
        assertThat(onEdt(() -> harvest.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(harvest, stale)))).isFalse();
        Transferable current = dragPayload(root);
        onEdt(() -> field(root, "Block name").setText("Unsaved name"));
        assertThat(onEdt(() -> harvest.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(harvest, current)))).isFalse();
        assertThat(field(root, "Block name").getText()).isEqualTo("Unsaved name");
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(harvest.isEnabled()).isFalse();
        assertThat(onEdt(() -> harvest.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(harvest, current)))).isFalse();
    }

    @Test
    void viewerCanReviewButCannotSaveOrMoveBlocks() throws Exception {
        VineyardBlockPlanService service = mock(VineyardBlockPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "East slope")));
        VineyardBlockPlanTab tab = new VineyardBlockPlanTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            tab.applyRolePermissions(false);
            tab.initialize();
        });
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(field(root, "Block name").getText()).isEqualTo("East slope");
        assertThat(field(root, "Block name").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "New vineyard block").isEnabled()).isFalse();
        assertThat(button(root, "Refresh blocks").isEnabled()).isTrue();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, never()).create(any());
        verify(service, never()).updateStage(anyLong(), any());
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("vineyard-stage-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static VineyardBlockPlan saved(Long id, String block) {
        VineyardBlockPlan plan = new VineyardBlockPlan(block, "Parcel 4", "Pinot noir",
                new BigDecimal("2.50"), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 20), VineyardDestination.WINERY_BUYER,
                VineyardBlockStage.GROWING, "Planning note");
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
        return named(root, "vineyardBlock.feedback", JLabel.class).getText();
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
        return named(root, "vineyardBlock.planTable", JTable.class);
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
