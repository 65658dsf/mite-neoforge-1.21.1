package io.github.ningmeng123.mite.compat.bettercombat;

import io.github.ningmeng123.mite.config.MiteConfig;
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

    public static float attackInterval(Player player, float ticks) {
        if (!BetterCombatBridge.enabled() || Loaded.attacksDisabled(player)) return ticks;
        boolean betterCombatAttack = player.level().isClientSide
                ? ClientLoaded.hasWeapon(player) : BetterCombatPlayerReach.isAttackInProgress(player);
        return betterCombatAttack ? ticks / (float)MiteConfig.betterCombatAttackSpeedMultiplier() : ticks;
    }

    private static final class Loaded {
        private static boolean attacksDisabled(Player player) {
            return net.bettercombat.api.CombatFlags.isAttackDisabled(player);
        }
    }

    private static final class ClientLoaded {
        private static boolean hasWeapon(Player player) {
            if (!(player instanceof net.minecraft.client.player.LocalPlayer)
                    || !net.bettercombat.client.BetterCombatClientMod.ENABLED) return false;
            var attributes = net.bettercombat.logic.WeaponRegistry.getAttributes(player.getMainHandItem());
            return attributes != null && attributes.attacks() != null;
        }
    }
}
