package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.combat.MobWeaponReach;
import io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Navigation-only support for existing vanilla hostile ground melee goals. @author NingMeng123 */
public final class VanillaMeleePursuit {
    private static final Map<PathfinderMob, Progress> PROGRESS = new WeakHashMap<>();

    /** Values do not retain their weak mob key, its level, navigation or living target. */
    private static final class Progress {
        final int targetId;
        Vec3 position;
        long movedAt, checkedAt = Long.MIN_VALUE;
        Path path;
        boolean clear;
        boolean opensDoors, passesDoors;

        Progress(PathfinderMob mob, LivingEntity target, Path route) {
            targetId = target.getId(); position = mob.position(); movedAt = mob.level().getGameTime(); path = route;
        }
    }

    private VanillaMeleePursuit() {}

    public static boolean supports(PathfinderMob mob) {
        return mob instanceof Enemy && !(mob instanceof NeutralMob) && !R196MonsterAi.managed(mob)
                && BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace().equals("minecraft")
                && mob.getNavigation() instanceof GroundPathNavigation
                && !(mob.getNavigation() instanceof WallClimberNavigation) && !mob.isPassenger()
                && !(mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem)
                && !(mob instanceof Drowned && mob.getMainHandItem().is(Items.TRIDENT))
                && mob.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof MeleeAttackGoal);
    }

    public static boolean applies(PathfinderMob mob, @Nullable LivingEntity target) {
        return supports(mob) && !mob.level().isClientSide && target instanceof Player player && target.isAlive()
                && target.level() == mob.level() && MeleePursuitPaths.inRange(mob, target)
                && !player.isCreative() && !player.isSpectator();
    }

    /** This is geometric planning only; native goals retain stun, roar and attack cooldown checks. */
    public static boolean canStrikeFrom(PathfinderMob mob, LivingEntity target, Vec3 feet) {
        if (!target.isAlive() || target.level() != mob.level() || !MobWeaponReach.canReachFrom(mob, target, feet)) return false;
        if (BetterMobCombat.supported(mob)) return BetterMobCombat.canStrikeFrom(mob, target, feet);
        var attack = ((MobAttackBoundsAccess)mob).mite$attackBoundingBox().move(feet.subtract(mob.position()));
        if (!attack.intersects(target.getHitbox())) return false;
        return mob.level().clip(new ClipContext(feet.add(0D, mob.getEyeHeight(), 0D), target.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
    }

    static int attackPositionRadius(PathfinderMob mob, LivingEntity target) {
        var attack = ((MobAttackBoundsAccess)mob).mite$attackBoundingBox();
        return Math.clamp((int)Math.ceil(Math.max(attack.getXsize() + target.getHitbox().getXsize(),
                attack.getZsize() + target.getHitbox().getZsize()) * .5D), 2, 6);
    }

    /** Called only by a currently running native melee goal, never by patrol or door goals. */
    public static void recordProgress(PathfinderMob mob, LivingEntity target, @Nullable Path route) {
        if (route == null || !applies(mob, target)) { clear(mob); return; }
        Progress progress = PROGRESS.get(mob);
        if (progress == null || progress.targetId != target.getId()) {
            PROGRESS.put(mob, new Progress(mob, target, route));
            return;
        }
        if (progress.path != route) { progress.path = route; progress.checkedAt = Long.MIN_VALUE; }
        if (progress.position.distanceToSqr(mob.position()) >= .25D) {
            progress.position = mob.position(); progress.movedAt = mob.level().getGameTime();
        }
    }

    public static void clear(PathfinderMob mob) { PROGRESS.remove(mob); }

    public static boolean followsReachableRoute(PathfinderMob mob, @Nullable LivingEntity target) {
        Progress progress = PROGRESS.get(mob);
        long now = mob.level().getGameTime();
        if (!applies(mob, target) || progress == null || progress.targetId != target.getId()
                || now < progress.movedAt || now - progress.movedAt >= 60L
                || progress.path != mob.getNavigation().getPath() || mob.getNavigation().isDone()
                || mob.getNavigation().isStuck() || !MeleePursuitPaths.reachesVanilla(mob, progress.path, target)) return false;
        boolean opens = mob.getNavigation().getNodeEvaluator().canOpenDoors();
        boolean passes = mob.getNavigation().getNodeEvaluator().canPassDoors();
        if (progress.opensDoors != opens || progress.passesDoors != passes) {
            progress.checkedAt = Long.MIN_VALUE;
            progress.opensDoors = opens; progress.passesDoors = passes;
        }
        if (progress.checkedAt == Long.MIN_VALUE || now - progress.checkedAt >= 10L) {
            progress.checkedAt = now;
            progress.clear = MeleePursuitPaths.completeVanillaRouteClear(mob, progress.path, target);
        }
        return progress.clear;
    }
}
