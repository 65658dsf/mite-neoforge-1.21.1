package io.github.ningmeng123.mite.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused original-reference integration checks; shared real-entity fixtures. @author NingMeng123 */
@GameTestHolder("mite_spawning")
@PrefixGameTestTemplate(false)
public final class ReferenceSpawningGameTests {
    private ReferenceSpawningGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void independentHeightAllowances(GameTestHelper helper) {
        HostilePopulationGameTests.referencePresetRestoresIndependentR196HeightAllowances(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void strictNearbyCrowdThresholds(GameTestHelper helper) {
        HostilePopulationGameTests.referenceNearbyCrowdGateUsesStrictEightTenAndTwelveThresholds(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bloodDayAndLateDateOriginalPools(GameTestHelper helper) {
        HostilePopulationGameTests.referenceBloodDayStillRejectsOutdoorUndeadAndLateDaysDoNotExpandSourcePools(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void acceleratedThreeSeededNights(GameTestHelper helper) {
        HostilePopulationGameTests.referenceThreeSeededNightsStopReplenishingCrowdedAreas(helper);
    }
}
