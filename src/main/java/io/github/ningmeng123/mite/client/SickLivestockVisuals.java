package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.animal.LivestockHealth033;
import javax.annotation.Nullable;
import net.minecraft.client.model.EntityModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;

/** Keep resource-pack model UVs and independent textures intact when livestock are unwell. @author NingMeng123 */
public final class SickLivestockVisuals {
    public static final int CUSTOM_MODEL_SICK_COLOR = 0xFFBFC6AF;
    private static final ThreadLocal<BodyColorScope> BODY_COLORS = new ThreadLocal<>();
    private static boolean failed;

    private SickLivestockVisuals() {}

    /** Null keeps the renderer's normal texture; CEM bodies use a color change instead of a foreign UV atlas. */
    @Nullable public static ResourceLocation bodyTexture(Mob animal, EntityModel<?> model) {
        ResourceLocation sick = LivestockHealth033.sickBodyTexture(animal);
        if (sick == null || customized(model)) return null;
        return animal.getType() == EntityType.CHICKEN ? SickChickenTexture.repair(sick) : sick;
    }

    /** Called only for the main body draw; separate render layers keep their own colors. */
    public static int bodyColor(LivingEntity entity, EntityModel<?> model, int color) {
        if (!(entity instanceof Mob animal) || LivestockHealth033.sickBodyTexture(animal) == null
                || !customized(model)) return color;
        return FastColor.ARGB32.multiply(color, CUSTOM_MODEL_SICK_COLOR);
    }

    /** Save the incoming color so CEM parts that bind an independent atlas can retain their own appearance. */
    @Nullable public static BodyColorScope beginBodyColor(int originalColor, int tintedColor) {
        BodyColorScope previous = BODY_COLORS.get();
        if (previous == null && originalColor == tintedColor) return null;
        BodyColorScope scope = new BodyColorScope(previous, originalColor, tintedColor);
        BODY_COLORS.set(scope);
        return scope;
    }

    public static void endBodyColor(@Nullable BodyColorScope scope) {
        if (scope == null) return;
        if (scope.previous == null) BODY_COLORS.remove();
        else BODY_COLORS.set(scope.previous);
    }

    /** Preserve any color already changed by the resource pack rather than replacing it with a fixed white. */
    public static int independentTextureColor(int color) {
        BodyColorScope scope = BODY_COLORS.get();
        return scope != null && color == scope.tintedColor ? scope.originalColor : color;
    }

    public static final class BodyColorScope {
        private final BodyColorScope previous;
        private final int originalColor;
        private final int tintedColor;

        private BodyColorScope(BodyColorScope previous, int originalColor, int tintedColor) {
            this.previous = previous;
            this.originalColor = originalColor;
            this.tintedColor = tintedColor;
        }
    }

    private static boolean customized(EntityModel<?> model) {
        if (!OptionalMods.EMF) return false;
        // If an optional API becomes unavailable, preserving the active atlas is safer than substituting legacy UVs.
        if (failed) return true;
        try {
            return Loaded.customized(model);
        } catch (Exception | LinkageError failure) {
            failed = true;
            Mite.LOGGER.warn("Cannot inspect optional EMF livestock model; preserving its texture", failure);
            return true;
        }
    }

    private static final class Loaded {
        private static boolean customized(EntityModel<?> model) {
            return traben.entity_model_features.EMFAnimationApi.isModelCustomizedByEMF(model);
        }
    }

    private static final class OptionalMods {
        private static final boolean EMF = ModList.get().isLoaded("entity_model_features");
    }
}
