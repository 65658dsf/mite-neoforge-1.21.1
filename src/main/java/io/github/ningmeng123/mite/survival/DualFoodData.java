package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.item.FoodProfile;

/** Separate R196 nutrition reserve; vanilla saturation remains an interoperability field. @author NingMeng123 */
public interface DualFoodData extends LeveledFoodData {
    int mite$getNutrition();
    void mite$setNutrition(int nutrition);
    void mite$ingest(FoodProfile profile);
}
