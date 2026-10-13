package io.github.ningmeng123.mite.worldgen;

import java.util.Random;

/** Pure, thread-safe terrain rules extracted from ChunkProviderUnderworld, Chunk and RNG. @author NingMeng123 */
public final class R196UnderworldTerrain {
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 256;
    public static final int CAVERN_OFFSET = 120;
    public static final int SEA_LEVEL = 144;
    public static final byte AIR = 0, STONE = 1, BEDROCK = 7, WATER = 9, COBWEB = 30, MANTLE = -1;
    private final long hashedSeed;
    private final R196OctaveNoise lower;
    private final R196OctaveNoise upper;
    private final R196OctaveNoise blend;
    private final R196OctaveNoise[] strata = new R196OctaveNoise[5];
    private final R196OctaveNoise[] bumps = new R196OctaveNoise[6];
    private final int[] randomInts = new int[32768];

    public R196UnderworldTerrain(long seed) {
        Random random = new Random(seed);
        random.nextInt();
        hashedSeed = random.nextLong();
        random.setSeed(seed);
        lower = new R196OctaveNoise(random, 16);
        upper = new R196OctaveNoise(random, 16);
        blend = new R196OctaveNoise(random, 8);
        // The original allocates these unused fields before bedrock noise. Keep their RNG consumption.
        new R196OctaveNoise(random, 4);
        new R196OctaveNoise(random, 4);
        new R196OctaveNoise(random, 10);
        new R196OctaveNoise(random, 16);
        for (int i = 0; i < strata.length; i++) strata[i] = new R196OctaveNoise(random, 4);
        for (int i = 0; i < bumps.length; i++) bumps[i] = new R196OctaveNoise(random, 4);
        random.setSeed(seed);
        for (int i = 0; i < randomInts.length; i++) {
            randomInts[i] = random.nextInt() & Integer.MAX_VALUE;
            random.nextInt();
            random.nextFloat();
            random.nextFloat();
            random.nextDouble();
        }
    }

    public static int index(int x, int y, int z) { return (x * 16 + z) * HEIGHT + y; }
    private static int cavernIndex(int x, int y, int z) { return (x * 16 + z) * 128 + y; }
    public static int chunkHash(int x, int z) { return (17 * 31 + x) * 31 + z; }
    public long hashedSeed() { return hashedSeed; }

