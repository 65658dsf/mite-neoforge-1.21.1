package io.github.ningmeng123.mite.client.mob;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.MobKind;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** Uses the installed spider CEM together with an atlas matching its geometry. @author NingMeng123 */
public final class SpiderResourcePackModel {
    private static final ResourceLocation PACK_BODY = ResourceLocation.withDefaultNamespace("textures/entity/spider/spider.png");

    public record Selected(LegacyArachnidModel model, ResourceLocation body, ResourceLocation glow, boolean customized) {}

    private SpiderResourcePackModel() {}

    public static Selected create(EntityRendererProvider.Context context, MobKind kind) {
        String name = kind == MobKind.BLACK_WIDOW_SPIDER ? "black_widow" : kind.path();
        ResourceLocation body = R196MobRenderers.texture("spider/" + name);
        ResourceLocation glow = R196MobRenderers.texture("spider/" + name + "_glow");
        if (ModList.get().isLoaded("entity_model_features")) {
            try {
                Selected selected = Loaded.create(context, body, glow, kind.path());
                if (selected != null) return selected;
            } catch (Exception | LinkageError failure) {
                Mite.LOGGER.warn("Cannot adapt resource-pack spider model for {}; using the source model", kind.path(), failure);
            }
        }
        return new Selected(new LegacyArachnidModel(context.bakeLayer(MobModelLayers.SPIDER)), body, glow, false);
    }

    // The optional implementation is never linked by a client without EMF, or a dedicated server.
    private static final class Loaded {
        private static Selected create(EntityRendererProvider.Context context, ResourceLocation body,
                                       ResourceLocation glow, String variant) throws java.io.IOException {
            var root = context.bakeLayer(ModelLayers.SPIDER);
            if (!(root instanceof traben.entity_model_features.models.parts.EMFModelPartRoot emf)
                    || !emf.containsCustomModel) return null;
            // Per-part texture overrides need their own atlases. Keep those packs on the safe fallback.
            // Fresh Animations and FA+Spiders use one spider atlas without texture overrides.
            if (root.getAllParts().anyMatch(part -> part instanceof
                    traben.entity_model_features.models.parts.EMFModelPart custom && custom.textureOverride != null)) return null;
            var textures = SpiderPackTextures.create(body, glow, PACK_BODY, null, variant);
            return new Selected(new LegacyArachnidModel(root), textures.body(), textures.glow(), true);
        }
    }
}
