package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.OrderService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.BadLocationException;
import javax.swing.text.View;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Dashboard daily sheet rendered contracts")
class DashboardDailySheetVisualTest {
    private static final Path OUTPUT = Path.of("target", "ui-previews", "daily-sheets");
    private static final String NAVIGATION = "Review <html>Nootka Rose & Dahlia</html> quantities"
            + " for the Port Orchard waterfront market and the reserved Saturday wedding collection"
            + " before recording the final count and completing the selected task.";
    private static final String FINAL_LINE = "End of daily report: keep the reserved collection together.";
    private static final String REPORT = "FLOWERS FOREVER — DAILY OPERATIONS\n\n"
            + "The report retains all original text for selection, copying, and PDF export.\n\n"
            + ("Nootka Rose & Dahlia | Market collection | Confirm the reserved quantities,"
            + " recorded harvest, packing notes, customer handoff, and next action.\n\n").repeat(22)
            + FINAL_LINE;

    @ParameterizedTest(name = "{0} {1} preview in a {2} by {3} owner")
    @CsvSource({
            "light, morning, 660, 500", "dark, morning, 660, 500",
            "light, closeout, 660, 500", "dark, closeout, 660, 500",
            "light, morning, 1050, 760", "dark, closeout, 1050, 760"
    })
    @DisplayName("daily reports fit compact owners and keep task details and the complete report reachable")
    void rendersDailySheets(String theme, String kind, int ownerWidth, int ownerHeight) throws Exception {
        Files.createDirectories(OUTPUT);
        SwingUtilities.invokeAndWait(() -> {
            try {
                installTheme(theme);
                JPanel owner = new JPanel();
                owner.setSize(ownerWidth, ownerHeight);
                ChooserFixture fixture = chooser(kind);
                String prefix = "dashboard." + kind;
                JScrollPane dialog = DashboardTab.buildDailySheetPreview(owner, prefix, REPORT,
                        FarmUi.statusBadge("2 follow-up areas", FarmUi.Tone.WARNING),
                        "morning".equals(kind)
                                ? "Choose today’s next job, then refresh after completing it."
                                : "Choose a follow-up, resolve it, then rebuild closeout.",
                        fixture.component());
                assertThat(dialog.getPreferredSize().width).isLessThanOrEqualTo(ownerWidth - 80);
                assertThat(dialog.getPreferredSize().height).isLessThanOrEqualTo(ownerHeight - 140);
                Object[] options = {"morning".equals(kind)
                        ? "Start selected task" : "Open selected follow-up", "Export PDF…", "Close"};
                JOptionPane optionPane = layoutDialog(owner, dialog, options);

                JTextArea report = named(dialog, prefix + ".report", JTextArea.class);
                JScrollPane reportScroll = named(dialog, prefix + ".reportScroll", JScrollPane.class);
                JLabel detail = named(dialog, fixture.detailName(), JLabel.class);
                JLabel guidance = named(dialog, prefix + ".guidance", JLabel.class);
                assertThat(report).isNotNull();
                assertThat(reportScroll).isNotNull();
                assertThat(detail).isNotNull();
                assertThat(guidance).isNotNull();
                assertInViewport(fixture.queue(), dialog);
                assertThat(fixture.queue().getHeight()).as("comfortable daily task target").isGreaterThanOrEqualTo(36);
                assertThat(fixture.queue().getAccessibleContext().getAccessibleName()).isNotBlank();
                assertThat(fixture.queue().getToolTipText()).isEqualTo(fixture.queue().getSelectedItem().toString());
                assertWrappedTextFits(guidance);
                assertWrappedTextFits(detail);
                assertThat(detail.getText()).contains(NAVIGATION);
                assertThat(detail.getHeight()).as("long task guidance wraps")
                        .isGreaterThan(detail.getFontMetrics(detail.getFont()).getHeight());
                assertThat(detail.getAccessibleContext().getAccessibleDescription()).contains(NAVIGATION);
                assertLiteralText(detail, NAVIGATION);
                assertThat(report.getText()).isEqualTo(REPORT);
                assertThat(report.isEditable()).isFalse();
                assertThat(report.isEnabled()).isTrue();
                assertThat(report.isFocusable()).as("report supports keyboard selection and copy").isTrue();
                assertThat(report.getFont().getSize()).isGreaterThanOrEqualTo(13);
                assertThat(report.getInsets().left).isGreaterThanOrEqualTo(12);
                assertThat(report.getInsets().top).isGreaterThanOrEqualTo(10);
                assertThat(report.getLineWrap()).isTrue();
                assertThat(report.getWrapStyleWord()).isTrue();
                assertThat(report.getCaretPosition()).isZero();
                assertThat(reportScroll.getViewport().getExtentSize().height).isGreaterThanOrEqualTo(260);
                assertThat(reportScroll.getHorizontalScrollBar().isVisible()).isFalse();
                assertThat(dialog.getHorizontalScrollBar().isVisible()).isFalse();
                String imageName = kind + "-" + theme + "-" + ownerWidth + "x" + ownerHeight;
                render(imageName, optionPane);

                fixture.queue().setSelectedIndex(1);
                settleLayout(optionPane);
                assertThat(fixture.selectedAction().get()).isSameAs(fixture.secondAction());
                assertThat(fixture.queue().getToolTipText()).isEqualTo(fixture.secondAction().toString());
                assertThat(detail.getAccessibleContext().getAccessibleDescription()).contains("Confirm the recorded harvest");
                assertWrappedTextFits(detail);

                report.selectAll();
                assertThat(report.getSelectedText()).as("copy preserves every report line").isEqualTo(REPORT);
                report.setCaretPosition(report.getDocument().getLength());
                reportScroll.getVerticalScrollBar().setValue(reportScroll.getVerticalScrollBar().getMaximum());
                settleLayout(optionPane);
                Point reportPosition = reportScroll.getViewport().getViewPosition();
                focus(report);
                settleLayout(optionPane);
                assertThat(reportScroll.getViewport().getViewPosition())
                        .as("revealing the nested report preserves the reader position").isEqualTo(reportPosition);
                assertInViewport(reportScroll, dialog);
                try {
                    Rectangle end = report.modelToView2D(report.getDocument().getLength()).getBounds();
                    assertThat(reportScroll.getViewport().getViewRect().contains(end))
                            .as("the final daily report line is readable").isTrue();
                } catch (BadLocationException exception) {
                    throw new AssertionError("The final report line must be reachable", exception);
                }
                render(imageName + "-end", optionPane);
                focus(fixture.queue());
                settleLayout(optionPane);
                assertInViewport(fixture.queue(), dialog);
            } finally {
                installTheme("light");
            }
        });
    }

