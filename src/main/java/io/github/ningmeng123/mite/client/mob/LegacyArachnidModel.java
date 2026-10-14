package io.github.ningmeng123.mite.client.mob;

import io.github.ningmeng123.mite.entity.R196Spider;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/** ModelArachnid dimensions and unmirrored left-leg UVs; modern SpiderModel supplies the identical gait. @author NingMeng123 */
public final class LegacyArachnidModel extends SpiderModel<R196Spider> {
    public LegacyArachnidModel(ModelPart root) { super(root); }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(32,4).addBox(-4,-4,-8,8,8,8), PartPose.offset(0,15,-3));
        root.addOrReplaceChild("body0", CubeListBuilder.create().texOffs(0,0).addBox(-3,-3,-3,6,6,6), PartPose.offset(0,15,0));
        root.addOrReplaceChild("body1", CubeListBuilder.create().texOffs(0,12).addBox(-5,-4,-6,10,8,12), PartPose.offset(0,15,9));
        String[] pairs = {"hind", "middle_hind", "middle_front", "front"};
        for (int i = 0; i < pairs.length; ++i) {
            root.addOrReplaceChild("right_" + pairs[i] + "_leg", CubeListBuilder.create().texOffs(18,0).addBox(-15,-1,-1,16,2,2),
                    PartPose.offset(-4,15,2-i));
            root.addOrReplaceChild("left_" + pairs[i] + "_leg", CubeListBuilder.create().texOffs(18,0).addBox(-1,-1,-1,16,2,2),
                    PartPose.offset(4,15,2-i));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
}
