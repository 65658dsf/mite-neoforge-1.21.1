package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.LiquidJumpInputPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Source liquid-jump input changes are sent immediately, with a bounded held-input keepalive. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID,value=Dist.CLIENT)
public final class LiquidJumpInput {
    private static boolean previous;
    private static int heartbeat;
    private static Object previousPlayer;
    private LiquidJumpInput() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null || mc.getConnection()==null) { previous=false;heartbeat=0;previousPlayer=null;return; }
        if(previousPlayer!=player) { previous=false;heartbeat=0;previousPlayer=player; }
        boolean held=!mc.isPaused() && mc.screen==null && player.input.jumping && !player.isPassenger()
                && !player.isSpectator() && !player.getAbilities().invulnerable && (player.isInWater() || player.isInLava());
        if(held!=previous || held && ++heartbeat>=10) {
            PacketDistributor.sendToServer(new LiquidJumpInputPayload(held));previous=held;heartbeat=0;
        }
    }
}
