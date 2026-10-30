package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.network.ServerClockStatus;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Decorate before NeoForge encodes its cached status JSON. @author NingMeng123 */
@Mixin(MinecraftServer.class)
public abstract class ServerClockStatusMixin {
    @Inject(method = "buildServerStatus", at = @At("RETURN"), cancellable = true)
    private void mite$publishWorldClock(CallbackInfoReturnable<ServerStatus> cir) {
        var level = ((MinecraftServer)(Object)this).overworld();
        if (level != null) cir.setReturnValue(ServerClockStatus.withClock(cir.getReturnValue(), level.getDayTime()));
    }
}
