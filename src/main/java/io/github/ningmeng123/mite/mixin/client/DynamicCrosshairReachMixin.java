package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Dynamic Crosshair 9.11 entity attack indicators follow live MITE reach. @author NingMeng123 */
@Pseudo
@Mixin(targets = "mod.crend.dynamiccrosshair.component.CrosshairHandler", remap = false)
public abstract class DynamicCrosshairReachMixin {
    @Unique private static boolean mite$reachableEntity;

    @WrapOperation(method = "buildCrosshair", at = @At(value = "INVOKE",
            target = "Lmod/crend/dynamiccrosshair/component/State;changed(Lnet/minecraft/world/phys/HitResult;Lnet/minecraft/client/player/LocalPlayer;)Z"), require = 1)
    private static boolean mite$refreshAttackReach(@Coerce Object state, HitResult hit, LocalPlayer player,
            Operation<Boolean> original) {
        boolean reachable = hit instanceof EntityHitResult entity && SourcePlayerReach.canTargetMelee(player, entity, 1F);
        boolean reachChanged = reachable != mite$reachableEntity;
        mite$reachableEntity = reachable;
        boolean stateChanged = original.call(state, hit, player);
        return stateChanged || reachChanged;
    }

    @ModifyExpressionValue(method = "buildCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;"), require = 1)
    private static HitResult.Type mite$attackableEntityType(HitResult.Type type) {
        return type == HitResult.Type.ENTITY && !mite$reachableEntity ? HitResult.Type.MISS : type;
    }
}
