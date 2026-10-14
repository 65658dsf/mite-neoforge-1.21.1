package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One R196 pass per level replaces vanilla chunk passes and initial population. @author NingMeng123 */
@Mixin(NaturalSpawner.class)
public abstract class R196NaturalSpawnerMixin {
    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void mite$sourceOwnsNaturalSpawning(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state,
            boolean hostile, boolean peaceful, boolean animals, CallbackInfo ci) {
        if (R196MobSpawning.manages(level)) ci.cancel();
    }
    @Inject(method = "spawnMobsForChunkGeneration", at = @At("HEAD"), cancellable = true)
    private static void mite$sourceInitialPopulation(ServerLevelAccessor level, Holder<Biome> biome, ChunkPos chunk, RandomSource random, CallbackInfo ci) {
        if (!R196MobSpawning.manages(level.getLevel())) return;
        R196MobSpawning.initialPopulation(level, biome, chunk, random);
        ci.cancel();
    }
}
