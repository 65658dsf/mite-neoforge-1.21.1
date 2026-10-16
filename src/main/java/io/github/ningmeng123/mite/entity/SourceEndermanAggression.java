package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** tg.f provocation, after the outer og/abw visible-candidate search. @author NingMeng123 */
public final class SourceEndermanAggression {
    public static final double SEARCH_RANGE = 64D;
    public static final int INVENTORY_ROLL_BOUND = 2000;

    public enum Decision {
        NONE(false, false), NATIVE(true, false), CURSE(true, false),
        HELD_ITEM(true, true), INVENTORY_ITEMS(true, true);

        private final boolean accepted;
        private final boolean immediate;
        Decision(boolean accepted, boolean immediate) { this.accepted = accepted; this.immediate = immediate; }
        public boolean accepted() { return accepted; }
        public boolean immediate() { return immediate; }
    }

    private SourceEndermanAggression() {}

    public static boolean isEnderItem(ItemStack stack) {
        return stack.is(Items.ENDER_PEARL) || stack.is(Items.ENDER_EYE);
    }

    /** The source has one hand; treating the modern offhand as held is an explicit adaptation. */
    public static boolean holdsEnderItem(Player player) {
        return isEnderItem(player.getMainHandItem()) || isEnderItem(player.getOffhandItem());
    }

    /** Direct player inventory only. Do not inspect the cursor, crafting grids or nested containers. */
    public static int countItems(Player player) {
        return player.getInventory().countItem(Items.ENDER_PEARL) + player.getInventory().countItem(Items.ENDER_EYE);
    }

    /** One decision per eligible candidate; callers must perform visibility and legality checks first. */
    public static Decision decide(EnderMan enderman, Player player, Predicate<LivingEntity> nativeAggression) {
        if (enderman.level().isClientSide || enderman.getTarget() != null) return Decision.NONE;
        // Preserve the source's first draw even when the player has no curse. A successful curse
        // returns before the item branch and therefore does not acquire its instant-warning rule.
        if (enderman.getRandom().nextInt(3) == 0 && PlayerCurses.has(player, R196Curse.ENDERMEN_ENEMY, true))
            return Decision.CURSE;
        if (holdsEnderItem(player)) return Decision.HELD_ITEM;
        int count = countItems(player);
        if (count > 0 && enderman.getRandom().nextInt(INVENTORY_ROLL_BOUND) < count)
            return Decision.INVENTORY_ITEMS;
        return nativeAggression.test(player) ? Decision.NATIVE : Decision.NONE;
    }
}
