package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.presenter.InventoryItemPresenter;
import com.flowerfarm.model.Item;
import com.flowerfarm.repository.InMemoryInventoryRepository;
import com.flowerfarm.service.InventoryCsvExporter;
import com.flowerfarm.service.InventoryReportGenerator;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InventoryTab modern UI")
class InventoryTabUiTest {

    @Test
    @DisplayName("builds the shared page hierarchy and empty state headlessly")
    void buildsSharedHierarchyAndEmptyState() throws Exception {
        InventoryTab tab = new InventoryTab(emptyService(), new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(findNamed(root[0], "farm.pageHeader")).hasSize(1);
        assertThat(findNamed(root[0], "farm.section")).hasSize(3);
        assertThat(findNamed(root[0], "farm.emptyState"))
                .singleElement()
                .satisfies(component -> assertThat(component.isVisible()).isTrue());
        JTable table = find(root[0], JTable.class).get(0);
        assertThat(table.getRowHeight()).isEqualTo(FarmUi.TABLE_ROW_HEIGHT);
        assertThat(table.getAccessibleContext().getAccessibleName())
                .isEqualTo("Inventory stock table");
        assertThat(findNamed(root[0], "farm.statusBadge")).hasSize(2);
    }

    @Test
    @DisplayName("viewer mode disables mutations while keeping print and export actions")
    void viewerPermissionsPreserveReadActions() throws Exception {
        InventoryTab tab = new InventoryTab(emptyService(), new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.applyRolePermissions(false);
        });

        assertThat(button(root[0], "Edit selected").isEnabled()).isFalse();
        assertThat(button(root[0], "Record stock count…").isEnabled()).isFalse();
        assertThat(button(root[0], "Delete selected").isEnabled()).isFalse();
        assertThat(button(root[0], "Low-stock reorder…").isEnabled()).isTrue();
        assertThat(button(root[0], "Price list…").isEnabled()).isTrue();
        assertThat(button(root[0], "Export visible CSV…").isEnabled()).isFalse();
        assertThat(findNamed(root[0], "farm.statusBadge"))
                .extracting(component -> ((javax.swing.JLabel) component).getText())
                .contains("VIEWER · read and export only");
    }

