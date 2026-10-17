package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.SkeletonAiming;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real release directions and native projectile ticks against exposed elevated targets. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SkeletonElevatedShotGameTests {
    private SkeletonElevatedShotGameTests() {}

    private static Vec3 point(GameTestHelper helper, double x, double y, double z) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new Vec3(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
    }

    private static void clear(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 10, 1), new BlockPos(14, 24, 6)))
            helper.setBlock(pos, Blocks.AIR);
    }

    private static List<EntityType<? extends AbstractSkeleton>> archers() {
        return List.of(EntityType.SKELETON, (EntityType<? extends AbstractSkeleton>)(EntityType<?>)MiteMobs.type(MobKind.LONGDEAD));
    }

    private static AbstractSkeleton skeleton(GameTestHelper helper, EntityType<? extends AbstractSkeleton> type) {
        AbstractSkeleton skeleton = type.create(helper.getLevel());
        skeleton.moveTo(point(helper, 1.5D, 12D, 3.5D));
        skeleton.setNoAi(true);
        skeleton.setNoGravity(true);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        return skeleton;
    }

    private static Cow target(GameTestHelper helper, double x, double y) {
        Cow target = EntityType.COW.create(helper.getLevel());
        target.moveTo(point(helper, x, y, 3.5D));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        target.setHealth(200F);
        helper.getLevel().addFreshEntity(target);
        return target;
    }

    private static void plane(GameTestHelper helper, Block block) {
        var state = block.defaultBlockState();
        if (state.hasProperty(LeavesBlock.PERSISTENT)) state = state.setValue(LeavesBlock.PERSISTENT, true);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(6, 10, 1), new BlockPos(6, 23, 6)))
            helper.setBlock(pos, state);
    }

    private static void tickFlight(AbstractArrow arrow) {
        for (int tick = 0; tick < 35 && !arrow.isRemoved(); tick++) arrow.tick();
    }

    private static int hits(GameTestHelper helper, AbstractSkeleton skeleton, Cow target) {
        int hits = 0;
        Vec3 previous = null;
        boolean varied = false;
        // A batch measures the ability to hit while retaining each shot's native random spread.
        for (int shot = 0; shot < 32; shot++) {
            target.setHealth(200F);
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.setDeltaMovement(Vec3.ZERO);
            try (var spawned = new SpawnedEntityCapture(helper.getLevel(),
                    entity -> entity instanceof AbstractArrow arrow && arrow.getOwner() == skeleton)) {
                skeleton.performRangedAttack(target, 1F);
                var arrows = spawned.live(AbstractArrow.class);
                helper.assertValueEqual(arrows.size(), 1, "A visible target receives one native skeleton arrow");
                AbstractArrow arrow = arrows.getFirst();
                Vec3 velocity = arrow.getDeltaMovement();
                if (previous != null && previous.distanceToSqr(velocity) > 1e-7) varied = true;
                previous = velocity;
                tickFlight(arrow);
                if (target.getHealth() < 200F) hits++;
            }
        }
        helper.assertTrue(varied, "The improved release direction retains random scatter");
        return hits;
    }

    @GameTest(template = "skeleton_elevated_shot")
    public static void exposedHeadIsActuallyAimedAtInsteadOfTheCoveredBody(GameTestHelper helper) {
        clear(helper);
        for (int z = 2; z <= 4; z++) helper.setBlock(new BlockPos(9, 12, z), Blocks.STONE);
        Cow target = target(helper, 10.5D, 12D);
        helper.runAfterDelay(1, () -> {
            try {
                for (var type : archers()) {
                    AbstractSkeleton skeleton = skeleton(helper, type);
                    try {
                        Vec3 middle = target.position().add(0D, target.getBbHeight() * .5D, 0D);
                        helper.assertFalse(SourceRangedAttackGoal.clearShot(skeleton, middle), "The wall hides the body midpoint");
                        helper.assertTrue(target.getEyePosition().equals(SkeletonAiming.aimPoint(skeleton, target)),
                                "The only exposed head becomes the shared visibility and release point");
                        helper.assertTrue(hits(helper, skeleton, target) >= 2,
                                "Actual inaccurate arrows hit the exposed head above its solid cover: " + type);
                    } finally { skeleton.discard(); }
                }
            } finally { target.discard(); }
            helper.succeed();
        });
    }

    @GameTest(template = "skeleton_elevated_shot")
    public static void nativeArrowsHitAnElevatedTargetThroughTheLeafCanopy(GameTestHelper helper) {
        clear(helper);
        plane(helper, Blocks.OAK_LEAVES);
        Cow target = target(helper, 13.5D, 18D);
        helper.runAfterDelay(1, () -> {
            try {
                for (var type : archers()) {
                    AbstractSkeleton skeleton = skeleton(helper, type);
                    try {
                        helper.assertTrue(SkeletonAiming.aimPoint(skeleton, target) != null, "Leaves do not hide the elevated target");
                        helper.assertTrue(hits(helper, skeleton, target) >= 2,
                                "Native arrows compensate height and gravity while crossing the canopy: " + type);
                    } finally { skeleton.discard(); }
                }
                helper.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(6, 16, 3));
            } finally { target.discard(); }
            helper.succeed();
        });
    }

    @GameTest(template = "skeleton_elevated_shot")
    public static void elevatedTargetBehindStoneCannotBeRevealedOrShotThrough(GameTestHelper helper) {
        clear(helper);
        plane(helper, Blocks.STONE);
        Cow target = target(helper, 13.5D, 18D);
        helper.runAfterDelay(1, () -> {
            try {
                for (var type : archers()) {
                    AbstractSkeleton skeleton = skeleton(helper, type);
                    try (var spawned = new SpawnedEntityCapture(helper.getLevel(),
                            entity -> entity instanceof AbstractArrow arrow && arrow.getOwner() == skeleton)) {
                        helper.assertTrue(SkeletonAiming.aimPoint(skeleton, target) == null, "Solid stone hides every aim point");
                        skeleton.performRangedAttack(target, 1F);
                        helper.assertValueEqual(spawned.live(AbstractArrow.class).size(), 0, "A fully covered target receives no shot");
                        plane(helper, Blocks.AIR);
                        skeleton.performRangedAttack(target, 1F);
                        var arrows = spawned.live(AbstractArrow.class);
                        helper.assertValueEqual(arrows.size(), 1, "Removing the wall permits one native shot");
                        plane(helper, Blocks.STONE);
                        target.setHealth(200F);
                        target.invulnerableTime = 0;
                        AbstractArrow arrow = arrows.getFirst();
                        tickFlight(arrow);
                        CompoundTag saved = arrow.saveWithoutId(new CompoundTag());
                        helper.assertTrue(saved.getBoolean("inGround") && target.getHealth() == 200F,
                                "A wall entering the flight path embeds the real arrow and protects the target");
                    } finally { skeleton.discard(); }
                }
            } finally { target.discard(); }
            helper.succeed();
        });
    }
}
