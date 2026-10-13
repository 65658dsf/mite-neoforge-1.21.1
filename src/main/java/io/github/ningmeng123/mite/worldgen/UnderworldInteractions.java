package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** R196 anb rejects underground sleep instead of exploding the bed. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class UnderworldInteractions {
    private UnderworldInteractions() {}
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void bed(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (event.isCanceled() || event.getUseBlock().isFalse()
                || !(level.dimension().equals(MiteWorldDimensions.UNDERWORLD) || level.dimension().equals(Level.NETHER))
                || !(level.getBlockState(event.getPos()).getBlock() instanceof BedBlock)) return;
        var player = event.getEntity();
        boolean hasItem = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        boolean bypass = player.getMainHandItem().doesSneakBypassUse(level, event.getPos(), player)
                && player.getOffhandItem().doesSneakBypassUse(level, event.getPos(), player);
        if (!event.getUseBlock().isTrue() && player.isSecondaryUseActive() && hasItem && !bypass) return;
        if (!level.isClientSide) event.getEntity().displayClientMessage(Component.translatable("message.mite.underground_sleep_denied"), true);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
