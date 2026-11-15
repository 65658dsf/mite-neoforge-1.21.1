package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.client.AppleSkinNutritionView;
import io.github.ningmeng123.mite.rules.AppleSkinNutritionRules;
import io.github.ningmeng123.mite.rules.FoodHudLayout;
import java.util.Vector;
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
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

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
        private static final String NUTRITION_DRAW = "drawSaturationOverlay(FFLnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;IIFI)V";
        private static final String FOOD_DRAW = "drawHungerOverlay(IILnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;IIFZI)V";

        @ModifyConstant(method = NUTRITION_DRAW, constant = @Constant(floatValue = 20F), require = 1, allow = 1, remap = false)
        private static float mite$boundedNutritionRow(float original, float added, float current, Player player, GuiGraphics graphics, int x, int y, float alpha, int tick) {
            return AppleSkinNutritionView.capacity(player);
        }

        @ModifyConstant(method = FOOD_DRAW, constant = @Constant(intValue = 20), require = 1, allow = 1, remap = false)
        private static int mite$boundedFoodPreview(int original, int added, int current, Player player, GuiGraphics graphics, int x, int y, float alpha, boolean rotten, int tick) {
            return AppleSkinNutritionView.capacity(player);
        }

        // AppleSkin caches one row of original animation offsets. Reuse its columns on every row.
        @ModifyExpressionValue(method = {NUTRITION_DRAW, FOOD_DRAW}, at = @At(value = "INVOKE",
                target = "Ljava/util/Vector;size()I"), require = 2, allow = 2, remap = false)
        private static int mite$foodOffsetCount(int original, @Local(argsOnly = true) Player player) {
            // Before AppleSkin's first cache generation, keep its empty-vector fallback.
            return original == 0 ? 0
                    : Math.max(original, AppleSkinNutritionRules.maximumIcons(AppleSkinNutritionView.capacity(player)));
        }

        @WrapOperation(method = {NUTRITION_DRAW, FOOD_DRAW}, at = @At(value = "INVOKE",
                target = "Ljava/util/Vector;get(I)Ljava/lang/Object;"), require = 2, allow = 2, remap = false)
        private static Object mite$foodColumnOffset(Vector<?> offsets, int icon, Operation<Object> original) {
            return original.call(offsets, icon % 10);
        }

        @ModifyArgs(method = NUTRITION_DRAW, at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"),
                require = 1, allow = 1, remap = false)
        private static void mite$nutritionIconRow(Args args, @Local(argsOnly = true) Player player, @Local(index = 13) int icon) {
            args.set(2, (int) args.get(2) - FoodHudLayout.rowOffset(AppleSkinNutritionView.capacity(player), icon));
        }

        @ModifyArgs(method = FOOD_DRAW, at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"),
                require = 2, allow = 2, remap = false)
        private static void mite$foodPreviewIconRow(Args args, @Local(argsOnly = true) Player player, @Local(index = 15) int icon) {
            args.set(2, (int) args.get(2) - FoodHudLayout.rowOffset(AppleSkinNutritionView.capacity(player), icon));
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
