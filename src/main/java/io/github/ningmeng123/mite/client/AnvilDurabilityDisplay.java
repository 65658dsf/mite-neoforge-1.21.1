package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.AnvilDurability;
import io.github.ningmeng123.mite.crafting.AnvilDurabilityAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Item and menu views never change wear or make anvils damageable equipment. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class AnvilDurabilityDisplay {
    private AnvilDurabilityDisplay() {}

    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        var view = AnvilDurability.of(event.getItemStack());
        if (view != null) event.getToolTip().add(view.text().copy().withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AnvilScreen screen)) return;
        var data = (AnvilDurabilityAccess)screen.getMenu();
        int maximum = data.mite$anvilMaximumDurability();
        int remaining = data.mite$anvilRemainingDurability();
        if (maximum <= 0 || remaining < 0 || remaining > maximum) return;
        int x = screen.getGuiLeft(), y = screen.getGuiTop() - 23, width = 176;
        if (y < 2) return;
        var graphics = event.getGuiGraphics();
        graphics.fill(x, y, x + width, y + 20, 0xDD101010);
        graphics.fill(x + 2, y + 16, x + width - 2, y + 19, 0xFF343434);
        int fill = (int)((long)(width - 4) * remaining / maximum);
        int color = remaining * 5L <= maximum ? 0xFFE05040 : remaining * 2L <= maximum ? 0xFFE0AD40 : 0xFF70BF44;
        graphics.fill(x + 2, y + 16, x + 2 + fill, y + 19, color);
        graphics.drawCenteredString(Minecraft.getInstance().font,
                new AnvilDurability.View(remaining, maximum).text(), x + width / 2, y + 4, 0xFFFFFF);
    }
}
