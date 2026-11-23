package io.github.ningmeng123.mite.compat.bettercombat;

import io.github.ningmeng123.mite.registry.MiteAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** The player's saved MITE combat preference, shared with tracking clients. @author NingMeng123 */
public final class BetterCombatPlayerMode {
    private BetterCombatPlayerMode() {}

    public static boolean isVanilla(Player player) {
        return BetterCombatBridge.enabled()
                && Boolean.TRUE.equals(player.getExistingDataOrNull(MiteAttachments.VANILLA_COMBAT));
    }

    public static void setVanilla(ServerPlayer player, boolean vanilla) {
        if (!BetterCombatBridge.enabled()) return;
        if (vanilla) player.setData(MiteAttachments.VANILLA_COMBAT, true);
        else player.removeData(MiteAttachments.VANILLA_COMBAT);
    }

    public static boolean attacksDisabled(Player player) {
        return BetterCombatBridge.enabled() && Loaded.attacksDisabled(player);
    }

    private static final class Loaded {
        private static boolean attacksDisabled(Player player) {
            return net.bettercombat.api.CombatFlags.isAttackDisabled(player);
        }
    }
}
