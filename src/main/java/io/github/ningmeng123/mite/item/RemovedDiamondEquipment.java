package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared exclusion for retired equipment and Simply Swords diamond/netherite/unique weapons. @author NingMeng123 */
public final class RemovedDiamondEquipment {
    public static final Set<ResourceLocation> IDS = retiredIds();

    private RemovedDiamondEquipment() {}

    private static Set<ResourceLocation> retiredIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String family : List.of("sword", "axe", "pickaxe", "shovel", "hoe"))
            ids.add(ResourceLocation.withDefaultNamespace("stone_" + family));
        for (String family : List.of("sword", "axe", "pickaxe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots")) {
            ids.add(ResourceLocation.withDefaultNamespace("diamond_" + family));
            ids.add(ResourceLocation.withDefaultNamespace("netherite_" + family));
        }
        ids.add(ResourceLocation.withDefaultNamespace("diamond_horse_armor"));
        // Stable optional IDs keep filtering independent of mod loading. Runic weapons remain available.
        for (String material : List.of("diamond", "netherite")) for (String family : SimplySwordsCompat.families())
            ids.add(ResourceLocation.fromNamespaceAndPath(SimplySwordsCompat.MOD_ID, material + "_" + family));
        return Set.copyOf(ids);
    }

    public static boolean isRemoved(ItemStack stack) {
        return !stack.isEmpty() && isRemoved(stack.getItem());
    }

    public static boolean isRemoved(Item item) {
        return IDS.contains(BuiltInRegistries.ITEM.getKey(item)) || SimplySwordsCompat.uniqueWeapon(item);
    }
}
