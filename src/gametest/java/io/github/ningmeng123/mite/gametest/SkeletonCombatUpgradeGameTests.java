package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.OrdinarySkeletonState;
import io.github.ningmeng123.mite.entity.R196Skeleton;
import io.github.ningmeng123.mite.entity.SkeletonAiming;
import io.github.ningmeng123.mite.entity.SkeletonDualWeapons;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real shooting, pickup, goal changes, saves and death drops. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SkeletonCombatUpgradeGameTests {
    private static final AtomicInteger SHOT_FIXTURES = new AtomicInteger();

    private static <T extends Mob> T spawn(GameTestHelper h, EntityType<T> type) {
        T mob = type.create(h.getLevel());
        BlockPos pos = h.absolutePos(new BlockPos(1, 10, 1));
        mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        mob.setNoAi(true); mob.setNoGravity(true); h.getLevel().addFreshEntity(mob);
        return mob;
    }
    private static List<EntityType<? extends AbstractSkeleton>> archers() {
        return List.of(EntityType.SKELETON, (EntityType<? extends AbstractSkeleton>)(EntityType<?>)MiteMobs.type(MobKind.LONGDEAD));
    }
    private static void moveTarget(Mob source, Mob target, double distance) {
        target.setPos(source.getX() + distance, source.getY(), source.getZ());
        source.setTarget(target);
    }
    private static List<AbstractArrow> arrows(GameTestHelper h, AbstractSkeleton source) {
        return h.getLevel().getEntitiesOfClass(AbstractArrow.class, source.getBoundingBox().inflate(40), a -> a.getOwner() == source);
    }

    @GameTest(template = "empty")
    public static void droppedSwordIsActuallyPickedUpWithoutDiscardingBow(GameTestHelper h) {
        for (var type : archers()) {
            var skeleton = spawn(h, type);
            skeleton.setCanPickUpLoot(true);
            ItemStack bow = new ItemStack(Items.BOW); bow.setDamageValue(17);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
            ItemStack sword = new ItemStack(Items.IRON_SWORD); sword.setDamageValue(23);
            var item = new ItemEntity(h.getLevel(), skeleton.getX(), skeleton.getY(), skeleton.getZ(), sword.copy());
            item.setNoPickUpDelay(); h.getLevel().addFreshEntity(item);
            skeleton.aiStep();
            h.assertTrue(item.isRemoved(), "Native Mob looting consumes the dropped sword");
            h.assertTrue(skeleton.getMainHandItem() == bow && ItemStack.matches(skeleton.getOffhandItem(), sword),
                    "The same worn bow remains equipped and the full sword is retained");
            h.assertTrue(skeleton.isPersistenceRequired(), "Picked-up player equipment prevents despawning");
            skeleton.discard(); item.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualMiteSwordsAndDaggersUpgradeTheSpareWithoutDuplicatingEitherWeapon(GameTestHelper h) {
        for (var type : archers()) for (String id : List.of("mite:copper_sword", "mite:copper_dagger")) {
            var skeleton = spawn(h, type); skeleton.setCanPickUpLoot(true);
            ItemStack bow = new ItemStack(Items.BOW); skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
            ItemStack wooden = new ItemStack(Items.WOODEN_SWORD); wooden.setDamageValue(17);
            skeleton.equipItemIfPossible(wooden);
            ItemStack incoming = new ItemStack(MiteTools.get(id)); incoming.setDamageValue(23);
            h.assertFalse(incoming.getItem() instanceof net.minecraft.world.item.SwordItem,
                    "The regression exercises MITE's actual non-SwordItem weapon implementation");
            try (var spawned = new SpawnedEntityCapture(h.getLevel(), entity -> entity instanceof ItemEntity)) {
                var item = new ItemEntity(h.getLevel(), skeleton.getX(), skeleton.getY(), skeleton.getZ(), incoming.copy());
                item.setNoPickUpDelay(); h.getLevel().addFreshEntity(item); skeleton.aiStep();
                h.assertTrue(item.isRemoved(), "Native pickup consumes the better MITE weapon");
                h.assertTrue(skeleton.getMainHandItem() == bow && ItemStack.matches(skeleton.getOffhandItem(), incoming),
                        "MITE sword/dagger upgrades the weaker spare and retains the same bow");
                var oldDrops = spawned.live(ItemEntity.class).stream().filter(d -> ItemStack.matches(d.getItem(), wooden)).toList();
                h.assertValueEqual(oldDrops.size(), 1, "The previously picked-up wooden sword is dropped exactly once");
                h.assertValueEqual(spawned.live(ItemEntity.class).stream().filter(d -> ItemStack.matches(d.getItem(), incoming)).count(), 0L,
                        "The accepted better weapon is not duplicated on the ground");
                skeleton.discard(); item.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realDisarmingCooldownBlocksSpareRecoveryAndSurvivesReloadUntilTickForty(GameTestHelper h) {
        for (var type : archers()) {
            var skeleton = spawn(h, type); skeleton.setCanPickUpLoot(true);
            skeleton.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D); skeleton.setHealth(200F);
            ItemStack bow = new ItemStack(Items.BOW); skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
            ItemStack sword = new ItemStack(Items.IRON_SWORD); skeleton.equipItemIfPossible(sword);
            var attacker = h.makeMockPlayer(GameType.SURVIVAL);
            attacker.setPos(skeleton.getX() + 1, skeleton.getY(), skeleton.getZ());
            ItemStack disarming = new ItemStack(Items.IRON_SWORD);
            disarming.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(R196Enchantments.key("disarming")), 5);
            attacker.setItemSlot(EquipmentSlot.MAINHAND, disarming);
            try (var spawned = new SpawnedEntityCapture(h.getLevel(), entity -> entity instanceof ItemEntity)) {
                attacker.attack(skeleton);
                h.assertTrue(skeleton.getMainHandItem().isEmpty() && ItemStack.matches(skeleton.getOffhandItem(), sword)
                        && SourceEnchantmentCombat.disarmed(skeleton), "Actual native player attack disarms only the current bow");
                h.assertFalse(((SkeletonDualWeapons)skeleton).mite$updateDualWeapons(), "The spare cannot bypass the fresh disarming cooldown");
                CompoundTag saved = skeleton.saveWithoutId(new CompoundTag());
                var restored = type.create(h.getLevel()); restored.load(saved);
                for (int tick = 0; tick < 39; ++tick) {
                    SourceEnchantmentCombat.tick(new EntityTickEvent.Pre(restored));
                    h.assertFalse(((SkeletonDualWeapons)restored).mite$updateDualWeapons(), "Cooldown still blocks automatic spare recovery through tick 39");
                }
                h.assertTrue(restored.getMainHandItem().isEmpty() && !restored.canPickUpLoot(), "Saving cannot bypass the equipment cooldown");
                SourceEnchantmentCombat.tick(new EntityTickEvent.Pre(restored));
                h.assertTrue(((SkeletonDualWeapons)restored).mite$updateDualWeapons() && ItemStack.matches(restored.getMainHandItem(), sword)
                        && restored.getOffhandItem().isEmpty() && restored.canPickUpLoot(), "Tick 40 restores exactly the saved spare weapon");
                h.assertValueEqual(spawned.live(ItemEntity.class).stream().filter(d -> ItemStack.matches(d.getItem(), bow)).count(), 1L,
                        "The disarmed bow remains a single original world item");
                skeleton.discard(); restored.discard(); attacker.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void switchingUsesHysteresisAndChangesTheInstalledAttackGoal(GameTestHelper h) {
        for (var type : archers()) {
            var skeleton = spawn(h, type); var target = spawn(h, EntityType.COW);
            ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow); skeleton.equipItemIfPossible(sword);
            moveTarget(skeleton, target, 5);
            h.assertFalse(((SkeletonDualWeapons)skeleton).mite$updateDualWeapons(), "Exactly five blocks retains ranged weapon");
            moveTarget(skeleton, target, 4.9);
            h.assertTrue(((SkeletonDualWeapons)skeleton).mite$updateDualWeapons(), "Near target switches to sword");
            h.assertTrue(skeleton.getMainHandItem().is(Items.IRON_SWORD) && skeleton.getOffhandItem() == bow,
                    "Switch stores the same bow instead of throwing it away");
            h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof SourceMeleeGoal)
                    && skeleton.goalSelector.getAvailableGoals().stream().noneMatch(w -> w.getGoal() instanceof SourceRangedAttackGoal),
                    "Only the actual melee attack goal remains installed");
            moveTarget(skeleton, target, 6);
            h.assertFalse(((SkeletonDualWeapons)skeleton).mite$updateDualWeapons(), "Exactly six blocks retains sword");
            moveTarget(skeleton, target, 6.1);
            h.assertTrue(((SkeletonDualWeapons)skeleton).mite$updateDualWeapons() && skeleton.getMainHandItem() == bow,
                    "Distant visible target restores the original bow");
            h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof SourceRangedAttackGoal),
                    "The source ranged goal is restored");
            skeleton.discard(); target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bothWeaponsAndTheirDropChancesSurviveSavingAfterSwitch(GameTestHelper h) {
        for (var type : archers()) {
            var skeleton = spawn(h, type); var target = spawn(h, EntityType.COW);
            ItemStack bow = new ItemStack(Items.BOW); bow.setDamageValue(37);
            ItemStack sword = new ItemStack(Items.IRON_SWORD); sword.setDamageValue(19);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow); skeleton.setDropChance(EquipmentSlot.MAINHAND, .17F);
            moveTarget(skeleton, target, 3);
            skeleton.equipItemIfPossible(sword);
            CompoundTag saved = new CompoundTag(); skeleton.addAdditionalSaveData(saved);
            var restored = type.create(h.getLevel()); restored.readAdditionalSaveData(saved);
            h.assertTrue(ItemStack.matches(restored.getMainHandItem(), sword) && ItemStack.matches(restored.getOffhandItem(), bow),
                    "Saving retains the full components, damage and equipped mode of both stacks");
            CompoundTag again = new CompoundTag(); restored.addAdditionalSaveData(again);
            h.assertTrue(saved.getList("HandDropChances", 5).equals(again.getList("HandDropChances", 5)),
                    "Picked-up guaranteed sword and original bow each retain their own drop chance");
            h.assertTrue(again.getList("HandDropChances", 5).getFloat(0) > 1F
                    && again.getList("HandDropChances", 5).getFloat(1) == .17F, "Switching moves drop chance with each weapon");
            skeleton.discard(); restored.discard(); target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pickedUpBowAndSwordBothDropExactlyOnceInEitherCombatMode(GameTestHelper h) {
        for (var type : archers()) for (boolean close : new boolean[]{false, true}) {
            var skeleton = spawn(h, type); var target = spawn(h, EntityType.COW);
            ItemStack bow = new ItemStack(Items.BOW); bow.setDamageValue(31);
            ItemStack sword = new ItemStack(Items.IRON_SWORD); sword.setDamageValue(29);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); // Both following stacks must really be picked up, rather than compared with the new unworn birth bow.
            skeleton.equipItemIfPossible(bow); skeleton.equipItemIfPossible(sword);
            moveTarget(skeleton, target, close ? 3 : 9); ((SkeletonDualWeapons)skeleton).mite$updateDualWeapons();
            boolean[] observed = {false};
            Consumer<LivingDropsEvent> listener = event -> {
                if (event.getEntity() != skeleton) return;
                observed[0] = true;
                h.assertValueEqual(event.getDrops().stream().filter(d -> ItemStack.matches(d.getItem(), bow)).count(), 1L,
                        "Picked-up bow survives native death with its exact durability");
                h.assertValueEqual(event.getDrops().stream().filter(d -> ItemStack.matches(d.getItem(), sword)).count(), 1L,
                        "Picked-up sword survives native death with its exact durability");
                event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { skeleton.hurt(h.getLevel().damageSources().generic(), 1000F); }
            finally { NeoForge.EVENT_BUS.unregister(listener); skeleton.discard(); target.discard(); }
            h.assertTrue(observed[0], "Real death reaches the native cancellable drop event");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyMainHandRecoversSpareAndPureMeleeVariantsRemainMelee(GameTestHelper h) {
        var archer = spawn(h, EntityType.SKELETON);
        archer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); // Simulate a broken/disarmed bow, including early archers born with a plain bow.
        archer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
        h.assertTrue(((SkeletonDualWeapons)archer).mite$updateDualWeapons() && archer.getMainHandItem().is(Items.BOW),
                "A broken or disarmed empty main hand can recover its spare weapon");
        archer.discard();
        for (var type : archers()) {
            var melee = spawn(h, type);
            if (melee instanceof R196Skeleton old) old.setSkeletonType(2);
            else ((OrdinarySkeletonState)melee).mite$setSourceSkeletonType(2);
            melee.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            melee.equipItemIfPossible(new ItemStack(Items.IRON_SWORD));
            h.assertTrue(melee.getMainHandItem().is(Items.IRON_SWORD) && melee.getOffhandItem().isEmpty(),
                    "Dedicated melee variant follows its original single-weapon equipment policy");
            melee.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realArrowsLeadLateralMotionForOrdinaryAndLongdeadArchers(GameTestHelper h) {
        for (var type : archers()) {
            var skeleton = spawn(h, type); var target = spawn(h, EntityType.COW);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            moveTarget(skeleton, target, 18); target.setDeltaMovement(0, 0, .3);
            double averageZ = 0;
            for (int n = 0; n < 24; ++n) {
                skeleton.performRangedAttack(target, 1F);
                var shots = arrows(h, skeleton); h.assertValueEqual(shots.size(), 1, "Native attack creates one projectile");
                averageZ += shots.getFirst().getDeltaMovement().z; shots.forEach(AbstractArrow::discard);
            }
            h.assertTrue(averageZ / 24 > .1, "Release direction anticipates lateral target movement");
            skeleton.discard(); target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void releaseRetainsRandomSpreadAndArrowNeverTracksAfterLaunch(GameTestHelper h) {
        var skeleton = spawn(h, EntityType.SKELETON); var target = spawn(h, EntityType.COW);
        try {
            // Keep the eighteen-block shot clear of neighboring structures in the randomized test grid.
            BlockPos origin = new BlockPos(2_300_000 + SHOT_FIXTURES.getAndIncrement() * 64, 96, 2_300_000);
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -2), origin.offset(20, 3, 2)))
                h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            skeleton.moveTo(Vec3.atBottomCenterOf(origin));
            moveTarget(skeleton, target, 18);
            Vec3 previous = null; boolean varied = false;
            for (int seed = 0; seed < 12; ++seed) {
                Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel()); arrow.setOwner(skeleton);
                try {
                    arrow.setPos(skeleton.getX(), skeleton.getEyeY() - .1, skeleton.getZ()); arrow.getRandom().setSeed(seed);
                    h.assertTrue(SkeletonAiming.aimPoint(skeleton, target) != null, "Each spread sample has a visible target before release");
                    SkeletonAiming.shoot(skeleton, arrow, target, 1.6F);
                    Vec3 launch = arrow.getDeltaMovement();
                    h.assertTrue(launch.lengthSqr() > 1D, "Each native random draw launches a real moving arrow");
                    if (previous != null && launch.distanceToSqr(previous) > 1e-7) varied = true;
                    previous = launch;
                    target.setPos(target.getX(), target.getY(), target.getZ() + 20);
                    arrow.tick();
                    h.assertTrue(Math.abs(arrow.getDeltaMovement().x - launch.x * .99) < 1e-5
                            && Math.abs(arrow.getDeltaMovement().z - launch.z * .99) < 1e-5,
                            "After firing, the arrow only follows native drag and never turns toward target");
                    moveTarget(skeleton, target, 18);
                } finally { arrow.discard(); }
            }
            h.assertTrue(varied, "Different native random draws produce different inaccurate shots");
        } finally {
            skeleton.discard(); target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void solidWallPreventsShootingRatherThanRevealingAHiddenTarget(GameTestHelper h) {
        for (var type : archers()) {
            var skeleton = spawn(h, type); var target = spawn(h, EntityType.COW);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW)); moveTarget(skeleton, target, 4);
            BlockPos wall = skeleton.blockPosition().east();
            h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
            try {
                skeleton.performRangedAttack(target, 1F);
                h.assertValueEqual(arrows(h, skeleton).size(), 0, "An obstructed target cannot receive an assisted shot");
            } finally {
                h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
                h.getLevel().setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());
                skeleton.discard(); target.discard();
            }
        }
        h.succeed();
    }
}
