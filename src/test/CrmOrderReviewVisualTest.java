package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.BadLocationException;
import javax.swing.text.View;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Compact CRM order review rendered contracts")
class CrmOrderReviewVisualTest {
    private static final Path OUTPUT = Path.of("target", "ui-previews", "dialogs");
    private static final String CUSTOMER_NAME = "<html>Kitsap Blooms & Family</html> waterfront florist"
            + " collective with a reserved North Field wedding collection and an extended Saturday"
            + " market handoff for the Port Orchard community garden";
    private static final int WIDTH = 580;
    private static final int HEIGHT = 340;

    @ParameterizedTest(name = "{0} {1} review at 580 by 340")
    @CsvSource({
            "light, confirmation", "dark, confirmation",
            "light, completion", "dark, completion",
            "light, cancellation", "dark, cancellation",
            "light, deletion", "dark, deletion"
    })
    @DisplayName("long identities wrap literally and every reviewed consequence stays readable and copyable")
    void rendersCompleteOrderReviews(String theme, String kind) throws Exception {
        Files.createDirectories(OUTPUT);
        SwingUtilities.invokeAndWait(() -> {
            try {
                if ("dark".equals(theme)) {
                    FlatDarkLaf.setup();
                } else {
                    FlatLightLaf.setup();
                }
                FarmUi.installThemeDefaults();
                ReviewFixture fixture = review(kind);
                JComponent panel = fixture.panel();
                panel.setSize(WIDTH, HEIGHT);
                settleLayout(panel);

                JLabel heading = named(panel, fixture.prefix() + "Heading", JLabel.class);
                JTextArea details = named(panel, fixture.prefix() + "Details", JTextArea.class);
                JScrollPane scroll = named(panel, fixture.prefix() + "Scroll", JScrollPane.class);
                assertThat(heading).isNotNull();
                assertThat(details).isNotNull();
                assertThat(scroll).isNotNull();
                assertThat(heading.getText()).isEqualTo(fixture.heading()).contains(CUSTOMER_NAME);
                assertThat(heading.getAccessibleContext().getAccessibleName())
                        .contains(fixture.heading());
                assertThat(panel.getAccessibleContext().getAccessibleName()).contains(CUSTOMER_NAME);
                assertFullyVisible(heading, panel);
                int singleLineHeight = heading.getFontMetrics(heading.getFont()).getHeight()
                        + heading.getInsets().top + heading.getInsets().bottom;
                assertThat(heading.getHeight()).as("long customer identity wraps across lines")
                        .isGreaterThan(singleLineHeight);
                View renderedHeading = (View) heading.getClientProperty(BasicHTML.propertyKey);
                assertThat(renderedHeading).as("wrapped heading renderer").isNotNull();
                assertThat((int) Math.ceil(renderedHeading.getPreferredSpan(View.Y_AXIS)))
                        .as("every wrapped heading line fits without clipping")
                        .isLessThanOrEqualTo(heading.getHeight()
                                - heading.getInsets().top - heading.getInsets().bottom);
                try {
                    assertThat(renderedHeading.getDocument().getText(
                            0, renderedHeading.getDocument().getLength()))
                            .as("customer markup is displayed literally").contains(CUSTOMER_NAME);
                } catch (BadLocationException exception) {
                    throw new AssertionError("The complete heading must remain readable", exception);
                }

                assertFullyVisible(scroll, panel);
                assertThat(scroll.getViewport().getExtentSize().width).isGreaterThanOrEqualTo(500);
                assertThat(scroll.getViewport().getExtentSize().height).isGreaterThanOrEqualTo(180);
                assertThat(details.getText()).isEqualTo(fixture.details())
                        .contains("Dahlia Mix", "Rose Bunch", "Complimentary Cosmos")
                        .endsWith(fixture.finalConsequence());
                assertThat(details.getAccessibleContext().getAccessibleName()).isNotBlank();
                assertThat(details.getAccessibleContext().getAccessibleDescription())
                        .isEqualTo(fixture.details());
                assertThat(details.isEditable()).isFalse();
                assertThat(details.isEnabled()).isTrue();
                assertThat(details.isFocusable()).as("review text supports keyboard copy").isTrue();
                assertThat(details.getLineWrap()).isTrue();
                assertThat(details.getWrapStyleWord()).isTrue();
                assertThat(details.getCaretPosition()).isZero();
                assertThat(scroll.getHorizontalScrollBar().isVisible())
                        .as("details fit the compact width").isFalse();
                assertThat(scroll.getVerticalScrollBar().isVisible()).isTrue();
                String imageName = "crm-" + kind + "-" + theme + "-580x340";
                render(imageName, panel);

                details.selectAll();
                assertThat(details.getSelectedText()).as("copy retains the full review text")
                        .isEqualTo(fixture.details());
                details.setCaretPosition(details.getDocument().getLength());
                scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
                settleLayout(panel);
                assertThat(scroll.getViewport().getViewPosition().y).isPositive();
                try {
                    int finalStart = details.getText().lastIndexOf(fixture.finalConsequence());
                    Rectangle firstConsequenceLine = details.modelToView2D(finalStart).getBounds();
                    Rectangle lastConsequenceLine = details.modelToView2D(
                            details.getDocument().getLength()).getBounds();
                    assertThat(scroll.getViewport().getViewRect().contains(firstConsequenceLine))
                            .as("the final consequence begins inside the scrolled reader").isTrue();
                    assertThat(scroll.getViewport().getViewRect().contains(lastConsequenceLine))
                            .as("the review can be read through its final consequence").isTrue();
                } catch (BadLocationException exception) {
                    throw new AssertionError("The final consequence must be reachable", exception);
                }
                render(imageName + "-end", panel);
            } finally {
                FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
            }
        });
    }

