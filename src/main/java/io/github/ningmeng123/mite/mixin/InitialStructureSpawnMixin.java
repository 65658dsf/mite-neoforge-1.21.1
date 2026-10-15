package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Initial spawn probing generates chunks before the ordinary level tick or spawn setter. @author NingMeng123 */
@Mixin(MinecraftServer.class)
public abstract class InitialStructureSpawnMixin {
    @WrapOperation(method = "setInitialSpawn", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/storage/ServerLevelData;setSpawn(Lnet/minecraft/core/BlockPos;F)V"))
    private static void mite$publishBeforeInitialSpawnSearch(ServerLevelData data, BlockPos pos, float angle,
            Operation<Void> original, ServerLevel level, ServerLevelData initialData, boolean bonus, boolean debug) {
        original.call(data, pos, angle);
        R196StructureGeneration.refresh(level);
    }
}
