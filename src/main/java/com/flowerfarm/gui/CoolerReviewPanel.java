package com.flowerfarm.gui;

import com.flowerfarm.model.HarvestGrade;
import com.flowerfarm.service.CoolerShelfLife;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** In-app cooler shelf-life board: aging lots, review actions, crop intervals. */
public final class CoolerReviewPanel extends JPanel {

    public static final String TITLE = CoolerShelfLife.TITLE;

    private final LotTableModel model = new LotTableModel();
    private final JTable table = new JTable(model);
    private final JLabel headline = FarmUi.mutedLabel(" ");
    private final JLabel guidance = FarmUi.mutedLabel(" ");
    private final JLabel feedback = FarmUi.mutedLabel(" ");
    private final Consumer<String> status;

    public CoolerReviewPanel(Consumer<String> status) {
        this.status = Objects.requireNonNullElse(status, ignored -> { });
        setName("cooler.panel");
        setLayout(new BorderLayout(0, FarmUi.SECTION_GAP));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        getAccessibleContext().setAccessibleName(TITLE);

        headline.setName("cooler.headline");
        guidance.setName("cooler.guidance");
        feedback.setName("cooler.feedback");

        table.setName("cooler.lots");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(24);
        table.getAccessibleContext().setAccessibleName("Cooler lots by age");
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(760, 280));

        JPanel north = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        north.setOpaque(false);
        north.add(headline, BorderLayout.NORTH);
        north.add(guidance, BorderLayout.SOUTH);

