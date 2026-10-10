package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Inventory modal decisions preserve reviewed targets")
class InventoryDialogSafetyTest {

    @ParameterizedTest
    @CsvSource({
            "count, role", "count, selection", "count, snapshot",
            "edit, role", "edit, selection", "edit, snapshot",
            "delete, role", "delete, selection", "delete, snapshot"
    })
    void modalApprovalRechecksAccessSelectionAndCurrentValues(String action, String change)
            throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item rose = item(11L, "Nootka Rose", 10);
        Item dahlia = item(22L, "Dahlia Mix", 12);
        when(service.getAllItems()).thenReturn(List.of(rose, dahlia));
        SwingDialogHarness scripts = new SwingDialogHarness();
        if ("count".equals(action)) {
            scripts.enterSpinnerAndChoose("Count “Nootka Rose”",
                    "Counted quantity for Nootka Rose", 37, "Save count")
                    .choose("Count “Nootka Rose”", "Cancel");
        } else if ("edit".equals(action)) {
            scripts.confirmForm("Edit “Nootka Rose”", true, form -> form
                    .type("Selling price", "7.25")
                    .type("Item notes", "Keep this reviewed draft"))
                    .confirm("Edit “Nootka Rose”", false);
        } else {
            scripts.confirm("Delete inventory item?", true);
        }
        Fixture[] fixture = new Fixture[1];
        AtomicInteger decisions = new AtomicInteger();
        FarmDialogs dialogs = intercept(scripts, message -> {
            Fixture current = fixture[0];
            if (decisions.incrementAndGet() == 1) {
                switch (change) {
                    case "role" -> current.tab().applyRolePermissions(false);
                    case "selection" -> select(current.root(), "Dahlia Mix");
                    default -> {
                        when(service.getAllItems()).thenReturn(
                                List.of(item(11L, "Nootka Rose", 19), dahlia));
                        refreshInsideModal(current);
                    }
                }
            } else if ("count".equals(action)) {
                assertThat(find(message, JSpinner.class).getValue()).isEqualTo(37);
            } else {
                SwingUserHarness form = SwingUserHarness.on((JComponent) message);
                assertThat(form.fieldValue("Selling price")).isEqualTo("7.25");
                assertThat(form.fieldValue("Item notes")).isEqualTo("Keep this reviewed draft");
            }
        });
        fixture[0] = build(service, new TrackingHost(), dialogs);
        Fixture current = fixture[0];
        String actionButton = switch (action) {
            case "count" -> "Record stock count…";
            case "edit" -> "Edit selected";
            default -> "Delete selected";
        };
        SwingUtilities.invokeAndWait(() -> select(current.root(), "Nootka Rose"));
        current.user().click(actionButton);

