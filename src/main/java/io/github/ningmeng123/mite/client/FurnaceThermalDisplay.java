package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.furnace.FurnaceThermalMenuAccess;
import java.math.BigDecimal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.neoforged.neoforge.client.event.ScreenEvent;
import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.rules.LavaBucketRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Source thermal display and contextual material hints. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class FurnaceThermalDisplay {
    private FurnaceThermalDisplay() {}

    @SubscribeEvent
    public static void draw(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof AbstractFurnaceMenu)
                || !(screen.getMenu() instanceof FurnaceThermalMenuAccess thermal)) return;
        if (screen instanceof AbstractFurnaceScreen<?> furnaceScreen && screen.width < 379
                && furnaceScreen.getRecipeBookComponent().isVisible()) return;
        if (thermal.mite$getMaxHeat() <= 0) return;
        var graphics = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int x = screen.getGuiLeft();
        int y = Math.max(2, screen.getGuiTop() - 25);
        int width = screen.getXSize();
        int heat = Math.clamp(thermal.mite$getHeat(), 0, 4);
        int capacity = Math.clamp(thermal.mite$getMaxHeat(), 0, 4);
        int required = Math.clamp(thermal.mite$getRequiredHeat(), 0, 4);
        Component temperature = Component.translatable("screen.mite.furnace_temperature", heat, capacity);
        Component requirement = required > 0 ? Component.translatable("screen.mite.furnace_required", required)
                : Component.translatable("screen.mite.furnace_no_input");
        graphics.fill(x, y, x + width, y + 23, 0xDD101010);
        drawFit(graphics, font, temperature, x + 4, y + 3, width - 8, heat > 0 ? 0xFFF2B35E : 0xFFC0C0C0);
        drawFit(graphics, font, requirement, x + 4, y + 13, width - 8, required > 0 && heat < required ? 0xFFFF8585 : 0xFFB9E2A0);
    }

    private static void drawFit(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        float scale = Math.min(1F, width / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    @SubscribeEvent
    public static void explainFuel(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        var fuel = FurnaceThermalController.fuel(stack);
        if (fuel.ticks() > 0 && fuel.heat() > 0) {
            String seconds = BigDecimal.valueOf(fuel.ticks()).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
            event.getToolTip().add(Component.translatable("tooltip.mite.furnace_fuel", fuel.heat(), seconds).withStyle(ChatFormatting.GRAY));
        }
        if (stack.getItem() instanceof BlockItem block) {
            var profile = FurnaceThermalController.profile(block.getBlock().defaultBlockState());
            if (profile != null) event.getToolTip().add(Component.translatable("tooltip.mite.furnace_max_heat", profile.maxHeat()).withStyle(ChatFormatting.GRAY));
        }
        String metal = stack.is(Items.BUCKET) ? "iron" : stack.getItem() instanceof MiteBucketItem bucket
                && bucket.contents() == MiteBucketItem.Contents.EMPTY ? bucket.metal() : null;
        if (metal != null) event.getToolTip().add(Component.translatable("tooltip.mite.bucket_melting", Math.round(LavaBucketRules.meltingChance(metal) * 100F))
                .withStyle(ChatFormatting.GRAY));
        if (stack.is(Items.SAND) || stack.is(Items.RED_SAND)) event.getToolTip().add(
                Component.translatable("tooltip.mite.furnace_sand_batch").withStyle(ChatFormatting.GRAY));
        if (stack.is(Items.CLAY_BALL)) event.getToolTip().add(
                Component.translatable("tooltip.mite.furnace_clay_batch").withStyle(ChatFormatting.GRAY));
    }
}
