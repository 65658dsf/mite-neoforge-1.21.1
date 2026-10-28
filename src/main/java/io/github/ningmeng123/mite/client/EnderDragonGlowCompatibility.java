package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** EDP's vanilla-UV overlays cannot be drawn on a resource pack's replacement dragon atlas. @author NingMeng123 */
public final class EnderDragonGlowCompatibility {
    private static final ResourceLocation CRACKS = ResourceLocation.fromNamespaceAndPath("enderdragonplus", "textures/entity/dragon_glow_cracks.png");
    private static final ResourceLocation VEINS = ResourceLocation.fromNamespaceAndPath("enderdragonplus", "textures/entity/dragon_glow_veins.png");
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    private EnderDragonGlowCompatibility() {}

    public static MultiBufferSource compatibleBuffers(EntityModel<?> model, MultiBufferSource original) {
        if (!OptionalMods.EDP || !OptionalMods.EMF || !Loaded.customized(model)) return original;
        return type -> type == RenderType.eyes(CRACKS) || type == RenderType.eyes(VEINS)
                ? DISCARD : original.getBuffer(type);
    }

    private static final class Loaded {
        private static boolean customized(EntityModel<?> model) {
            return traben.entity_model_features.EMFAnimationApi.isModelCustomizedByEMF(model);
        }
    }

    private static final class OptionalMods {
        private static final boolean EDP = ModList.get().isLoaded("enderdragonplus");
        private static final boolean EMF = ModList.get().isLoaded("entity_model_features");
    }
}
