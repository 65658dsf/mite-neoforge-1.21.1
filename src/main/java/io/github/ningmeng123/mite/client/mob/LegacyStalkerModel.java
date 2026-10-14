package io.github.ningmeng123.mite.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/** ModelInvisibleStalker arm equations and source opacity, with unchanged 64x32 UVs. @author NingMeng123 */
public final class LegacyStalkerModel<T extends Mob> extends HumanoidModel<T> {
    private final float opacity;
    public LegacyStalkerModel(ModelPart root, float opacity) { super(root); this.opacity = opacity; }
    @Override public void setupAnim(T mob, float swing, float speed, float age, float yaw, float pitch) {
        super.setupAnim(mob, swing, speed, age, yaw, pitch);
        float attack = Mth.sin(attackTime * Mth.PI), eased = Mth.sin((1 - (1 - attackTime) * (1 - attackTime)) * Mth.PI);
        rightArm.zRot = 0; leftArm.zRot = 0;
        rightArm.yRot = -(.1F - attack * .6F); leftArm.yRot = .1F - attack * .6F;
        rightArm.xRot = -Mth.HALF_PI - (attack * 1.2F - eased * .4F);
        leftArm.xRot = rightArm.xRot;
        rightArm.zRot += Mth.cos(age * .09F) * .05F + .05F; leftArm.zRot -= Mth.cos(age * .09F) * .05F + .05F;
        rightArm.xRot += Mth.sin(age * .067F) * .05F; leftArm.xRot -= Mth.sin(age * .067F) * .05F;
    }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
        int alpha = Math.round((color >>> 24) * opacity);
        super.renderToBuffer(pose, vertices, light, overlay, color & 0x00FFFFFF | alpha << 24);
    }
}
