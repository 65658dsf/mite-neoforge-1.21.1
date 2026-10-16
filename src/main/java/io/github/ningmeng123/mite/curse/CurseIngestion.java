package io.github.ningmeng123.mite.curse;

import io.github.ningmeng123.mite.item.FoodProfile;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.rules.CurseProductRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.UseAnim;

/** uf.isIngestionForbiddenByCurse uses product identity, not nutrition flags. @author NingMeng123 */
public final class CurseIngestion {
    private CurseIngestion() {}

    public static boolean allowed(Player player, ItemStack stack) {
        if (stack.isEmpty()) return true;
        // ThrowablePotionItem inherits the DRINK animation but its use action throws immediately.
        if (stack.getItem() instanceof ThrowablePotionItem) return true;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (CurseProductRules.animalProduct(id)
                && PlayerCurses.has(player, R196Curse.CANNOT_EAT_ANIMALS, true)) return false;
        if (CurseProductRules.plantProduct(id)
                && PlayerCurses.has(player, R196Curse.CANNOT_EAT_PLANTS, true)) return false;
        if (id.equals("mite:bottle_of_disenchanting")) return true;
        FoodProfile food = MiteFoodCatalog.profile(stack);
        boolean drink = food != null ? food.drink() : stack.getUseAnimation() == UseAnim.DRINK;
        return !drink || !PlayerCurses.has(player, R196Curse.CANNOT_DRINK, true);
    }

    /** anh creates a temporary cake slice marked as both plant and animal product. */
    public static boolean cakeAllowed(Player player) {
        return !PlayerCurses.has(player, R196Curse.CANNOT_EAT_ANIMALS, true)
                && !PlayerCurses.has(player, R196Curse.CANNOT_EAT_PLANTS, true);
    }
}
