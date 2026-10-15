package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live source goal and metadata regressions for the modern bow animation lifecycle. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SkeletonBowAnimationGameTests {
    private static <T extends Mob> T spawn(GameTestHelper h, EntityType<T> type) {
        T mob = type.create(h.getLevel());
        BlockPos position = h.absolutePos(new BlockPos(1, 10, 1));
        mob.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5, 0, 0);
        mob.setNoAi(true); mob.setNoGravity(true);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static ItemStack materialBow() {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mite", "ancient_metal_bow")));
    }

    private static WrappedGoal ranged(AbstractSkeleton skeleton) {
        return skeleton.goalSelector.getAvailableGoals().stream()
                .filter(w -> w.getGoal() instanceof SourceRangedAttackGoal).findFirst().orElseThrow();
    }

    private static List<EntityType<? extends AbstractSkeleton>> sourceSkeletons() {
        return List.of(EntityType.SKELETON, EntityType.WITHER_SKELETON,
                (EntityType<? extends AbstractSkeleton>)(EntityType<?>)MiteMobs.type(MobKind.LONGDEAD),
                (EntityType<? extends AbstractSkeleton>)(EntityType<?>)MiteMobs.type(MobKind.LONGDEAD_GUARDIAN),
                (EntityType<? extends AbstractSkeleton>)(EntityType<?>)MiteMobs.type(MobKind.BONE_LORD),
                (EntityType<? extends AbstractSkeleton>)(EntityType<?>)MiteMobs.type(MobKind.ANCIENT_BONE_LORD));
    }

    @GameTest(template = "empty")
    public static void sourceSkeletonsRaiseAndUseBothVanillaAndMaterialBows(GameTestHelper h) {
        for (var type : sourceSkeletons()) for (ItemStack weapon : List.of(new ItemStack(Items.BOW), materialBow())) {
            var skeleton = spawn(h, type); var target = spawn(h, EntityType.COW);
            target.setPos(skeleton.getX() + 4, skeleton.getY(), skeleton.getZ());
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy()); skeleton.setTarget(target);
            WrappedGoal goal = ranged(skeleton);
            h.assertTrue(goal.canUse(), "The installed bow goal accepts its current target: " + type);
            goal.start(); goal.tick();
            h.assertTrue(skeleton.isAggressive(), "Native models receive the attacking pose flag: " + type);
            h.assertTrue(skeleton.isUsingItem() && skeleton.getUsedItemHand() == InteractionHand.MAIN_HAND
                    && skeleton.getUseItem() == skeleton.getMainHandItem(), "The actual equipped bow is drawn: " + type);
            goal.stop(); skeleton.discard(); target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void arrowReleaseResetsDrawingWithoutChangingSourceSixtyTickCadence(GameTestHelper h) {
        long previousTime = h.getLevel().getDayTime();
        var skeleton = spawn(h, EntityType.SKELETON); var target = spawn(h, EntityType.COW);
        try {
            h.getLevel().setDayTime(6_000L);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, materialBow());
            target.setPos(skeleton.getX() + 4, skeleton.getY(), skeleton.getZ()); skeleton.setTarget(target);
            WrappedGoal wrapped = ranged(skeleton); var goal = (SourceRangedAttackGoal)wrapped.getGoal();
            h.assertTrue(wrapped.canUse(), "Actual installed source goal acquires the target"); wrapped.start();
            for (int tick = 0; tick < 60; tick++) wrapped.tick();
            h.assertTrue(skeleton.isUsingItem(), "The bow remains drawn before the source cooldown expires");
            h.assertValueEqual(arrows(h, skeleton).size(), 0, "Animation does not fire before the original source countdown");
            wrapped.tick();
            h.assertValueEqual(arrows(h, skeleton).size(), 1, "One real source arrow is released on the unchanged sixty-tick countdown");
            h.assertTrue(!skeleton.isUsingItem() && skeleton.isAggressive(), "Release clears drawing but preserves active aiming");
            h.assertValueEqual(goal.remainingAttackTicks(), 60, "Release still starts exactly the source cooldown");
            wrapped.tick();
            h.assertTrue(skeleton.isUsingItem(), "The next cycle starts a fresh bow draw");
            h.assertValueEqual(arrows(h, skeleton).size(), 1, "Restarting animation cannot duplicate the projectile");
            wrapped.stop();
        } finally {
            arrows(h, skeleton).forEach(AbstractArrow::discard);
            skeleton.discard(); target.discard(); h.getLevel().setDayTime(previousTime);
        }
        h.succeed();
    }

    private static List<AbstractArrow> arrows(GameTestHelper h, AbstractSkeleton skeleton) {
        return h.getLevel().getEntitiesOfClass(AbstractArrow.class, skeleton.getBoundingBox().inflate(32), arrow -> arrow.getOwner() == skeleton);
    }

    @GameTest(template = "empty")
    public static void goalStopClearsAimingAndDrawFlagsForEverySourceSkeleton(GameTestHelper h) {
        for (var type : sourceSkeletons()) {
            var skeleton = spawn(h, type); var target = spawn(h, EntityType.COW);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, materialBow());
            target.setPos(skeleton.getX() + 4, skeleton.getY(), skeleton.getZ()); skeleton.setTarget(target);
            WrappedGoal goal = ranged(skeleton); h.assertTrue(goal.canUse(), "Source target enters bow AI"); goal.start(); goal.tick();
            h.assertTrue(skeleton.isAggressive() && skeleton.isUsingItem(), "Fixture enters the actual drawn pose");
            goal.stop();
            h.assertTrue(!skeleton.isAggressive() && !skeleton.isUsingItem() && skeleton.getUseItem().isEmpty(),
                    "Stopping the goal clears the synchronized flags and held-use stack: " + type);
            skeleton.discard(); target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void replacingBowWithMeleeWeaponCancelsRunningDraw(GameTestHelper h) {
        for (var type : sourceSkeletons()) {
            var skeleton = spawn(h, type); var target = spawn(h, EntityType.COW);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, materialBow());
            target.setPos(skeleton.getX() + 4, skeleton.getY(), skeleton.getZ()); skeleton.setTarget(target);
            WrappedGoal old = ranged(skeleton); h.assertTrue(old.canUse(), "Source bow goal can start"); old.start(); old.tick();
            h.assertTrue(old.isRunning() && skeleton.isUsingItem(), "Fixture uses the actual running wrapped goal");
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.assertTrue(!old.isRunning() && !skeleton.isAggressive() && !skeleton.isUsingItem(),
                    "Weapon reassessment stops the old running goal and clears draw flags: " + type);
            h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof SourceMeleeGoal)
                    && skeleton.goalSelector.getAvailableGoals().stream().noneMatch(w -> w.getGoal() instanceof SourceRangedAttackGoal),
                    "Only the proper melee goal remains after changing weapon: " + type);
            skeleton.discard(); target.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void solidObstructionCancelsDrawingAndVisibleTargetRestartsIt(GameTestHelper h) {
        var skeleton = spawn(h, EntityType.SKELETON); var target = spawn(h, EntityType.COW);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, materialBow());
        target.setPos(skeleton.getX() + 4, skeleton.getY(), skeleton.getZ()); skeleton.setTarget(target);
        WrappedGoal goal = ranged(skeleton); h.assertTrue(goal.canUse(), "Target starts visible"); goal.start(); goal.tick();
        h.assertTrue(skeleton.isUsingItem(), "Visible source target starts drawing");
        BlockPos wall = skeleton.blockPosition().east();
        h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
        goal.tick(); h.assertFalse(skeleton.isUsingItem(), "Blocked projectile sight releases the bow model");
        h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());
        goal.tick(); h.assertTrue(skeleton.isUsingItem(), "Reacquiring projectile sight starts a fresh draw");
        target.setPos(skeleton.getX() + 31, skeleton.getY(), skeleton.getZ());
        goal.tick(); h.assertFalse(skeleton.isUsingItem(), "A target outside the thirty-block source radius is not drawn at");
        goal.stop(); skeleton.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void metadataPacketsCarryBowPoseWithoutAnyClientTargetReference(GameTestHelper h) {
        for (var type : sourceSkeletons()) {
            var skeleton = spawn(h, type); var observer = type.create(h.getLevel()); var target = spawn(h, EntityType.COW);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, materialBow()); observer.setItemSlot(EquipmentSlot.MAINHAND, materialBow());
            target.setPos(skeleton.getX() + 4, skeleton.getY(), skeleton.getZ()); skeleton.setTarget(target);
            skeleton.getEntityData().packDirty();
            WrappedGoal goal = ranged(skeleton); h.assertTrue(goal.canUse(), "Source goal accepts target"); goal.start(); goal.tick();
            transfer(h, skeleton, observer);
            h.assertTrue(observer.getTarget() == null && observer.isAggressive() && observer.isUsingItem()
                    && observer.getUsedItemHand() == InteractionHand.MAIN_HAND,
                    "Native watcher packet synchronizes attacking and use state with no observer target: " + type);
            goal.stop(); transfer(h, skeleton, observer);
            h.assertTrue(!observer.isAggressive() && !observer.isUsingItem(), "Subsequent native packet resets the pose: " + type);
            skeleton.discard(); observer.discard(); target.discard();
        }
        h.succeed();
    }

    private static void transfer(GameTestHelper h, Mob source, Mob observer) {
        var dirty = source.getEntityData().packDirty();
        h.assertTrue(dirty != null && !dirty.isEmpty(), "Animation changes actually dirty the native synced state");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer, new ClientboundSetEntityDataPacket(source.getId(), dirty));
            var packet = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
            h.assertTrue(packet.id() == source.getId() && !buffer.isReadable(), "Native entity metadata codec preserves the complete packet");
            observer.getEntityData().assignValues(packet.packedItems());
        } finally { buffer.release(); }
    }

    @GameTest(template = "empty")
    public static void witchAndSnowGolemDoNotAcquireSkeletonBowUseLifecycle(GameTestHelper h) {
        for (var type : List.of(EntityType.WITCH, EntityType.SNOW_GOLEM)) {
            var mob = spawn(h, type); var target = spawn(h, EntityType.COW);
            target.setPos(mob.getX() + 4, mob.getY(), mob.getZ()); mob.setTarget(target);
            SourceRangedAttackGoal goal = mob.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal)
                    .filter(SourceRangedAttackGoal.class::isInstance).map(SourceRangedAttackGoal.class::cast).findFirst().orElseThrow();
            h.assertTrue(goal.canUse(), "Other source ranged mob accepts target"); goal.start(); goal.tick();
            h.assertTrue(!mob.isAggressive() && !mob.isUsingItem(), "Skeleton animation flags do not leak into potion/snowball AI");
            goal.stop(); mob.discard(); target.discard();
        }
        h.succeed();
    }
}
