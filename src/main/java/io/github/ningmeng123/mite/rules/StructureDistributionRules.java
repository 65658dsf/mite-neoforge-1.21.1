package io.github.ningmeng123.mite.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** R196 candidate coordinates; terrain and actual structure pieces stay modern. @author NingMeng123 */
public final class StructureDistributionRules {
    public static final int SPACING = 40;
    public static final int SEPARATION = 20;
    public static final int VILLAGE_SALT = 10387312;
    public static final int SCATTERED_SALT = 14357617;
    public static final float MINESHAFT_FREQUENCY = .005F;
    public static final int MINESHAFT_DISTANCE_BOUND = 80;
    public static final int STRONGHOLD_COUNT = 3;
    public static final int STRONGHOLD_DISTANCE = 512;
    public static final int STRONGHOLD_BIOME_RADIUS = 112;

    public record ChunkPosition(int x, int z) {}
    public record QuartPosition(int x, int z) {}

    private StructureDistributionRules() {}

    /** aer's two world-seeded multipliers, then ain's discarded nextInt, precede agg's candidate draws. */
    public static boolean mineshaftCandidate(long worldSeed, int chunkX, int chunkZ, boolean hillyBiome) {
        Random random = new Random(worldSeed);
        long xMultiplier = random.nextLong(), zMultiplier = random.nextLong();
        random.setSeed((long)chunkX * xMultiplier ^ (long)chunkZ * zMultiplier ^ worldSeed);
        random.nextInt();
        if (random.nextFloat() >= MINESHAFT_FREQUENCY || !hillyBiome) return false;
        return mineshaftDistanceAllows(chunkX, chunkZ, random.nextInt(MINESHAFT_DISTANCE_BOUND));
    }

    /** This test follows the already-selected 0.5% and biome candidates; it never rolls that probability again. */
    public static boolean mineshaftDistanceAllows(int chunkX, int chunkZ, int draw) {
        if (draw < 0 || draw >= MINESHAFT_DISTANCE_BOUND) throw new IllegalArgumentException("Expected nextInt(80)");
        return draw < Math.max(Math.abs(chunkX), Math.abs(chunkZ));
    }

    /** aiw/ahh use floor division for negative regions and the same vanilla legacy seed arithmetic. */
    public static ChunkPosition randomSpreadCandidate(long seed, int chunkX, int chunkZ, int salt) {
        int regionX = Math.floorDiv(chunkX, SPACING), regionZ = Math.floorDiv(chunkZ, SPACING);
        Random random = new Random((long)regionX * 341873128712L + (long)regionZ * 132897987541L + seed + salt);
        return new ChunkPosition(regionX * SPACING + random.nextInt(SPACING - SEPARATION),
                regionZ * SPACING + random.nextInt(SPACING - SEPARATION));
    }

    public static List<ChunkPosition> strongholdCandidates(long seed) {
        Random random = new Random(seed);
        return strongholdCandidates(random::nextDouble, UnaryOperator.identity());
    }

    /** Biome relocation consumes the same random stream before the next radius, as ahq.a(int,int) does. */
    public static List<ChunkPosition> strongholdCandidates(DoubleSupplier random, UnaryOperator<ChunkPosition> relocate) {
        Objects.requireNonNull(random);
        Objects.requireNonNull(relocate);
        List<ChunkPosition> positions = new ArrayList<>(STRONGHOLD_COUNT);
        double angle = random.getAsDouble() * Math.PI * 2.0;
        for (int index = 0; index < STRONGHOLD_COUNT; index++) {
            double radius = (1.25 + random.getAsDouble()) * STRONGHOLD_DISTANCE;
            ChunkPosition candidate = new ChunkPosition((int)Math.round(Math.cos(angle) * radius),
                    (int)Math.round(Math.sin(angle) * radius));
            positions.add(Objects.requireNonNull(relocate.apply(candidate)));
            angle += Math.PI * 2 * 1.0 / STRONGHOLD_COUNT;
        }
        return List.copyOf(positions);
    }

    /**
     * acv.a's counter counts successful selections, not all matching cells. Keep this source behavior
     * and its random consumption instead of substituting the modern biome reservoir or random.fork().
     */
    public static ChunkPosition relocateStrongholdBiome(ChunkPosition candidate, Predicate<QuartPosition> preferred,
            IntUnaryOperator random) {
        int centerX = candidate.x() * 16 + 8, centerZ = candidate.z() * 16 + 8;
        int minX = (centerX - STRONGHOLD_BIOME_RADIUS) >> 2;
        int minZ = (centerZ - STRONGHOLD_BIOME_RADIUS) >> 2;
        int maxX = (centerX + STRONGHOLD_BIOME_RADIUS) >> 2;
        int maxZ = (centerZ + STRONGHOLD_BIOME_RADIUS) >> 2;
        QuartPosition selected = null;
        int selections = 0;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                QuartPosition position = new QuartPosition(x, z);
                if (preferred.test(position) && (selected == null || random.applyAsInt(selections + 1) == 0)) {
                    selected = position;
                    selections++;
                }
            }
        }
        return selected == null ? candidate : new ChunkPosition(selected.x() >> 2, selected.z() >> 2);
    }
}
