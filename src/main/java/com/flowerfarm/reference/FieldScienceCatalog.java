package com.flowerfarm.reference;

import com.flowerfarm.model.FarmWorkspace;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Curated CSRBT field instruments, shown only where their subject fits the farm. */
public final class FieldScienceCatalog {

    private static final String SOURCE_ROOT =
            "https://github.com/RicheyWorks/CSRBT/blob/"
                    + FieldScienceBundle.SOURCE_COMMIT + "/docs/";

    private static final Set<FarmWorkspace> TERRESTRIAL_GROWERS = Set.copyOf(EnumSet.of(
            FarmWorkspace.ROSE_AND_EVENTS, FarmWorkspace.WILDFLOWER_FARM,
            FarmWorkspace.CANNABIS_FARM, FarmWorkspace.POTATO_FARM,
            FarmWorkspace.APPLE_ORCHARD, FarmWorkspace.BERRY_FARM,
            FarmWorkspace.NURSERY_FARM, FarmWorkspace.VINEYARD_FARM,
            FarmWorkspace.HAY_FARM, FarmWorkspace.HOPS_FARM,
            FarmWorkspace.GRAIN_FARM, FarmWorkspace.SEED_FARM,
            FarmWorkspace.NUT_ORCHARD, FarmWorkspace.VEGETABLE_FARM,
            FarmWorkspace.CHRISTMAS_TREE_FARM, FarmWorkspace.CRANBERRY_FARM,
            FarmWorkspace.PUMPKIN_PATCH));

    private static final List<Instrument> INSTRUMENTS = List.of(
            tool("soil-recipes", "Soil recipes · Subcool’s Super Soil",
                    "soil-recipes.html", "Soil & growing media",
                    "Subcool’s Super Soil, The Rev’s TLO, Coot’s, and BuildASoil recipes "
                            + "with source-specific batch scaling.",
                    "Grower recipes, not a soil test or a nutrient recommendation. "
                            + "Check the named original source and your own inputs.",
                    "CSRBT Soil Recipes · Subcool / High Times source credited in the tool",
                    Set.of(FarmWorkspace.CANNABIS_FARM)),
            tool("soil-bench", "Soil Bench", "soil-bench.html",
                    "Soil & growing media",
                    "Record compost conditions, work with dry-mass carbon and nitrogen, "
                            + "design a media mix, and use the soil texture key.",
                    "Enter measurements from your own site. A mix rank or compost chart "
                            + "does not certify safety or suitability.",
                    "CSRBT Soil Bench", TERRESTRIAL_GROWERS),
            tool("farm-scout", "Farm Scout", "farm-scout.html",
                    "Field observation",
                    "Record pest counts, pollinator visits, germination, and crop-family rotation.",
                    "Action thresholds are supplied by the grower. This tool does not "
                            + "recommend a pesticide or authorize a treatment.",
                    "CSRBT Farm Scout", TERRESTRIAL_GROWERS),
            tool("greenhouse", "Greenhouse Monitor", "greenhouse.html",
                    "Controlled environments",
                    "Review temperature, humidity, light, and energy against the "
                            + "conditions and equipment values you enter.",
                    "Use measured conditions and your own targets; this is not "
                            + "an automated climate controller.",
                    "CSRBT Greenhouse Monitor",
                    Set.of(FarmWorkspace.CANNABIS_FARM, FarmWorkspace.NURSERY_FARM)),
            tool("breeding-bench", "Breeding Bench", "breeding-bench.html",
                    "Selection & breeding",
                    "Explore population size, isolation, trials, and germination uncertainty.",
                    "Generic trial and selection analysis. Preset crop and isolation "
                            + "references are not cannabis-specific; check crop sources "
                            + "before setting a production protocol.",
                    "CSRBT Breeding Bench",
                    Set.of(FarmWorkspace.SEED_FARM, FarmWorkspace.VEGETABLE_FARM,
                            FarmWorkspace.CANNABIS_FARM)),
            tool("pheno-tracker", "Pheno Tracker", "pheno-tracker.html",
                    "Selection & breeding",
                    "Record plant traits, compare selections, and inspect segregation patterns.",
                    "An observational selection aid, not a cultivar identity or yield guarantee.",
                    "CSRBT Pheno Tracker",
                    Set.of(FarmWorkspace.CANNABIS_FARM, FarmWorkspace.SEED_FARM,
                            FarmWorkspace.VEGETABLE_FARM, FarmWorkspace.APPLE_ORCHARD,
                            FarmWorkspace.BERRY_FARM, FarmWorkspace.VINEYARD_FARM,
                            FarmWorkspace.HOPS_FARM)),
            tool("releve", "Relevé", "releve.html",
                    "Wild plants & specimens",
                    "Survey plant cover and keep plot and voucher observations.",
                    "A vegetation survey, not a crop production or harvest record.",
                    "CSRBT Relevé", Set.of(FarmWorkspace.WILDFLOWER_FARM)),
            tool("collection-sheet", "Collection Sheet", "collection-sheet.html",
                    "Wild plants & specimens",
                    "Record wild fungal specimen characters, substrate, host, and vouchers.",
                    "Wild specimen study only. It does not identify edible mushrooms "
                            + "or replace cultivated batch records.",
                    "CSRBT Collection Sheet", Set.of(FarmWorkspace.MUSHROOM_FARM)),
            tool("fungal-characters", "Fungal Characters", "fungal-characters.html",
                    "Wild plants & specimens",
                    "Consult illustrated fungal morphology and spore-print vocabulary.",
                    "Identification vocabulary only; no edibility or food-safety verdict.",
                    "CSRBT Fungal Characters", Set.of(FarmWorkspace.MUSHROOM_FARM)),
            tool("ethogram", "Ethogram", "ethogram.html",
                    "Animal behavior",
                    "Observe timed behavior states and counted events using a declared "
                            + "sampling method.",
                    "Field behavior observation, separate from herd, flock, milk, "
                            + "and animal-health records.",
                    "CSRBT Ethogram",
                    Set.of(FarmWorkspace.LIVESTOCK_FARM, FarmWorkspace.POULTRY_FARM,
                            FarmWorkspace.DAIRY_FARM)),
            tool("cp-bench", "CP Bench · carnivorous plants", "cp-bench.html",
                    "Specialist nursery",
                    "Explore carnivorous-plant water, media, dormancy, and propagation logs.",
                    "Specialist reference only; nursery growers choose whether it fits "
                            + "their stock and verify their own measurements.",
                    "CSRBT CP Bench", Set.of(FarmWorkspace.NURSERY_FARM)),
            tool("food-web", "Food Web Builder", "food-web.html",
                    "Aquatic ecology",
                    "Explore feeding links and trophic relationships in an aquatic ecosystem.",
                    "Ecology study only; it does not measure production water quality "
                            + "or manage aquatic cohorts.",
                    "CSRBT Food Web Builder", Set.of(FarmWorkspace.AQUACULTURE_FARM))
    );

