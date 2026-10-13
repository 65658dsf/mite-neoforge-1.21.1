/* Read-only R196 terrain bytecode oracle. Developer: NingMeng123. */
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Random;
import sun.misc.Unsafe;

/**
 * Executes only R196 terrain/noise and chunk-array conversion, never world/server constructors.
 * A constructor-free carrier supplies the six scalar world properties needed by Chunk.
 */
public final class UnderworldTerrainExport {
    private static void set(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    @SuppressWarnings("removal")
    public static void main(String[] arguments) throws Exception {
        Path root = Path.of(arguments[0]).toAbsolutePath().normalize();
        RegistryExport.ReadOnlyGuard guard = new RegistryExport.ReadOnlyGuard(List.of(root, Path.of(System.getProperty("java.home"))));
        guard.checkPermission(new java.io.FilePermission(root.resolve("tools/reference/UnderworldTerrainExport.java").toString(), "read"));
        System.setSecurityManager(guard);
        Class.forName("aqz");
        Class.forName("yc");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe allocator = (Unsafe) unsafeField.get(null);
        long[] seeds = {0L, 1L, 123456789L, -987654321L, Long.MAX_VALUE};
        int[][] chunks = {{0, 0}, {1, -2}, {-7, 11}, {1024, -1024}, {-11, 1}};
        System.out.println("UNDERWORLD_FIXTURE_JSON_BEGIN");
        System.out.println("{\"author\":\"NingMeng123\",\"archive_sha256\":\"fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3\",\"cases\":[");
        boolean comma = false;
        for (long seed : seeds) {
            WorldInfoShared shared = (WorldInfoShared) allocator.allocateInstance(WorldInfoShared.class);
            set(shared, "randomSeed", seed);
            Random hashed = new Random(seed);
            hashed.nextInt();
            set(shared, "random_seed_hashed", hashed.nextLong());
            als info = (als) allocator.allocateInstance(als.class);
            set(info, "shared", shared);
            abw carrier = (abw) allocator.allocateInstance(js.class);
            set(carrier, "x", info);
            set(carrier, "t", new WorldProviderUnderworld());
            set(carrier, "is_underworld", true);
            set(carrier, "has_skylight", false);
            set(carrier, "underworld_y_offset", 120);
            set(carrier, "min_chunk_xz", -32768);
            set(carrier, "max_chunk_xz", 32767);
            RNG.random_numbers_have_been_initialized = false;
            RNG.init(carrier);
            ChunkProviderUnderworld provider = new ChunkProviderUnderworld(carrier, seed);
            for (int[] coordinate : chunks) {
                int cx = coordinate[0], cz = coordinate[1];
                byte[] upper = new byte[32768];
                provider.generateNetherTerrain(cx, cz, upper);
                aet.placeRandomCobwebs(cx, cz, upper, new Random(cx * 341873128712L + cz * 132897987541L));
                adr chunk = new adr(carrier, upper, cx, cz);
                byte[] canonical = new byte[65536];
                int[] counts = new int[256];
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 256; y++) {
                    int block = chunk.getBlockIDOptimized(x | z << 4, y);
                    byte code = block == aqz.mantleOrCore.cF ? (byte) -1 : (byte) block;
                    canonical[(x * 16 + z) * 256 + y] = code;
                    counts[code & 255]++;
                }
                String digest = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
                if (comma) System.out.println(",");
                comma = true;
                System.out.print("{\"seed\":\"" + seed + "\",\"chunk_x\":" + cx + ",\"chunk_z\":" + cz
                        + ",\"sha256\":\"" + digest + "\",\"counts\":{\"air\":" + counts[0]
                        + ",\"stone\":" + counts[1] + ",\"bedrock\":" + counts[7] + ",\"water\":" + counts[9]
                        + ",\"cobweb\":" + counts[30] + ",\"mantle\":" + counts[255] + "},\"mycelium_posts\":[");
                boolean postComma = false;
                for (Object entry : new ChunkPostField(1, carrier.getHashedSeed(), 24, .0625f).getNearbyPosts(cx, cz)) {
                    ChunkPost post = (ChunkPost) entry;
                    if (postComma) System.out.print(",");
                    postComma = true;
                    System.out.print("{\"x\":" + post.getNonLocalBlockPosX() + ",\"z\":" + post.getNonLocalBlockPosZ()
                            + ",\"seed\":\"" + post.getSeed() + "\"}");
                }
                System.out.print("]}");
            }
        }
        System.out.println("]}\nUNDERWORLD_FIXTURE_JSON_END");
    }
}
