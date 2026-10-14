package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.network.VisionDimmingPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Source pending shadows combine per tick; the received effect takes max, then fades by .01 each tick. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class VisionDimming {
    public static final String VALUE = "mite_vision_dimming_031", PENDING = "mite_vision_pending_031";
    private VisionDimming() {}
    public static void apply(Player player, float amount) {
        if (player.level().isClientSide) return;
        var tag = player.getPersistentData();
        tag.putFloat(PENDING, Math.max(0F, tag.getFloat(PENDING)) + amount * (1F - MobCombat.silverResistance(player)));
    }
    public static float value(Player player) { return nonnegative(player.getPersistentData().getFloat(VALUE)); }
    private static float nonnegative(float value) { return Float.isFinite(value) ? Math.max(0F, value) : 0F; }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var tag = player.getPersistentData();
        float before = value(player), pending = tag.getFloat(PENDING);
        float next = Math.max(Math.max(0F, before - .01F), nonnegative(pending));
        tag.putFloat(VALUE, next); tag.remove(PENDING);
        if (pending > 0F || before > 0F && (player.tickCount % 10 == 0 || next == 0F)) VisionDimmingPayload.send(player, next);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) VisionDimmingPayload.send(player, value(player));
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) VisionDimmingPayload.send(player, value(player));
    }
}
