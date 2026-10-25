package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static io.github.ningmeng123.mite.rules.SurfaceCaveConnectionRules.Cell;
import static io.github.ningmeng123.mite.rules.SurfaceCaveConnectionRules.Plan;
import static io.github.ningmeng123.mite.rules.SurfaceCaveConnectionRules.Position;
import static org.junit.jupiter.api.Assertions.*;

/** Physical walking, protection and bounded-generation regression tests. @author NingMeng123 */
class SurfaceCaveConnectionRulesTest {
    @Test
    void opensThinWallAndActuallyJoinsWalkableRooms() {
        Terrain terrain = twoRooms(60, 0);
        Position left = new Position(3, 60, 7), right = new Position(12, 60, 7);
        assertFalse(walkable(terrain, left, right));

        Plan plan = plan(terrain);
        assertEquals(1, plan.links().size());
        assertFalse(plan.carved().isEmpty());
        assertTrue(plan.carved().stream().allMatch(p -> terrain.cell(p.x(), p.y(), p.z()) == Cell.ROCK));
        Terrain result = terrain.apply(plan);
        assertTrue(walkable(result, left, right), "a link must yield a supported two-block-high route");
        assertTrue(plan.carved().stream().noneMatch(p -> p.y() < 60), "the original cave floor stays intact");
    }

    @Test
    void joinsRaisedRoomsWithRealStepHeadroom() {
        for (int rise = 1; rise <= 4; rise++) {
            Terrain terrain = twoRooms(60, rise);
            Position left = new Position(3, 60, 7), right = new Position(12, 60 + rise, 7);
            Plan plan = plan(terrain);
            assertFalse(plan.links().isEmpty(), "supported ramp for a height difference of " + rise);
            assertTrue(walkable(terrain.apply(plan), left, right), "players must fit above each step: " + rise);
        }
    }

    @Test
    void diagonalAndBentLinksAlsoHavePhysicalWalkingRoutes() {
        int successfulLayouts = 0;
        for (int offset : new int[]{2, 4, 6}) for (int rise = 0; rise <= 4; rise++) {
            Terrain terrain = new Terrain();
            terrain.box(2, 5, 60, 65, 3, 6, Cell.AIR);
            terrain.box(10, 13, 60 + rise, 65 + rise, 3 + offset, 6 + offset, Cell.AIR);
            Plan plan = SurfaceCaveConnectionRules.plan(terrain, 173L + offset * 31L + rise, -7, 13);
            if (!plan.links().isEmpty()) successfulLayouts++;
            Terrain result = terrain.apply(plan);
            for (SurfaceCaveConnectionRules.Link link : plan.links()) {
                assertTrue(walkable(result, link.start(), link.end()), "diagonal link cannot depend on diagonal player movement");
            }
        }
        assertTrue(successfulLayouts >= 10, "diagonal planning must connect more than just flat rooms");
    }

    @Test
    void naturalMouthWithLowerSidesCanJoinWithoutAFlatThreeWideFloor() {
        Terrain terrain = narrowMouth();
        Plan plan = plan(terrain);
        assertFalse(plan.links().isEmpty(), "a supported center ledge can meet a wider tunnel");
        Terrain result = terrain.apply(plan);
        assertTrue(walkable(result, new Position(3, 60, 7), new Position(12, 60, 7)));
        for (int x = 2; x <= 5; x++) assertEquals(Cell.ROCK, result.cell(x, 59, 7));
    }

    @Test
    void unsupportedRockOverhangAtAMouthIsLeftIntact() {
        Terrain terrain = narrowMouth();
        terrain.box(5, 5, 60, 61, 6, 6, Cell.ROCK);
        terrain.box(5, 5, 60, 61, 8, 8, Cell.ROCK);
        Plan plan = plan(terrain);
        assertFalse(plan.links().isEmpty());
        Terrain result = terrain.apply(plan);
        assertTrue(walkable(result, new Position(3, 60, 7), new Position(12, 60, 7)));
        for (int z : new int[]{6, 8}) for (int y = 60; y <= 61; y++) {
            assertEquals(Cell.ROCK, result.cell(5, y, z), "unsupported side columns must not be widened");
        }
    }

