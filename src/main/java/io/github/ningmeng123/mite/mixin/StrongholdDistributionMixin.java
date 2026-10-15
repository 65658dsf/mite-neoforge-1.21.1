package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.rules.StructureDistributionRules;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the native stronghold set uses source coordinates; modern structure pieces remain untouched. @author NingMeng123 */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class StrongholdDistributionMixin {
    @Shadow @Final private RandomState randomState;
    @Shadow @Final private BiomeSource biomeSource;
    @Shadow @Final private long levelSeed;

    @Inject(method = "generateRingPositions", at = @At("HEAD"), cancellable = true)
    private void mite$sourceStrongholdCandidates(Holder<StructureSet> set, ConcentricRingsStructurePlacement placement,
            CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> cir) {
        if (!set.is(BuiltinStructureSets.STRONGHOLDS)) return;
        if (placement.count() == 0) {
            cir.setReturnValue(CompletableFuture.completedFuture(List.of()));
            return;
        }
        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            RandomSource random = RandomSource.create(levelSeed);
            return StructureDistributionRules.strongholdCandidates(random::nextDouble, candidate ->
                    StructureDistributionRules.relocateStrongholdBiome(candidate,
                            position -> placement.preferredBiomes().contains(biomeSource.getNoiseBiome(
                                    position.x(), 0, position.z(), randomState.sampler())), random::nextInt))
                    .stream().map(position -> new ChunkPos(position.x(), position.z())).toList();
        }, Util.backgroundExecutor()));
    }
}
