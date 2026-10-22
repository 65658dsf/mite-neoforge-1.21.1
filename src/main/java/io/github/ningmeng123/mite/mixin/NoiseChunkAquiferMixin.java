package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.OverworldAquiferAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Normal generation and temporary noise columns receive the same dimension policy. @author NingMeng123 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkAquiferMixin {
    @Shadow @Final private Aquifer aquifer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mite$inheritAquiferPolicy(int cellCount, RandomState randomState, int firstX, int firstZ,
            NoiseSettings noiseSettings, DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings settings,
            Aquifer.FluidPicker fluidPicker, Blender blender, CallbackInfo callback) {
        if (aquifer instanceof OverworldAquiferAccess access)
            access.mite$setUndergroundWaterReduction(((OverworldAquiferAccess) (Object) randomState).mite$undergroundWaterReduction());
    }
}
