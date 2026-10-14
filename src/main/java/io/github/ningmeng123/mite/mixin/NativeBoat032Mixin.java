package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.entity.animal.SourceAquatic032;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** sq six squid contacts destroy its targeted rider's boat; persisted count decays every200offsetticks. @author NingMeng123 */
@Mixin(Boat.class) public abstract class NativeBoat032Mixin {
    @Inject(method="push",at=@At("HEAD")) private void mite$sourceSquidBoat(Entity entity,CallbackInfo ci) { SourceAquatic032.boatCollision((Boat)(Object)this,entity); }
    @Inject(method="tick",at=@At("RETURN")) private void mite$sourceContactDecay(CallbackInfo ci) { Boat boat=(Boat)(Object)this; if(!boat.level().isClientSide && (boat.tickCount+boat.getId()*47)%200==0) { int value=boat.getPersistentData().getInt("mite_squid_collisions_032"); if(value>0) boat.getPersistentData().putInt("mite_squid_collisions_032",value-1); } }
}
