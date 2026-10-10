package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Inventory saved-action recovery rendered contracts")
class InventoryRecoveryVisualTest {
    private static final Path OUTPUT = Path.of("target", "ui-previews", "inventory-recovery");
    private static final String PRODUCT_NAME = "Nootka Rose premium long-stem market bouquet selection"
            + " from the North Field reserved for Thursday florist collection at the waterfront";

    @ParameterizedTest(name = "{0} theme at {1} by {2}")
    @CsvSource({"light, 850, 500", "dark, 850, 500", "light, 1180, 760", "dark, 1180, 760"})
    @DisplayName("saved count and refresh recovery stay readable beside usable inventory controls")
    void rendersSavedCountRecovery(String theme, int width, int height) throws Exception {
        Files.createDirectories(OUTPUT);
        InventoryService service = savedCountWithFailedReadback();
        InventoryTab[] tab = new InventoryTab[1];
        JComponent[] root = new JComponent[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                if ("dark".equals(theme)) {
                    FlatDarkLaf.setup();
                } else {
                    FlatLightLaf.setup();
                }
                FarmUi.installThemeDefaults();
                tab[0] = new InventoryTab(service, new Host());
                root[0] = tab[0].getUIComponent();
                root[0].setSize(width, height);
                tab[0].initialize();
            });
            SwingUserHarness user = SwingUserHarness.on(root[0]);
            user.awaitButtonEnabled("Refresh", true).awaitTableRows("inventory.table", 2);
            SwingUtilities.invokeAndWait(() -> {
                JTable table = named(root[0], "inventory.table", JTable.class);
                for (int row = 0; row < table.getRowCount(); row++) {
                    if (PRODUCT_NAME.equals(table.getValueAt(row, 0))) {
                        table.setRowSelectionInterval(row, row);
                        tab[0].startSelectedStockCountSave(37, 13, false);
                        return;
                    }
                }
                throw new AssertionError("The long-name inventory product was not loaded");
            });
            user.awaitButtonEnabled("Refresh", true).awaitText("was saved");

