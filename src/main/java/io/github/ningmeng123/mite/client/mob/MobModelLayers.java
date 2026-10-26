package io.github.ningmeng123.mite.client.mob;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Layer dimensions follow the original PNG layouts rather than a modern texture reinterpretation. @author NingMeng123 */
public final class MobModelLayers {
    public static final ModelLayerLocation STALKER = layer("stalker"), BAT = layer("legacy_bat"), SPIDER = layer("arachnid"),
            SKELETON = layer("source_skeleton"), INNER_ARMOR = layer("source_inner_armor"), OUTER_ARMOR = layer("source_outer_armor"),
            CUBE_INNER = layer("cube_inner"), CUBE_OUTER = layer("cube_outer"),
            SOURCE_CREEPER = layer("source_creeper"), SOURCE_CREEPER_ARMOR = layer("source_creeper_armor");
    private MobModelLayers() {}
    private static ModelLayerLocation layer(String path) { return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("mite", path), "main"); }
    public static void register(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // The pinned modern humanoid mesh retains the old mirrored 64x32 limb layout exactly.
        event.registerLayerDefinition(STALKER, () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 32));
        event.registerLayerDefinition(INNER_ARMOR, () -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(.5F), 0), 64, 32));
        event.registerLayerDefinition(OUTER_ARMOR, () -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1F), 0), 64, 32));
        event.registerLayerDefinition(BAT, LegacyBatModel::createLayer);
        event.registerLayerDefinition(SPIDER, LegacyArachnidModel::createLayer);
        event.registerLayerDefinition(SKELETON, SkeletonModel::createBodyLayer);
        // ModelGelatinousCube's two meshes and UVs equal the pinned vanilla inner/outer slime meshes.
        event.registerLayerDefinition(CUBE_INNER, SlimeModel::createInnerBodyLayer);
        event.registerLayerDefinition(CUBE_OUTER, SlimeModel::createOuterBodyLayer);
        event.registerLayerDefinition(SOURCE_CREEPER, () -> CreeperModel.createBodyLayer(CubeDeformation.NONE));
        event.registerLayerDefinition(SOURCE_CREEPER_ARMOR, () -> CreeperModel.createBodyLayer(new CubeDeformation(2F)));
    }
}