    @Test
    void airConnectedHighTubeDoesNotPretendThereIsAWalkingRoute() {
        Terrain terrain = twoRooms(60, 1);
        terrain.box(5, 10, 65, 66, 7, 7, Cell.AIR);
        Position left = new Position(3, 60, 7), right = new Position(12, 61, 7);
        assertTrue(airConnected(terrain, left, right));
        assertFalse(walkable(terrain, left, right));
        Plan plan = plan(terrain);
        assertFalse(plan.links().isEmpty());
        assertTrue(walkable(terrain.apply(plan), left, right));
    }

    @Test
    void alreadyWalkableCavesDoNotGainExtraTunnels() {
        Terrain terrain = twoRooms(60, 0);
        terrain.box(5, 10, 60, 62, 7, 7, Cell.AIR);
        assertTrue(walkable(terrain, new Position(3, 60, 7), new Position(12, 60, 7)));
        assertEquals(Plan.EMPTY, plan(terrain));
    }

    @Test
    void refusesDistantDiagonalRoomsRatherThanExcavatingAcrossTheChunk() {
        Terrain terrain = new Terrain();
        terrain.box(1, 3, 60, 65, 1, 3, Cell.AIR);
        terrain.box(12, 14, 60, 65, 12, 14, Cell.AIR);
        assertEquals(Plan.EMPTY, plan(terrain));
    }

    @Test
    void protectsThinRoofsWetColumnsAndOutOfBandCaves() {
        Terrain thinRoof = twoRooms(60, 0); Arrays.fill(thinRoof.surface, 67);
        Terrain wet = twoRooms(60, 0); Arrays.fill(wet.dry, false);
        Terrain deep = twoRooms(30, 0);
        Terrain tooHigh = twoRooms(170, 0); Arrays.fill(tooHigh.surface, 200);
        assertAll(
                () -> assertEquals(Plan.EMPTY, plan(thinRoof)),
                () -> assertEquals(Plan.EMPTY, plan(wet)),
                () -> assertEquals(Plan.EMPTY, plan(deep)),
                () -> assertEquals(Plan.EMPTY, plan(tooHigh)));
    }

    @Test
    void unsafeBarrierAndOneBlockSafetyShellAreNeverBreached() {
        Terrain barrier = twoRooms(60, 0);
        barrier.box(7, 7, 7, 164, 0, 15, Cell.UNSAFE);
        assertEquals(Plan.EMPTY, plan(barrier), "water, lava and protected blocks veto the whole crossing");

        Terrain protectedRoof = twoRooms(60, 0);
        protectedRoof.box(0, 15, 62, 62, 0, 15, Cell.UNSAFE);
        assertEquals(Plan.EMPTY, plan(protectedRoof), "a protected block next to the headroom also vetoes it");

        Terrain smallObstacle = twoRooms(60, 0);
        smallObstacle.set(7, 61, 7, Cell.UNSAFE);
        Plan detour = plan(smallObstacle);
        for (Position p : detour.carved()) {
            assertTrue(Math.abs(p.x() - 7) > 1 || Math.abs(p.y() - 61) > 1 || Math.abs(p.z() - 7) > 1);
        }
        assertEquals(Cell.UNSAFE, smallObstacle.apply(detour).cell(7, 61, 7));
    }

