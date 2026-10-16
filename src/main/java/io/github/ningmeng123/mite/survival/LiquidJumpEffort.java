package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Only upward-input intent crosses the network; liquid eligibility, amount and cadence remain server-owned. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class LiquidJumpEffort {
    private static final Map<ServerPlayer, Input> INPUTS = new WeakHashMap<>();
    private static final class Input { boolean held; long refreshed; long charged = Long.MIN_VALUE; }
    private LiquidJumpEffort() {}
    private static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }
    public static void setInput(ServerPlayer player, boolean held) {
        Input input=INPUTS.get(player);
        if (input==null) { if (!held) return; input=new Input(); INPUTS.put(player,input); }
        input.held=held; input.refreshed=now(player);
    }
    public static void tick(ServerPlayer player) {
        Input input=INPUTS.get(player);long now=now(player);
        if (input==null || !input.held || now-input.refreshed>40L || input.charged==now) return;
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable
                || player.isPassenger() || !(player.isInWater() || player.isInLava())) return;
        input.charged=now;player.causeFoodExhaustion(.01F);
    }
    /** Clear intent on dimension changes without permitting a second charge during the same server tick. */
    public static void clear(Player player) {
        if (player instanceof ServerPlayer server) { Input input=INPUTS.get(server); if(input!=null) input.held=false; }
    }
    @SubscribeEvent public static void post(PlayerTickEvent.Post event) { if(event.getEntity() instanceof ServerPlayer player) tick(player); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if(event.getEntity() instanceof ServerPlayer p) INPUTS.remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { if(event.getOriginal() instanceof ServerPlayer p) INPUTS.remove(p); clear(event.getEntity()); }
}
