package com.flowerfarm.reference;

import com.flowerfarm.model.PnwPlantProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pacific Northwest plant catalog")
class PnwPlantCatalogTest {

    @Test
    @DisplayName("ships broad regional coverage with complete growing fields")
    void shipsBroadCompleteCoverage() {
        assertThat(PnwPlantCatalog.all()).hasSizeGreaterThanOrEqualTo(90);
        assertThat(PnwPlantCatalog.types()).contains(
                "Native tree", "Native shrub", "Native perennial", "Native fern",
                "Fruit & berry", "Cool-season vegetable", "Warm-season vegetable",
                "Cut flower", "Bulb & corm", "Herb");
        assertThat(PnwPlantCatalog.all())
                .filteredOn(PnwPlantProfile::nativeToPacificNorthwest)
                .hasSizeGreaterThanOrEqualTo(35);
        assertThat(PnwPlantCatalog.all()).allSatisfy(plant -> {
            assertThat(plant.commonName()).isNotBlank();
            assertThat(plant.scientificName()).isNotBlank();
            assertThat(plant.light()).isNotBlank();
            assertThat(plant.moisture()).isNotBlank();
            assertThat(plant.season()).isNotBlank();
            assertThat(plant.uses()).isNotBlank();
            assertThat(plant.fieldNotes()).isNotBlank();
            assertThat(plant.source().url()).startsWith("https://");
        });
    }

    @Test
    @DisplayName("retains public provenance from Washington and Oregon institutions")
    void retainsRegionalPublicProvenance() {
        assertThat(PnwPlantCatalog.sources()).hasSizeGreaterThanOrEqualTo(15);
        assertThat(PnwPlantCatalog.sources())
                .extracting(PnwPlantProfile.Source::organization)
                .anyMatch(name -> name.contains("Washington State University"))
                .anyMatch(name -> name.contains("WSU") && name.contains("Master Gardeners"))
                .anyMatch(name -> name.contains("Oregon State University"))
                .anyMatch(name -> name.contains("University of Washington"))
                .anyMatch(name -> name.contains("Evergreen State College"));
        assertThat(new HashSet<>(PnwPlantCatalog.sources()))
                .hasSameSizeAs(PnwPlantCatalog.sources());
    }

    @Test
    @DisplayName("search text includes field uses, notes, and source provenance")
    void searchableTextIncludesDecisionFields() {
        PnwPlantProfile dahlia = PnwPlantCatalog.all().stream()
                .filter(plant -> "Dahlia".equals(plant.commonName()))
                .findFirst().orElseThrow();

        assertThat(dahlia.searchableText()).contains(
                "tuber production", "winter tuber storage", "oregon state university");
    }
}
