package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.Mite;
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

/** Shows the actual apple input, output and point payment without a lapis ingredient. @author NingMeng123 */
public final class EnchantingJeiCategory implements IRecipeCategory<EnchantingJeiRecipe> {
    private final RecipeType<EnchantingJeiRecipe> type;
    private final IDrawable icon;
    private final IDrawable arrow;

    public EnchantingJeiCategory(RecipeType<EnchantingJeiRecipe> type, IGuiHelper gui) {
        this.type = type;
        icon = gui.createDrawableItemStack(new ItemStack(Items.ENCHANTING_TABLE));
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<EnchantingJeiRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable("jei.mite.enchanting"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 164; }
    @Override public int getHeight() { return 82; }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, EnchantingJeiRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(28, 14).setStandardSlotBackground().addItemStack(recipe.input());
        builder.addOutputSlot(120, 14).setOutputSlotBackground().addItemStack(recipe.output());
    }

    @Override public void draw(EnchantingJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 76, 15);
        drawFit(graphics, Component.translatable("jei.mite.enchanting_experience", recipe.experienceCost()), 40);
        drawFit(graphics, Component.translatable("jei.mite.enchanting_no_lapis"), 53);
        drawFit(graphics, Component.translatable("jei.mite.enchanting_no_bookshelves"), 66);
    }

    @Override public ResourceLocation getRegistryName(EnchantingJeiRecipe recipe) {
        return ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "enchanting/golden_apple");
    }

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
