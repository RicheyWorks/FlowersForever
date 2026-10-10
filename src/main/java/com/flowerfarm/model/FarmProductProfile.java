package com.flowerfarm.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Editable catalog suggestions. These examples never create farm records by themselves. */
public record FarmProductProfile(String category, List<String> units, List<String> examples) {
    public FarmProductProfile {
        units = List.copyOf(units);
        examples = List.copyOf(examples);
    }

    public String defaultUnit() { return units.get(0); }
    public String nameHint() { return "e.g. " + examples.get(0); }
    public String quantityHint() {
        return "Use the named product and its selling unit. Weights and volumes accept decimals; "
                + "plants, packages, and other counts require whole quantities.";
    }

    public static FarmProductProfile forWorkspace(FarmWorkspace workspace) {
        return switch (workspace) {
            case ROSE_AND_EVENTS -> profile("Flowers/Plants", "Per Stem,bunches,bouquets,buckets,each",
                    "Nootka Rose,Café au Lait Dahlia,Sarah Bernhardt Peony");
            case WILDFLOWER_FARM -> profile("Flowers/Plants", "stems,bunches,bouquets,buckets,each",
                    "Yarrow,Black-eyed Susan,Wildflower Bouquet");
            case FLOWER_SHOP -> profile("Flowers/Plants", "bouquets,stems,bunches,vases,each",
                    "Seasonal Bouquet,Red Rose,Orchid Arrangement");
            case CANNABIS_FARM -> profile("Cannabis", "grams,kg,oz,lbs,plants,clones,each",
                    "Blue Dream flower,OG Kush flower,Northern Lights flower");
            case POTATO_FARM -> profile("Potatoes", "lbs,kg,cwt,tons,bags,each",
                    "Russet Burbank,Yukon Gold,Red Norland");
            case APPLE_ORCHARD -> profile("Apples", "lbs,kg,bushels,boxes,each",
                    "Honeycrisp,Gala,Granny Smith");
            case BERRY_FARM -> profile("Berries", "lbs,kg,flats,boxes,each",
                    "Bluecrop Blueberry,Chandler Strawberry,Heritage Raspberry");
            case NURSERY_FARM -> profile("Live plants", "plants,pots,trays,flats,each",
                    "Japanese Maple,Basil Starts,Lavender Plant");
            case LIVESTOCK_FARM -> profile("Livestock", "head,each,lbs,kg",
                    "Angus Cattle,Sheep,Goats");
            case POULTRY_FARM -> profile("Poultry and eggs", "dozen,eggs,each,birds,lbs,kg",
                    "Large Chicken Eggs,Duck Eggs,Pasture-raised Chicken");
            case DAIRY_FARM -> profile("Dairy", "gallons,liters,lbs,kg,bottles,each",
                    "Whole Cow Milk,Goat Milk,Cream");
            case VINEYARD_FARM -> profile("Grapes", "lbs,kg,tons,crates,each",
                    "Pinot Noir Grapes,Chardonnay Grapes,Concord Grapes");
            case HAY_FARM -> profile("Hay and forage", "bales,tons,lbs,kg",
                    "Timothy Hay,Alfalfa Hay,Orchardgrass Hay");
            case HOPS_FARM -> profile("Hops", "lbs,kg,grams,bags",
                    "Cascade Hops,Citra Hops,Centennial Hops");
            case GRAIN_FARM -> profile("Grain", "bushels,lbs,kg,tons,bags",
                    "Hard Red Wheat,Oats,Barley");
            case SEED_FARM -> profile("Seeds", "grams,kg,lbs,packets,bags,each",
                    "Clover Seed,Lettuce Seed,Sunflower Seed");
            case NUT_ORCHARD -> profile("Nuts", "lbs,kg,tons,bags,each",
                    "Hazelnuts,Walnuts,Almonds");
            case VEGETABLE_FARM -> profile("Vegetables", "lbs,kg,bunches,boxes,each",
                    "Carrots,Heirloom Tomatoes,Lacinato Kale");
            case AQUACULTURE_FARM -> profile("Aquatic products", "lbs,kg,fish,each,dozen",
                    "Rainbow Trout,Pacific Oysters,Clams");
            case CHRISTMAS_TREE_FARM -> profile("Christmas trees", "trees,each",
                    "Noble Fir,Douglas Fir,Fraser Fir");
            case APIARY_FARM -> profile("Apiary products", "lbs,kg,grams,jars,colonies,each",
                    "Wildflower Honey,Clover Honey,Beeswax");
            case MUSHROOM_FARM -> profile("Mushrooms", "lbs,kg,grams,boxes,each",
                    "Blue Oyster Mushrooms,Shiitake Mushrooms,Lion's Mane Mushrooms");
            case CRANBERRY_FARM -> profile("Cranberries", "lbs,kg,tons,bags,boxes",
                    "Stevens Cranberries,Pilgrim Cranberries,Early Black Cranberries");
            case PUMPKIN_PATCH -> profile("Pumpkins", "each,lbs,kg,bins",
                    "Howden Pumpkin,Sugar Pie Pumpkin,Cinderella Pumpkin");
            case CUSTOM_FARM -> profile("Farm products", "each,lbs,kg,grams,liters,gallons,plants,boxes",
                    "Your named product,Your crop variety,Your packaged product");
        };
    }

    /** Primary farm first, then other enabled farms; never add unrelated flower units. */
    public static FarmProductProfile combined(FarmWorkspace primary, Set<FarmWorkspace> enabled) {
        if (!enabled.contains(primary)) throw new IllegalArgumentException("Primary farm must be enabled.");
        FarmProductProfile main = forWorkspace(primary);
        Set<String> units = new LinkedHashSet<>(main.units());
        Set<String> names = new LinkedHashSet<>(main.examples());
        for (FarmWorkspace workspace : FarmWorkspace.values()) {
            if (enabled.contains(workspace)) {
                units.addAll(forWorkspace(workspace).units());
                names.addAll(forWorkspace(workspace).examples());
            }
        }
        return new FarmProductProfile(main.category(), new ArrayList<>(units), new ArrayList<>(names));
    }

    private static FarmProductProfile profile(String category, String units, String names) {
        return new FarmProductProfile(category, List.of(units.split(",")), List.of(names.split(",")));
    }
}
