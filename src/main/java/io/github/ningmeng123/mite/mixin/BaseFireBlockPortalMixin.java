package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fire can activate an obsidian frame in the separate underworld. @author NingMeng123 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockPortalMixin {
    @Inject(method = "inPortalDimension", at = @At("HEAD"), cancellable = true)
    private static void mite$includeUnderworld(Level level, CallbackInfoReturnable<Boolean> cir) {
        if (level.dimension() == MiteWorldDimensions.UNDERWORLD) cir.setReturnValue(true);
    }
}
