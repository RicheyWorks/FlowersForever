package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Wedding and memorial event services desktop")
class EventServicesTabTest {

    @Test
    @DisplayName("keeps filters, catalog, details, and brief actions usable at compact size")
    void keepsCompactWorkspaceVisible() throws Exception {
        JComponent root = new EventServicesTab(new Host()).getUIComponent();
        SwingUserHarness user = SwingUserHarness.on(root).layoutAt(1050, 700);
        JScrollPane scroll = (JScrollPane) named(root, "eventServices.scroll");
        assertThat(scroll).isNotNull();

        assertThat(user.visibleExtent("eventServices.search").width()).isGreaterThan(100);
        assertThat(user.visibleExtent("eventServices.occasionFilter").width())
                .isGreaterThan(100);
        assertThat(user.visibleExtent("eventServices.categoryFilter").width())
                .isGreaterThan(100);
        assertThat(user.visibleExtent("eventServices.table").height()).isGreaterThan(90);

        Component brief = named(root, "eventServices.brief");
        SwingUtilities.invokeAndWait(() -> {
            FocusEvent focus = new FocusEvent(brief, FocusEvent.FOCUS_GAINED);
            for (var listener : brief.getFocusListeners()) {
                listener.focusGained(focus);
            }
            assertThat(scroll.getViewport().getViewPosition().y).isPositive();
        });
        assertThat(user.visibleExtent("eventServices.brief").height()).isGreaterThan(45);

        Component startCrm = named(root, "eventServices.startCrmBrief");
        SwingUtilities.invokeAndWait(() -> {
            scroll.getViewport().setViewPosition(new Point(0, 0));
            FocusEvent focus = new FocusEvent(startCrm, FocusEvent.FOCUS_GAINED);
            for (var listener : startCrm.getFocusListeners()) {
                listener.focusGained(focus);
            }
            assertThat(scroll.getViewport().getViewPosition().y).isPositive();
        });
        assertThat(user.visibleExtent("eventServices.startCrmBrief").width())
                .isGreaterThan(90).isEqualTo(startCrm.getWidth());
        assertThat(user.visibleExtent("eventServices.startCrmBrief").height())
                .isEqualTo(startCrm.getHeight());
    }

    @Test
    @DisplayName("filters memorial photography and exposes dignity safeguards")
    void filtersMemorialPhotography() throws Exception {
        JComponent root = new EventServicesTab(new Host()).getUIComponent();
        JTable table = (JTable) named(root, "eventServices.table");
        JComboBox<?> occasion = (JComboBox<?>) named(root, "eventServices.occasionFilter");
        JComboBox<?> category = (JComboBox<?>) named(root, "eventServices.categoryFilter");
        JTextArea details = (JTextArea) named(root, "eventServices.details");

        SwingUtilities.invokeAndWait(() -> {
            occasion.setSelectedItem("Memorial / funeral");
            category.setSelectedItem("Photography & media");
        });
        flushEvents();

        assertThat(table.getRowCount()).isGreaterThanOrEqualTo(2);
        for (int row = 0; row < table.getRowCount(); row++) {
            assertThat(table.getValueAt(row, 1)).isEqualTo("Memorial / funeral");
            assertThat(table.getValueAt(row, 2)).isEqualTo("Photography & media");
        }
        assertThat(details.getText())
                .contains("Privacy & safety", "family", "https://");
    }

    @Test
    @DisplayName("builds a compact brief and routes it to CRM")
    void buildsBriefAndRoutesToCrm() throws Exception {
        Host host = new Host();
        JComponent root = new EventServicesTab(host).getUIComponent();
        JTextField eventName = (JTextField) named(root, "eventServices.eventName");
        JTextField venue = (JTextField) named(root, "eventServices.venue");
        JButton build = (JButton) named(root, "eventServices.buildBrief");
        JButton startCrm = (JButton) named(root, "eventServices.startCrmBrief");
        JTextArea brief = (JTextArea) named(root, "eventServices.brief");

        SwingUtilities.invokeAndWait(() -> {
            eventName.setText("Rivera wedding");
            venue.setText("Harbor Hall");
            build.doClick();
        });
        flushEvents();

        assertThat(brief.getText())
                .contains("EVENT BRIEF", "Rivera wedding", "Harbor Hall",
                        "SELECTED SERVICES", "CONFIRM BEFORE APPROVAL")
                .hasSizeLessThanOrEqualTo(1950);

        SwingUtilities.invokeAndWait(startCrm::doClick);
        flushEvents();
        assertThat(host.eventBrief).isEqualTo(brief.getText());
        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
    }

    @Test
    @DisplayName("copies the exact reviewed brief through the replaceable clipboard boundary")
    void copiesExactBrief() throws Exception {
        AtomicReference<String> copied = new AtomicReference<>();
        JComponent root = new EventServicesTab(new Host(), copied::set).getUIComponent();
        JButton build = (JButton) named(root, "eventServices.buildBrief");
        JButton copy = (JButton) named(root, "eventServices.copyBrief");
        JTextArea brief = (JTextArea) named(root, "eventServices.brief");
        JLabel feedback = (JLabel) named(root, "eventServices.feedback");

        SwingUtilities.invokeAndWait(() -> {
            build.doClick();
            copy.doClick();
        });
        flushEvents();

        assertThat(copied).hasValue(brief.getText());
        assertThat(feedback.getText()).isEqualTo("Brief copied");
    }

    @Test
    @DisplayName("CRM handoff fills a blank brief but preserves unrelated instructions")
    void crmHandoffPreservesUnrelatedDraft() throws Exception {
        CrmTab crm = new CrmTab(
                mock(CustomerService.class), mock(OrderService.class), new Host());
        JComponent root = crm.getUIComponent();
        JTextField notes = (JTextField) accessible(root, "Order notes");

        assertThat(crm.prepareEventOrderBrief("EVENT BRIEF — Wedding")).isTrue();
        assertThat(notes.getText()).isEqualTo("EVENT BRIEF — Wedding");

        SwingUtilities.invokeAndWait(() -> notes.setText("Separate family draft"));
        assertThat(crm.prepareEventOrderBrief("EVENT BRIEF — Memorial")).isFalse();
        assertThat(notes.getText()).isEqualTo("Separate family draft");
    }

    private static Component named(Component root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = named(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Component accessible(Component root, String name) {
        if (root instanceof JComponent component
                && component.getAccessibleContext() != null
                && name.equals(component.getAccessibleContext().getAccessibleName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = accessible(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void flushEvents() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static final class Host implements TabHost {
        private String selectedTab;
        private String eventBrief;

        @Override public void refreshAll() {
        }

        @Override public void selectTab(String tabTitle) {
            selectedTab = tabTitle;
        }

        @Override public void setStatus(String message) {
        }

        @Override public boolean showEventOrderDraft(
                String occasion, String services, String brief) {
            selectedTab = "Customers & Orders";
            eventBrief = brief;
            return true;
        }

        @Override public void runTrendAnalysis() {
        }
    }
}
