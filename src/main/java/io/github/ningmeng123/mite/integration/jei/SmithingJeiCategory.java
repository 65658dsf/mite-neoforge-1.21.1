package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import java.math.BigDecimal;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SmithingJeiCategory implements IRecipeCategory<SmithingJeiRecipe> {
    private static final int WIDTH = 164;
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
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return 88; }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, SmithingJeiRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(7, 14).setStandardSlotBackground().addItemStacks(recipe.templates());
        builder.addInputSlot(35, 14).setStandardSlotBackground().addItemStacks(recipe.bases());
        builder.addInputSlot(63, 14).setStandardSlotBackground().addItemStacks(recipe.additions())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable("jei.mite.material_upgrade_inventory")));
        builder.addOutputSlot(129, 14).setOutputSlotBackground().addItemStack(recipe.output())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable("jei.mite.material_upgrade_preservation")));
        var minimum = recipe.minimumTable();
        var compatible = minimum.compatibleTables();
        builder.addSlot(RecipeIngredientRole.CATALYST, 7, 39).setStandardSlotBackground()
                .addItemStack(new ItemStack(minimum.block())).addRichTooltipCallback((slot, tooltip) -> {
                    int divisor = minimum.timeReductionDivisor();
                    if (divisor != 0) tooltip.add(Component.translatable("jei.mite.material_upgrade_speed", divisor));
                    var player = Minecraft.getInstance().player;
                    int level = player == null ? 0 : player.experienceLevel;
                    boolean clumsy = player != null && PlayerCurses.has(player, R196Curse.CLUMSINESS);
                    for (var table : compatible) {
                        tooltip.add(Component.translatable("jei.mite.material_upgrade_table_time", table.block().getName(),
                                seconds(recipe.durationTicks(table, level, clumsy))));
                    }
                });
        var higherTables = compatible.stream().filter(table -> table != minimum)
                .map(table -> new ItemStack(table.block())).toList();
        if (!higherTables.isEmpty()) builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST).addItemStacks(higherTables);
    }

    @Override public void draw(SmithingJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 95, 15);
        drawFit(graphics, Component.translatable("jei.mite.material_upgrade_table", recipe.minimumTable().block().getName()), 29, 43);
        drawFit(graphics, Component.translatable("jei.mite.material_upgrade_ingots", recipe.additions().getFirst().getCount()), 5, 62);
        var player = Minecraft.getInstance().player;
        int level = player == null ? 0 : player.experienceLevel;
        boolean clumsy = player != null && PlayerCurses.has(player, R196Curse.CLUMSINESS);
        String duration = seconds(recipe.durationTicks(level, clumsy));
        drawFit(graphics, Component.translatable("jei.mite.material_upgrade_time", duration, level), 5, 76);
    }

    @Override public ResourceLocation getRegistryName(SmithingJeiRecipe recipe) { return recipe.source().id(); }

    private static String seconds(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }

    private static void drawFit(GuiGraphics graphics, Component text, int horizontal, int vertical) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1F, (WIDTH - horizontal - 5F) / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(horizontal, vertical, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, 0xFF404040, false);
        graphics.pose().popPose();
    }
}
