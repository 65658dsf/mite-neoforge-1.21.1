package io.github.ningmeng123.mite.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.phys.AABB;

/** Search-local geometry; vehicle avoidance does not change native entity collisions. @author NingMeng123 */
final class PursuitEntityObstacles {
    private record Obstacle(int id, UUID uuid, AABB box) {}

    private PursuitEntityObstacles() {}

    static Snapshot capture(PathfinderMob mob, AABB area) {
        return capture(mob, area, Set.of());
    }

    static Snapshot capture(PathfinderMob mob, AABB area, Set<UUID> ignoredBoats) {
        return new Snapshot(area, entries(mob, area, ignoredBoats), ignoredBoats);
    }

    private static List<Obstacle> entries(PathfinderMob mob, AABB area, Set<UUID> ignoredBoats) {
        List<Obstacle> result = new ArrayList<>();
        for (Entity entity : mob.level().getEntities(mob, area.inflate(.2D),
                other -> !ignoredBoats.contains(other.getUUID()) && relevant(mob, other)))
            result.add(new Obstacle(entity.getId(), entity.getUUID(), planningBox(mob, entity)));
        result.sort(Comparator.comparingInt(Obstacle::id));
        return List.copyOf(result);
    }

    private static boolean relevant(PathfinderMob mob, Entity entity) {
        return entity.isAlive() && !entity.isSpectator() && !entity.isPassengerOfSameVehicle(mob)
                && (entity instanceof AbstractMinecart || mob.canCollideWith(entity));
    }

    private static AABB planningBox(PathfinderMob mob, Entity entity) {
        // Entering a vehicle's pickup/push area can trap a pursuit even without a hard collision.
        boolean canBoard = entity instanceof Boat boat && !(boat.getControllingPassenger() instanceof Player)
                && boat.hasEnoughSpaceFor(mob) && boat.getPassengers().size() < (boat instanceof ChestBoat ? 1 : 2);
        return entity instanceof AbstractMinecart || canBoard
                ? entity.getBoundingBox().inflate(.2D, 0D, .2D) : entity.getBoundingBox();
    }

    static boolean clearOfVehicles(PathfinderMob mob, AABB body, boolean escapingStart) {
        return clearOfVehicles(mob, body, escapingStart, Set.of());
    }

    static boolean clearOfVehicles(PathfinderMob mob, AABB body, boolean escapingStart, Set<UUID> ignoredBoats) {
        for (Entity entity : mob.level().getEntities(mob, body.inflate(.2D, 0D, .2D),
                other -> !ignoredBoats.contains(other.getUUID()) && (other instanceof AbstractMinecart || other instanceof Boat) && relevant(mob, other)))
            if (planningBox(mob, entity).intersects(body)
                    && !(escapingStart && planningBox(mob, entity).intersects(mob.getBoundingBox()))) return false;
        return true;
    }

    /** Includes between-node motion, so a long edge cannot hide the candidate boat. */
    static boolean crossesBoat(PathfinderMob mob, Boat boat, AABB from, AABB to) {
        return crossesVolume(expandedBoat(mob, boat, from), from, to);
    }

    static AABB expandedBoat(PathfinderMob mob, Boat boat, AABB body) {
        return planningBox(mob, boat).inflate(body.getXsize() * .5D, body.getYsize() * .5D, body.getZsize() * .5D);
    }

    static boolean crossesVolume(AABB volume, AABB from, AABB to) {
        return volume.contains(from.getCenter()) || volume.contains(to.getCenter())
                || volume.clip(from.getCenter(), to.getCenter()).isPresent();
    }

    static final class Snapshot {
        private final AABB area;
        private final List<Obstacle> obstacles;
        private final Set<UUID> ignoredBoats;
        private final Map<Long, List<AABB>> bins = new HashMap<>();

        private Snapshot(AABB area, List<Obstacle> obstacles, Set<UUID> ignoredBoats) {
            this.area = area;
            this.obstacles = obstacles;
            this.ignoredBoats = Set.copyOf(ignoredBoats);
            for (Obstacle obstacle : obstacles) {
                AABB box = obstacle.box();
                for (int x = cell(box.minX); x <= cell(box.maxX); x++)
                    for (int z = cell(box.minZ); z <= cell(box.maxZ); z++)
                        bins.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(box);
            }
        }

        boolean isEmpty() { return obstacles.isEmpty(); }

        boolean unchanged(PathfinderMob mob) { return obstacles.equals(entries(mob, area, ignoredBoats)); }

        boolean intersects(AABB body) {
            for (int x = cell(body.minX); x <= cell(body.maxX); x++)
                for (int z = cell(body.minZ); z <= cell(body.maxZ); z++) {
                    List<AABB> boxes = bins.get(key(x, z));
                    if (boxes != null) for (AABB box : boxes) if (box.intersects(body)) return true;
                }
            return false;
        }

        boolean blocksMotion(AABB from, AABB to) {
            if (isEmpty()) return false;
            AABB swept = from.minmax(to);
            for (int x = cell(swept.minX); x <= cell(swept.maxX); x++)
                for (int z = cell(swept.minZ); z <= cell(swept.maxZ); z++) {
                    List<AABB> boxes = bins.get(key(x, z));
                    if (boxes == null) continue;
                    for (AABB box : boxes) {
                        AABB expanded = box.inflate(from.getXsize() * .5D, from.getYsize() * .5D, from.getZsize() * .5D);
                        if (expanded.contains(from.getCenter()) && !expanded.contains(to.getCenter())) continue;
                        if (expanded.contains(from.getCenter()) || expanded.contains(to.getCenter())
                                || expanded.clip(from.getCenter(), to.getCenter()).isPresent()) return true;
                    }
                }
            return false;
        }

        private static int cell(double coordinate) { return (int)Math.floor(coordinate / 4D); }
        private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    }
}
