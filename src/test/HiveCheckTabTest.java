package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.HiveCheck;
import com.flowerfarm.model.HiveCheckStatus;
import com.flowerfarm.service.HiveCheckService;
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

@DisplayName("Apiary hive checks")
class HiveCheckTabTest {

    private static final Path PREVIEWS = Path.of("target", "ui-previews", "hive-checks");

    @Test
    void screenExplainsObservationScopeAndShowsRepeatableChecks() throws Exception {
        onEdt((Runnable) () -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        });
        HiveCheckService service = mock(HiveCheckService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "Hive 12")));
        RecordingHost host = new RecordingHost();
        HiveCheckTab tab = new HiveCheckTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getTabTitle()).isEqualTo("Hive checks");
        List<String> labels = descendants(root, JLabel.class).stream()
                .map(JLabel::getText).toList();
        assertThat(labels).contains("Hive checks");
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("not a diagnosis"))).isTrue();
        assertThat(labels.stream().anyMatch(text -> text != null
                && text.contains("registration, pollination contracts"))).isTrue();
        for (String name : List.of("Apiary name", "Hive label", "Check date",
                "Purpose", "Next check date")) assertThat(field(root, name)).isNotNull();
        assertThat(combo(root).getAccessibleContext().getAccessibleName())
                .isEqualTo("Hive check status");
        assertThat(area(root, "hiveCheck.observations").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Observations");
        assertThat(area(root, "hiveCheck.actions").getAccessibleContext()
                .getAccessibleName()).isEqualTo("Actions noted");
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        assertThat(table(root).getColumnName(4)).isEqualTo("Next check");
        onEdt(() -> {
            field(root, "Hive label").setText("Hive 14");
            button(root, "Open Inventory").doClick();
            button(root, "Open Customers & Orders").doClick();
            button(root, "Open Knowledge Log").doClick();
        });
        assertThat(host.selected).containsExactly("Inventory", "Customers & Orders",
                "Knowledge Log");
        assertThat(field(root, "Hive label").getText()).isEqualTo("Hive 14");
        assertThat(tab.applicationExitWarning()).contains("unsaved hive check draft");
        onEdt(() -> {
            field(root, "Hive label").setText("");
            table(root).setRowSelectionInterval(0, 0);
        });
        SwingUserHarness.on(root).layoutAt(920, 1160);
        onEdt(() -> {
            JScrollPane scroll = named(root, "hiveCheck.scroll", JScrollPane.class);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(named(root, "hiveCheck.statusTargets", JPanel.class)
                    .getComponentCount()).isEqualTo(HiveCheckStatus.values().length);
            try {
                Files.createDirectories(PREVIEWS);
                scroll.getVerticalScrollBar().setValue(0);
                savePreview(root, "hive-checks-full.png");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void saveValidatesNextDateAndRetryKeepsAllObservationFields() throws Exception {
        HiveCheckService service = mock(HiveCheckService.class);
        when(service.create(any(HiveCheck.class)))
                .thenThrow(new IllegalStateException("store busy"))
                .thenAnswer(invocation -> {
                    HiveCheck result = invocation.getArgument(0);
                    ReflectionTestUtils.setField(result, "id", 7L);
                    return result;
                });
        RecordingHost host = new RecordingHost();
        HiveCheckTab tab = new HiveCheckTab(service, host);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(() -> button(root, "Save check").doClick());
        assertThat(feedback(root)).contains("Enter an apiary name");
        onEdt(() -> {
            field(root, "Apiary name").setText("North orchard");
            field(root, "Hive label").setText("Hive 12");
            field(root, "Check date").setText("2027-04-01");
            field(root, "Purpose").setText("Spring check");
            field(root, "Next check date").setText("2027-03-31");
            combo(root).setSelectedItem(HiveCheckStatus.NEEDS_FOLLOW_UP);
            area(root, "hiveCheck.observations").setText("Queen seen; brood pattern uneven");
            area(root, "hiveCheck.actions").setText("Schedule another observation");
            button(root, "Save check").doClick();
        });
        assertThat(feedback(root)).contains("Next check date cannot precede check date");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Next check date").setText("2027-04-08");
            button(root, "Save check").doClick();
        });
        await(() -> feedback(root).contains("complete hive check draft kept"));
        assertThat(area(root, "hiveCheck.actions").getText())
                .isEqualTo("Schedule another observation");
        assertThat(tab.applicationExitWarning()).contains("unsaved hive check draft");
        onEdt(() -> button(root, "Save check").doClick());
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("Hive 12"));
        assertThat(tab.applicationExitWarning()).isNull();
        org.mockito.ArgumentCaptor<HiveCheck> draft =
                org.mockito.ArgumentCaptor.forClass(HiveCheck.class);
        verify(service, times(2)).create(draft.capture());
        assertThat(draft.getValue().getApiaryName()).isEqualTo("North orchard");
        assertThat(draft.getValue().getCheckDate()).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(draft.getValue().getNextCheckDate()).isEqualTo(LocalDate.of(2027, 4, 8));
        assertThat(draft.getValue().getStatus()).isEqualTo(HiveCheckStatus.NEEDS_FOLLOW_UP);
        assertThat(draft.getValue().getObservations())
                .isEqualTo("Queen seen; brood pattern uneven");
        assertThat(draft.getValue().getActions()).isEqualTo("Schedule another observation");
    }

    @Test
    void nextCheckCarriesHiveIdentityButClearsDatedVisitAndProtectsDraft() throws Exception {
        HiveCheckService service = mock(HiveCheckService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "Hive 12")));
        when(service.create(any(HiveCheck.class))).thenAnswer(invocation -> {
            HiveCheck result = invocation.getArgument(0);
            ReflectionTestUtils.setField(result, "id", 22L);
            return result;
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(0);
        RecordingHost host = new RecordingHost();
        HiveCheckTab tab = new HiveCheckTab(service, host, dialogs);
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            field(root, "Hive label").setText("Unfinished edit");
            button(root, "Start next check").doClick();
        });
        assertThat(field(root, "Hive label").getText()).isEqualTo("Unfinished edit");
        assertThat(table(root).getSelectedRow()).isZero();
        onEdt(() -> {
            field(root, "Hive label").setText("Hive 12");
            button(root, "Start next check").doClick();
        });
        assertThat(field(root, "Apiary name").getText()).isEqualTo("North orchard");
        assertThat(field(root, "Hive label").getText()).isEqualTo("Hive 12");
        assertThat(field(root, "Purpose").getText()).isEqualTo("Spring check");
        assertThat(field(root, "Check date").getText()).isEmpty();
        assertThat(field(root, "Next check date").getText()).isEmpty();
        assertThat(combo(root).getSelectedItem()).isEqualTo(HiveCheckStatus.NOT_ASSESSED);
        assertThat(area(root, "hiveCheck.observations").getText()).isEmpty();
        assertThat(area(root, "hiveCheck.actions").getText()).isEmpty();
        assertThat(table(root).getSelectedRow()).isEqualTo(-1);
        assertThat(tab.applicationExitWarning()).contains("unsaved hive check draft");
        onEdt(() -> button(root, "Save check").doClick());
        assertThat(feedback(root)).contains("Enter a check date");
        verify(service, never()).create(any());
        onEdt(() -> {
            field(root, "Check date").setText("2027-04-15");
            button(root, "Save check").doClick();
        });
        await(() -> !tab.hasPendingSave() && host.lastStatus.contains("saved"));
        org.mockito.ArgumentCaptor<HiveCheck> draft =
                org.mockito.ArgumentCaptor.forClass(HiveCheck.class);
        verify(service).create(draft.capture());
        assertThat(draft.getValue().getApiaryName()).isEqualTo("North orchard");
        assertThat(draft.getValue().getHiveLabel()).isEqualTo("Hive 12");
        assertThat(draft.getValue().getCheckDate()).isEqualTo(LocalDate.of(2027, 4, 15));
        assertThat(draft.getValue().getNextCheckDate()).isNull();
        assertThat(draft.getValue().getStatus()).isEqualTo(HiveCheckStatus.NOT_ASSESSED);
    }

    @Test
    void selectedSavedCheckCanBeEditedWithoutCreatingAnotherVisit() throws Exception {
        HiveCheckService service = mock(HiveCheckService.class);
        when(service.list()).thenReturn(List.of(saved(11L, "Hive 12")));
        when(service.update(eq(11L), any(HiveCheck.class))).thenAnswer(invocation -> {
            HiveCheck result = invocation.getArgument(1);
            ReflectionTestUtils.setField(result, "id", 11L);
            return result;
        });
        HiveCheckTab tab = new HiveCheckTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> {
            table(root).setRowSelectionInterval(0, 0);
            area(root, "hiveCheck.observations").setText("New forage visible");
            button(root, "Save check").doClick();
        });
        await(() -> !tab.hasPendingSave() && feedback(root).contains("saved"));
        org.mockito.ArgumentCaptor<HiveCheck> draft =
                org.mockito.ArgumentCaptor.forClass(HiveCheck.class);
        verify(service).update(eq(11L), draft.capture());
        assertThat(draft.getValue().getObservations()).isEqualTo("New forage visible");
        verify(service, never()).create(any());
    }

    @Test
    void statusDragAndClickRejectForeignStaleDirtyAndViewerActions() throws Exception {
        AtomicReference<HiveCheck> stored = new AtomicReference<>(saved(21L, "Hive 12"));
        HiveCheckService service = mock(HiveCheckService.class);
        when(service.list()).thenAnswer(invocation -> List.of(stored.get()));
        when(service.updateStatus(eq(21L), any(HiveCheckStatus.class)))
                .thenAnswer(invocation -> {
                    HiveCheck updated = stored.get().snapshot();
                    updated.setStatus(invocation.getArgument(1));
                    stored.set(updated);
                    return updated;
                });
        HiveCheckTab tab = new HiveCheckTab(service, new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        onEdt(tab::initialize);
        await(() -> table(root).getRowCount() == 1);
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        JButton followUp = named(root, "hiveCheck.statusTarget.NEEDS_FOLLOW_UP", JButton.class);
        JButton active = named(root, "hiveCheck.statusTarget.ACTIVE", JButton.class);
        assertThat(followUp.getToolTipText()).contains("Status change saves immediately");
        onEdt(() -> area(root, "hiveCheck.observations").setText("Unsaved note"));
        assertThat(dragPayload(root)).isNull();
        onEdt(() -> area(root, "hiveCheck.observations")
                .setText("Queen seen; brood pattern steady"));
        Transferable payload = dragPayload(root);
        HiveCheckTab foreignTab = new HiveCheckTab(service, new RecordingHost());
        JComponent foreignRoot = onEdt(foreignTab::getUIComponent);
        onEdt(foreignTab::initialize);
        await(() -> table(foreignRoot).getRowCount() == 1);
        onEdt(() -> table(foreignRoot).setRowSelectionInterval(0, 0));
        Transferable foreign = dragPayload(foreignRoot);
        assertThat(onEdt(() -> followUp.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(followUp, foreign)))).isFalse();
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(followUp, payload);
        assertThat(onEdt(() -> followUp.getTransferHandler().canImport(drop))).isTrue();
        assertThat(onEdt(() -> followUp.getTransferHandler().importData(drop))).isTrue();
        await(() -> !tab.hasPendingSave()
                && stored.get().getStatus() == HiveCheckStatus.NEEDS_FOLLOW_UP);
        assertThat(stored.get().getActions()).isEqualTo("Check again next week");
        await(() -> "Needs follow up".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh checks").isEnabled());
        onEdt((Runnable) active::doClick);
        await(() -> !tab.hasPendingSave() && stored.get().getStatus() == HiveCheckStatus.ACTIVE);
        verify(service, times(2)).updateStatus(eq(21L), any(HiveCheckStatus.class));
        await(() -> "Active".equals(table(root).getValueAt(0, 5))
                && button(root, "Refresh checks").isEnabled());
        onEdt(() -> table(root).setRowSelectionInterval(0, 0));
        Transferable stale = dragPayload(root);
        long revision = (Long) ReflectionTestUtils.getField(tab, "listRevision");
        onEdt(tab::refreshData);
        await(() -> ((Long) ReflectionTestUtils.getField(tab, "listRevision")) > revision
                && button(root, "Refresh checks").isEnabled());
        assertThat(onEdt(() -> followUp.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(followUp, stale)))).isFalse();
        onEdt(() -> tab.applyRolePermissions(false));
        assertThat(followUp.isEnabled()).isFalse();
        assertThat(button(root, "Start next check").isEnabled()).isFalse();
        assertThat(button(root, "Save check").isEnabled()).isFalse();
        assertThat(button(root, "Refresh checks").isEnabled()).isTrue();
        verify(service, never()).create(any());
        verify(service, never()).update(any(), any());
        verify(service, never()).updateStatus(anyLong(), eq(HiveCheckStatus.OVERWINTERING));
    }

    private static Transferable dragPayload(JComponent root) throws Exception {
        return onEdt(() -> {
            Clipboard clipboard = new Clipboard("hive-status-test");
            table(root).getTransferHandler().exportToClipboard(
                    table(root), clipboard, TransferHandler.COPY);
            return clipboard.getContents(null);
        });
    }

    private static HiveCheck saved(Long id, String hiveLabel) {
        HiveCheck check = new HiveCheck("North orchard", hiveLabel,
                LocalDate.of(2027, 4, 1), "Spring check", HiveCheckStatus.ACTIVE,
                LocalDate.of(2027, 4, 8), "Queen seen; brood pattern steady",
                "Check again next week");
        ReflectionTestUtils.setField(check, "id", id);
        return check;
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
        return named(root, "hiveCheck.feedback", JLabel.class).getText();
    }

    private static JTextField field(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(field -> name.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea area(Component root, String name) {
        return named(root, name, JTextArea.class);
    }

    private static JComboBox<?> combo(Component root) {
        return named(root, "hiveCheck.status", JComboBox.class);
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static JTable table(Component root) {
        return named(root, "hiveCheck.checkTable", JTable.class);
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
            for (Component child : container.getComponents()) found.addAll(descendants(child, type));
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
