package io.github.ningmeng123.mite.rules;

/** wr.getChanceOfMeltingWhenFilledWithLava source probabilities. @author NingMeng123 */
public final class LavaBucketRules {
    private LavaBucketRules() {}
    public static float meltingChance(String metal) {
        return switch (metal) {
            case "adamantium" -> 0F;
            case "gold" -> .2F;
            case "copper", "silver" -> .01F * (64F / 4F);
            case "iron" -> .01F * (64F / 8F);
            case "ancient_metal" -> .01F * (64F / 16F);
            case "mithril" -> .01F * (64F / 64F);
            default -> 0F;
        };
    }
}
