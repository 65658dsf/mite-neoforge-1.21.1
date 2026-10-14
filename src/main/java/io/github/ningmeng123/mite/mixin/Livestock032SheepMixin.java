package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.LivestockGrazingGoal032;
import io.github.ningmeng123.mite.entity.animal.LivestockSheep032View;
import net.minecraft.world.entity.animal.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** rz.n: actual grazing restores half food, wool and exactly three hundred baby age ticks. @author NingMeng123 */
@Mixin(Sheep.class)
public abstract class Livestock032SheepMixin implements LivestockSheep032View {
    @Shadow private int eatAnimationTick;
    @Unique private int mite$woolFireDamage032;
    @Override public int mite$woolFireDamage() { return mite$woolFireDamage032; }
    @Override public void mite$setWoolFireDamage(int amount) { mite$woolFireDamage032=Math.max(0,amount); }
    @Inject(method="ate",at=@At("HEAD"),cancellable=true)
    private void mite$sourceAte(CallbackInfo ci) {
        Sheep sheep=(Sheep)(Object)this;
        if(!sheep.level().isClientSide) { sheep.setSheared(false); if(sheep.isBaby()) sheep.ageUp(60); Livestock032.needs(sheep,Livestock032.needs(sheep).feed()); }
        ci.cancel();
    }
    @Inject(method="customServerAiStep",at=@At("TAIL"))
    private void mite$sourceEatingAnimation(CallbackInfo ci) {
        Sheep sheep=(Sheep)(Object)this;
        eatAnimationTick=sheep.goalSelector.getAvailableGoals().stream().map(w->w.getGoal()).filter(LivestockGrazingGoal032.class::isInstance)
                .map(LivestockGrazingGoal032.class::cast).mapToInt(LivestockGrazingGoal032::eatingTicks).findFirst().orElse(0);
    }
}
