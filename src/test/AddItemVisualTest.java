package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.view.InventoryItemFormView;
import com.flowerfarm.service.InventoryService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.BadLocationException;
import javax.swing.text.View;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Responsive add-inventory rendered contracts")
class AddItemVisualTest {
    private static final Path OUTPUT = Path.of("target", "ui-previews", "add-item");
    private static final String PRODUCT = "<html>North Field & Community Garden</html>"
            + " reserved mixed dahlia stems for the Port Orchard waterfront wedding collection"
            + " and Saturday market handoff, including the late-season Cafe au Lait succession"
            + " from the sheltered west bed and the family florist cooperative's extended"
            + " pickup allocation for the final September celebration";

    @ParameterizedTest(name = "{0} at {1} by {2}")
    @CsvSource({"light, 850, 500", "dark, 850, 500", "light, 1180, 760", "dark, 1180, 760"})
    @DisplayName("fields, economics, every save action, and long routed status remain reachable")
    void rendersReachableItemWorkspace(String theme, int width, int height) throws Exception {
        Files.createDirectories(OUTPUT);
        InventoryService inventory = mock(InventoryService.class);
        SwingUtilities.invokeAndWait(() -> {
            try {
                if ("dark".equals(theme)) FlatDarkLaf.setup();
                else FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
                AddItemTab tab = new AddItemTab(inventory, null);
                JComponent root = tab.getUIComponent();
                root.setSize(width, height);
                settle(root);
                JScrollPane scroll = named(root, "inventory.itemScroll", JScrollPane.class);
                InventoryItemFormView form = named(root, "inventory.itemForm", InventoryItemFormView.class);
                JComponent economics = named(root, "inventory.economicsPreview", JComponent.class);
                assertThat(scroll.getHorizontalScrollBar().isVisible()).isFalse();
                assertThat(scroll.getViewport().getView().getWidth())
                        .isEqualTo(scroll.getViewport().getWidth());
                assertResponsivePlacement(form, economics, width);
                JComponent itemName = accessible(form, "Item name");
                assertThat(scroll.getViewport().getViewRect().contains(boundsInView(itemName, scroll)))
                        .as("the initial product name is visible").isTrue();
                render(theme + "-" + width + "x" + height + "-entry", root);

                tab.prepareMissingItem(PRODUCT);
                settle(root);
                assertThat(form.draft().name()).isEqualTo(PRODUCT);
                assertThat(form.draft().price()).isEqualTo("0.00");
                assertThat(form.draft().quantity()).isEqualTo("0");
                for (String name : List.of("Item name", "Item category", "Inventory quantity",
                        "Selling unit", "Selling price", "Cost per unit", "Item notes", "Rose variety")) {
                    JComponent field = accessible(form, name);
                    assertThat(field.getWidth()).as("usable width for %s", name).isGreaterThanOrEqualTo(160);
                    assertThat(field.getHeight()).as("comfortable height for %s", name)
                            .isGreaterThanOrEqualTo(36);
                    revealByFocus(field, scroll);
                }
                for (String text : List.of("Use starter", "Keep setup for next item",
                        "Add item to inventory", "Reset form", "Return to stock queue")) {
                    AbstractButton action = button(root, text);
                    assertThat(action.isEnabled()).as("available action %s", text).isTrue();
                    assertThat(action.isVisible()).isTrue();
                    revealByFocus(action, scroll);
                    assertThat(action.getWidth()).isGreaterThanOrEqualTo(action.getPreferredSize().width);
                    assertThat(action.getHeight()).isGreaterThanOrEqualTo(action.getPreferredSize().height);
                }
                AbstractButton existing = button(root, "Open existing item");
                assertThat(existing.isEnabled()).as("no duplicate has been submitted").isFalse();
                assertThat(scroll.getViewport().getViewRect().contains(boundsInView(existing, scroll)))
                        .as("the fourth action fits beside Return to stock queue").isTrue();
                if (height == 500) {
                    assertThat(scroll.getViewport().getViewPosition().y)
                            .as("keyboard navigation scrolls the compact workspace").isPositive();
                }
                render(theme + "-" + width + "x" + height + "-actions", root);

                JLabel feedback = descendants(named(root, "inventory.itemFeedback", JComponent.class),
                        JLabel.class).get(0);
                assertThat(feedback.getText()).isEqualTo("Missing product prepared · “" + PRODUCT + "”");
                assertThat(feedback.getAccessibleContext().getAccessibleName()).contains(PRODUCT);
                View rendered = (View) feedback.getClientProperty(BasicHTML.propertyKey);
                assertThat(rendered).as("feedback wraps within its dedicated row").isNotNull();
                try {
                    assertThat(rendered.getDocument().getText(0, rendered.getDocument().getLength()))
                            .as("product markup stays literal").contains(PRODUCT);
                } catch (BadLocationException exception) {
                    throw new AssertionError(exception);
                }
                assertThat(feedback.getHeight()).isGreaterThan(feedback.getFontMetrics(feedback.getFont()).getHeight()
                        + feedback.getInsets().top + feedback.getInsets().bottom);
                assertThat((int) Math.ceil(rendered.getPreferredSpan(View.Y_AXIS)))
                        .isLessThanOrEqualTo(feedback.getHeight()
                                - feedback.getInsets().top - feedback.getInsets().bottom);
                ((JComponent) scroll.getViewport().getView()).scrollRectToVisible(boundsInView(feedback, scroll));
                assertThat(scroll.getViewport().getViewRect().contains(boundsInView(feedback, scroll))).isTrue();
                render(theme + "-" + width + "x" + height + "-feedback", root);

                root.setSize(width == 850 ? 1180 : 850, height);
                settle(root);
                assertResponsivePlacement(form, economics, root.getWidth());
                root.setSize(width, height);
                settle(root);
                assertResponsivePlacement(form, economics, width);
                assertThat(form.draft().name()).as("resizing preserves the routed draft").isEqualTo(PRODUCT);
                scroll.getViewport().setViewPosition(new Point());
            } finally {
                FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
            }
        });
        verifyNoInteractions(inventory);
    }

