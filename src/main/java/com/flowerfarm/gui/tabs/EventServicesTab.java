package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.FarmClipboard;
import com.flowerfarm.model.EventServiceProfile;
import com.flowerfarm.reference.EventServiceCatalog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Sourced wedding and memorial service starters with a CRM brief handoff. */
public final class EventServicesTab implements FlowerFarmTab {

    private static final int MAX_BRIEF_LENGTH = 1950;
    private static final int MAX_BRIEF_SERVICES = 8;

    private final TabHost host;
    private final FarmClipboard clipboard;
    private final List<EventServiceProfile> services = EventServiceCatalog.all();
    private JPanel panel;
    private JTable table;
    private JTextArea brief;
    private JLabel feedback;
    private JButton startCrmButton;
    private boolean canWrite = true;

    public EventServicesTab(TabHost host) {
        this(host, FarmClipboard.system());
    }

    EventServicesTab(TabHost host, FarmClipboard clipboard) {
        this.host = host;
        this.clipboard = java.util.Objects.requireNonNull(clipboard, "clipboard");
    }

    @Override
    public String getTabTitle() {
        return "Event Services";
    }

    @Override
    public String getDescription() {
        return "Wedding and memorial floral, catering, décor, photography, and logistics";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUi();
        }
        return panel;
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        if (startCrmButton != null) {
            startCrmButton.setEnabled(canWrite);
            startCrmButton.setToolTipText(canWrite
                    ? "Prepare these event instructions in a new CRM order draft."
                    : "Viewer access can build and copy briefs but cannot prepare an order draft.");
        }
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("eventServices.page");
        JLabel catalogCount = FarmUi.statusBadge(
                services.size() + " service starters", FarmUi.Tone.INFO);
        catalogCount.setName("eventServices.catalogCount");
        panel.add(FarmUi.pageHeader(
                "Wedding & memorial services",
                "Plan flowers, catering, décor, photography, setup, and vendor handoffs without "
                        + "mixing non-stock services into physical inventory.",
                catalogCount), BorderLayout.NORTH);

        EventTableModel model = new EventTableModel(services);
        table = new JTable(model);
        table.setName("eventServices.table");
        FarmUi.configureTable(table,
                "Wedding and memorial event service starters; select one or more services");
        // configureTable defaults ordinary grids to single-selection; event briefs
        // intentionally combine several deliverables through Ctrl/Cmd-click.
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        TableRowSorter<EventTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(115);

        JTextField search = FarmUi.searchField(
                "Event service search",
                "Search services, deliverables, operations, privacy, or source", 22);
        search.setName("eventServices.search");
        JComboBox<String> occasion = new JComboBox<>();
        occasion.setName("eventServices.occasionFilter");
        occasion.addItem("All occasions");
        EventServiceCatalog.occasions().forEach(occasion::addItem);
        occasion.getAccessibleContext().setAccessibleName("Event occasion filter");
        JComboBox<String> category = new JComboBox<>();
        category.setName("eventServices.categoryFilter");
        category.addItem("All categories");
        EventServiceCatalog.categories().forEach(category::addItem);
        category.getAccessibleContext().setAccessibleName("Event service category filter");
        JLabel resultCount = FarmUi.statusBadge(services.size() + " matches", FarmUi.Tone.INFO);
        resultCount.setName("eventServices.results");

