package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.registry.MiteTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;

/** Shared log/tool eligibility without player strength events or mining side effects. @author NingMeng123 */
public final class LogHarvestRules {
    private LogHarvestRules() {
    }

    public static boolean isBlocked(Player player, BlockState state) {
        return !player.isCreative() && !player.isSpectator()
                && state.is(MiteTags.LOGS_REQUIRING_AXE)
                && !hasQualifiedAxe(player.getMainHandItem(), state);
    }

    public static boolean hasQualifiedAxe(ItemStack stack, BlockState state) {
        return !stack.isEmpty() && !stack.is(MiteTags.BELOW_LOG_MINING_TIER)
                && stack.canPerformAction(ItemAbilities.AXE_DIG)
                && stack.getDestroySpeed(state) > 1.0F
                && stack.isCorrectToolForDrops(state);
    }
}
