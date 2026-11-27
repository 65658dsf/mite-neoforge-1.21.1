package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.rules.NutritionRules;

/** R196's two food resources and independent micronutrient flags. @author NingMeng123 */
public record FoodProfile(int satiation, int nutrition, boolean protein, boolean fat, boolean plants,
                          int sugar, boolean drink, boolean alwaysEdible, float saturationGain, int plantNutrientGain) {
    public FoodProfile(int satiation, int nutrition, boolean protein, boolean fat, boolean plants,
                       int sugar, boolean drink, boolean alwaysEdible) {
        this(satiation, nutrition, protein, fat, plants, sugar, drink, alwaysEdible,
                0F, NutritionRules.addMicronutrient(0, nutrition, plants));
    }

    public FoodProfile {
        if (satiation < 0 || nutrition < 0 || sugar < 0 || plantNutrientGain < 0
                || !Float.isFinite(saturationGain) || saturationGain < 0F)
            throw new IllegalArgumentException("Food resources, saturation and sugar must be finite and nonnegative");
    }
}
