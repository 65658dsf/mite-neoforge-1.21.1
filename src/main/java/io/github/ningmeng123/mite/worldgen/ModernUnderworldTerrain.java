package io.github.ningmeng123.mite.worldgen;

import java.util.LinkedHashMap;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * Explicit 1.21.1 height/accessibility adaptation of the independently verified R196 terrain.
 * Interior bedrock becomes mineable stone, and short dry ledges receive walkable stone steps.
 *
 * @author NingMeng123
 */
public final class ModernUnderworldTerrain {
    public static final int CURRENT_LAYOUT_VERSION = 2;
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    public static final int MAX_Y_EXCLUSIVE = MIN_Y + HEIGHT;
    public static final int CAVERN_OFFSET = fromLegacyY(R196UnderworldTerrain.CAVERN_OFFSET);
    public static final int CAVERN_TOP_EXCLUSIVE = fromLegacyY(R196UnderworldTerrain.CAVERN_OFFSET + 128);
    public static final int SEA_LEVEL = fromLegacyY(R196UnderworldTerrain.SEA_LEVEL);
    public static final int SPAWN_HEIGHT = fromLegacyY(160);
    public static final int MAX_MANTLE_Y = fromLegacyY(3) - 1;
    public static final int MIN_PORTAL_MANTLE_Y = -1;
    public static final int MAX_PORTAL_MANTLE_Y = 1;
    public static final int MIN_ROOF_Y = fromLegacyY(R196UnderworldTerrain.HEIGHT - 3);

    private static final int PORTAL_MANTLE_CELL_SIZE = 31;
    private static final int STEP_RADIUS = 2;
    private static final int MAX_STEP_RISE = 3;
    private static final int HEADROOM = 3;
    private static final int PADDED_WIDTH = 16 + STEP_RADIUS * 2;
    private static final int CAVERN_HEIGHT = CAVERN_TOP_EXCLUSIVE - CAVERN_OFFSET;
    private static final int MAX_CACHED_SOURCE_CHUNKS = 64;

    private final R196UnderworldTerrain source;
    private final long mantleSeed;
    private final R196OctaveNoise mantleRoughness;
    private final LinkedHashMap<Long, byte[]> sourceChunks = new LinkedHashMap<>(16, .75f, true);

    public ModernUnderworldTerrain(long seed) {
        source = new R196UnderworldTerrain(seed);
        mantleSeed = seed ^ 0x4D414E544C45L;
        mantleRoughness = new R196OctaveNoise(new Random(mantleSeed), 1);
    }

    public long hashedSeed() { return source.hashedSeed(); }

    /** Source layer represented at an absolute modern Y; the vertical extent is scaled by 3/2. */
    public static int toLegacyY(int y) {
        return Math.floorDiv((y - MIN_Y) * R196UnderworldTerrain.HEIGHT, HEIGHT);
    }

    /** First modern Y representing the supplied source layer (also maps exclusive boundaries). */
    public static int fromLegacyY(int legacyY) {
        return MIN_Y - Math.floorDiv(-legacyY * HEIGHT, R196UnderworldTerrain.HEIGHT);
    }

    /** Unlike the archival array index this accepts absolute negative world Y coordinates. */
    public static int index(int x, int y, int z) {
        return (x * 16 + z) * HEIGHT + y - MIN_Y;
    }

