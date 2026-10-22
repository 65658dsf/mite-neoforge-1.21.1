package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import io.github.ningmeng123.mite.entity.animal.R196AnimalAi032;
import io.github.ningmeng123.mite.api.MiteExtensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source breeding gates and cooldown use the same server state as resource-seeking and loot. @author NingMeng123 */
@Mixin(Animal.class)
public abstract class Livestock032AnimalMixin {
    @WrapOperation(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/animal/Animal;resetLove()V"))
    private void mite$loveAfterDamageTransaction(Animal animal,Operation<Void> original) {
        // Native Animal clears love before NeoForge can cancel/zero damage; source checks the actual result.
        if(!R196AnimalAi032.managed(animal)) original.call(animal);
    }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void mite$transientPanic(net.minecraft.nbt.CompoundTag tag,CallbackInfo ci) {
        Animal animal=(Animal)(Object)this;
        if(R196AnimalAi032.managed(animal)) { var state=R196AnimalAi032.state(animal); state.remove("panic_countdown"); state.remove("done_panicking"); }
    }
    @Inject(method="setInLove",at=@At("HEAD"),cancellable=true)
    private void mite$healthyLove(Player player,CallbackInfo ci) {
        Animal animal=(Animal)(Object)this;
        if(!animal.level().isClientSide && R196AnimalAi032.managed(animal) && (animal.getHealth()<animal.getMaxHealth()
                || Livestock032.sourceLivestock(animal) && !Livestock032.well(animal))) ci.cancel();
    }
    @Inject(method="mobInteract",at=@At("HEAD"),cancellable=true)
    private void mite$sourceFood(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir) {
        Animal animal=(Animal)(Object)this;
        if(!Livestock032.sourceLivestock(animal)) return;
        var stack=player.getItemInHand(hand); if(!Livestock032.foodItem(animal,stack) && !animal.isFood(stack)) return;
        boolean accept=Livestock032.shouldEat(animal,stack);
        if(accept && !animal.level().isClientSide) { Livestock032.onFoodEaten(animal,stack); stack.consume(1,player); }
        cir.setReturnValue(accept?InteractionResult.sidedSuccess(animal.level().isClientSide):InteractionResult.PASS);
    }
    @ModifyConstant(method={"spawnChildFromBreeding","finalizeSpawnChildFromBreeding"},constant=@Constant(intValue=6000))
    private int mite$sourceCooldown(int original) { return LivestockRules032.BREEDING_COOLDOWN; }
    @ModifyArg(method="finalizeSpawnChildFromBreeding",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/ExperienceOrb;<init>(Lnet/minecraft/world/level/Level;DDDI)V"),index=4)
    private int mite$extensionBreedingExperience(int original) { return Math.max(0,MiteExtensions.policy().breedingExperience((Animal)(Object)this,original)); }
    @Inject(method="finalizeSpawnChildFromBreeding",at=@At("TAIL"))
    private void mite$sourceNewborn(ServerLevel level,Animal other,AgeableMob child,CallbackInfo ci) {
        if(child!=null && R196AnimalAi032.managed(child)) child.setAge(LivestockRules032.NEWBORN_AGE);
    }
    @Inject(method="getWalkTargetValue",at=@At("HEAD"),cancellable=true)
    private void mite$sourceFoodPreference(BlockPos pos,LevelReader level,CallbackInfoReturnable<Float> cir) {
        Animal animal=(Animal)(Object)this;
        if(Livestock032.sourceLivestock(animal) && (Livestock032.foodBlock(animal,level.getBlockState(pos)) || Livestock032.foodBlock(animal,level.getBlockState(pos.below()))
                || Livestock032.isNearFood(animal,pos) || !Livestock032.waterBlock(animal,pos) && !Livestock032.waterBlock(animal,pos.below()) && Livestock032.isNearWater(animal,pos))) cir.setReturnValue(20F);
    }
}
