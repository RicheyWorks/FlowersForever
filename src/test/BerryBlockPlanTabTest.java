package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.BerryBlockPlan;
import com.flowerfarm.model.BerryBlockStage;
import com.flowerfarm.model.BerrySalesChannel;
import com.flowerfarm.service.BerryBlockPlanService;
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
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Berry block planning notebook")
class BerryBlockPlanTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "berry-block");

    @Test
    void berryFieldsAndGeneralLinksFitCompactScrollableWorkspace() throws Exception {
        BerryBlockPlanService service = mock(BerryBlockPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North block")));
        RecordingHost host = new RecordingHost();
        BerryBlockPlanTab tab = new BerryBlockPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Berry blocks");
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains("Berry block plans")
                .anySatisfy(text -> assertThat(text).contains("U-pick bookings"));
        for (String field : List.of("Block name", "Berry crop", "Cultivar", "Acres",
                "Planting date", "First planned pick", "Last planned pick")) {
            assertThat(field(root, field)).isNotNull();
        }
        assertThat(combo(root, "berryBlock.channel").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Intended sales channel");
        assertThat(combo(root, "berryBlock.stage").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Planning stage");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(4)).isEqualTo("First pick");
        assertThat(table(root).getColumnName(5)).isEqualTo("Last pick");

        onEdt(() -> field(root, "Berry crop").setText("blueberries"));
        assertThat(tab.applicationExitWarning()).contains("unsaved berry block draft");
        onEdt(() -> {
            button(root, "Open Harvest Log").doClick();
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
        });
        assertThat(host.selected).containsExactly(
                "Harvest Log", "Inventory", "Customers & Orders");
        assertThat(field(root, "Berry crop").getText()).isEqualTo("blueberries");
        onEdt(() -> {
            field(root, "Berry crop").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });

        SwingUserHarness.on(root).layoutAt(900, 600);
        onEdt(() -> {
            JScrollPane scroll = named(root, "berryBlock.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            JScrollPane tableScroll = named(root, "farm.tableScroll", JScrollPane.class);
            assertThat(tableScroll.getColumnHeader()).isNotNull();
            assertThat(tableScroll.getColumnHeader().getHeight()).isGreaterThan(0);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "berry-compact.png");
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                savePreview(root, "berry-form.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsFullBerryWindowAndChannelForRetry() throws Exception {
        BerryBlockPlanService service = mock(BerryBlockPlanService.class);
        when(service.create(any(BerryBlockPlan.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    BerryBlockPlan saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        BerryBlockPlanTab tab = new BerryBlockPlanTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Block name").setText("North block");
            field(root, "Berry crop").setText("blueberries");
            field(root, "Cultivar").setText("Duke");
            field(root, "Acres").setText("2.50");
            field(root, "Planting date").setText("2026-04-20");
            field(root, "First planned pick").setText("2026-07-01");
            field(root, "Last planned pick").setText("2026-08-15");
            combo(root, "berryBlock.channel").setSelectedItem(BerrySalesChannel.FRESH);
            combo(root, "berryBlock.stage").setSelectedItem(BerryBlockStage.GROWING);
            button(root, "Save plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Last planned pick").getText()).isEqualTo("2026-08-15");
        assertThat(combo(root, "berryBlock.channel").getSelectedItem())
                .isEqualTo(BerrySalesChannel.FRESH);
        assertThat(tab.applicationExitWarning()).contains("unsaved berry block draft");

        onEdt(() -> button(root, "Save plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("North block"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<BerryBlockPlan> draft =
                org.mockito.ArgumentCaptor.forClass(BerryBlockPlan.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getCrop()).isEqualTo("blueberries");
        assertThat(draft.getValue().getLastPickDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(draft.getValue().getChannel()).isEqualTo(BerrySalesChannel.FRESH);
    }

    @Test
    void editingSelectionAndInvalidPickWindowProtectDraft() throws Exception {
        BerryBlockPlanService service = mock(BerryBlockPlanService.class);
        when(service.list()).thenReturn(List.of(
                saved(11L, "North block"), saved(12L, "South block")));
        when(service.update(eq(11L), any(BerryBlockPlan.class)))
                .thenAnswer(invocation -> {
                    BerryBlockPlan saved = invocation.getArgument(1);
                    ReflectionTestUtils.setField(saved, "id", 11L);
                    return saved;
                });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        BerryBlockPlanTab tab = new BerryBlockPlanTab(service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "First planned pick").setText("2026-09-01");
            field(root, "Last planned pick").setText("2026-08-01");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Block name").getText()).isEqualTo("North block");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> button(root, "Save plan").doClick());
        assertThat(feedback(root)).contains("Last planned pick cannot precede first pick");
        verify(service, never()).update(any(), any());
        assertThat(tab.applicationExitWarning()).contains("unsaved berry block draft");

        onEdt(() -> {
            field(root, "Last planned pick").setText("2026-09-15");
            button(root, "Save plan").doClick();
        });
        await(() -> !tab.hasPendingSave());
        org.mockito.ArgumentCaptor<BerryBlockPlan> draft =
                org.mockito.ArgumentCaptor.forClass(BerryBlockPlan.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getFirstPickDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(draft.getValue().getLastPickDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void viewerCanReviewButCannotSavePlans() throws Exception {
        BerryBlockPlanService service = mock(BerryBlockPlanService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North block")));
        BerryBlockPlanTab tab = new BerryBlockPlanTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            tab.applyRolePermissions(false);
            tab.initialize();
        });
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(field(root, "Block name").getText()).isEqualTo("North block");
        assertThat(field(root, "Block name").isEnabled()).isFalse();
        assertThat(button(root, "Save plan").isEnabled()).isFalse();
        assertThat(button(root, "New berry block").isEnabled()).isFalse();
        assertThat(button(root, "Refresh plans").isEnabled()).isTrue();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, never()).create(any());
    }

    private static BerryBlockPlan saved(Long id, String block) {
        BerryBlockPlan plan = new BerryBlockPlan(block, "blueberries", "Duke",
                new BigDecimal("2.50"), LocalDate.of(2026, 4, 20),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 15),
                BerrySalesChannel.FRESH, BerryBlockStage.GROWING, "Planning note");
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
        return named(root, "berryBlock.feedback", JLabel.class).getText();
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
        return named(root, "berryBlock.planTable", JTable.class);
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
