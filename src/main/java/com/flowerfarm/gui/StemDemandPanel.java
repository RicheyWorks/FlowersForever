package com.flowerfarm.gui;

import com.flowerfarm.service.StemDemand;
import com.flowerfarm.service.StemDemandService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Stem demand board: one list of stems by crop and date for orders in a window, with
 * what comes from the cooler, what to cut (forecast), and what is short; the same
 * window split by event as a buying list.
 */
public final class StemDemandPanel extends JPanel {

    public static final String TITLE = StemDemand.TITLE;
    static final Integer[] DAY_CHOICES = {7, 14, 21, 28};

    private final JTextField from = new JTextField(10);
    private final JComboBox<Integer> days = new JComboBox<>(DAY_CHOICES);
    private final JCheckBox drafts = new JCheckBox("Include draft orders");
    private final JButton refresh = FarmUi.secondaryButton("Refresh");
    private final JLabel headline = FarmUi.mutedLabel(" ");
    private final JLabel feedback = FarmUi.mutedLabel(" ");
    private final JTextArea notes = new JTextArea(3, 60);
    private final RowsModel<StemDemand.Line> lines = new RowsModel<>(new String[]{
            "Date", "Crop", "Needed", "Draft", "From cooler", "To cut (forecast)", "Short", "Action",
            "Needed for"}, StemDemandPanel::lineCells);
    private final RowsModel<EventRow> events = new RowsModel<>(new String[]{
            "Date", "Event", "Crop", "Needed", "From cooler", "To cut (forecast)", "Short", "Action",
            "For"}, StemDemandPanel::eventCells);
    private final RowsModel<StemDemand.NotCounted> notCounted = new RowsModel<>(new String[]{
            "Date", "Order", "Product", "Qty", "Why not counted"}, StemDemandPanel::notCountedCells);
    private final RowsModel<StemDemandService.LinkedLine> links = new RowsModel<>(new String[]{
            "Date", "Order", "Product", "Designs", "Counted as recipe"}, StemDemandPanel::linkCells);
    private final JTable lineTable = new JTable(lines);
    private final JTable eventTable = new JTable(events);
    private final JTable notCountedTable = new JTable(notCounted);
    private final JTable linkTable = new JTable(links);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JScrollPane eventScroll;
    private final JScrollPane notCountedScroll;
    private final JScrollPane linkScroll;
    private final java.util.function.Consumer<String> status;
    private StemDemand.Board board;

