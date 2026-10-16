package io.github.ningmeng123.mite.worldgen;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.UnderworldSpringRules;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** acu's 70 spring candidates and afw's three-stone/one-air cavity, in the adapted depth bands. @author NingMeng123 */
public final class UnderworldSpringGeneration {
    private static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,Mite.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<Boolean>> NATURAL_MYCELIUM=TYPES.register("natural_underworld_mycelium",
            ()->AttachmentType.builder(()->false).serialize(Codec.BOOL).build());
    private UnderworldSpringGeneration() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
    private static ChunkAccess existing(LevelReader level,int x,int z) {
        if (level instanceof ServerLevel server) return server.getChunkSource().getChunkNow(x,z);
        if (level instanceof WorldGenRegion region && region.hasChunk(x,z)) return region.getChunk(x,z,ChunkStatus.EMPTY,false);
        return null;
    }
    public static void markMycelium(WorldGenLevel level,BlockPos pos) {
        ChunkAccess chunk=existing(level,pos.getX()>>4,pos.getZ()>>4);
        if (chunk!=null) { chunk.setData(NATURAL_MYCELIUM.get(),true);chunk.setUnsaved(true); }
    }
    public static boolean neighboringMycelium(LevelReader level,int chunkX,int chunkZ) {
        for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
            ChunkAccess chunk=existing(level,chunkX+dx,chunkZ+dz);
            if (chunk!=null && Boolean.TRUE.equals(chunk.getExistingDataOrNull(NATURAL_MYCELIUM.get()))) return true;
        }
        return false;
    }
    public static int decorate(WorldGenLevel level,int chunkX,int chunkZ,long seed) {
        if (neighboringMycelium(level,chunkX,chunkZ)) return 0;
        // Dedicated deterministic stream avoids sharing mutable decorator RNG between modern generation threads.
        Random random=new Random(seed ^ R196UnderworldTerrain.chunkHash(chunkX,chunkZ) ^ 0x535052494e4753L);
        int placed=0;
        for (int i=0;i<70;i++) {
            var c=UnderworldSpringRules.candidate(random,chunkX,chunkZ);
            BlockPos pos=new BlockPos(c.x(),ModernUnderworldTerrain.fromLegacyY(c.legacyY()+120),c.z());
            if (generate(level,pos,c.water())) placed++;
        }
        return placed;
    }
    public static boolean generate(WorldGenLevel level,BlockPos pos,boolean water) {
        if (level.isOutsideBuildHeight(pos.above()) || level.isOutsideBuildHeight(pos.below())
                || !level.getBlockState(pos.above()).is(Blocks.STONE) || !level.getBlockState(pos.below()).is(Blocks.STONE)
                || !level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(Blocks.STONE)) return false;
        int stone=0,air=0;
        for (Direction direction:Direction.Plane.HORIZONTAL) {
            var state=level.getBlockState(pos.relative(direction));
            if (state.is(Blocks.STONE)) stone++;
            if (state.isAir()) air++;
        }
        if (stone!=3 || air!=1) return false;
        var fluid=water?Fluids.WATER:Fluids.LAVA;
        if (!level.setBlock(pos,fluid.defaultFluidState().createLegacyBlock(),2)) return false;
        level.scheduleTick(pos,fluid,fluid.getTickDelay(level));
        return true;
    }
}
