package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.mixin.BlazeChargeAccess;
import io.github.ningmeng123.mite.rules.SourceFireballRules;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Replaces only the native source species' attacks; wandering, flight and modern projectile impact remain native. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class NativeFireballAttacks {
    private NativeFireballAttacks() {}
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        install(mob);
    }
    public static void install(Mob mob) {
        if (mob.getType() == EntityType.BLAZE && mob instanceof Blaze blaze) {
            if (blaze.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof BlazeAttack)) return;
            blaze.goalSelector.removeAllGoals(goal -> goal.getClass().getEnclosingClass() == Blaze.class);
            blaze.goalSelector.addGoal(4, new BlazeAttack(blaze));
        } else if (mob.getType() == EntityType.GHAST && mob instanceof Ghast ghast) {
            if (ghast.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof GhastAttack)) return;
            ghast.goalSelector.removeAllGoals(goal -> goal.getClass().getEnclosingClass() == Ghast.class && goal.getFlags().isEmpty());
            ghast.goalSelector.addGoal(7, new GhastAttack(ghast));
            ghast.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?>);
            ghast.targetSelector.addGoal(1, new GhastTarget(ghast));
        }
    }
    private static Vec3 center(LivingEntity entity) { return entity.position().add(0, entity.getBbHeight() * .5F, 0); }
    /** getKnownMovement supplies the server player's received displacement; source falls back per axis above 1 block/tick. */
    private static Vec3 motion(LivingEntity target) {
        Vec3 ordinary = target.getDeltaMovement();
        if (!(target instanceof ServerPlayer)) return ordinary;
        Vec3 known = target.getKnownMovement();
        return new Vec3(Math.abs(known.x) <= 1D ? known.x : ordinary.x, 0,
                Math.abs(known.z) <= 1D ? known.z : ordinary.z);
    }
    private static Vec3 vector(SourceFireballRules.Direction direction) { return new Vec3(direction.x(), direction.y(), direction.z()); }

    public static final class BlazeAttack extends Goal {
        private final Blaze blaze;
        private final SourceFireballRules.BlazeCycle cycle = new SourceFireballRules.BlazeCycle();
        public BlazeAttack(Blaze blaze) { this.blaze = blaze; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return blaze.getTarget() != null && blaze.getTarget().isAlive() && blaze.canAttack(blaze.getTarget()); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { ((BlazeChargeAccess)blaze).mite$setCharged(false); }
        public int attackStep() { return cycle.step(); }
        @Override public void tick() {
            LivingEntity target = blaze.getTarget();
            if (target == null || !target.isAlive()) return;
            boolean clear = SourceRangedAttackGoal.clearShot(blaze, target);
            double distanceSquared = blaze.distanceToSqr(target);
            if (clear && distanceSquared < 4D && target.getBoundingBox().maxY > blaze.getBoundingBox().minY
                    && target.getBoundingBox().minY < blaze.getBoundingBox().maxY && cycle.ready(blaze.tickCount)) {
                cycle.melee(blaze.tickCount);
                blaze.doHurtTarget(target);
            } else if (clear && distanceSquared < SourceFireballRules.BLAZE_RANGE * SourceFireballRules.BLAZE_RANGE) {
                Vec3 delta = center(target).subtract(center(blaze));
                float lead = SourceFireballRules.blazeLead(delta.lengthSqr(), blaze.getRandom().nextFloat());
                Vec3 movement = motion(target);
                Vec3 aim = delta.add(movement.x * lead, 0, movement.z * lead);
                if (cycle.advance(blaze.tickCount)) {
                    var random = blaze.getRandom();
                    var direction = SourceFireballRules.blazeDirection(aim.x, aim.y, aim.z, (float)Math.sqrt(distanceSquared),
                            random.nextGaussian(), random.nextGaussian(), random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
                    SmallFireball fireball = new SmallFireball(blaze.level(), blaze, vector(direction));
                    fireball.setPos(blaze.getX(), blaze.getY(.5D) + .5D, blaze.getZ());
                    if (!blaze.isSilent()) blaze.level().levelEvent(null, 1018, blaze.blockPosition(), 0);
                    blaze.level().addFreshEntity(fireball);
                }
                blaze.setYRot((float)(Math.atan2(aim.z, aim.x) * 180D / Math.PI) - 90F);
                blaze.getLookControl().setLookAt(target, 10F, 10F);
            } else {
                blaze.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1D);
            }
            ((BlazeChargeAccess)blaze).mite$setCharged(cycle.charged());
        }
    }

    public static final class GhastAttack extends Goal {
        private final Ghast ghast;
        private final SourceFireballRules.GhastCycle cycle = new SourceFireballRules.GhastCycle();
        public GhastAttack(Ghast ghast) { this.ghast = ghast; }
        @Override public boolean canUse() { return true; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { ghast.setCharging(false); }
        public int chargeTime() { return cycle.charge(); }
        @Override public void tick() {
            LivingEntity target = ghast.getTarget();
            boolean eligible = target != null && target.isAlive()
                    && ghast.distanceToSqr(target) < SourceFireballRules.GHAST_RANGE * SourceFireballRules.GHAST_RANGE
                    && SourceMonsterTargeting.canSee(ghast, target, false);
            boolean fire = cycle.advance(eligible);
            if (eligible && cycle.warning() && !ghast.isSilent()) ghast.level().levelEvent(null, 1015, ghast.blockPosition(), 0);
            if (fire) {
                Vec3 origin = center(ghast), targetCenter = center(target);
                var random = ghast.getRandom();
                float lead = SourceFireballRules.ghastLead(origin.distanceToSqr(targetCenter), random.nextFloat());
                Vec3 movement = motion(target);
                Vec3 aim = targetCenter.add(movement.x * lead, 0, movement.z * lead).subtract(origin);
                // The old projectile starts at the shooter's centre; setTarget measures from the projectile centre (+.5Y).
                var direction = SourceFireballRules.ghastDirection(aim.x, aim.y - .5D, aim.z,
                        random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat());
                Vec3 travel = vector(direction);
                LargeFireball fireball = new LargeFireball(ghast.level(), ghast, travel, ghast.getExplosionPower());
                fireball.setPos(origin.add(travel.scale(4D)));
                if (!ghast.isSilent()) ghast.level().levelEvent(null, 1016, ghast.blockPosition(), 0);
                ghast.level().addFreshEntity(fireball);
            }
            ghast.setCharging(cycle.charging());
        }
    }

    /** tj searches visible vulnerable players in a sphere every 21 owner ticks; no modern four-block vertical band. */
    public static final class GhastTarget extends Goal {
        private final Ghast ghast;
        private int remaining;
        public GhastTarget(Ghast ghast) { this.ghast = ghast; setFlags(EnumSet.of(Flag.TARGET)); }
        @Override public boolean canUse() { return true; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            LivingEntity current = ghast.getTarget();
            if (current != null && !current.isAlive()) { ghast.setTarget(null); current = null; }
            if (current != null && remaining-- > 0) return;
            Player nearest = null;
            double best = Double.MAX_VALUE;
            for (Player player : ghast.level().players()) {
                if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable) continue;
                double range = SourceFireballRules.GHAST_TARGET_RANGE;
                if (player.isCrouching()) range *= .8F;
                if (player.isInvisible()) range *= .7F * Math.max(.1F, player.getArmorCoverPercentage());
                double distance = player.position().distanceToSqr(center(ghast));
                if (distance < range * range && distance < best && SourceMonsterTargeting.canSee(ghast, player, false)) {
                    nearest = player; best = distance;
                }
            }
            ghast.setTarget(nearest);
            if (nearest != null) remaining = 20;
        }
    }
}
