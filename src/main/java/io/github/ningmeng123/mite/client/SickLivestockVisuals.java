package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.animal.LivestockHealth033;
import javax.annotation.Nullable;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;

/** Keep resource-pack model UVs and independent textures intact when livestock are unwell. @author NingMeng123 */
public final class SickLivestockVisuals {
    private static final ThreadLocal<BodyTextureScope> BODY_TEXTURES = new ThreadLocal<>();
    private static boolean failed;
    private static boolean textureFailed;

    private SickLivestockVisuals() {}

    /** Native bodies retain the original sick artwork; CEM atlases are selected after ETF resolves their active variant. */
    @Nullable public static ResourceLocation bodyTexture(Mob animal, EntityModel<?> model) {
        ResourceLocation sick = LivestockHealth033.sickBodyTexture(animal);
        if (sick == null || customized(model)) return null;
        if (animal.getType() == EntityType.CHICKEN) sick = SickChickenTexture.repair(sick);
        return sick;
    }

    /** Swap only the actual main-body atlas; independent wool, saddle and CEM part textures retain their appearance. */
    @Nullable public static BodyTextureScope beginBodyTexture(LivingEntity entity, EntityModel<?> model, VertexConsumer vertices) {
        BodyTextureScope previous = BODY_TEXTURES.get();
        BodyTextureScope scope = null;
        if (!textureFailed && entity instanceof Mob animal && LivestockHealth033.sickBodyTexture(animal) != null
                && customized(model)) {
            try {
                scope = Loaded.prepare(previous, vertices);
            } catch (Exception | LinkageError failure) {
                textureFailed = true;
                Mite.LOGGER.warn("Cannot prepare gray-white livestock texture; preserving its active atlas", failure);
            }
        }
        // A nested ordinary draw must suspend the outer livestock's base-texture alias.
        if (scope == null && previous != null) scope = new BodyTextureScope(previous, vertices, null, null);
        if (scope != null) BODY_TEXTURES.set(scope);
        return scope;
    }

    public static void endBodyTexture(@Nullable BodyTextureScope scope) {
        if (scope == null) return;
        if (scope.previous == null) BODY_TEXTURES.remove();
        else BODY_TEXTURES.set(scope.previous);
    }

    /** CEM may explicitly bind the base atlas again after the main buffer has been replaced. */
    public static RenderType baseTextureLayer(RenderType layer) {
        BodyTextureScope scope = BODY_TEXTURES.get();
        if (scope == null || scope.source == null) return layer;
        try {
            return scope.source.equals(Loaded.textureId(layer)) ? Loaded.layer(scope.gray) : layer;
        } catch (Exception | LinkageError failure) {
            if (!textureFailed) Mite.LOGGER.warn("Cannot preserve gray-white CEM base texture", failure);
            textureFailed = true;
            return layer;
        }
    }

    public static final class BodyTextureScope {
        private final BodyTextureScope previous;
        private final VertexConsumer vertices;
        private final ResourceLocation source;
        private final ResourceLocation gray;

        private BodyTextureScope(BodyTextureScope previous, VertexConsumer vertices, ResourceLocation source, ResourceLocation gray) {
            this.previous = previous;
            this.vertices = vertices;
            this.source = source;
            this.gray = gray;
        }

        public VertexConsumer vertices() { return vertices; }
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

        @Nullable private static BodyTextureScope prepare(BodyTextureScope previous, VertexConsumer vertices) {
            if (!(vertices instanceof traben.entity_texture_features.utils.ETFVertexConsumer metadata)) return null;
            var provider = metadata.etf$getProvider();
            if (provider == null) return null;
            ResourceLocation source = textureId(metadata.etf$getRenderLayer());
            if (source == null && metadata.etf$getETFTexture() != null) source = metadata.etf$getETFTexture().thisIdentifier;
            if (source == null) return null;
            ResourceLocation gray = SickLivestockTextures.gray(source);
            if (gray.equals(source)) return null;
            VertexConsumer replacement = provider.getBuffer(layer(gray));
            return new BodyTextureScope(previous, replacement, source, gray);
        }

        @Nullable private static ResourceLocation textureId(RenderType layer) {
            return layer instanceof traben.entity_texture_features.utils.ETFRenderLayerWithTexture textured
                    ? textured.etf$getId().orElse(null) : null;
        }

        private static RenderType layer(ResourceLocation texture) {
            return traben.entity_model_features.models.animation.state.EMFState.getLayerFromRecentFactoryOrETFOverrideOrTranslucent(texture);
        }
    }

    private static final class OptionalMods {
        private static final boolean EMF = ModList.get().isLoaded("entity_model_features");
    }
}
