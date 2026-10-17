package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.registry.MiteShields;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;

/** Reapply blocking after native zombie/skeleton attack animations move both arms. @author NingMeng123 */
public final class MonsterShieldPoses {
    private MonsterShieldPoses() {}

    public static boolean active(LivingEntity entity) {
        return entity instanceof Monster && entity.isUsingItem() && MiteShields.definition(entity.getUseItem()) != null;
    }

    public static void apply(HumanoidModel<?> model, LivingEntity entity) {
        apply(model.head, model.rightArm, model.leftArm, entity);
    }

    public static void apply(ModelPart head, ModelPart rightArm, ModelPart leftArm, LivingEntity entity) {
        if (!active(entity)) return;
        HumanoidArm arm = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart shieldArm = right ? rightArm : leftArm;
        shieldArm.xRot = -.9424779F + Mth.clamp(head.xRot, -1.3962634F, .43633232F);
        shieldArm.yRot = (right ? -.5235988F : .5235988F) + Mth.clamp(head.yRot, -.5235988F, .5235988F);
        shieldArm.zRot = 0F;
    }
}
