package io.github.ningmeng123.mite.ranged;

import io.github.ningmeng123.mite.item.R196ReinforcedBowItem;
import io.github.ningmeng123.mite.rules.R196WeaponRules;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Source Gaussian launch spread rather than the modern triangular approximation. @author NingMeng123 */
public final class SourceBowLaunch {
    private SourceBowLaunch() {}
    public static boolean launch(Projectile projectile, LivingEntity shooter, float pitch, float yaw, float speed) {
        ItemStack launcher = projectile instanceof AbstractArrow arrow ? arrow.getWeaponItem() : shooter.getUseItem();
        if (launcher == null || !(launcher.is(Items.BOW) || launcher.getItem() instanceof R196ReinforcedBowItem)) return false;
        String material = launcher.getItem() instanceof R196ReinforcedBowItem bow ? bow.material() : "wood";
        float spread = shooter instanceof Player player ? inaccuracy(launcher, player,
                shooter.isInWaterOrBubble() || shooter.isInLava()) : 1F;
        if (shooter instanceof Player) {
            speed *= R196WeaponRules.velocityMultiplier(material);
            projectile.setPos(shooter.getX() - (double)(Mth.cos(yaw * Mth.DEG_TO_RAD) * .16F) * (double).3F,
                    shooter.getEyeY() - (double).1F,
                    shooter.getZ() - (double)(Mth.sin(yaw * Mth.DEG_TO_RAD) * .16F) * (double).3F);
        }
        Vec3 direction = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD),
                -Mth.sin(pitch * Mth.DEG_TO_RAD), Mth.cos(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD)).normalize();
        var random = projectile.getRandom();
        Vec3 velocity = direction.add(random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * .0075F * spread,
                random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * .0075F * spread,
                random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * .0075F * spread).scale(speed);
        projectile.setDeltaMovement(velocity);
        projectile.setYRot((float)(Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG));
        projectile.setXRot((float)(Mth.atan2(velocity.y, velocity.horizontalDistance()) * Mth.RAD_TO_DEG));
        projectile.yRotO = projectile.getYRot(); projectile.xRotO = projectile.getXRot();
        return true;
    }

    /** True Flight's nonlinear accuracy gains are evaluated independently against the player's base. */
    public static float inaccuracy(ItemStack launcher, Player player, boolean inLiquid) {
        var sources = R196Enchantments.sources(launcher, player.level(), "true_flight");
        int level = ProgressionRules.attributeLevel(player.experienceLevel);
        if (sources.affix() == 0) return R196WeaponRules.inaccuracy((int)Math.min((long)level
                + Math.round(8F * sources.normal()), Integer.MAX_VALUE), inLiquid);
        float baseline = R196WeaponRules.inaccuracy(level, inLiquid);
        return (float)Math.max(0D, EnchantmentSources.delta(sources, baseline, value ->
                R196WeaponRules.inaccuracy((int)Math.min((long)level + Math.round(8F * value),
                        Integer.MAX_VALUE), inLiquid)));
    }
}
