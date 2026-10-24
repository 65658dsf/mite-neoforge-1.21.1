package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** Equipment finalization tests must opt into the existing day35 equipment unlock explicitly. @author NingMeng123 */
final class SourceEquipmentDateFixture implements AutoCloseable {
    private final ServerLevel level;
    private final long time;
    private final long highest;
    SourceEquipmentDateFixture(GameTestHelper helper) {
        level = helper.getLevel().getServer().overworld();
        time = level.getDayTime();
        highest = BloodMoonInvasionData.get(level).highestDay();
        level.setDayTime(34L * 24000L + 18000L);
        BloodMoonInvasionData.get(level).restoreProgress(35L);
    }
    @Override public void close() {
        level.setDayTime(time);
        BloodMoonInvasionData.get(level).restoreProgress(highest);
    }
}
