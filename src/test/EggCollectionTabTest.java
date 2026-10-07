package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.EggCollection;
import com.flowerfarm.service.EggCollectionService;
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

@DisplayName("Poultry egg collection log")
class EggCollectionTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "egg-collections");

    @Test
    void countsAndSharedLinksFitCompactWorkspace() throws Exception {
        EggCollectionService service = mock(EggCollectionService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "East coop", 37, 2)));
        RecordingHost host = new RecordingHost();
        EggCollectionTab tab = new EggCollectionTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Egg collections");
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains("Egg collections")
                .anySatisfy(text -> assertThat(text).contains("does not add inventory"));
        for (String name : List.of("Collection date", "Flock / coop", "Location",
                "Collected eggs", "Unusable eggs")) {
            assertThat(field(root, name)).isNotNull();
        }
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(5)).isEqualTo("Usable");
        assertThat(table(root).getValueAt(0, 5)).isEqualTo(35);

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Collected eggs").setText("40");
        });
        assertThat(preview(root)).contains("38 eggs");
        assertThat(tab.applicationExitWarning()).contains("unsaved egg collection draft");
        onEdt(() -> {
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");

        SwingUserHarness.on(root).layoutAt(900, 600);
        onEdt(() -> {
            JScrollPane scroll = named(root, "eggCollection.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            JScrollPane notesScroll = named(root, "eggCollection.notesScroll",
                    JScrollPane.class);
            assertThat(notesScroll.getHeight()).isGreaterThanOrEqualTo(75);
            assertThat(notesScroll.getViewport().getExtentSize().height)
                    .isGreaterThanOrEqualTo(60);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "egg-collections-compact.png");
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                savePreview(root, "egg-collection-form.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void invalidCountsAndCancelledSelectionKeepDraftThenUpdateSelectedEntry() throws Exception {
        EggCollectionService service = mock(EggCollectionService.class);
        when(service.list()).thenReturn(List.of(
                saved(11L, "East coop", 37, 2), saved(12L, "West coop", 20, 0)));
        when(service.update(eq(11L), any(EggCollection.class)))
                .thenAnswer(invocation -> {
                    EggCollection saved = invocation.getArgument(1);
                    ReflectionTestUtils.setField(saved, "id", 11L);
                    return saved;
                });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        EggCollectionTab tab = new EggCollectionTab(service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Collected eggs").setText("40");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Flock / coop").getText()).isEqualTo("East coop");
        assertThat(field(root, "Collected eggs").getText()).isEqualTo("40");
        assertThat(table(root).getSelectedRow()).isZero();

        onEdt(() -> {
            field(root, "Collection date").setText("next Friday");
            button(root, "Save collection").doClick();
        });
        assertThat(feedback(root)).contains("YYYY-MM-DD");
        onEdt(() -> {
            field(root, "Collection date").setText("2026-09-30");
            field(root, "Unusable eggs").setText("41");
            button(root, "Save collection").doClick();
        });
        assertThat(feedback(root)).contains("cannot exceed");
        assertThat(preview(root)).contains("must be between");
        onEdt(() -> {
            field(root, "Unusable eggs").setText("two");
            button(root, "Save collection").doClick();
        });
        assertThat(feedback(root)).contains("whole number");
        verify(service, never()).update(anyLong(), any());

        onEdt(tab::refreshData);
        await(() -> feedback(root).contains("unsaved draft kept"));
        assertThat(field(root, "Collected eggs").getText()).isEqualTo("40");
        onEdt(() -> {
            field(root, "Unusable eggs").setText("3");
            button(root, "Save collection").doClick();
        });
        await(() -> !tab.hasPendingSave());
        org.mockito.ArgumentCaptor<EggCollection> draft =
                org.mockito.ArgumentCaptor.forClass(EggCollection.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getCollectionDate())
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(draft.getValue().getCollectedCount()).isEqualTo(40);
        assertThat(draft.getValue().getUnusableCount()).isEqualTo(3);
        assertThat(draft.getValue().getUsableCount()).isEqualTo(37);
    }

    @Test
    void failedSaveKeepsDraftForRetryAndViewerCanOnlyReview() throws Exception {
        EggCollectionService service = mock(EggCollectionService.class);
        when(service.create(any(EggCollection.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    EggCollection saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 7L);
                    return saved;
                });
        RecordingHost host = new RecordingHost();
        EggCollectionTab tab = new EggCollectionTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Collection date").setText("2026-09-30");
            field(root, "Flock / coop").setText("East coop");
            field(root, "Collected eggs").setText("37");
            field(root, "Unusable eggs").setText("2");
            button(root, "Save collection").doClick();
        });
        await(() -> feedback(root).contains("complete collection draft kept"));
        assertThat(tab.applicationExitWarning()).contains("unsaved egg collection draft");
        assertThat(field(root, "Collected eggs").getText()).isEqualTo("37");

        onEdt(() -> button(root, "Save collection").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("East coop"));
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, times(2)).create(any(EggCollection.class));

        EggCollectionService viewerService = mock(EggCollectionService.class);
        when(viewerService.list()).thenReturn(List.of(saved(11L, "East coop", 37, 2)));
        EggCollectionTab viewer = new EggCollectionTab(viewerService, new RecordingHost());
        JComponent viewerRoot = onEdt(viewer::getUIComponent);
        onEdt(() -> {
            viewer.applyRolePermissions(false);
            viewer.initialize();
        });
        await(() -> table(viewerRoot).getRowCount() == 1);
        onEdt(() -> table(viewerRoot).setRowSelectionInterval(0, 0));
        assertThat(field(viewerRoot, "Flock / coop").getText()).isEqualTo("East coop");
        assertThat(field(viewerRoot, "Collected eggs").isEnabled()).isFalse();
        assertThat(button(viewerRoot, "Save collection").isEnabled()).isFalse();
        assertThat(button(viewerRoot, "New collection").isEnabled()).isFalse();
        assertThat(button(viewerRoot, "Refresh log").isEnabled()).isTrue();
        assertThat(viewer.applicationExitWarning()).isNull();
        verify(viewerService, never()).create(any());
    }

    private static EggCollection saved(Long id, String flock, int collected, int unusable) {
        EggCollection collection = new EggCollection(LocalDate.of(2026, 9, 30), flock,
                "East yard", collected, unusable, "Morning collection");
        ReflectionTestUtils.setField(collection, "id", id);
        return collection;
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
        return named(root, "eggCollection.feedback", JLabel.class).getText();
    }

    private static String preview(Component root) {
        return named(root, "eggCollection.usablePreview", JLabel.class).getText();
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
        return named(root, "eggCollection.logTable", JTable.class);
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
