package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.progression.EnchantmentSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** wp/uh/of launcher effects use the native persisted weapon snapshot. @author NingMeng123 */
public final class RangedEnchantments {
    private RangedEnchantments() {}
    public static int pullTicks(ItemStack bow, Level level) {
        return Math.max(1, 20 - Math.round(2F * R196Enchantments.level(bow, level, "quickness")));
    }
    public static float pull(ItemStack bow, Level level, int ticks) {
        return Math.min(Math.max(ticks, 0) / (float)pullTicks(bow, level), 1F);
    }
    public static float power(ItemStack bow, Level level, int ticks) {
        float fraction = pull(bow, level, ticks);
        return (fraction * fraction + 2F * fraction) / 3F;
    }
    public static float recovery(AbstractArrow arrow, float materialChance) {
        var sources = R196Enchantments.sources(arrow.getWeaponItem(), arrow.level(), "arrow_recovery");
        if (sources.affix() == 0) {
            float fraction = Math.min(sources.normal() / 5F, 1F);
            return fraction + (1F - fraction) * materialChance;
        }
        float improvement = (Math.min(sources.normal() / 5F, 1F) + sources.affix() / 5F) * (1F - materialChance);
        return Math.clamp(materialChance + improvement, 0F, 1F);
    }
    public static void afterHit(AbstractArrow arrow, Entity entity) {
        if (arrow.level().isClientSide || !(entity instanceof LivingEntity target)) return;
        var poison = R196Enchantments.sources(arrow.getWeaponItem(), arrow.level(), "poison");
        if (EnchantmentSources.present(poison) && SourceEnchantmentCombat.biological(target)
                && arrow.getRandom().nextFloat() < EnchantmentSources.probability(poison, value -> value / 5F))
            target.addEffect(new MobEffectInstance(MobEffects.POISON,
                    (int)EnchantmentSources.number(poison, value -> 160 + Math.round(48F * value)), 0), arrow.getOwner());
        SourceEnchantmentCombat.thorns(arrow.getOwner(), target, arrow.getRandom());
    }
}
