package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Explain source attachment and operation requirements before the player places a metal door. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class MaterialDoorTooltips {
    private MaterialDoorTooltips() {}

    @SubscribeEvent
    public static void explain(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof BlockItem item && MiteDecorativeContent.isMetalDoor(item.getBlock())) {
            event.getToolTip().add(Component.translatable("tooltip.mite.metal_door.frame").withStyle(ChatFormatting.GRAY));
            String opening = MiteDecorativeContent.isCopperDoor(item.getBlock())
                    ? "tooltip.mite.copper_door.manual" : "tooltip.mite.metal_door.redstone";
            event.getToolTip().add(Component.translatable(opening).withStyle(ChatFormatting.GRAY));
        }
    }
}