    public byte[] generate(int chunkX, int chunkZ) {
        byte[] original = sourceChunk(chunkX, chunkZ);
        byte[] adapted = new byte[16 * 16 * HEIGHT];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y; y < MAX_Y_EXCLUSIVE; y++) {
                    int legacyY = toLegacyY(y);
                    byte block = original[R196UnderworldTerrain.index(x, legacyY, z)];
                    if (block == R196UnderworldTerrain.BEDROCK && y < MIN_ROOF_Y) {
                        block = R196UnderworldTerrain.STONE;
                    }
                    adapted[index(x, y, z)] = block;
                }
            }
        }
        addPortalMantle(adapted, chunkX * 16, chunkZ * 16);
        addFloorSteps(adapted, chunkX, chunkZ);
        return adapted;
    }

    private void addPortalMantle(byte[] blocks, int chunkMinX, int chunkMinZ) {
        double[] roughness = mantleRoughness.sample(chunkMinX, 0, chunkMinZ, 16, 1, 16, .2, 1, .2);
        int size = PORTAL_MANTLE_CELL_SIZE;
        // Bound each patch inside its own cell to keep solid, mineable space between patches.
        // These cells are independent of Minecraft chunks, so a patch can cross a chunk border.
        for (int cellX = Math.floorDiv(chunkMinX, size); cellX <= Math.floorDiv(chunkMinX + 15, size); cellX++) {
            for (int cellZ = Math.floorDiv(chunkMinZ, size); cellZ <= Math.floorDiv(chunkMinZ + 15, size); cellZ++) {
                var random = new SplittableRandom(mantleSeed ^ cellX * 0x9E3779B97F4A7C15L ^ cellZ * 0xC2B2AE3D27D4EB4FL);
                if (random.nextInt(4) == 0) continue;
                int radius = random.nextInt(7, 12);
                int width = random.nextInt(5, 8);
                int inset = radius + 1;
                int centerX = cellX * size + random.nextInt(inset, size - inset) - chunkMinX;
                int centerZ = cellZ * size + random.nextInt(inset, size - inset) - chunkMinZ;
                int baseY = random.nextInt(MIN_PORTAL_MANTLE_Y, MAX_PORTAL_MANTLE_Y + 1);
                double angle = random.nextDouble(2 * Math.PI);
                double cos = StrictMath.cos(angle), sin = StrictMath.sin(angle);

                for (int x = Math.max(0, centerX - radius); x <= Math.min(15, centerX + radius); x++) {
                    for (int z = Math.max(0, centerZ - radius); z <= Math.min(15, centerZ + radius); z++) {
                        int dx = x - centerX, dz = z - centerZ;
                        double along = (dx * cos + dz * sin) / radius;
                        double across = (dz * cos - dx * sin) / width;
                        double distanceSquared = along * along + across * across;
                        double edge = roughness[x * 16 + z];
                        if (distanceSquared > Math.min(1, .76 + .4 * edge)) continue;
                        // Keep a relatively flat center to build on, with height variation towards the edge.
                        int rise = distanceSquared < .32 ? 0 : (int) Math.round(edge * 2);
                        int y = Math.clamp(baseY + rise, MIN_PORTAL_MANTLE_Y, MAX_PORTAL_MANTLE_Y);
                        int index = index(x, y, z);
                        if (blocks[index] == R196UnderworldTerrain.STONE) blocks[index] = R196UnderworldTerrain.MANTLE;
                    }
                }
            }
        }
    }

    private byte[] sourceChunk(int chunkX, int chunkZ) {
        long key = (long) chunkX << 32 | (chunkZ & 0xffffffffL);
        synchronized (sourceChunks) {
            byte[] cached = sourceChunks.get(key);
            if (cached != null) return cached;
        }
        // Noise generation stays outside the cache lock so different chunks can generate in parallel.
        byte[] generated = source.generate(chunkX, chunkZ);
        synchronized (sourceChunks) {
            sourceChunks.put(key, generated);
            if (sourceChunks.size() > MAX_CACHED_SOURCE_CHUNKS) sourceChunks.pollFirstEntry();
        }
        return generated;
    }

    private void addFloorSteps(byte[] blocks, int chunkX, int chunkZ) {
        byte[][] neighbors = new byte[9][];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                neighbors[(dx + 1) * 3 + dz + 1] = sourceChunk(chunkX + dx, chunkZ + dz);
            }
        }
        byte[] cavern = new byte[PADDED_WIDTH * PADDED_WIDTH * CAVERN_HEIGHT];
        boolean[] floors = new boolean[cavern.length];
        for (int x = 0; x < PADDED_WIDTH; x++) {
            for (int z = 0; z < PADDED_WIDTH; z++) {
                int localX = x - STEP_RADIUS, localZ = z - STEP_RADIUS;
                byte[] original = neighbors[(Math.floorDiv(localX, 16) + 1) * 3 + Math.floorDiv(localZ, 16) + 1];
                for (int y = CAVERN_OFFSET; y < CAVERN_TOP_EXCLUSIVE; y++) {
                    cavern[cavernIndex(x, y, z)] = original[R196UnderworldTerrain.index(
                            Math.floorMod(localX, 16), toLegacyY(y), Math.floorMod(localZ, 16))];
                }
                for (int y = CAVERN_OFFSET; y < CAVERN_TOP_EXCLUSIVE - HEADROOM; y++) {
                    int index = cavernIndex(x, y, z);
                    floors[index] = cavern[index] == R196UnderworldTerrain.STONE
                            && airAbove(cavern, index, HEADROOM);
                }
            }
        }
        // All decisions use the same unmodified snapshot, including the neighboring chunk halo.
        // Short ledges gain a stepped apron wherever existing walking routes permit it.
        for (int x = STEP_RADIUS; x < STEP_RADIUS + 16; x++) {
            for (int z = STEP_RADIUS; z < STEP_RADIUS + 16; z++) {
                for (int y = CAVERN_OFFSET; y < CAVERN_TOP_EXCLUSIVE - HEADROOM; y++) {
                    int index = cavernIndex(x, y, z);
                    if (!floors[index]) continue;
                    int raise = stepHeight(cavern, floors, x, y, z);
                    if (raise == 0 || y + raise + HEADROOM >= CAVERN_TOP_EXCLUSIVE
                            || !airAbove(cavern, index, raise + HEADROOM)) continue;
                    for (int dy = 1; dy <= raise; dy++) {
                        blocks[index(x - STEP_RADIUS, y + dy, z - STEP_RADIUS)] = R196UnderworldTerrain.STONE;
                    }
                }
            }
        }
    }

    private static int stepHeight(byte[] cavern, boolean[] floors, int x, int y, int z) {
        int raise = 0;
        for (int dx = -STEP_RADIUS; dx <= STEP_RADIUS; dx++) {
            for (int dz = -STEP_RADIUS; dz <= STEP_RADIUS; dz++) {
                int distance = Math.abs(dx) + Math.abs(dz);
                if (distance == 0 || distance > STEP_RADIUS) continue;
                int neighbor = cavernIndex(x + dx, y, z + dz);
                for (int rise = distance + 1; rise <= MAX_STEP_RISE
                        && y + rise + HEADROOM < CAVERN_TOP_EXCLUSIVE; rise++) {
                    if (!floors[neighbor + rise]) continue;
                    boolean solidWall = true;
                    for (int dy = 0; dy < rise; dy++) {
                        if (cavern[neighbor + dy] != R196UnderworldTerrain.STONE) solidWall = false;
                    }
                    if (solidWall) raise = Math.max(raise, rise - distance);
                }
            }
        }
        // Keep every old two-block-high passage connected, including one-block downhill steps.
        // Using the original neighbor floor as the bound makes the constraint order-independent.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    int neighborY = y + dy;
                    if (neighborY < CAVERN_OFFSET || neighborY + 2 >= CAVERN_TOP_EXCLUSIVE) continue;
                    int neighbor = cavernIndex(x + dx, neighborY, z + dz);
                    if (cavern[neighbor] == R196UnderworldTerrain.STONE && airAbove(cavern, neighbor, 2)) {
                        raise = Math.min(raise, dy + 1);
                    }
                }
            }
        }
        return raise;
    }

    private static boolean airAbove(byte[] cavern, int floor, int height) {
        for (int dy = 1; dy <= height; dy++) {
            if (cavern[floor + dy] != R196UnderworldTerrain.AIR) return false;
        }
        return true;
    }

    private static int cavernIndex(int x, int y, int z) {
        return (x * PADDED_WIDTH + z) * CAVERN_HEIGHT + y - CAVERN_OFFSET;
    }
}
