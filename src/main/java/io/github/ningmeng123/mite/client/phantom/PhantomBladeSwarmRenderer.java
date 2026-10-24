package io.github.ningmeng123.mite.client.phantom;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.phantom.PhantomBladeSwarm;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** One networked formation renders eight native material swords, hiding each launched blade. @author NingMeng123 */
public final class PhantomBladeSwarmRenderer extends EntityRenderer<PhantomBladeSwarm> {
    private final ItemRenderer items;

    public PhantomBladeSwarmRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemRenderer();
        shadowRadius = 0F;
    }

    @Override public boolean shouldRender(PhantomBladeSwarm entity, Frustum frustum, double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) <= 4096D && frustum.isVisible(entity.getBoundingBox().inflate(2D));
    }

    @Override public void render(PhantomBladeSwarm entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        double time = entity.tickCount + partialTick;
        int color = PhantomBladeVisuals.color(entity.material());
        ItemStack sword = new ItemStack(MitePhantomBlades.sword(entity.material()));
        pose.pushPose();
        // Match the owner's interpolated body immediately, rather than trailing the server attachment packet.
        var owner = entity.owner();
        if (owner != null) {
            Vec3 adjustment = owner.getPosition(partialTick).add(0D, owner.getBbHeight() * .6D, 0D)
                    .subtract(entity.getPosition(partialTick));
            pose.translate(adjustment.x, adjustment.y, adjustment.z);
        }
        PhantomBladeVisuals.formationRing(pose, buffers, time, color);
        int bladeCount = entity.bladeCount();
        for (int index = 0; index < bladeCount; index++) {
            if ((entity.orbitBladeMask() & 1 << index) == 0) continue;
            Vec3 offset = PhantomBladeVisuals.orbit(time, index, bladeCount);
            pose.pushPose();
            pose.translate(offset.x, offset.y, offset.z);
            PhantomBladeVisuals.orientOrbit(pose, time, index, bladeCount);
            pose.scale(.82F, .82F, .82F);
            PhantomBladeVisuals.bladeAura(pose, buffers, color, .58F);
            PhantomBladeVisuals.alignSwordSprite(pose);
            items.renderStatic(sword, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId() + index);
            pose.popPose();
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(PhantomBladeSwarm entity) { return PhantomBladeVisuals.LIGHT; }
}
