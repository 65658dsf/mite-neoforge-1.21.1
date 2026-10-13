package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.rules.FoodCookingExperienceRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;

/** Food result-slot XP uses current withdrawal quantities instead of the modern recipe XP bank. @author NingMeng123 */
public final class SourceCookingExperience {
    private SourceCookingExperience() {}

    public static boolean isSourceResult(ItemStack output) {
        return !output.isEmpty() && FoodCookingExperienceRules.rewardPerItem(itemId(output)) >= 0;
    }
    public static boolean isSourceRecipe(AbstractCookingRecipe recipe, HolderLookup.Provider registries) {
        return isSourceResult(recipe.getResultItem(registries));
    }
    public static void awardTaken(Player player, ItemStack result, int removed) {
        if (!(player.level() instanceof ServerLevel level) || removed <= 0 || result.isEmpty()) return;
        int reward = FoodCookingExperienceRules.rewardForTaken(itemId(result), removed);
        if (reward <= 0) return;
        // vq creates one orb for the whole taken quantity; this is not the death-orb ownership path.
        level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + .5D, player.getZ() + .5D, reward));
    }
    private static String itemId(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
}
