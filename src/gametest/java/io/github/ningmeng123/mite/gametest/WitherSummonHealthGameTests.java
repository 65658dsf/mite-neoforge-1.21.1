package io.github.ningmeng123.mite.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Summon charge and combat health through native server ticks. @author NingMeng123 */
@GameTestHolder("mite_gameplay")
@PrefixGameTestTemplate(false)
public final class WitherSummonHealthGameTests {
    private WitherSummonHealthGameTests() {}

    @GameTest(template = "empty")
    public static void highHealthWitherFinishesSummoningAtFullHealth(GameTestHelper helper) {
        var level = helper.getLevel();
        WitherBoss wither = EntityType.WITHER.create(level);
        helper.assertTrue(wither != null, "The native Wither can be constructed");
        try {
            boolean ite = ModList.get().isLoaded("modern_ite");
            if (ite) {
                // Admit this explicit summon even in a new GameTest world's first-day grace period.
                wither.getPersistentData().putBoolean("modern_ite:explicit_command_spawn", true);
            } else {
                wither.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024D);
            }
            // Run the explosion above the test structures and follow the skull ritual's initialization order.
            wither.moveTo(helper.absolutePos(new BlockPos(2, 40, 2)).getCenter());
            wither.setNoGravity(true);
            wither.makeInvulnerable();
            helper.assertTrue(level.addFreshEntity(wither), "The summoned Wither joins the server level");
            float maximum = ite ? 1300F : 1024F;
            helper.assertValueEqual(wither.getMaxHealth(), maximum, "Effective maximum after all spawn handlers");
            helper.assertValueEqual(wither.getInvulnerableTicks(), 220, "The ordinary skull charge lasts 220 ticks");
            float startingHealth = wither.getHealth();
            for (int tick = 0; tick < 110; tick++) level.tickNonPassenger(wither);
            helper.assertTrue(wither.getHealth() > startingHealth && wither.getHealth() < maximum,
                    "Health increases during the first half of the summon charge");
            for (int tick = 110; tick < 219; tick++) level.tickNonPassenger(wither);
            helper.assertValueEqual(wither.getInvulnerableTicks(), 1, "The explosion is still one tick away");
            level.tickNonPassenger(wither);
            helper.assertValueEqual(wither.getInvulnerableTicks(), 0, "The summon charge has finished");
            helper.assertValueEqual(wither.getHealth(), maximum, "Combat begins at full effective health");

            helper.assertTrue(wither.hurt(wither.damageSources().generic(), 30F), "The active Wither takes damage");
            float woundedHealth = wither.getHealth();
            helper.assertTrue(woundedHealth < maximum, "The combat hit leaves an actual wound");
            level.tickNonPassenger(wither);
            helper.assertValueEqual(wither.getHealth(), woundedHealth, "The next combat tick preserves the wound");
        } finally {
            wither.discard();
        }
        helper.succeed();
    }
}
