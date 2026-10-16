package io.github.ningmeng123.mite.mixin.client;

import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** R196 selects the nearest collision in a 16-block ray, then checks its target-specific reach. @author NingMeng123 */
@Mixin(GameRenderer.class)
public abstract class SourceReachSelectionMixin {
    @Shadow @Final private Minecraft minecraft;

    @ModifyArgs(method = "pick(F)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;"))
    private void mite$sourceSelectionRay(Args args) { args.set(1, 16D); args.set(2, 16D); }

    @Inject(method = "pick(F)V", at = @At("RETURN"))
    private void mite$filterTargetReach(float partialTick, CallbackInfo ci) {
        var player = minecraft.player;
        HitResult hit = minecraft.hitResult;
        if (player == null || hit == null || hit.getType() == HitResult.Type.MISS) return;
        boolean reachable;
        if (hit instanceof BlockHitResult block) {
            reachable = SourcePlayerReach.canReachBlock(player, block.getBlockPos(), InteractionHand.MAIN_HAND, partialTick)
                    || SourcePlayerReach.canReachBlock(player, block.getBlockPos(), InteractionHand.OFF_HAND, partialTick);
        } else if (hit instanceof EntityHitResult entity) {
            reachable = SourcePlayerReach.canReachEntityAt(player, entity.getEntity(), hit.getLocation(), true, partialTick)
                    || SourcePlayerReach.canReachEntityAt(player, entity.getEntity(), hit.getLocation(), false, partialTick);
        } else return;
        if (!reachable) {
            var point = hit.getLocation();
            var direction = player.getViewVector(partialTick);
            minecraft.hitResult = BlockHitResult.miss(point,
                    Direction.getNearest(direction.x, direction.y, direction.z), net.minecraft.core.BlockPos.containing(point));
            minecraft.crosshairPickEntity = null;
        }
    }
}
