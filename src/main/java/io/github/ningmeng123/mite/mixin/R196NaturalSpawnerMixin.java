package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** R196 owns land spawning while aquatic categories retain native scheduling and budgets. @author NingMeng123 */
@Mixin(NaturalSpawner.class)
public abstract class R196NaturalSpawnerMixin {
    @Shadow @Final private static MobCategory[] SPAWNING_CATEGORIES;
    @Unique private static final MobCategory[] mite$aquaticCategories = {
            MobCategory.AXOLOTLS, MobCategory.UNDERGROUND_WATER_CREATURE,
            MobCategory.WATER_CREATURE, MobCategory.WATER_AMBIENT
    };

    @Redirect(method = "spawnForChunk", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/NaturalSpawner;SPAWNING_CATEGORIES:[Lnet/minecraft/world/entity/MobCategory;"))
    private static MobCategory[] mite$nativeAquaticSpawning(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state,
            boolean friendly, boolean hostile, boolean animals) {
        return R196MobSpawning.manages(level) ? mite$aquaticCategories : SPAWNING_CATEGORIES;
    }
    @Inject(method = "spawnMobsForChunkGeneration", at = @At("HEAD"), cancellable = true)
    private static void mite$sourceInitialPopulation(ServerLevelAccessor level, Holder<Biome> biome, ChunkPos chunk, RandomSource random, CallbackInfo ci) {
        if (!R196MobSpawning.manages(level.getLevel())) return;
        R196MobSpawning.initialPopulation(level, biome, chunk, random);
        ci.cancel();
    }
}
