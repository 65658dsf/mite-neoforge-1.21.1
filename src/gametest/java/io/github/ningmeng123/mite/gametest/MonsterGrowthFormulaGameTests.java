package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Configured expressions through real attributes and native armed melee. @author NingMeng123 */
@GameTestHolder("mite_config")
@PrefixGameTestTemplate(false)
public final class MonsterGrowthFormulaGameTests {
    private MonsterGrowthFormulaGameTests() {}

    @GameTest(template = "empty")
    public static void configuredBasesAndExpressionsAffectRealHealthAndOnlyInnateMelee(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (var ignored = new Fixture(helper)) {
            var settings = new MonsterGrowthRules.Settings(40D, 6D, 20D, .005D, .0025D,
                    "base + reference * rate * days * 2", MonsterGrowthRules.DEFAULT_FORMULA);
            apply(settings);
            day(helper, 33L);
            Zombie attacker = (Zombie)mob(helper, EntityType.ZOMBIE);
            attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4D);
            attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            Method updates = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
            updates.setAccessible(true);
            updates.invoke(attacker);
            double nativeAttack = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double weapon = nativeAttack - 4D;
            helper.assertTrue(weapon > 0D, "The fixture's real iron sword installs a native damage modifier");
            MonsterDayScaling.refresh(attacker);
            close(helper, attacker.getMaxHealth(), 52.8D, "Configured health basis and double-rate formula reach the actual attribute");

            Mob target = mob(helper, EntityType.COW);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
            target.setHealth(200F);
            float[] raw = {Float.NaN};
            Consumer<LivingIncomingDamageEvent> capture = event -> {
                if (event.getEntity() == target && event.getSource().getEntity() == attacker) raw[0] = event.getOriginalAmount();
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, capture);
            try {
                helper.assertTrue(attacker.doHurtTarget(target), "A real native zombie melee transaction succeeds");
                close(helper, raw[0], 6D + weapon, "The configured six-damage body stays fixed, then retains the sword's original bonus");
                close(helper, 200D - target.getHealth(), 6D + weapon, "The damage event adds no calendar growth");
            } finally { NeoForge.EVENT_BUS.unregister(capture); }
            close(helper, attacker.getAttributeValue(Attributes.ATTACK_DAMAGE), nativeAttack, "The actual melee attribute remains unchanged");
            close(helper, MonsterDayScaling.attackMultiplier(attacker), 1D, "Other owned attacks ignore legacy growth settings and the fixed melee base");

            apply(MonsterGrowthRules.DEFAULTS);
            MonsterDayScaling.refresh(attacker);
            close(helper, attacker.getMaxHealth(), 23.2D, "Restoring settings recomputes from the native basis without retaining the configured override");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blueMoonKeepsTheOldBasisAndTheFollowingBloodMoonResetsIt(GameTestHelper helper) {
        try (var ignored = new Fixture(helper)) {
            apply(MonsterGrowthRules.DEFAULTS);
            day(helper, 129L);
            Mob monster = mob(helper, EntityType.ZOMBIE);
            MonsterDayScaling.refresh(monster);
            close(helper, monster.getMaxHealth(), 36.2127872D, "Blue-moon day retains the period's thirty-two accumulated increments");
            monster.setHealth(monster.getMaxHealth() / 2F);
            day(helper, 130L);
            MonsterDayScaling.refresh(monster);
            close(helper, monster.getMaxHealth(), 36.3688768D, "The day after a blue moon still adds half percent of day 97's basis");
            day(helper, 161L);
            MonsterDayScaling.refresh(monster);
            close(helper, monster.getMaxHealth(), 41.2076544D, "The next real blood moon closes a sixty-four-day period");
            close(helper, monster.getHealth(), 20.6038272D, "Blood-moon rebasing preserves the actual monster's wound fraction");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void independentBloodMoonExpressionsReachRealAttributesAndSkipBlueMoons(GameTestHelper helper) {
        try (var ignored = new Fixture(helper)) {
            apply(new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                    MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA,
                    "base + 10", "base * 2"));
            day(helper, 33L);
            Mob monster = mob(helper, EntityType.ZOMBIE);
            MonsterDayScaling.refresh(monster);
            close(helper, monster.getMaxHealth(), 30D,
                    "The first blood moon installs the independent health settlement result as the new basis");
            close(helper, MonsterDayScaling.attackMultiplier(monster), 1D,
                    "A legacy attack settlement expression cannot restore calendar damage growth");
            day(helper, 34L);
            MonsterDayScaling.refresh(monster);
            close(helper, monster.getMaxHealth(), 30.15D,
                    "Daily growth after settlement uses the period expression and the newly installed reference");
            day(helper, 129L);
            MonsterDayScaling.refresh(monster);
            close(helper, monster.getMaxHealth(), 58D,
                    "The blue moon leaves three completed blood settlements and thirty-two days of period growth");
            close(helper, MonsterDayScaling.attackMultiplier(monster), 1D,
                    "The blue moon also leaves owned damage unchanged");
            day(helper, 161L);
            MonsterDayScaling.refresh(monster);
            close(helper, monster.getMaxHealth(), 60D,
                    "The next blood moon applies the custom health settlement after the extended period");
            close(helper, MonsterDayScaling.attackMultiplier(monster), 1D,
                    "Later blood moons cannot grow the attack basis through the old formula");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aValidSampledFormulaWithAnUnseenFutureSingularityFallsBackSafelyOnTheRealMob(GameTestHelper helper) {
        try (var ignored = new Fixture(helper)) {
            var settings = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D,
                    "base + 1 / abs(day - 201)", MonsterGrowthRules.DEFAULT_FORMULA);
            helper.assertTrue(MiteConfig.validateMonsterGrowth(settings).valid(), "Representative save-time runs can execute this formula");
            apply(settings);
            day(helper, 201L);
            Mob monster = mob(helper, EntityType.ZOMBIE);
            for (int repeat = 0; repeat < 5; repeat++) MonsterDayScaling.refresh(monster);
            double expected = MonsterGrowthRules.health(201L, 20D, false, MonsterGrowthRules.DEFAULTS, 1D);
            close(helper, monster.getMaxHealth(), expected, "A division by zero for the real date falls back to the default growth");
            helper.assertTrue(Float.isFinite(monster.getHealth()) && monster.getHealth() > 0F,
                    "A late formula domain error cannot install NaN, infinity or nonpositive monster health");
            helper.assertValueEqual(monster.getAttribute(Attributes.MAX_HEALTH).getModifiers().size(), 1,
                    "Repeated fallback refreshes retain one calendar modifier");
        }
        helper.succeed();
    }

    private static Mob mob(GameTestHelper helper, EntityType<?> type) {
        Mob result = (Mob)type.create(helper.getLevel());
        if (result == null) throw new AssertionError("Cannot construct native formula fixture " + type);
        result.setNoAi(true);
        result.setNoGravity(true);
        return result;
    }

    private static void day(GameTestHelper helper, long day) {
        helper.getLevel().getServer().overworld().setDayTime(6000L);
        BloodMoonInvasionData.get(helper.getLevel()).restoreProgress(day);
    }

    private static void apply(MonsterGrowthRules.Settings settings) {
        MiteConfig.MONSTER_HEALTH_BASE.set(settings.healthBase());
        MiteConfig.MONSTER_DAMAGE_BASE.set(settings.damageBase());
        MiteConfig.MONSTER_SKELETON_REFERENCE.set(settings.skeletonReference());
        MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.set(settings.healthRate() * 100D);
        MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT.set(settings.damageRate() * 100D);
        MiteConfig.MONSTER_HEALTH_FORMULA.set(settings.healthFormula());
        MiteConfig.MONSTER_DAMAGE_FORMULA.set(settings.damageFormula());
        MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.set(settings.healthBloodMoonFormula());
        MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set(settings.damageBloodMoonFormula());
        MiteConfig.MONSTER_GROWTH.set(1D);
        MiteConfig.MONSTER_HEALTH_GROWTH.set(1D);
        MiteConfig.MONSTER_DAMAGE_GROWTH.set(1D);
        MiteConfig.refreshEnchantmentRules();
    }

    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < 1E-4D, label + ": " + actual + " vs " + expected);
    }

    private static final class Fixture implements AutoCloseable {
        private final MiteConfig.Snapshot original = MiteConfig.localSnapshot();
        private final String healthBloodMoonFormula = MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.get();
        private final String damageBloodMoonFormula = MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.get();
        private final ServerLevel level;
        private final long time, highest;

        private Fixture(GameTestHelper helper) {
            level = helper.getLevel().getServer().overworld();
            time = level.getDayTime();
            highest = BloodMoonInvasionData.get(level).highestDay();
        }

        @Override public void close() {
            apply(original.monsterSettings());
            MiteConfig.MONSTER_GROWTH.set(original.growth());
            MiteConfig.MONSTER_HEALTH_GROWTH.set(original.healthGrowth());
            MiteConfig.MONSTER_DAMAGE_GROWTH.set(original.damageGrowth());
            MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.set(healthBloodMoonFormula);
            MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set(damageBloodMoonFormula);
            MiteConfig.refreshEnchantmentRules();
            level.setDayTime(time);
            BloodMoonInvasionData.get(level).restoreProgress(highest);
        }
    }
}
