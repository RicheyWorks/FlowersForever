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
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
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
        assertThat(user.visibleExtent("eventServices.serviceDropTarget").height())
                .isGreaterThan(20);
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
    @DisplayName("dragging selected filtered services builds an editable event brief")
    void draggedSelectionBuildsEditableBrief() throws Exception {
        EventServicesTab tab = new EventServicesTab(new Host());
        JComponent root = tab.getUIComponent();
        JTable table = (JTable) named(root, "eventServices.table");
        JComboBox<?> occasion = (JComboBox<?>) named(root, "eventServices.occasionFilter");
        JComboBox<?> category = (JComboBox<?>) named(root, "eventServices.categoryFilter");
        JTextField eventName = (JTextField) named(root, "eventServices.eventName");
        JTextArea brief = (JTextArea) named(root, "eventServices.brief");
        JComponent target = (JComponent) named(root, "eventServices.serviceDropTarget");

        SwingUtilities.invokeAndWait(() -> {
            occasion.setSelectedItem("Wedding");
            category.setSelectedItem("Floral design");
            table.setRowSelectionInterval(0, 1);
            eventName.setText("Rivera wedding");
        });
        assertThat(table.getRowCount()).isGreaterThanOrEqualTo(2);
        String first = String.valueOf(table.getValueAt(0, 0));
        String second = String.valueOf(table.getValueAt(1, 0));
        Transferable dragged = dragPayload(table);
        TransferHandler.TransferSupport drop =
                new TransferHandler.TransferSupport(target, dragged);

        SwingUtilities.invokeAndWait(() -> {
            assertThat(target.getTransferHandler().canImport(drop)).isTrue();
            assertThat(target.getTransferHandler().importData(drop)).isTrue();
        });
        assertThat(brief.getText()).contains("Rivera wedding", first, second,
                "CONFIRM BEFORE APPROVAL");
        assertThat(brief.isEditable()).isTrue();
        assertThat(tab.applicationExitWarning()).contains("unfinished brief");
        SwingUtilities.invokeAndWait(() -> brief.append("\nClient requested a blue ribbon."));
        assertThat(brief.getText()).endsWith("Client requested a blue ribbon.");
    }

    @Test
    @DisplayName("service drop rejects stale and foreign drags without overwriting draft text")
    void serviceDropKeepsExistingBriefAndRejectsStalePayloads() throws Exception {
        EventServicesTab tab = new EventServicesTab(new Host());
        JComponent root = tab.getUIComponent();
        JTable table = (JTable) named(root, "eventServices.table");
        JComponent target = (JComponent) named(root, "eventServices.serviceDropTarget");
        JTextArea brief = (JTextArea) named(root, "eventServices.brief");
        JComboBox<?> occasion = (JComboBox<?>) named(root, "eventServices.occasionFilter");
        Transferable initial = dragPayload(table);
        TransferHandler.TransferSupport firstDrop =
                new TransferHandler.TransferSupport(target, initial);

        SwingUtilities.invokeAndWait(() -> {
            brief.setText("Keep these exact client instructions.");
            assertThat(target.getTransferHandler().canImport(firstDrop)).isFalse();
            assertThat(target.getTransferHandler().importData(firstDrop)).isFalse();
            assertThat(brief.getText()).isEqualTo("Keep these exact client instructions.");
            brief.setText(" ");
            assertThat(target.getTransferHandler().canImport(firstDrop)).isFalse();
            brief.setText("");
            table.setRowSelectionInterval(1, 1);
            assertThat(target.getTransferHandler().canImport(firstDrop)).isFalse();
            occasion.setSelectedItem("Memorial / funeral");
        });
        assertThat(target.getTransferHandler().canImport(firstDrop)).isFalse();
        assertThat(brief.getText()).isEmpty();

        EventServicesTab other = new EventServicesTab(new Host());
        JComponent otherRoot = other.getUIComponent();
        Transferable foreign = dragPayload(
                (JTable) named(otherRoot, "eventServices.table"));
        SwingUtilities.invokeAndWait(() -> {
            assertThat(target.getTransferHandler().canImport(
                    new TransferHandler.TransferSupport(target, foreign))).isFalse();
            assertThat(target.getTransferHandler().canImport(
                    new TransferHandler.TransferSupport(target,
                            new StringSelection("Ceremony flowers")))).isFalse();
            table.clearSelection();
        });
        assertThat(brief.getText()).isEmpty();
    }

    @Test
    @DisplayName("keeps unfinished event details when switching farm types")
    void protectsEventDraftUntilClearedOrHandedOff() throws Exception {
        Host host = new Host();
        EventServicesTab tab = new EventServicesTab(host);
        JComponent root = tab.getUIComponent();
        JTextField eventName = (JTextField) named(root, "eventServices.eventName");
        JTextArea brief = (JTextArea) named(root, "eventServices.brief");
        JButton clear = (JButton) named(root, "eventServices.clearBrief");
        JButton startCrm = (JButton) named(root, "eventServices.startCrmBrief");

        assertThat(tab.applicationExitWarning()).isNull();
        SwingUtilities.invokeAndWait(() -> eventName.setText("Rivera wedding"));
        assertThat(tab.applicationExitWarning()).contains("unfinished brief");

        SwingUtilities.invokeAndWait(clear::doClick);
        assertThat(eventName.getText()).isBlank();
        assertThat(tab.applicationExitWarning()).isNull();

        SwingUtilities.invokeAndWait(() -> {
            brief.setText("Reviewed wedding instructions");
            startCrm.doClick();
        });
        assertThat(host.eventBrief).isEqualTo("Reviewed wedding instructions");
        assertThat(tab.applicationExitWarning()).isNull();

        SwingUtilities.invokeAndWait(() -> brief.append("; revised"));
        assertThat(tab.applicationExitWarning()).contains("unfinished brief");
        SwingUtilities.invokeAndWait(clear::doClick);
        assertThat(tab.applicationExitWarning()).isNull();
    }

    @Test
    @DisplayName("CRM handoff preserves reviewed edits and builds a blank brief")
    void crmHandoffUsesReviewedBriefOrBuildsBlankBrief() throws Exception {
        Host host = new Host();
        JComponent root = new EventServicesTab(host).getUIComponent();
        JButton build = (JButton) named(root, "eventServices.buildBrief");
        JButton startCrm = (JButton) named(root, "eventServices.startCrmBrief");
        JTextArea brief = (JTextArea) named(root, "eventServices.brief");
        String reviewed = "EVENT BRIEF — Wedding\nUse the family entrance for setup.";

        SwingUtilities.invokeAndWait(() -> {
            build.doClick();
            brief.setText(reviewed);
            startCrm.doClick();
        });
        assertThat(brief.getText()).isEqualTo(reviewed);
        assertThat(host.eventBrief).isEqualTo(reviewed);

        SwingUtilities.invokeAndWait(() -> {
            brief.setText("");
            startCrm.doClick();
        });
        assertThat(brief.getText()).contains("EVENT BRIEF", "SELECTED SERVICES");
        assertThat(host.eventBrief).isEqualTo(brief.getText());
    }

    @Test
    @DisplayName("CRM handoff needs a selected service when the brief is blank")
    void crmHandoffDoesNotSendMissingSelectionGuidance() throws Exception {
        Host host = new Host();
        JComponent root = new EventServicesTab(host).getUIComponent();
        JTextField search = (JTextField) named(root, "eventServices.search");
        JTable table = (JTable) named(root, "eventServices.table");
        JTextArea brief = (JTextArea) named(root, "eventServices.brief");
        JButton build = (JButton) named(root, "eventServices.buildBrief");
        JButton startCrm = (JButton) named(root, "eventServices.startCrmBrief");
        JLabel feedback = (JLabel) named(root, "eventServices.feedback");

        SwingUtilities.invokeAndWait(() -> {
            search.setText("zzzz-no-matching-service");
            assertThat(table.getRowCount()).isZero();
            assertThat(brief.getText()).isBlank();
            startCrm.doClick();
        });
        assertThat(host.eventBrief).isNull();
        assertThat(host.selectedTab).isNull();
        assertThat(brief.getText()).isBlank();
        assertThat(feedback.getText()).isEqualTo("Choose one or more service rows");

        SwingUtilities.invokeAndWait(() -> {
            build.doClick();
            assertThat(brief.getText()).isBlank();
            startCrm.doClick();
        });
        assertThat(host.eventBrief).isNull();
        assertThat(host.selectedTab).isNull();

        SwingUtilities.invokeAndWait(() -> {
            search.setText("");
            assertThat(table.getSelectedRow()).isNotNegative();
            startCrm.doClick();
        });
        assertThat(host.eventBrief).startsWith("EVENT BRIEF");
        assertThat(host.eventBrief).isEqualTo(brief.getText());

        String reviewed = "Custom event instructions after the catalog search.";
        SwingUtilities.invokeAndWait(() -> {
            search.setText("zzzz-no-matching-service");
            brief.setText(reviewed);
            startCrm.doClick();
        });
        assertThat(host.eventBrief).isEqualTo(reviewed);
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

    private static Transferable dragPayload(JTable table) throws Exception {
        AtomicReference<Transferable> payload = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Clipboard clipboard = new Clipboard("event-service-drag-test");
            table.getTransferHandler().exportToClipboard(
                    table, clipboard, TransferHandler.COPY);
            payload.set(clipboard.getContents(null));
        });
        return payload.get();
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
