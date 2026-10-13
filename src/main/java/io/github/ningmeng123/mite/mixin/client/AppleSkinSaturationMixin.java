package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.AppleSkinNutritionView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Optional AppleSkin 3.0.9 nutrition HUD; strict pinned selectors, no external dependency. @author NingMeng123 */
@Pseudo
@Mixin(targets = "squeek.appleskin.client.HUDOverlayHandler$SaturationOverlay", remap = false)
public abstract class AppleSkinSaturationMixin {
    private static final String RENDER = "render(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;IIII)V";

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;getSaturationLevel()F", remap = false), require = 3, allow = 3, remap = false)
    private float mite$nutritionCurrent(FoodData food, Minecraft minecraft, Player player, GuiGraphics graphics, int left, int right, int top, int tick) {
        return AppleSkinNutritionView.current(player);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;getFoodLevel()I", remap = false), require = 1, allow = 1, remap = false)
    private int mite$independentCapacity(FoodData food, Minecraft minecraft, Player player, GuiGraphics graphics, int left, int right, int top, int tick) {
        return AppleSkinNutritionView.capacity(player);
    }

    // This nutrition() is used only by AppleSkin's S + foodS cap expression. C + 0 gives
    // the independent R196 capacity without relying on a compiler's local-variable slot.
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodProperties;nutrition()I", remap = false), require = 1, allow = 1, remap = false)
    private int mite$noSatiationInNutritionCap(FoodProperties food) { return 0; }

    @Redirect(method = RENDER, at = @At(value = "FIELD", target = "Lsqueek/appleskin/helpers/FoodHelper$QueriedFoodResult;modifiedFoodProperties:Lnet/minecraft/world/food/FoodProperties;", remap = false), require = 1, allow = 1, remap = false)
    private FoodProperties mite$qualifiedNutritionPreview(@Coerce Object queried, Minecraft minecraft, Player player, GuiGraphics graphics, int left, int right, int top, int tick) {
        return AppleSkinNutritionView.previewProperties(player, (AppleSkinNutritionView.QueriedFood) queried);
    }

    /** The public float overload is shared by current and forecast drawing. */
    @Pseudo
    @Mixin(targets = "squeek.appleskin.client.HUDOverlayHandler", remap = false)
    public abstract static class Draw {
        @ModifyConstant(method = "drawSaturationOverlay(FFLnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;IIFI)V", constant = @Constant(floatValue = 20F), require = 1, allow = 1, remap = false)
        private static float mite$boundedNutritionRow(float original, float added, float current, Player player, GuiGraphics graphics, int x, int y, float alpha, int tick) {
            return AppleSkinNutritionView.capacity(player);
        }
    }

    /** The constructor result carries the exact selected main/offhand food, including compatibility foods. */
    @Pseudo
    @Mixin(targets = "squeek.appleskin.helpers.FoodHelper$QueriedFoodResult", remap = false)
    public abstract static class QueriedFoodResult implements AppleSkinNutritionView.QueriedFood {
        @Shadow @Final public ItemStack itemStack;
        @Shadow public FoodProperties modifiedFoodProperties;
        @Override public ItemStack mite$queriedStack() { return itemStack; }
        @Override public FoodProperties mite$queriedProperties() { return modifiedFoodProperties; }
    }
}
