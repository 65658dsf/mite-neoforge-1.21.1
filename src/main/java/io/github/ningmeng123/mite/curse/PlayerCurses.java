package io.github.ningmeng123.mite.curse;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.CurseNotificationPayload;
import io.github.ningmeng123.mite.network.CurseStatePayload;
import io.github.ningmeng123.mite.network.RevealCursePayload;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.utility.PlayerDisenchantingEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Authoritative lifecycle and discovery, separate from removable potion effects. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PlayerCurses {
    public static final String NOTIFICATION = "mite:curse_notification";
    private static final String LAST_REVEAL = "mite:curse_last_reveal";
    private PlayerCurses() {}

    public static CurseState state(Player player) { return player.getData(MiteAttachments.CURSE_STATE); }
    public static boolean has(Player player, R196Curse type) { return has(player, type, false); }
    public static boolean has(Player player, R196Curse type, boolean learn) {
        if (state(player).id() != type.id()) return false;
        if (learn) reveal(player);
        return true;
    }

    public static boolean schedule(ServerPlayer player, Witch witch, R196Curse type, int delay) {
        if (!witch.isAlive() || player.isDeadOrDying() || type == null || delay < 0) return false;
        var data = WitchCurseData.get(player.serverLevel());
        return data.add(new WitchCurseData.Entry(player.getUUID(), player.getGameProfile().getName(), witch.getUUID(), type,
                player.server.overworld().getGameTime() + delay, false, false));
    }

    public static void reconcile(ServerPlayer player) {
        reconcile(player, false);
    }

    private static void reconcile(ServerPlayer player, boolean forceSync) {
        var data = WitchCurseData.get(player.serverLevel());
        var entry = data.entry(player.getUUID());
        boolean realizedNow = entry != null && !entry.realized()
                && entry.realizationTime() <= player.server.overworld().getGameTime();
        if (realizedNow && player.isAlive()) {
            entry = entry.realize();
            data.replace(entry);
        }
        CurseState before = state(player);
        CurseState next = entry != null && entry.realized() ? new CurseState(entry.type().id(), entry.known()) : CurseState.NONE;
        if (!before.equals(next)) player.setData(MiteAttachments.CURSE_STATE, next);
        if (forceSync || !before.equals(next)) CurseStatePayload.send(player, next);
        if (realizedNow && entry.realized()) {
            CurseNotificationPayload.send(player, true);
            CursePlayerEffects.realize(player, entry.type());
        } else if (before.active() && !next.active()) {
            CurseNotificationPayload.send(player, false);
        }
    }

    public static void reveal(Player player) {
        if (!state(player).active() || state(player).known()) return;
        if (player instanceof ServerPlayer server) {
            var data = WitchCurseData.get(server.serverLevel());
            var entry = data.entry(server.getUUID());
            if (entry == null || !entry.realized() || entry.known()) return;
            data.replace(entry.learn());
            server.setData(MiteAttachments.CURSE_STATE, new CurseState(entry.type().id(), true));
            CurseStatePayload.send(server, state(server));
            server.serverLevel().sendParticles(ParticleTypes.WITCH, server.getX(), server.getY() + 1, server.getZ(),
                    30, .4, .6, .4, 0);
        } else if (player.level().isClientSide) {
            var tag = player.getPersistentData();
            if (!tag.contains(LAST_REVEAL) || player.tickCount - tag.getInt(LAST_REVEAL) >= 10) {
                tag.putInt(LAST_REVEAL, player.tickCount);
                RevealCursePayload.send(state(player).id());
            }
        }
    }

    public static boolean cure(ServerPlayer player) {
        boolean removed = WitchCurseData.get(player.serverLevel()).remove(player.getUUID());
        reconcile(player);
        return removed;
    }
    public static void removeForWitch(Witch witch) {
        if (witch.level().isClientSide || witch.getServer() == null) return;
        var server = witch.getServer();
        if (WitchCurseData.get(server.overworld()).removeForWitch(witch.getUUID())) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) reconcile(player);
        }
    }
    public static int notificationTicks(Player player) {
        var tag = player.getPersistentData().getCompound(NOTIFICATION);
        return Math.clamp((long) tag.getInt("until") - player.tickCount, 0, 100);
    }
    public static boolean notificationActive(Player player) {
        return player.getPersistentData().getCompound(NOTIFICATION).getBoolean("active");
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) reconcile(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reconcile(player, true);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reconcile(player, true);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reconcile(player, true);
    }
    @SubscribeEvent public static void drink(PlayerDisenchantingEvent event) { cure(event.player()); }
}
