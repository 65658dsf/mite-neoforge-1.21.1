package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.SkeletonWeaponDisplay;
import io.github.ningmeng123.mite.entity.SkeletonWeapons;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native poses remain authoritative outside the short, client-only weapon exchange. @author NingMeng123 */
@Mixin(SkeletonModel.class)
public abstract class SkeletonWeaponPoseMixin {
    @WrapOperation(method = "prepareMobModel(Lnet/minecraft/world/entity/Mob;FFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mite$posePreparingVisibleWeapon(Mob mob, InteractionHand hand, Operation<ItemStack> original,
            Mob entity, float swing, float speed, float partial) {
        if (hand == InteractionHand.MAIN_HAND && mob instanceof AbstractSkeleton skeleton && SkeletonWeapons.eligible(skeleton))
            return SkeletonWeaponDisplay.frame(skeleton, skeleton.tickCount + partial).stack();
        return original.call(mob, hand);
    }

    @WrapOperation(method = "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mite$poseAnimatingVisibleWeapon(Mob mob, Operation<ItemStack> original,
            Mob entity, float swing, float speed, float age, float yaw, float pitch) {
        if (mob instanceof AbstractSkeleton skeleton && SkeletonWeapons.eligible(skeleton))
            return SkeletonWeaponDisplay.frame(skeleton, age).stack();
        return original.call(mob);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V", at = @At("TAIL"))
    private void mite$lowerArmsDuringWeaponSwap(Mob entity, float swing, float speed, float age, float yaw,
            float pitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractSkeleton skeleton)) return;
        var display = SkeletonWeaponDisplay.frame(skeleton, age);
        if (!display.switching()) return;
        HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
        boolean right = skeleton.getMainArm() == HumanoidArm.RIGHT;
        ModelPart active = right ? model.rightArm : model.leftArm;
        ModelPart other = right ? model.leftArm : model.rightArm;
        float lower = display.lowering();
        active.xRot = Mth.lerp(lower, active.xRot, .6F);
        active.yRot = Mth.lerp(lower, active.yRot, 0F);
        active.zRot = Mth.lerp(lower, active.zRot, right ? -.15F : .15F);
        other.xRot = Mth.lerp(lower, other.xRot, 0F);
        other.yRot = Mth.lerp(lower, other.yRot, 0F);
        other.zRot = Mth.lerp(lower, other.zRot, 0F);
    }
}
