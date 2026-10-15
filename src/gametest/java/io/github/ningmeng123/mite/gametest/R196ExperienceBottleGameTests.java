package io.github.ningmeng123.mite.gametest;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real projectile collision verifies the independent ut source value without bypassing NeoForge cancellation. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class R196ExperienceBottleGameTests {
    private R196ExperienceBottleGameTests() {}
    private static ThrownExperienceBottle bottle(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        helper.getLevel().setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 2);
        var bottle = new ThrownExperienceBottle(helper.getLevel(), pos.getX() + .5, pos.getY() + 2D, pos.getZ() + .5);
        bottle.setDeltaMovement(0, -2, 0);
        helper.getLevel().addFreshEntity(bottle);
        return bottle;
    }

    @GameTest(template = "empty")
    public static void actualBottleImpactReleasesExactlyTwoHundredExperiencePoints(GameTestHelper helper) {
        var bottle = bottle(helper);
        AtomicInteger impacts = new AtomicInteger();
        var orbs = new ArrayList<ExperienceOrb>();
        Consumer<EntityJoinLevelEvent> awards = event -> {
            if (event.getEntity() instanceof ExperienceOrb orb && orb.position().distanceToSqr(bottle.position()) < 4D)
                orbs.add(orb);
        };
        Consumer<ProjectileImpactEvent> impact = event -> { if (event.getProjectile() == bottle) impacts.incrementAndGet(); };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, awards);
        NeoForge.EVENT_BUS.addListener(ProjectileImpactEvent.class, impact);
        try {
            bottle.tick();
            helper.assertValueEqual(impacts.get(), 1, "Real movement collision emits the native projectile impact event once");
            helper.assertTrue(bottle.isRemoved(), "Native impact consumes the thrown bottle");
            int points = orbs.stream().mapToInt(orb -> orb.getValue() * orb.saveWithoutId(new CompoundTag()).getInt("Count")).sum();
            helper.assertValueEqual(points, 200, "Source ut releases200 points rather than the modern3..11, including native merged-orb counts");
        } finally {
            NeoForge.EVENT_BUS.unregister(awards);
            NeoForge.EVENT_BUS.unregister(impact);
            bottle.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledBottleImpactDoesNotReleaseExperienceOrConsumeProjectile(GameTestHelper helper) {
        var bottle = bottle(helper);
        AtomicInteger points = new AtomicInteger(), impacts = new AtomicInteger();
        Consumer<EntityJoinLevelEvent> awards = event -> {
            if (event.getEntity() instanceof ExperienceOrb orb && orb.position().distanceToSqr(bottle.position()) < 4D)
                points.addAndGet(orb.getValue());
        };
        Consumer<ProjectileImpactEvent> cancel = event -> {
            if (event.getProjectile() == bottle) { impacts.incrementAndGet(); event.setCanceled(true); }
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, awards);
        NeoForge.EVENT_BUS.addListener(ProjectileImpactEvent.class, cancel);
        try {
            bottle.tick();
            helper.assertValueEqual(impacts.get(), 1, "Actual collision reaches a cancelable NeoForge event");
            helper.assertFalse(bottle.isRemoved(), "Cancellation preserves native projectile flight");
            helper.assertValueEqual(points.get(), 0, "Canceled impact cannot mint stored experience");
        } finally {
            NeoForge.EVENT_BUS.unregister(awards);
            NeoForge.EVENT_BUS.unregister(cancel);
            bottle.discard();
        }
        helper.succeed();
    }
}
