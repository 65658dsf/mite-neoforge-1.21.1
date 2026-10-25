package io.github.ningmeng123.mite.rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded walkable connections between nearby shallow caves. @author NingMeng123 */
public final class SurfaceCaveConnectionRules {
    public static final int CHUNK_WIDTH = 16;
    public static final int MIN_Y = 8, MAX_Y = 160;
    public static final int MIN_SURFACE_DEPTH = 8, MAX_SURFACE_DEPTH = 48;
    public static final int MAX_LINKS = 3, MAX_CARVED_BLOCKS = 240;
    public static final int MIN_WALL_LENGTH = 2, MAX_WALL_LENGTH = 10;
    private static final int MAX_DISTANCE = 12, MAX_RISE = 4;
    private static final int MIN_COMPONENT_SIZE = 3, CHOICES_PER_PAIR = 32;
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public enum Cell { AIR, ROCK, UNSAFE }

    /** Coordinates are local X/Z and absolute Y. Both vertical access limits are inclusive. */
    public interface Access {
        int minY();
        int maxY();
        /** First air height above the original surface; never the underground cavity ceiling. */
        int surfaceY(int x, int z);
        boolean dryColumn(int x, int z);
        Cell cell(int x, int y, int z);
    }

    public record Position(int x, int y, int z) {}
    public record Link(Position start, Position end, int rocks) {}
    /** Only previously solid ROCK positions appear in carved; existing AIR is never rewritten. */
    public record Plan(List<Position> carved, List<Link> links) {
        public static final Plan EMPTY = new Plan(List.of(), List.of());
        public Plan { carved = List.copyOf(carved); links = List.copyOf(links); }
    }

    private SurfaceCaveConnectionRules() {}

    public static Plan plan(Access access, long worldSeed, int chunkX, int chunkZ) {
        Snapshot s = new Snapshot(access);
        if (s.height < 5) return Plan.EMPTY;
        Components components = components(s);
        if (components.count < 2) return Plan.EMPTY;
        long seed = mix(worldSeed ^ (long)chunkX * 341873128712L ^ (long)chunkZ * 132897987541L);
        List<Candidate> candidates = candidates(s, components, seed);
        int[] parent = new int[components.count + 1];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        BitSet carved = new BitSet(s.cells.length), floors = new BitSet(s.cells.length);
        // Never cut an existing cave floor: adding a link must not sever a previous walking route.
        for (int node : components.nodes) {
            Position p = s.position(node); floors.set(s.index(p.x, p.y - 1, p.z));
        }
        List<Link> links = new ArrayList<>();
        List<Position> midpoints = new ArrayList<>();
        for (Candidate c : candidates) {
            if (links.size() == MAX_LINKS) break;
            int a = components.labels[c.a], b = components.labels[c.b];
            if (find(parent, a) == find(parent, b)) continue;
            Position start = s.position(c.a), end = s.position(c.b);
            Position midpoint = new Position((start.x + end.x) / 2, (start.y + end.y) / 2, (start.z + end.z) / 2);
            if (midpoints.stream().anyMatch(p -> square(p.x - midpoint.x) + square(p.z - midpoint.z) < 16
                    && Math.abs(p.y - midpoint.y) < 4)) continue;
            Corridor corridor = corridor(s, start, end, c.hash, carved, floors, true);
            if (corridor == null) corridor = corridor(s, start, end, c.hash, carved, floors, false);
            if (corridor == null || carved.cardinality() + corridor.rocks.cardinality() > MAX_CARVED_BLOCKS) continue;
            carved.or(corridor.rocks);
            floors.or(corridor.floors);
            parent[find(parent, b)] = find(parent, a);
            // A passage may touch more than its two endpoint rooms. Do not redundantly join them again.
            for (Position p : corridor.path) for (int[] d : CARDINAL) for (int dy = -1; dy <= 1; dy++) {
                int label = components.label(s, p.x + d[0], p.y + dy, p.z + d[1]);
                if (label == 0 || dy > 0 && !air(s, carved, p.x, p.y + 2, p.z)
                        || dy < 0 && !air(s, carved, p.x + d[0], p.y + dy + 2, p.z + d[1])) continue;
                parent[find(parent, label)] = find(parent, a);
            }
            links.add(new Link(start, end, corridor.rocks.cardinality()));
            midpoints.add(midpoint);
        }
        if (links.isEmpty()) return Plan.EMPTY;
        List<Position> positions = new ArrayList<>(carved.cardinality());
        for (int i = carved.nextSetBit(0); i >= 0; i = carved.nextSetBit(i + 1)) positions.add(s.position(i));
        return new Plan(positions, links);
    }

