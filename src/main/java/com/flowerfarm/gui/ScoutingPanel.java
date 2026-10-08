package com.flowerfarm.gui;

import com.flowerfarm.scouting.LabDirectory;
import com.flowerfarm.scouting.PestReference;
import com.flowerfarm.scouting.SampleGuides;
import com.flowerfarm.scouting.Scouting;
import com.flowerfarm.scouting.ScoutingService;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** Help → Scouting &amp; lab samples: findings, samples, pest guide, labs, and packing guides. */
public final class ScoutingPanel extends JPanel {

    public static final String TITLE = Scouting.TITLE;
    static final int FINDINGS_TAB = 0;
    static final int SAMPLES_TAB = 1;

    private final FindingModel findings = new FindingModel();
    private final SampleModel samples = new SampleModel();
    private final JTable findingTable = new JTable(findings);
    private final JTable sampleTable = new JTable(samples);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel headline = FarmUi.mutedLabel(" ");
    private final JLabel feedback = FarmUi.mutedLabel(" ");
    private final Consumer<String> status;

    public ScoutingPanel(Consumer<String> status) {
        this.status = Objects.requireNonNullElse(status, ignored -> { });
        setName("scouting.panel");
        setLayout(new BorderLayout(0, FarmUi.CONTROL_GAP));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        getAccessibleContext().setAccessibleName(TITLE);
        headline.setName("scouting.headline");
        feedback.setName("scouting.feedback");

        findingTable.setName("scouting.findings");
        FarmUi.configureTable(findingTable, "Scouting findings, newest first");
        findingTable.setAutoCreateRowSorter(true);
        sampleTable.setName("scouting.samples");
        FarmUi.configureTable(sampleTable, "Lab samples, overdue first");
        sampleTable.setAutoCreateRowSorter(true);

        tabs.setName("scouting.tabs");
        tabs.addTab("Findings", scroll(findingTable));
        tabs.addTab("Lab samples", scroll(sampleTable));
        tabs.addTab("Pest & disease guide", browser("scouting.reference", PestReference.all(),
                PestReference.Entry::name, ScoutingPanel::referenceText));
        tabs.addTab("Labs", browser("scouting.labs", LabDirectory.all(), LabDirectory.Lab::name,
                ScoutingPanel::labText));
        tabs.addTab("How to send a sample", browser("scouting.guides", SampleGuides.all(),
                SampleGuides.Guide::title, SampleGuides.Guide::asText));

        add(headline, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(feedback, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(900, 460));
    }

    private static JScrollPane scroll(JTable table) {
        JScrollPane scroll = FarmUi.tableScroll(table);
        scroll.setPreferredSize(new Dimension(860, 320));
        return scroll;
    }

    private static <T> JSplitPane browser(String name, List<T> items, Function<T, String> title,
                                          Function<T, String> text) {
        List<String> titles = items.stream().map(title).toList();
        JList<String> list = new JList<>(titles.toArray(String[]::new));
        list.setName(name + ".list");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName(name.substring(name.indexOf('.') + 1) + " list");
        JTextArea area = FarmUi.displayArea(16, 48);
        area.setName(name + ".text");
        list.addListSelectionListener(event -> {
            int index = list.getSelectedIndex();
            area.setText(index < 0 ? "" : text.apply(items.get(index)));
            area.setCaretPosition(0);
        });
        if (!items.isEmpty()) {
            list.setSelectedIndex(0);
        }
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(250, 320));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, new JScrollPane(area));
        split.setName(name);
        split.setResizeWeight(0.25);
        return split;
    }

    static String referenceText(PestReference.Entry entry) {
        return entry.name() + "\n" + entry.category().label()
                + (entry.organism().isEmpty() ? "" : " · " + entry.organism())
                + "\n\nWhat to look for\n" + entry.lookFor()
                + "\n\nScouting tips\n" + entry.scoutingTips()
                + "\n\nSource: " + entry.sourceTitle() + "\n" + entry.sourceUrl()
                + "\n\nA guide only. Confirm a diagnosis with a lab before spraying or pulling plants.";
    }

    static String labText(LabDirectory.Lab lab) {
        List<String> accepts = lab.accepts().stream().map(Scouting.SampleType::label).toList();
        StringBuilder text = new StringBuilder();
        text.append(lab.name()).append("\n\n").append(lab.whatTheyDo())
                .append("\n\nAccepts: ").append(String.join(", ", accepts))
                .append("\nAddress: ").append(lab.address())
                .append("\nPhone: ").append(lab.phone())
                .append("\nEmail: ").append(lab.email())
                .append("\nFees: ").append(lab.fees())
                .append("\nSubmission form: ").append(lab.formUrl())
                .append("\nMore information: ").append(lab.infoUrl());
        if (!lab.notes().isEmpty()) {
            text.append("\n\n").append(lab.notes());
        }
        text.append("\n\nChecked ").append(LabDirectory.CHECKED)
                .append(". Fees, hours, and addresses change; confirm with the lab before sending.");
        return text.toString();
    }

    public void showBoard(ScoutingService.Board board) {
        Objects.requireNonNull(board, "board");
        findings.set(board.findings());
        samples.set(board.samples(), board);
        headline.setText(board.headline());
    }

    public void announce(String message) {
        String text = message == null || message.isBlank() ? " " : message;
        feedback.setText(text);
        status.accept(text.trim());
    }

    public void showTab(int index) {
        tabs.setSelectedIndex(index);
    }

    public Optional<Scouting.Finding> selectedFinding() {
        int row = findingTable.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(findings.rows.get(findingTable.convertRowIndexToModel(row)));
    }

    /** Selects a finding by id (after a save), so follow-up actions apply to it. */
    void selectFinding(String id) {
        for (int row = 0; row < findings.rows.size(); row++) {
            if (findings.rows.get(row).id().equals(id)) {
                int view = findingTable.convertRowIndexToView(row);
                findingTable.setRowSelectionInterval(view, view);
                return;
            }
        }
    }

    public Optional<Scouting.LabSample> selectedSample() {
        int row = sampleTable.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(samples.rows.get(sampleTable.convertRowIndexToModel(row)));
    }

    private static final class FindingModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"ID", "Date", "Bed / block", "Crop", "Category",
            "Suspected", "Severity", "% affected", "Photos", "Action"};
        private transient List<Scouting.Finding> rows = new ArrayList<>();

        void set(List<Scouting.Finding> list) {
            rows = new ArrayList<>(list);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Scouting.Finding f = rows.get(row);
            return switch (column) {
                case 0 -> f.id();
                case 1 -> String.valueOf(f.date());
                case 2 -> f.bed();
                case 3 -> f.variety().isEmpty() ? f.crop() : f.crop() + " (" + f.variety() + ")";
                case 4 -> f.category().label();
                case 5 -> f.organism();
                case 6 -> f.severity().label();
                case 7 -> f.incidencePercent() == null ? "" : f.incidencePercent() + "%";
                case 8 -> f.photos().isEmpty() ? "" : String.valueOf(f.photos().size());
                default -> f.action();
            };
        }
    }

    private static final class SampleModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"ID", "Type", "Lab", "Collected", "Shipped", "Tracking",
            "Status", "Waiting", "Result"};
        private transient List<Scouting.LabSample> rows = new ArrayList<>();
        private transient ScoutingService.Board board;

        void set(List<Scouting.LabSample> list, ScoutingService.Board current) {
            rows = new ArrayList<>(list);
            board = current;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Scouting.LabSample s = rows.get(row);
            return switch (column) {
                case 0 -> s.id();
                case 1 -> s.type().label();
                case 2 -> s.labName();
                case 3 -> String.valueOf(s.collected());
                case 4 -> s.shipped() == null ? "" : s.shipped().toString();
                case 5 -> s.tracking();
                case 6 -> s.status().label();
                case 7 -> waiting(s);
                default -> s.resultSummary().isEmpty() ? s.reportPath() : s.resultSummary();
            };
        }

        private String waiting(Scouting.LabSample s) {
            if (s.status() == Scouting.SampleStatus.RESULTS_IN || board == null) {
                return "";
            }
            long days = Scouting.daysWaiting(s, board.today());
            return (board.overdue(s) ? "OVERDUE · " : "") + days + (days == 1 ? " day" : " days");
        }
    }
}
