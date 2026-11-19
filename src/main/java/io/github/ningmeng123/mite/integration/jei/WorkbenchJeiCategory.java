package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.RunestoneItem;
import io.github.ningmeng123.mite.crafting.WorkbenchDismantleRecipe;
import io.github.ningmeng123.mite.crafting.FoodManufacturingRecipe;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import java.math.BigDecimal;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
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

/** One minimum-capacity category with all compatible tables as JEI catalysts. @author NingMeng123 */
public final class WorkbenchJeiCategory implements IRecipeCategory<WorkbenchJeiRecipe> {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 100;
    private final RecipeType<WorkbenchJeiRecipe> type;
    private final Component title;
    private final IDrawable icon;
    private final IDrawable arrow;

    public WorkbenchJeiCategory(RecipeType<WorkbenchJeiRecipe> type, ItemStack bench, IGuiHelper gui, boolean inventory) {
        this.type = type;
        title = inventory ? Component.translatable("jei.mite.inventory_crafting") : bench.getHoverName();
        icon = gui.createDrawableItemStack(bench);
        arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<WorkbenchJeiRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WorkbenchJeiRecipe recipe, IFocusGroup focuses) {
        for (int slot = 0; slot < recipe.inputs().size(); slot++) {
            builder.addInputSlot(8 + slot % recipe.width() * 18, 8 + slot / recipe.width() * 18)
                    .setStandardSlotBackground().setSlotName("input_" + slot).addItemStacks(recipe.inputs().get(slot));
        }
        builder.addOutputSlot(115, 26).setOutputSlotBackground().setSlotName("output")
                .addItemStack(recipe.output()).addTooltipCallback((slot, tooltip) -> {
                    if (EquipmentDurability.isManaged(recipe.output()))
                        tooltip.add(Component.translatable("jei.mite.workbench_quality"));
                    if (recipe.output().getItem() instanceof RunestoneItem)
                        tooltip.add(Component.translatable("jei.mite.workbench_runestone"));
                    if (recipe.shapeless()) tooltip.add(Component.translatable("jei.mite.workbench_shapeless"));
                    if (isMixedMilk(recipe)) tooltip.add(Component.translatable("jei.mite.workbench_milk_remainder"));
                    else for (ItemStack remaining : recipe.remainders())
                        tooltip.add(Component.translatable("jei.mite.workbench_remainder", remaining.getCount(), remaining.getHoverName()));
                });
        // Dismantling has a second useful product, returned through the input slot in the real menu.
        if (recipe.source().value() instanceof WorkbenchDismantleRecipe) {
            var remaining = recipe.remainders();
            if (!remaining.isEmpty()) builder.addOutputSlot(137, 26).setStandardSlotBackground()
                    .setSlotName("wooden_body").addItemStack(remaining.getFirst());
        }
    }

    @Override
    public void draw(WorkbenchJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 78, 27);
        if (recipe.rule() == null) {
            drawFit(graphics, Component.translatable("jei.mite.workbench_untimed"), 5, 80);
        } else {
            var player = Minecraft.getInstance().player;
            int level = player == null ? 0 : player.experienceLevel;
            boolean clumsy = player != null && PlayerCurses.has(player, R196Curse.CLUMSINESS);
            drawFit(graphics, Component.translatable("jei.mite.workbench_time_basis", level), 5, 67);
            var times = WorkbenchJeiRecipes.categoryWorkstations(recipe.workstationId()).stream()
                    .mapToInt(id -> recipe.durationTicks(id, level, clumsy)).summaryStatistics();
            String duration = times.getCount() == 0 ? seconds(recipe.durationTicks(recipe.workstationId(), level, clumsy))
                    : times.getMin() == times.getMax() ? seconds(times.getMin())
                    : seconds(times.getMin()) + "–" + seconds(times.getMax());
            drawFit(graphics, Component.translatable("jei.mite.workbench_time", duration), 5, 80);
        }
        if (isMixedMilk(recipe))
            drawFit(graphics, Component.translatable("jei.mite.workbench_mixed_milk"), 5, 91);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, WorkbenchJeiRecipe recipe, IRecipeSlotsView slots,
            double mouseX, double mouseY) {
        if (recipe.rule() == null || mouseX < 5 || mouseX >= WIDTH - 5 || mouseY < 78 || mouseY >= 90) return;
        var player = Minecraft.getInstance().player;
        int level = player == null ? 0 : player.experienceLevel;
        boolean clumsy = player != null && PlayerCurses.has(player, R196Curse.CLUMSINESS);
        for (String id : WorkbenchJeiRecipes.categoryWorkstations(recipe.workstationId())) {
            ItemStack bench = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
            tooltip.add(bench.getHoverName().copy().append(": ")
                    .append(Component.translatable("jei.mite.workbench_time", seconds(recipe.durationTicks(id, level, clumsy)))));
        }
    }

    private static String seconds(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }

    private static boolean isMixedMilk(WorkbenchJeiRecipe recipe) {
        return recipe.source().value() instanceof FoodManufacturingRecipe food && food.manufacturing().anyMilkBuckets();
    }

    @Override
    public ResourceLocation getRegistryName(WorkbenchJeiRecipe recipe) {
        ResourceLocation bench = ResourceLocation.parse(recipe.workstationId());
        ResourceLocation source = recipe.displayId();
        return ResourceLocation.fromNamespaceAndPath("mite", "jei_workbench/" + bench.getNamespace() + "/" + bench.getPath()
                + "/" + source.getNamespace() + "/" + source.getPath());
    }

    private static void drawFit(GuiGraphics graphics, Component text, int x, int y) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1F, (WIDTH - 10F) / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(font, text, 0, 0, 0xFF404040, false);
        graphics.pose().popPose();
    }
}
