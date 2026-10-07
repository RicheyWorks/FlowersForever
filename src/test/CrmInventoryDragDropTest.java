package com.flowerfarm.gui.tabs;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDate;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrmInventoryDragDropTest {

    @Test
    void draggedStockFillsOnlyDraftAndKeepsManualEditing() throws Exception {
        Fixture fixture = open();
        SwingUtilities.invokeAndWait(() -> {
            JList<?> list = named(fixture.root, "crm.inventoryDragList", JList.class);
            JComponent target = named(fixture.root, "crm.inventoryDropTarget", JComponent.class);
            list.setSelectedIndex(0);
            Clipboard clipboard = new Clipboard("inventory drag test");
            list.getTransferHandler().exportToClipboard(list, clipboard, TransferHandler.COPY);
            try {
                assertThat(clipboard.getData(DataFlavor.stringFlavor)).isEqualTo("Dahlia Mix");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            TransferHandler.TransferSupport drop = new TransferHandler.TransferSupport(
                    target, new StringSelection("Dahlia Mix"));
            assertThat(target.getTransferHandler().canImport(drop)).isTrue();
            assertThat(target.getTransferHandler().importData(drop)).isTrue();
            assertThat(field(fixture.root, "Order product name").getText())
                    .isEqualTo("Dahlia Mix");
            assertThat(field(fixture.root, "Order unit price").getText())
                    .isEqualTo("3.50");
            assertThat(field(fixture.root, "Order unit").getText())
                    .isEqualTo("stems");
            field(fixture.root, "Order quantity").setText("12");
            assertThat(field(fixture.root, "Order quantity").getText()).isEqualTo("12");
            assertThat(fixture.host.status).contains("Review quantity", "save");
        });
    }

    @Test
    void dropCannotReplaceUnfinishedDraftOrUseStaleProduct() throws Exception {
        Fixture fixture = open();
        SwingUtilities.invokeAndWait(() -> {
            JComponent target = named(fixture.root, "crm.inventoryDropTarget", JComponent.class);
            JTextField product = field(fixture.root, "Order product name");
            product.setText("Custom bouquet");
            TransferHandler.TransferSupport second = new TransferHandler.TransferSupport(
                    target, new StringSelection("Dahlia Mix"));
            assertThat(target.getTransferHandler().importData(second)).isFalse();
            assertThat(product.getText()).isEqualTo("Custom bouquet");
            assertThat(fixture.host.status).contains("unfinished product draft");

            product.setText("");
            TransferHandler.TransferSupport stale = new TransferHandler.TransferSupport(
                    target, new StringSelection("Removed stock"));
            assertThat(target.getTransferHandler().importData(stale)).isFalse();
            assertThat(product.getText()).isEmpty();
            assertThat(fixture.host.status).contains("no longer in the current inventory list");
        });
    }

    @Test
    void viewerCannotDragOrDropInventoryIntoOrder() throws Exception {
        Fixture fixture = open();
        SwingUtilities.invokeAndWait(() -> {
            fixture.tab.applyRolePermissions(false);
            JList<?> list = named(fixture.root, "crm.inventoryDragList", JList.class);
            JButton button = named(fixture.root, "crm.useInventoryProduct", JButton.class);
            JComponent target = named(fixture.root, "crm.inventoryDropTarget", JComponent.class);
            TransferHandler.TransferSupport drop = new TransferHandler.TransferSupport(
                    target, new StringSelection("Dahlia Mix"));
            assertThat(list.isEnabled()).isFalse();
            assertThat(button.isEnabled()).isFalse();
            assertThat(target.getTransferHandler().canImport(drop)).isFalse();
            assertThat(target.getTransferHandler().importData(drop)).isFalse();
            assertThat(field(fixture.root, "Order product name").getText()).isEmpty();
        });
    }

    @Test
    void inventoryDragShelfFitsOrderEntryAtDesktopWidth() throws Exception {
        Fixture fixture = open();
        Path preview = Path.of("target", "ui-previews", "crm-inventory-drag.png");
        Files.createDirectories(preview.getParent());
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane workspace = named(fixture.root, "crm.workspace", JTabbedPane.class);
            workspace.setSelectedIndex(1);
            fixture.root.setSize(1280, 900);
            layoutDeep(fixture.root);
            JList<?> list = named(fixture.root, "crm.inventoryDragList", JList.class);
            JComponent target = named(fixture.root, "crm.inventoryDropTarget", JComponent.class);
            assertThat(list.getWidth()).isGreaterThan(150);
            assertThat(target.getWidth()).isGreaterThan(250);
            assertThat(target.getHeight()).isGreaterThan(20);
            BufferedImage image = new BufferedImage(1280, 900, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                fixture.root.printAll(graphics);
            } finally {
                graphics.dispose();
            }
            try {
                ImageIO.write(image, "png", preview.toFile());
            } catch (Exception exception) {
                throw new AssertionError("Could not render CRM inventory drag preview", exception);
            }
        });
    }

    private static Fixture open() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        when(inventory.getAllItems()).thenReturn(List.of(
                new Item("Dahlia Mix", "Flowers/Plants", 3.5, "stems", 1.0, 8, ""),
                new Item("Rose Bouquet", "Flowers/Plants", 15.0, "bouquets", 5.0, 4, "")));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, inventory, host);
        JComponent[] root = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        for (int attempt = 0; attempt < 100; attempt++) {
            String[] status = new String[1];
            SwingUtilities.invokeAndWait(() -> status[0] = named(
                    root[0], "crm.refreshStatus", JLabel.class).getText());
            if (status[0].startsWith("Customers and orders current")) {
                break;
            }
            Thread.sleep(20);
        }
        SwingUtilities.invokeAndWait(() -> assertThat(named(
                root[0], "crm.inventoryDragList", JList.class).getModel().getSize()).isEqualTo(2));
        return new Fixture(tab, root[0], host);
    }

    private static JTextField field(Component root, String accessibleName) {
        for (JTextField field : descendants(root, JTextField.class)) {
            if (accessibleName.equals(field.getAccessibleContext().getAccessibleName())) {
                return field;
            }
        }
        throw new AssertionError("Missing field: " + accessibleName);
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        for (T child : descendants(root, type)) {
            if (name.equals(child.getName())) {
                return child;
            }
        }
        throw new AssertionError("Missing component: " + name);
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        java.util.ArrayList<T> matches = new java.util.ArrayList<>();
        if (type.isInstance(root)) {
            matches.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                matches.addAll(descendants(child, type));
            }
        }
        return matches;
    }

    private static void layoutDeep(Component root) {
        if (root instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutDeep(child);
            }
        }
    }

    private record Fixture(CrmTab tab, JComponent root, TrackingHost host) { }

    private static final class TrackingHost implements TabHost {
        private String status = "";
        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { }
        @Override public void setStatus(String message) { status = message; }
        @Override public void runTrendAnalysis() { }
    }
}
