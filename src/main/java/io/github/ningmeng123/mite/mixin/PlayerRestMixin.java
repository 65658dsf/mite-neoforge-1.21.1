package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.survival.PlayerRest;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** An occupied bed is not deep sleep until the source consciousness reaches 100. @author NingMeng123 */
@Mixin(Player.class)
public abstract class PlayerRestMixin {
    @Inject(method="isSleepingLongEnough",at=@At("HEAD"),cancellable=true)
    private void mite$sourceConsciousness(CallbackInfoReturnable<Boolean> cir) {
        Player player=(Player)(Object)this;
        if(!player.level().isClientSide) cir.setReturnValue(player.isSleeping() && PlayerRest.counter(player)>=100);
    }
}
