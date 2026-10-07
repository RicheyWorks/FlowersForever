package com.flowerfarm.reference;

import com.flowerfarm.model.FarmWorkspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Farm-filtered CSRBT field science catalog")
class FieldScienceCatalogTest {

    @Test
    void cannabisGetsSubCoolAndOnlyItsRelevantBenchTools() {
        assertThat(ids(Set.of(FarmWorkspace.CANNABIS_FARM))).containsExactly(
                "soil-recipes", "soil-bench", "farm-scout", "greenhouse",
                "breeding-bench", "pheno-tracker");
        FieldScienceCatalog.Instrument recipes = FieldScienceCatalog
                .forWorkspaces(Set.of(FarmWorkspace.CANNABIS_FARM)).get(0);
        assertThat(recipes.title()).contains("Subcool’s Super Soil");
        assertThat(recipes.description()).contains("The Rev", "Coot", "BuildASoil");
        assertThat(recipes.scopeNote()).contains("not a soil test");
        assertThat(recipes.sourceLabel()).contains("CSRBT", "Subcool");
    }

    @Test
    void combinedFarmsGetOneCardPerInstrumentAndDoNotLeakSubCool() {
        assertThat(ids(Set.of(FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD)))
                .containsExactly("soil-bench", "farm-scout", "pheno-tracker");
        assertThat(ids(Set.of(FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD)))
                .containsExactly("soil-recipes", "soil-bench", "farm-scout",
                        "greenhouse", "breeding-bench", "pheno-tracker");
        for (FarmWorkspace workspace : FarmWorkspace.values()) {
            if (workspace != FarmWorkspace.CANNABIS_FARM) {
                assertThat(ids(Set.of(workspace))).doesNotContain("soil-recipes");
            }
        }
    }

    @Test
    void specialtyToolsStayInTheirOwnSubjectAreas() {
        assertThat(ids(Set.of(FarmWorkspace.MUSHROOM_FARM)))
                .containsExactly("collection-sheet", "fungal-characters");
        assertThat(FieldScienceCatalog.forWorkspaces(Set.of(FarmWorkspace.MUSHROOM_FARM)))
                .allSatisfy(tool -> assertThat(tool.scopeNote())
                        .containsAnyOf("edible", "edibility"));
        assertThat(ids(Set.of(FarmWorkspace.LIVESTOCK_FARM,
                FarmWorkspace.POULTRY_FARM, FarmWorkspace.DAIRY_FARM)))
                .containsExactly("ethogram");
        assertThat(ids(Set.of(FarmWorkspace.AQUACULTURE_FARM)))
                .containsExactly("food-web");
        assertThat(ids(Set.of(FarmWorkspace.WILDFLOWER_FARM)))
                .containsExactly("soil-bench", "farm-scout", "releve");
        assertThat(ids(Set.of(FarmWorkspace.NURSERY_FARM)))
                .containsExactly("soil-bench", "farm-scout", "greenhouse", "cp-bench");
        assertThat(ids(Set.of(FarmWorkspace.SEED_FARM)))
                .containsExactly("soil-bench", "farm-scout", "breeding-bench", "pheno-tracker");
    }

    @Test
    void retailAndCustomProfilesHaveNoDefaultToolsAndAllCardsAreSourced() {
        assertThat(FieldScienceCatalog.hasTools(Set.of(FarmWorkspace.FLOWER_SHOP))).isFalse();
        assertThat(FieldScienceCatalog.hasTools(Set.of(FarmWorkspace.CUSTOM_FARM))).isFalse();
        assertThat(FieldScienceCatalog.forWorkspaces(Set.of())).isEmpty();
        List<FieldScienceCatalog.Instrument> all = FieldScienceCatalog
                .forWorkspaces(EnumSet.allOf(FarmWorkspace.class));
        assertThat(all).hasSize(12);
        assertThat(all).extracting(FieldScienceCatalog.Instrument::id).doesNotHaveDuplicates();
        assertThat(all).allSatisfy(tool -> {
            assertThat(tool.title()).isNotBlank();
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.scopeNote()).isNotBlank();
            assertThat(tool.fileName()).endsWith(".html");
            assertThat(tool.sourceLabel()).startsWith("CSRBT");
            assertThat(tool.sourceUrl()).startsWith("https://github.com/RicheyWorks/CSRBT/blob/"
                            + FieldScienceBundle.SOURCE_COMMIT + "/docs/")
                    .endsWith("/docs/" + tool.fileName());
            assertThat(tool.workspaces()).doesNotContain(
                    FarmWorkspace.FLOWER_SHOP, FarmWorkspace.CUSTOM_FARM);
        });
        assertThatThrownBy(() -> FieldScienceCatalog.forWorkspaces(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static List<String> ids(Set<FarmWorkspace> workspaces) {
        return FieldScienceCatalog.forWorkspaces(workspaces).stream()
                .map(FieldScienceCatalog.Instrument::id).toList();
    }
}
