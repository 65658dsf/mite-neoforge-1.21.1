package io.github.ningmeng123.mite.integration.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SmithingJeiCategory implements IRecipeCategory<SmithingJeiRecipe> {
    private final RecipeType<SmithingJeiRecipe> type;
    private final IDrawable icon;
    private final IDrawable arrow;

    public SmithingJeiCategory(RecipeType<SmithingJeiRecipe> type, IGuiHelper gui) {
        this.type = type;
        icon = gui.createDrawableItemStack(new ItemStack(Items.SMITHING_TABLE));
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<SmithingJeiRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable("jei.mite.material_upgrade"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 164; }
    @Override public int getHeight() { return 70; }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, SmithingJeiRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(7, 14).setStandardSlotBackground().addItemStacks(recipe.templates());
        builder.addInputSlot(35, 14).setStandardSlotBackground().addItemStacks(recipe.bases());
        builder.addInputSlot(63, 14).setStandardSlotBackground().addItemStacks(recipe.additions());
        builder.addOutputSlot(129, 14).setOutputSlotBackground().addItemStack(recipe.output())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable("jei.mite.material_upgrade_preservation")));
    }

    @Override public void draw(SmithingJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 95, 15);
        drawFit(graphics, Component.translatable("jei.mite.material_upgrade_ingots", recipe.additions().getFirst().getCount()), 39);
        drawFit(graphics, Component.translatable("jei.mite.material_upgrade_time",
                java.math.BigDecimal.valueOf(recipe.baseTicks()).divide(java.math.BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString()), 53);
    }

    @Override public ResourceLocation getRegistryName(SmithingJeiRecipe recipe) { return recipe.source().id(); }

    private static void drawFit(GuiGraphics graphics, Component text, int vertical) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1F, 154F / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(5, vertical, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, 0xFF404040, false);
        graphics.pose().popPose();
    }
}
