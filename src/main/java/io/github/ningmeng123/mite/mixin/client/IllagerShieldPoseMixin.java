package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.client.MonsterShieldPoses;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.AbstractIllager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vindicators unfold their arms and raise the shield while blocking. @author NingMeng123 */
@Mixin(IllagerModel.class)
public abstract class IllagerShieldPoseMixin {
    @Shadow @Final private ModelPart head;
    @Shadow @Final private ModelPart arms;
    @Shadow @Final private ModelPart rightArm;
    @Shadow @Final private ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/AbstractIllager;FFFFF)V", at = @At("TAIL"))
    private void mite$showIllagerShieldBlock(AbstractIllager entity, float swing, float speed, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!MonsterShieldPoses.active(entity)) return;
        arms.visible = false;
        leftArm.visible = true;
        rightArm.visible = true;
        MonsterShieldPoses.apply(head, rightArm, leftArm, entity);
    }
}
