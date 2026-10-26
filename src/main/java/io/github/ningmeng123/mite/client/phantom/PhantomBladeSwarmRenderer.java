package io.github.ningmeng123.mite.client.phantom;

import io.github.ningmeng123.mite.phantom.PhantomBladeSwarm;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** The controller stays invisible; its one persistent projectile renders the sword in every phase. @author NingMeng123 */
public final class PhantomBladeSwarmRenderer extends EntityRenderer<PhantomBladeSwarm> {
    public PhantomBladeSwarmRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0F;
    }

    @Override public boolean shouldRender(PhantomBladeSwarm entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override public ResourceLocation getTextureLocation(PhantomBladeSwarm entity) { return PhantomBladeVisuals.LIGHT; }
}
