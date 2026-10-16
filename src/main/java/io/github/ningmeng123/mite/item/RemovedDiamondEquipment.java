package io.github.ningmeng123.mite.item;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared exclusion for diamond equipment while retaining diamond materials. @author NingMeng123 */
public final class RemovedDiamondEquipment {
    public static final Set<ResourceLocation> IDS = Set.of(
            ResourceLocation.withDefaultNamespace("diamond_sword"),
            ResourceLocation.withDefaultNamespace("diamond_axe"),
            ResourceLocation.withDefaultNamespace("diamond_pickaxe"),
            ResourceLocation.withDefaultNamespace("diamond_shovel"),
            ResourceLocation.withDefaultNamespace("diamond_hoe"),
            ResourceLocation.withDefaultNamespace("diamond_helmet"),
            ResourceLocation.withDefaultNamespace("diamond_chestplate"),
            ResourceLocation.withDefaultNamespace("diamond_leggings"),
            ResourceLocation.withDefaultNamespace("diamond_boots"),
            ResourceLocation.withDefaultNamespace("diamond_horse_armor"));

    private RemovedDiamondEquipment() {}

    public static boolean isRemoved(ItemStack stack) {
        return !stack.isEmpty() && isRemoved(stack.getItem());
    }

    public static boolean isRemoved(Item item) {
        return IDS.contains(BuiltInRegistries.ITEM.getKey(item));
    }
}