    private static ReviewFixture review(String kind) {
        Customer customer = new Customer(7L, CUSTOMER_NAME, "Sam Rivera",
                "sam@kitsap.test", "360-555-0142", "FLORIST",
                "Use the side gate and keep the reserved wedding collection separate.");
        CustomerOrder order = new CustomerOrder(customer, LocalDate.now().plusDays(7),
                "confirmation".equals(kind) ? "DRAFT" : "CONFIRMED",
                "Keep upright; customer will inspect the complete load before approving the handoff.");
        order.setId(52L);
        order.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        order.addLine(new OrderLine("Rose Bunch", 1.6, "bunches", 18));
        order.addLine(new OrderLine("Complimentary Cosmos", 1, "stem", 0));
        CrmTab.OrderStockAssessment stock = new CrmTab.OrderStockAssessment(true, 3, List.of());
        return switch (kind) {
            case "confirmation" -> {
                CrmTab.OrderConfirmationReview review = CrmTab.orderConfirmationReview(order, stock);
                yield new ReviewFixture(CrmTab.orderConfirmationPanel(review), "crm.orderConfirmation",
                        review.heading(), review.details(),
                        "Inventory is not deducted until Complete order.");
            }
            case "completion" -> {
                CrmTab.OrderCompletionReview review = CrmTab.orderCompletionReview(order, stock);
                yield new ReviewFixture(CrmTab.orderCompletionPanel(review), "crm.orderCompletion",
                        review.heading(), review.details(),
                        "Cancel if any customer, date, product, quantity, unit, price, or instruction is not exact.");
            }
            case "cancellation", "deletion" -> {
                CrmTab.OrderDestructiveReview review = CrmTab.orderDestructiveReview(
                        order, "deletion".equals(kind));
                yield new ReviewFixture(CrmTab.orderDestructivePanel(review), "crm.orderDestructive",
                        review.heading(), review.details(),
                        "Continue only if every customer, date, instruction, and product above is exact.");
            }
            default -> throw new IllegalArgumentException("Unknown review kind: " + kind);
        };
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

    private static void render(String name, JComponent panel) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(UIManager.getColor("Panel.background"));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            panel.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write CRM order review visual evidence", exception);
        }
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        if (name.equals(root.getName()) && type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = named(child, name, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private record ReviewFixture(JComponent panel, String prefix, String heading,
                                 String details, String finalConsequence) { }
}
