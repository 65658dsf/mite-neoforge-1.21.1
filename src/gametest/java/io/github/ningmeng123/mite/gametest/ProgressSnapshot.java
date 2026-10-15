package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import net.minecraft.server.level.ServerLevel;

/** Test-only isolation for source-era fixtures sharing persistent modern world progression. @author NingMeng123 */
final class ProgressSnapshot implements AutoCloseable {
    private final ServerLevel overworld;
    private final BloodMoonInvasionData original;
    private final long dayTime;
    private final long highestDay;
    private final boolean dirty;

    ProgressSnapshot(ServerLevel level) {
        overworld = level.getServer().overworld();
        original = BloodMoonInvasionData.get(overworld);
        dayTime = overworld.getDayTime(); highestDay = original.highestDay(); dirty = original.isDirty();
    }

    /** Construct the pre-second-blood-moon source branch without weakening any source probability assertion. */
    static ProgressSnapshot earlySource(ServerLevel level) {
        ProgressSnapshot saved = new ProgressSnapshot(level);
        saved.overworld.setDayTime(0L);
        saved.overworld.updateSkyBrightness();
        saved.original.restoreProgress(1L);
        return saved;
    }

    @Override public void close() {
        // Restore the clock first: no later cleanup should observe a temporary future date
        // and immediately promote the maximum after this exact progression restore.
        overworld.setDayTime(dayTime);
        overworld.updateSkyBrightness();
        overworld.getDataStorage().set(BloodMoonInvasionData.NAME, original);
        original.restoreProgress(highestDay);
        original.setDirty(dirty);
        BloodMoonInvasionData.get(overworld); // Publish the restored read-only snapshot without observing the clock.
    }
}