    private static void assertResponsivePlacement(JComponent form, JComponent economics, int width) {
        if (width == 850) {
            assertThat(economics.getY()).as("compact economics sit below the full-width form")
                    .isGreaterThanOrEqualTo(form.getY() + form.getHeight());
            assertThat(economics.getWidth()).isEqualTo(form.getWidth());
        } else {
            assertThat(economics.getX()).as("desktop economics sit beside the form")
                    .isGreaterThanOrEqualTo(form.getX() + form.getWidth());
        }
        for (Component card : economics.getComponents()) {
            assertThat(card.getHeight()).isGreaterThanOrEqualTo(card.getPreferredSize().height);
            assertThat(card.getWidth()).isGreaterThanOrEqualTo(200);
        }
    }

    private static void revealByFocus(JComponent control, JScrollPane scroll) {
        FocusEvent gained = new FocusEvent(control, FocusEvent.FOCUS_GAINED);
        for (var listener : control.getFocusListeners()) listener.focusGained(gained);
        assertThat(scroll.getViewport().getViewRect().contains(boundsInView(control, scroll)))
                .as("keyboard reveals %s", control.getAccessibleContext().getAccessibleName()).isTrue();
        FocusEvent lost = new FocusEvent(control, FocusEvent.FOCUS_LOST);
        for (var listener : control.getFocusListeners()) listener.focusLost(lost);
    }

    private static Rectangle boundsInView(JComponent component, JScrollPane scroll) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(),
                scroll.getViewport().getView());
    }

    private static AbstractButton button(Component root, String text) {
        return descendants(root, AbstractButton.class).stream()
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static JComponent accessible(Component root, String name) {
        return descendants(root, JComponent.class).stream()
                .filter(component -> component.getAccessibleContext() != null
                        && name.equals(component.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        return descendants(root, type).stream()
                .filter(component -> name.equals(component.getName())).findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(root)) result.add(type.cast(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) result.addAll(descendants(child, type));
        }
        return result;
    }

    private static void settle(Component root) {
        for (int pass = 0; pass < 8; pass++) layout(root);
    }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) layout(child);
        }
    }

    private static void render(String name, JComponent root) {
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        root.printAll(graphics);
        graphics.dispose();
        try {
            ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
        } catch (IOException exception) {
            throw new AssertionError("Could not save add-item preview", exception);
        }
    }
}