    private FieldScienceCatalog() { }

    /** Returns each matching instrument once, in stable display order. */
    public static List<Instrument> forWorkspaces(Set<FarmWorkspace> enabledWorkspaces) {
        Objects.requireNonNull(enabledWorkspaces, "enabledWorkspaces");
        if (enabledWorkspaces.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Farm workspaces cannot contain null.");
        }
        return INSTRUMENTS.stream()
                .filter(tool -> enabledWorkspaces.stream().anyMatch(tool.workspaces()::contains))
                .toList();
    }

    public static boolean hasTools(Set<FarmWorkspace> enabledWorkspaces) {
        return !forWorkspaces(enabledWorkspaces).isEmpty();
    }

    /** Resolve only a curated instrument; imported exports cannot claim an arbitrary source. */
    public static Optional<Instrument> byId(String id) {
        return INSTRUMENTS.stream()
                .filter(instrument -> instrument.id().equals(id))
                .findFirst();
    }

    private static Instrument tool(String id, String title, String fileName,
                                   String section, String description, String scopeNote,
                                   String sourceLabel, Set<FarmWorkspace> workspaces) {
        return new Instrument(id, title, fileName, section, description, scopeNote,
                sourceLabel, SOURCE_ROOT + fileName, workspaces);
    }

    public record Instrument(String id, String title, String fileName, String section,
                             String description, String scopeNote, String sourceLabel,
                             String sourceUrl, Set<FarmWorkspace> workspaces) {
        public Instrument {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(section, "section");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(scopeNote, "scopeNote");
            Objects.requireNonNull(sourceLabel, "sourceLabel");
            Objects.requireNonNull(sourceUrl, "sourceUrl");
            workspaces = Set.copyOf(Objects.requireNonNull(workspaces, "workspaces"));
            if (workspaces.isEmpty()) {
                throw new IllegalArgumentException("Select at least one farm workspace.");
            }
        }
    }
}
