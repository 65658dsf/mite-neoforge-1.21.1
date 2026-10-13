package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteTools;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Enforce the declared R196 tool tiers at progress and final server removal. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class ToolHarvestRules {
    private ToolHarvestRules() {}
    public static boolean isBlocked(Player player, BlockState state) {
        return !player.isCreative() && !player.isSpectator() && state.requiresCorrectToolForDrops()
                && MiteTools.definition(player.getMainHandItem()) != null
                && !player.getMainHandItem().isCorrectToolForDrops(state);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void harvest(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && isBlocked(event.getPlayer(), event.getState())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hint(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.isCanceled() && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && event.getEntity() instanceof ServerPlayer player && player.canInteractWithBlock(event.getPos(), 1.0)
                && player.serverLevel().hasChunkAt(event.getPos())
                && isBlocked(player, player.serverLevel().getBlockState(event.getPos()))) {
            player.displayClientMessage(Component.translatable("message.mite.tool_tier_too_low"), true);
        }
    }
}
