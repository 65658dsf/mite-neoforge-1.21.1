package io.github.ningmeng123.mite.loot;

import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;

/** Adds a planting source to village houses and spawner dungeons. @author NingMeng123 */
public final class LemonSeedChestLoot {
    private static final float DUNGEON_CHANCE = 0.20F;
    private static final float VILLAGE_CHANCE = 0.125F;
    private static final Set<ResourceLocation> VILLAGE_HOUSES = Set.of(
            BuiltInLootTables.VILLAGE_DESERT_HOUSE.location(),
            BuiltInLootTables.VILLAGE_PLAINS_HOUSE.location(),
            BuiltInLootTables.VILLAGE_SAVANNA_HOUSE.location(),
            BuiltInLootTables.VILLAGE_SNOWY_HOUSE.location(),
            BuiltInLootTables.VILLAGE_TAIGA_HOUSE.location());

    private LemonSeedChestLoot() {}

    public static ObjectArrayList<ItemStack> append(ObjectArrayList<ItemStack> loot, LootContext context) {
        ResourceLocation table = context.getQueriedLootTableId();
        float chance = BuiltInLootTables.SIMPLE_DUNGEON.location().equals(table)
                ? DUNGEON_CHANCE : VILLAGE_HOUSES.contains(table) ? VILLAGE_CHANCE : 0F;
        if (chance > 0F && context.getRandom().nextFloat() < chance) {
            loot.add(new ItemStack(MiteFoodPlants.LEMON_SEEDS.get(), 1 + context.getRandom().nextInt(2)));
        }
        return loot;
    }
}
