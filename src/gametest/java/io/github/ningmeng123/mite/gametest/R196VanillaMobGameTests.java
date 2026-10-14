package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.rules.R196VanillaMobRules;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tests constructor/finalize/taming/XP/tick paths against independent old constants, including modern split identities. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class R196VanillaMobGameTests {
    private R196VanillaMobGameTests() {}
    private static <T extends Mob> T create(GameTestHelper helper, EntityType<T> type) {
        T mob = type.create(helper.getLevel()); helper.assertTrue(mob != null, "Native mob creates " + type);
        mob.setNoAi(true); mob.setNoGravity(true); mob.moveTo(helper.absolutePos(new BlockPos(4, 2, 4)).getCenter()); return mob;
    }
    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .00001, message + " expected=" + expected + " actual=" + actual);
    }
    @GameTest(template = "ranged_027")
    public static void oldNativeSpeciesHaveSourceAttributesAndExperience(GameTestHelper helper) {
        EntityType<? extends Mob>[] types = new EntityType[]{EntityType.SKELETON, EntityType.SPIDER, EntityType.ZOMBIE,
                EntityType.ZOMBIFIED_PIGLIN, EntityType.ENDERMAN, EntityType.SILVERFISH, EntityType.BLAZE,
                EntityType.BAT, EntityType.WITCH, EntityType.COW};
        double[][] expected = {{6,4,.3,32,5},{12,4,1,28,5},{20,5,.23,40,5},{20,8,.5,40,15},{40,10,.3,32,5},
                {8,3,.6,32,5},{20,6,.1,32,20},{3,0,.7,16,0},{26,2,.25,32,20},{20,0,.2,16,0}};
        for (int index = 0; index < types.length; index++) {
            Mob mob = create(helper, types[index]); var row = expected[index];
            close(helper, mob.getMaxHealth(), row[0], types[index] + " source HP");
            if (R196VanillaMobRules.entry(types[index]).sourceAttackAttribute()) close(helper, mob.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), row[1], types[index] + " source attack");
            close(helper, mob.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), row[2], types[index] + " source movement");
            close(helper, mob.getAttributeBaseValue(Attributes.FOLLOW_RANGE), row[3], types[index] + " source follow range");
            helper.assertValueEqual(mob.getExperienceReward(helper.getLevel(), null), (int)row[4], "Source XP getter " + types[index]);
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void nativeAndSplitSourceEntitiesHaveTheirOriginalPhysicalDimensions(GameTestHelper helper) {
        EntityType<? extends Mob>[] types = new EntityType[]{EntityType.CAVE_SPIDER, EntityType.CHICKEN, EntityType.WOLF,
                EntityType.HORSE, EntityType.WITHER, EntityType.WITHER_SKELETON, EntityType.SNOW_GOLEM};
        float[][] expected = {{.98F,.63F},{.3F,.7F},{.7F,.8F},{1.4F,1.6F},{.9F,4F},{.72F,2.34F},{.6F,1.8F}};
        for (int index = 0; index < types.length; index++) {
            Mob mob = create(helper, types[index]); mob.refreshDimensions();
            close(helper, mob.getBbWidth(), expected[index][0], "Native source width " + types[index]);
            close(helper, mob.getBbHeight(), expected[index][1], "Native source height " + types[index]);
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceExperienceDoesNotRandomlyGrowWithEquipmentOrRepeatReads(GameTestHelper helper) {
        for (var type : List.of(EntityType.SKELETON, EntityType.ZOMBIE, EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN)) {
            Mob mob = create(helper, type);
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)); mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            int expected = type == EntityType.ZOMBIFIED_PIGLIN ? 15 : 5;
            for (int i = 0; i < 32; i++) helper.assertValueEqual(mob.getExperienceReward(helper.getLevel(), null), expected, "No equipment or repeated-query XP inflation");
        }
        helper.assertValueEqual(create(helper, EntityType.ENDER_DRAGON).getExperienceReward(helper.getLevel(), null), 100, "Dragon getter differs from actual 1200 death sequence");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceWolfTamingPreservesMissingHealthAndSurvivesNbtReload(GameTestHelper helper) {
        Wolf wolf = create(helper, EntityType.WOLF); close(helper, wolf.getMaxHealth(), 8, "Wild source wolf");
        wolf.setOwnerUUID(java.util.UUID.randomUUID()); wolf.setHealth(3); wolf.setTame(true, true);
        close(helper, wolf.getMaxHealth(), 12, "Tamed source wolf"); close(helper, wolf.getHealth(), 7, "Taming adds only four source HP");
        close(helper, wolf.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), 3, "Taming does not replace source attack");
        close(helper, wolf.getAttributeBaseValue(Attributes.FOLLOW_RANGE), 32, "Tamed follow range");
        CompoundTag saved = new CompoundTag(); wolf.saveWithoutId(saved); Wolf restored = create(helper, EntityType.WOLF); restored.load(saved);
        helper.assertTrue(restored.isTame(), "Tame flag persists"); close(helper, restored.getMaxHealth(), 12, "Saved tame attributes"); close(helper, restored.getHealth(), 7, "Reload does not heal to full");
        restored.setTame(false, false); close(helper, restored.getMaxHealth(), 8, "Untaming returns wild cap"); close(helper, restored.getHealth(), 7, "Untaming keeps health under new cap");
        restored.setHealth(8); restored.setTame(true, false); close(helper, restored.getHealth(), 12, "Source tame API applies even without modern side effects");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceNaturalHealingUsesEntityOffsetAndExcludesUndeadAndSnowGolems(GameTestHelper helper) {
        Cow cow = create(helper, EntityType.COW); cow.setHealth(9);
        int boundary = Math.floorMod(-cow.getId() * 47, 1000); cow.tickCount = Math.floorMod(boundary - 1, 1000); cow.aiStep();
        close(helper, cow.getHealth(), 9, "Before source period there is no heal");
        cow.tickCount = boundary; cow.aiStep(); close(helper, cow.getHealth(), 11, "Exactly one tenth of max20 at period");
        cow.setHealth(19); cow.aiStep(); close(helper, cow.getHealth(), 20, "Source percent recovery respects max");
        for (var type : List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SNOW_GOLEM)) {
            Mob mob = create(helper, type); mob.setHealth(2); mob.tickCount = Math.floorMod(-mob.getId() * 47, 1000); mob.aiStep();
            close(helper, mob.getHealth(), 2, "No general source healing for " + type);
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceZombieIdentitiesStayAdultAndDoNotCreateChickenJockeys(GameTestHelper helper) {
        for (var type : List.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN)) {
            Zombie zombie = create(helper, type); zombie.setBaby(true);
            helper.assertTrue(!zombie.isBaby(), "Source setter stays adult " + type);
            EventHooks.finalizeMobSpawn(zombie, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.NATURAL, new Zombie.ZombieGroupData(true, true));
            helper.assertTrue(!zombie.isBaby() && !zombie.isPassenger(), "A source baby candidate cannot spawn an adult chicken jockey");
        }
        Zombie modern = create(helper, EntityType.DROWNED); modern.setBaby(true); helper.assertTrue(modern.isBaby(), "Modern drowned age variants remain available");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceSkeletonCannotBeginNativePowderSnowConversion(GameTestHelper helper) {
        Skeleton skeleton = create(helper, EntityType.SKELETON); CompoundTag saved = new CompoundTag(); saved.putInt("StrayConversionTime", 10);
        skeleton.readAdditionalSaveData(saved); helper.assertTrue(!skeleton.isFreezeConverting(), "Loading a modern conversion timer does not begin source conversion");
        skeleton.setNoAi(false);
        for (int i = 0; i < 150; i++) { skeleton.isInPowderSnow = true; skeleton.tick(); }
        helper.assertTrue(!skeleton.isFreezeConverting() && skeleton.getType() == EntityType.SKELETON, "The native140-tick trigger leaves source skeleton identity intact");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sunlightDoesNotWearSourceHelmetsWhileModernStrayControlStillWearsItsHelmet(GameTestHelper helper) {
        var level = helper.getLevel();
        long time = level.getDayTime();
        ServerLevelData weather = (ServerLevelData)level.getLevelData();
        boolean raining = weather.isRaining(), thundering = weather.isThundering();
        int rainTime = weather.getRainTime(), thunderTime = weather.getThunderTime(), clearTime = weather.getClearWeatherTime();
        float rain = level.getRainLevel(1F);
        // getThunderLevel is multiplied by rain; temporarily use rain=1 to preserve the raw value.
        level.setRainLevel(1F); float thunder = level.getThunderLevel(1F); level.setRainLevel(rain);
        AbstractSkeleton source = create(helper, EntityType.SKELETON), modern = create(helper, EntityType.STRAY);
        source.setPos(source.getX(), 280, source.getZ()); modern.setPos(modern.getX() + 2, 280, modern.getZ());
        ItemStack sourceHelmet = new ItemStack(Items.IRON_HELMET), modernHelmet = new ItemStack(Items.IRON_HELMET);
        source.setItemSlot(EquipmentSlot.HEAD, sourceHelmet); modern.setItemSlot(EquipmentSlot.HEAD, modernHelmet);
        try {
            level.setWeatherParameters(6000, 0, false, false); level.setRainLevel(0F); level.setThunderLevel(0F);
            level.setDayTime(6000); level.getChunkAt(modern.blockPosition()); level.updateSkyBrightness();
            helper.assertTrue(level.isDay() && level.canSeeSky(BlockPos.containing(modern.getEyePosition()))
                    && modern.getLightLevelDependentMagicValue() > .5F, "Loaded clear-noon sky reaches the native sunlight check");
            long seed = 0;
            for (; seed < 100_000; seed++) {
                RandomSource trial = RandomSource.create(seed);
                if (trial.nextFloat() < .01F && trial.nextInt(2) == 1) break;
            }
            helper.assertTrue(seed < 100_000, "Deterministic native sunlight and one-point wear draw exists");
            for (int i = 0; i < 16; i++) {
                source.getRandom().setSeed(seed); modern.getRandom().setSeed(seed);
                source.aiStep(); modern.aiStep();
            }
            helper.assertValueEqual(sourceHelmet.getDamageValue(), 0, "Source sunlight does not consume helmet wear");
            helper.assertTrue(modernHelmet.getDamageValue() > 0, "Positive control proves native sunlight wear path was reached");
        } finally {
            weather.setRaining(raining); weather.setThundering(thundering);
            weather.setRainTime(rainTime); weather.setThunderTime(thunderTime); weather.setClearWeatherTime(clearTime);
            level.setRainLevel(rain); level.setThunderLevel(thunder); level.setDayTime(time); level.updateSkyBrightness();
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void nativeSourceHorseFinalizationKeepsOriginalRandomHealthFormula(GameTestHelper helper) {
        for (var type : List.of(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.ZOMBIE_HORSE, EntityType.SKELETON_HORSE)) {
            for (int seed = 0; seed < 12; seed++) {
                AbstractHorse horse = create(helper, type); helper.getLevel().getRandom().setSeed(seed * 1000000007L);
                EventHooks.finalizeMobSpawn(horse, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(horse.blockPosition()), MobSpawnType.NATURAL, null);
                boolean undead = type == EntityType.ZOMBIE_HORSE || type == EntityType.SKELETON_HORSE;
                if (undead) { close(helper, horse.getMaxHealth(), 15, "Undead source horse fixed HP"); close(helper, horse.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), .2, "Undead source speed"); }
                else helper.assertTrue(horse.getMaxHealth() >= 15 && horse.getMaxHealth() <= 30 && horse.getMaxHealth() == (int)horse.getMaxHealth(), "15+nextInt8+nextInt9 source range");
                close(helper, horse.getHealth(), horse.getMaxHealth(), "Finalize current health follows randomized source maximum");
                helper.assertValueEqual(horse.getExperienceReward(helper.getLevel(), null), 0, "Source horses grant no death XP");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void dragonDeathSequenceOffersTenHundredsAndFinalTwoHundred(GameTestHelper helper) throws Exception {
        EnderDragon dragon = create(helper, EntityType.ENDER_DRAGON); dragon.setHealth(0); dragon.setSilent(true);
        List<Integer> amounts = new ArrayList<>(); Consumer<LivingExperienceDropEvent> listener = event -> {
            if (event.getEntity() == dragon) { amounts.add(event.getDroppedExperience()); event.setDroppedExperience(0); }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var method = EnderDragon.class.getDeclaredMethod("tickDeath"); method.setAccessible(true);
            for (int tick = 0; tick < 200; tick++) method.invoke(dragon);
            helper.assertValueEqual(amounts.size(), 11, "Ten five-tick intervals plus the final award");
            helper.assertValueEqual(amounts.stream().mapToInt(Integer::intValue).sum(), 1200, "Source dragon total death experience");
            for (int i = 0; i < 10; i++) helper.assertValueEqual(amounts.get(i), 100, "Source dragon interval award");
            helper.assertValueEqual(amounts.getLast(), 200, "Source dragon final award");
        } finally { NeoForge.EVENT_BUS.unregister(listener); dragon.discard(); }
        helper.succeed();
    }
}
