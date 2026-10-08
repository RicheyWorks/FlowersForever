package com.flowerfarm.gui;

import com.flowerfarm.scouting.LabDirectory;
import com.flowerfarm.scouting.PestReference;
import com.flowerfarm.scouting.Scouting;
import com.flowerfarm.scouting.ScoutingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Scouting forms and panel")
class ScoutingFormsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    @Test
    void parsesDatesPercentsAndMoneyWithPlainErrors() {
        assertThat(ScoutingForms.date("Collected", " 2026-10-01 ")).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(ScoutingForms.date("Collected", "")).isNull();
        assertThatThrownBy(() -> ScoutingForms.date("Collected", "10/1/26"))
                .hasMessage("Collected: use a date like 2026-10-07.");
        assertThat(ScoutingForms.percent("15%")).isEqualTo(15.0);
        assertThat(ScoutingForms.percent(" ")).isNull();
        assertThatThrownBy(() -> ScoutingForms.percent("lots")).hasMessageContaining("0 to 100");
        assertThat(ScoutingForms.money("$1,040.50")).isEqualByComparingTo(new BigDecimal("1040.50"));
        assertThat(ScoutingForms.money("")).isNull();
        assertThatThrownBy(() -> ScoutingForms.money("forty")).hasMessageContaining("40.00");
    }

    @Test
    void findingFormFillsFromPlantingAndReadsBack() {
        ScoutingService.PlantingChoice dahlias = new ScoutingService.PlantingChoice(41L, "Dahlia", "Café au Lait",
                "Bed 4", 2026);
        ScoutingForms.FindingForm form = new ScoutingForms.FindingForm(null, List.of(dahlias), TODAY);
        assertThat(form.date.getText()).isEqualTo("2026-10-07");
        form.planting.setSelectedItem(dahlias);
        assertThat(form.bed.getText()).isEqualTo("Bed 4");
        form.category.setSelectedItem(Scouting.Category.INSECT_MITE);
        form.organism.setText("Thrips");
        form.incidence.setText("20");
        Scouting.Finding read = form.read();
        assertThat(read.id()).isNull();
        assertThat(read.plantingId()).isEqualTo(41L);
        assertThat(read.crop()).isEqualTo("Dahlia");
        assertThat(read.variety()).isEqualTo("Café au Lait");
        assertThat(read.incidencePercent()).isEqualTo(20.0);
        assertThat(Scouting.problems(read, TODAY)).isEmpty();

        ScoutingForms.FindingForm edit = new ScoutingForms.FindingForm(read.withId("F-1"), List.of(dahlias), TODAY);
        assertThat(edit.read()).isEqualTo(read.withId("F-1"));
    }

    @Test
    void sampleFormLinksTheSelectedFindingAndLab() {
        Scouting.Finding thrips = new Scouting.Finding("F-1", TODAY, null, "Bed 4", "Dahlia", "",
                Scouting.Category.INSECT_MITE, "Thrips", "", null, null, "", "");
        ScoutingForms.SampleForm form = new ScoutingForms.SampleForm(null, thrips, List.of(thrips), TODAY);
        assertThat(form.type.getSelectedItem()).isEqualTo(Scouting.SampleType.INSECT);
        for (int i = 0; i < form.lab.getItemCount(); i++) {
            if (form.lab.getItemAt(i).toString().equals(LabDirectory.byId("wsu-puyallup").orElseThrow().name())) {
                form.lab.setSelectedIndex(i);
            }
        }
        assertThat(form.labHint.getText()).contains("$25");
        form.status.setSelectedItem(Scouting.SampleStatus.RESULTS_IN);
        form.collected.setText("2026-10-01");
        form.shipped.setText("2026-10-02");
        form.result.setText("Western flower thrips");
        Scouting.LabSample read = form.read();
        assertThat(read.findingId()).isEqualTo("F-1");
        assertThat(read.labId()).isEqualTo("wsu-puyallup");
        assertThat(read.resultsDate()).as("results date defaults to today").isEqualTo(TODAY);
        assertThat(Scouting.problems(read, TODAY)).isEmpty();

        form.type.setSelectedItem(Scouting.SampleType.SOIL);
        assertThat(form.labHint.getText()).contains("Check with the lab first");
    }

    @Test
    void panelShowsBoardAndReferenceText() {
        List<String> statuses = new ArrayList<>();
        ScoutingPanel panel = new ScoutingPanel(statuses::add);
        Scouting.Finding finding = new Scouting.Finding("F-1", TODAY, null, "Bed 4", "Dahlia", "", null,
                "Botrytis?", "", null, 10.0, "", "");
        Scouting.LabSample late = new Scouting.LabSample("S-1", "F-1", "", Scouting.SampleType.PLANT_DISEASE,
                "wsu-puyallup", "WSU", TODAY.minusDays(30), TODAY.minusDays(29), "", Scouting.SampleStatus.SHIPPED,
                null, "", "", null);
        panel.showBoard(new ScoutingService.Board(List.of(finding), List.of(late), 14, 1, TODAY));
        assertThat(panel.selectedFinding()).isEmpty();
        assertThat(panel.selectedSample()).isEmpty();
        panel.announce("Saved finding F-1.");
        assertThat(statuses).containsExactly("Saved finding F-1.");
        assertThat(ScoutingPanel.labText(LabDirectory.byId("soiltest").orElseThrow()))
                .contains("Moses Lake").contains("Checked 2026-10-07");
        assertThat(ScoutingPanel.referenceText(PestReference.byId("thrips").orElseThrow()))
                .contains("Source:").contains("https://");
    }
}
