package io.github.ningmeng123.mite.item;

/** R196's two food resources and independent micronutrient flags. @author NingMeng123 */
public record FoodProfile(int satiation, int nutrition, boolean protein, boolean fat, boolean plants,
                          int sugar, boolean drink, boolean alwaysEdible) {
    public FoodProfile {
        if (satiation < 0 || nutrition < 0 || sugar < 0)
            throw new IllegalArgumentException("Food resources and sugar must be nonnegative");
    }
}
