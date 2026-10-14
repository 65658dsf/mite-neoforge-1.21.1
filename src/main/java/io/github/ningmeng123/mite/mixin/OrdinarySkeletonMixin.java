package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only minecraft:skeleton receives source tr spawn metadata and its persistent 25 percent melee variant. @author NingMeng123 */
@Mixin(AbstractSkeleton.class)
public abstract class OrdinarySkeletonMixin extends Monster implements OrdinarySkeletonState {
    @Unique private static final EntityDataAccessor<Integer> MITE_TYPE = SynchedEntityData.defineId(AbstractSkeleton.class, EntityDataSerializers.INT);
    @Shadow @Final private RangedBowAttackGoal<AbstractSkeleton> bowGoal;
    @Shadow @Final private MeleeAttackGoal meleeGoal;
    @Unique private RangedAttackGoal mite$rangedGoal;
    @Unique private MeleeAttackGoal mite$meleeGoal;
    protected OrdinarySkeletonMixin(EntityType<? extends Monster> type, Level level) { super(type, level); }
    @Unique private AbstractSkeleton mite$self() { return (AbstractSkeleton)(Object)this; }
    @Unique private boolean mite$ordinary() { return getType() == EntityType.SKELETON; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(MITE_TYPE, 0);
    }
    @Override public int mite$sourceSkeletonType() { return entityData.get(MITE_TYPE); }
    @Override public void mite$setSourceSkeletonType(int type) {
        entityData.set(MITE_TYPE, Math.clamp(type,0,2)); refreshDimensions();
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor.equals(MITE_TYPE) && mite$ordinary()) refreshDimensions();
    }
    @Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
    private void mite$ordinaryEquipment(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group, CallbackInfoReturnable<SpawnGroupData> cir) {
        if (!mite$ordinary()) return;
        ((MobSpawnTypeAccessor)(Object)this).mite$setSpawnType(reason);
        getAttribute(Attributes.FOLLOW_RANGE).addOrReplacePermanentModifier(new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath("mite", "random_follow_bonus"), random.nextGaussian() * .05,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        int type = level.getLevel().dimension().equals(Level.NETHER) ? 1 : random.nextFloat() < .25F ? 2 : 0;
        mite$setSourceSkeletonType(type); R196Skeleton.initializeOrdinaryTroop(mite$self(),type); mite$self().reassessWeaponGoal();
        cir.setReturnValue(group);
    }
    @Inject(method = "reassessWeaponGoal", at = @At("HEAD"), cancellable = true)
    private void mite$sourceWeaponGoals(CallbackInfo ci) {
        if (!mite$ordinary() || level().isClientSide) return;
        if (mite$rangedGoal == null) mite$rangedGoal = new RangedAttackGoal(mite$self(),1D,20,60,15F);
        if (mite$meleeGoal == null) mite$meleeGoal = new MeleeAttackGoal(mite$self(),1.2D,false);
        goalSelector.removeGoal(bowGoal); goalSelector.removeGoal(meleeGoal);
        goalSelector.removeGoal(mite$rangedGoal); goalSelector.removeGoal(mite$meleeGoal);
        goalSelector.addGoal(4,getMainHandItem().getItem() instanceof BowItem ? mite$rangedGoal : mite$meleeGoal);
        ci.cancel();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); if (mite$ordinary()) tag.putByte("SkeletonType",(byte)mite$sourceSkeletonType());
    }
    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void mite$restoreVariant(CompoundTag tag, CallbackInfo ci) {
        if (!mite$ordinary()) return;
        mite$setSourceSkeletonType(tag.getByte("SkeletonType")); mite$self().reassessWeaponGoal();
    }
    @Override public EntityDimensions getDefaultDimensions(Pose pose) {
        return mite$ordinary() && entityData != null && mite$sourceSkeletonType() == 1
                ? EntityDimensions.scalable(.72F,2.34F).withEyeHeight(2.1F) : super.getDefaultDimensions(pose);
    }
    @Override public boolean fireImmune() { return mite$ordinary() && mite$sourceSkeletonType() == 1 || super.fireImmune(); }
    @Override protected boolean isSunBurnTick() {
        return (!mite$ordinary() || mite$sourceSkeletonType() != 1 && getItemBySlot(EquipmentSlot.HEAD).isEmpty()) && super.isSunBurnTick();
    }
    @Override public boolean doHurtTarget(Entity target) {
        boolean affected = super.doHurtTarget(target);
        if (affected && mite$ordinary() && mite$sourceSkeletonType() == 1 && target instanceof LivingEntity living && living.isAlive())
            living.addEffect(new MobEffectInstance(MobEffects.WITHER,200),mite$self());
        return affected;
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recent) {
        if (!mite$ordinary()) { super.dropCustomDeathLoot(level,source,recent); return; }
        R196MobEquipment.dropEquipment(mite$self(),source,recent,getPersistentData().getBoolean("mite_massive_fall_031"),false,this::getEquipmentDropChance);
    }
    @Override protected void dropFromLootTable(DamageSource source, boolean recent) {
        if (!mite$ordinary() || mite$sourceSkeletonType() == 0) { super.dropFromLootTable(source,recent); return; }
        if (getPersistentData().getBoolean("mite_massive_fall_031") && random.nextFloat() >= .1F) return;
        int looting = source.getEntity() instanceof LivingEntity killer ? net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentLevel(
                registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(
                        net.minecraft.world.item.enchantment.Enchantments.LOOTING),killer) : 0;
        if (mite$sourceSkeletonType() == 1) {
            int coal = random.nextInt(3+looting)-1;
            if (coal>0 && !recent) coal-=random.nextInt(coal+1);
            for (int i=0;i<coal;++i) spawnAtLocation(net.minecraft.world.item.Items.COAL);
            if (recent && !getPersistentData().getBoolean("mite_massive_fall_031") && random.nextInt(200)<5+looting*2)
                spawnAtLocation(net.minecraft.world.item.Items.WITHER_SKELETON_SKULL);
        }
        int bones = random.nextInt(3);
        if (bones>0 && !recent) bones-=random.nextInt(bones+1);
        for (int i=0;i<bones;++i) spawnAtLocation(net.minecraft.world.item.Items.BONE);
    }
}
