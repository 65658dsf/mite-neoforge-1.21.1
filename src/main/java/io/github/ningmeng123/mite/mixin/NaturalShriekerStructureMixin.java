package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.worldgen.NaturalShriekerOres;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

/** Ancient-city rewards wait until every intersecting native piece has finished writing. @author NingMeng123 */
@Mixin(StructureStart.class)
public abstract class NaturalShriekerStructureMixin {
    @WrapMethod(method = "placeInChunk")
    private void mite$ancientCityOres(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox bounds, ChunkPos chunk, Operation<Void> original) {
        try (var scope = NaturalShriekerOres.structure(level, ((StructureStart)(Object)this).getStructure())) {
            original.call(level, structures, generator, random, bounds, chunk);
            scope.finish();
        }
    }
}