    public byte[] generate(int chunkX, int chunkZ) {
        byte[] cavern = cavern(chunkX, chunkZ);
        placeCobwebs(chunkX, chunkZ, cavern);
        byte[] blocks = new byte[16 * 16 * HEIGHT];
        double[][] beds = new double[strata.length][];
        double[][] rises = new double[bumps.length][];
        for (int i = 0; i < beds.length; i++) {
            double scale = i < 2 ? .03125 : .0625;
            beds[i] = strata[i].sample(chunkX * 16, 0, chunkZ * 16, 16, 1, 16, scale, .0625, scale);
        }
        for (int i = 0; i < rises.length; i++) {
            double scale = i == 0 ? .125 : i == 1 ? .25 : i == 2 ? .5 : 1;
            rises[i] = bumps[i].sample(chunkX * 16, 0, chunkZ * 16, 16, 1, 16, scale, .0625, scale);
        }
        Random random = new Random(hashedSeed * chunkHash(chunkX, chunkZ));
        int randomIndex = chunkX * 2653 + chunkZ * 6714631;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int boundaryThickness = random.nextInt(3) + 1;
                int column = z + x * 16;
                for (int y = 0; y < HEIGHT; y++) {
                    byte block;
                    if (y >= CAVERN_OFFSET && y < CAVERN_OFFSET + 128) {
                        block = cavern[cavernIndex(x, y - CAVERN_OFFSET, z)];
                    } else if (y < boundaryThickness) {
                        block = MANTLE;
                    } else if (y > 255 - boundaryThickness) {
                        block = BEDROCK;
                    } else {
                        block = STONE;
                        double bed = Math.max(beds[0][column], beds[1][column]);
                        bed += Math.max(0, rises[0][column]) * .25;
                        bed += Math.max(0, rises[1][column]) * .125;
                        bed += Math.max(0, rises[2][column]) * .125;
                        if (rises[5][column] > 0) bed += rises[5][column] * .09375 + .125;
                        if (bed > 0 && y - 3 <= bed * 7) block = BEDROCK;
                        if (block != BEDROCK) {
                            int distance = y - 32;
                            bed = beds[2][column] - bed * 1.5;
                            if (bed > 0) {
                                if (distance > 0 && rises[3][column] > 0) bed += rises[3][column] * .25 + .25;
                                if (distance < 0) {
                                    if ((randomInts[++randomIndex & 32767] & 1) == 0) distance++;
                                    distance = -distance;
                                }
                                if (distance <= bed * 2) block = BEDROCK;
                            }
                        }
                        if (block != BEDROCK) {
                            int distance = y - 72;
                            bed = beds[3][column] - beds[4][column] * .375 + .5;
                            if (bed > 0) {
                                if (distance > 0 && rises[4][column] > 0) bed += rises[4][column] * .25 + .25;
                                if (distance < 0) {
                                    if ((randomInts[++randomIndex & 32767] & 1) == 0) distance++;
                                    distance = -distance;
                                }
                                if (distance <= bed * 2) block = BEDROCK;
                            }
                        }
                        if (block != BEDROCK) {
                            int distance = y - 96;
                            bed = beds[4][column] - beds[3][column] * .375 + .5;
                            if (bed > 0) {
                                if (distance > 0 && rises[5][column] > 0) bed += rises[5][column] * .25 + .25;
                                if (distance < 0) {
                                    if ((randomInts[++randomIndex & 32767] & 1) == 0) distance++;
                                    distance = -distance;
                                }
                                if (distance <= bed * 2) block = BEDROCK;
                            }
                        }
                    }
                    blocks[index(x, y, z)] = block;
                }
            }
        }
        return blocks;
    }

    private byte[] cavern(int chunkX, int chunkZ) {
        double[] a = lower.sample(chunkX * 4, 0, chunkZ * 4, 5, 17, 5, 684.412, 2053.236, 684.412);
        double[] b = upper.sample(chunkX * 4, 0, chunkZ * 4, 5, 17, 5, 684.412, 2053.236, 684.412);
        double[] weights = blend.sample(chunkX * 4, 0, chunkZ * 4, 5, 17, 5,
                684.412 / 80, 2053.236 / 60, 684.412 / 80);
        double[] density = new double[a.length];
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = 0; y < 17; y++) {
                    int i = (x * 5 + z) * 17 + y;
                    double bias = Math.cos(y * Math.PI * 6 / 17) * 2;
                    int edge = y > 17 / 2 ? 16 - y : y;
                    if (edge < 4) bias -= Math.pow(4 - edge, 3) * 10;
                    double weight = (weights[i] / 10 + 1) / 2;
                    density[i] = (weight < 0 ? a[i] / 512 : weight > 1 ? b[i] / 512
                            : a[i] / 512 + (b[i] / 512 - a[i] / 512) * weight) - bias;
                    if (y > 13) {
                        double ceilingBlend = (float) (y - 13) / 3.0f;
                        density[i] = density[i] * (1 - ceilingBlend) - 10 * ceilingBlend;
                    }
                }
            }
        }
        byte[] blocks = new byte[32768];
        for (int cellX = 0; cellX < 4; cellX++) {
            for (int cellZ = 0; cellZ < 4; cellZ++) {
                for (int cellY = 0; cellY < 16; cellY++) {
                    double d00 = density[(cellX * 5 + cellZ) * 17 + cellY];
                    double d01 = density[(cellX * 5 + cellZ + 1) * 17 + cellY];
                    double d10 = density[((cellX + 1) * 5 + cellZ) * 17 + cellY];
                    double d11 = density[((cellX + 1) * 5 + cellZ + 1) * 17 + cellY];
                    double dy00 = (density[(cellX * 5 + cellZ) * 17 + cellY + 1] - d00) * .125;
                    double dy01 = (density[(cellX * 5 + cellZ + 1) * 17 + cellY + 1] - d01) * .125;
                    double dy10 = (density[((cellX + 1) * 5 + cellZ) * 17 + cellY + 1] - d10) * .125;
                    double dy11 = (density[((cellX + 1) * 5 + cellZ + 1) * 17 + cellY + 1] - d11) * .125;
                    for (int ry = 0; ry < 8; ry++) {
                        double near = d00, far = d01;
                        double dxNear = (d10 - d00) * .25, dxFar = (d11 - d01) * .25;
                        for (int rx = 0; rx < 4; rx++) {
                            double value = near, dz = (far - near) * .25;
                            for (int rz = 0; rz < 4; rz++) {
                                int y = cellY * 8 + ry;
                                blocks[cavernIndex(cellX * 4 + rx, y, cellZ * 4 + rz)] = value > 0 ? STONE : y < 24 ? WATER : AIR;
                                value += dz;
                            }
                            near += dxNear;
                            far += dxFar;
                        }
                        d00 += dy00;
                        d01 += dy01;
                        d10 += dy10;
                        d11 += dy11;
                    }
                }
            }
        }
        return blocks;
    }

    private void placeCobwebs(int chunkX, int chunkZ, byte[] blocks) {
        Random random = new Random(chunkX * 341873128712L + chunkZ * 132897987541L);
        int randomIndex = random.nextInt();
        for (int attempt = 0; attempt < 128; attempt++) {
            int x = randomInts[++randomIndex & 32767] % 14 + 1;
            int y = randomInts[++randomIndex & 32767] % 126 + 1;
            int z = randomInts[++randomIndex & 32767] % 14 + 1;
            // R196's cobweb helper orders its local X/Z opposite to the terrain cache.
            int i = (z * 16 + x) * 128 + y;
            if (blocks[i] != AIR) continue;
            int adjacent = 0;
            boolean stone = false;
            for (int offset : new int[]{1, -1, 128, -128, 2048, -2048}) {
                if (blocks[i + offset] != AIR) adjacent++;
                if (blocks[i + offset] == STONE) stone = true;
            }
            if (adjacent >= 4 && stone) {
                blocks[i] = COBWEB;
                attempt -= 512;
            }
        }
    }
}
