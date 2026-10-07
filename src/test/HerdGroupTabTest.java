package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.HerdGroup;
import com.flowerfarm.service.HerdGroupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import javax.swing.JButton;
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

@DisplayName("Herd group planning notebook")
class HerdGroupTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "herd-groups");

    @Test
    void freeTextSpeciesScopeAndSharedLinksFitCompactWorkspace() throws Exception {
        HerdGroupService service = mock(HerdGroupService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North pasture")));
        RecordingHost host = new RecordingHost();
        HerdGroupTab tab = new HerdGroupTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Herd groups");
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains("Herd groups")
                .anySatisfy(text -> assertThat(text).contains("do not track animal health"));
        for (String name : List.of("Group name", "Species", "Head count", "Location",
                "Purpose", "Next check date")) {
            assertThat(field(root, name)).isNotNull();
        }
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(2)).isEqualTo("Heads");
        assertThat(table(root).getColumnName(5)).isEqualTo("Next check");

        onEdt(() -> field(root, "Species").setText("alpacas"));
        assertThat(tab.applicationExitWarning()).contains("unsaved herd group draft");
        onEdt(() -> {
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");

        SwingUserHarness.on(root).layoutAt(900, 600);
        onEdt(() -> {
            JScrollPane scroll = named(root, "herdGroup.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "herd-groups-compact.png");
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                savePreview(root, "herd-group-form.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void failedSaveKeepsCompleteDraftForRetry() throws Exception {
        HerdGroupService service = mock(HerdGroupService.class);
        when(service.create(any(HerdGroup.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    HerdGroup saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        HerdGroupTab tab = new HerdGroupTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Group name").setText("North pasture");
            field(root, "Species").setText("alpacas");
            field(root, "Head count").setText("24");
            field(root, "Location").setText("Upper field");
            field(root, "Purpose").setText("Grazing rotation");
            field(root, "Next check date").setText("2026-05-15");
            button(root, "Save group plan").doClick();
        });
        await(() -> feedback(root).contains("complete planning draft kept"));
        assertThat(field(root, "Next check date").getText()).isEqualTo("2026-05-15");
        assertThat(tab.applicationExitWarning()).contains("unsaved herd group draft");

        onEdt(() -> button(root, "Save group plan").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("North pasture"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<HerdGroup> draft =
                org.mockito.ArgumentCaptor.forClass(HerdGroup.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getSpecies()).isEqualTo("alpacas");
        assertThat(draft.getValue().getHeadCount()).isEqualTo(24);
        assertThat(draft.getValue().getNextCheckDate())
                .isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void invalidFieldsAndCancelledSelectionProtectEditedGroup() throws Exception {
        HerdGroupService service = mock(HerdGroupService.class);
        when(service.list()).thenReturn(List.of(
                saved(11L, "North pasture"), saved(12L, "South pasture")));
        when(service.update(eq(11L), any(HerdGroup.class)))
                .thenAnswer(invocation -> {
                    HerdGroup saved = invocation.getArgument(1);
                    ReflectionTestUtils.setField(saved, "id", 11L);
                    return saved;
                });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        HerdGroupTab tab = new HerdGroupTab(service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Species").setText("goats");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Group name").getText()).isEqualTo("North pasture");
        assertThat(field(root, "Species").getText()).isEqualTo("goats");
        assertThat(table(root).getSelectedRow()).isZero();

        onEdt(() -> {
            field(root, "Next check date").setText("next Friday");
            button(root, "Save group plan").doClick();
        });
        assertThat(feedback(root)).contains("YYYY-MM-DD");
        verify(service, never()).update(anyLong(), any());
        onEdt(() -> {
            field(root, "Next check date").setText("2026-06-01");
            field(root, "Head count").setText("two dozen");
            button(root, "Save group plan").doClick();
        });
        assertThat(feedback(root)).contains("whole number");
        onEdt(() -> {
            field(root, "Head count").setText("1000001");
            button(root, "Save group plan").doClick();
        });
        assertThat(feedback(root)).contains("between 0 and 1,000,000");
        verify(service, never()).update(anyLong(), any());

        onEdt(tab::refreshData);
        await(() -> feedback(root).contains("unsaved draft kept"));
        assertThat(field(root, "Species").getText()).isEqualTo("goats");
        onEdt(() -> {
            field(root, "Head count").setText("18");
            button(root, "Save group plan").doClick();
        });
        await(() -> !tab.hasPendingSave());
        org.mockito.ArgumentCaptor<HerdGroup> draft =
                org.mockito.ArgumentCaptor.forClass(HerdGroup.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getSpecies()).isEqualTo("goats");
        assertThat(draft.getValue().getHeadCount()).isEqualTo(18);
    }

    @Test
    void viewerCanReviewButCannotSaveGroups() throws Exception {
        HerdGroupService service = mock(HerdGroupService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North pasture")));
        HerdGroupTab tab = new HerdGroupTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            tab.applyRolePermissions(false);
            tab.initialize();
        });
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        assertThat(field(root, "Group name").getText()).isEqualTo("North pasture");
        assertThat(field(root, "Species").isEnabled()).isFalse();
        assertThat(button(root, "Save group plan").isEnabled()).isFalse();
        assertThat(button(root, "New herd group").isEnabled()).isFalse();
        assertThat(button(root, "Refresh groups").isEnabled()).isTrue();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, never()).create(any());
    }

    private static HerdGroup saved(Long id, String name) {
        HerdGroup group = new HerdGroup(name, "sheep", 24,
                "North pasture", "Grazing rotation", LocalDate.of(2026, 5, 15),
                "Planning note");
        ReflectionTestUtils.setField(group, "id", id);
        return group;
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
        return named(root, "herdGroup.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(item -> name.equals(item.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JButton button(Component root, String title) {
        return descendants(root, JButton.class).stream()
                .filter(item -> title.equals(item.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "herdGroup.planTable", JTable.class);
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        return descendants(root, type).stream()
                .filter(item -> name.equals(item.getName())).findFirst().orElseThrow();
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