        Runnable applyFilters = () -> {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            String selectedOccasion = String.valueOf(occasion.getSelectedItem());
            String selectedCategory = String.valueOf(category.getSelectedItem());
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(
                        Entry<? extends EventTableModel, ? extends Integer> entry) {
                    EventServiceProfile service = model.serviceAt(entry.getIdentifier());
                    return (query.isBlank() || service.searchableText().contains(query))
                            && (selectedOccasion.startsWith("All")
                            || service.occasion().equals(selectedOccasion))
                            && (selectedCategory.startsWith("All")
                            || service.category().equals(selectedCategory));
                }
            });
            FarmUi.updateStatusBadge(resultCount,
                    table.getRowCount() + " matches", FarmUi.Tone.INFO);
            if (table.getRowCount() > 0) {
                table.setRowSelectionInterval(0, 0);
            } else {
                table.clearSelection();
            }
        };
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) {
                applyFilters.run();
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) {
                applyFilters.run();
            }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) {
                applyFilters.run();
            }
        });
        occasion.addActionListener(event -> applyFilters.run());
        category.addActionListener(event -> applyFilters.run());
        JButton clear = FarmUi.secondaryButton("Clear service filters");
        clear.setName("eventServices.clearFilters");
        clear.addActionListener(event -> {
            search.setText("");
            occasion.setSelectedIndex(0);
            category.setSelectedIndex(0);
        });
        JPanel filters = new JPanel();
        filters.setName("eventServices.filters");
        filters.setOpaque(false);
        filters.setLayout(new BoxLayout(filters, BoxLayout.Y_AXIS));
        JPanel searchRow = FarmUi.toolbar(
                new JLabel("Search:"), search,
                new JLabel("Occasion:"), occasion);
        searchRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JPanel categoryRow = FarmUi.toolbar(
                new JLabel("Category:"), category, clear, resultCount,
                FarmUi.formHint("Ctrl/Cmd-click rows to combine services."));
        categoryRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        filters.add(searchRow);
        filters.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        filters.add(categoryRow);

        JTextArea details = FarmUi.displayArea(18, 40);
        details.setName("eventServices.details");
        details.getAccessibleContext().setAccessibleName(
                "Selected event service details and source");
        JScrollPane detailScroll = new JScrollPane(details);
        detailScroll.setBorder(BorderFactory.createLineBorder(FarmUi.borderColor()));
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelection(model, details);
            }
        });
        JPanel serviceDetails = FarmUi.section(
                "Selected service",
                "Operational prompts are starters—not vendor quotes, contracts, permits, or legal advice.",
                detailScroll);
        serviceDetails.setMinimumSize(new Dimension(365, 260));

        JSplitPane catalogSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                FarmUi.tableScroll(table), serviceDetails);
        catalogSplit.setName("eventServices.catalogSplit");
        catalogSplit.setResizeWeight(0.61);
        catalogSplit.setContinuousLayout(true);
        catalogSplit.setBorder(null);

        JPanel catalog = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        catalog.setOpaque(false);
        catalog.setMinimumSize(new Dimension(0, 250));
        catalog.add(filters, BorderLayout.NORTH);
        catalog.add(catalogSplit, BorderLayout.CENTER);

        JPanel builder = buildBriefBuilder(occasion);
        builder.setMinimumSize(new Dimension(0, 190));
        builder.setPreferredSize(new Dimension(0, 245));
        JSplitPane workspace = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                FarmUi.section("Service catalog",
                        "Filter the starter library, then select one or several deliverables.",
                        catalog), builder);
        workspace.setName("eventServices.workspace");
        workspace.setResizeWeight(0.60);
        workspace.setDividerLocation(325);
        workspace.setContinuousLayout(true);
        workspace.setBorder(null);
        panel.add(workspace, BorderLayout.CENTER);
        panel.add(FarmUi.formHint(
                "Catering must be handled by an appropriately licensed provider. Confirm venue, "
                        + "insurance, accessibility, cultural, privacy, image-rights, and local rules."),
                BorderLayout.SOUTH);

        if (!services.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
            showSelection(model, details);
        }
        applyRolePermissions(canWrite);
    }

    private JPanel buildBriefBuilder(JComboBox<String> occasionFilter) {
        JTextField eventName = new JTextField(15);
        eventName.setName("eventServices.eventName");
        eventName.getAccessibleContext().setAccessibleName("Event or family name");
        JTextField eventDate = new JTextField(10);
        eventDate.setName("eventServices.eventDate");
        eventDate.getAccessibleContext().setAccessibleName("Event date");
        eventDate.setToolTipText("Use YYYY-MM-DD when the date is known.");
        JTextField venue = new JTextField(15);
        venue.setName("eventServices.venue");
        venue.getAccessibleContext().setAccessibleName("Event venue or locations");
        JTextField guestCount = new JTextField(7);
        guestCount.setName("eventServices.guestCount");
        guestCount.getAccessibleContext().setAccessibleName("Estimated guest count");

        brief = FarmUi.displayArea(9, 70);
        brief.setName("eventServices.brief");
        brief.setEditable(true);
        brief.getAccessibleContext().setAccessibleName("Editable event service brief");
        JScrollPane briefScroll = new JScrollPane(brief);
        briefScroll.setBorder(BorderFactory.createLineBorder(FarmUi.borderColor()));

        JButton build = FarmUi.primaryButton("Build selected-service brief");
        build.setName("eventServices.buildBrief");
        build.addActionListener(event -> buildBrief(
                selectedProfiles(), occasionFilter, eventName, eventDate, venue, guestCount));
        JButton copy = FarmUi.secondaryButton("Copy brief");
        copy.setName("eventServices.copyBrief");
        copy.addActionListener(event -> copyBrief());
        startCrmButton = FarmUi.secondaryButton("Start CRM order brief");
        startCrmButton.setName("eventServices.startCrmBrief");
        startCrmButton.addActionListener(event -> {
            List<EventServiceProfile> selected = selectedProfiles();
            buildBrief(selected, occasionFilter, eventName, eventDate, venue, guestCount);
            if (host == null) {
                showFeedback("CRM handoff is unavailable in this view", FarmUi.Tone.WARNING);
                return;
            }
            String serviceNames = selected.isEmpty() ? "event services"
                    : selected.stream().map(EventServiceProfile::serviceName)
                            .limit(3).reduce((left, right) -> left + ", " + right)
                            .orElse("event services");
            String occasion = selected.isEmpty()
                    ? String.valueOf(occasionFilter.getSelectedItem())
                    : selected.stream().map(EventServiceProfile::occasion).distinct()
                            .reduce((left, right) -> left + " / " + right).orElse("Event");
            boolean prepared = host.showEventOrderDraft(
                    occasion, serviceNames, brief.getText());
            showFeedback(prepared
                            ? "Brief sent to Customers & Orders"
                            : "CRM opened · existing instructions kept; copy brief when ready",
                    prepared ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
        });
        feedback = FarmUi.statusBadge("Select services and build a brief", FarmUi.Tone.NEUTRAL);
        feedback.setName("eventServices.feedback");

        JPanel fields = FarmUi.toolbar(
                new JLabel("Event / family:"), eventName,
                new JLabel("Date:"), eventDate,
                new JLabel("Venue:"), venue,
                new JLabel("Guests:"), guestCount);
        JPanel actions = FarmUi.toolbar(build, copy, startCrmButton, feedback);
        JPanel content = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        content.setOpaque(false);
        content.add(fields, BorderLayout.NORTH);
        content.add(briefScroll, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        return FarmUi.section(
                "Event brief builder",
                "Build a compact handoff for the event record; CRM keeps physical flower lines separate.",
                content);
    }

    private void buildBrief(List<EventServiceProfile> selected,
                            JComboBox<String> occasionFilter,
                            JTextField eventName, JTextField eventDate,
                            JTextField venue, JTextField guestCount) {
        if (selected.isEmpty()) {
            brief.setText("Select at least one visible service before building the event brief.");
            showFeedback("Choose one or more service rows", FarmUi.Tone.WARNING);
            return;
        }
        String occasion = selected.stream().map(EventServiceProfile::occasion).distinct()
                .reduce((left, right) -> left + " / " + right)
                .orElse(String.valueOf(occasionFilter.getSelectedItem()));
        StringBuilder text = new StringBuilder("EVENT BRIEF — ").append(occasion).append('\n')
                .append("Event / family: ").append(valueOrTbd(eventName.getText())).append('\n')
                .append("Date: ").append(valueOrTbd(eventDate.getText())).append('\n')
                .append("Venue(s): ").append(valueOrTbd(venue.getText())).append('\n')
                .append("Estimated guests: ").append(valueOrTbd(guestCount.getText()))
                .append("\n\nSELECTED SERVICES\n");
        selected.stream().limit(MAX_BRIEF_SERVICES).forEach(service -> text
                .append("• ").append(service.serviceName()).append(" [")
                .append(service.category()).append("] — ")
                .append(service.deliverables()).append('\n'));
        if (selected.size() > MAX_BRIEF_SERVICES) {
            text.append("• Plus ").append(selected.size() - MAX_BRIEF_SERVICES)
                    .append(" additional selected services; review the catalog selection.\n");
        }
        text.append("\nCONFIRM BEFORE APPROVAL\n")
                .append("• Family/client decision maker, venue contacts, addresses, access, setup, "
                        + "strike, weather backup, accessibility, and final destinations.\n")
                .append("• Written scope, quantities, service providers, quote/deposit dates, "
                        + "change approvals, rentals, and return responsibility.\n")
                .append("• Catering: guest count, dietary/allergy notes, licensed provider, kitchen, "
                        + "serviceware, service timing, permits, and cleanup.\n")
                .append("• Photography/media: exact coverage, family/client and venue permission, "
                        + "private exclusions, usage rights, approver, preview, and delivery.\n")
                .append("• Memorial work: use family-led language, cultural and faith preferences, "
                        + "and no public image sharing by default.\n")
                .append("• Keep flowers and other physical goods as CRM product lines; retain "
                        + "non-stock services and vendor responsibilities in this brief.");
        String built = text.length() <= MAX_BRIEF_LENGTH
                ? text.toString() : text.substring(0, MAX_BRIEF_LENGTH - 1) + "…";
        brief.setText(built);
        brief.setCaretPosition(0);
        showFeedback(selected.size() + " service"
                + (selected.size() == 1 ? "" : "s") + " in brief", FarmUi.Tone.SUCCESS);
    }

    private void copyBrief() {
        if (brief.getText().isBlank()) {
            showFeedback("Build a brief before copying", FarmUi.Tone.WARNING);
            return;
        }
        try {
            clipboard.copyText(brief.getText());
            showFeedback("Brief copied", FarmUi.Tone.SUCCESS);
        } catch (HeadlessException | IllegalStateException exception) {
            showFeedback("Clipboard unavailable; select the brief text to copy",
                    FarmUi.Tone.WARNING);
        }
    }

    private void showSelection(EventTableModel model, JTextArea details) {
        List<EventServiceProfile> selected = selectedProfiles();
        if (selected.isEmpty()) {
            details.setText("No services match the current filters.");
            return;
        }
        EventServiceProfile service = selected.get(0);
        String prefix = selected.size() == 1 ? ""
                : selected.size() + " services selected; showing the first.\n\n";
        details.setText(prefix + service.serviceName() + "\n"
                + service.occasion() + " · " + service.category() + "\n\n"
                + "Pricing basis: " + service.pricingBasis() + "\n"
                + "Lead time: " + service.leadTime() + "\n\n"
                + "Deliverables\n" + service.deliverables() + "\n\n"
                + "Ask during intake\n" + service.intakeQuestions() + "\n\n"
                + "Operations checklist\n" + service.operationsChecklist() + "\n\n"
                + "Privacy & safety\n" + service.privacyAndSafety() + "\n\n"
                + "Workflow source: " + service.source().label() + "\n"
                + service.source().url());
        details.setCaretPosition(0);
    }

    private List<EventServiceProfile> selectedProfiles() {
        if (table == null) {
            return List.of();
        }
        Set<Integer> modelRows = new LinkedHashSet<>();
        for (int row : table.getSelectedRows()) {
            modelRows.add(table.convertRowIndexToModel(row));
        }
        List<EventServiceProfile> selected = new ArrayList<>();
        for (Integer modelRow : modelRows) {
            selected.add(services.get(modelRow));
        }
        return List.copyOf(selected);
    }

    private void showFeedback(String message, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(feedback, message, tone);
        feedback.setToolTipText(message);
        feedback.getAccessibleContext().setAccessibleDescription(message);
        if (host != null) {
            host.setStatus(message);
        }
    }

    private static String valueOrTbd(String value) {
        return value == null || value.isBlank() ? "TBD" : value.trim();
    }

    private static final class EventTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Service", "Occasion", "Category", "Pricing basis"};
        private final List<EventServiceProfile> rows;

        private EventTableModel(List<EventServiceProfile> rows) {
            this.rows = rows;
        }

        @Override public int getRowCount() {
            return rows.size();
        }

        @Override public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            EventServiceProfile service = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> service.serviceName();
                case 1 -> service.occasion();
                case 2 -> service.category();
                case 3 -> service.pricingBasis();
                default -> "";
            };
        }

        private EventServiceProfile serviceAt(int row) {
            return rows.get(row);
        }
    }
}
