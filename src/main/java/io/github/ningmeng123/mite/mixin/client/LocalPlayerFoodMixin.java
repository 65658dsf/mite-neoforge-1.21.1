package io.github.ningmeng123.mite.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** R196 allows sprinting with food energy, including at level zero. @author NingMeng123 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerFoodMixin {
    @ModifyConstant(method = "hasEnoughFoodToStartSprinting", constant = @Constant(floatValue = 6.0F), require = 1, allow = 1)
    private float mite$sprintWhileFoodRemains(float original) {
        return 0.0F;
    }
}
