package io.github.ningmeng123.mite.loot;

import io.github.ningmeng123.mite.registry.MiteFoods;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;

/** Independent food rewards appended after the complete dungeon loot transaction. @author NingMeng123 */
public final class DungeonFoodLoot {
    public static final float CHANCE = .2F;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 3;

    private DungeonFoodLoot() {}

    public static ObjectArrayList<ItemStack> append(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!BuiltInLootTables.SIMPLE_DUNGEON.location().equals(context.getQueriedLootTableId())) return loot;
        var random = context.getRandom();
        if (random.nextFloat() < CHANCE)
            loot.add(new ItemStack(MiteFoods.get("orange"), MIN_COUNT + random.nextInt(MAX_COUNT - MIN_COUNT + 1)));
        if (random.nextFloat() < CHANCE)
            loot.add(new ItemStack(MiteFoods.get("onion"), MIN_COUNT + random.nextInt(MAX_COUNT - MIN_COUNT + 1)));
        return loot;
    }
}
