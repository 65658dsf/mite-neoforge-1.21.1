package io.github.ningmeng123.mite.enchantment;

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
        float fraction = Math.min(R196Enchantments.level(arrow.getWeaponItem(), arrow.level(), "arrow_recovery") / 5F, 1F);
        return fraction + (1F - fraction) * materialChance;
    }
    public static void afterHit(AbstractArrow arrow, Entity entity) {
        if (arrow.level().isClientSide || !(entity instanceof LivingEntity target)) return;
        int poison = R196Enchantments.level(arrow.getWeaponItem(), arrow.level(), "poison");
        if (poison > 0 && SourceEnchantmentCombat.biological(target) && arrow.getRandom().nextFloat() < poison / 5F)
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 160 + Math.round(48F * poison), 0), arrow.getOwner());
        SourceEnchantmentCombat.thorns(arrow.getOwner(), target, arrow.getRandom());
    }
}
