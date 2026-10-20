package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/** Samples installed CEM animation without replacing a custom model's geometry or UVs. @author NingMeng123 */
public final class FreshAnimationsModelDriver {
    private static boolean failed;

    private FreshAnimationsModelDriver() {}

    public static boolean animate(LivingEntity entity, EntityModel<?> model) {
        if (failed || !ModList.get().isLoaded("entity_model_features")) return false;
        try {
            return Loaded.animate(entity, model);
        } catch (Exception | LinkageError failure) {
            failed = true;
            Mite.LOGGER.warn("Cannot sample optional EMF animation for a custom model", failure);
            return false;
        }
    }

    private static final class Loaded {
        private static boolean animate(LivingEntity entity, EntityModel<?> model) {
            return traben.entity_model_features.EMFAnimationApi.animateModelForEntity(
                    traben.entity_model_features.EMFAnimationApi.emfEntityOf(entity), model, false);
        }
    }
}
