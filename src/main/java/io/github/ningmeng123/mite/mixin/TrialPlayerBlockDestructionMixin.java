package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.worldgen.TrialBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Preserve provenance until playerDestroy has finished producing loot. @author NingMeng123 */
@Mixin(ServerPlayerGameMode.class)
public abstract class TrialPlayerBlockDestructionMixin {
    @Shadow protected ServerLevel level;

    @WrapMethod(method = "destroyBlock")
    private boolean mite$naturalTrialPlayerBreak(BlockPos pos, Operation<Boolean> original) {
        try (var ignored = TrialBlockDrops.destruction(level, pos)) {
            return original.call(pos);
        }
    }
}
