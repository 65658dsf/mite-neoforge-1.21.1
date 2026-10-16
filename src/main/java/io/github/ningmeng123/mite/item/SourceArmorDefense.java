package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.rules.SourceArmorDefenseRules.Piece;
import io.github.ningmeng123.mite.rules.SourceArmorDefenseRules.Source;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** Source classifications and horse defense evidence; humanoid armor uses the modern damage pipeline. @author NingMeng123 */
public final class SourceArmorDefense {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private SourceArmorDefense() {}

    /** Source Piercing removes armor points once, before native toughness and armor-effectiveness functions. */
    public static float armorAfterPiercing(LivingEntity target, DamageSource source, float armor) {
        int piercing = io.github.ningmeng123.mite.enchantment.R196Enchantments.level(
                io.github.ningmeng123.mite.entity.MobCombat.weapon(source), target.level(), "piercing");
        return Math.max(armor - piercing, 0F);
    }
    public static Source flags(DamageSource source) {
        boolean absolute = source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        boolean unblockable = source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.STARVE)
                || io.github.ningmeng123.mite.combat.SourcePlayerCombat.isHandDamage(source);
        boolean lava = source.is(DamageTypes.LAVA);
        // R196 nb.setLavaDamage bypasses mundane armor but does not set nb.m()'s fire flag.
        boolean fire = source.is(DamageTypeTags.IS_FIRE) && !lava;
        return new Source(absolute, unblockable, source.is(DamageTypeTags.BYPASSES_ARMOR) || fire || lava,
                source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS), fire, source.is(DamageTypeTags.IS_FALL),
                source.is(DamageTypeTags.IS_EXPLOSION), source.is(DamageTypeTags.IS_PROJECTILE));
    }
    public static List<Piece> pieces(LivingEntity player) {
        List<Piece> pieces = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            var profile = EquipmentDurability.armorProfile(stack);
            if (profile == null) continue;
            pieces.add(new Piece(profile, stack.getDamageValue(), stack.getMaxDamage(), level(player, stack, Enchantments.PROTECTION),
                    level(player, stack, Enchantments.FIRE_PROTECTION), level(player, stack, Enchantments.FEATHER_FALLING),
                    level(player, stack, Enchantments.BLAST_PROTECTION), level(player, stack, Enchantments.PROJECTILE_PROTECTION)));
        }
        return List.copyOf(pieces);
    }
    private static int level(LivingEntity player, ItemStack stack, ResourceKey<Enchantment> enchantment) {
        return player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(enchantment)
                .map(stack::getEnchantmentLevel).orElse(0);
    }
}
