package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.RespawnCountdownPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** jv/js/avc: a saved automatic-respawn timer, not a manual-respawn lock. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class RespawnCountdown {
    public static final String KEY = "mite_respawn_countdown";
    private RespawnCountdown() {}
    public static int remaining(Player player) { return player.getPersistentData().contains(KEY) ? Math.clamp(player.getPersistentData().getInt(KEY), 0, 120) : 120; }
    public static void died(ServerPlayer player) {
        player.getPersistentData().putInt(KEY, 120);
        send(player);
    }
    public static boolean paused(ServerPlayer player) {
        boolean sleeping = false;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (!other.isAlive() || other.isSpectator()) continue;
            if (!other.isSleepingLongEnough()) return false;
            sleeping = true;
        }
        return sleeping;
    }
    public static int advance(int seconds, long worldTime, boolean paused) {
        return seconds > 0 && !paused && Math.floorMod(worldTime, 20L) == 0 ? seconds - 1 : seconds;
    }
    private static void send(ServerPlayer player) { RespawnCountdownPayload.send(player, remaining(player)); }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(event.getEntity() instanceof ServerPlayer player && !player.isAlive())
            process(player,player.level().getGameTime(),paused(player));
    }
    public static void process(ServerPlayer player,long gameTime,boolean paused) {
        if (player.isAlive() || player.isSpectator()
                || player.serverLevel().getLevelData().isHardcore()) return;
        int before = remaining(player), after = advance(before, gameTime, paused);
        if (before != after || !player.getPersistentData().contains(KEY)) {
            player.getPersistentData().putInt(KEY, after);
            send(player);
        }
        if (after == 0 && player.connection != null && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            // Native respawn keeps keepInventory, the death XP queue, bed validation and all clone events.
            ServerPlayer replacement = player.server.getPlayerList().respawn(player, false,
                    net.minecraft.world.entity.Entity.RemovalReason.KILLED);
            player.connection.player = replacement;
            replacement.getPersistentData().remove(KEY);
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        event.getEntity().getPersistentData().remove(KEY);
        if (event.getEntity() instanceof ServerPlayer player) RespawnCountdownPayload.send(player, -1);
    }
}