    @ParameterizedTest(name = "{0} ready report without a task chooser")
    @CsvSource({"light", "dark"})
    @DisplayName("ready reports remain readable without an action queue")
    void rendersReadySheet(String theme) throws Exception {
        Files.createDirectories(OUTPUT);
        SwingUtilities.invokeAndWait(() -> {
            try {
                installTheme(theme);
                JPanel owner = new JPanel();
                owner.setSize(660, 500);
                JScrollPane dialog = DashboardTab.buildDailySheetPreview(owner, "dashboard.closeout", REPORT,
                        FarmUi.statusBadge("Ready to close", FarmUi.Tone.SUCCESS),
                        "No operational blockers remain for today.", null);
                JOptionPane optionPane = layoutDialog(owner, dialog, new Object[]{"Export PDF…", "Close"});
                JTextArea report = named(dialog, "dashboard.closeout.report", JTextArea.class);
                assertThat(report.getText()).isEqualTo(REPORT);
                assertThat(named(dialog, "dashboard.closeoutFollowUps", JComboBox.class)).isNull();
                focus(report);
                settleLayout(optionPane);
                assertInViewport(named(dialog, "dashboard.closeout.reportScroll", JScrollPane.class), dialog);
                render("ready-" + theme + "-660x500", optionPane);
            } finally {
                installTheme("light");
            }
        });
    }

