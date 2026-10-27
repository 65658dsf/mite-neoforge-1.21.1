package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsMobEquipment;
import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.entity.MobEquipmentEnchantments;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real first16-day restrictions, functional plain-bow archers and unchanged fresh equipment from day17. @author NingMeng123 */
@GameTestHolder("mite_equipment_progression")
@PrefixGameTestTemplate(false)
public final class EarlyMobEquipmentGameTests {
    private static final List<EntityType<?>> NATIVE = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED,
            EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE, EntityType.VINDICATOR, EntityType.PILLAGER);
    private static final List<MobKind> SOURCE = List.of(MobKind.LONGDEAD_GUARDIAN, MobKind.REVENANT, MobKind.LONGDEAD);
    private static final List<EquipmentSlot> VISIBLE = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final List<String> LORDS = List.of("mite:bone_lord", "mite:ancient_bone_lord",
            "modern_ite:zombie_lord", "modern_ite:zombie_elite_lord", "modern_ite:skeleton_elite_lord",
            "modern_ite:zombie_pigman_lord", "modern_ite:witch_lord", "modern_ite:zombie_boss",
            "modern_ite:undying_zombie_emperor", "modern_ite:annihilation_skeleton");

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

    private static List<EntityType<?>> archerTypes() {
        return List.of(EntityType.SKELETON,EntityType.STRAY,EntityType.BOGGED,
                MiteMobs.type(MobKind.LONGDEAD),MiteMobs.type(MobKind.LONGDEAD_GUARDIAN));
    }

    private static AbstractSkeleton archer(GameTestHelper helper,EntityType<?> type) {
        var skeleton = (AbstractSkeleton)create(helper,type);
        if (skeleton instanceof R196Skeleton source) source.forceSkeletonType(0);
        var level = helper.getLevel();
        EventHooks.finalizeMobSpawn(skeleton,level,level.getCurrentDifficultyAt(skeleton.blockPosition()),MobSpawnType.NATURAL,null);
        if (type == EntityType.SKELETON) ((OrdinarySkeletonState)skeleton).mite$setSourceSkeletonType(0);
        return skeleton;
    }

    private static void rangedGoal(GameTestHelper helper,AbstractSkeleton skeleton) {
        helper.assertTrue(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(wrapped ->
                        wrapped.getGoal() instanceof SourceRangedAttackGoal || wrapped.getGoal() instanceof RangedBowAttackGoal<?>),
                "The real archer has its ranged bow AI installed after equipment reassessment: "+skeleton.getType());
        helper.assertFalse(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(wrapped ->
                        wrapped.getGoal() instanceof SourceMeleeGoal || wrapped.getGoal() instanceof MeleeAttackGoal),
                "Restoring the plain bow removes the obsolete empty-hand melee AI: "+skeleton.getType());
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

    private static void restricted(GameTestHelper helper, Mob mob) {
        boolean archer = mob instanceof R196Skeleton source ? source.isLongdead() && source.skeletonType() == 0
                : mob.getType() == EntityType.STRAY || mob.getType() == EntityType.BOGGED
                || mob.getType() == EntityType.SKELETON && ((OrdinarySkeletonState)mob).mite$sourceSkeletonType() == 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) helper.assertTrue(
                slot == EquipmentSlot.MAINHAND && archer ? ItemStack.matches(mob.getMainHandItem(),new ItemStack(Items.BOW)) : mob.getItemBySlot(slot).isEmpty(),
                "Early archers retain only one new plain bow; every other visible slot is empty: " + mob.getType() + "/" + slot);
        if (mob instanceof R196Skeleton skeleton) helper.assertTrue(skeleton.stowedItem().isEmpty(),
                "An early guardian cannot restore its hidden weapon");
    }

    @GameTest(template = "empty")
    public static void first16DaysKeepOnlyArcherBowsAndClearAllOtherHeldWornAndStowedItems(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long day : new long[]{1L, 8L, 15L, 16L}) {
                date(helper, day, day == 15L ? 15L * 24_000L - 1L : (day - 1L) * 24_000L + 6_000L);
                for (EntityType<?> type : types()) {
                    Mob mob = create(helper, type);
                    try {
                        finalizeSpawn(mob);
                        if (mob instanceof R196Skeleton skeleton && skeleton.miteKind() == MobKind.LONGDEAD_GUARDIAN)
                            helper.assertFalse(skeleton.stowedItem().isEmpty(), "The real guardian finalizer supplies its hidden dagger");
                        suppliedEquipment(mob);
                        helper.assertTrue(helper.getLevel().addFreshEntity(mob), "The finalized monster enters the real level");
                        helper.assertTrue(BloodMoonEquipment.spawnEquipmentLocked(mob), "The early spawn decision is saved");
                        restricted(helper, mob);
                    } finally { mob.discard(); }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void delayedSpawnRollsStayLockedAndNeverUpgradeOldEarlyMobsAfterDay17(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (EntityType<?> type : types()) {
                date(helper, 15L, 14L * 24_000L + 6_000L);
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
                    restricted(helper, mob);
                    date(helper, 17L, 16L * 24_000L);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, helper.getLevel(), false));
                    helper.assertFalse(MobShields.populate(mob), "Crossing day17 never repeats the shield spawn roll");
                    helper.assertFalse(SimplySwordsMobEquipment.populate(mob), "Crossing day17 never repeats the optional weapon spawn roll");
                    restricted(helper, mob);
                    if (mob instanceof R196Skeleton skeleton) helper.assertFalse(skeleton.updateGuardianWeapon(),
                            "The cleared hidden guardian weapon cannot be restored later");
                } finally { mob.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactDay17AndRememberedDay17KeepRealNativeAndSourceEquipment(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long time : new long[]{16L * 24_000L, 6_000L}) {
                date(helper, 17L, time);
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
                        helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(mob), "Day17 keeps the original fresh equipment path unlocked");
                        unchanged(helper, mob, before);
                        if (mob instanceof R196Skeleton skeleton) helper.assertTrue(ItemStack.matches(stowed, skeleton.stowedItem()),
                                "The guardian's exact hidden weapon survives from day17");
                    } finally { mob.discard(); }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void earlyRestrictionAndDay17PassThroughDoNotConsumeEquipmentRandom(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long day : new long[]{1L, 8L, 15L, 16L, 17L}) {
                date(helper, day, (day - 1L) * 24_000L);
                Mob mob = create(helper, EntityType.ZOMBIE);
                try {
                    suppliedEquipment(mob);
                    List<ItemStack> before = equipment(mob);
                    mob.getRandom().setSeed(196L);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, helper.getLevel(), false));
                    helper.assertValueEqual(mob.getRandom().nextLong(), RandomSource.create(196L).nextLong(),
                            "The date restriction does not draw or change existing equipment probabilities");
                    if (day <= 16L) restricted(helper, mob);
                    else unchanged(helper, mob, before);
                } finally { mob.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lordBirthsKeepTheirExactEquipmentThroughoutTheFirst16Days(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long day : new long[]{1L, 16L}) {
                date(helper, day, (day - 1L) * 24_000L + 6_000L);
                for (String id : LORDS) {
                    var key = ResourceLocation.parse(id);
                    if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) continue; // Optional Modern ITE integration.
                    Mob mob = create(helper, BuiltInRegistries.ENTITY_TYPE.get(key));
                    try {
                        finalizeSpawn(mob);
                        MobEquipmentEnchantments.populate(mob);
                        List<ItemStack> before = equipment(mob);
                        helper.assertTrue(BloodMoonEquipment.earlyEquipmentExempt(mob), "The explicit lord identity is exempt: " + id);
                        if (mob instanceof R196Skeleton) helper.assertFalse(mob.getMainHandItem().isEmpty(), "The source lord starts with its native weapon");
                        BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, helper.getLevel(), false));
                        unchanged(helper, mob, before);
                        helper.assertTrue(helper.getLevel().addFreshEntity(mob), "The finalized lord enters the real level");
                        helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(mob), "Early dates never lock a lord's equipment: " + id);
                        if (id.startsWith("mite:")) unchanged(helper, mob, before);
                        else if (!id.equals("modern_ite:witch_lord")) {
                            // Modern ITE supplies its fixed kit in its real join listener, after finalizeSpawn.
                            helper.assertFalse(mob.getMainHandItem().isEmpty(), "The addon lord's real join listener supplies its own weapon: " + id);
                            helper.assertFalse(mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "The addon lord's real join listener supplies its own armor: " + id);
                        }
                    } finally { mob.discard(); }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedLordBirthLocksNeverClearOrSuppressTheirEquipment(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper, 1L, 6_000L);
            for (String id : LORDS) {
                var key = ResourceLocation.parse(id);
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) continue;
                Mob original = create(helper, BuiltInRegistries.ENTITY_TYPE.get(key));
                Mob restored = null;
                try {
                    finalizeSpawn(original);
                    suppliedEquipment(original);
                    var oldLock = new CompoundTag(); oldLock.putBoolean("early_equipment_locked", true);
                    original.getPersistentData().put(BloodMoonEquipment.DATA_KEY, oldLock);
                    CompoundTag saved = new CompoundTag(); original.saveWithoutId(saved);
                    restored = create(helper, original.getType()); restored.load(saved);
                    List<ItemStack> before = equipment(restored);
                    helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(restored), "Old locks cannot suppress addon equipment initialization before level join: " + id);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(restored, helper.getLevel(), true));
                    unchanged(helper, restored, before);
                    helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(restored), "Loaded lords remain exempt from the old stored lock: " + id);
                } finally { original.discard(); if (restored != null) restored.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loadedMonstersAndNonmonstersKeepTheirExistingEquipmentBeforeDay16(GameTestHelper helper) {
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

    @GameTest(template = "empty")
    public static void allFiveNativeAndSourceArcherRolesKeepPlainBowsAndActuallyLaunchArrows(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper,16L,15L*24_000L+6_000L);
            for (EntityType<?> type : archerTypes()) {
                AbstractSkeleton skeleton = archer(helper,type);
                Mob target = create(helper,EntityType.COW);
                var arrows = new java.util.ArrayList<AbstractArrow>();
                try {
                    suppliedEquipment(skeleton);
                    helper.assertTrue(helper.getLevel().addFreshEntity(skeleton),"The actual archer enters on the inclusive16th day");
                    restricted(helper,skeleton); rangedGoal(helper,skeleton);
                    helper.assertFalse(MobEquipmentEnchantments.populate(skeleton),"Delayed birth enchantments cannot upgrade the plain early bow");
                    target.setPos(skeleton.position().add(1D,0D,0D));
                    helper.assertTrue(helper.getLevel().addFreshEntity(target),"The real ranged-attack fixture target enters the same clear chamber");
                    skeleton.setTarget(target);
                    var goal = skeleton.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                            .filter(candidate -> candidate instanceof SourceRangedAttackGoal || candidate instanceof RangedBowAttackGoal<?>)
                            .findFirst().orElseThrow();
                    helper.assertTrue(goal.canUse(),"The installed ranged AI recognizes its target with the restored bow");
                    skeleton.performRangedAttack(target,1F);
                    for (var entity : helper.getLevel().getAllEntities())
                        if (entity instanceof AbstractArrow arrow && arrow.getOwner() == skeleton) arrows.add(arrow);
                    helper.assertFalse(arrows.isEmpty(),"The actual skeleton ranged callback launches an owned arrow, rather than only displaying a bow: "+type);
                    helper.assertTrue(arrows.stream().allMatch(arrow -> arrow.getDeltaMovement().lengthSqr() > 0D),"The spawned arrows have real nonzero flight velocity");
                    restricted(helper,skeleton);
                } finally { arrows.forEach(AbstractArrow::discard); target.discard(); skeleton.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedOldLockedEmptyArchersRepairOnlyTheirBowAndKeepVisibleCustomOrPickedUpGear(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper,1L,6_000L);
            for (EntityType<?> type : archerTypes()) {
                AbstractSkeleton original = archer(helper,type);
                Mob restored = null;
                try {
                    helper.assertTrue(helper.getLevel().addFreshEntity(original),"The original archer receives the persisted early birth marker");
                    original.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); // The old release saved locked archers empty.
                    ItemStack helmet = new ItemStack(Items.IRON_HELMET);
                    helmet.set(DataComponents.CUSTOM_NAME,Component.literal("Kept picked-up helmet"));
                    original.setItemSlot(EquipmentSlot.HEAD,helmet);
                    original.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.STICK));
                    CompoundTag saved = new CompoundTag(); original.saveWithoutId(saved); original.discard();
                    restored = create(helper,type); restored.load(saved);
                    var before = equipment(restored);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(restored,helper.getLevel(),true));
                    helper.assertTrue(ItemStack.matches(restored.getMainHandItem(),new ItemStack(Items.BOW)),"A truly saved/loaded old locked empty archer regains exactly one plain bow");
                    for (EquipmentSlot slot : EquipmentSlot.values()) if (slot != EquipmentSlot.MAINHAND)
                        helper.assertTrue(ItemStack.matches(before.get(slot.ordinal()),restored.getItemBySlot(slot)),"The old-archer migration never globally clears visible picked-up/custom equipment: "+slot);
                    rangedGoal(helper,(AbstractSkeleton)restored);
                    ItemStack picked = new ItemStack(Items.IRON_SWORD);
                    picked.set(DataComponents.CUSTOM_NAME,Component.literal("Kept custom main hand"));
                    restored.setItemSlot(EquipmentSlot.MAINHAND,picked); before = equipment(restored);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(restored,helper.getLevel(),true));
                    unchanged(helper,restored,before);
                    restored.getPersistentData().remove(BloodMoonEquipment.DATA_KEY);
                    restored.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); before = equipment(restored);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(restored,helper.getLevel(),true));
                    unchanged(helper,restored,before);
                    helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(restored),"An unrelated loaded empty archer without the old lock is not migrated or globally cleaned");
                } finally { original.discard(); if (restored != null) restored.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void meleeSkeletonMetadataAndBoneLordsDoNotReceiveTheArcherException(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper,16L,15L*24_000L+6_000L);
            for (int subtype : new int[]{1,2}) {
                var skeleton = (AbstractSkeleton)create(helper,EntityType.SKELETON);
                try {
                    finalizeSpawn(skeleton); ((OrdinarySkeletonState)skeleton).mite$setSourceSkeletonType(subtype);
                    suppliedEquipment(skeleton); BloodMoonEquipment.join(new EntityJoinLevelEvent(skeleton,helper.getLevel(),false));
                    helper.assertFalse(BloodMoonEquipment.earlyEquipmentArcher(skeleton),"Ordinary skeleton metadata1/2 denotes a melee role");
                    restricted(helper,skeleton);
                } finally { skeleton.discard(); }
            }
            for (MobKind kind : List.of(MobKind.LONGDEAD,MobKind.LONGDEAD_GUARDIAN,MobKind.BONE_LORD,MobKind.ANCIENT_BONE_LORD)) {
                var skeleton = (R196Skeleton)create(helper,MiteMobs.type(kind));
                try {
                    skeleton.forceSkeletonType(kind == MobKind.BONE_LORD || kind == MobKind.ANCIENT_BONE_LORD ? 0 : 2);
                    var level = helper.getLevel();
                    EventHooks.finalizeMobSpawn(skeleton,level,level.getCurrentDifficultyAt(skeleton.blockPosition()),MobSpawnType.NATURAL,null);
                    var before = equipment(skeleton);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(skeleton,level,false));
                    helper.assertFalse(BloodMoonEquipment.earlyEquipmentArcher(skeleton),"Melee longdead and bone-lord bosses never become archers merely by extending AbstractSkeleton");
                    if (kind == MobKind.BONE_LORD || kind == MobKind.ANCIENT_BONE_LORD) unchanged(helper, skeleton, before);
                    else restricted(helper,skeleton);
                } finally { skeleton.discard(); }
            }
        }
        helper.succeed();
    }

    private static final class Progress implements AutoCloseable {
        private final ServerLevel level;
        private final long time, highest;
        private final boolean dirty;
        private final SourceConfigFixture config = new SourceConfigFixture(true, false);
        private final Map<BlockPos, BlockState> chamber = new HashMap<>();
        Progress(GameTestHelper helper) {
            level = helper.getLevel().getServer().overworld();
            time = level.getDayTime(); highest = BloodMoonInvasionData.get(level).highestDay();
            dirty = BloodMoonInvasionData.get(level).isDirty();
            // Equipment timing requires actual NATURAL joins at noon, inside a genuinely enclosed habitat.
            // This keeps the daylight spawn restriction active while isolating the equipment lifecycle.
            BlockPos origin = helper.absolutePos(new BlockPos(2, 3, 2));
            for (int x=-2; x<=2; x++) for (int z=-2; z<=2; z++) for (int y=-1; y<=3; y++) {
                BlockPos pos = origin.offset(x,y,z);
                chamber.put(pos, level.getBlockState(pos));
                boolean wall = Math.abs(x)==2 || Math.abs(z)==2 || y==-1 || y==3;
                level.setBlockAndUpdate(pos, (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            }
        }
        @Override public void close() {
            chamber.forEach(level::setBlockAndUpdate);
            level.setDayTime(time); BloodMoonInvasionData.get(level).restoreProgress(highest);
            BloodMoonInvasionData.get(level).setDirty(dirty);
            config.close();
        }
    }
}
