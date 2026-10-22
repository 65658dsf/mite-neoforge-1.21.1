package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.OverworldAquiferAccess;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Other dimensions keep their own original aquifer policy even when sharing noise settings. @author NingMeng123 */
@Mixin(ServerChunkCache.class)
public abstract class OverworldAquiferLevelMixin {
    @Shadow @Final public ServerLevel level;

    @Shadow public abstract RandomState randomState();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mite$publishDimensionPolicy(CallbackInfo callback) {
        ((OverworldAquiferAccess) (Object) randomState()).mite$setUndergroundWaterReduction(level.dimension().equals(Level.OVERWORLD));
    }
}
