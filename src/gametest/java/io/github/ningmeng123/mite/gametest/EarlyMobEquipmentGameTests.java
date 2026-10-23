package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsMobEquipment;
import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.entity.MobEquipmentEnchantments;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real early spawn restrictions and unchanged equipment behavior from day 35. @author NingMeng123 */
@GameTestHolder("mite_equipment_progression")
@PrefixGameTestTemplate(false)
public final class EarlyMobEquipmentGameTests {
    private static final List<EntityType<?>> NATIVE = List.of(EntityType.ZOMBIE, EntityType.SKELETON,
            EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE, EntityType.VINDICATOR, EntityType.PILLAGER);
    private static final List<MobKind> SOURCE = List.of(MobKind.LONGDEAD_GUARDIAN, MobKind.BONE_LORD,
            MobKind.ANCIENT_BONE_LORD, MobKind.REVENANT, MobKind.LONGDEAD);
    private static final List<EquipmentSlot> VISIBLE = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private EarlyMobEquipmentGameTests() {}

    private static List<EntityType<?>> types() {
        var types = new java.util.ArrayList<>(NATIVE);
        for (MobKind kind : SOURCE) types.add(MiteMobs.type(kind));
        return types;
    }

    private static Mob create(GameTestHelper helper, EntityType<?> type) {
        Mob mob = (Mob)type.create(helper.getLevel());
        if (mob == null) throw new AssertionError("Registered monster cannot be created: " + type);
        mob.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        mob.setNoAi(true); mob.setNoGravity(true); mob.getRandom().setSeed(196L);
        return mob;
    }

