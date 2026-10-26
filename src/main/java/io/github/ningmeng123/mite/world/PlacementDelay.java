package io.github.ningmeng123.mite.world;

import com.google.common.collect.MapMaker;
import io.github.ningmeng123.mite.Mite;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Successful placement starts a shared eight-tick cooldown without queuing future actions. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PlacementDelay {
    public static final int DELAY_TICKS = 8;
    // Entity equality uses its network ID, shared by the integrated client and server.
    // Weak identity keys keep their deadlines separate without retaining old players.
    private static final Map<Player, Cooldown> COOLDOWNS = new MapMaker().weakKeys().makeMap();
    private static final ThreadLocal<Attempt> ATTEMPT = new ThreadLocal<>();

    private record Cooldown(WeakReference<Level> level, long due) {}
    private static final class Attempt {
        private final Player player;
        private boolean placed;
        private Attempt(Player player) { this.player = player; }
    }

    private PlacementDelay() {}

    public static int remainingTicks(@Nullable Player player) {
        if (player == null || player.hasInfiniteMaterials() || player.isSpectator()) return 0;
        Cooldown cooldown = COOLDOWNS.get(player);
        if (cooldown == null) return 0;
        long remaining = cooldown.due() - now(player);
        if (cooldown.level().get() != player.level() || remaining <= 0 || remaining > DELAY_TICKS) {
            COOLDOWNS.remove(player);
            return 0;
        }
        return (int) remaining;
    }

    public static boolean blocked(@Nullable Player player) { return remainingTicks(player) > 0; }

    public static void placed(@Nullable Player player) {
        if (player != null && !player.hasInfiniteMaterials() && !player.isSpectator())
            COOLDOWNS.put(player, new Cooldown(new WeakReference<>(player.level()), now(player) + DELAY_TICKS));
    }

    public static InteractionResult useOn(@Nullable Player player, Supplier<InteractionResult> action) {
        Attempt previous = ATTEMPT.get();
        Attempt current = new Attempt(player);
        ATTEMPT.set(current);
        try {
            InteractionResult result = action.get();
            if (current.placed && result.consumesAction()) placed(player);
            return result;
        } finally {
            if (previous == null) ATTEMPT.remove();
            else ATTEMPT.set(previous);
        }
    }

    public static void blockPlaced(@Nullable Player player, Level level) {
        Attempt attempt = ATTEMPT.get();
        if (!level.isClientSide && level.captureBlockSnapshots && attempt != null && attempt.player == player)
            attempt.placed = true;
        else if (!level.captureBlockSnapshots || level.isClientSide) placed(player);
    }

    private static long now(Player player) {
        return player instanceof ServerPlayer server ? server.server.overworld().getGameTime() : player.level().getGameTime();
    }

    public static void clear(Player player) { COOLDOWNS.remove(player); }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        clear(event.getOriginal());
        clear(event.getEntity());
    }
}
