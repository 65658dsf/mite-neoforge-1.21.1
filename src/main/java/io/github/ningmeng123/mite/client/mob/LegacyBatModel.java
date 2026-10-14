package io.github.ningmeng123.mite.client.mob;

import io.github.ningmeng123.mite.entity.R196Bat;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

/** The source baz 64x64 bat is unrelated to 1.21's redesigned bat mesh and animation. @author NingMeng123 */
public final class LegacyBatModel extends HierarchicalModel<R196Bat> {
    private final ModelPart root, head, body, rightWing, leftWing, rightTip, leftTip;
    public LegacyBatModel(ModelPart root) {
        this.root = root; head = root.getChild("head"); body = root.getChild("body");
        rightWing = body.getChild("right_wing"); leftWing = body.getChild("left_wing");
        rightTip = rightWing.getChild("tip"); leftTip = leftWing.getChild("tip");
    }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        var head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0,0).addBox(-3,-3,-3,6,6,6), PartPose.ZERO);
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(24,0).addBox(-4,-6,-2,3,4,1), PartPose.ZERO);
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(24,0).mirror().addBox(1,-6,-2,3,4,1), PartPose.ZERO);
        var body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0,16).addBox(-3,4,-3,6,12,6)
                .texOffs(0,34).addBox(-5,16,0,10,6,1), PartPose.ZERO);
        var right = body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(42,0).addBox(-12,1,1.5F,10,16,1), PartPose.ZERO);
        right.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(24,16).addBox(-8,1,0,8,12,1), PartPose.offset(-12,1,1.5F));
        var left = body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(42,0).mirror().addBox(2,1,1.5F,10,16,1), PartPose.ZERO);
        left.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(24,16).mirror().addBox(0,1,0,8,12,1), PartPose.offset(12,1,1.5F));
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public ModelPart root() { return root; }
    @Override public void setupAnim(R196Bat bat, float swing, float speed, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.xRot = pitch * Mth.DEG_TO_RAD;
        if (bat.isResting()) {
            head.yRot = Mth.PI - yaw * Mth.DEG_TO_RAD; head.zRot = Mth.PI; head.setPos(0,-2,0); head.yScale = 1;
            rightWing.setPos(-3,0,3); leftWing.setPos(3,0,3); body.xRot = Mth.PI;
            rightWing.xRot = -.15707964F; leftWing.xRot = rightWing.xRot;
            rightWing.yRot = -1.2566371F; leftWing.yRot = -rightWing.yRot;
            rightTip.yRot = -1.7278761F; leftTip.yRot = -rightTip.yRot;
            // Old renderer's hanging offsets are measured before its per-model 1/16 conversion.
            head.y += 1F; rightWing.y -= 1F; leftWing.y -= 1F;
        } else {
            head.yRot = yaw * Mth.DEG_TO_RAD;
            body.xRot = Mth.PI / 4 + Mth.cos(age * .1F) * .15F;
            rightWing.yRot = Mth.cos(age * 1.3F) * Mth.PI * .25F; leftWing.yRot = -rightWing.yRot;
            rightTip.yRot = rightWing.yRot * .5F; leftTip.yRot = -rightWing.yRot * .5F;
        }
    }
}
