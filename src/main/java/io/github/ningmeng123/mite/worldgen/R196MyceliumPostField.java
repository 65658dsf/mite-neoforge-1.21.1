package io.github.ningmeng123.mite.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure immutable alternative to R196's shared, mutable ChunkPostField cache. @author NingMeng123 */
public final class R196MyceliumPostField {
    private static final long HASHED_TYPE;
    static {
        Random random = new Random(1);
        random.nextInt();
        HASHED_TYPE = random.nextLong();
    }
    public record Post(int x, int z, long seed) {}
    private R196MyceliumPostField() {}

    public static List<Post> nearbyPosts(int chunkX, int chunkZ, long hashedSeed) {
        List<Post> posts = new ArrayList<>();
        Random random = new Random();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int cx = chunkX + dx, cz = chunkZ + dz;
                long seed = R196UnderworldTerrain.chunkHash(cx, cz) * HASHED_TYPE * hashedSeed;
                random.setSeed(seed);
                random.nextInt();
                if (random.nextFloat() < .0625f) posts.add(new Post(cx * 16 + random.nextInt(16),
                        cz * 16 + random.nextInt(16), seed));
            }
        }
        return List.copyOf(posts);
    }
}
