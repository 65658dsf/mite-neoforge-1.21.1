package io.github.ningmeng123.mite.enchantment;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/** Shared registry-based enchantment reads; R196 worn levels use the maximum, never the sum. @author NingMeng123 */
public final class R196Enchantments {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private R196Enchantments() {}

    public static ResourceKey<Enchantment> key(String id) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                id.indexOf(':') >= 0 ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath("mite", id));
    }

    public static int level(ItemStack stack, Level level, String id) {
        if (stack == null || stack.isEmpty()) return 0;
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(key(id))
                .map(holder -> Math.max(0, stack.getEnchantmentLevel(holder))).orElse(0);
    }

    public static int equipped(LivingEntity entity, String id) {
        int highest = 0;
        for (EquipmentSlot slot : ARMOR) highest = Math.max(highest, level(entity.getItemBySlot(slot), entity.level(), id));
        return highest;
    }

    public static Set<String> sourceIds() {
        return R196EnchantmentCatalog.definitions().stream().map(R196EnchantmentCatalog.Definition::resourceId).collect(Collectors.toUnmodifiableSet());
    }
}
