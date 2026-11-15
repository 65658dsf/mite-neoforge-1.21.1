package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.progression.EnchantmentSources;
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
        return sources(stack, level, id).totalLevel();
    }

    /** Only linear level coefficients may consume level(); nonlinear effects inspect both sources. */
    public static EnchantmentSources.Levels sources(ItemStack stack, Level level, String id) {
        return EnchantmentSources.levels(stack, level, key(id).location().toString());
    }

    /** Keep ordinary R196's historical cap while allowing independent affix resistance to add to it. */
    public static float freeActionResistance(LivingEntity entity) {
        float highest = 0F;
        for (EquipmentSlot slot : ARMOR) {
            var pair = sources(entity.getItemBySlot(slot), entity.level(), "free_action");
            float resistance = io.github.ningmeng123.mite.rules.EnchantmentCombatRules.freeActionResistance(pair.normal())
                    + Math.max(pair.affix(), 0) * .2F;
            highest = Math.max(highest, resistance);
        }
        return Math.clamp(highest, 0F, 1F);
    }

    /** Source chances add first; every requested point consumes exactly one random roll. */
    public static int unbreakingDamage(ItemStack stack, net.minecraft.server.level.ServerLevel world, int requested) {
        return unbreakingDamage(stack, world, requested, EnchantmentSources.levels(stack, world, "minecraft:unbreaking").normal());
    }
    public static int unbreakingDamage(ItemStack stack, net.minecraft.server.level.ServerLevel world, int requested, int ordinaryLevel) {
        var pair = EnchantmentSources.levels(stack, world, "minecraft:unbreaking");
        int remaining = Math.max(requested, 0);
        if (remaining == 0) return 0;
        float baseChance = io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.unbreakingNegationChance(
                pair.affix() == 0 ? ordinaryLevel : pair.normal()) + pair.affix() * .15F;
        float chance = Math.clamp(io.github.ningmeng123.mite.api.MiteExtensions.policy()
                .unbreakingNegationChance(stack, baseChance), 0F, 1F);
        if (chance <= 0F) return remaining;
        for (int point = 0; point < requested; point++) if (world.random.nextFloat() < chance) remaining--;
        return remaining;
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
