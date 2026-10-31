package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.client.BoatWaterMaskCompatibility;
import net.minecraft.client.model.WaterPatchModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve the hull animation while keeping its water mask below the rider. @author NingMeng123 */
@Mixin(BoatRenderer.class)
public abstract class BoatWaterMaskMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/WaterPatchModel;waterPatch()Lnet/minecraft/client/model/geom/ModelPart;"))
    private ModelPart mite$protectBoatPassenger(WaterPatchModel model, Operation<ModelPart> original,
            Boat boat, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        return BoatWaterMaskCompatibility.select(original.call(model),
                boat.getPassengers().stream().anyMatch(Player.class::isInstance));
    }
}
