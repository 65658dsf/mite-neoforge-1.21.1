package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Explain why wood cannot be processed in the inventory grid. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class CraftingTooltips {
    private CraftingTooltips() {}

    @SubscribeEvent
    public static void explainEquipment(ItemTooltipEvent event) {
        if (event.getItemStack().is(ItemTags.LOGS) || event.getItemStack().is(ItemTags.BAMBOO_BLOCKS)) {
            event.getToolTip().add(Component.translatable("tooltip.mite.planks_require_workbench")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
