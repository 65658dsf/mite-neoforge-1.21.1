package io.github.ningmeng123.mite.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused entry points for trial provenance after the requested torch-crushing change. @author NingMeng123 */
@GameTestHolder("mite_torch_regression")
@PrefixGameTestTemplate(false)
public final class TorchCrushingRegressionGameTests {
    private TorchCrushingRegressionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void realObstaclesRetainNaturalAndPlayerSandDropRules(GameTestHelper helper) {
        TrialFallingBlockGameTests.blockedNaturalSandLandingNeverBecomesAnItemAndPlayerSandStillDoes(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void crushingTorchesRetainsNaturalAndPlayerSandProvenance(GameTestHelper helper) {
        TrialFallingBlockGameTests.naturalAndPlayerSandCrushTorchesWhileKeepingTheirLandingProvenance(helper);
    }
}
