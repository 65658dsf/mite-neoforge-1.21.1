package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Rejects invalid final harvests before tool wear, removal and drops. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class LogHarvestEvents {
    private LogHarvestEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player
                && LogHarvestRules.isBlocked(player, event.getState())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStart(PlayerInteractEvent.LeftClickBlock event) {
        if (event.isCanceled() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // This event precedes vanilla's range check. Do not load arbitrary requested chunks for a hint.
        if (player.canInteractWithBlock(event.getPos(), 1.0) && player.serverLevel().hasChunkAt(event.getPos())
                && LogHarvestRules.isBlocked(player, player.serverLevel().getBlockState(event.getPos()))) {
            player.displayClientMessage(Component.translatable("message.mite.log_requires_axe"), true);
        }
        // START is feedback only; ABORT/STOP retain vanilla cleanup and the final break event is authoritative.
    }
}
