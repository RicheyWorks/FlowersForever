package com.flowerfarm.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.FloralRecipeIngredient;
import com.flowerfarm.model.GrowingTemplate;

import java.time.Instant;
import java.util.List;

/** Editable production inputs never bind persistence IDs, timestamps, or recipe back references. */
public final class ProductionApiDtos {
    private ProductionApiDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TemplateRequest(
            String name, String cropName, String variety, double spacingInches,
            int rowsPerBed, int sowDaysFromFrost, Integer transplantDaysFromFrost,
            int daysToHarvest, int harvestWindowDays, int successionDays,
            double stemsPerPlant, double germinationPercent, int soilPrepLeadDays,
            int supportDaysAfterPlanting, boolean active, String notes) {
        public GrowingTemplate toTemplate() {
            return new GrowingTemplate(name, cropName, variety, spacingInches, rowsPerBed,
                    sowDaysFromFrost, transplantDaysFromFrost, daysToHarvest,
                    harvestWindowDays, successionDays, stemsPerPlant, germinationPercent,
                    soilPrepLeadDays, supportDaysAfterPlanting, active, notes);
        }
    }

    public record TemplateResponse(
            Long id, String name, String cropName, String variety, double spacingInches,
            int rowsPerBed, int sowDaysFromFrost, Integer transplantDaysFromFrost,
            int daysToHarvest, int harvestWindowDays, int successionDays,
            double stemsPerPlant, double germinationPercent, int soilPrepLeadDays,
            int supportDaysAfterPlanting, boolean active, String notes,
            Instant createdAt, Instant updatedAt) {
        public static TemplateResponse from(GrowingTemplate template) {
            return new TemplateResponse(template.getId(), template.getName(), template.getCropName(),
                    template.getVariety(), template.getSpacingInches(), template.getRowsPerBed(),
                    template.getSowDaysFromFrost(), template.getTransplantDaysFromFrost(),
                    template.getDaysToHarvest(), template.getHarvestWindowDays(),
                    template.getSuccessionDays(), template.getStemsPerPlant(),
                    template.getGerminationPercent(), template.getSoilPrepLeadDays(),
                    template.getSupportDaysAfterPlanting(), template.isActive(), template.getNotes(),
                    template.getCreatedAt(), template.getUpdatedAt());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecipeRequest(
            String name, String designType, double salePrice, int laborMinutes,
            double laborRatePerHour, double overheadPercent, double wastePercent,
            String notes, List<IngredientRequest> ingredients) {
        public FloralRecipe toRecipe() {
            FloralRecipe recipe = new FloralRecipe(name, designType, salePrice, laborMinutes,
                    laborRatePerHour, overheadPercent, wastePercent, notes);
            if (ingredients != null) {
                for (IngredientRequest ingredient : ingredients) {
                    // Entity binding previously ignored null ingredient entries as well.
                    if (ingredient != null) {
                        recipe.addIngredient(ingredient.toIngredient());
                    }
                }
            }
            return recipe;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IngredientRequest(
            String itemName, double stemsPerDesign, int packSize, double packCost, String notes) {
        public FloralRecipeIngredient toIngredient() {
            return new FloralRecipeIngredient(itemName, stemsPerDesign, packSize, packCost, notes);
        }
    }

    public record RecipeResponse(
            Long id, String name, String designType, double salePrice, int laborMinutes,
            double laborRatePerHour, double overheadPercent, double wastePercent,
            String notes, List<IngredientResponse> ingredients, Instant createdAt, Instant updatedAt) {
        public static RecipeResponse from(FloralRecipe recipe) {
            return new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getDesignType(),
                    recipe.getSalePrice(), recipe.getLaborMinutes(), recipe.getLaborRatePerHour(),
                    recipe.getOverheadPercent(), recipe.getWastePercent(), recipe.getNotes(),
                    recipe.getIngredients().stream().map(IngredientResponse::from).toList(),
                    recipe.getCreatedAt(), recipe.getUpdatedAt());
        }
    }

    public record IngredientResponse(
            Long id, String itemName, double stemsPerDesign, int packSize, double packCost, String notes) {
        public static IngredientResponse from(FloralRecipeIngredient ingredient) {
            return new IngredientResponse(ingredient.getId(), ingredient.getItemName(),
                    ingredient.getStemsPerDesign(), ingredient.getPackSize(), ingredient.getPackCost(),
                    ingredient.getNotes());
        }
    }
}
