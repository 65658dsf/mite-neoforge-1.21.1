package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import io.github.ningmeng123.mite.entity.animal.Livestock032View;
import io.github.ningmeng123.mite.entity.animal.R196AnimalAi032;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** nk source age units, starvation gate, horse batching and cauldron emergence guard. @author NingMeng123 */
@Mixin(AgeableMob.class)
public abstract class Livestock032GrowthMixin implements Livestock032View {
    @Unique private static final EntityDataAccessor<Float> MITE_FOOD_032=SynchedEntityData.defineId(AgeableMob.class,EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Float> MITE_WATER_032=SynchedEntityData.defineId(AgeableMob.class,EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Float> MITE_FREEDOM_032=SynchedEntityData.defineId(AgeableMob.class,EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Integer> MITE_RECOVERY=SynchedEntityData.defineId(AgeableMob.class,EntityDataSerializers.INT);
    @Unique private net.minecraft.core.BlockPos mite$lastTrampled032=net.minecraft.core.BlockPos.ZERO;
    @Override public net.minecraft.core.BlockPos mite$lastTrampled() { return mite$lastTrampled032; }
    @Override public void mite$setLastTrampled(net.minecraft.core.BlockPos pos) { mite$lastTrampled032=pos.immutable(); }
    @Inject(method="defineSynchedData",at=@At("TAIL"))
    private void mite$wellnessWatcher(SynchedEntityData.Builder builder,CallbackInfo ci) { builder.define(MITE_FOOD_032,1F);builder.define(MITE_WATER_032,1F);builder.define(MITE_FREEDOM_032,1F);builder.define(MITE_RECOVERY,0); }
    @Override public int mite$recoveryTicks() { return ((AgeableMob)(Object)this).getEntityData().get(MITE_RECOVERY); }
    @Override public void mite$setRecoveryTicks(int ticks) { ((AgeableMob)(Object)this).getEntityData().set(MITE_RECOVERY,ticks); }
    @Override public LivestockRules032.Needs mite$livestockNeeds() {
        var data=((AgeableMob)(Object)this).getEntityData();return new LivestockRules032.Needs(data.get(MITE_FOOD_032),data.get(MITE_WATER_032),data.get(MITE_FREEDOM_032));
    }
    @Override public void mite$setLivestockNeeds(LivestockRules032.Needs needs) {
        var data=((AgeableMob)(Object)this).getEntityData();data.set(MITE_FOOD_032,needs.food());data.set(MITE_WATER_032,needs.water());data.set(MITE_FREEDOM_032,needs.freedom());
    }
    @ModifyVariable(method="setAge",at=@At("HEAD"),argsOnly=true)
    private int mite$growthGate(int age) {
        AgeableMob mob=(AgeableMob)(Object)this;
        if(age==6000 && mob.getAge()==0 && mob instanceof net.minecraft.world.entity.animal.Animal) age=LivestockRules032.BREEDING_COOLDOWN;
        if(mob.level().isClientSide || !R196AnimalAi032.managed(mob) || !mob.isBaby()) return age;
        if(age>mob.getAge() && Livestock032.sourceLivestock(mob) && (Livestock032.isDesperateForFood(mob)||Livestock032.isDesperateForWater(mob))) return mob.getAge();
        var block=mob.level().getBlockState(mob.blockPosition());
        if(age>=0 && (block.is(Blocks.CAULDRON) || block.is(Blocks.WATER_CAULDRON))
                && mob.level().getBlockState(mob.blockPosition().above()).blocksMotion()) return -1;
        return age;
    }
    @WrapOperation(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/AgeableMob;setAge(I)V"))
    private void mite$horseGrowth(AgeableMob mob,int age,Operation<Void> original) {
        if(!mob.level().isClientSide && R196AnimalAi032.managed(mob) && mob instanceof AbstractHorse && mob.getAge()<0) {
            if(MobCombat.ticksWithOffset(mob)%200==0) original.call(mob,Math.min(0,mob.getAge()+200));
        } else original.call(mob,age);
    }
    @ModifyArg(method={"setBaby","finalizeSpawn"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/AgeableMob;setAge(I)V"),index=0)
    private int mite$newbornAge(int age) { return age<0 && R196AnimalAi032.managed((AgeableMob)(Object)this)?LivestockRules032.NEWBORN_AGE:age; }
    @Inject(method="ageUp(IZ)V",at=@At("HEAD"),cancellable=true)
    private void mite$sourceFeedingUnits(int seconds,boolean forced,CallbackInfo ci) {
        AgeableMob mob=(AgeableMob)(Object)this;
        if(!R196AnimalAi032.managed(mob) || mob.level().isClientSide) return;
        mob.setAge((int)Math.min(0L,(long)mob.getAge()+(long)seconds*5L)); ci.cancel();
    }
}
