package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.AppleSkinNutritionRules;
import io.github.ningmeng123.mite.rules.FoodHudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Wraps vanilla food icons into R196 rows and reserves their NeoForge HUD height.
 *
 * @author NingMeng123
 */
@Mixin(Gui.class)
public abstract class GuiFoodMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyConstant(
            method = "renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V",
            constant = @Constant(intValue = 10),
            require = 1,
            allow = 1
    )
    private int mite$foodContainerCount(int vanillaContainerCount, GuiGraphics graphics, Player player, int top, int right) {
        return AppleSkinNutritionRules.maximumIcons(MiteExtensions.foodCapacity(player));
    }

    @ModifyArgs(method = "renderFood", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"),
            require = 3, allow = 3)
    private void mite$foodIconPosition(Args args, GuiGraphics graphics, Player player, int top, int right) {
        int icon = (right - (int) args.get(1) - 9) / 8;
        args.set(1, FoodHudLayout.iconX(right, icon));
        args.set(2, (int) args.get(2) - FoodHudLayout.rowOffset(MiteExtensions.foodCapacity(player), icon));
    }

    @ModifyConstant(method = "renderFoodLevel", constant = @Constant(intValue = 10), require = 1, allow = 1)
    private int mite$foodReservedHeight(int vanillaHeight) {
        return minecraft.player == null ? vanillaHeight
                : vanillaHeight + FoodHudLayout.extraHeight(MiteExtensions.foodCapacity(minecraft.player));
    }
}
