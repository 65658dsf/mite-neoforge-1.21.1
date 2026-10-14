package io.github.ningmeng123.mite.client.mob;

import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.BowItem;

/** The source bow pose applies to material bows as well as minecraft:bow. @author NingMeng123 */
public final class LegacySkeletonModel<T extends AbstractSkeleton> extends SkeletonModel<T> {
    public LegacySkeletonModel(ModelPart root) { super(root); }
    @Override public void setupAnim(T mob, float swing, float speed, float age, float yaw, float pitch) {
        super.setupAnim(mob,swing,speed,age,yaw,pitch);
        if (mob.isAggressive() && mob.getMainHandItem().getItem() instanceof BowItem) {
            rightArm.zRot = 0; leftArm.zRot = 0;
            rightArm.xRot = -Mth.HALF_PI + head.xRot; leftArm.xRot = rightArm.xRot;
            if (mob.getMainArm() == HumanoidArm.RIGHT) {
                rightArm.yRot = -.1F + head.yRot; leftArm.yRot = .5F + head.yRot;
            } else { rightArm.yRot = -.5F + head.yRot; leftArm.yRot = .1F + head.yRot; }
        }
    }
}
