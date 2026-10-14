package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.entity.animal.NativeAnimalSpecial032;
import io.github.ningmeng123.mite.entity.animal.R196AnimalAi032;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.horse.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Apply rs hand-feeding/riding rejection before subclass-specific modern interaction paths. @author NingMeng123 */
@Mixin({AbstractHorse.class,Horse.class,AbstractChestedHorse.class,SkeletonHorse.class,ZombieHorse.class})
public abstract class NativeHorse032Mixin {
    @Inject(method="mobInteract",at=@At("HEAD"),cancellable=true,require=0)
    private void mite$sourceRebellion(Player p,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir) { var value=NativeAnimalSpecial032.horseInteract((AbstractHorse)(Object)this,p,hand); if(value!=null) cir.setReturnValue(value); }
    @Inject(method="handleEating",at=@At("HEAD"),cancellable=true,require=0)
    private void mite$sourceFoodTable(Player p,ItemStack food,CallbackInfoReturnable<Boolean> cir) { if(NativeAnimalSpecial032.horse((AbstractHorse)(Object)this)) cir.setReturnValue(NativeAnimalSpecial032.horseEat((AbstractHorse)(Object)this,p,food)); }
    @Inject(method="setEating",at=@At("HEAD"),cancellable=true,require=0)
    private void mite$sourceShyGrazing(boolean value,CallbackInfo ci) {
        AbstractHorse horse=(AbstractHorse)(Object)this; if(!value || horse.level().isClientSide || !NativeAnimalSpecial032.horse(horse)) return;
        if(horse.hurtTime>0 || R196AnimalAi032.state(horse).getBoolean("decided_to_flee")
                || horse.goalSelector.getAvailableGoals().stream().anyMatch(goal->goal.isRunning() && (goal.getGoal() instanceof io.github.ningmeng123.mite.entity.animal.AnimalCommon032Goals.AvoidPredators
                        || goal.getGoal() instanceof io.github.ningmeng123.mite.entity.animal.AnimalCommon032Goals.Flee))
                || NativeAnimalSpecial032.isShy(horse) && !horse.level().getEntitiesOfClass(Player.class,horse.getBoundingBox().inflate(16,2,16)).isEmpty()
                || !R196AnimalAi032.predatorsNear(horse,horse.getBoundingBox().inflate(16,2,16)).isEmpty()) ci.cancel();
    }
}
