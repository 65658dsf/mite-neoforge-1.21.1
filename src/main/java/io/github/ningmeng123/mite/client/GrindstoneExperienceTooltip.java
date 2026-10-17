package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.survival.GrindstoneExperienceAccess;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Adds the synchronized point cost only to the hovered grindstone result. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class GrindstoneExperienceTooltip {
    private GrindstoneExperienceTooltip() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || !(client.screen instanceof GrindstoneScreen screen) || client.player == null) return;
        Slot result = hoveredResult(screen);
        // Equality by instance keeps identical inventory items free of transaction-specific text.
        if (result == null || event.getItemStack() != result.getItem()) return;

        var experience = (GrindstoneExperienceAccess)screen.getMenu();
        int cost = experience.mite$experienceCost();
        appendCost(event.getToolTip(), cost, cost >= 0 && experience.mite$canTake(client.player));
    }

    private static void appendCost(List<Component> lines, int cost, boolean affordable) {
        if (cost < 0) {
            lines.add(Component.translatable("tooltip.mite.grindstone.pending")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        lines.add(Component.translatable("tooltip.mite.grindstone.cost", cost)
                .withStyle(affordable ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (!affordable) {
            lines.add(Component.translatable("tooltip.mite.grindstone.insufficient")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void renderWhileCarrying(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof GrindstoneScreen screen) || screen.getMenu().getCarried().isEmpty()) return;
        Slot result = hoveredResult(screen);
        if (result == null) return;

        // Native container tooltips are hidden while carrying a stack. Preserve the full item tooltip here, too.
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        ItemStack stack = result.getItem();
        event.getGuiGraphics().renderTooltip(client.font, Screen.getTooltipFromItem(client, stack),
                stack.getTooltipImage(), stack, event.getMouseX(), event.getMouseY());
    }

    private static Slot hoveredResult(GrindstoneScreen screen) {
        Slot result = screen.getMenu().getSlot(2);
        return screen.getSlotUnderMouse() == result && result.hasItem() ? result : null;
    }
}
