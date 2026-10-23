package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import net.minecraft.server.level.ServerLevel;

/** Synchronous blood-night fixture; always restores shared world time. @author NingMeng123 */
final class BloodMoonHuntingTestClock implements AutoCloseable {
    static final long BLOOD_NIGHT = 31L * 24_000L + 18_000L;
    private final ServerLevel level;
    private final long oldTime;
    private final long oldHighestDay;
    private final double oldGrowth;
    BloodMoonHuntingTestClock(ServerLevel level) {
        this.level = level; oldTime = level.getDayTime();
        oldHighestDay = BloodMoonInvasionData.get(level).highestDay();
        oldGrowth = MiteConfig.MONSTER_GROWTH.get();
        MiteConfig.MONSTER_GROWTH.set(0D); MiteConfig.refreshEnchantmentRules();
        level.setDayTime(BLOOD_NIGHT);
    }
    @Override public void close() {
        level.setDayTime(oldTime);
        BloodMoonInvasionData.get(level).restoreProgress(oldHighestDay);
        MiteConfig.MONSTER_GROWTH.set(oldGrowth); MiteConfig.refreshEnchantmentRules();
    }
}
