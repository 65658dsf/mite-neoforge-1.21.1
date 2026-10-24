package io.github.ningmeng123.mite.client.phantom;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.phantom.PhantomBladeProjectile;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The real flying material sword follows its velocity; old world positions form a fading light ribbon. @author NingMeng123 */
public final class PhantomBladeProjectileRenderer extends EntityRenderer<PhantomBladeProjectile> {
    private final ItemRenderer items;
    private final Map<PhantomBladeProjectile, Trail> trails = new WeakHashMap<>();

    public PhantomBladeProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemRenderer();
        shadowRadius = 0F;
    }

    @Override public boolean shouldRender(PhantomBladeProjectile entity, Frustum frustum, double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) <= 4096D && frustum.isVisible(entity.getBoundingBox().inflate(5D));
    }

    @Override public void render(PhantomBladeProjectile entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        Vec3 current = entity.getPosition(partialTick);
        Vec3 motion = entity.getDeltaMovement();
        float facingYaw = motion.lengthSqr() > .000001D ? (float) Math.toDegrees(Math.atan2(motion.x, motion.z))
                : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float elevation = motion.lengthSqr() > .000001D ? (float) Math.toDegrees(Math.atan2(motion.y, motion.horizontalDistance()))
                : Mth.rotLerp(partialTick, entity.xRotO, entity.getXRot());
        // Attach the ribbon at the hilt, behind the center of the tip-first flying sword.
        double yawRadians = Math.toRadians(facingYaw), elevationRadians = Math.toRadians(elevation);
        Vec3 hiltOffset = new Vec3(Math.sin(yawRadians) * Math.cos(elevationRadians), Math.sin(elevationRadians),
                Math.cos(yawRadians) * Math.cos(elevationRadians)).scale(-.62D);
        int color = PhantomBladeVisuals.color(entity.material());
        Trail trail = trails.computeIfAbsent(entity, ignored -> new Trail());
        // Preserve each tick's actual hilt position, including its heading when turning.
        trail.sample(entity.tickCount, entity.position().add(hiltOffset));
        Vec3 camera = entityRenderDispatcher.camera.getPosition().subtract(current);
        Vec3 from = hiltOffset;
        double fromAge = 0D;
        for (TrailPoint point : trail.points()) {
            double age = entity.tickCount - 1D + partialTick - point.tick();
            if (age < 0D) continue;
            Vec3 to = point.position().subtract(current);
            PhantomBladeVisuals.ribbon(pose, buffers, from, to, camera,
                    .115D * PhantomBladeVisuals.trailOpacity(fromAge), .115D * PhantomBladeVisuals.trailOpacity(age),
                    color, PhantomBladeVisuals.trailOpacity(fromAge), PhantomBladeVisuals.trailOpacity(age));
            from = to; fromAge = age;
        }
        pose.pushPose();
        PhantomBladeVisuals.orientProjectile(pose, facingYaw, elevation);
        pose.scale(.95F, .95F, .95F);
        PhantomBladeVisuals.bladeAura(pose, buffers, color, .9F);
        PhantomBladeVisuals.alignSwordSprite(pose);
        items.renderStatic(new ItemStack(MitePhantomBlades.sword(entity.material())), ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(PhantomBladeProjectile entity) { return PhantomBladeVisuals.LIGHT; }

    /** Each trail belongs to a weak entity key, samples at most once per tick and expires while stationary. */
    public static final class Trail {
        private final ArrayDeque<TrailPoint> history = new ArrayDeque<>();
        private int sampledTick = Integer.MIN_VALUE;
        public void sample(int tick, Vec3 position) {
            if (sampledTick == tick) return;
            sampledTick = tick;
            if (!history.isEmpty() && history.getFirst().position().distanceToSqr(position) > 16D) history.clear();
            if (history.isEmpty() || history.getFirst().position().distanceToSqr(position) > .000001D)
                history.addFirst(new TrailPoint(position, tick));
            while (!history.isEmpty() && tick - history.getLast().tick() >= PhantomBladeVisuals.TRAIL_TICKS)
                history.removeLast();
        }
        public java.util.List<TrailPoint> points() { return new ArrayList<>(history); }
    }
    public record TrailPoint(Vec3 position, int tick) {}
}
