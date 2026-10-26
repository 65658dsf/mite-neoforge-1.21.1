package io.github.ningmeng123.mite.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.*;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.function.Function;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client-only render registration for all 29 source identities and their actual UV/opacity/scale variants. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class R196MobRenderers {
    private R196MobRenderers() {}
    public static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("mite", "textures/entity/" + path + ".png"); }
    @SuppressWarnings("unchecked") private static <T extends Mob> EntityType<T> type(MobKind kind) {
        return (EntityType<T>)(EntityType<?>)MiteMobs.type(kind);
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) { MobModelLayers.register(event); }
    @SubscribeEvent public static void dimming(RegisterGuiLayersEvent event) {
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath("mite", "vision_dimming"), VisionDimmingOverlay::render);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        for (MobKind kind : MobKind.values()) switch (kind.family()) {
            case UNDEAD -> event.<R196Undead>registerEntityRenderer(type(kind), context -> {
                String path = kind == MobKind.INVISIBLE_STALKER ? "wight" : kind.path();
                return new SourceHumanoidRenderer<>(context, entity -> texture(path), kind == MobKind.INVISIBLE_STALKER ? .05F : 1F, null);
            });
            case FIRE -> event.<R196FireElemental>registerEntityRenderer(type(kind), context ->
                    new SourceHumanoidRenderer<>(context, entity -> texture("fire_elemental"), 0F, null));
            case EARTH -> event.<R196Elemental>registerEntityRenderer(type(kind), context ->
                    new SourceHumanoidRenderer<>(context, R196MobRenderers::elementalTexture, 1F,
                            entity -> texture("earth_elemental/earth_elemental" + (entity.isMagma() ? "_magma" : "") + "_glow")));
            case SPIDER -> event.<R196Spider>registerEntityRenderer(type(kind), context -> new SourceSpiderRenderer(context, kind));
            case BAT -> event.<R196Bat>registerEntityRenderer(type(kind), SourceBatRenderer::new);
            case SKELETON -> event.<R196Skeleton>registerEntityRenderer(type(kind), SourceSkeletonRenderer::new);
            case ZOMBIE -> event.<Zombie>registerEntityRenderer(type(kind), SourceZombieRenderer::new);
            case WOLF -> event.<Wolf>registerEntityRenderer(type(kind), context -> new SourceWolfRenderer(context, kind));
            case CREEPER -> event.<Creeper>registerEntityRenderer(type(kind), SourceCreeperRenderer::new);
            case CUBE -> event.<R196Cube>registerEntityRenderer(type(kind), SourceCubeRenderer::new);
            case SILVERFISH -> event.<Silverfish>registerEntityRenderer(type(kind), context -> new SourceSilverfishRenderer(context, kind));
        }
        event.registerEntityRenderer(MiteMobs.WEB.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(EntityType.SKELETON, OrdinarySkeletonRenderer::new);
    }
    public static ResourceLocation elementalTexture(R196Elemental entity) {
        if (entity.isClay()) return texture("earth_elemental/clay/earth_elemental_clay" + (entity.isHardenedClay() ? "_hardened" : ""));
        String material = entity.materialBlock() == Blocks.OBSIDIAN ? "obsidian" : entity.materialBlock() == Blocks.NETHERRACK ? "netherrack"
                : entity.materialBlock() == Blocks.END_STONE ? "end_stone" : "stone";
        return texture("earth_elemental/" + material + "/earth_elemental_" + material + (entity.isMagma() ? "_magma" : ""));
    }
    private static final class SourceHumanoidRenderer<T extends Mob> extends HumanoidMobRenderer<T, LegacyStalkerModel<T>> {
        private final Function<T,ResourceLocation> textures;
        private final float opacity;
        SourceHumanoidRenderer(EntityRendererProvider.Context context, Function<T,ResourceLocation> textures, float opacity,
                Function<T,ResourceLocation> glow) {
            super(context, new LegacyStalkerModel<>(context.bakeLayer(MobModelLayers.STALKER), opacity,
                    () -> context.bakeLayer(ModelLayers.ZOMBIE)), opacity == 0 ? 0F : .5F);
            this.textures = textures; this.opacity = opacity; shadowStrength = opacity;
            addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<T>(context.bakeLayer(MobModelLayers.INNER_ARMOR)),
                    new HumanoidModel<T>(context.bakeLayer(MobModelLayers.OUTER_ARMOR)), context.getModelManager()));
            if (glow != null) addLayer(new SourceGlowLayer<>(this, glow));
        }
        @Override public ResourceLocation getTextureLocation(T entity) { return textures.apply(entity); }
        @Override protected RenderType getRenderType(T entity, boolean body, boolean translucent, boolean outline) {
            if (opacity == 0F) return outline ? RenderType.outline(getTextureLocation(entity)) : null;
            if (opacity < 1F) return body || translucent ? RenderType.entityTranslucent(getTextureLocation(entity))
                    : outline ? RenderType.outline(getTextureLocation(entity)) : null;
            return super.getRenderType(entity, body, translucent, outline);
        }
    }
    private static final class SourceGlowLayer<T extends Mob, M extends EntityModel<T>> extends RenderLayer<T,M> {
        private final Function<T,ResourceLocation> textures;
        SourceGlowLayer(RenderLayerParent<T,M> parent, Function<T,ResourceLocation> textures) { super(parent); this.textures = textures; }
        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float swing, float speed, float partial,
                float age, float yaw, float pitch) {
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(textures.apply(entity))), 15728640, OverlayTexture.NO_OVERLAY);
        }
    }
    private static final class SourceSpiderRenderer extends MobRenderer<R196Spider,LegacyArachnidModel> {
        private final ResourceLocation body;
        private final float scale;
        SourceSpiderRenderer(EntityRendererProvider.Context context, MobKind kind) {
            this(context, kind, SpiderResourcePackModel.create(context, kind));
        }
        private SourceSpiderRenderer(EntityRendererProvider.Context context, MobKind kind, SpiderResourcePackModel.Selected selected) {
            super(context, selected.model(), kind == MobKind.WOOD_SPIDER
                    || kind == MobKind.BLACK_WIDOW_SPIDER || kind == MobKind.PHASE_SPIDER ? .6F : 1F);
            body = selected.body(); scale = kind == MobKind.WOOD_SPIDER || kind == MobKind.BLACK_WIDOW_SPIDER || kind == MobKind.PHASE_SPIDER ? .6F : 1F;
            addLayer(new SourceGlowLayer<>(this, entity -> selected.glow()));
        }
        @Override public ResourceLocation getTextureLocation(R196Spider entity) { return body; }
        @Override protected void scale(R196Spider entity, PoseStack pose, float partial) { pose.scale(scale, scale, scale); }
        @Override protected float getFlipDegrees(R196Spider entity) { return 180; }
    }
    private static final class SourceBatRenderer extends MobRenderer<R196Bat,LegacyBatModel> {
        SourceBatRenderer(EntityRendererProvider.Context context) { super(context, new LegacyBatModel(context.bakeLayer(MobModelLayers.BAT)), .25F); }
        @Override public ResourceLocation getTextureLocation(R196Bat entity) { return texture(entity.miteKind() == MobKind.NIGHTWING ? "bat/nightwing" : "bat/vampire"); }
        @Override protected void scale(R196Bat entity, PoseStack pose, float partial) {
            float factor = .35F * (entity.miteKind() == MobKind.GIANT_VAMPIRE_BAT ? 2F : 1F); pose.scale(factor,factor,factor);
        }
        @Override protected void setupRotations(R196Bat entity, PoseStack pose, float age, float yaw, float partial, float scale) {
            pose.translate(0, entity.isResting() ? -.1F : Mth.cos(age * .3F) * .1F, 0);
            super.setupRotations(entity,pose,age,yaw,partial,scale);
        }
    }
    private static final class SourceSkeletonRenderer extends SkeletonRenderer<R196Skeleton> {
        SourceSkeletonRenderer(EntityRendererProvider.Context context) {
            super(context, MobModelLayers.INNER_ARMOR, MobModelLayers.OUTER_ARMOR,
                    new LegacySkeletonModel<R196Skeleton>(context.bakeLayer(ModelLayers.SKELETON)));
            addLayer(new PlayerTargetEyesLayer<>(this));
        }
        @Override public ResourceLocation getTextureLocation(R196Skeleton entity) {
            return texture("skeleton/" + switch (entity.miteKind()) {
                case LONGDEAD -> "longdead"; case LONGDEAD_GUARDIAN, ANCIENT_BONE_LORD -> "longdead_guardian";
                default -> "bone_lord";
            });
        }
        @Override protected void scale(R196Skeleton entity, PoseStack pose, float partial) {
            if (entity.skeletonType() == 1) pose.scale(1.2F,1.2F,1.2F);
        }
    }
    private static final class OrdinarySkeletonRenderer extends SkeletonRenderer<Skeleton> {
        OrdinarySkeletonRenderer(EntityRendererProvider.Context context) {
            super(context, MobModelLayers.INNER_ARMOR, MobModelLayers.OUTER_ARMOR,
                    new LegacySkeletonModel<Skeleton>(context.bakeLayer(ModelLayers.SKELETON)));
        }
        @Override public ResourceLocation getTextureLocation(Skeleton entity) {
            return ResourceLocation.withDefaultNamespace("textures/entity/skeleton/"
                    + (((OrdinarySkeletonState)entity).mite$sourceSkeletonType() == 1 ? "wither_skeleton" : "skeleton") + ".png");
        }
        @Override protected void scale(Skeleton entity, PoseStack pose, float partial) {
            if (((OrdinarySkeletonState)entity).mite$sourceSkeletonType() == 1) pose.scale(1.2F,1.2F,1.2F);
        }
    }
    private static final class SourceZombieRenderer extends ZombieRenderer {
        SourceZombieRenderer(EntityRendererProvider.Context context) { super(context); addLayer(new PlayerTargetEyesLayer<>(this)); }
        @Override public ResourceLocation getTextureLocation(Zombie entity) { return texture("zombie/revenant"); }
    }
    private static final class SourceWolfRenderer extends WolfRenderer {
        private final MobKind kind;
        SourceWolfRenderer(EntityRendererProvider.Context context, MobKind kind) {
            super(context); this.kind = kind;
            layers.removeIf(layer -> layer instanceof WolfCollarLayer);
            if (kind == MobKind.DIRE_WOLF) addLayer(new SourceWolfCollar(this));
        }
        @Override public ResourceLocation getTextureLocation(Wolf entity) {
            return texture(kind == MobKind.HELLHOUND ? "hellhound/hellhound" : "dire_wolf/"
                    + (entity.isTame() ? "tame" : entity.isAngry() || entity.isAggressive() ? "angry" : "neutral"));
        }
        @Override protected void scale(Wolf entity, PoseStack pose, float partial) {
            if (kind == MobKind.DIRE_WOLF) pose.scale(1.1F,1.1F,1.1F);
        }
    }
    private static final class SourceWolfCollar extends RenderLayer<Wolf,WolfModel<Wolf>> {
        SourceWolfCollar(RenderLayerParent<Wolf,WolfModel<Wolf>> parent) { super(parent); }
        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, Wolf wolf, float swing, float speed, float partial,
                float age, float yaw, float pitch) {
            if (wolf.isTame() && !wolf.isInvisible()) getParentModel().renderToBuffer(pose,
                    buffers.getBuffer(RenderType.entityCutoutNoCull(texture("dire_wolf/collar"))), light,
                    LivingEntityRenderer.getOverlayCoords(wolf,0), 0xFF000000 | wolf.getCollarColor().getTextureDiffuseColor());
        }
    }
    private static final class SourceCreeperRenderer extends CreeperRenderer {
        SourceCreeperRenderer(EntityRendererProvider.Context context) {
            super(context); layers.removeIf(layer -> layer instanceof CreeperPowerLayer);
            model = new SourceCreeperModel(context.bakeLayer(MobModelLayers.SOURCE_CREEPER),
                    () -> context.bakeLayer(ModelLayers.CREEPER));
            addLayer(new SourceCreeperPower(this,context));
        }
        @Override public ResourceLocation getTextureLocation(Creeper entity) { return texture("creeper/infernal_creeper"); }
    }
    private static final class SourceCreeperPower extends EnergySwirlLayer<Creeper,CreeperModel<Creeper>> {
        private final CreeperModel<Creeper> model;
        SourceCreeperPower(RenderLayerParent<Creeper,CreeperModel<Creeper>> parent, EntityRendererProvider.Context context) {
            super(parent); model = new SourceCreeperModel(context.bakeLayer(MobModelLayers.SOURCE_CREEPER_ARMOR),
                    () -> context.bakeLayer(ModelLayers.CREEPER));
        }
        @Override protected float xOffset(float age) { return age * .01F; }
        @Override protected ResourceLocation getTextureLocation() { return texture("creeper/creeper_armor"); }
        @Override protected EntityModel<Creeper> model() { return model; }
    }
    private static final class SourceCubeRenderer extends MobRenderer<R196Cube,SlimeModel<R196Cube>> {
        SourceCubeRenderer(EntityRendererProvider.Context context) {
            super(context, new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME)), .25F);
            addLayer(new SlimeOuterLayer<>(this,context.getModelSet()));
        }
        @Override public ResourceLocation getTextureLocation(R196Cube entity) { return texture("slime/" + entity.miteKind().path()); }
        @Override public void render(R196Cube entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
            shadowRadius = .25F * entity.getSize(); super.render(entity,yaw,partial,pose,buffers,light);
        }
        @Override protected void scale(R196Cube entity, PoseStack pose, float partial) {
            float size = entity.getSize(), squish = Mth.lerp(partial,entity.oSquish,entity.squish) / (size * .5F + 1F), factor = 1F / (squish + 1F);
            pose.scale(factor * size, size / factor, factor * size);
        }
    }
    private static final class SourceSilverfishRenderer extends SilverfishRenderer {
        private final ResourceLocation body;
        SourceSilverfishRenderer(EntityRendererProvider.Context context, MobKind kind) {
            super(context); body = texture("silverfish/" + (kind == MobKind.HOARY_SILVERFISH ? "hoary" : kind.path()));
        }
        @Override public ResourceLocation getTextureLocation(Silverfish entity) { return body; }
    }
}
