package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Pair;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StrongholdRecovery;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic gates stay outside StructureCheck's permanent candidate cache. @author NingMeng123 */
@Mixin(ChunkGenerator.class)
public abstract class StructureProgressGateMixin {
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void mite$gateNewStructureStart(StructureSet.StructureSelectionEntry entry, StructureManager structures,
            RegistryAccess registry, RandomState random, StructureTemplateManager templates, long seed,
            ChunkAccess chunk, ChunkPos coordinate, SectionPos section, CallbackInfoReturnable<Boolean> cir) {
        LevelAccessor owner = ((StructureManagerAccess) structures).mite$structureLevel();
        ServerLevel level = owner instanceof ServerLevel server ? server
                : owner instanceof WorldGenRegion region ? region.getLevel() : null;
        if (level != null && !R196StructureGeneration.canGenerate(level, entry.structure(), coordinate))
            cir.setReturnValue(false);
    }

    @WrapMethod(method = "findNearestMapStructure")
    private Pair<BlockPos, Holder<Structure>> mite$filterLockedStructureSearch(ServerLevel level,
            HolderSet<Structure> requested, BlockPos origin, int radius, boolean skipReferenced,
            Operation<Pair<BlockPos, Holder<Structure>>> original) {
        StrongholdRecovery.recover(level);
        var allowed = requested.stream().filter(holder -> R196StructureGeneration.unlocked(level, holder)).toList();
        if (allowed.isEmpty()) return null;
        return original.call(level, allowed.size() == requested.size() ? requested : HolderSet.direct(allowed),
                origin, radius, skipReferenced);
    }

    @WrapMethod(method = "getStructureGeneratingAt")
    private static Pair<BlockPos, Holder<Structure>> mite$filterDynamicCandidateLocation(Set<Holder<Structure>> requested,
            LevelReader reader, StructureManager structures, boolean skipReferenced, StructurePlacement placement,
            ChunkPos coordinate, Operation<Pair<BlockPos, Holder<Structure>>> original) {
        ServerLevel level = reader instanceof ServerLevel server ? server
                : reader instanceof WorldGenRegion region ? region.getLevel() : null;
        if (level == null) return original.call(requested, reader, structures, skipReferenced, placement, coordinate);
        Set<Holder<Structure>> allowed = new LinkedHashSet<>();
        for (Holder<Structure> holder : requested)
            if (R196StructureGeneration.canGenerate(level, holder, coordinate)) allowed.add(holder);
        if (allowed.isEmpty()) return null;
        return original.call(allowed.size() == requested.size() ? requested : allowed,
                reader, structures, skipReferenced, placement, coordinate);
    }
}