    public StemDemandPanel(java.util.function.Consumer<String> status, LocalDate today) {
        this.status = Objects.requireNonNullElse(status, ignored -> { });
        setName("stemDemand.panel");
        setLayout(new BorderLayout(0, FarmUi.SECTION_GAP));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        getAccessibleContext().setAccessibleName(TITLE);

        from.setText(String.valueOf(Objects.requireNonNull(today, "today")));
        from.setName("stemDemand.from");
        from.getAccessibleContext().setAccessibleName("First date (YYYY-MM-DD)");
        days.setName("stemDemand.days");
        days.setSelectedItem(StemDemandService.DEFAULT_DAYS);
        days.getAccessibleContext().setAccessibleName("Number of days");
        drafts.setName("stemDemand.drafts");
        drafts.setOpaque(false);
        refresh.setName("stemDemand.refresh");
        headline.setName("stemDemand.headline");
        feedback.setName("stemDemand.feedback");
        notes.setName("stemDemand.notes");
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setOpaque(false);

        table(lineTable, "stemDemand.lines", "Stems needed by crop and date");
        table(eventTable, "stemDemand.events", "Buying list by event");
        table(notCountedTable, "stemDemand.notCounted", "Order lines without a stem count");
        table(linkTable, "stemDemand.links", "Order lines counted as saved recipes");
        eventScroll = scroll(eventTable, 260);
        notCountedScroll = scroll(notCountedTable, 260);
        linkScroll = scroll(linkTable, 260);
        tabs.setName("stemDemand.tabs");
        tabs.addTab("Stems by crop and date", scroll(lineTable, 260));
        tabs.addTab("By event (0)", eventScroll);
        tabs.addTab("Not counted (0)", notCountedScroll);
        tabs.addTab("Counted as recipes (0)", linkScroll);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, FarmUi.CONTROL_GAP, 0));
        filters.setOpaque(false);
        filters.add(new JLabel("From:"));
        filters.add(from);
        filters.add(new JLabel("Days:"));
        filters.add(days);
        filters.add(drafts);
        filters.add(refresh);

        JPanel north = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        north.setOpaque(false);
        north.add(filters, BorderLayout.NORTH);
        north.add(headline, BorderLayout.SOUTH);

        JPanel south = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        south.setOpaque(false);
        south.add(notes, BorderLayout.CENTER);
        south.add(feedback, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        body.setOpaque(false);
        body.add(north, BorderLayout.NORTH);
        body.add(tabs, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        add(FarmUi.section(TITLE, "Stems that confirmed orders need, by crop and date or by event. The "
                + "cooler covers first, then the season plan's projected harvest; anything left is short.", body),
                BorderLayout.CENTER);
    }

    private static void table(JTable table, String name, String accessible) {
        table.setName(name);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(24);
        table.getAccessibleContext().setAccessibleName(accessible);
    }

    private static JScrollPane scroll(JTable table, int height) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(860, height));
        return scroll;
    }

    public void onRefresh(Runnable action) {
        refresh.addActionListener(event -> action.run());
    }

    /** The window chosen in the filters; a bad date raises a plain message. */
    public Window window() {
        LocalDate start;
        try {
            start = LocalDate.parse(from.getText().trim());
        } catch (DateTimeParseException bad) {
            throw new IllegalArgumentException("Type the first date as YYYY-MM-DD, for example 2026-10-12.");
        }
        Integer chosen = (Integer) days.getSelectedItem();
        return new Window(start, chosen == null ? StemDemandService.DEFAULT_DAYS : chosen, drafts.isSelected());
    }

    public void showBoard(StemDemand.Board next, List<StemDemandService.LinkedLine> linked) {
        this.board = Objects.requireNonNull(next, "board");
        lines.setRows(next.lines());
        events.setRows(eventRows(next));
        notCounted.setRows(next.notCounted());
        links.setRows(linked == null ? List.of() : linked);
        tabs.setTitleAt(tabs.indexOfComponent(eventScroll), "By event (" + next.events().size() + ")");
        tabs.setTitleAt(tabs.indexOfComponent(notCountedScroll), "Not counted (" + next.notCounted().size() + ")");
        tabs.setTitleAt(tabs.indexOfComponent(linkScroll), "Counted as recipes (" + links.getRowCount() + ")");
        headline.setText(StemDemand.summary(next));
        notes.setText(String.join("\n", next.notes()));
        feedback.setText(" ");
    }

    public Optional<StemDemand.Board> board() {
        return Optional.ofNullable(board);
    }

    /** Opens on the buying list by event instead of the crop-and-date list. */
    public void showByEvent() {
        tabs.setSelectedComponent(eventScroll);
    }

    /** A not-counted line selected under "Not counted", or under "By event". */
    public Optional<StemDemand.NotCounted> selectedNotCounted() {
        Optional<StemDemand.NotCounted> line = selected(notCountedTable, notCounted);
        if (line.isPresent()) {
            return line;
        }
        return selected(eventTable, events).map(EventRow::notCounted);
    }

    public Optional<StemDemandService.LinkedLine> selectedLink() {
        return selected(linkTable, links);
    }

    private static <T> Optional<T> selected(JTable table, RowsModel<T> model) {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? Optional.empty() : model.rowAt(table.convertRowIndexToModel(viewRow));
    }

    public void announce(String message) {
        feedback.setText(message);
        status.accept(message);
    }

    public record Window(LocalDate from, int days, boolean includeDrafts) {
    }

    private static Object[] lineCells(StemDemand.Line line) {
        return new Object[]{line.date().toString(), line.crop(), line.needed(), line.draftStems(),
                line.fromCooler(), line.toCut(), line.shortBy(), line.action().label(), line.neededFor()};
    }

    /** One row in the by-event table: a crop the event needs, or a line not counted. */
    record EventRow(LocalDate date, String event, String crop, Integer needed, Integer fromCooler, Integer toCut,
                    Integer shortBy, String action, String madeFor, StemDemand.NotCounted notCounted) {
    }

    static List<EventRow> eventRows(StemDemand.Board board) {
        List<EventRow> rows = new ArrayList<>();
        for (StemDemand.Event event : board.events()) {
            for (StemDemand.EventLine line : event.lines()) {
                rows.add(new EventRow(event.date(), event.event(), line.crop(), line.needed(), line.fromCooler(),
                        line.toCut(), line.shortBy(), line.action().label(), line.madeFor(), null));
            }
            for (StemDemand.NotCounted line : event.notCounted()) {
                rows.add(new EventRow(event.date(), event.event(), line.product(), null, null, null, null,
                        "Not counted", trim(line.quantity()) + " " + line.unit() + " · " + line.reason(), line));
            }
        }
        return rows;
    }

    private static Object[] eventCells(EventRow row) {
        return new Object[]{row.date().toString(), row.event(), row.crop(), row.needed(), row.fromCooler(),
                row.toCut(), row.shortBy(), row.action(), row.madeFor()};
    }

    private static Object[] notCountedCells(StemDemand.NotCounted line) {
        return new Object[]{line.date().toString(), line.source(), line.product(),
                trim(line.quantity()) + " " + line.unit(), line.reason()};
    }

    private static Object[] linkCells(StemDemandService.LinkedLine line) {
        return new Object[]{line.date().toString(), line.source(), line.product(), line.designs(),
                line.recipeName() == null ? "(recipe deleted)" : line.recipeName()};
    }

    private static String trim(double value) {
        return Math.rint(value) == value ? Long.toString((long) value) : Double.toString(value);
    }

    private static final class RowsModel<T> extends AbstractTableModel {
        private final transient List<T> rows = new ArrayList<>();
        private final String[] columns;
        private final transient Function<T, Object[]> cells;

        RowsModel(String[] columns, Function<T, Object[]> cells) {
            this.columns = columns.clone();
            this.cells = cells;
        }

        void setRows(List<T> next) {
            rows.clear();
            rows.addAll(next);
            fireTableDataChanged();
        }

        Optional<T> rowAt(int row) {
            return row < 0 || row >= rows.size() ? Optional.empty() : Optional.of(rows.get(row));
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            for (T row : rows) {
                Object cell = cells.apply(row)[column];
                if (cell != null) {
                    return cell instanceof Integer ? Integer.class : Object.class;
                }
            }
            return Object.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return cells.apply(rows.get(rowIndex))[columnIndex];
        }
    }
}
