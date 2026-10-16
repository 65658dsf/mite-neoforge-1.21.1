package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Short-lived, single-use Ctrl intent bound to a real player's hand and use sequence. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class BucketPlacementIntent {
    private static final long MAX_AGE_TICKS = 5L;
    private static final Map<ServerPlayer, Intent> PENDING = new WeakHashMap<>();

    private record Intent(InteractionHand hand, int sequence, ResourceKey<Level> dimension,
                          long receivedAt, ItemStack stack, int selectedSlot) {}

    private BucketPlacementIntent() {}

    /** Milk, fish, empty buckets and bowls retain their existing interactions. */
    public static boolean isPlacementBucket(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET)) return true;
        if (!(stack.getItem() instanceof MiteBucketItem bucket)) return false;
        return bucket.contents() == MiteBucketItem.Contents.WATER || bucket.contents() == MiteBucketItem.Contents.LAVA;
    }

    public static void receive(ServerPlayer player, InteractionHand hand, int sequence, boolean sourceRequested) {
        PENDING.remove(player);
        if (!sourceRequested || hand == null || sequence < 0 || !eligible(player)) return;
        ItemStack stack = player.getItemInHand(hand);
        if (!isPlacementBucket(stack)) return;
        PENDING.put(player, new Intent(hand, sequence, player.level().dimension(), now(player), stack,
                hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : -1));
    }

    /** A mismatch also consumes the request, so no failed or intercepted use can arm a later placement. */
    public static boolean consume(ServerPlayer player, InteractionHand hand, int sequence) {
        Intent intent = PENDING.remove(player);
        if (intent == null || intent.hand() != hand || intent.sequence() != sequence || !eligible(player)) return false;
        long age = now(player) - intent.receivedAt();
        if (age < 0L || age > MAX_AGE_TICKS || intent.dimension() != player.level().dimension()) return false;
        ItemStack stack = player.getItemInHand(hand);
        if (stack != intent.stack() || !isPlacementBucket(stack)) return false;
        return hand != InteractionHand.MAIN_HAND || player.getInventory().selected == intent.selectedSlot();
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.hasInfiniteMaterials();
    }

    private static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }

    public static void clear(Player player) {
        if (player instanceof ServerPlayer server) PENDING.remove(server);
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        clear(event.getOriginal());
        clear(event.getEntity());
    }
}
