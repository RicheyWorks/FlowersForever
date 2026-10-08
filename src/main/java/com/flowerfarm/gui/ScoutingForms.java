package com.flowerfarm.gui;

import com.flowerfarm.scouting.LabDirectory;
import com.flowerfarm.scouting.Scouting;
import com.flowerfarm.scouting.ScoutingService;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Entry forms for scouting findings and lab samples. Reading a form never saves anything. */
final class ScoutingForms {

    static final String NO_PLANTING = "(not linked to a planting)";
    static final String NO_FINDING = "(soil or water sample, no finding)";
    static final String OTHER_LAB = "Other lab (type the name below)";

    private ScoutingForms() {
    }

    /** Finding form. */
    static final class FindingForm {
        final JPanel panel = grid("scouting.findingForm");
        final JTextField date = text("scouting.finding.date", 10);
        final JComboBox<Object> planting = new JComboBox<>();
        final JTextField bed = text("scouting.finding.bed", 20);
        final JTextField crop = text("scouting.finding.crop", 20);
        final JTextField variety = text("scouting.finding.variety", 20);
        final JComboBox<Scouting.Category> category = new JComboBox<>(Scouting.Category.values());
        final JTextField organism = text("scouting.finding.organism", 28);
        final JTextArea symptoms = area("scouting.finding.symptoms");
        final JComboBox<Scouting.Severity> severity = new JComboBox<>(Scouting.Severity.values());
        final JTextField incidence = text("scouting.finding.incidence", 6);
        final JTextField photo = text("scouting.finding.photo", 28);
        final JTextArea action = area("scouting.finding.action");
        private final Scouting.Finding original;

        FindingForm(Scouting.Finding existing, List<ScoutingService.PlantingChoice> choices, LocalDate today) {
            this.original = existing;
            planting.setName("scouting.finding.planting");
            category.setName("scouting.finding.category");
            severity.setName("scouting.finding.severity");
            planting.addItem(NO_PLANTING);
            choices.forEach(planting::addItem);
            planting.addActionListener(event -> fillFromPlanting());
            date.setText(existing == null ? String.valueOf(today) : String.valueOf(existing.date()));
            if (existing != null) {
                choices.stream().filter(choice -> Objects.equals(choice.id(), existing.plantingId())).findFirst()
                        .ifPresent(planting::setSelectedItem);
                bed.setText(existing.bed());
                crop.setText(existing.crop());
                variety.setText(existing.variety());
                category.setSelectedItem(existing.category());
                organism.setText(existing.organism());
                symptoms.setText(existing.symptoms());
                severity.setSelectedItem(existing.severity());
                incidence.setText(existing.incidencePercent() == null ? ""
                        : String.valueOf(existing.incidencePercent()));
                photo.setText(existing.photoPath());
                action.setText(existing.action());
            } else {
                category.setSelectedItem(Scouting.Category.UNKNOWN);
            }
            row(panel, "Date scouted (YYYY-MM-DD)", date);
            row(panel, "Planting", planting);
            row(panel, "Bed or block", bed);
            row(panel, "Crop", crop);
            row(panel, "Variety", variety);
            row(panel, "Category", category);
            row(panel, "Suspected pest or disease", organism);
            row(panel, "Symptoms", new JScrollPane(symptoms));
            row(panel, "Severity", severity);
            row(panel, "Plants affected (%)", incidence);
            row(panel, "Photo file (optional)", photo);
            row(panel, "Action taken", new JScrollPane(action));
        }

        private void fillFromPlanting() {
            if (planting.getSelectedItem() instanceof ScoutingService.PlantingChoice choice) {
                bed.setText(Objects.requireNonNullElse(choice.bed(), ""));
                crop.setText(Objects.requireNonNullElse(choice.crop(), ""));
                variety.setText(Objects.requireNonNullElse(choice.variety(), ""));
            }
        }

        Scouting.Finding read() {
            Long plantingId = planting.getSelectedItem() instanceof ScoutingService.PlantingChoice choice
                    ? choice.id() : null;
            return new Scouting.Finding(original == null ? null : original.id(), date("Date scouted", date.getText()),
                    plantingId, bed.getText(), crop.getText(), variety.getText(),
                    (Scouting.Category) category.getSelectedItem(), organism.getText(), symptoms.getText(),
                    (Scouting.Severity) severity.getSelectedItem(), percent(incidence.getText()), photo.getText(),
                    action.getText(), original == null ? List.of() : original.photos());
        }
    }

