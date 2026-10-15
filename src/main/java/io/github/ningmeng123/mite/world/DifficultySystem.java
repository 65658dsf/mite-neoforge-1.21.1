package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.rules.R196DifficultyRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.WorldData;

/** Source fixed Extreme and abw.I, using modern dimension heights and native inhabited-time storage. @author NingMeng123 */
public final class DifficultySystem {
    private DifficultySystem() {}

    public static void normalize(WorldData data) {
        if (data.getDifficulty() != Difficulty.HARD) data.setDifficulty(Difficulty.HARD);
        if (!data.isDifficultyLocked()) data.setDifficultyLocked(true);
    }

    /** Reading spawn difficulty must never load or generate a chunk. */
    public static float tension(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return 0F;
        int x = pos.getX() >> 4, z = pos.getZ() >> 4;
        ChunkAccess chunk = level instanceof ServerLevel server
                ? server.getChunkSource().getChunkNow(x, z)
                : level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
        return R196DifficultyRules.tension(level.getDifficulty().getId(), chunk == null ? 0L : chunk.getInhabitedTime(),
                R196DifficultyRules.phaseFraction(level.getDayTime()), chunk != null);
    }
}
