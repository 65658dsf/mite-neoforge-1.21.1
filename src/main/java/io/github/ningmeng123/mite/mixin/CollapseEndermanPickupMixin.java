package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.world.CollapseSecondaryTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** tg.c: picking up a support explicitly tests the one block above it. @author NingMeng123 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class CollapseEndermanPickupMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean mite$pickedUpSupport(Level level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        boolean removed = original.call(level, pos, moving);
        if (removed) CollapseSecondaryTriggers.removedSupport(level, pos);
        return removed;
    }
}
