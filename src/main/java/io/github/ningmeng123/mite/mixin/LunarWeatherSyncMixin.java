package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.world.LunarWeather;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native login and dimension snapshots omit thunder when no rain is falling. @author NingMeng123 */
@Mixin(PlayerList.class)
public abstract class LunarWeatherSyncMixin {
    @Inject(method = "sendLevelInfo", at = @At("TAIL"))
    private void mite$syncDryBloodThunder(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
        if (!LunarWeather.dryBloodStorm(level)) return;
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1F)));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1F)));
    }
}