        verify(service, never()).updateById(anyLong(), any(Item.class));
        verify(service, never()).editItem(anyInt(), any(Item.class));
        verify(service, never()).deleteById(anyLong());
        verify(service, never()).deleteItem(anyInt());
        assertThat(current.host().status).contains("No change was submitted", "review current inventory");
        assertThat(current.tab().hasPendingSave()).isFalse();
        if (!"delete".equals(action)) {
            assertThat(current.tab().applicationExitWarning()).contains("retained for review");
            SwingUtilities.invokeAndWait(() -> {
                current.tab().applyRolePermissions(true);
                select(current.root(), "Nootka Rose");
            });
            current.user().click(actionButton);
            assertThat(decisions).hasValue(2);
        } else {
            assertThat(current.tab().applicationExitWarning()).isNull();
        }
        scripts.assertComplete();
    }

    @Test
    void countNextWaitsForTheHostsInventoryRefreshAndUsesItsLatestQuantity() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item original = item(11L, "Nootka Rose", 10);
        Item saved = item(11L, "Nootka Rose", 37);
        Item next = item(22L, "Dahlia Mix", 12);
        Item refreshedNext = item(22L, "Dahlia Mix", 19);
        when(service.getAllItems()).thenReturn(
                List.of(original, next), List.of(saved, next), List.of(saved, refreshedNext));
        when(service.updateById(anyLong(), any(Item.class))).thenReturn(saved);
        SwingDialogHarness scripts = new SwingDialogHarness().choose("Count “Dahlia Mix”", "Cancel");
        CountDownLatch nextOpened = new CountDownLatch(1);
        FarmDialogs dialogs = intercept(scripts, message -> {
            assertThat(find(message, JSpinner.class).getValue()).isEqualTo(19);
            nextOpened.countDown();
        });
        TrackingHost host = new TrackingHost();
        Fixture fixture = build(service, host, dialogs);
        host.refresh = fixture.tab()::refreshData;
        SwingUtilities.invokeAndWait(() -> {
            JPanel showingPanel = mock(JPanel.class);
            when(showingPanel.isShowing()).thenReturn(true);
            ReflectionTestUtils.setField(fixture.tab(), "panel", showingPanel);
            select(fixture.root(), "Nootka Rose");
            fixture.tab().startSelectedStockCountSave(37, 27, true);
        });

        assertThat(nextOpened.await(3, TimeUnit.SECONDS)).isTrue();
        verify(service, times(3)).getAllItems();
        verify(service).updateById(org.mockito.ArgumentMatchers.eq(11L), any(Item.class));
        assertThat(fixture.tab().hasPendingSave()).isFalse();
        scripts.assertComplete();
    }

    private static Fixture build(InventoryService service, TrackingHost host, FarmDialogs dialogs)
            throws Exception {
        InventoryTab tab = new InventoryTab(service, host, dialogs);
        JComponent[] root = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        SwingUserHarness user = SwingUserHarness.on(root[0]);
        user.awaitButtonEnabled("Refresh", true);
        return new Fixture(tab, root[0], user, host);
    }

    /** Native modal dialogs keep dispatching events; reproduce that during the scripted answer. */
    private static void refreshInsideModal(Fixture fixture) {
        assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
        fixture.tab().refreshData();
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        Timer timer = new Timer(10, event -> {
            if (refreshButton(fixture.root()).isEnabled() || System.nanoTime() >= deadline) {
                loop.exit();
            }
        });
        timer.start();
        try {
            assertThat(loop.enter()).isTrue();
        } finally {
            timer.stop();
        }
        assertThat(refreshButton(fixture.root()).isEnabled()).isTrue();
        JTable table = find(fixture.root(), JTable.class);
        assertThat(table.getValueAt(table.getSelectedRow(), 5)).isEqualTo(19);
    }

    private static JButton refreshButton(Component component) {
        if (component instanceof JButton button && "Refresh".equals(button.getText())) {
            return button;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton match = refreshButton(child);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static void select(Component root, String name) {
        JTable table = find(root, JTable.class);
        for (int row = 0; row < table.getRowCount(); row++) {
            if (name.equals(table.getValueAt(row, 0))) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
        throw new AssertionError("Missing inventory product " + name);
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = find(child, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static FarmDialogs intercept(SwingDialogHarness scripts, Consumer<Component> beforeReturn) {
        return new FarmDialogs() {
            @Override
            public int showOption(Component parent, Object message, String title, int optionType,
                                  int messageType, Icon icon, Object[] options, Object initialValue) {
                int result = scripts.showOption(parent, message, title, optionType,
                        messageType, icon, options, initialValue);
                beforeReturn.accept((Component) message);
                return result;
            }

            @Override
            public int showConfirm(Component parent, Object message, String title,
                                   int optionType, int messageType) {
                int result = scripts.showConfirm(parent, message, title, optionType, messageType);
                beforeReturn.accept((Component) message);
                return result;
            }

            @Override
            public void showMessage(Component parent, Object message, String title, int messageType) {
                scripts.showMessage(parent, message, title, messageType);
            }
        };
    }

    private static Item item(long id, String name, int quantity) {
        return new Item(id, name, "Flowers/Plants", 4.5, "Per Stem", 1.2, quantity, "Field A");
    }

    private record Fixture(InventoryTab tab, JComponent root, SwingUserHarness user, TrackingHost host) {
    }

    private static final class TrackingHost implements TabHost {
        private String status;
        private Runnable refresh = () -> { };

        @Override public void refreshAll() { refresh.run(); }
        @Override public void selectTab(String title) { }
        @Override public void setStatus(String message) { status = message; }
        @Override public void runTrendAnalysis() { }
    }
}
