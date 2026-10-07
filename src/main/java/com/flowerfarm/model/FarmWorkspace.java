package com.flowerfarm.model;

import java.util.Optional;

/** The primary desktop workspace shown to this installation. */
public enum FarmWorkspace {
    ROSE_AND_EVENTS(FarmEnterprise.CUT_FLOWERS),
    WILDFLOWER_FARM(FarmEnterprise.CUT_FLOWERS),
    FLOWER_SHOP(FarmEnterprise.FLORAL_RETAIL),
    CANNABIS_FARM(FarmEnterprise.CANNABIS),
    POTATO_FARM(FarmEnterprise.ROW_CROPS),
    APPLE_ORCHARD(FarmEnterprise.ORCHARDS),
    BERRY_FARM(FarmEnterprise.BERRIES),
    NURSERY_FARM(FarmEnterprise.NURSERY),
    LIVESTOCK_FARM(FarmEnterprise.LIVESTOCK),
    POULTRY_FARM(FarmEnterprise.POULTRY_AND_EGGS),
    DAIRY_FARM(FarmEnterprise.DAIRY),
    VINEYARD_FARM(FarmEnterprise.VINEYARDS),
    HAY_FARM(FarmEnterprise.HAY_AND_FORAGE),
    HOPS_FARM(FarmEnterprise.HOPS),
    GRAIN_FARM(FarmEnterprise.GRAINS),
    SEED_FARM(FarmEnterprise.SEED_CROPS),
    NUT_ORCHARD(FarmEnterprise.TREE_NUTS),
    VEGETABLE_FARM(FarmEnterprise.VEGETABLES),
    AQUACULTURE_FARM(FarmEnterprise.AQUACULTURE),
    CHRISTMAS_TREE_FARM(FarmEnterprise.CHRISTMAS_TREES),
    APIARY_FARM(FarmEnterprise.APIARY),
    MUSHROOM_FARM(FarmEnterprise.MUSHROOMS),
    CRANBERRY_FARM(FarmEnterprise.CRANBERRIES),
    PUMPKIN_PATCH(FarmEnterprise.PUMPKINS),
    CUSTOM_FARM(null);

    private final FarmEnterprise requiredEnterprise;

    FarmWorkspace(FarmEnterprise requiredEnterprise) {
        this.requiredEnterprise = requiredEnterprise;
    }

    /** A workspace may require a business line, while custom farms accept any mix. */
    public Optional<FarmEnterprise> requiredEnterprise() {
        return Optional.ofNullable(requiredEnterprise);
    }
}
