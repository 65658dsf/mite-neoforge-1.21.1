package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Monsters work through trapping boats or proven nearby boat roadblocks. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class MonsterBoatBreakout {
    public static final int WAIT_TICKS = 40;
    public static final int STAGE_INTERVAL_TICKS = 10;
    public static final int BREAK_STAGES = 11;
    private static final int ROAD_CHECK_TICKS = 20;
    private static final double ROAD_REACH = 1.5D;
    private static final Map<Mob, State> STATES = new WeakHashMap<>();
    private static final Map<PathfinderMob, RoadState> ROAD_STATES = new WeakHashMap<>();
    private static final Map<PathfinderMob, RoadCheck> ROAD_CHECKS = new WeakHashMap<>();

    private MonsterBoatBreakout() {}

    private static final class State {
        final UUID boat;
        int ridingTicks, stages, lastMobTick = Integer.MIN_VALUE;
        State(Boat boat) { this.boat = boat.getUUID(); }
    }

    /** UUIDs and immutable bounds only: a weak mob key must not be retained by its value. */
    private static final class RoadState {
        final UUID boat, target;
        final AABB boatBounds;
        int workTicks, stages, lastMobTick = Integer.MIN_VALUE;
        RoadState(Boat boat, LivingEntity target) {
            this.boat = boat.getUUID(); this.target = target.getUUID(); boatBounds = boat.getBoundingBox();
        }
    }

    private static final class RoadCheck {
        final UUID target;
        long nextAt;
        int candidateCursor;
        UUID waitingBoat;
        boolean waitingForBudget;
        RoadCheck(LivingEntity target) { this.target = target.getUUID(); }
    }

    /** Read-only diagnostics; no entity or level is retained in a state value. Server thread only. */
    record Progress(UUID boat, int ridingTicks, int stages) {}

    @Nullable static Progress progress(Mob mob) {
        State state = STATES.get(mob);
        return state == null ? null : new Progress(state.boat, state.ridingTicks, state.stages);
    }

    record RoadProgress(UUID boat, UUID target, int workTicks, int stages) {}

    @Nullable static RoadProgress roadProgress(PathfinderMob mob) {
        RoadState state = ROAD_STATES.get(mob);
        return state == null ? null : new RoadProgress(state.boat, state.target, state.workTicks, state.stages);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (!(mob.getVehicle() instanceof Boat boat) || !MonsterHunting.monster(mob) || !allowed(level, mob, boat)) {
            STATES.remove(mob);
            if (mob instanceof PathfinderMob pathfinder) tickRoad(level, pathfinder);
            return;
        }
        if (mob instanceof PathfinderMob pathfinder) clearRoad(pathfinder);
        State state = STATES.get(mob);
        if (state == null || !state.boat.equals(boat.getUUID())) {
            state = new State(boat);
            STATES.put(mob, state);
        }
        // Real passenger ticks increment tickCount too. A duplicate event cannot accelerate work.
        if (state.lastMobTick == mob.tickCount) return;
        state.lastMobTick = mob.tickCount;
        ++state.ridingTicks;
        if (state.ridingTicks < WAIT_TICKS || (state.ridingTicks - WAIT_TICKS) % STAGE_INTERVAL_TICKS != 0) return;

        mob.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, boat.blockPosition(), SoundEvents.WOOD_HIT, mob.getSoundSource(), .6F, .8F);
        if (++state.stages < BREAK_STAGES) return;

        // Vehicle damage decays each tick. Apply the completed break as one normal mob hit;
        // the boat owns invulnerability/protection checks, destruction, inventory and item drops.
        if (mob.getVehicle() == boat && allowed(level, mob, boat)) boat.hurt(mob.damageSources().mobAttack(mob), 5F);
        STATES.remove(mob);
    }

    private static boolean allowed(ServerLevel level, Mob mob, Boat boat) {
        return canBreak(level, mob, boat) && boat.hasPassenger(mob);
    }

    private static boolean canBreak(ServerLevel level, Mob mob, Boat boat) {
        return mob.isAlive() && !mob.isRemoved() && !mob.isNoAi() && boat.isAlive() && !boat.isInvulnerable()
                && boat.level() == level && EventHooks.canEntityGrief(level, mob);
    }

    private static void tickRoad(ServerLevel level, PathfinderMob mob) {
        LivingEntity target = mob.getTarget();
        if (!MonsterHunting.monster(mob) || mob.isPassenger() || !mob.isAlive() || mob.isRemoved() || mob.isNoAi()
                || !(target instanceof Player player) || !player.isAlive() || player.isCreative() || player.isSpectator()
                || target.level() != level || !MeleePursuitPaths.inRange(mob, target)
                || !(MeleePursuitPaths.applies(mob) || VanillaMeleePursuit.applies(mob, target))) {
            clearRoad(mob);
            return;
        }

        RoadState state = ROAD_STATES.get(mob);
        Boat boat = state != null && level.getEntity(state.boat) instanceof Boat value ? value : null;
        if (state != null && (!state.target.equals(target.getUUID()) || boat == null
                || !state.boatBounds.equals(boat.getBoundingBox()) || !canBreak(level, mob, boat) || !touchable(mob, boat))) {
            ROAD_STATES.remove(mob);
            state = null; boat = null;
        }
        RoadCheck check = ROAD_CHECKS.get(mob);
        if (check != null && !check.target.equals(target.getUUID())) {
            clearRoad(mob);
            check = null;
        }
        boolean retryAdmission = check != null && check.target.equals(target.getUUID()) && check.waitingForBudget;
        // Keep a deferred proof's admission alive on subsequent ticks; its actual search
        // still runs at most once per cadence. Async ordinary pursuit remains a pause.
        if (AsyncMeleePursuit.pending(mob)) return;
        if (retryAdmission && !MeleePursuitPaths.currentFailedPursuitWaitingForBudget(mob, target)) {
            // An ordinary fresh pursuit may legitimately take over this admission. Keep
            // existing work if it confirms the same obstruction instead of starting over.
            MeleePursuitPaths.cancelBoatRouteProof(mob);
            check.waitingForBudget = false; check.waitingBoat = null;
            retryAdmission = false;
        }
        if (!retryAdmission && MeleePursuitPaths.pending(mob)) return;
        if (!(retryAdmission ? MeleePursuitPaths.currentFailedPursuitWaitingForBudget(mob, target)
                : MeleePursuitPaths.currentFailedPursuit(mob, target))) {
            clearRoad(mob);
            return;
        }

        if (check == null || !check.target.equals(target.getUUID())) {
            check = new RoadCheck(target);
            ROAD_CHECKS.put(mob, check);
        }
        long now = level.getGameTime();
        boolean finalStage = state != null && state.stages == BREAK_STAGES - 1
                && state.workTicks + 1 >= WAIT_TICKS
                && (state.workTicks + 1 - WAIT_TICKS) % STAGE_INTERVAL_TICKS == 0;
        if (state == null || now >= check.nextAt || finalStage || retryAdmission) {
            // The final swing waits for its own fresh proof instead of bypassing this
            // cadence when the shared server budget temporarily defers verification.
            if (!retryAdmission && now < check.nextAt) return;
            if (retryAdmission && level.getEntity(check.waitingBoat) instanceof Boat pendingBoat
                    && canBreak(level, mob, pendingBoat) && touchable(mob, pendingBoat)) boat = pendingBoat;
            else if (retryAdmission) {
                MeleePursuitPaths.cancelBoatRouteProof(mob);
                check.waitingForBudget = false; check.waitingBoat = null;
                ROAD_STATES.remove(mob);
                return;
            }
            if (boat == null) {
                var candidates = level.getEntitiesOfClass(Boat.class, mob.getBoundingBox().inflate(ROAD_REACH + 2D),
                        candidate -> canBreak(level, mob, candidate) && touchable(mob, candidate));
                if (candidates.isEmpty()) { check.nextAt = now + ROAD_CHECK_TICKS; return; }
                candidates.sort(Comparator.comparingDouble(mob::distanceToSqr));
                // One nearby candidate per check bounds work even beside a pile of boats.
                boat = candidates.get(Math.floorMod(check.candidateCursor++, candidates.size()));
            }
            try {
                // This fresh, budgeted query verifies that the ordinary route is still
                // blocked and a complete route without roadblock boats really crosses this one.
                var proof = MeleePursuitPaths.routeWithoutBoat(mob, target, boat);
                check.waitingForBudget = false; check.waitingBoat = null;
                check.nextAt = now + ROAD_CHECK_TICKS;
                if (proof == null) {
                    ROAD_STATES.remove(mob);
                    return;
                }
            } catch (AsyncMeleePursuit.Pending pending) {
                check.waitingForBudget = true; check.waitingBoat = boat.getUUID();
                return;
            }
            if (state == null) {
                state = new RoadState(boat, target);
                ROAD_STATES.put(mob, state);
            }
        }

        if (state.lastMobTick == mob.tickCount) return;
        state.lastMobTick = mob.tickCount;
        mob.getLookControl().setLookAt(boat, 30F, 30F);
        ++state.workTicks;
        if (state.workTicks < WAIT_TICKS || (state.workTicks - WAIT_TICKS) % STAGE_INTERVAL_TICKS != 0) return;
        mob.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, boat.blockPosition(), SoundEvents.WOOD_HIT, mob.getSoundSource(), .6F, .8F);
        if (++state.stages < BREAK_STAGES) return;
        if (mob.getTarget() == target && canBreak(level, mob, boat) && touchable(mob, boat)) {
            boat.hurt(mob.damageSources().mobAttack(mob), 5F);
            // Destroying the obstruction must release the old failed-route backoff immediately.
            if (!boat.isAlive()) MeleePursuitPaths.invalidate(mob);
        }
        ROAD_STATES.remove(mob);
    }

    /** Physical proximity plus a real block collision ray prevents remote or through-wall work. */
    private static boolean touchable(PathfinderMob mob, Boat boat) {
        AABB body = mob.getBoundingBox(), bounds = boat.getBoundingBox();
        double x = Math.max(0D, Math.max(bounds.minX - body.maxX, body.minX - bounds.maxX));
        double y = Math.max(0D, Math.max(bounds.minY - body.maxY, body.minY - bounds.maxY));
        double z = Math.max(0D, Math.max(bounds.minZ - body.maxZ, body.minZ - bounds.maxZ));
        if (x * x + y * y + z * z > ROAD_REACH * ROAD_REACH) return false;
        Vec3 eye = mob.getEyePosition();
        Vec3 point = new Vec3(Mth.clamp(eye.x, bounds.minX + .001D, bounds.maxX - .001D),
                Mth.clamp(eye.y, bounds.minY + .001D, bounds.maxY - .001D),
                Mth.clamp(eye.z, bounds.minZ + .001D, bounds.maxZ - .001D));
        return mob.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType()
                == HitResult.Type.MISS;
    }

    private static void clearRoad(PathfinderMob mob) {
        ROAD_STATES.remove(mob);
        RoadCheck check = ROAD_CHECKS.remove(mob);
        if (check != null && check.waitingForBudget) MeleePursuitPaths.cancelBoatRouteProof(mob);
    }

    /** Observe accepted mount changes without cancelling them or moving passengers. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void mount(EntityMountEvent event) {
        if (!event.isCanceled() && !event.getLevel().isClientSide && event.getEntityMounting() instanceof Mob mob) {
            STATES.remove(mob);
            if (mob instanceof PathfinderMob pathfinder) clearRoad(pathfinder);
        }
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Mob mob) {
            STATES.remove(mob);
            if (mob instanceof PathfinderMob pathfinder) clearRoad(pathfinder);
        }
    }
}
