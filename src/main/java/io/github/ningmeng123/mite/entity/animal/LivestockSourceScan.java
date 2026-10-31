package io.github.ningmeng123.mite.entity.animal;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** A single synchronous source scan can skip sections whose palettes contain no possible source. @author NingMeng123 */
final class LivestockSourceScan implements Predicate<BlockPos> {
    private static final byte ABSENT=-1, POSSIBLE=1;
    private final Level level;
    private final Predicate<BlockPos> actualSource;
    private final Predicate<BlockState> possibleSource;
    private final Long2ByteOpenHashMap sections=new Long2ByteOpenHashMap();
    private int paletteQueries,sourceQueries;

    LivestockSourceScan(PathfinderMob mob,boolean water,Predicate<BlockPos> actualSource) {
        level=mob.level();
        this.actualSource=actualSource;
        // Cauldron height remains a per-position decision. A palette verdict only excludes
        // impossible block states, including all water-tagged and waterlogged fluid states.
        possibleSource=water?LivestockSourceScan::possibleWater:state -> Livestock032.foodBlock(mob,state);
    }

    private static boolean possibleWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER) || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.WATER_CAULDRON);
    }

    @Override public boolean test(BlockPos pos) {
        if(level.isOutsideBuildHeight(pos)) return false;
        long sectionKey=SectionPos.asLong(pos);
        byte hint=sections.get(sectionKey);
        if(hint==0) {
            LevelChunk chunk=level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            if(chunk!=null) {
                ++paletteQueries;
                hint=chunk.getSection(chunk.getSectionIndex(pos.getY())).maybeHas(possibleSource)?POSSIBLE:ABSENT;
                sections.put(sectionKey,hint);
            }
            // A not-yet-available chunk provides no palette proof. Keep the original
            // loaded-chunk check authoritative instead of loading terrain for this hint.
        }
        if(hint==ABSENT) return false;
        ++sourceQueries;
        return actualSource.test(pos);
    }

    /** Navigation hooks may change terrain synchronously while attempting a route. */
    void invalidate() { sections.clear(); }
    int paletteQueries() { return paletteQueries; }
    int sourceQueries() { return sourceQueries; }
}
