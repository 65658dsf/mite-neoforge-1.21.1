package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.ranged.ArrowRecovery;
import io.github.ningmeng123.mite.ranged.MaterialArrowView;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve native arrow stacks/launchers/packets, replacing only source-ammunition arithmetic. @author NingMeng123 */
@Mixin(AbstractArrow.class)
public abstract class RangedArrowMixin implements MaterialArrowView {
    @Unique private static final EntityDataAccessor<String> MITE_MATERIAL = SynchedEntityData.defineId(AbstractArrow.class, EntityDataSerializers.STRING);
    @Unique private float mite$preCriticalDamage;
    @Unique private AbstractArrow mite$self() { return (AbstractArrow)(Object)this; }
    @Override public String mite$arrowMaterial() { return mite$self().getEntityData().get(MITE_MATERIAL); }
    @Unique private void mite$syncMaterial() {
        var material = MiteRangedItems.arrowMaterial(mite$self().getPickupItemStackOrigin());
        mite$self().getEntityData().set(MITE_MATERIAL, material == null ? "" : material.id);
    }
    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void mite$defineMaterial(SynchedEntityData.Builder builder, CallbackInfo ci) { builder.define(MITE_MATERIAL, ""); }
    @Inject(method = {
            "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",
            "<init>(Lnet/minecraft/world/entity/EntityType;DDDLnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"
    }, at = @At("RETURN"))
    private void mite$sourceDamage(CallbackInfo ci) {
        if ((Object)this instanceof Arrow) {
            var material = MiteRangedItems.arrowMaterial(mite$self().getPickupItemStackOrigin());
            mite$self().setBaseDamage(material == null ? 2D : material.arrowDamage());
            mite$syncMaterial();
        }
    }
    @Inject(method = "setPickupItemStack", at = @At("TAIL"))
    private void mite$pickupMaterial(net.minecraft.world.item.ItemStack stack, CallbackInfo ci) { mite$syncMaterial(); }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mite$loadedMaterial(CompoundTag tag, CallbackInfo ci) { mite$syncMaterial(); }
    @Redirect(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;ceil(D)I"))
    private int mite$recordFractionalDamage(double damage) {
        mite$preCriticalDamage = (float)damage;
        return Mth.ceil(damage);
    }
    @Redirect(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int mite$criticalUsesSourceFloat(RandomSource random, int bound) {
        return MiteRangedItems.arrowMaterial(mite$self().getPickupItemStackOrigin()) == null ? random.nextInt(bound) : 0;
    }
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;igniteForSeconds(F)V"), index = 0)
    private float mite$sourceFlamingHit(float seconds) {
        var weapon = mite$self().getWeaponItem();
        return weapon != null && weapon.getItem() instanceof net.minecraft.world.item.BowItem ? seconds + .5F : seconds;
    }
    @Redirect(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mite$sourceImpact(Entity target, DamageSource source, float modernDamage) {
        AbstractArrow arrow = mite$self();
        var material = MiteRangedItems.arrowMaterial(arrow.getPickupItemStackOrigin());

        float speed = (float)arrow.getDeltaMovement().length();
        float damage = material == null ? modernDamage : R196RangedRules.impactDamage(material, speed, mite$preCriticalDamage,
                target.getType().is(EntityTypeTags.UNDEAD), target instanceof AbstractSkeleton,
                arrow.getOwner() instanceof AbstractSkeleton, arrow.getOwner() == null,
                arrow.getWeaponItem() != null && arrow.getWeaponItem().isEnchanted(), arrow.isCritArrow(),
                arrow.isCritArrow() ? arrow.getRandom().nextFloat() : 0F);
        if (!(damage > 0F)) return false;
        var snapshot = target instanceof LivingEntity living
                ? new io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat.HarmSnapshot(living) : null;
        boolean success = ArrowRecovery.hurt(arrow, target, source, damage);
        if (snapshot != null && snapshot.negativelyAffected((LivingEntity)target))
            io.github.ningmeng123.mite.enchantment.RangedEnchantments.afterHit(arrow, target);
        return success;
    }
}
