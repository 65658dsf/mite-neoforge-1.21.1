package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.entity.animal.SourceAquatic032;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Keep ro ordinary hanging and negative-height flight separate from 031 vampire/nightwing AI. @author NingMeng123 */
@Mixin(Bat.class) public abstract class NativeBat032Mixin {
    @Inject(method="<init>",at=@At("RETURN")) private void mite$sourceInitiallyFlying(CallbackInfo ci) { var bat=(Bat)(Object)this; if(bat.getType()==EntityType.BAT && !bat.level().isClientSide) bat.setResting(false); }
    @Inject(method="customServerAiStep",at=@At("HEAD"),cancellable=true) private void mite$sourceHanging(CallbackInfo ci) { var bat=(Bat)(Object)this; if(bat.getType()==EntityType.BAT) { SourceAquatic032.batAi(bat); ci.cancel(); } }
    @Inject(method="tick",at=@At("RETURN")) private void mite$sourceHangingPosition(CallbackInfo ci) { SourceAquatic032.batTick((Bat)(Object)this); }
    @Inject(method="setResting",at=@At("HEAD")) private void mite$sourceDetachCooldown(boolean resting,CallbackInfo ci) {
        var bat=(Bat)(Object)this;
        if(bat.getType()==EntityType.BAT && !bat.level().isClientSide && bat.isResting() && !resting)
            bat.getPersistentData().putInt("mite_bat_prevent_hang_032",60);
    }
}