    private record Components(int[] labels, int[] sizes, int count, List<Integer> nodes) {
        int label(Snapshot s, int x, int y, int z) { return s.contains(x, y, z) ? labels[s.index(x, y, z)] : 0; }
    }

    private static Components components(Snapshot s) {
        int[] labels = new int[s.cells.length];
        BitSet walk = new BitSet(s.cells.length);
        List<Integer> nodes = new ArrayList<>();
        for (int y = Math.max(MIN_Y, s.minY + 1); y <= Math.min(MAX_Y, s.maxY - 3); y++)
            for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) {
                if (s.eligible(x, y, z) && s.cell(x, y, z) == Cell.AIR
                        && s.cell(x, y + 1, z) == Cell.AIR && s.cell(x, y - 1, z) == Cell.ROCK) {
                    int index = s.index(x, y, z);
                    walk.set(index); nodes.add(index);
                }
            }
        int count = 0;
        List<Integer> sizes = new ArrayList<>(); sizes.add(0);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int node : nodes) {
            if (labels[node] != 0) continue;
            count++; int size = 0; labels[node] = count; queue.add(node);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst(); size++;
                Position p = s.position(current);
                for (int[] d : CARDINAL) for (int dy = -1; dy <= 1; dy++) {
                    int x = p.x + d[0], y = p.y + dy, z = p.z + d[1];
                    if (!s.contains(x, y, z)) continue;
                    int next = s.index(x, y, z);
                    if (!walk.get(next) || labels[next] != 0) continue;
                    // A one-block step needs clearance over the lower side as well.
                    if (dy > 0 && s.cell(p.x, p.y + 2, p.z) != Cell.AIR
                            || dy < 0 && s.cell(x, y + 2, z) != Cell.AIR) continue;
                    labels[next] = count; queue.add(next);
                }
            }
            sizes.add(size);
        }
        return new Components(labels, sizes.stream().mapToInt(Integer::intValue).toArray(), count, nodes);
    }

    private record Candidate(int a, int b, int distance, long hash) {}
    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator.comparingInt(Candidate::distance)
            .thenComparing((a, b) -> Long.compareUnsigned(a.hash, b.hash)).thenComparingInt(Candidate::a).thenComparingInt(Candidate::b);

    private static List<Candidate> candidates(Snapshot s, Components components, long seed) {
        Map<Long, List<Candidate>> pairs = new HashMap<>();
        for (int a : components.nodes) {
            Position p = s.position(a);
            int label = components.labels[a];
            if (!s.interior(p.x, p.z) || components.sizes[label] < MIN_COMPONENT_SIZE) continue;
            for (int x = 2; x < 14; x++) for (int z = 2; z < 14; z++) {
                int distance = square(x - p.x) + square(z - p.z);
                if (distance < 9 || distance > MAX_DISTANCE * MAX_DISTANCE) continue;
                for (int dy = -MAX_RISE; dy <= MAX_RISE; dy++) {
                    int y = p.y + dy;
                    if (!s.contains(x, y, z)) continue;
                    int b = s.index(x, y, z), other = components.labels[b];
                    if (b <= a || other == 0 || other == label || components.sizes[other] < MIN_COMPONENT_SIZE) continue;
                    long pair = ((long)Math.min(label, other) << 32) | Math.max(label, other);
                    List<Candidate> choices = pairs.computeIfAbsent(pair, ignored -> new ArrayList<>());
                    Candidate candidate = new Candidate(a, b, distance + Math.abs(dy) * 4, mix(seed ^ (long)a * 31L ^ (long)b * 0x9E3779B97F4A7C15L));
                    choices.add(candidate); choices.sort(CANDIDATE_ORDER);
                    if (choices.size() > CHOICES_PER_PAIR) choices.removeLast();
                }
            }
        }
        List<Candidate> result = new ArrayList<>(); pairs.values().forEach(result::addAll); result.sort(CANDIDATE_ORDER);
        return result;
    }

    private record Corridor(BitSet rocks, BitSet floors, List<Position> path) {}

    private static Corridor corridor(Snapshot s, Position start, Position end, long hash, BitSet existing,
                                     BitSet protectedFloors, boolean bend) {
        List<Position> path = path(start, end, hash, bend);
        if (path.size() > MAX_DISTANCE + 3) return null;
        int walls = 0, run = 0, longest = 0;
        BitSet rocks = new BitSet(s.cells.length), floors = new BitSet(s.cells.length), volume = new BitSet(s.cells.length);
        for (int i = 0; i < path.size(); i++) {
            Position p = path.get(i);
            if (!s.interior(p.x, p.z) || !s.eligible(p.x, p.y, p.z)) return null;
            if (s.cell(p.x, p.y, p.z) == Cell.ROCK || s.cell(p.x, p.y + 1, p.z) == Cell.ROCK) {
                walls++; longest = Math.max(longest, ++run);
            } else run = 0;
            Position before = path.get(Math.max(0, i - 1)), after = path.get(Math.min(path.size() - 1, i + 1));
            if (Math.abs(p.y - before.y) > 1 || Math.abs(p.y - after.y) > 1) return null;
            boolean alongX = Math.abs(after.x - before.x) >= Math.abs(after.z - before.z);
            boolean stepClearance = before.y > p.y || after.y > p.y;
            for (int width = -1; width <= 1; width++) {
                int x = p.x + (alongX ? 0 : width), z = p.z + (alongX ? width : 0);
                int ceiling = width == 0 && ((mix(hash + i) & 3L) == 0 || stepClearance) ? 2 : 1;
                if (!s.eligible(x, p.y, z) || s.cell(x, p.y - 1, z) == Cell.UNSAFE) return null;
                int floor = s.index(x, p.y - 1, z);
                if (s.cell(x, p.y - 1, z) != Cell.ROCK || existing.get(floor)) {
                    if (width == 0) return null;
                    // Round cave mouths often drop away beside the walking line. Leave those
                    // side columns intact instead of demanding a flat three-wide platform.
                    for (int h = 0; h <= ceiling; h++) {
                        int y = p.y + h;
                        if (s.cell(x, y, z) == Cell.UNSAFE) return null;
                        if (air(s, existing, x, y, z)) volume.set(s.index(x, y, z));
                    }
                    continue;
                }
                floors.set(floor);
                for (int h = 0; h <= ceiling; h++) {
                    int y = p.y + h;
                    if (s.cell(x, y, z) == Cell.UNSAFE) return null;
                    int index = s.index(x, y, z); volume.set(index);
                    if (s.cell(x, y, z) == Cell.ROCK && !existing.get(index)) rocks.set(index);
                }
            }
        }
        if (walls < MIN_WALL_LENGTH || walls > MAX_WALL_LENGTH || longest < MIN_WALL_LENGTH || rocks.isEmpty()
                || rocks.intersects(floors) || rocks.intersects(protectedFloors)) return null;
        // Inspect an intact one-block shell before carving, including roof/floor and end caps.
        for (int i = volume.nextSetBit(0); i >= 0; i = volume.nextSetBit(i + 1)) {
            Position p = s.position(i);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
                if (s.cell(p.x + dx, p.y + dy, p.z + dz) == Cell.UNSAFE) return null;
        }
        // A narrowed corner must still join by cardinal steps; diagonal air contact is insufficient.
        if (!walkConnected(s, start, end, path, rocks, existing)) return null;
        return new Corridor(rocks, floors, path);
    }

    private static boolean walkConnected(Snapshot s, Position start, Position end, List<Position> path,
                                         BitSet rocks, BitSet existing) {
        BitSet air = (BitSet)existing.clone(); air.or(rocks);
        int minX = start.x, maxX = start.x, minY = start.y, maxY = start.y, minZ = start.z, maxZ = start.z;
        for (Position p : path) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
            minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
        }
        minX--; maxX++; minY--; maxY++; minZ--; maxZ++;
        BitSet visited = new BitSet(s.cells.length);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int first = s.index(start.x, start.y, start.z);
        visited.set(first); queue.add(first);
        while (!queue.isEmpty()) {
            Position p = s.position(queue.removeFirst());
            if (p.equals(end)) return true;
            for (int[] d : CARDINAL) for (int dy = -1; dy <= 1; dy++) {
                int x = p.x + d[0], y = p.y + dy, z = p.z + d[1];
                if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ
                        || !s.contains(x, y - 1, z) || !s.contains(x, y + 2, z)) continue;
                int index = s.index(x, y, z), floor = s.index(x, y - 1, z);
                if (visited.get(index) || !air(s, air, x, y, z) || !air(s, air, x, y + 1, z)
                        || s.cell(x, y - 1, z) != Cell.ROCK || air.get(floor)
                        || dy > 0 && !air(s, air, p.x, p.y + 2, p.z)
                        || dy < 0 && !air(s, air, x, y + 2, z)) continue;
                visited.set(index); queue.add(index);
            }
        }
        return false;
    }

    private static List<Position> path(Position start, Position end, long hash, boolean bend) {
        int dx = end.x - start.x, dz = end.z - start.z;
        int bendX = 0, bendZ = 0;
        if (bend && Math.max(Math.abs(dx), Math.abs(dz)) >= 6) {
            int sign = (hash & 1L) == 0 ? -1 : 1;
            if (Math.abs(dx) >= Math.abs(dz)) bendZ = sign; else bendX = sign;
        }
        Position middle = new Position((start.x + end.x) / 2 + bendX, start.y, (start.z + end.z) / 2 + bendZ);
        List<Position> horizontal = new ArrayList<>();
        line(horizontal, start.x, start.z, middle.x, middle.z);
        line(horizontal, middle.x, middle.z, end.x, end.z);
        List<Position> result = new ArrayList<>(horizontal.size());
        for (int i = 0; i < horizontal.size(); i++) {
            Position p = horizontal.get(i);
            int y = start.y + (int)Math.round((end.y - start.y) * i / (double)Math.max(1, horizontal.size() - 1));
            result.add(new Position(p.x, y, p.z));
        }
        return result;
    }

    private static void line(List<Position> out, int ax, int az, int bx, int bz) {
        int length = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
        for (int i = 0; i <= length; i++) {
            Position p = new Position(ax + (int)Math.round((bx - ax) * i / (double)Math.max(1, length)), 0,
                    az + (int)Math.round((bz - az) * i / (double)Math.max(1, length)));
            if (out.isEmpty() || out.getLast().x != p.x || out.getLast().z != p.z) out.add(p);
        }
    }

    private static final class Snapshot {
        final int minY, maxY, height;
        final Cell[] cells;
        final int[] surfaces = new int[256];
        final boolean[] dry = new boolean[256];
        Snapshot(Access access) {
            minY = Math.max(access.minY(), MIN_Y - 1); maxY = Math.min(access.maxY(), MAX_Y + 4);
            height = Math.max(0, maxY - minY + 1); cells = new Cell[256 * height];
            Arrays.fill(cells, Cell.UNSAFE);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                surfaces[x * 16 + z] = access.surfaceY(x, z); dry[x * 16 + z] = access.dryColumn(x, z);
                for (int y = minY; y <= maxY; y++) {
                    Cell cell = access.cell(x, y, z);
                    cells[index(x, y, z)] = cell == null ? Cell.UNSAFE : cell;
                }
            }
        }
        int index(int x, int y, int z) { return (y - minY) * 256 + x * 16 + z; }
        Position position(int index) { return new Position(index % 256 / 16, index / 256 + minY, index % 16); }
        boolean contains(int x, int y, int z) { return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= minY && y <= maxY; }
        boolean interior(int x, int z) { return x >= 2 && x < 14 && z >= 2 && z < 14; }
        Cell cell(int x, int y, int z) { return contains(x, y, z) ? cells[index(x, y, z)] : Cell.UNSAFE; }
        boolean eligible(int x, int y, int z) {
            if (!contains(x, y, z) || y < MIN_Y || y > MAX_Y || y + 3 > maxY) return false;
            int depth = surfaces[x * 16 + z] - y;
            return dry[x * 16 + z] && depth >= MIN_SURFACE_DEPTH && depth <= MAX_SURFACE_DEPTH;
        }
    }

    private static int square(int value) { return value * value; }
    private static boolean air(Snapshot s, BitSet carved, int x, int y, int z) {
        return s.cell(x, y, z) == Cell.AIR || s.contains(x, y, z) && carved.get(s.index(x, y, z));
    }
    private static int find(int[] parents, int value) {
        while (parents[value] != value) { parents[value] = parents[parents[value]]; value = parents[value]; }
        return value;
    }
    private static long mix(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