    @Test
    @DisplayName("sample farm closeout gallery uses the generated report and visible follow-up actions")
    void rendersServiceGeneratedCloseoutGallery() throws Exception {
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        LocalDate today = LocalDate.now(zone);
        FarmProfile profile = new FarmProfile();
        profile.setName("Kitsap Flower Farm");
        profile.setLocation("Port Orchard, WA");
        profile.setTimeZone(zone.getId());

        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        OrderService orders = mock(OrderService.class);
        CustomerOrder fulfilled = sampleOrder(9L, "Harbor Floral", "FLORIST", today, "FULFILLED");
        fulfilled.addLine(new OrderLine("Nootka Rose", 20, "stems", 4.50));
        fulfilled.addLine(new OrderLine("Dahlia Mix", 6, "bunches", 18.00));
        CustomerOrder kitsap = sampleOrder(10L, "Kitsap Blooms", "FLORIST", today, "CONFIRMED");
        CustomerOrder stall = sampleOrder(11L, "Saturday Stall", "MARKET", today, "CONFIRMED");
        for (CustomerOrder order : List.of(kitsap, stall)) {
            order.addLine(new OrderLine("Nootka Rose", 40, "stems", 4.50));
            order.addLine(new OrderLine("Dahlia Mix", 6, "bunches", 18.00));
        }
        when(orders.findBetween(eq(today), eq(today)))
                .thenReturn(List.of(fulfilled, kitsap, stall));
        when(orders.weekRevenueSummary()).thenReturn(new OrderService.WeekRevenueSummary(
                today.minusDays(6), today, 1268.00, 576.00, 225.00, 9, 2, 2));
        when(harvest.findBetween(eq(today), eq(today))).thenReturn(List.of(
                new HarvestEntry(today, "Nootka Rose", 72, "stems", "Field A", "Cut before 8 AM")));
        when(harvest.totalQuantityLast7Days()).thenReturn(386.0);
        when(inventory.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(5, 1290.00, 395.60, 2, 143, 10));
        when(inventory.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 4.50, "Per Stem", 1.10, 96, "Field A"),
                new Item("Dahlia Mix", "Flowers/Plants", 18.00, "Per Bunch", 6.00, 20, "Field B"),
                new Item("Market Bouquet", "Flowers/Plants", 28.00, "Per Unit", 9.00, 14,
                        "Saturday market"),
                new Item("Floral Snips", "Tools/Equipment", 18.00, "Per Unit", 8.00, 5,
                        "Packing shed"),
                new Item("Market Tags", "Tools/Equipment", 2.00, "Per Unit", 0.50, 8,
                        "Packing shed")));

        DayCloseoutService.DayCloseout closeout =
                new DayCloseoutService(inventory, harvest, orders, profile).build();
        assertThat(closeout.fulfilledRevenue()).isEqualTo(198.00);
        assertThat(closeout.remainingPipelineRevenue()).isEqualTo(576.00);
        assertThat(closeout.todayHarvestQty()).isEqualTo(72.0);
        assertThat(closeout.lowStock()).hasSize(2);
        List<DashboardTab.CloseoutAction> followUps = DashboardTab.closeoutActions(closeout);
        assertThat(followUps).extracting(DashboardTab.CloseoutAction::targetTab)
                .containsExactly("Market Day", "Inventory");

        Files.createDirectories(OUTPUT);
        SwingUtilities.invokeAndWait(() -> {
            try {
                installTheme("light");
                JPanel owner = new JPanel();
                owner.setSize(1050, 760);
                DashboardTab.CloseoutActionChooser chooser =
                        DashboardTab.buildCloseoutActionChooser(followUps);
                JScrollPane preview = DashboardTab.buildDailySheetPreview(owner,
                        "dashboard.closeout", closeout.plainText(),
                        FarmUi.statusBadge(followUps.size() + " follow-up areas", FarmUi.Tone.WARNING),
                        "Choose a follow-up, resolve it, then rebuild closeout.", chooser.component());
                JOptionPane dialog = layoutDialog(owner, preview,
                        new Object[]{"Open selected follow-up", "Export PDF…", "Close"});
                JTextArea report = named(preview, "dashboard.closeout.report", JTextArea.class);
                JScrollPane reportScroll = named(preview, "dashboard.closeout.reportScroll",
                        JScrollPane.class);
                assertThat(report.getText()).isEqualTo(closeout.plainText())
                        .contains("Today: fulfilled 1 ($198.00)", "harvest 72", "open CONFIRMED 2",
                                "Finish 2 open CONFIRMED order(s) ($576.00)");
                assertReportPhraseVisible(report, reportScroll, "Today: fulfilled 1 ($198.00)");
                assertReportPhraseVisible(report, reportScroll, "open CONFIRMED 2");
                Rectangle reportBounds = SwingUtilities.convertRectangle(reportScroll.getParent(),
                        reportScroll.getBounds(), preview.getViewport().getView());
                Rectangle visiblePreview = preview.getViewport().getViewRect();
                assertThat(visiblePreview.y + visiblePreview.height
                        - reportBounds.y - reportBounds.height)
                        .as("the closeout report ends near the visible dialog actions")
                        .isBetween(0, 32);
                assertThat(chooser.queue().getSelectedItem().toString())
                        .contains("Finish 2 confirmed order(s)", "Market Day");
                JLabel followUpDetail = named(preview, "dashboard.closeoutFollowUpDetail", JLabel.class);
                assertThat(followUpDetail.getText())
                        .contains("Opens Market Day to finish")
                        .doesNotContain("Opened Market Day");
                DashboardTab.MorningActionChooser morningChooser =
                        DashboardTab.buildMorningActionChooser(List.of(new DashboardTab.MorningAction(
                                15, "Market Day", "Review confirmed orders",
                                "Opened Market Day to review the customer load-out.", List.of())));
                JLabel morningDetail = named(morningChooser.component(),
                        "dashboard.morningTaskDetail", JLabel.class);
                assertThat(morningDetail.getText())
                        .contains("Opens Market Day to review")
                        .doesNotContain("Opened Market Day");
                render("closeout-gallery", dialog);
            } finally {
                installTheme("light");
            }
        });
    }

    private static CustomerOrder sampleOrder(long id, String customerName, String customerType,
                                             LocalDate day, String status) {
        Customer customer = new Customer(customerName, "", "", "", customerType, "");
        CustomerOrder order = new CustomerOrder(customer, day, status, "Sample farm order");
        order.setId(id);
        return order;
    }

    private static void assertReportPhraseVisible(JTextArea report, JScrollPane scroll, String phrase) {
        int start = report.getText().indexOf(phrase);
        assertThat(start).as("generated closeout contains %s", phrase).isGreaterThanOrEqualTo(0);
        try {
            Rectangle viewport = scroll.getViewport().getViewRect();
            assertThat(viewport.contains(report.modelToView2D(start).getBounds()))
                    .as("start of %s is visible in the gallery", phrase).isTrue();
            assertThat(viewport.contains(report.modelToView2D(start + phrase.length() - 1).getBounds()))
                    .as("end of %s is visible in the gallery", phrase).isTrue();
        } catch (BadLocationException exception) {
            throw new AssertionError("The closeout summary must be visible in the gallery", exception);
        }
    }

    private static ChooserFixture chooser(String kind) {
        if ("morning".equals(kind)) {
            DashboardTab.MorningAction first = new DashboardTab.MorningAction(10, "Inventory",
                    "Review <html>Nootka Rose & Dahlia</html> reserved market collection quantities",
                    NAVIGATION, List.of("Nootka Rose", "Dahlia"));
            DashboardTab.MorningAction second = new DashboardTab.MorningAction(30, "Harvest Log",
                    "Review the final harvest entry", "Confirm the recorded harvest before opening the market.", List.of());
            DashboardTab.MorningActionChooser chooser = DashboardTab.buildMorningActionChooser(List.of(first, second));
            return new ChooserFixture(chooser.component(), chooser.queue(), "dashboard.morningTaskDetail",
                    chooser::selectedAction, second);
        }
        DashboardTab.CloseoutAction first = new DashboardTab.CloseoutAction("Inventory",
                "Review <html>Nootka Rose & Dahlia</html> reserved market collection quantities",
                NAVIGATION, List.of("Nootka Rose", "Dahlia"));
        DashboardTab.CloseoutAction second = new DashboardTab.CloseoutAction("Harvest Log",
                "Review the final harvest entry", "Confirm the recorded harvest before closing the day.", List.of());
        DashboardTab.CloseoutActionChooser chooser = DashboardTab.buildCloseoutActionChooser(List.of(first, second));
        return new ChooserFixture(chooser.component(), chooser.queue(), "dashboard.closeoutFollowUpDetail",
                chooser::selectedAction, second);
    }

    private static void installTheme(String theme) {
        if ("dark".equals(theme)) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        FarmUi.installThemeDefaults();
    }

    private static JOptionPane layoutDialog(JComponent owner, JScrollPane scroll, Object[] options) {
        JOptionPane optionPane = new JOptionPane(scroll, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, options, options[options.length - 1]);
        optionPane.setSize(optionPane.getPreferredSize());
        settleLayout(optionPane);
        assertThat(optionPane.getWidth()).as("complete daily dialog fits its owner").isLessThan(owner.getWidth());
        assertThat(optionPane.getHeight()).as("complete daily dialog leaves room for its title bar")
                .isLessThanOrEqualTo(owner.getHeight() - 30);
        Rectangle dialogBounds = new Rectangle(0, 0, optionPane.getWidth(), optionPane.getHeight());
        Rectangle previewBounds = SwingUtilities.convertRectangle(scroll.getParent(),
                scroll.getBounds(), optionPane);
        assertThat(dialogBounds.contains(previewBounds)).as("daily preview fits in the dialog").isTrue();
        List<AbstractButton> actions = descendants(optionPane, AbstractButton.class).stream()
                .filter(button -> List.of(options).contains(button.getText())).toList();
        assertThat(actions).extracting(AbstractButton::getText).containsExactlyElementsOf(
                List.of(options).stream().map(Object::toString).toList());
        for (AbstractButton action : actions) {
            Rectangle bounds = SwingUtilities.convertRectangle(action.getParent(), action.getBounds(), optionPane);
            assertThat(dialogBounds.contains(bounds)).as("daily action stays visible: %s", action.getText()).isTrue();
            assertThat(bounds.y).as("daily action stays below the report: %s", action.getText())
                    .isGreaterThanOrEqualTo(previewBounds.y + previewBounds.height);
            assertThat(action.getHeight()).isGreaterThan(20);
            assertThat(action.getWidth()).isPositive();
        }
        return optionPane;
    }

    private static void assertWrappedTextFits(JLabel label) {
        View view = (View) label.getClientProperty(BasicHTML.propertyKey);
        assertThat(view).as("wrapped guidance renderer").isNotNull();
        assertThat((int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)))
                .as("every wrapped line fits %s", label.getName())
                .isLessThanOrEqualTo(label.getHeight() - label.getInsets().top - label.getInsets().bottom);
    }

    private static void assertLiteralText(JLabel label, String expected) {
        View view = (View) label.getClientProperty(BasicHTML.propertyKey);
        try {
            assertThat(view.getDocument().getText(0, view.getDocument().getLength()))
                    .as("task names containing markup remain literal").contains(expected);
        } catch (BadLocationException exception) {
            throw new AssertionError("The complete selected task detail must remain readable", exception);
        }
    }

    private static void focus(JComponent component) {
        FocusEvent event = new FocusEvent(component, FocusEvent.FOCUS_GAINED);
        for (FocusListener listener : component.getFocusListeners()) {
            listener.focusGained(event);
        }
        FocusEvent lost = new FocusEvent(component, FocusEvent.FOCUS_LOST);
        for (FocusListener listener : component.getFocusListeners()) {
            listener.focusLost(lost);
        }
    }

    private static void assertInViewport(JComponent component, JScrollPane scroll) {
        Rectangle bounds = SwingUtilities.convertRectangle(component,
                new Rectangle(0, 0, component.getWidth(), component.getHeight()), scroll.getViewport().getView());
        assertThat(scroll.getViewport().getViewRect().contains(bounds))
                .as("%s is fully visible inside the owner-bounded preview", component.getName()).isTrue();
        assertThat(component.getWidth()).isPositive();
        assertThat(component.getHeight()).isPositive();
    }

    private static void settleLayout(Component root) {
        for (int pass = 0; pass < 6; pass++) {
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

    private static void render(String name, JComponent component) {
        Dimension size = component.getSize();
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(UIManager.getColor("Panel.background"));
            graphics.fillRect(0, 0, size.width, size.height);
            component.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write daily report visual evidence", exception);
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

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(root)) matches.add(type.cast(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) matches.addAll(descendants(child, type));
        }
        return matches;
    }

    private record ChooserFixture(JComponent component, JComboBox<?> queue, String detailName,
                                  Supplier<Object> selectedAction, Object secondAction) { }
}
