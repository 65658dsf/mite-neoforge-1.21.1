package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.SourceRangedAttackGoal;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercise the installed attack goal, including its separate release-origin check. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SkeletonRangedGoalGameTests {
    private static <T extends Mob> T spawn(GameTestHelper h, EntityType<T> type) {
        T mob = type.create(h.getLevel());
        BlockPos pos = h.absolutePos(new BlockPos(2, 10, 2));
        mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static SourceRangedAttackGoal ranged(AbstractSkeleton skeleton) {
        return skeleton.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal())
                .filter(SourceRangedAttackGoal.class::isInstance).map(SourceRangedAttackGoal.class::cast)
                .findFirst().orElseThrow();
    }

    @GameTest(template = "skeleton_arrow_leaves")
    public static void installedBowGoalActuallyReleasesFromInsideLeaves(GameTestHelper h) {
        for (var type : List.of(EntityType.SKELETON, MiteMobs.type(MobKind.LONGDEAD))) {
            var skeleton = (AbstractSkeleton)spawn(h, type);
            var target = spawn(h, EntityType.COW);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            target.setPos(skeleton.getX() + 6, skeleton.getY() + 2, skeleton.getZ());
            skeleton.setTarget(target);
            BlockPos eye = BlockPos.containing(skeleton.getEyePosition());
            h.getLevel().setBlockAndUpdate(eye, Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, true));
            var goal = ranged(skeleton);
            try (var capture = new SpawnedEntityCapture(h.getLevel(),
                    e -> e instanceof AbstractArrow a && a.getOwner() == skeleton)) {
                h.assertTrue(goal.canUse() && SourceRangedAttackGoal.clearShot(skeleton, target),
                        "A leaf-embedded archer has a visible elevated target");
                goal.start();
                int interval = goal.attackInterval();
                for (int tick = 0; tick < interval; ++tick) goal.tick();
                h.assertTrue(capture.live(AbstractArrow.class).isEmpty(), "The existing cooldown is retained");
                goal.tick();
                var arrows = capture.live(AbstractArrow.class);
                h.assertValueEqual(arrows.size(), 1, "The installed goal releases one real arrow from foliage");
                AbstractArrow arrow = arrows.getFirst();
                double launchY = arrow.getY();
                arrow.tick();
                h.assertTrue(arrow.getX() > skeleton.getX() && arrow.getY() > launchY,
                        "The arrow leaves its foliage origin moving toward the elevated target");
                h.assertTrue(h.getLevel().getBlockState(eye).is(Blocks.OAK_LEAVES), "Shooting preserves the leaf block");
                arrows.forEach(AbstractArrow::discard);
            } finally {
                goal.stop();
                h.getLevel().setBlockAndUpdate(eye, Blocks.AIR.defaultBlockState());
                skeleton.discard();
                target.discard();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void removedTargetCannotKeepTheRangedGoalRunning(GameTestHelper h) {
        var skeleton = spawn(h, EntityType.SKELETON);
        var target = spawn(h, EntityType.COW);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        target.setPos(skeleton.getX() + 4, skeleton.getY(), skeleton.getZ());
        skeleton.setTarget(target);
        var goal = ranged(skeleton);
        h.assertTrue(goal.canUse(), "The living target enters the ranged goal");
        goal.start();
        target.discard();
        h.assertFalse(goal.canContinueToUse(), "An old navigation path cannot preserve a removed target");
        goal.stop();
        h.assertFalse(skeleton.isUsingItem(), "Stopping clears the bow use state");
        skeleton.discard();
        h.succeed();
    }
}