        add(FarmUi.section(TITLE, CoolerShelfLife.pageGuidance(), body(north, scroll)),
                BorderLayout.CENTER);
    }

    private JPanel body(JPanel north, JScrollPane scroll) {
        JPanel body = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        body.setOpaque(false);
        body.add(north, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        body.add(feedback, BorderLayout.SOUTH);
        return body;
    }

    public void showBoard(CoolerShelfLife.Board board) {
        Objects.requireNonNull(board, "board");
        model.setLots(board.lots());
        headline.setText(board.headline());
        guidance.setText(board.guidance());
        if (board.isEmpty()) {
            announce(CoolerShelfLife.statusEmpty());
        } else {
            feedback.setText(" ");
        }
    }

    public Optional<CoolerShelfLife.AgedLot> selectedLot() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return Optional.empty();
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        return model.lotAt(modelRow);
    }

    public JPanel buildReviewForm(CoolerShelfLife.AgedLot lot) {
        Objects.requireNonNull(lot, "lot");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setName("cooler.reviewForm");
        form.setOpaque(false);
        form.add(FarmUi.mutedLabel("Lot " + lot.lotCode() + " · " + lot.cropName()
                        + " · " + lot.storageLocation()
                        + " · age " + lot.ageDays() + " day"
                        + (lot.ageDays() == 1 ? "" : "s")),
                BorderLayout.NORTH);

        JSpinner count = new JSpinner(new SpinnerNumberModel(
                Double.valueOf(lot.marketableQuantity()),
                Double.valueOf(0.5),
                Double.valueOf(Integer.MAX_VALUE),
                Double.valueOf(1)));
        count.setName("cooler.count");
        JSpinner loss = new JSpinner(new SpinnerNumberModel(0.0, 0.0, Integer.MAX_VALUE, 1.0));
        loss.setName("cooler.loss");
        JComboBox<HarvestGrade> grade = new JComboBox<>(HarvestGrade.values());
        grade.setSelectedItem(lot.grade());
        grade.setName("cooler.grade");
        JTextField note = new JTextField(28);
        note.setName("cooler.note");
        note.getAccessibleContext().setAccessibleName("Review note");

        JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, FarmUi.CONTROL_GAP, FarmUi.CONTROL_GAP));
        fields.setOpaque(false);
        fields.add(new JLabel("Counted marketable:"));
        fields.add(count);
        fields.add(new JLabel("Additional loss / culls:"));
        fields.add(loss);
        fields.add(new JLabel("Quality grade:"));
        fields.add(grade);
        fields.add(new JLabel("Note (optional):"));
        fields.add(note);
        form.add(fields, BorderLayout.CENTER);
        form.putClientProperty("cooler.count", count);
        form.putClientProperty("cooler.loss", loss);
        form.putClientProperty("cooler.grade", grade);
        form.putClientProperty("cooler.note", note);
        return form;
    }

    public CoolerShelfLife.ReviewAction readReviewForm(JPanel form) {
        JSpinner count = (JSpinner) form.getClientProperty("cooler.count");
        JSpinner loss = (JSpinner) form.getClientProperty("cooler.loss");
        @SuppressWarnings("unchecked")
        JComboBox<HarvestGrade> grade = (JComboBox<HarvestGrade>) form.getClientProperty("cooler.grade");
        JTextField note = (JTextField) form.getClientProperty("cooler.note");
        double counted = ((Number) count.getValue()).doubleValue();
        double extraLoss = ((Number) loss.getValue()).doubleValue();
        HarvestGrade selected = (HarvestGrade) grade.getSelectedItem();
        return new CoolerShelfLife.ReviewAction(counted, selected, extraLoss, note.getText());
    }

    public JPanel buildIntervalForm(String cropSuggestion, int currentDays,
                                    CoolerShelfLife.Conditioning conditioning) {
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, FarmUi.CONTROL_GAP, FarmUi.CONTROL_GAP));
        form.setName("cooler.intervalForm");
        form.setOpaque(false);
        JTextField crop = new JTextField(cropSuggestion == null ? "" : cropSuggestion, 18);
        crop.setName("cooler.intervalCrop");
        JSpinner days = new JSpinner(new SpinnerNumberModel(
                currentDays, CoolerShelfLife.MIN_INTERVAL_DAYS, CoolerShelfLife.MAX_INTERVAL_DAYS, 1));
        days.setName("cooler.intervalDays");
        JComboBox<CoolerShelfLife.Conditioning> method =
                new JComboBox<>(CoolerShelfLife.Conditioning.values());
        method.setSelectedItem(conditioning == null
                ? CoolerShelfLife.Conditioning.FRESH_CUT : conditioning);
        method.setName("cooler.intervalMethod");
        form.add(new JLabel("Crop:"));
        form.add(crop);
        form.add(new JLabel("Review every (days):"));
        form.add(days);
        form.add(new JLabel("Conditioning:"));
        form.add(method);
        form.putClientProperty("cooler.intervalCrop", crop);
        form.putClientProperty("cooler.intervalDays", days);
        form.putClientProperty("cooler.intervalMethod", method);
        return form;
    }

    public IntervalChoice readIntervalForm(JPanel form) {
        JTextField crop = (JTextField) form.getClientProperty("cooler.intervalCrop");
        JSpinner days = (JSpinner) form.getClientProperty("cooler.intervalDays");
        @SuppressWarnings("unchecked")
        JComboBox<CoolerShelfLife.Conditioning> method =
                (JComboBox<CoolerShelfLife.Conditioning>) form.getClientProperty("cooler.intervalMethod");
        return new IntervalChoice(
                crop.getText(),
                ((Number) days.getValue()).intValue(),
                (CoolerShelfLife.Conditioning) method.getSelectedItem());
    }

    public void announce(String message) {
        feedback.setText(message);
        status.accept(message);
    }

    public record IntervalChoice(String crop, int days, CoolerShelfLife.Conditioning conditioning) {
    }

    private static final class LotTableModel extends AbstractTableModel {
        private final List<CoolerShelfLife.AgedLot> lots = new ArrayList<>();
        private final String[] columns = {
                "Status", "Lot", "Crop", "Harvested", "Storage", "Age",
                "Due", "Qty", "Grade", "Hint"
        };

        void setLots(List<CoolerShelfLife.AgedLot> next) {
            lots.clear();
            if (next != null) {
                lots.addAll(next);
            }
            fireTableDataChanged();
        }

        Optional<CoolerShelfLife.AgedLot> lotAt(int row) {
            if (row < 0 || row >= lots.size()) {
                return Optional.empty();
            }
            return Optional.of(lots.get(row));
        }

        @Override
        public int getRowCount() {
            return lots.size();
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
        public Object getValueAt(int rowIndex, int columnIndex) {
            CoolerShelfLife.AgedLot lot = lots.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> lot.status().name();
                case 1 -> lot.lotCode();
                case 2 -> lot.cropName();
                case 3 -> lot.harvestDate().toString();
                case 4 -> lot.storageLocation();
                case 5 -> lot.ageDays();
                case 6 -> lot.reviewDueDate().toString();
                case 7 -> trim(lot.marketableQuantity()) + " " + lot.unit();
                case 8 -> lot.grade().name();
                case 9 -> lot.packFirstHint();
                default -> "";
            };
        }

        private static String trim(double value) {
            if (Math.rint(value) == value) {
                return Integer.toString((int) value);
            }
            return Double.toString(value);
        }
    }
}
