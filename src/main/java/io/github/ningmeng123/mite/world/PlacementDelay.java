package io.github.ningmeng123.mite.world;

import com.google.common.collect.MapMaker;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.PlacementCooldown;
import java.lang.ref.WeakReference;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** R196's real-time client input lock and the shared survival hunger gate. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PlacementDelay {
    // Compatibility for callers displaying the duration at the normal 20 TPS.
    public static final int DELAY_TICKS = (int) (PlacementCooldown.BLOCK_MILLIS / 50L);
    // Identity keys prevent reused network IDs from inheriting another player's input lock.
    private static final Map<Player, Cooldown> COOLDOWNS = new MapMaker().weakKeys().makeMap();

    private record Cooldown(WeakReference<Level> level, PlacementCooldown timer) {}

    private PlacementDelay() {}

    public static int remainingTicks(@Nullable Player player) {
        return (int) ((remainingMillis(player) + 49L) / 50L);
    }

    public static long remainingMillis(@Nullable Player player) {
        if (exempt(player)) return 0L;
        Cooldown cooldown = COOLDOWNS.get(player);
        if (cooldown == null) return 0L;
        long remaining = cooldown.timer().remainingMillis(Util.getMillis());
        if (cooldown.level().get() != player.level() || remaining == 0L) {
            COOLDOWNS.remove(player, cooldown);
            return 0L;
        }
        return remaining;
    }

    public static boolean blocked(@Nullable Player player) { return remainingMillis(player) > 0L; }

    public static boolean placementBlocked(@Nullable Player player) {
        return player != null && !player.isSpectator()
                && ((!player.hasInfiniteMaterials() && player.getFoodData().getFoodLevel() <= 0) || blocked(player));
    }

    public static void placed(@Nullable Player player) {
        placed(player, false);
    }

    public static void bucketPlaced(@Nullable Player player) {
        placed(player, true);
    }

    private static void placed(@Nullable Player player, boolean bucket) {
        if (!exempt(player)) {
            COOLDOWNS.put(player, new Cooldown(new WeakReference<>(player.level()),
                    PlacementCooldown.afterPlacement(Util.getMillis(), bucket)));
        }
    }

    private static boolean exempt(@Nullable Player player) {
        // The source locks creative input too. Packet timing is left to the client,
        // avoiding false rejections when network or server scheduling compresses arrivals.
        return player == null || player.isSpectator() || !player.level().isClientSide;
    }

    public static void blockPlaced(@Nullable Player player, Level level) {
        blockPlaced(player, level, false);
    }

    public static void blockPlaced(@Nullable Player player, Level level, boolean bucket) {
        if (level.isClientSide) placed(player, bucket);
    }

    public static void clear(Player player) { COOLDOWNS.remove(player); }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        clear(event.getOriginal());
        clear(event.getEntity());
    }
}