    /** Lab sample form. */
    static final class SampleForm {
        final JPanel panel = grid("scouting.sampleForm");
        final JComboBox<Object> finding = new JComboBox<>();
        final JTextField soilBlock = text("scouting.sample.block", 20);
        final JComboBox<Scouting.SampleType> type = new JComboBox<>(Scouting.SampleType.values());
        final JComboBox<Object> lab = new JComboBox<>();
        final JTextField labName = text("scouting.sample.labName", 28);
        final JTextField collected = text("scouting.sample.collected", 10);
        final JTextField shipped = text("scouting.sample.shipped", 10);
        final JTextField tracking = text("scouting.sample.tracking", 24);
        final JComboBox<Scouting.SampleStatus> status = new JComboBox<>(Scouting.SampleStatus.values());
        final JTextField cost = text("scouting.sample.cost", 8);
        final JTextField resultsDate = text("scouting.sample.resultsDate", 10);
        final JTextArea result = area("scouting.sample.result");
        final JTextField report = text("scouting.sample.report", 28);
        final JLabel labHint = FarmUi.mutedLabel(" ");
        private final Scouting.LabSample original;
        private final LocalDate today;

        SampleForm(Scouting.LabSample existing, Scouting.Finding linkTo, List<Scouting.Finding> findings,
                   LocalDate today) {
            this.original = existing;
            this.today = today;
            finding.setName("scouting.sample.finding");
            type.setName("scouting.sample.type");
            lab.setName("scouting.sample.lab");
            status.setName("scouting.sample.status");
            labHint.setName("scouting.sample.labHint");
            finding.addItem(NO_FINDING);
            List<FindingChoice> findingChoices = new ArrayList<>();
            findings.forEach(item -> findingChoices.add(new FindingChoice(item)));
            findingChoices.forEach(finding::addItem);
            lab.addItem(OTHER_LAB);
            LabDirectory.all().forEach(item -> lab.addItem(new LabChoice(item)));
            lab.addActionListener(event -> showLabHint());
            type.addActionListener(event -> showLabHint());

            String linkedId = existing != null ? existing.findingId() : linkTo == null ? "" : linkTo.id();
            findingChoices.stream().filter(choice -> choice.finding.id().equals(linkedId)).findFirst()
                    .ifPresent(finding::setSelectedItem);
            if (existing == null) {
                collected.setText(String.valueOf(today));
                type.setSelectedItem(linkTo != null && linkTo.category() == Scouting.Category.INSECT_MITE
                        ? Scouting.SampleType.INSECT : Scouting.SampleType.PLANT_DISEASE);
                status.setSelectedItem(Scouting.SampleStatus.COLLECTED);
            } else {
                soilBlock.setText(existing.soilBlock());
                type.setSelectedItem(existing.type());
                collected.setText(String.valueOf(existing.collected()));
                shipped.setText(existing.shipped() == null ? "" : existing.shipped().toString());
                tracking.setText(existing.tracking());
                status.setSelectedItem(existing.status());
                cost.setText(existing.cost() == null ? "" : existing.cost().toPlainString());
                resultsDate.setText(existing.resultsDate() == null ? "" : existing.resultsDate().toString());
                result.setText(existing.resultSummary());
                report.setText(existing.reportPath());
                boolean known = false;
                for (int i = 0; i < lab.getItemCount(); i++) {
                    if (lab.getItemAt(i) instanceof LabChoice choice && choice.lab.id().equals(existing.labId())) {
                        lab.setSelectedIndex(i);
                        known = true;
                    }
                }
                if (!known) {
                    labName.setText(existing.labName());
                }
            }
            showLabHint();
            row(panel, "Scouting finding", finding);
            row(panel, "Soil block or water source", soilBlock);
            row(panel, "Sample type", type);
            row(panel, "Lab or clinic", lab);
            row(panel, "", labHint);
            row(panel, "Other lab name", labName);
            row(panel, "Collected (YYYY-MM-DD)", collected);
            row(panel, "Shipped or dropped off", shipped);
            row(panel, "Tracking number", tracking);
            row(panel, "Status", status);
            row(panel, "Cost ($)", cost);
            row(panel, "Results received", resultsDate);
            row(panel, "Result summary", new JScrollPane(result));
            row(panel, "Lab report file", report);
        }