    @Test
    void negativeChunkCoordinatesAreDeterministicAndPlansAreImmutable() {
        Terrain terrain = twoRooms(60, 1);
        Plan first = SurfaceCaveConnectionRules.plan(terrain, -0x123456789abcdefL, -731, -19);
        Plan second = SurfaceCaveConnectionRules.plan(terrain, -0x123456789abcdefL, -731, -19);
        assertFalse(first.carved().isEmpty());
        assertEquals(first, second);
        assertThrows(UnsupportedOperationException.class, () -> first.carved().add(new Position(0, 0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> first.links().clear());
        assertTrue(first.carved().stream().allMatch(p -> p.x() >= 1 && p.x() <= 14 && p.z() >= 1 && p.z() <= 14));
    }

    @Test
    void limitsTotalWorkWhileConnectingSeveralIndependentPairs() {
        Terrain terrain = new Terrain();
        for (int y : new int[]{60, 72, 84}) addRooms(terrain, y, 0);
        Plan plan = plan(terrain);
        assertEquals(3, plan.links().size());
        assertTrue(plan.carved().size() <= SurfaceCaveConnectionRules.MAX_CARVED_BLOCKS);
        assertEquals(plan.carved().size(), new HashSet<>(plan.carved()).size());
        assertEquals(plan.carved().size(), plan.links().stream().mapToInt(SurfaceCaveConnectionRules.Link::rocks).sum());
        Terrain result = terrain.apply(plan);
        for (int y : new int[]{60, 72, 84}) {
            assertTrue(walkable(result, new Position(3, y, 7), new Position(12, y, 7)));
        }
    }

    @Test
    void existingRaisedWalkwaysKeepEveryOriginalStandingPosition() {
        Terrain terrain = twoRooms(60, 0);
        // This walkable shelf hangs above the short lower connection candidates.
        terrain.box(6, 9, 63, 67, 5, 10, Cell.AIR);
        Set<Position> standingBefore = new HashSet<>();
        for (int y = 8; y <= 160; y++) for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) {
            Position p = new Position(x, y, z);
            if (standing(terrain, p)) standingBefore.add(p);
        }
        Plan plan = plan(terrain);
        assertFalse(plan.links().isEmpty(), "safe alternatives around the shelf still connect caves");
        Terrain result = terrain.apply(plan);
        for (Position p : standingBefore) {
            assertTrue(standing(result, p), "carving must preserve an existing standing position at " + p);
        }
        assertTrue(walkable(result, new Position(7, 63, 6), new Position(8, 63, 9)), "the shelf stays walkable");
    }

    @Test
    void touchingARaisedSideRoomWithoutStepHeadroomDoesNotMarkItConnected() {
        Terrain terrain = new Terrain();
        terrain.box(5, 5, 60, 61, 4, 6, Cell.AIR);
        terrain.box(10, 13, 60, 65, 4, 13, Cell.AIR);
        terrain.box(2, 4, 61, 66, 4, 13, Cell.AIR);
        // Make the second safe crossing farther along the same pair of cave walls.
        terrain.box(5, 10, 7, 164, 8, 8, Cell.UNSAFE);
        Position low = new Position(5, 60, 5), right = new Position(12, 60, 5), raised = new Position(3, 61, 12);
        assertFalse(walkable(terrain, low, raised));
        Plan plan = plan(terrain);
        Terrain result = terrain.apply(plan);
        assertTrue(walkable(result, low, right));
        assertTrue(walkable(result, low, raised), "the raised side room needs a genuine second route");
        assertEquals(2, plan.links().size());
    }

    private static Plan plan(Terrain terrain) { return SurfaceCaveConnectionRules.plan(terrain, 91234123L, 0, 0); }
    private static Terrain twoRooms(int y, int rise) {
        Terrain terrain = new Terrain(); addRooms(terrain, y, rise); return terrain;
    }
    private static Terrain narrowMouth() {
        Terrain terrain = twoRooms(60, 0);
        terrain.box(2, 5, 50, 59, 5, 6, Cell.AIR);
        terrain.box(2, 5, 50, 59, 8, 10, Cell.AIR);
        return terrain;
    }
    private static void addRooms(Terrain terrain, int y, int rise) {
        terrain.box(2, 5, y, y + 5, 5, 10, Cell.AIR);
        terrain.box(10, 13, y + rise, y + rise + 5, 5, 10, Cell.AIR);
    }

    /** Independent player route search: standing support plus swept headroom on one-block stairs. */
    private static boolean walkable(Terrain terrain, Position from, Position to) {
        Set<Position> visited = new HashSet<>(); ArrayDeque<Position> queue = new ArrayDeque<>();
        if (!standing(terrain, from)) return false;
        visited.add(from); queue.add(from);
        while (!queue.isEmpty()) {
            Position p = queue.remove(); if (p.equals(to)) return true;
            for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int rise = -1; rise <= 1; rise++) {
                    Position next = new Position(p.x() + direction[0], p.y() + rise, p.z() + direction[1]);
                    if (!standing(terrain, next) || rise > 0 && terrain.at(p.x(), p.y() + 2, p.z()) != Cell.AIR
                            || rise < 0 && terrain.at(next.x(), next.y() + 2, next.z()) != Cell.AIR) continue;
                    if (visited.add(next)) queue.add(next);
                }
            }
        }
        return false;
    }
    private static boolean standing(Terrain terrain, Position p) {
        return terrain.at(p.x(), p.y(), p.z()) == Cell.AIR && terrain.at(p.x(), p.y() + 1, p.z()) == Cell.AIR
                && terrain.at(p.x(), p.y() - 1, p.z()) == Cell.ROCK;
    }
    private static boolean airConnected(Terrain terrain, Position from, Position to) {
        Set<Position> visited = new HashSet<>(); ArrayDeque<Position> queue = new ArrayDeque<>();
        visited.add(from); queue.add(from);
        while (!queue.isEmpty()) {
            Position p = queue.remove(); if (p.equals(to)) return true;
            for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                Position next = new Position(p.x() + d[0], p.y() + d[1], p.z() + d[2]);
                if (terrain.at(next.x(), next.y(), next.z()) == Cell.AIR && visited.add(next)) queue.add(next);
            }
        }
        return false;
    }

    private static final class Terrain implements SurfaceCaveConnectionRules.Access {
        private final Cell[] cells = new Cell[256 * 256];
        private final int[] surface = new int[256];
        private final boolean[] dry = new boolean[256];
        Terrain() {
            Arrays.fill(cells, Cell.ROCK); Arrays.fill(surface, 100); Arrays.fill(dry, true);
        }
        @Override public int minY() { return -64; }
        @Override public int maxY() { return 191; }
        @Override public int surfaceY(int x, int z) { requireColumn(x, z); return surface[x * 16 + z]; }
        @Override public boolean dryColumn(int x, int z) { requireColumn(x, z); return dry[x * 16 + z]; }
        @Override public Cell cell(int x, int y, int z) {
            requireColumn(x, z);
            if (y < minY() || y > maxY()) throw new AssertionError("read outside inclusive access height");
            return cells[(y + 64) * 256 + x * 16 + z];
        }
        Cell at(int x, int y, int z) {
            return x < 0 || x > 15 || z < 0 || z > 15 || y < minY() || y > maxY() ? Cell.UNSAFE : cell(x, y, z);
        }
        void set(int x, int y, int z, Cell cell) { cells[(y + 64) * 256 + x * 16 + z] = cell; }
        void box(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Cell cell) {
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) set(x, y, z, cell);
        }
        Terrain apply(Plan plan) {
            Terrain result = new Terrain();
            System.arraycopy(cells, 0, result.cells, 0, cells.length);
            System.arraycopy(surface, 0, result.surface, 0, surface.length);
            System.arraycopy(dry, 0, result.dry, 0, dry.length);
            for (Position p : plan.carved()) result.set(p.x(), p.y(), p.z(), Cell.AIR);
            return result;
        }
        private void requireColumn(int x, int z) {
            if (x < 0 || x > 15 || z < 0 || z > 15) throw new AssertionError("planner read a neighboring chunk");
        }
    }
}
