package io.github.ningmeng123.mite.integration.jei;

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

/** Compact repair layout; usable anvils are indexed here and drawn in JEI's native sidebar. @author NingMeng123 */
public final class AnvilJeiCategory implements IRecipeCategory<AnvilJeiRecipe> {
    private static final int WIDTH = 164;
    private static final int HEIGHT = 96;
    private final RecipeType<AnvilJeiRecipe> type;
    private final Component title;
    private final IDrawable icon;
    private final IDrawable arrow;

    public AnvilJeiCategory(RecipeType<AnvilJeiRecipe> type, ItemStack anvil, IGuiHelper gui) {
        this.type = type;
        title = anvil.getHoverName();
        icon = gui.createDrawableItemStack(anvil);
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<AnvilJeiRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AnvilJeiRecipe recipe, IFocusGroup focuses) {
        var anvils = AnvilJeiRecipes.categoryAnvils(recipe.anvilId()).stream()
                .map(id -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)))).toList();
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST).addItemStacks(anvils);
        builder.addInputSlot(10, 15).setStandardSlotBackground().setSlotName("equipment")
                .addItemStack(recipe.left());
        builder.addInputSlot(60, 15).setStandardSlotBackground().setSlotName("repair_material")
                .addItemStack(recipe.right()).addRichTooltipCallback((slot, tooltip) -> {
                    if (recipe.kind() == AnvilJeiRecipe.Kind.COMBINATION)
                        tooltip.add(Component.translatable("jei.mite.anvil_combination_rules"));
                });
        builder.addOutputSlot(127, 15).setOutputSlotBackground().setSlotName("repaired_equipment")
                .addItemStack(recipe.output()).addRichTooltipCallback((slot, tooltip) -> {
                    tooltip.add(Component.translatable("jei.mite.anvil_example"));
                    tooltip.add(Component.translatable("jei.mite.anvil_qualification"));
                    tooltip.add(Component.translatable("jei.mite.anvil_preservation"));
                });
    }

    @Override
    public void draw(AnvilJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        graphics.drawString(Minecraft.getInstance().font, "+", 40, 20, 0xFF404040, false);
        arrow.draw(graphics, 89, 16);
        drawFit(graphics, Component.translatable(recipe.kind() == AnvilJeiRecipe.Kind.MATERIAL
                ? "jei.mite.anvil_material" : "jei.mite.anvil_combination"), 43);
        drawFit(graphics, Component.translatable("jei.mite.anvil_restored",
                recipe.left().getDamageValue() - recipe.output().getDamageValue(), recipe.left().getMaxDamage()), 56);
        drawFit(graphics, Component.translatable("jei.mite.anvil_wear", recipe.anvilWear()), 69);
        drawFit(graphics, Component.translatable("jei.mite.anvil_no_xp"), 82);
    }

    @Override
    public ResourceLocation getRegistryName(AnvilJeiRecipe recipe) {
        ResourceLocation anvil = ResourceLocation.parse(recipe.anvilId());
        return ResourceLocation.fromNamespaceAndPath("mite", "jei_anvil/" + anvil.getNamespace() + "/" + anvil.getPath()
                + "/" + recipe.kind().name().toLowerCase(java.util.Locale.ROOT)
                + "/" + recipe.itemId().getNamespace() + "/" + recipe.itemId().getPath());
    }

    private static void drawFit(GuiGraphics graphics, Component text, int y) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1F, (WIDTH - 10F) / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(5, y, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, 0xFF404040, false);
        graphics.pose().popPose();
    }
}
