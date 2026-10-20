package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.progression.GearModifierEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Apply ITE mining additions before native enchantment/environment multipliers on both sides. @author NingMeng123 */
@Mixin(Player.class)
public abstract class GearModifierPlayerMixin {
    @ModifyExpressionValue(method = "getDigSpeed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F"))
    private float mite$modifierMiningSpeed(float original, @Local(argsOnly = true) BlockState state) {
        return GearModifierEffects.miningSpeed((Player)(Object)this, state, original);
    }

    @ModifyExpressionValue(method = "getDigSpeed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getValue()D"))
    private double mite$aquadynamicUnderwater(double original) {
        return GearModifierEffects.submergedSpeed((Player)(Object)this, original);
    }
}
