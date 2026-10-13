package io.github.ningmeng123.mite.survival;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** A scoped, server-validated exception to the egg's default food interaction. @author NingMeng123 */
public final class EggThrowControl {
    private static final ThreadLocal<Player> THROWING = new ThreadLocal<>();
    private EggThrowControl() {}

    public static boolean isThrowing(Player player) {
        return player != null && THROWING.get() == player;
    }

    public static InteractionResult throwEgg(ServerPlayer player, InteractionHand hand) {
        return throwEgg(player, hand, player.getYRot(), player.getXRot());
    }

    public static InteractionResult throwEgg(ServerPlayer player, InteractionHand hand, float yRot, float xRot) {
        if (hand == null || !player.isAlive() || player.isSpectator() || player.isSleeping()
                || player.isUsingItem() || !Float.isFinite(yRot) || !Float.isFinite(xRot)) return InteractionResult.PASS;
        var stack = player.getItemInHand(hand);
        if (!stack.is(Items.EGG) || stack.isEmpty() || player.getCooldowns().isOnCooldown(Items.EGG)
                || !stack.isItemEnabled(player.serverLevel().enabledFeatures())) return InteractionResult.PASS;
        player.resetLastActionTime();
        // Match vanilla ServerboundUseItemPacket rotation semantics so a click after
        // turning the camera does not throw along the previous server heading.
        player.absRotateTo(Mth.wrapDegrees(yRot), Mth.wrapDegrees(xRot));
        Player previous = THROWING.get();
        THROWING.set(player);
        try {
            // Keep native egg spawning, consumption, creative behavior, statistics,
            // inventory synchronization and NeoForge RightClickItem cancellation.
            return player.gameMode.useItem(player, player.serverLevel(), stack, hand);
        } finally {
            if (previous == null) THROWING.remove();
            else THROWING.set(previous);
        }
    }
}
