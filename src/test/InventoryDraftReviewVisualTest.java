package com.flowerfarm.gui.view;

import com.flowerfarm.gui.FarmUi;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Retained inventory draft review rendered contracts")
class InventoryDraftReviewVisualTest {
    private static final Path OUTPUT = Path.of("target", "ui-previews", "inventory-drafts");
    private static final String PRODUCT_NAME = "Nootka Rose premium long-stem market bouquet selection"
            + " from the North Field reserved for Thursday florist collection at the waterfront";
    private static final String COUNT_DETAILS = "Stock count for product #11\nProduct: " + PRODUCT_NAME
            + "\nEntered physical count: 37\nOriginal unit: Per Stem\n\n"
            + "This product is outside the current inventory view. The count remains available for review.";
    private static final String EDIT_DETAILS = "Item edit for product #22\nProduct: " + PRODUCT_NAME
            + "\n\nThe original product is missing from inventory. These values have not been saved.\n"
            + "Name: <html>Keep this literal product name</html>\nCategory: Flowers/Plants\n"
            + "Rose variety: Nootka\nSelling price: not-a-price\nUnit: Per Bunch\nCost: -unfinished\n"
            + "Quantity: 2?\nNotes: "
            + "Keep this missing product draft available to copy. ".repeat(24)
            + "\nFinal retained value: cooler 2 · Thursday 06:30";

    @ParameterizedTest(name = "{0} theme at {1} by {2}")
    @CsvSource({"light, 580, 300", "dark, 580, 300", "light, 700, 380", "dark, 700, 380"})
    @DisplayName("draft choice and complete missing-product values remain reachable and copyable")
    void rendersReviewableDrafts(String theme, int width, int height) throws Exception {
        Files.createDirectories(OUTPUT);
        SwingUtilities.invokeAndWait(() -> {
            try {
                if ("dark".equals(theme)) {
                    FlatDarkLaf.setup();
                } else {
                    FlatLightLaf.setup();
                }
                FarmUi.installThemeDefaults();
                InventoryDraftReviewView view = new InventoryDraftReviewView(List.of(
                        new InventoryDraftReviewView.DraftDetails(
                                "Stock count · #11 · " + PRODUCT_NAME, COUNT_DETAILS),
                        new InventoryDraftReviewView.DraftDetails(
                                "Item edit · #22 · " + PRODUCT_NAME, EDIT_DETAILS)));
                view.setSize(width, height);
                settleLayout(view);
                String prefix = theme + "-" + width + "x" + height;
                render(prefix + "-count", view);

                JComboBox<?> choice = find(view, JComboBox.class).get(0);
                JTextArea details = find(view, JTextArea.class).get(0);
                JScrollPane scroll = find(view, JScrollPane.class).get(0);
                assertThat(choice.getAccessibleContext().getAccessibleName())
                        .isEqualTo("Retained inventory draft");
                assertThat(details.getAccessibleContext().getAccessibleName())
                        .isEqualTo("Retained inventory draft details");
                assertThat(view.selectedIndex()).isZero();
                assertThat(choice.isEnabled()).isTrue();
                assertThat(choice.getItemCount()).isEqualTo(2);
                assertThat(choice.getHeight()).as("comfortable draft selector").isGreaterThanOrEqualTo(32);
                assertFullyVisible(choice, view);
                assertFullyVisible(scroll, view);
                assertThat(details.getText()).isEqualTo(COUNT_DETAILS).contains(PRODUCT_NAME);
                assertThat(details.isEditable()).isFalse();
                assertThat(details.isEnabled()).isTrue();
                assertThat(details.isFocusable()).as("retained values support keyboard selection and copy").isTrue();
                assertThat(details.getLineWrap()).isTrue();
                assertThat(details.getWrapStyleWord()).isTrue();
                assertThat(scroll.getViewport().getExtentSize().width).isGreaterThanOrEqualTo(400);
                assertThat(scroll.getViewport().getExtentSize().height).isGreaterThanOrEqualTo(180);

                choice.setSelectedIndex(1);
                settleLayout(view);
                render(prefix + "-missing-edit", view);
                assertThat(view.selectedIndex()).isEqualTo(1);
                assertThat(details.getText()).isEqualTo(EDIT_DETAILS)
                        .contains(PRODUCT_NAME, "<html>Keep this literal product name</html>",
                                "not-a-price", "-unfinished", "Quantity: 2?");
                assertThat(details.getCaretPosition()).isZero();
                assertThat(scroll.getVerticalScrollBar().isVisible())
                        .as("long missing-product details have a usable scrollbar").isTrue();
                assertFullyVisible(scroll.getVerticalScrollBar(), view);
                assertThat(scroll.getHorizontalScrollBar().isVisible())
                        .as("wrapped details fit the review width").isFalse();
                details.selectAll();
                assertThat(details.getSelectedText()).as("all raw values can be copied without editing")
                        .isEqualTo(EDIT_DETAILS);
                details.setCaretPosition(details.getDocument().getLength());
                scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
                settleLayout(view);
                assertThat(scroll.getViewport().getViewPosition().y)
                        .as("the last retained values can be reached").isPositive();
                Rectangle lastLine;
                try {
                    lastLine = details.modelToView2D(details.getDocument().getLength()).getBounds();
                } catch (javax.swing.text.BadLocationException exception) {
                    throw new AssertionError("The complete retained draft must be reachable", exception);
                }
                assertThat(scroll.getViewport().getViewRect().contains(lastLine))
                        .as("the final retained line is visible after scrolling").isTrue();
                render(prefix + "-missing-edit-end", view);

                choice.setSelectedIndex(0);
                settleLayout(view);
                assertThat(details.getText()).isEqualTo(COUNT_DETAILS);
                assertThat(details.getCaretPosition()).isZero();
                assertThat(scroll.getViewport().getViewPosition().y).isZero();
            } finally {
                FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
            }
        });
    }

    private static void assertFullyVisible(JComponent component, JComponent root) {
        assertThat(component.isVisible()).isTrue();
        Rectangle bounds = SwingUtilities.convertRectangle(component,
                new Rectangle(0, 0, component.getWidth(), component.getHeight()), root);
        assertThat(new Rectangle(0, 0, root.getWidth(), root.getHeight()).contains(bounds))
                .as("%s fits the review panel", component.getName()).isTrue();
        assertThat(component.getWidth()).isPositive();
        assertThat(component.getHeight()).isPositive();
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
            graphics.setColor(UIManager.getColor("Panel.background"));
            graphics.fillRect(0, 0, root.getWidth(), root.getHeight());
            root.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write retained inventory draft visual evidence", exception);
        }
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
}
