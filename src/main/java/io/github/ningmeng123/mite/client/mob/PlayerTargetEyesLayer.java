package io.github.ningmeng123.mite.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.PlayerTargetEyes;
import io.github.ningmeng123.mite.entity.R196Mob;
import javax.annotation.Nullable;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Original four-pixel eye masks: opaque black at rest and emissive red while targeting a player. @author NingMeng123 */
public final class PlayerTargetEyesLayer<T extends Mob, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public PlayerTargetEyesLayer(RenderLayerParent<T, M> parent) { super(parent); }

    @Nullable public static ResourceLocation textureFor(@Nullable MobKind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case LONGDEAD_GUARDIAN, ANCIENT_BONE_LORD -> R196MobRenderers.texture("skeleton/longdead_guardian_glow");
            case BONE_LORD -> R196MobRenderers.texture("skeleton/bone_lord_glow");
            case REVENANT -> R196MobRenderers.texture("zombie/revenant_glow");
            default -> null;
        };
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float swing, float speed,
            float partial, float age, float yaw, float pitch) {
        if (!(entity instanceof R196Mob source) || !(entity instanceof PlayerTargetEyes eyes)) return;
        ResourceLocation texture = textureFor(source.miteKind());
        if (texture == null || entity.isInvisible()) return;
        boolean red = eyes.hasRedEyes();
        // A normal-lit black mask fills the original body's transparent eye holes without changing that source PNG.
        getParentModel().renderToBuffer(pose,
                buffers.getBuffer(red ? RenderType.eyes(texture) : RenderType.entityCutoutNoCull(texture)),
                red ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, red ? 0xFFFFFFFF : 0xFF000000);
    }
}
