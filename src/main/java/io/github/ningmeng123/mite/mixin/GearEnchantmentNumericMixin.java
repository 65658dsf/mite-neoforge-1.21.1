package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;

/** Each numerical pipeline starts from the same base for ordinary and affix effects. @author NingMeng123 */
@Mixin(EnchantmentHelper.class)
public abstract class GearEnchantmentNumericMixin {
    @WrapMethod(method = "processAmmoUse")
    private static int mite$processAmmoUse(ServerLevel world, ItemStack launcher, ItemStack ammo, int amount, Operation<Integer> original) {
        return (int)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(launcher), amount, () -> original.call(world, launcher, ammo, amount)));
    }

    @WrapMethod(method = "processBlockExperience")
    private static int mite$processBlockExperience(ServerLevel world, ItemStack stack, int amount, Operation<Integer> original) {
        return (int)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), amount, () -> original.call(world, stack, amount)));
    }

    @WrapMethod(method = "processMobExperience")
    private static int mite$processMobExperience(ServerLevel world, Entity killer, Entity target, int amount, Operation<Integer> original) {
        return (int)Math.max(0D, EnchantmentSources.calculate(killer instanceof LivingEntity living && EnchantmentSources.hasAffixes(living), amount, () -> original.call(world, killer, target, amount)));
    }

    @WrapMethod(method = "getDamageProtection")
    private static float mite$getDamageProtection(ServerLevel world, LivingEntity living, DamageSource damage, Operation<Float> original) {
        return (float)EnchantmentSources.calculate(EnchantmentSources.hasAffixes(living), 0, () -> original.call(world, living, damage));
    }

    @WrapMethod(method = "modifyDamage")
    private static float mite$modifyDamage(ServerLevel world, ItemStack stack, Entity target, DamageSource damage, float amount, Operation<Float> original) {
        return (float)EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), amount, () -> original.call(world, stack, target, damage, amount));
    }

    @WrapMethod(method = "modifyFallBasedDamage")
    private static float mite$modifyFallBasedDamage(ServerLevel world, ItemStack stack, Entity target, DamageSource damage, float amount, Operation<Float> original) {
        return (float)EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), amount, () -> original.call(world, stack, target, damage, amount));
    }

    @WrapMethod(method = "modifyArmorEffectiveness")
    private static float mite$modifyArmorEffectiveness(ServerLevel world, ItemStack stack, Entity target, DamageSource damage, float amount, Operation<Float> original) {
        return (float)EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), amount, () -> original.call(world, stack, target, damage, amount));
    }

    @WrapMethod(method = "modifyKnockback")
    private static float mite$modifyKnockback(ServerLevel world, ItemStack stack, Entity target, DamageSource damage, float amount, Operation<Float> original) {
        return (float)EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), amount, () -> original.call(world, stack, target, damage, amount));
    }

    @WrapMethod(method = "processProjectileCount")
    private static int mite$processProjectileCount(ServerLevel world, ItemStack stack, Entity shooter, int amount, Operation<Integer> original) {
        return (int)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), amount, () -> original.call(world, stack, shooter, amount)));
    }

    @WrapMethod(method = "processProjectileSpread")
    private static float mite$processProjectileSpread(ServerLevel world, ItemStack stack, Entity shooter, float amount, Operation<Float> original) {
        return (float)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), amount, () -> original.call(world, stack, shooter, amount)));
    }

    @WrapMethod(method = "getPiercingCount")
    private static int mite$getPiercingCount(ServerLevel world, ItemStack stack, ItemStack projectile, Operation<Integer> original) {
        return (int)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), 0, () -> original.call(world, stack, projectile)));
    }

    @WrapMethod(method = "modifyDurabilityToRepairFromXp")
    private static int mite$modifyDurabilityToRepairFromXp(ServerLevel world, ItemStack stack, int amount, Operation<Integer> original) {
        return (int)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), amount, () -> original.call(world, stack, amount)));
    }

    @WrapMethod(method = "processEquipmentDropChance")
    private static float mite$processEquipmentDropChance(ServerLevel world, LivingEntity victim, DamageSource damage, float amount, Operation<Float> original) {
        return (float)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(victim) || damage.getEntity() instanceof LivingEntity attacker && EnchantmentSources.hasAffixes(attacker), amount, () -> original.call(world, victim, damage, amount)));
    }

    @WrapMethod(method = "getFishingLuckBonus")
    private static int mite$getFishingLuckBonus(ServerLevel world, ItemStack stack, Entity fisher, Operation<Integer> original) {
        return (int)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), 0, () -> original.call(world, stack, fisher)));
    }

    @WrapMethod(method = "getFishingTimeReduction")
    private static float mite$getFishingTimeReduction(ServerLevel world, ItemStack stack, Entity fisher, Operation<Float> original) {
        return (float)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), 0, () -> original.call(world, stack, fisher)));
    }

    @WrapMethod(method = "getTridentReturnToOwnerAcceleration")
    private static int mite$getTridentReturnToOwnerAcceleration(ServerLevel world, ItemStack stack, Entity owner, Operation<Integer> original) {
        return (int)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), 0, () -> original.call(world, stack, owner)));
    }

    @WrapMethod(method = "modifyCrossbowChargingTime")
    private static float mite$modifyCrossbowChargingTime(ItemStack stack, LivingEntity owner, float amount, Operation<Float> original) {
        return (float)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), amount, () -> original.call(stack, owner, amount)));
    }

    @WrapMethod(method = "getTridentSpinAttackStrength")
    private static float mite$getTridentSpinAttackStrength(ItemStack stack, LivingEntity owner, Operation<Float> original) {
        return (float)Math.max(0D, EnchantmentSources.calculate(EnchantmentSources.hasAffixes(stack), 0, () -> original.call(stack, owner)));
    }

}
