package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import java.util.stream.Stream;

/** Retain the registry identity for save compatibility, disable its normal use. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class DisabledVanillaWorkbench {
    private DisabledVanillaWorkbench() {}
    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, DisabledVanillaWorkbench::creative);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        Stream.concat(event.getParentEntries().stream(), event.getSearchEntries().stream())
                .filter(stack -> stack.is(Items.CRAFTING_TABLE)).toList()
                .forEach(stack -> event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
    }
    @SubscribeEvent
    public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        if (event.getLevel().getBlockState(event.getPos()).is(Blocks.CRAFTING_TABLE)
                || event.getItemStack().is(Items.CRAFTING_TABLE)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            if (!event.getLevel().isClientSide()) event.getEntity().displayClientMessage(
                    Component.translatable("message.mite.vanilla_workbench_disabled"), true);
        }
    }
}