        private void showLabHint() {
            Scouting.SampleType chosen = (Scouting.SampleType) type.getSelectedItem();
            if (lab.getSelectedItem() instanceof LabChoice choice) {
                boolean accepts = chosen == null || choice.lab.accepts().contains(chosen);
                labHint.setText("<html>" + LabelText.esc(choice.lab.fees())
                        + (accepts ? "" : "<br><b>This lab does not list " + chosen.label().toLowerCase(
                                java.util.Locale.ROOT) + " samples. Check with the lab first.</b>") + "</html>");
            } else {
                labHint.setText("Pick a lab from the list, or type another lab's name below.");
            }
        }

        Scouting.LabSample read() {
            Object selectedFinding = finding.getSelectedItem();
            String findingId = selectedFinding instanceof FindingChoice choice ? choice.finding.id() : "";
            String labId = "";
            String name = labName.getText();
            if (lab.getSelectedItem() instanceof LabChoice choice) {
                labId = choice.lab.id();
                name = choice.lab.name();
            }
            Scouting.LabSample read = new Scouting.LabSample(original == null ? null : original.id(), findingId,
                    soilBlock.getText(), (Scouting.SampleType) type.getSelectedItem(), labId, name,
                    date("Collected", collected.getText()), date("Shipped", shipped.getText()), tracking.getText(),
                    (Scouting.SampleStatus) status.getSelectedItem(), money(cost.getText()), result.getText(),
                    report.getText(), date("Results received", resultsDate.getText()));
            if (read.status() == Scouting.SampleStatus.RESULTS_IN && read.resultsDate() == null) {
                return new Scouting.LabSample(read.id(), read.findingId(), read.soilBlock(), read.type(),
                        read.labId(), read.labName(), read.collected(), read.shipped(), read.tracking(),
                        read.status(), read.cost(), read.resultSummary(), read.reportPath(), today);
            }
            return read;
        }
    }

    private record FindingChoice(Scouting.Finding finding) {
        @Override
        public String toString() {
            return finding.id() + " · " + finding.label();
        }
    }

    private record LabChoice(LabDirectory.Lab lab) {
        @Override
        public String toString() {
            return lab.name();
        }
    }

    /** HTML escaping for Swing labels. */
    static final class LabelText {
        private LabelText() {
        }

        static String esc(String text) {
            return Objects.requireNonNullElse(text, "").replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
    }

    static LocalDate date(String field, String text) {
        String value = Objects.requireNonNullElse(text, "").strip();
        if (value.isEmpty() || "null".equals(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException bad) {
            throw new IllegalArgumentException(field + ": use a date like 2026-10-07.", bad);
        }
    }

    static Double percent(String text) {
        String value = Objects.requireNonNullElse(text, "").strip().replace("%", "");
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("Plants affected: enter a number from 0 to 100.", bad);
        }
    }

    static BigDecimal money(String text) {
        String value = Objects.requireNonNullElse(text, "").strip().replace("$", "").replace(",", "");
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("Cost: enter an amount like 40 or 40.00.", bad);
        }
    }

    private static JPanel grid(String name) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setName(name);
        panel.setOpaque(false);
        return panel;
    }

    private static JTextField text(String name, int columns) {
        JTextField field = new JTextField(columns);
        field.setName(name);
        return field;
    }

    private static JTextArea area(String name) {
        JTextArea area = new JTextArea(3, 28);
        area.setName(name);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static void row(JPanel panel, String label, JComponent field) {
        int y = panel.getComponentCount() / 2;
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = y;
        left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(3, 0, 3, FarmUi.CONTROL_GAP);
        JLabel caption = new JLabel(label);
        caption.setLabelFor(field);
        panel.add(caption, left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = y;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(3, 0, 3, 0);
        if (!label.isEmpty()) {
            field.getAccessibleContext().setAccessibleName(label);
        }
        panel.add(field, right);
    }
}
