package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.CollapseSecondaryTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The modern equivalent of BlockOperation.spawn_silverfish's post-removal support check. @author NingMeng123 */
@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishWakeUpFriendsGoal")
public abstract class CollapseSilverfishWakeMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean mite$releasedInhabitantSupport(Level level, BlockPos pos, boolean drops, Entity cause,
            Operation<Boolean> original) {
        boolean removed = original.call(level, pos, drops, cause);
        if (removed) CollapseSecondaryTriggers.removedSupport(level, pos);
        return removed;
    }
}
