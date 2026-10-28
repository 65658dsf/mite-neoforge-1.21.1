package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.EnderDragonModelCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adapt newer resource-pack rigs while preserving their own geometry and animations. @author NingMeng123 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.jem_objects.EMFJemData", remap = false)
public abstract class EmfDragonJemCompatibilityMixin {
    @Inject(method = "prepare(Ltraben/entity_model_features/utils/EMFDirectoryHandler;Ltraben/entity_model_features/models/EMFModel_ID;)V",
            at = @At("HEAD"), require = 1, remap = false)
    private void mite$adaptSegmentedDragon(@Coerce Object directory, @Coerce Object modelId, CallbackInfo ci) {
        EnderDragonModelCompatibility.prepare(this, modelId);
    }
}
