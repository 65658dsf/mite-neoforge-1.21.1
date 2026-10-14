package io.github.ningmeng123.mite.ranged;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/** Constructor context follows the actual rod-use hand and never leaks outside that item-use call. @author NingMeng123 */
public final class FishingCastContext {
    public record Cast(Player player, InteractionHand hand) {}
    private static final ThreadLocal<Cast> ACTIVE = new ThreadLocal<>();
    private FishingCastContext() {}
    public static Cast begin(Player player, InteractionHand hand) {
        Cast previous = ACTIVE.get(); ACTIVE.set(new Cast(player, hand)); return previous;
    }
    public static void end(Cast previous) { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    public static InteractionHand hand(Player player) {
        Cast current = ACTIVE.get();
        return current != null && current.player() == player ? current.hand() : null;
    }
}
