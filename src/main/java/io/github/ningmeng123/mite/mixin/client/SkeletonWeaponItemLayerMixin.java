package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.entity.SkeletonWeaponDisplay;
import io.github.ningmeng123.mite.entity.SkeletonWeapons;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Render the active weapon, then stow it before drawing its replacement. @author NingMeng123 */
@Mixin(ItemInHandLayer.class)
public abstract class SkeletonWeaponItemLayerMixin {
    @WrapMethod(method = "renderArmWithItem")
    private void mite$renderOnlyActiveWeapon(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
            HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        if (!(entity instanceof AbstractSkeleton skeleton) || !SkeletonWeapons.eligible(skeleton)) {
            original.call(entity, stack, context, arm, pose, buffers, light);
            return;
        }
        if (arm != skeleton.getMainArm()) {
            if (!SkeletonWeaponDisplay.hidesOffhand(skeleton)) original.call(entity, stack, context, arm, pose, buffers, light);
            return;
        }
        float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        var display = SkeletonWeaponDisplay.frame(skeleton, skeleton.tickCount + partial);
        if (display.stack().isEmpty()) return;
        pose.pushPose();
        try {
            pose.translate(0D, .15D * display.lowering(), .1D * display.lowering());
            original.call(entity, display.stack(), context, arm, pose, buffers, light);
        } finally { pose.popPose(); }
    }
}
