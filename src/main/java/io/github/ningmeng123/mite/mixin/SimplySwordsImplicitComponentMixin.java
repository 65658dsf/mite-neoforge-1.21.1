package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.rules.SimplySwordsImplicitRules;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep persisted and networked implicit values inside the new bounds. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.sweenus.simplyswords.item.component.WeaponImplicitComponent", remap = false)
public abstract class SimplySwordsImplicitComponentMixin implements SimplySwordsImplicitRules.ComponentAccess {
    @Shadow @Override public abstract ResourceLocation implicitId();

    // The canonical constructor is shared by new rolls, the save codec and the network codec.
    @ModifyVariable(method = "<init>", at = @At("LOAD"), argsOnly = true, ordinal = 0, allow = 1)
    private int mite$boundImplicitValue(int value, ResourceLocation implicitId) {
        var range = SimplySwordsImplicitRules.range(implicitId);
        return range == null ? value : Math.clamp(value, range.min(), range.max());
    }
}
