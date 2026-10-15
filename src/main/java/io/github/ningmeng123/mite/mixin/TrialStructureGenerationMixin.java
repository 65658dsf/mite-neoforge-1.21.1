package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

/** The complete structure placement owns a narrowly scoped generation provenance. @author NingMeng123 */
@Mixin(StructureStart.class)
public abstract class TrialStructureGenerationMixin {
    @WrapMethod(method = "placeInChunk")
    private void mite$trialGeneration(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox bounds, ChunkPos chunk, Operation<Void> original) {
        try (var scope = NaturalTrialBlocks.generation(level, ((StructureStart)(Object)this).getStructure())) {
            original.call(level, structures, generator, random, bounds, chunk);
        }
    }
}
