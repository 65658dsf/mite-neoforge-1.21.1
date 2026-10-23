package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.CubeState;
import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.MobCorrosion;
import io.github.ningmeng123.mite.entity.MonsterHunting;
import io.github.ningmeng123.mite.entity.R196Cube;
import io.github.ningmeng123.mite.entity.VanillaCubeMoveControl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196's EntityCubic/GelatinousCube parent changes also apply to actual vanilla slime identities. @author NingMeng123 */
@Mixin(Slime.class)
public abstract class SourceVanillaCubeMixin extends Mob implements CubeState {
    @Unique private MobCorrosion mite$corrosion;
    protected SourceVanillaCubeMixin(EntityType<? extends Mob> type,Level level) { super(type,level); }
    @Override public MobCorrosion corrosion() { if (mite$corrosion==null) mite$corrosion=new MobCorrosion(); return mite$corrosion; }
    @Inject(method="<init>",at=@At("TAIL"))
    private void mite$sourceConstruction(EntityType<? extends Slime> type,Level level,CallbackInfo ci) {
        if ((Object)this instanceof R196Cube) return;
        Slime cube=(Slime)(Object)this;
        moveControl=new VanillaCubeMoveControl(cube);
        cube.setSize(1<<random.nextInt(3),true);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(16);
    }
    @Inject(method="registerGoals",at=@At("HEAD"),cancellable=true)
    private void mite$sourceTargets(CallbackInfo ci) {
        if ((Object)this instanceof R196Cube) return;
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
        if (!((Object)this instanceof MagmaCube)) {
            targetSelector.addGoal(3,new MonsterHunting.PreyGoal<>(this,Animal.class,true) { @Override protected double getFollowDistance() { return 8; } });
            targetSelector.addGoal(3,new MonsterHunting.PreyGoal<>(this,Villager.class,true) { @Override protected double getFollowDistance() { return 8; } });
        }
        ci.cancel();
    }
    @Inject(method="setSize",at=@At("TAIL"))
    private void mite$sourceSize(int size,boolean heal,CallbackInfo ci) {
        if ((Object)this instanceof R196Cube) return;
        Slime cube=(Slime)(Object)this; int actual=cube.getSize(); boolean magma=cube instanceof MagmaCube;
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(magma ? .2 : .1);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(actual*(magma ? 2 : 1));
        xpReward=actual*(magma ? 3 : 1);
    }
    @Inject(method="getDefaultDimensions",at=@At("RETURN"),cancellable=true)
    private void mite$sourceDimensions(Pose pose,CallbackInfoReturnable<EntityDimensions> cir) {
        if (!((Object)this instanceof R196Cube)) { int size=((Slime)(Object)this).getSize(); cir.setReturnValue(EntityDimensions.scalable(.5F*size,.5F*size).withEyeHeight(.325F*size)); }
    }
    @Inject(method="isDealsDamage",at=@At("HEAD"),cancellable=true)
    private void mite$tinySlimesAttack(CallbackInfoReturnable<Boolean> cir) { if (!((Object)this instanceof R196Cube)) cir.setReturnValue(isEffectiveAi()); }
    @Inject(method="dealDamage",at=@At("HEAD"),cancellable=true)
    private void mite$sourceContact(LivingEntity target,CallbackInfo ci) { if (!((Object)this instanceof R196Cube)) { R196Cube.vanillaContact((Slime)(Object)this,target); ci.cancel(); } }
    @Inject(method="tick",at=@At("TAIL"))
    private void mite$sourceChemicalTick(CallbackInfo ci) {
        if ((Object)this instanceof R196Cube || (Object)this instanceof MagmaCube || level().isClientSide) return;
        corrosion().advanceCountdowns(); corrosion().contact((Slime)(Object)this,false);
        if (getTarget() instanceof Player) corrosion().clearFeeding();
        if (corrosion().isFeeding()) ((Slime)(Object)this).targetSquish=(float)Math.sin(MobCombat.ticksWithOffset(this)/5F)*.1F;
    }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void mite$saveChemicalProgress(CompoundTag tag,CallbackInfo ci) { if (!((Object)this instanceof R196Cube)) corrosion().save(tag); }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void mite$loadChemicalProgress(CompoundTag tag,CallbackInfo ci) { if (!((Object)this instanceof R196Cube)) corrosion().load(tag); }
    @Inject(method="finalizeSpawn",at=@At("RETURN"))
    private void mite$unweightedSourceSize(CallbackInfoReturnable<net.minecraft.world.entity.SpawnGroupData> cir) { if (!((Object)this instanceof R196Cube)) ((Slime)(Object)this).setSize(1<<random.nextInt(3),true); }
    @WrapOperation(method="remove",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/monster/Slime;getSize()I",ordinal=0))
    private int mite$burningDoesNotSplit(Slime cube,Operation<Integer> original) { return !(cube instanceof R196Cube) && cube.isOnFire() && cube.isDeadOrDying() ? 1 : original.call(cube); }
    @Override public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) { return false; }
    @Override public void igniteForTicks(int ticks) { }
    @Override protected void dropFromLootTable(DamageSource source,boolean recent) {
        if (!((Object)this instanceof R196Cube)) R196Cube.sourceDrops((Slime)(Object)this,source,recent);
    }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        if (super.isInvulnerableTo(source)) return true;
        return !((Object)this instanceof R196Cube) && R196Cube.vanillaImmune((Slime)(Object)this,source);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        boolean hit=super.hurt(source,amount);
        if (hit && !((Object)this instanceof R196Cube) && !((Object)this instanceof MagmaCube) && source.getDirectEntity() instanceof Player player)
            MobCorrosion.equipment(player.getMainHandItem(),false,1,player,EquipmentSlot.MAINHAND);
        return hit;
    }
}
