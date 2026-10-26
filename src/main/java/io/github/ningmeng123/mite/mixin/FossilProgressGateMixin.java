package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.FossilFeature;
import net.minecraft.world.level.levelgen.feature.FossilFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Overworld fossil features share the exploration gate while monster rooms remain unchanged. @author NingMeng123 */
@Mixin(FossilFeature.class)
public abstract class FossilProgressGateMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void mite$gateNaturalFossil(FeaturePlaceContext<FossilFeatureConfiguration> context,
            CallbackInfoReturnable<Boolean> cir) {
        if (!R196StructureGeneration.fossilUnlocked(context.level().getLevel())) cir.setReturnValue(false);
    }
}
