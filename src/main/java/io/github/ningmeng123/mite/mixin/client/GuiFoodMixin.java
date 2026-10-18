package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.rules.ProgressionRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Sizes the vanilla food row without replacing its sprites or rendering logic.
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
    private int mite$foodContainerCount(int vanillaContainerCount) {
        if (this.minecraft.player == null) {
            return vanillaContainerCount;
        }
        // renderFoodLevel still reserves one row; mount hearts and air use their
        // own layers and keep NeoForge's existing rightHeight accounting.
        return io.github.ningmeng123.mite.api.MiteExtensions.foodCapacity(this.minecraft.player) / 2;
    }
}