    private static void finalizeSpawn(Mob mob) {
        if (mob instanceof R196Skeleton skeleton)
            skeleton.forceSkeletonType(skeleton.miteKind() == MobKind.LONGDEAD_GUARDIAN ? 0 : 2);
        ServerLevel level = (ServerLevel)mob.level();
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.NATURAL, null);
    }

    private static void date(GameTestHelper helper, long day, long time) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        overworld.setDayTime(time);
        BloodMoonInvasionData.get(overworld).restoreProgress(day);
    }

    private static void suppliedEquipment(Mob mob) {
        for (EquipmentSlot slot : VISIBLE)
            if (mob.getItemBySlot(slot).isEmpty()) mob.setItemSlot(slot, new ItemStack(Items.STICK));
    }

    private static List<ItemStack> equipment(Mob mob) {
        return Arrays.stream(EquipmentSlot.values()).map(slot -> mob.getItemBySlot(slot).copy()).toList();
    }

    private static void unchanged(GameTestHelper helper, Mob mob, List<ItemStack> before) {
        for (EquipmentSlot slot : EquipmentSlot.values()) helper.assertTrue(
                ItemStack.matches(before.get(slot.ordinal()), mob.getItemBySlot(slot)),
                "Exact equipment survives for " + mob.getType() + "/" + slot);
    }

    private static void empty(GameTestHelper helper, Mob mob) {
        for (EquipmentSlot slot : EquipmentSlot.values()) helper.assertTrue(mob.getItemBySlot(slot).isEmpty(),
                "Early monster equipment must be empty: " + mob.getType() + "/" + slot);
        if (mob instanceof R196Skeleton skeleton) helper.assertTrue(skeleton.stowedItem().isEmpty(),
                "An early guardian cannot restore its hidden weapon");
    }

    @GameTest(template = "empty")
    public static void realOrdinaryAndFirstBloodSpawnsHaveNoHeldWornOrStowedItemsBeforeDay35(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long day : new long[]{1L, 32L, 34L}) {
                date(helper, day, day == 34L ? 34L * 24_000L - 1L : (day - 1L) * 24_000L + 6_000L);
                for (EntityType<?> type : types()) {
                    Mob mob = create(helper, type);
                    try {
                        finalizeSpawn(mob);
                        if (mob instanceof R196Skeleton skeleton && skeleton.miteKind() == MobKind.LONGDEAD_GUARDIAN)
                            helper.assertFalse(skeleton.stowedItem().isEmpty(), "The real guardian finalizer supplies its hidden dagger");
                        suppliedEquipment(mob);
                        helper.assertTrue(helper.getLevel().addFreshEntity(mob), "The finalized monster enters the real level");
                        helper.assertTrue(BloodMoonEquipment.spawnEquipmentLocked(mob), "The early spawn decision is saved");
                        empty(helper, mob);
                    } finally { mob.discard(); }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void delayedSpawnRollsStayEmptyAndNeverEquipOldEarlyMobsAfterDay35(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (EntityType<?> type : types()) {
                date(helper, 34L, 33L * 24_000L + 6_000L);
                Mob mob = create(helper, type);
                try {
                    finalizeSpawn(mob);
                    helper.assertTrue(helper.getLevel().addFreshEntity(mob), "The early monster enters the real level");
                    mob.getRandom().setSeed(196L);
                    helper.assertFalse(MobShields.populate(mob), "The delayed sword/shield roll respects the early spawn decision");
                    helper.assertFalse(SimplySwordsMobEquipment.populate(mob), "The delayed optional weapon roll respects the early spawn decision");
                    if (MobShields.handles(mob)) helper.assertTrue(mob.getPersistentData().getBoolean(MobShields.SPAWN_ROLL),
                            "The shield spawn roll is permanently decided");
                    if (SimplySwordsCompat.enabled() && SimplySwordsMobEquipment.handles(mob)) helper.assertTrue(
                            mob.getPersistentData().getCompound(SimplySwordsMobEquipment.DATA_KEY).getBoolean("decided"),
                            "The optional weapon spawn roll is permanently decided");
                    helper.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(196L).nextLong(),
                            "Skipped delayed equipment draws consume no RNG");
                    empty(helper, mob);
                    date(helper, 35L, 34L * 24_000L);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, helper.getLevel(), false));
                    helper.assertFalse(MobShields.populate(mob), "Crossing day 35 never repeats the shield spawn roll");
                    helper.assertFalse(SimplySwordsMobEquipment.populate(mob), "Crossing day 35 never repeats the optional weapon spawn roll");
                    empty(helper, mob);
                    if (mob instanceof R196Skeleton skeleton) helper.assertFalse(skeleton.updateGuardianWeapon(),
                            "The cleared hidden guardian weapon cannot be restored later");
                } finally { mob.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactDay35AndRememberedDay35KeepRealNativeAndSourceEquipment(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long time : new long[]{34L * 24_000L, 6_000L}) {
                date(helper, 35L, time);
                for (EntityType<?> type : types()) {
                    Mob mob = create(helper, type);
                    try {
                        finalizeSpawn(mob);
                        // Include the existing join-time date enchantments in the expected unlocked equipment.
                        MobEquipmentEnchantments.populate(mob);
                        List<ItemStack> before = equipment(mob);
                        ItemStack stowed = mob instanceof R196Skeleton skeleton ? skeleton.stowedItem().copy() : ItemStack.EMPTY;
                        if (type == EntityType.SKELETON || type == EntityType.ZOMBIFIED_PIGLIN || mob instanceof R196Skeleton)
                            helper.assertFalse(mob.getMainHandItem().isEmpty(), "The fixed finalizer supplied its normal weapon: " + type);
                        helper.assertTrue(helper.getLevel().addFreshEntity(mob), "The unlocked monster enters the real level");
                        helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(mob), "Day 35 keeps the original equipment path unlocked");
                        unchanged(helper, mob, before);
                        if (mob instanceof R196Skeleton skeleton) helper.assertTrue(ItemStack.matches(stowed, skeleton.stowedItem()),
                                "The guardian's exact hidden weapon survives from day 35");
                    } finally { mob.discard(); }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void earlyRestrictionAndDay35PassThroughDoNotConsumeEquipmentRandom(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long day : new long[]{1L, 32L, 34L, 35L}) {
                date(helper, day, (day - 1L) * 24_000L);
                Mob mob = create(helper, EntityType.ZOMBIE);
                try {
                    suppliedEquipment(mob);
                    List<ItemStack> before = equipment(mob);
                    mob.getRandom().setSeed(196L);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, helper.getLevel(), false));
                    helper.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(196L).nextLong(),
                            "The date restriction does not draw or change existing equipment probabilities");
                    if (day < 35L) empty(helper, mob);
                    else unchanged(helper, mob, before);
                } finally { mob.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loadedMonstersAndNonmonstersKeepTheirExistingEquipmentBeforeDay35(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper, 1L, 6_000L);
            for (EntityType<?> type : List.of(EntityType.ZOMBIE, EntityType.COW)) {
                Mob mob = create(helper, type);
                try {
                    suppliedEquipment(mob);
                    List<ItemStack> before = equipment(mob);
                    mob.getRandom().setSeed(196L);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, helper.getLevel(), type == EntityType.ZOMBIE));
                    unchanged(helper, mob, before);
                    helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(mob), "Only fresh monsters receive the early spawn restriction");
                    helper.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(196L).nextLong(),
                            "Loaded monsters and nonmonsters do not consume a spawn equipment draw");
                } finally { mob.discard(); }
            }
        }
        helper.succeed();
    }

    private static final class Progress implements AutoCloseable {
        private final ServerLevel level;
        private final long time, highest;
        Progress(GameTestHelper helper) {
            level = helper.getLevel().getServer().overworld();
            time = level.getDayTime(); highest = BloodMoonInvasionData.get(level).highestDay();
        }
        @Override public void close() {
            level.setDayTime(time); BloodMoonInvasionData.get(level).restoreProgress(highest);
        }
    }
}
