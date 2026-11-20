package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.MaterialUpgradeRequirements;
import io.github.ningmeng123.mite.crafting.SmithingTableTier;
import io.github.ningmeng123.mite.network.CraftingProgressPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Render after the screen so its background never hides authoritative progress. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class CraftingProgressOverlay {
    private CraftingProgressOverlay() {}
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event) {
        var state = CraftingProgressPayload.latest();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.getMenu() instanceof SmithingMenu smithing) drawSmithingCost(event, screen, smithing);
        if (!(screen.getMenu() instanceof CraftingMenu || screen.getMenu() instanceof InventoryMenu
                || screen.getMenu() instanceof SmithingMenu)
                || state == null || !state.active() || screen.getMenu().containerId != state.containerId()
                || System.nanoTime() - CraftingProgressPayload.receivedAt() > 2_000_000_000L) return;
        if (screen.width < 379 && screen instanceof RecipeUpdateListener recipeScreen
                && recipeScreen.getRecipeBookComponent().isVisible()) return;
        int width = 176;
        int x = screen.getGuiLeft();
        int y = Math.max(4, screen.getGuiTop() - 22);
        int percent = Math.clamp((int)(100L * state.progress() / Math.max(1, state.period())), 0, 100);
        var graphics = event.getGuiGraphics();
        boolean inventory = screen.getMenu() instanceof InventoryMenu;
        boolean smithing = screen.getMenu() instanceof SmithingMenu;
        int arrowX = screen.getGuiLeft() + (smithing ? 65 : inventory ? 135 : 89);
        int arrowY = screen.getGuiTop() + (smithing ? 46 : inventory ? 29 : 35);
        int arrowWidth = smithing ? 28 : inventory ? 16 : 24;
        int arrowHeight = smithing ? 21 : inventory ? 13 : 16;
        int filled = Math.clamp((int)((long)arrowWidth * state.progress() / Math.max(1, state.period())), 0, arrowWidth);
        if (filled > 0) graphics.blitSprite(ResourceLocation.withDefaultNamespace("container/furnace/burn_progress"),
                arrowWidth, arrowHeight, 0, 0, arrowX, arrowY, filled, arrowHeight);
        graphics.fill(x, y, x + width, y + 20, 0xDD101010);
        graphics.fill(x + 2, y + 16, x + width - 2, y + 19, 0xFF343434);
        graphics.fill(x + 2, y + 16, x + 2 + (width - 4) * percent / 100, y + 19,
                state.hungry() ? 0xFFE09040 : 0xFF70BF44);
        var label = state.hungry() ? Component.translatable("screen.mite.crafting_paused", percent)
                : Component.translatable("screen.mite.crafting_progress", percent);
        graphics.drawCenteredString(Minecraft.getInstance().font, label, screen.width / 2, y + 4, 0xFFFFFF);
    }

    private static void drawSmithingCost(ScreenEvent.Render.Post event, AbstractContainerScreen<?> screen, SmithingMenu menu) {
        var level = Minecraft.getInstance().level;
        var template = menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem();
        var base = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
        var addition = menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem();
        if (level == null || !MaterialUpgradeRequirements.usesTemplate(template) || base.isEmpty() || addition.isEmpty()) return;
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
            var recipe = holder.value();
            if (!recipe.isTemplateIngredient(template) || !recipe.isBaseIngredient(base) || !recipe.isAdditionIngredient(addition)) continue;
            var result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) continue;
            int x = screen.getGuiLeft() + 88;
            int y = Math.min(screen.height - 12, screen.getGuiTop() + 170);
            var currentTable = SmithingTableTier.current(menu);
            var requiredTable = SmithingTableTier.requiredFor(result);
            if (currentTable != null && !currentTable.supports(requiredTable)) {
                var label = Component.translatable("screen.mite.smithing_required_table", requiredTable.block().getName());
                event.getGuiGraphics().drawCenteredString(Minecraft.getInstance().font, label, x, y, 0xFFFF7070);
                return;
            }
            int required = MaterialUpgradeRequirements.ingots(result);
            int available = addition.getCount();
            var label = Component.translatable("screen.mite.smithing_ingredients", addition.getHoverName(), available, required);
            event.getGuiGraphics().drawCenteredString(Minecraft.getInstance().font, label, x, y,
                    available < required ? 0xFFFF7070 : 0xFFFFFFFF);
            return;
        }
    }
}
