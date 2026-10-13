package io.github.ningmeng123.mite.integration.jei;

import java.math.BigDecimal;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Each category has exactly one real furnace catalyst and only executable batches. @author NingMeng123 */
public final class FurnaceJeiCategory implements IRecipeCategory<FurnaceJeiRecipe> {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private final RecipeType<FurnaceJeiRecipe> type;
    private final Component title;
    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable flame;

    public FurnaceJeiCategory(RecipeType<FurnaceJeiRecipe> type, ItemStack furnace, IGuiHelper gui) {
        this.type = type;
        title = furnace.getHoverName();
        icon = gui.createDrawableItemStack(furnace);
        arrow = gui.getRecipeArrow();
        flame = gui.getRecipeFlameFilled();
    }

    @Override public RecipeType<FurnaceJeiRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FurnaceJeiRecipe recipe, IFocusGroup focuses) {
        var input = builder.addInputSlot(18, 11).setStandardSlotBackground().setSlotName("input")
                .addItemStacks(recipe.inputs());
        builder.addOutputSlot(113, 11).setOutputSlotBackground().setSlotName("output")
                .addItemStack(recipe.output());
        builder.addInputSlot(18, 55).setStandardSlotBackground().setSlotName("fuel")
                .addItemStacks(recipe.fuels()).addTooltipCallback((slot, tooltip) ->
                        tooltip.add(Component.translatable("jei.mite.furnace_fuel_batch")));
        if (isClayBatch(recipe)) input.addTooltipCallback((slot, tooltip) ->
                tooltip.add(Component.translatable("jei.mite.furnace_clay_batch")));
    }

    @Override
    public void draw(FurnaceJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 62, 12);
        flame.draw(graphics, 19, 35);
        drawFit(graphics, Component.translatable("jei.mite.furnace_required_heat", recipe.heat()), 49, 45, 106);
        String seconds = BigDecimal.valueOf(recipe.cycleTicks()).divide(BigDecimal.valueOf(20))
                .stripTrailingZeros().toPlainString();
        drawFit(graphics, Component.translatable("jei.mite.furnace_cooking_time", seconds), 49, 59, 106);
        if (isClayBatch(recipe))
            drawFit(graphics, Component.translatable("jei.mite.furnace_clay_batch_short"), 5, 77, WIDTH - 10);
    }

    @Override
    public ResourceLocation getRegistryName(FurnaceJeiRecipe recipe) {
        ResourceLocation furnace = ResourceLocation.parse(recipe.furnaceId());
        ResourceLocation source = recipe.sourceRecipe();
        ItemStack input = recipe.inputs().getFirst();
        ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(input.getItem());
        ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(recipe.output().getItem());
        // One source recipe can yield distinct sand temperatures or clay batch sizes. Persist each view separately.
        String path = "jei_furnace/" + furnace.getNamespace() + "/" + furnace.getPath()
                + "/" + source.getNamespace() + "/" + source.getPath()
                + "/" + inputId.getNamespace() + "/" + inputId.getPath() + "/" + input.getCount()
                + "/" + outputId.getNamespace() + "/" + outputId.getPath() + "/" + recipe.output().getCount()
                + "/heat_" + recipe.heat();
        return ResourceLocation.fromNamespaceAndPath("mite", path);
    }

    private static boolean isClayBatch(FurnaceJeiRecipe recipe) {
        return recipe.output().is(Items.BRICK) && recipe.inputs().stream().anyMatch(stack -> stack.is(Items.CLAY_BALL));
    }

    private static void drawFit(GuiGraphics graphics, Component text, int x, int y, int width) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1F, width / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, 0xFF404040, false);
        graphics.pose().popPose();
    }
}
