package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.MilkCollection;
import com.flowerfarm.model.MilkCollectionUnit;
import com.flowerfarm.service.MilkCollectionService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Dairy milk collection log")
class MilkCollectionTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "milk-collections");

    @Test
    void measuredCollectionsAndSharedLinksFitCompactWorkspace() throws Exception {
        MilkCollectionService service = mock(MilkCollectionService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "North herd", "Morning",
                "42.50", MilkCollectionUnit.GALLONS)));
        RecordingHost host = new RecordingHost();
        MilkCollectionTab tab = new MilkCollectionTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Milk collections");
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains("Milk collections")
                .anySatisfy(text -> assertThat(text).contains("no conversion or stock posting"));
        for (String name : List.of("Collection date", "Milking group", "Session",
                "Tank / location", "Collected amount")) {
            assertThat(field(root, name)).isNotNull();
        }
        assertThat(unit(root).getItemCount()).isEqualTo(3);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(4)).isEqualTo("Amount");
        assertThat(table(root).getValueAt(0, 4)).isEqualTo("42.50");
        assertThat(table(root).getValueAt(0, 5)).isEqualTo("gal");

        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Collected amount").setText("43.25");
        });
        assertThat(preview(root)).contains("43.25 gal");
        assertThat(tab.applicationExitWarning()).contains("unsaved milk collection draft");
        onEdt(() -> {
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");

        SwingUserHarness.on(root).layoutAt(900, 600);
        onEdt(() -> {
            JScrollPane scroll = named(root, "milkCollection.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            JScrollPane notesScroll = named(root, "milkCollection.notesScroll",
                    JScrollPane.class);
            assertThat(notesScroll.getHeight()).isGreaterThanOrEqualTo(75);
            assertThat(notesScroll.getViewport().getExtentSize().height)
                    .isGreaterThanOrEqualTo(60);
            try {
                Files.createDirectories(PREVIEWS);
                savePreview(root, "milk-collections-compact.png");
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                savePreview(root, "milk-collection-form.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void invalidAmountAndCancelledSelectionKeepDraftThenUpdateSelectedEntry()
            throws Exception {
        MilkCollectionService service = mock(MilkCollectionService.class);
        when(service.list()).thenReturn(List.of(
                saved(11L, "North herd", "Morning", "42.50", MilkCollectionUnit.GALLONS),
                saved(12L, "South herd", "Evening", "95.00", MilkCollectionUnit.POUNDS)));
        when(service.update(eq(11L), any(MilkCollection.class)))
                .thenAnswer(invocation -> {
                    MilkCollection result = invocation.getArgument(1);
                    ReflectionTestUtils.setField(result, "id", 11L);
                    return result;
                });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        MilkCollectionTab tab = new MilkCollectionTab(service, new RecordingHost(), dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 2);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Collected amount").setText("43.25");
            table(root).setRowSelectionInterval(1, 1);
        });
        assertThat(field(root, "Milking group").getText()).isEqualTo("North herd");
        assertThat(field(root, "Collected amount").getText()).isEqualTo("43.25");
        assertThat(table(root).getSelectedRow()).isZero();

        onEdt(() -> {
            field(root, "Collection date").setText("next Friday");
            button(root, "Save collection").doClick();
        });
        assertThat(feedback(root)).contains("YYYY-MM-DD");
        onEdt(() -> {
            field(root, "Collection date").setText("2026-09-30");
            field(root, "Collected amount").setText("0");
            button(root, "Save collection").doClick();
        });
        assertThat(feedback(root)).contains("above 0");
        onEdt(() -> {
            field(root, "Collected amount").setText("42.125");
            button(root, "Save collection").doClick();
        });
        assertThat(feedback(root)).contains("two decimals");
        assertThat(preview(root)).contains("at most two decimals");
        verify(service, never()).update(anyLong(), any());

        onEdt(tab::refreshData);
        await(() -> feedback(root).contains("unsaved draft kept"));
        assertThat(field(root, "Collected amount").getText()).isEqualTo("42.125");
        onEdt(() -> {
            field(root, "Collected amount").setText("43.25");
            unit(root).setSelectedItem(MilkCollectionUnit.LITERS);
            button(root, "Save collection").doClick();
        });
        await(() -> !tab.hasPendingSave());
        org.mockito.ArgumentCaptor<MilkCollection> draft =
                org.mockito.ArgumentCaptor.forClass(MilkCollection.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getCollectionDate())
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(draft.getValue().getGroupName()).isEqualTo("North herd");
        assertThat(draft.getValue().getAmount()).isEqualByComparingTo("43.25");
        assertThat(draft.getValue().getUnit()).isEqualTo(MilkCollectionUnit.LITERS);
    }

    @Test
    void failedSaveKeepsDraftForRetryAndViewerCanOnlyReview() throws Exception {
        MilkCollectionService service = mock(MilkCollectionService.class);
        when(service.create(any(MilkCollection.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    MilkCollection result = invocation.getArgument(0);
                    ReflectionTestUtils.setField(result, "id", 7L);
                    return result;
                });
        RecordingHost host = new RecordingHost();
        MilkCollectionTab tab = new MilkCollectionTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> {
            field(root, "Collection date").setText("2026-09-30");
            field(root, "Milking group").setText("North herd");
            field(root, "Session").setText("Morning");
            field(root, "Collected amount").setText("42.50");
            unit(root).setSelectedItem(MilkCollectionUnit.GALLONS);
            button(root, "Save collection").doClick();
        });
        await(() -> feedback(root).contains("complete collection draft kept"));
        assertThat(tab.applicationExitWarning()).contains("unsaved milk collection draft");
        assertThat(field(root, "Collected amount").getText()).isEqualTo("42.50");

        onEdt(() -> button(root, "Save collection").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("North herd"));
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service, times(2)).create(any(MilkCollection.class));

        MilkCollectionService viewerService = mock(MilkCollectionService.class);
        when(viewerService.list()).thenReturn(List.of(saved(11L, "North herd",
                "Morning", "42.50", MilkCollectionUnit.GALLONS)));
        MilkCollectionTab viewer = new MilkCollectionTab(viewerService, new RecordingHost());
        JComponent viewerRoot = onEdt(viewer::getUIComponent);
        onEdt(() -> {
            viewer.applyRolePermissions(false);
            viewer.initialize();
        });
        await(() -> table(viewerRoot).getRowCount() == 1);
        onEdt(() -> table(viewerRoot).setRowSelectionInterval(0, 0));
        assertThat(field(viewerRoot, "Milking group").getText()).isEqualTo("North herd");
        assertThat(field(viewerRoot, "Collected amount").isEnabled()).isFalse();
        assertThat(unit(viewerRoot).isEnabled()).isFalse();
        assertThat(button(viewerRoot, "Save collection").isEnabled()).isFalse();
        assertThat(button(viewerRoot, "New collection").isEnabled()).isFalse();
        assertThat(button(viewerRoot, "Refresh log").isEnabled()).isTrue();
        assertThat(viewer.applicationExitWarning()).isNull();
        verify(viewerService, never()).create(any());
    }

    private static MilkCollection saved(Long id, String group, String session,
                                        String amount, MilkCollectionUnit unit) {
        MilkCollection collection = new MilkCollection(LocalDate.of(2026, 9, 30), group,
                session, "Tank A", new BigDecimal(amount), unit, "Morning milking");
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
        return named(root, "milkCollection.feedback", JLabel.class).getText();
    }

    private static String preview(Component root) {
        return named(root, "milkCollection.amountPreview", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(item -> name.equals(item.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JComboBox<?> unit(Component root) {
        return named(root, "milkCollection.unit", JComboBox.class);
    }

    private static JButton button(Component root, String title) {
        return descendants(root, JButton.class).stream()
                .filter(item -> title.equals(item.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "milkCollection.logTable", JTable.class);
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