            SwingUtilities.invokeAndWait(() -> {
                String view = theme + "-" + width + "x" + height + "-saved-refresh-required";
                settleLayout(root[0]);
                render(view, root[0]);
                JLabel status = find(root[0], JLabel.class).stream().filter(label ->
                        "Inventory results and selected product".equals(
                                label.getAccessibleContext().getAccessibleName()))
                        .findFirst().orElseThrow();
                assertThat(status.getText()).contains(PRODUCT_NAME);
                assertThat(status.getText().toLowerCase(Locale.ROOT))
                        .contains("saved", "refresh").doesNotContain("not saved");
                assertThat(status.getAccessibleContext().getAccessibleDescription())
                        .contains("inventory readback unavailable", "Do not repeat the saved action");
                assertThat(status.getClientProperty(BasicHTML.propertyKey))
                        .as("recovery status supports full-width wrapping in %s", view)
                        .isInstanceOf(View.class);
                assertLabelFits(status, root[0], view);
                if (width == 850) {
                    View renderer = (View) status.getClientProperty(BasicHTML.propertyKey);
                    assertThat(renderer.getPreferredSpan(View.Y_AXIS))
                            .as("long saved-product identity wraps at the compact size")
                            .isGreaterThan(status.getFontMetrics(status.getFont()).getHeight());
                }
                assertLabelFits(named(root[0], "inventory.documentStatus", JLabel.class), root[0], view);
                assertLabelFits(named(root[0], "farm.pageTitle", JLabel.class), root[0], view);

                JButton refresh = button(root[0], "Refresh");
                assertThat(refresh.isEnabled()).as("refresh remains the available recovery action").isTrue();
                assertButtonFits(refresh, view);
                assertFullyVisible(refresh, root[0], view);
                for (String text : List.of("Record stock count…", "Edit selected", "Delete selected")) {
                    JButton mutation = button(root[0], text);
                    assertThat(mutation.isEnabled()).as("stale inventory blocks %s", text).isFalse();
                    assertButtonReachable(mutation, root[0], view);
                }
                render(view + "-stock-actions", root[0]);
                for (String text : List.of("Low-stock reorder…", "Price list…", "Export visible CSV…")) {
                    assertButtonReachable(button(root[0], text), root[0], view);
                }
                render(view + "-report-actions", root[0]);

                JTable table = named(root[0], "inventory.table", JTable.class);
                JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, table);
                assertThat(viewport).as("inventory remains in a usable scroll viewport").isNotNull();
                JScrollPane tableScroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
                JScrollPane contentScroll = (JScrollPane) SwingUtilities.getAncestorOfClass(
                        JScrollPane.class, tableScroll);
                assertThat(contentScroll).as("inventory workspace can scroll vertically").isNotNull();
                assertThat(contentScroll.getViewport().getViewSize().width)
                        .as("workspace fits its available width in %s", view)
                        .isLessThanOrEqualTo(contentScroll.getViewport().getExtentSize().width);
                if (height == 500) {
                    assertThat(contentScroll.getViewport().getViewPosition().y)
                            .as("compact workspace actually scrolls to reach its actions").isPositive();
                }
                assertThat(viewport.getExtentSize().width).as("readable inventory width in %s", view)
                        .isGreaterThanOrEqualTo(300);
                assertThat(viewport.getExtentSize().height).as("at least one complete stock row in %s", view)
                        .isGreaterThanOrEqualTo(table.getRowHeight());
                revealThroughScrollPanes(viewport,
                        new Rectangle(0, 0, viewport.getWidth(), viewport.getHeight()));
                assertFullyVisible(viewport, root[0], view);
                assertThat(table.getRowCount()).as("saved snapshot stays available during recovery").isEqualTo(2);
                render(view + "-stock-table", root[0]);
                assertLabelFits(status, root[0], view);
                assertLabelFits(named(root[0], "inventory.documentStatus", JLabel.class), root[0], view);
                assertLabelFits(named(root[0], "farm.pageTitle", JLabel.class), root[0], view);
                assertFullyVisible(refresh, root[0], view);
            });
            verify(service, times(1)).updateById(eq(11L), any(Item.class));
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
            });
        }
    }

    private static InventoryService savedCountWithFailedReadback() {
        InventoryService service = mock(InventoryService.class);
        Item current = new Item(11L, PRODUCT_NAME, "Flowers/Plants", 4.5,
                "Per Stem", 1.2, 24, "North field · Cooler 2");
        Item next = new Item(22L, "Dahlia Mix", "Flowers/Plants", 3.5,
                "Per Stem", 1.0, 10, "Tunnel 2");
        Item saved = new Item(11L, PRODUCT_NAME, "Flowers/Plants", 4.5,
                "Per Stem", 1.2, 37, "North field · Cooler 2");
        AtomicBoolean committed = new AtomicBoolean();
        when(service.getAllItems()).thenAnswer(invocation -> {
            if (committed.get()) {
                throw new IllegalStateException("inventory readback unavailable");
            }
            return List.of(current, next);
        });
        when(service.updateById(eq(11L), any(Item.class))).thenAnswer(invocation -> {
            committed.set(true);
            return saved;
        });
        return service;
    }

    private static void assertLabelFits(JLabel label, JComponent root, String view) {
        Object html = label.getClientProperty(BasicHTML.propertyKey);
        int textWidth;
        int textHeight;
        if (html instanceof View renderer) {
            textWidth = (int) Math.ceil(renderer.getPreferredSpan(View.X_AXIS));
            textHeight = (int) Math.ceil(renderer.getPreferredSpan(View.Y_AXIS));
        } else {
            textWidth = label.getFontMetrics(label.getFont()).stringWidth(label.getText());
            textHeight = label.getFontMetrics(label.getFont()).getHeight();
        }
        assertThat(label.getWidth() - label.getInsets().left - label.getInsets().right)
                .as("complete label width for %s in %s", label.getName(), view)
                .isGreaterThanOrEqualTo(textWidth);
        assertThat(label.getHeight() - label.getInsets().top - label.getInsets().bottom)
                .as("complete label height for %s in %s", label.getName(), view)
                .isGreaterThanOrEqualTo(textHeight);
        assertFullyVisible(label, root, view);
    }

    private static void assertButtonReachable(JButton button, JComponent root, String view) {
        assertButtonFits(button, view);
        revealThroughScrollPanes(button,
                new Rectangle(0, 0, button.getWidth(), button.getHeight()));
        assertFullyVisible(button, root, view);
    }

    private static void assertButtonFits(JButton button, String view) {
        assertThat(button.getWidth()).as("complete %s button in %s", button.getText(), view)
                .isGreaterThanOrEqualTo(button.getPreferredSize().width);
        assertThat(button.getHeight()).as("comfortable %s button in %s", button.getText(), view)
                .isGreaterThanOrEqualTo(32);
    }

    private static void revealThroughScrollPanes(JComponent control, Rectangle requested) {
        for (Container parent = control.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof JViewport viewport) {
                Component view = viewport.getView();
                Rectangle target = SwingUtilities.convertRectangle(control, requested, view);
                Point position = viewport.getViewPosition();
                int width = viewport.getExtentSize().width;
                int height = viewport.getExtentSize().height;
                int x = Math.min(position.x, target.x);
                int y = Math.min(position.y, target.y);
                if (target.x + target.width > x + width) {
                    x = target.x + target.width - width;
                }
                if (target.y + target.height > y + height) {
                    y = target.y + target.height - height;
                }
                x = Math.max(0, Math.min(x, Math.max(0, view.getWidth() - width)));
                y = Math.max(0, Math.min(y, Math.max(0, view.getHeight() - height)));
                viewport.setViewPosition(new Point(x, y));
            }
        }
    }

    private static void assertFullyVisible(JComponent component, JComponent root, String view) {
        assertThat(component.isVisible()).as("visible %s in %s", component.getName(), view).isTrue();
        Rectangle bounds = SwingUtilities.convertRectangle(component,
                new Rectangle(0, 0, component.getWidth(), component.getHeight()), root);
        Rectangle visible = new Rectangle(bounds);
        for (Container ancestor = component.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            visible = visible.intersection(SwingUtilities.convertRectangle(ancestor,
                    new Rectangle(0, 0, ancestor.getWidth(), ancestor.getHeight()), root));
            if (ancestor == root) {
                break;
            }
        }
        if (!visible.equals(bounds)) {
            render(view + "-unreachable", root);
        }
        assertThat(visible).as("unclipped %s in %s", component.getName(), view).isEqualTo(bounds);
    }

    private static void settleLayout(Component root) {
        for (int pass = 0; pass < 4; pass++) {
            layoutRecursively(root);
        }
    }

    private static void layoutRecursively(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }

    private static void render(String name, JComponent root) {
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            root.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write inventory recovery visual evidence", exception);
        }
    }

    private static JButton button(Component root, String text) {
        return find(root, JButton.class).stream().filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        return find(root, type).stream().filter(component -> name.equals(component.getName()))
                .findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> find(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(root)) {
            matches.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                matches.addAll(find(child, type));
            }
        }
        return matches;
    }

    private static final class Host implements TabHost {
        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
    }
}
