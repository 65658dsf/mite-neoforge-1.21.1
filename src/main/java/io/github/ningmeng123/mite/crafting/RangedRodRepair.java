package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import net.minecraft.world.item.ItemStack;

/** aaf combines non-quality rods at half their genuine lowest manufacturing difficulty. @author NingMeng123 */
public final class RangedRodRepair {
    private RangedRodRepair() {}
    public static boolean applies(ItemStack stack) {
        return !stack.isEmpty() && (MiteRangedItems.rodMaterial(stack) != null || MiteRangedItems.carrotRodMaterial(stack) != null);
    }
    public static float lowestDifficulty(ItemStack stack) {
        if (MiteRangedItems.carrotRodMaterial(stack) != null) return 40F;
        Material material = MiteRangedItems.rodMaterial(stack);
        if (material == null) return -1F;
        return switch (material) {
            case FLINT -> 150F;
            case OBSIDIAN -> 147.22223F;
            case COPPER, SILVER, GOLD -> 169.44444F;
            case IRON -> 213.88889F;
            case ANCIENT_METAL -> 302.77777F;
            case MITHRIL -> 836.1111F;
            case ADAMANTIUM -> 2969.4443F;
            default -> -1F;
        };
    }
    public static TimedCraftingRecipes.Rule rule(ItemStack stack) {
        float difficulty = lowestDifficulty(stack);
        if (difficulty < 0F) return null;
        Material material = MiteRangedItems.rodMaterial(stack);
        if (material == null) material = MiteRangedItems.carrotRodMaterial(stack);
        // awy selects the hardest metal on output when aaf's synthetic repair has no recipe.
        String bench = material == Material.FLINT || material == Material.OBSIDIAN ? null : material.id;
        return new TimedCraftingRecipes.Rule(difficulty / 2F, bench, -1);
    }
}
