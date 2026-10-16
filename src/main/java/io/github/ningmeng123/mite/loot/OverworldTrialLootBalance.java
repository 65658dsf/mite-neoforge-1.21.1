package io.github.ningmeng123.mite.loot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.IdentityHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Reduce selected Overworld trial rewards without redistributing their weight to other rewards. @author NingMeng123 */
public final class OverworldTrialLootBalance {
    public static final int RETENTION_DENOMINATOR = 6;

    private OverworldTrialLootBalance() {}

    public static boolean applies(ServerLevel level, @Nullable ResourceLocation tableId) {
        if (tableId == null || !level.dimension().equals(Level.OVERWORLD) || !tableId.getNamespace().equals("minecraft")) return false;
        return tableId.getPath().startsWith("chests/trial_chambers/")
                || tableId.getPath().startsWith("pots/trial_chambers/");
    }

    /** One decision per material per complete reward: both occurrence probability and expected yield become one sixth. */
    public static boolean reduce(ObjectArrayList<ItemStack> loot, ServerLevel level, @Nullable ResourceLocation tableId, RandomSource random) {
        if (!applies(level, tableId)) return false;
        var retained = new IdentityHashMap<Item, Boolean>(3);
        return loot.removeIf(stack -> isTarget(stack) && !retained.computeIfAbsent(stack.getItem(),
                item -> random.nextInt(RETENTION_DENOMINATOR) == 0));
    }

    private static boolean isTarget(ItemStack stack) {
        return stack.is(Items.EMERALD_BLOCK) || stack.is(Items.EMERALD) || stack.is(Items.DIAMOND);
    }
}
