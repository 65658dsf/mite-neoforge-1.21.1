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

/** Removed early birth locks preserve real equipment, repair old missing weapons and retain decided rolls. @author NingMeng123 */
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
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWN_EGG, null);
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
        EventHooks.finalizeMobSpawn(skeleton,level,level.getCurrentDifficultyAt(skeleton.blockPosition()),MobSpawnType.SPAWN_EGG,null);
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

    @GameTest(template = "empty")
    public static void earlyDatesKeepSuppliedNativeSourceAndHiddenEquipment(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long day : new long[]{1L, 8L, 16L, 17L, 35L}) {
                date(helper, day, (day - 1L) * 24_000L + 6_000L);
                for (EntityType<?> type : types()) {
                    Mob mob = create(helper, type);
                    try {
                        finalizeSpawn(mob); suppliedEquipment(mob);
                        List<ItemStack> before = equipment(mob);
                        ItemStack stowed = mob instanceof R196Skeleton skeleton ? skeleton.stowedItem().copy() : ItemStack.EMPTY;
                        BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, helper.getLevel(), false));
                        helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(mob), "No date establishes a new birth-equipment lock");
                        helper.assertFalse(mob.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getBoolean("early_equipment_locked"),
                                "Early births no longer record the retired lock flag");
                        unchanged(helper, mob, before);
                        if (mob instanceof R196Skeleton skeleton) helper.assertTrue(ItemStack.matches(stowed, skeleton.stowedItem()),
                                "The guardian's actual hidden weapon is never cleared on early dates");
                    } finally { mob.discard(); }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realFirstDayIllagerBirthsKeepTheirOriginalCombatWeapons(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            for (long day : new long[]{1L, 16L, 17L, 35L}) {
                date(helper, day, (day - 1L) * 24_000L + 6_000L);
                for (EntityType<?> type : List.of(EntityType.PILLAGER, EntityType.VINDICATOR)) {
                    Mob mob = create(helper, type);
                    try {
                        finalizeSpawn(mob);
                        helper.assertFalse(mob.getMainHandItem().isEmpty(), "The full native finalizer supplies the species weapon");
                        helper.assertTrue(helper.getLevel().addFreshEntity(mob), "The real monster completes every native/addon join listener");
                        helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(mob), "The actual joined illager remains equipment-unlocked");
                        if (type == EntityType.PILLAGER) helper.assertTrue(mob.isHolding(stack -> stack.getItem() instanceof net.minecraft.world.item.CrossbowItem),
                                "A native or addon CrossbowItem survives even on the first day");
                        else helper.assertFalse(mob.getMainHandItem().isEmpty(), "The vindicator keeps its native or addon melee weapon");
                    } finally { mob.discard(); }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedOldLocksKeepVisibleAndHiddenGearWithoutRepeatingDecidedSpawnRolls(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper, 1L, 6_000L);
            for (EntityType<?> type : types()) {
                Mob original = create(helper, type), restored = null;
                try {
                    finalizeSpawn(original); suppliedEquipment(original);
                    ItemStack named = new ItemStack(Items.IRON_SWORD);
                    named.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved custom main hand"));
                    original.setItemSlot(EquipmentSlot.MAINHAND, named);
                    oldLock(original);
                    original.getPersistentData().putBoolean(MobShields.SPAWN_ROLL, true);
                    CompoundTag optional = new CompoundTag(); optional.putBoolean("decided", true);
                    original.getPersistentData().put(SimplySwordsMobEquipment.DATA_KEY, optional);
                    restored = create(helper, type); restored.load(original.saveWithoutId(new CompoundTag()));
                    List<ItemStack> before = equipment(restored);
                    ItemStack stowed = restored instanceof R196Skeleton skeleton ? skeleton.stowedItem().copy() : ItemStack.EMPTY;
                    helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(restored), "The public compatibility query ignores a retired lock even before joining");
                    restored.getRandom().setSeed(196L);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(restored, helper.getLevel(), true));
                    unchanged(helper, restored, before);
                    helper.assertFalse(restored.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getBoolean("early_equipment_locked"),
                            "The obsolete saved flag is retired when the actual old entity rejoins");
                    helper.assertTrue(restored.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY).getBoolean("existing_entity"),
                            "Migration records an existing entity rather than a fresh equipment draw");
                    if (restored instanceof R196Skeleton skeleton) helper.assertTrue(ItemStack.matches(stowed, skeleton.stowedItem()),
                            "Old hidden and spare weapon caches remain intact");
                    helper.assertFalse(MobShields.populate(restored), "An already-decided shield birth roll is not repeated");
                    helper.assertFalse(SimplySwordsMobEquipment.populate(restored), "An already-decided optional weapon roll is not repeated");
                    helper.assertValueEqual(restored.getRandom().nextLong(), RandomSource.create(196L).nextLong(),
                            "Retiring an old lock and honoring completed decisions consume no random equipment draws");
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(restored, helper.getLevel(), true));
                    unchanged(helper, restored, before);
                } finally { original.discard(); if (restored != null) restored.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oldLockedEmptyIllagersAndTrueArchersRepairOnlyTheirMissingOriginalWeapon(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper, 1L, 6_000L);
            var repairTypes = new java.util.ArrayList<EntityType<?>>(archerTypes());
            repairTypes.add(EntityType.PILLAGER); repairTypes.add(EntityType.VINDICATOR);
            for (EntityType<?> type : repairTypes) {
                Mob original = type == EntityType.PILLAGER || type == EntityType.VINDICATOR ? create(helper, type) : archer(helper, type);
                Mob restored = null;
                try {
                    if (type == EntityType.PILLAGER || type == EntityType.VINDICATOR) finalizeSpawn(original);
                    original.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    ItemStack helmet = new ItemStack(Items.IRON_HELMET);
                    helmet.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved picked-up helmet"));
                    original.setItemSlot(EquipmentSlot.HEAD, helmet);
                    original.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STICK));
                    oldLock(original);
                    restored = create(helper, type); restored.load(original.saveWithoutId(new CompoundTag()));
                    List<ItemStack> before = equipment(restored);
                    ItemStack stowed = restored instanceof R196Skeleton skeleton ? skeleton.stowedItem().copy() : ItemStack.EMPTY;
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(restored, helper.getLevel(), true));
                    var expected = type == EntityType.PILLAGER ? Items.CROSSBOW : type == EntityType.VINDICATOR ? Items.IRON_AXE : Items.BOW;
                    helper.assertTrue(ItemStack.matches(restored.getMainHandItem(), new ItemStack(expected)),
                            "A genuine old locked empty role receives only its missing plain original weapon: " + type);
                    for (EquipmentSlot slot : EquipmentSlot.values()) if (slot != EquipmentSlot.MAINHAND)
                        helper.assertTrue(ItemStack.matches(before.get(slot.ordinal()), restored.getItemBySlot(slot)), "Weapon repair preserves every other equipped item: " + slot);
                    if (restored instanceof R196Skeleton skeleton) helper.assertTrue(ItemStack.matches(stowed, skeleton.stowedItem()),
                            "Repairing the old visible bow never clears the hidden guardian weapon");
                    if (restored instanceof AbstractSkeleton skeleton) rangedGoal(helper, skeleton);
                    before = equipment(restored);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(restored, helper.getLevel(), true));
                    unchanged(helper, restored, before);
                } finally { original.discard(); if (restored != null) restored.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void existingOffhandCrossbowsAndUnmarkedEmptyMobsAreNotGivenExtraWeapons(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper, 1L, 6_000L);
            Mob pillager = create(helper, EntityType.PILLAGER);
            try {
                ItemStack crossbow = new ItemStack(Items.CROSSBOW);
                crossbow.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved offhand crossbow"));
                pillager.setItemSlot(EquipmentSlot.OFFHAND, crossbow); oldLock(pillager);
                List<ItemStack> before = equipment(pillager);
                BloodMoonEquipment.join(new EntityJoinLevelEvent(pillager, helper.getLevel(), true));
                unchanged(helper, pillager, before);
                helper.assertTrue(pillager.getMainHandItem().isEmpty(), "An existing offhand CrossbowItem prevents a duplicate main-hand repair");
            } finally { pillager.discard(); }
            for (EntityType<?> type : List.of(EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.ZOMBIE)) {
                Mob mob = create(helper, type);
                try {
                    List<ItemStack> before = equipment(mob);
                    BloodMoonEquipment.join(new EntityJoinLevelEvent(mob, helper.getLevel(), true));
                    unchanged(helper, mob, before);
                    helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(mob), "An unrelated existing entity is not retroactively equipped or locked");
                } finally { mob.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeAndSourceArchersKeepTheirRoleAndActuallyLaunchArrowsOnTheFirstDay(GameTestHelper helper) {
        try (Progress ignored = new Progress(helper)) {
            date(helper, 1L, 6_000L);
            for (EntityType<?> type : archerTypes()) {
                AbstractSkeleton skeleton = archer(helper, type);
                Mob target = create(helper, EntityType.COW);
                var arrows = new java.util.ArrayList<AbstractArrow>();
                try {
                    helper.assertTrue(helper.getLevel().addFreshEntity(skeleton), "The real first-day archer completes spawn and join");
                    helper.assertTrue(skeleton.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem,
                            "An actual bow role retains its original bow instead of becoming unarmed");
                    rangedGoal(helper, skeleton);
                    target.setPos(skeleton.position().add(1D, 0D, 0D));
                    helper.assertTrue(helper.getLevel().addFreshEntity(target), "The real ranged fixture target enters the clear chamber");
                    skeleton.setTarget(target); skeleton.performRangedAttack(target, 1F);
                    for (var entity : helper.getLevel().getAllEntities())
                        if (entity instanceof AbstractArrow arrow && arrow.getOwner() == skeleton) arrows.add(arrow);
                    helper.assertFalse(arrows.isEmpty(), "The actual native/source ranged callback launches an owned arrow");
                    helper.assertTrue(arrows.stream().allMatch(arrow -> arrow.getDeltaMovement().lengthSqr() > 0D), "The arrows have real flight velocity");
                } finally { arrows.forEach(AbstractArrow::discard); target.discard(); skeleton.discard(); }
            }
        }
        helper.succeed();
    }

    private static void oldLock(Mob mob) {
        var marker = new CompoundTag();
        marker.putInt("version", 3); marker.putInt("stage", -1); marker.putInt("tier", -1);
        marker.putBoolean("early_equipment_locked", true);
        mob.getPersistentData().put(BloodMoonEquipment.DATA_KEY, marker);
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