    @Test
    @DisplayName("stock count action requires both write access and a selected product")
    void stockCountRequiresSelection() throws Exception {
        InventoryService service = mock(InventoryService.class);
        when(service.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 4.5,
                        "Per Stem", 1.2, 24, "Field A")));
        InventoryTab tab = new InventoryTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        boolean[] states = new boolean[2];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            states[0] = button(root[0], "Record stock count…").isEnabled();
            find(root[0], JTable.class).get(0).setRowSelectionInterval(0, 0);
            states[1] = button(root[0], "Record stock count…").isEnabled();
        });

        assertThat(states).containsExactly(false, true);
    }

    @Test
    @DisplayName("refresh preserves the worker's search and selected product")
    void refreshPreservesWorkingContext() throws Exception {
        InventoryService service = mock(InventoryService.class);
        when(service.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 4.5,
                        "Per Stem", 1.2, 24, "Field A"),
                new Item("Dahlia Mix", "Flowers/Plants", 3.5,
                        "Per Stem", 1.0, 12, "Field B")));
        InventoryTab tab = new InventoryTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        String[] state = new String[2];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTextField search = find(root[0], JTextField.class).stream()
                    .filter(field -> "Inventory search".equals(
                            field.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            JTable table = find(root[0], JTable.class).get(0);
            search.setText("Nootka");
            table.setRowSelectionInterval(0, 0);
            tab.refreshData();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTextField search = find(root[0], JTextField.class).stream()
                    .filter(field -> "Inventory search".equals(
                            field.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            JTable table = find(root[0], JTable.class).get(0);
            state[0] = search.getText();
            state[1] = String.valueOf(table.getValueAt(table.getSelectedRow(), 0));
        });

        assertThat(state).containsExactly("Nootka", "Nootka Rose");
    }

    @Test
    @DisplayName("operational handoff shows only requested products through refresh")
    void marketDayHandoffBuildsPersistentProductQueue() throws Exception {
        InventoryService service = mock(InventoryService.class);
        when(service.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 4.5,
                        "Per Stem", 1.2, 24, "Field A"),
                new Item("Dahlia Mix", "Flowers/Plants", 3.5,
                        "Per Stem", 1.0, 4, "Field B"),
                new Item("Sunflower", "Flowers/Plants", 2.5,
                        "Per Stem", 0.8, 2, "Field C")));
        TrackingHost host = new TrackingHost();
        InventoryTab tab = new InventoryTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> tab.showProductQueue(
                List.of("Dahlia Mix", "Sunflower", "Anemone", "Dahlia Mix"),
                "Market Day", "Market Day"));

        JTable table = find(root[0], JTable.class).get(0);
        assertThat(table.getRowCount()).isEqualTo(2);
        assertThat(List.of(String.valueOf(table.getValueAt(0, 0)),
                        String.valueOf(table.getValueAt(1, 0))))
                .containsExactlyInAnyOrder("Dahlia Mix", "Sunflower");
        assertThat(table.getSelectedRow()).isNotNegative();
        assertThat(inventoryStatus(root[0]).getText())
                .contains("2 focused products · 3 requested");
        JButton returnButton = (JButton) findNamed(
                root[0], "inventory.returnHandoff").get(0);
        assertThat(returnButton.isVisible()).isTrue();
        assertThat(returnButton.getText()).isEqualTo("Return to Market Day");
        assertThat(((JLabel) findNamed(root[0], "inventory.handoffStatus").get(0)).getText())
                .contains("3 requested products", "return to Market Day");
        JLabel missingStatus = (JLabel) findNamed(
                root[0], "inventory.missingProductStatus").get(0);
        JButton addMissing = (JButton) findNamed(
                root[0], "inventory.addMissingProduct").get(0);
        JComboBox<?> missingChoice = (JComboBox<?>) findNamed(
                root[0], "inventory.missingProductChoice").get(0);
        assertThat(missingStatus.isVisible()).isTrue();
        assertThat(missingStatus.getText()).contains("1 requested product", "missing");
        assertThat(missingStatus.getAccessibleContext().getAccessibleDescription())
                .contains("Anemone", "add its catalog details");
        assertThat(missingChoice.getSelectedItem()).isEqualTo("Anemone");
        SwingUtilities.invokeAndWait(addMissing::doClick);
        assertThat(host.missingProduct).isEqualTo("Anemone");
        assertThat(host.status).contains(
                "missing focused product", "Anemone", "return to the stock queue");

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitInventoryRefresh(root[0]);
        assertThat(table.getRowCount()).isEqualTo(2);
        assertThat(inventoryStatus(root[0]).getText())
                .contains("2 focused products · 3 requested");
        SwingUtilities.invokeAndWait(returnButton::doClick);
        assertThat(host.selectedTab).isEqualTo("Market Day");
        assertThat(host.status).contains(
                "Returned to Market Day", "Refresh or rebuild", "current stock");

        SwingUtilities.invokeAndWait(() -> find(root[0], JTextField.class).stream()
                .filter(field -> "Inventory search".equals(
                        field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().setText("Nootka"));
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("Nootka Rose");
        assertThat(inventoryStatus(root[0]).getText()).isEqualTo("1 of 3 items shown");
        assertThat(returnButton.isVisible()).isFalse();
        assertThat(addMissing.isVisible()).isFalse();
    }

    @Test
    @DisplayName("refresh preserves the exact persisted product when names are duplicated")
    void refreshPreservesSelectionById() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item first = new Item(11L, "Market Bouquet", "Flowers/Plants", 25,
                "Per Unit", 8, 5, "Front cooler");
        Item second = new Item(22L, "Market Bouquet", "Flowers/Plants", 30,
                "Per Unit", 10, 20, "Wholesale cooler");
        when(service.getAllItems()).thenReturn(
                List.of(first, second), List.of(first, second));
        InventoryTab tab = new InventoryTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        String[] state = new String[2];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).get(0);
            table.setRowSelectionInterval(1, 1);
            tab.refreshData();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).get(0);
            state[0] = String.valueOf(table.getValueAt(table.getSelectedRow(), 5));
            state[1] = find(root[0], javax.swing.JLabel.class).stream()
                    .filter(label -> "Inventory results and selected product".equals(
                            label.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow().getText();
        });

        assertThat(state[0]).isEqualTo("20");
        assertThat(state[1]).contains("Market Bouquet", "20 Per Unit", "2 items");
    }

    @Test
    @DisplayName("low-stock mode builds a count queue and composes with search")
    void lowStockModeBuildsCountQueue() throws Exception {
        InventoryService service = mock(InventoryService.class);
        when(service.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 4.5,
                        "Per Stem", 1.2, 2, "Field A"),
                new Item("Dahlia Mix", "Flowers/Plants", 3.5,
                        "Per Stem", 1.0, 10, "Field B"),
                new Item("Sunflower", "Flowers/Plants", 2.5,
                        "Per Stem", 0.8, 18, "Field C")));
        TrackingHost host = new TrackingHost();
        InventoryTab tab = new InventoryTab(service, host);
        JComponent[] root = new JComponent[1];
        int[] rows = new int[3];
        String[] firstItem = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).get(0);
            JToggleButton lowStock = find(root[0], JToggleButton.class).stream()
                    .filter(button -> "inventory.lowStockOnly".equals(button.getName()))
                    .findFirst().orElseThrow();
            lowStock.doClick();
            rows[0] = table.getRowCount();
            firstItem[0] = String.valueOf(table.getValueAt(0, 0));
            JTextField search = find(root[0], JTextField.class).stream()
                    .filter(field -> "Inventory search".equals(
                            field.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            search.setText("Dahlia");
            rows[1] = table.getRowCount();
            button(root[0], "Clear search").doClick();
            rows[2] = table.getRowCount();
        });

        assertThat(rows).containsExactly(2, 1, 2);
        assertThat(firstItem[0]).isEqualTo("Nootka Rose");
        assertThat(button(root[0], "Record stock count…").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "farm.statusBadge").stream()
                .filter(component -> ((javax.swing.JLabel) component).getText()
                        .contains("low-stock")))
                .singleElement()
                .satisfies(component -> assertThat(((javax.swing.JLabel) component).getText())
                        .contains("2 low-stock items", "at or below 10"));
        assertThat(host.status).contains("count queue ready", "lowest quantity first");
    }

    @Test
    @DisplayName("inventory refresh runs off the UI thread and coalesces a newer reload")
    void inventoryRefreshIsResponsiveAndDiscardsStaleResult() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item stale = new Item(11L, "Old Count", "Supplies", 1,
                "Each", 0.5, 2, "stale");
        Item current = new Item(11L, "Current Count", "Supplies", 1,
                "Each", 0.5, 8, "current");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        when(service.getAllItems()).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                return List.of(stale);
            }
            return List.of(current);
        });
        TrackingHost host = new TrackingHost();
        InventoryTab tab = new InventoryTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startInventoryRefresh();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Refresh").isEnabled()).isFalse();
            tab.startInventoryRefresh();
            assertThat(inventoryStatus(root[0]).getText()).startsWith("Refresh queued");
        });
        release.countDown();
        awaitInventoryRefresh(root[0]);

        JTable table = find(root[0], JTable.class).get(0);
        assertThat(calledOnUiThread).isFalse();
        assertThat(calls).hasValue(2);
        verify(service, times(2)).getAllItems();
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("Current Count");
        assertThat(table.getValueAt(0, 5)).isEqualTo(8);
        assertThat(host.status).contains("Inventory refreshed", "1 item");
    }

    @Test
    @DisplayName("inventory refresh failure preserves rows, filters, selection, and retry")
    void inventoryRefreshFailurePreservesWorkingSnapshot() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item item = new Item(11L, "Nootka Rose", "Flowers/Plants", 4.5,
                "Per Stem", 1.2, 12, "Field A");
        when(service.getAllItems()).thenReturn(List.of(item))
                .thenThrow(new IllegalStateException("inventory database unavailable"));
        TrackingHost host = new TrackingHost();
        InventoryTab tab = new InventoryTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTextField search = find(root[0], JTextField.class).stream()
                    .filter(field -> "Inventory search".equals(
                            field.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            search.setText("Nootka");
            find(root[0], JTable.class).get(0).setRowSelectionInterval(0, 0);
            tab.startInventoryRefresh();
        });
        awaitInventoryStatus(root[0], "Inventory refresh failed · retry");

        JTable table = find(root[0], JTable.class).get(0);
        JTextField search = find(root[0], JTextField.class).stream()
                .filter(field -> "Inventory search".equals(
                        field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getSelectedRow()).isZero();
        assertThat(search.getText()).isEqualTo("Nootka");
        assertThat(button(root[0], "Record stock count…").isEnabled()).isTrue();
        assertThat(inventoryStatus(root[0]).getAccessibleContext()
                .getAccessibleDescription())
                .contains("inventory database unavailable", "Existing rows and filters were kept");
        assertThat(host.status).contains("failed", "Existing rows were kept", "retry");
    }

    @Test
    @DisplayName("low-stock report prepares off the UI thread and blocks duplicate document work")
    void lowStockReportRunsInBackgroundOnce() throws Exception {
        InventoryService service = mock(InventoryService.class);
        InventoryService.LowStockReport report = new InventoryService.LowStockReport(
                LocalDate.now(), 10, 4, 2, 35, List.of(), "Two products need restocking");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.buildLowStockReport(10)).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return report;
        });
        InventoryTab tab = new InventoryTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startLowStockReport(10);
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Low-stock reorder…").isEnabled()).isFalse();
            assertThat(button(root[0], "Price list…").isEnabled()).isFalse();
            tab.startLowStockReport(10);
        });

        release.countDown();
        awaitDocumentStatus(root[0], "Low-stock report ready · 2 products");

        assertThat(calledOnEdt).isFalse();
        assertThat(button(root[0], "Low-stock reorder…").isEnabled()).isTrue();
        verify(service).buildLowStockReport(10);
    }

    @Test
    @DisplayName("visible inventory CSV uses a captured snapshot and quotes every unsafe cell")
    void visibleCsvExportIsCapturedAndStandardsCompliant() throws Exception {
        InventoryTab tab = new InventoryTab(mock(InventoryService.class), new TestHost());
        JComponent[] root = new JComponent[1];
        Path destination = Files.createTempFile("inventory-visible-responsive", ".csv");
        List<List<String>> rows = List.of(List.of(
                "Bouquet, Large", "Flowers/Plants", "25.00", "Each",
                "8.00", "4", "Needs \"cooler\""));

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startVisibleCsvExport(destination.toFile(), rows);
            assertThat(button(root[0], "Export visible CSV…").isEnabled()).isFalse();
            tab.startVisibleCsvExport(destination.toFile(), rows);
        });
        awaitDocumentStatus(root[0], "Inventory CSV saved · 1 visible rows");

        assertThat(Files.readAllLines(destination)).containsExactly(
                "Name,Category,Price,Unit,Cost,Quantity,Notes",
                "\"Bouquet, Large\",Flowers/Plants,25.00,Each,8.00,4,\"Needs \"\"cooler\"\"\"");
        assertThat(button(root[0], "Low-stock reorder…").isEnabled()).isTrue();
        Files.deleteIfExists(destination);
    }

    @Test
    @DisplayName("inventory report failure preserves search and restores report controls")
    void inventoryReportFailureKeepsWorkingState() throws Exception {
        InventoryService service = mock(InventoryService.class);
        when(service.buildPriceListReport(true))
                .thenThrow(new IllegalStateException("pricing database unavailable"));
        TrackingHost host = new TrackingHost();
        InventoryTab tab = new InventoryTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            find(root[0], JTextField.class).stream()
                    .filter(field -> "Inventory search".equals(
                            field.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow().setText("Nootka");
            tab.startPriceListReport(true);
        });
        awaitDocumentStatus(root[0], "Report or export failed · retry");

        javax.swing.JLabel status = (javax.swing.JLabel) findNamed(
                root[0], "inventory.documentStatus").get(0);
        assertThat(find(root[0], JTextField.class).stream()
                .filter(field -> "Inventory search".equals(
                        field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().getText()).isEqualTo("Nootka");
        assertThat(button(root[0], "Low-stock reorder…").isEnabled()).isTrue();
        assertThat(button(root[0], "Price list…").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("pricing database unavailable", "Inventory rows", "search",
                        "count work were kept");
        assertThat(host.status).contains("failed", "Current inventory work was kept", "retry");
    }

    @Test
    @DisplayName("inventory edit saves off the UI thread once using a captured draft")
    void inventoryEditIsResponsiveAndCaptured() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item current = new Item(11L, "Nootka Rose", "Flowers/Plants", 4.5,
                "Per Stem", 1.2, 2, "Field A");
        Item saved = new Item(11L, "Nootka Rose Premium", "Flowers/Plants", 5.0,
                "Per Stem", 1.2, 2, "Field A");
        when(service.getAllItems()).thenReturn(List.of(current), List.of(saved));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(service.updateById(org.mockito.ArgumentMatchers.eq(11L), any(Item.class)))
                .thenAnswer(invocation -> {
                    calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
                    entered.countDown();
                    assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                    return saved;
                });
        InventoryTab tab = new InventoryTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        InventoryItemPresenter.ItemDraft draft = new InventoryItemPresenter.ItemDraft(
                "Nootka Rose Premium", "Flowers/Plants", "Nootka", "5.00",
                "Per Stem", "1.20", "2", "Field A");

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).get(0);
            int row = java.util.stream.IntStream.range(0, table.getRowCount())
                    .filter(index -> "Nootka Rose".equals(table.getValueAt(index, 0)))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(row, row);
            tab.startSelectedItemEdit(draft);
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Saving edit…").isEnabled()).isFalse();
            assertThat(find(root[0], JTable.class).get(0).isEnabled()).isFalse();
            tab.applyRolePermissions(true);
            assertThat(button(root[0], "Saving edit…").isEnabled()).isFalse();
            tab.startSelectedItemEdit(draft);
        });
        release.countDown();
        awaitInventoryStatus(root[0], "Nootka Rose Premium updated");

        JTable table = find(root[0], JTable.class).get(0);
        assertThat(calledOnUiThread).isFalse();
        verify(service, times(1)).updateById(
                org.mockito.ArgumentMatchers.eq(11L), any(Item.class));
        assertThat(table.getValueAt(table.getSelectedRow(), 0))
                .isEqualTo("Nootka Rose Premium");
        assertThat(button(root[0], "Edit selected").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("failed inventory edit retains every field for an exact retry")
    void inventoryEditFailureRetainsDraft() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item current = new Item(11L, "Nootka Rose", "Flowers/Plants", 4.5,
                "Per Stem", 1.2, 2, "Field A");
        Item saved = new Item(11L, "Nootka Rose Premium", "Flowers/Plants", 5.0,
                "Per Stem", 1.2, 7, "Cooler row 2");
        when(service.getAllItems()).thenReturn(List.of(current), List.of(saved));
        when(service.updateById(org.mockito.ArgumentMatchers.eq(11L), any(Item.class)))
                .thenThrow(new IllegalStateException("inventory database busy"))
                .thenReturn(saved);
        TrackingHost host = new TrackingHost();
        InventoryTab tab = new InventoryTab(service, host);
        JComponent[] root = new JComponent[1];
        InventoryItemPresenter.ItemDraft draft = new InventoryItemPresenter.ItemDraft(
                "Nootka Rose Premium", "Flowers/Plants", "Nootka", "5.00",
                "Per Stem", "1.20", "7", "Cooler row 2");

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            find(root[0], JTable.class).get(0).setRowSelectionInterval(0, 0);
            tab.startSelectedItemEdit(draft);
        });
        awaitInventoryStatus(root[0], "Nootka Rose not updated · retry edit");

        assertThat(inventoryStatus(root[0]).getAccessibleContext()
                .getAccessibleDescription())
                .contains("inventory database busy", "Every edited field was retained",
                        "Edit selected", "retry");
        assertThat(host.status).contains("not saved", "complete draft", "retry");

        SwingUtilities.invokeAndWait(() -> tab.startSelectedItemEdit(null));
        awaitInventoryStatus(root[0], "Nootka Rose Premium updated");

        verify(service, times(2)).updateById(
                org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.argThat(item ->
                        "Nootka Rose Premium".equals(item.getName())
                                && item.getQuantity() == 7
                                && item.getNotes().contains("Cooler row 2")));
    }

    @Test
    @DisplayName("duplicate inventory rename keeps the draft and focuses the existing SKU")
    void inventoryDuplicateRenameRoutesToExistingSku() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item current = new Item(11L, "Nootka Rose", "Flowers/Plants", 4.5,
                "Per Stem", 1.2, 2, "Field A");
        Item conflict = new Item(22L, "Dahlia Mix", "Flowers/Plants", 3.5,
                "Per Stem", 1.0, 12, "Field B");
        when(service.getAllItems()).thenReturn(List.of(current, conflict));
        when(service.updateById(org.mockito.ArgumentMatchers.eq(11L), any(Item.class)))
                .thenThrow(new InventoryService.DuplicateItemNameException(22L, "Dahlia Mix"));
        TrackingHost host = new TrackingHost();
        InventoryTab tab = new InventoryTab(service, host);
        JComponent[] root = new JComponent[1];
        InventoryItemPresenter.ItemDraft draft = new InventoryItemPresenter.ItemDraft(
                "dahlia mix", "Flowers/Plants", "Nootka", "5.00",
                "Per Stem", "1.20", "7", "Keep this full draft");

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).get(0);
            int row = java.util.stream.IntStream.range(0, table.getRowCount())
                    .filter(index -> "Nootka Rose".equals(table.getValueAt(index, 0)))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(row, row);
            tab.startSelectedItemEdit(draft);
        });
        awaitInventoryStatus(root[0], "Nootka Rose not renamed · name already used");

        assertThat(inventoryStatus(root[0]).getAccessibleContext()
                .getAccessibleDescription())
                .contains("already contains", "Every edited field was retained",
                        "Edit selected", "Review existing SKU");
        assertThat(button(root[0], "Review existing SKU").isEnabled()).isTrue();
        assertThat(host.status).contains("already contains “Dahlia Mix”",
                "complete draft was kept");

        SwingUtilities.invokeAndWait(() -> button(root[0], "Review existing SKU").doClick());

        JTable table = find(root[0], JTable.class).get(0);
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(table.getSelectedRow(), 0)).isEqualTo("Dahlia Mix");
        assertThat(inventoryStatus(root[0]).getText())
                .contains("1 focused product", "1 requested");
        assertThat(host.status).contains("Reviewing existing Inventory SKU “Dahlia Mix”",
                "edit draft remains available");
        verify(service).updateById(org.mockito.ArgumentMatchers.eq(11L), any(Item.class));
    }

    @Test
    @DisplayName("inventory deletion runs off the UI thread once and clears the target")
    void inventoryDeleteIsResponsiveAndCaptured() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item current = new Item(11L, "Spent Tulips", "Flowers/Plants", 2.5,
                "Per Stem", 0.8, 0, "Remove after season");
        when(service.getAllItems()).thenReturn(List.of(current), List.of());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        doAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(service).deleteById(11L);
        InventoryTab tab = new InventoryTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            find(root[0], JTable.class).get(0).setRowSelectionInterval(0, 0);
            tab.startSelectedItemDelete();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Deleting…").isEnabled()).isFalse();
            assertThat(find(root[0], JTable.class).get(0).isEnabled()).isFalse();
            tab.startSelectedItemDelete();
        });
        release.countDown();
        awaitInventoryStatus(root[0], "Spent Tulips deleted");

        JTable table = find(root[0], JTable.class).get(0);
        assertThat(calledOnUiThread).isFalse();
        verify(service, times(1)).deleteById(11L);
        assertThat(table.getRowCount()).isZero();
        assertThat(table.getSelectedRow()).isEqualTo(-1);
    }

    @Test
    @DisplayName("failed inventory deletion preserves the exact selected product")
    void inventoryDeleteFailurePreservesTarget() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item current = new Item(11L, "Nootka Rose", "Flowers/Plants", 4.5,
                "Per Stem", 1.2, 2, "Field A");
        when(service.getAllItems()).thenReturn(List.of(current));
        doThrow(new IllegalStateException("inventory database busy"))
                .when(service).deleteById(11L);
        TrackingHost host = new TrackingHost();
        InventoryTab tab = new InventoryTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            find(root[0], JTable.class).get(0).setRowSelectionInterval(0, 0);
            tab.startSelectedItemDelete();
        });
        awaitInventoryStatus(root[0], "Nootka Rose not deleted · retry available");

        JTable table = find(root[0], JTable.class).get(0);
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.isEnabled()).isTrue();
        assertThat(button(root[0], "Delete selected").isEnabled()).isTrue();
        assertThat(inventoryStatus(root[0]).getAccessibleContext()
                .getAccessibleDescription())
                .contains("inventory database busy", "remains selected",
                        "no deletion result", "retry");
        assertThat(host.status).contains("not deleted", "remains selected", "retry");
    }

    @Test
    @DisplayName("stock count saves off the UI thread once and advances by stable identity")
    void stockCountSaveIsResponsiveAndAdvancesSafely() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item first = new Item(11L, "Nootka Rose", "Flowers/Plants", 4.5,
                "Per Stem", 1.2, 2, "Field A");
        Item saved = new Item(11L, "Nootka Rose", "Flowers/Plants", 4.5,
                "Per Stem", 1.2, 7, "Field A");
        Item next = new Item(22L, "Dahlia Mix", "Flowers/Plants", 3.5,
                "Per Stem", 1.0, 10, "Field B");
        when(service.getAllItems()).thenReturn(
                List.of(first, next), List.of(saved, next));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        doAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return saved;
        }).when(service).updateById(org.mockito.ArgumentMatchers.eq(11L), any(Item.class));
        TrackingHost host = new TrackingHost();
        InventoryTab tab = new InventoryTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            find(root[0], JTable.class).get(0).setRowSelectionInterval(0, 0);
            tab.startSelectedStockCountSave(7, 5, true);
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Saving count…").isEnabled()).isFalse();
            assertThat(find(root[0], JTable.class).get(0).isEnabled()).isFalse();
            tab.applyRolePermissions(true);
            assertThat(button(root[0], "Saving count…").isEnabled()).isFalse();
            tab.startSelectedStockCountSave(7, 5, true);
        });
        release.countDown();
        awaitInventoryStatus(root[0], "Nootka Rose saved · 7 Per Stem");

        JTable table = find(root[0], JTable.class).get(0);
        assertThat(calledOnUiThread).isFalse();
        verify(service, times(1)).updateById(
                org.mockito.ArgumentMatchers.eq(11L), any(Item.class));
        assertThat(table.getValueAt(table.getSelectedRow(), 0)).isEqualTo("Dahlia Mix");
        assertThat(button(root[0], "Record stock count…").isEnabled()).isTrue();
        assertThat(inventoryStatus(root[0])
                .getAccessibleContext().getAccessibleDescription())
                .contains("adjusted up by 5");
        assertThat(host.status).contains("Counted “Nootka Rose”", "7 Per Stem");
    }

    @Test
    @DisplayName("failed stock count keeps the product and entered quantity retryable")
    void stockCountFailureKeepsRetryContext() throws Exception {
        InventoryService service = mock(InventoryService.class);
        Item item = new Item(11L, "Nootka Rose", "Flowers/Plants", 4.5,
                "Per Stem", 1.2, 2, "Field A");
        when(service.getAllItems()).thenReturn(List.of(item));
        doThrow(new IllegalStateException("inventory database unavailable"))
                .when(service).updateById(
                        org.mockito.ArgumentMatchers.eq(11L), any(Item.class));
        TrackingHost host = new TrackingHost();
        InventoryTab tab = new InventoryTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitInventoryRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            find(root[0], JTable.class).get(0).setRowSelectionInterval(0, 0);
            tab.startSelectedStockCountSave(6, 4, false);
        });
        awaitInventoryStatus(root[0], "Nootka Rose not saved · retry count");

        JTable table = find(root[0], JTable.class).get(0);
        javax.swing.JLabel result = inventoryStatus(root[0]);
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.isEnabled()).isTrue();
        assertThat(button(root[0], "Record stock count…").isEnabled()).isTrue();
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("inventory database unavailable", "entered count of 6 Per Stem",
                        "retained for retry");
        assertThat(host.status).contains("not saved", "retry");
    }

    private static void awaitInventoryStatus(Component root, String expected) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] found = new boolean[1];
            SwingUtilities.invokeAndWait(() -> found[0] =
                    expected.equals(inventoryStatus(root).getText()));
            if (found[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for " + expected);
    }

    private static void awaitDocumentStatus(Component root, String expected) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] found = new boolean[1];
            SwingUtilities.invokeAndWait(() -> found[0] = expected.equals(
                    ((javax.swing.JLabel) findNamed(
                            root, "inventory.documentStatus").get(0)).getText()));
            if (found[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for " + expected);
    }

    private static void awaitInventoryRefresh(Component root) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] complete = new boolean[1];
            SwingUtilities.invokeAndWait(() -> {
                String status = inventoryStatus(root).getText();
                complete[0] = button(root, "Refresh").isEnabled()
                        && !status.startsWith("Refreshing inventory")
                        && !status.startsWith("Refresh queued");
            });
            if (complete[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for inventory refresh");
    }

    private static javax.swing.JLabel inventoryStatus(Component root) {
        return find(root, javax.swing.JLabel.class).stream()
                .filter(label -> "Inventory results and selected product".equals(
                        label.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static InventoryService emptyService() {
        return new InventoryService(
                new InMemoryInventoryRepository(),
                "target/no-inventory-tab-ui-seed.csv",
                new InventoryReportGenerator(),
                new InventoryCsvExporter());
    }

    private static JButton button(Component root, String text) {
        return find(root, JButton.class).stream()
                .filter(candidate -> text.equals(candidate.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static List<Component> findNamed(Component root, String name) {
        List<Component> matches = new ArrayList<>();
        visit(root, component -> {
            if (name.equals(component.getName())) {
                matches.add(component);
            }
        });
        return matches;
    }

    private static <T extends Component> List<T> find(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        visit(root, component -> {
            if (type.isInstance(component)) {
                matches.add(type.cast(component));
            }
        });
        return matches;
    }

    private static void visit(Component component,
                              java.util.function.Consumer<Component> consumer) {
        consumer.accept(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                visit(child, consumer);
            }
        }
    }

    private static class TestHost implements TabHost {
        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
    }

    private static final class TrackingHost extends TestHost {
        private String status;
        private String selectedTab;
        private String missingProduct;

        @Override public void setStatus(String message) {
            status = message;
        }

        @Override public void selectTab(String tabTitle) {
            selectedTab = tabTitle;
        }

        @Override public void showAddInventoryItem(String productName) {
            missingProduct = productName;
            selectTab("Add Item");
        }
    }
}
