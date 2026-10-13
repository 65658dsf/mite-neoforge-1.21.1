package io.github.ningmeng123.mite.worldgen;

/**
 * Explicit 1.21.1 height/accessibility adaptation of the independently verified R196 terrain.
 * The original generator remains unchanged as the archival oracle. Interior bedrock becomes
 * mineable stone so survival players can descend to the mantle instead of crossing sealed strata.
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
    public static final int MIN_ROOF_Y = fromLegacyY(R196UnderworldTerrain.HEIGHT - 3);

    private final R196UnderworldTerrain source;

    public ModernUnderworldTerrain(long seed) {
        source = new R196UnderworldTerrain(seed);
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
        byte[] original = source.generate(chunkX, chunkZ);
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
        return adapted;
    }
}
