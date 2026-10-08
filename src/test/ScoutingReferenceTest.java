package com.flowerfarm.scouting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pest reference, lab directory, and sample guides")
class ScoutingReferenceTest {

    @Test
    void pestReferenceEntriesAreCompleteAndCiteExtensionSources() {
        List<PestReference.Entry> entries = PestReference.all();
        assertThat(entries).hasSizeGreaterThanOrEqualTo(10);
        Set<String> ids = new HashSet<>();
        for (PestReference.Entry entry : entries) {
            assertThat(ids.add(entry.id())).as("unique id %s", entry.id()).isTrue();
            assertThat(entry.name()).isNotBlank();
            assertThat(entry.lookFor()).as(entry.id()).isNotBlank();
            assertThat(entry.scoutingTips()).as(entry.id()).isNotBlank();
            assertThat(entry.sourceTitle()).as(entry.id()).isNotBlank();
            assertThat(entry.sourceUrl()).as(entry.id())
                    .matches("https://(pnwhandbooks\\.org|hortsense\\.cahnrs\\.wsu\\.edu)/.+");
        }
        assertThat(entries).extracting(PestReference.Entry::category)
                .contains(Scouting.Category.INSECT_MITE, Scouting.Category.FUNGAL, Scouting.Category.VIRAL);
        assertThat(PestReference.byId("botrytis")).isPresent();
        assertThat(PestReference.byId("nope")).isEmpty();
        assertThat(PestReference.additionalSources()).allMatch(url -> url.startsWith("https://"));
    }

    @Test
    void everyLabHasContactsLinksAndACheckedDate() {
        assertThat(LabDirectory.CHECKED).isEqualTo(LocalDate.of(2026, 10, 7));
        Set<String> ids = new HashSet<>();
        for (LabDirectory.Lab lab : LabDirectory.all()) {
            assertThat(ids.add(lab.id())).as("unique id %s", lab.id()).isTrue();
            assertThat(lab.name()).isNotBlank();
            assertThat(lab.accepts()).as(lab.id()).isNotEmpty();
            assertThat(lab.whatTheyDo()).as(lab.id()).isNotBlank();
            assertThat(lab.address()).as(lab.id()).isNotBlank();
            assertThat(lab.fees()).as(lab.id()).isNotBlank();
            assertThat(lab.infoUrl()).as(lab.id()).startsWith("https://");
            assertThat(lab.formUrl()).as(lab.id()).satisfiesAnyOf(
                    url -> assertThat(url).startsWith("https://"),
                    url -> assertThat(url).containsIgnoringCase("check with"));
        }
        assertThat(ids).contains("wsu-puyallup", "wsda-entomology", "soiltest", "al-western", "pierce-mg",
                "kitsap-mg", "ui-asl", "wml-tacoma", "spectra-kitsap");
    }

    @Test
    void puyallupClinicListsPublishedFeesAndNoAlcoholByMail() {
        LabDirectory.Lab clinic = LabDirectory.byId("wsu-puyallup").orElseThrow();
        assertThat(clinic.accepts()).contains(Scouting.SampleType.PLANT_DISEASE, Scouting.SampleType.INSECT);
        assertThat(clinic.fees()).contains("$40").contains("$25");
        assertThat(clinic.address()).contains("2606 W. Pioneer").contains("Puyallup");
        assertThat(clinic.formUrl()).contains("wpcdn.web.wsu.edu");
    }

    @Test
    void unverifiedDetailsAreMarkedCheckWithLab() {
        assertThat(LabDirectory.byId("al-western").orElseThrow().fees()).containsIgnoringCase("check with");
        assertThat(LabDirectory.byId("wml-tacoma").orElseThrow().fees()).containsIgnoringCase("check with");
        assertThat(LabDirectory.byId("spectra-kitsap").orElseThrow().formUrl()).containsIgnoringCase("check with");
    }

    @Test
    void wsdaMailingAddressIsConfirmed() {
        LabDirectory.Lab wsda = LabDirectory.byId("wsda-entomology").orElseThrow();
        assertThat(wsda.address()).contains("P.O. Box 42560").contains("98504-2560")
                .doesNotContainIgnoringCase("check with");
        assertThat(wsda.phone()).contains("1-800-443-6684");
    }

    @Test
    void universityOfIdahoTakesSoilAndWaterWithPublishedPrices() {
        LabDirectory.Lab idaho = LabDirectory.byId("ui-asl").orElseThrow();
        assertThat(idaho.accepts()).containsExactlyInAnyOrder(Scouting.SampleType.SOIL, Scouting.SampleType.WATER);
        assertThat(idaho.fees()).contains("$35").contains("$7.50");
        assertThat(idaho.address()).contains("875 Perimeter Drive").contains("Moscow, ID");
        assertThat(idaho.notes()).contains("before sending water");
    }

    @Test
    void waterLabsCoverIrrigationAndWellWater() {
        assertThat(LabDirectory.accepting(Scouting.SampleType.WATER)).extracting(LabDirectory.Lab::id)
                .contains("soiltest", "al-western", "ui-asl", "wml-tacoma", "spectra-kitsap");
        assertThat(LabDirectory.byId("soiltest").orElseThrow().fees()).contains("$77");
    }

    @Test
    void everySampleTypeHasAtLeastOneLab() {
        for (Scouting.SampleType type : EnumSet.allOf(Scouting.SampleType.class)) {
            assertThat(LabDirectory.accepting(type)).as(type.label()).isNotEmpty();
            assertThat(LabDirectory.accepting(type)).allMatch(lab -> lab.accepts().contains(type));
        }
    }

    @Test
    void guidesCiteSourcesAndCarryTheKeyRules() {
        assertThat(SampleGuides.all()).extracting(SampleGuides.Guide::id)
                .contains("plant-disease", "tape-lift", "insect", "soil", "water");
        for (SampleGuides.Guide guide : SampleGuides.all()) {
            assertThat(guide.steps()).as(guide.id()).isNotEmpty();
            assertThat(guide.sources()).as(guide.id()).isNotEmpty().allMatch(url -> url.startsWith("https://"));
            assertThat(guide.asText()).contains(guide.title());
        }
        String insect = SampleGuides.byId("insect").orElseThrow().asText().toLowerCase();
        assertThat(insect).contains("freez").contains("alcohol");
        String tape = SampleGuides.byId("tape-lift").orElseThrow().asText().toLowerCase();
        assertThat(tape).contains("ask");
        String plant = SampleGuides.byId("plant-disease").orElseThrow().asText().toLowerCase();
        assertThat(plant).contains("dig").contains("refrigerator");
        assertThat(SampleGuides.forType(Scouting.SampleType.INSECT).id()).isEqualTo("insect");
        assertThat(SampleGuides.forType(Scouting.SampleType.SOIL).id()).isEqualTo("soil");
        assertThat(SampleGuides.forType(Scouting.SampleType.WATER).id()).isEqualTo("water");
        String water = SampleGuides.byId("water").orElseThrow().asText().toLowerCase();
        assertThat(water).contains("do not rinse").contains("coliform").contains("24-36 hours")
                .contains("don't freeze");
    }
}
