package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.worldgen.TrialBlockDrops;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** Cover support loss and other native destroyBlock callers without cancelling destruction. @author NingMeng123 */
@Mixin(Level.class)
public abstract class TrialLevelBlockDestructionMixin {
    @WrapMethod(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z")
    private boolean mite$naturalTrialLevelBreak(BlockPos pos, boolean drops, @Nullable Entity breaker,
            int recursion, Operation<Boolean> original) {
        try (var ignored = TrialBlockDrops.destruction((Level)(Object)this, pos)) {
            return original.call(pos, drops, breaker, recursion);
        }
    }
}
