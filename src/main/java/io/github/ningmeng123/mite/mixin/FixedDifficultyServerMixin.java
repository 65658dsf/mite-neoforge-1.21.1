package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.world.DifficultySystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Normalize old saves and server.properties, while retaining native spawn flags and client packets. @author NingMeng123 */
@Mixin(MinecraftServer.class)
public abstract class FixedDifficultyServerMixin {
    @Shadow @Final protected WorldData worldData;

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void mite$initialExtreme(ChunkProgressListener progress, CallbackInfo ci) {
        DifficultySystem.normalize(worldData);
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void mite$keepExtremeAfterDebugSetup(ChunkProgressListener progress, CallbackInfo ci) {
        DifficultySystem.normalize(worldData);
    }

    @WrapMethod(method = "setDifficulty")
    private void mite$fixedExtreme(Difficulty requested, boolean force, Operation<Void> original) {
        // The native setter publishes one DifficultyChangeEvent and one client snapshot per request.
        worldData.setDifficultyLocked(true);
        original.call(Difficulty.HARD, true);
    }

    @WrapMethod(method = "setDifficultyLocked")
    private void mite$fixedLock(boolean requested, Operation<Void> original) {
        DifficultySystem.normalize(worldData);
        original.call(true);
    }
}
