package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.entity.PiglinBruteReinforcements;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the reinforcement wave through a real brute server tick. @author NingMeng123 */
@GameTestHolder("mite_spawning")
@PrefixGameTestTemplate(false)
public final class PiglinBruteReinforcementGameTests {
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bruteSummonsOneBoostedWaveThatSurvivesAnOverworldSave(GameTestHelper helper) {
        var site = new DaylightUndeadSpawnGameTests.Site(helper.getLevel(), true, 100);
        site.keepLoaded(helper);
        helper.startSequence().thenWaitUntil(() -> site.requireLightingReady(helper)).thenExecute(() -> {
            try (site; var progress = new DaylightUndeadSpawnGameTests.Progress(site.level)) {
                progress.time(159L * 24_000L + 18_000L);
                progress.data.restoreProgress(160L);
                var brute = (PiglinBrute)site.mob(EntityType.PIGLIN_BRUTE);
                brute.setNoAi(false);
                helper.assertTrue(site.level.addFreshEntity(brute), "The brute enters the test world");
                brute.tickCount = 9;
                site.level.tickNonPassenger(brute);
                helper.assertTrue(reinforcements(site, brute.getUUID()).isEmpty(), "An idle brute does not summon");

                var target = site.mob(EntityType.WITHER_SKELETON);
                target.moveTo(brute.getX() + 2D, brute.getY(), brute.getZ() + 2D, 0F, 0F);
                helper.assertTrue(site.level.addFreshEntity(target), "The combat target joins the test world");
                tickWithTarget(site, brute, target);
                List<Piglin> wave = reinforcements(site, brute.getUUID());
                site.mobs.addAll(wave);
                helper.assertValueEqual(wave.size(), 5, "The available floor allows exactly five reinforcements");

                var ordinary = (Piglin)site.mob(EntityType.PIGLIN);
                MonsterDayScaling.refresh(ordinary);
                for (var summon : wave) {
                    helper.assertTrue(summon.getSpawnType() == MobSpawnType.MOB_SUMMONED && summon.isAdult()
                                    && summon.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == target,
                            "The summoned adult piglin fights the brute's target");
                    helper.assertFalse(summon.isConverting(), "An Overworld reinforcement is immune to zombification");
                    for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE,
                            Attributes.MOVEMENT_SPEED, Attributes.FOLLOW_RANGE)) {
                        helper.assertTrue(Math.abs(summon.getAttributeBaseValue(attribute) - ordinary.getAttributeBaseValue(attribute) * 1.5D) < 1E-6D,
                                "The summoned piglin's base attribute is 1.5 times the natural value: " + attribute);
                    }
                    helper.assertTrue(Math.abs(summon.getMaxHealth() - ordinary.getMaxHealth() * 1.5F) < 1E-4F
                                    && Math.abs(summon.getHealth() - summon.getMaxHealth()) < 1E-4F,
                            "The summon starts at 1.5 times natural health even after normal date scaling");
                    double ordinaryAttack = MonsterDayScaling.attackBase(ordinary, ordinary.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
                    double summonedAttack = MonsterDayScaling.attackBase(summon, summon.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
                    helper.assertTrue(Math.abs(summonedAttack - ordinaryAttack * 1.5D) < 1E-6D,
                            "Date scaling preserves the summon attack multiplier");
                }

                var savedSummon = wave.getFirst().saveWithoutId(new CompoundTag());
                var restoredSummon = (Piglin)site.mob(EntityType.PIGLIN);
                restoredSummon.load(savedSummon);
                restoredSummon.setNoAi(false);
                helper.assertTrue(!restoredSummon.isConverting()
                                && restoredSummon.getAttributeBaseValue(Attributes.MAX_HEALTH) == wave.getFirst().getAttributeBaseValue(Attributes.MAX_HEALTH),
                        "Saving the summoned piglin preserves its immunity and upgraded attributes");

                wave.forEach(Piglin::kill);
                tickWithTarget(site, brute, target);
                helper.assertTrue(reinforcements(site, brute.getUUID()).isEmpty(), "Dead reinforcements are never replaced");

                var savedBrute = brute.saveWithoutId(new CompoundTag());
                brute.discard();
                var restoredBrute = (PiglinBrute)site.mob(EntityType.PIGLIN_BRUTE);
                restoredBrute.load(savedBrute);
                helper.assertTrue(site.level.addFreshEntity(restoredBrute), "The brute returns from the save");
                tickWithTarget(site, restoredBrute, target);
                helper.assertTrue(reinforcements(site, restoredBrute.getUUID()).isEmpty(), "Reloading the brute cannot restore the one-time wave");
            }
        }).thenSucceed();
    }

    private static void tickWithTarget(DaylightUndeadSpawnGameTests.Site site, PiglinBrute brute, LivingEntity target) {
        brute.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), 600L);
        brute.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        brute.tickCount = 9;
        site.level.tickNonPassenger(brute);
    }

    private static List<Piglin> reinforcements(DaylightUndeadSpawnGameTests.Site site, UUID owner) {
        return site.level.getEntitiesOfClass(Piglin.class, new AABB(site.pos).inflate(8D), piglin -> piglin.isAlive()
                && piglin.getPersistentData().hasUUID(PiglinBruteReinforcements.SUMMONER)
                && owner.equals(piglin.getPersistentData().getUUID(PiglinBruteReinforcements.SUMMONER)));
    }
}
