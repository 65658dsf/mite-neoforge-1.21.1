/* Guarded R196 ore bytecode oracle. Developer: NingMeng123. */
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import sun.misc.Unsafe;

/** Only registry constants, original afq sampling/growth and in-memory block arrays execute. */
public final class OreRulesExport {
    private static Unsafe allocator;
    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    public static final class Carrier extends js {
        private Carrier() { super(null, null, null, 0, null, null, null); }
        @Override public void markWorldMapPixelDirty(int x, int z) { }
        @Override public void j(int x, int y, int z) { }
        @Override public boolean l(int x, int y, int z) { return false; }
    }

    private static Carrier carrier(boolean underworld) throws Exception {
        Carrier world = (Carrier) allocator.allocateInstance(Carrier.class);
        set(world, "is_underworld", underworld);
        set(world, "is_overworld", !underworld);
        set(world, "underworld_y_offset", underworld ? 120 : 0);
        set(world, "min_block_xz", -1024); set(world, "max_block_xz", 1023);
        set(world, "min_chunk_xz", -64); set(world, "max_chunk_xz", 63);
        set(world, "t", new WorldProviderUnderworld());
        set(world, "u", new ArrayList<>());
        jr provider = (jr) allocator.allocateInstance(jr.class);
        set(provider, "f", new lq()); set(provider, "b", new HashSet<>());
        set(provider, "h", world); set(world, "v", provider); set(world, "b", provider);
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) {
            adr chunk = new adr(world, cx, cz);
            Arrays.fill(chunk.f, 256);
            for (int section = 0; section < 16; section++) {
                chunk.r[section] = new ads(section * 16, false);
                Arrays.fill(chunk.r[section].d, (byte) aqz.y.cF);
            }
            provider.f.a(abp.a(cx, cz), chunk);
        }
        return world;
    }

    private static final class BudgetCapture extends afq {
        int budget;
        BudgetCapture(int id, int size) { super(id, size); }
        @Override public int growVein(abw world, Random random, int size, int x, int y, int z,
                                       boolean supported, boolean dirt) {
            budget = size;
            return size;
        }
    }

    @SuppressWarnings("removal")
    public static void main(String[] arguments) throws Exception {
        Path root = Path.of(arguments[0]).toAbsolutePath().normalize();
        RegistryExport.ReadOnlyGuard guard = new RegistryExport.ReadOnlyGuard(List.of(root, Path.of(System.getProperty("java.home"))));
        guard.checkPermission(new java.io.FilePermission(root.resolve("tools/reference/OreRulesExport.java").toString(), "read"));
        System.setSecurityManager(guard);
        Class.forName("aqz"); Class.forName("yc");
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        allocator = (Unsafe) field.get(null);
        String[] names = {"dirt", "gravel", "coal", "copper", "silver", "gold", "iron", "mithril", "adamantium", "infested", "redstone", "diamond", "lapis"};
        int[] ids = {3, 13, 16, 200, 201, 14, 15, 202, 203, 97, 73, 56, 21};
        int[] sizes = {32, 32, 16, 6, 6, 4, 6, 3, 3, 3, 5, 3, 3};
        StringBuilder out = new StringBuilder("{\"author\":\"NingMeng123\",\"height_cases\":[");
        Carrier ow = carrier(false), uw = carrier(true);
        boolean comma = false;
        for (boolean underworld : new boolean[]{false, true}) for (int kind = 0; kind < names.length; kind++) {
            Random random = new Random(0x123456789abcdefL);
            afq generator = new afq(ids[kind], sizes[kind]);
            if (comma) out.append(','); comma = true;
            out.append("{\"kind\":\"").append(names[kind]).append("\",\"dimension\":\"").append(underworld ? "underworld" : "overworld")
                    .append("\",\"random_seed\":\"81985529216486895\",\"heights\":[");
            for (int sample = 0; sample < 32; sample++) {
                if (sample != 0) out.append(',');
                out.append(generator.getRandomVeinHeight(underworld ? uw : ow, random));
            }
            out.append("],\"next_long\":\"").append(random.nextLong()).append("\"}");
        }
        out.append("],\"size_cases\":["); comma = false;
        for (boolean underworld : new boolean[]{false, true}) for (int kind = 0; kind < names.length; kind++) {
            afq base = new afq(ids[kind], sizes[kind]);
            Carrier world = underworld ? uw : ow;
            for (int height : new int[]{base.getMinVeinHeight(world), (base.getMaxVeinHeight(world) + base.getMinVeinHeight(world)) / 2, base.getMaxVeinHeight(world)}) {
                for (boolean depth : new boolean[]{false, true}) {
                    if (comma) out.append(','); comma = true;
                    out.append("{\"kind\":\"").append(names[kind]).append("\",\"dimension\":\"").append(underworld ? "underworld" : "overworld")
                            .append("\",\"height\":").append(height).append(",\"depth\":").append(depth).append(",\"samples\":[");
                    long[] seeds = {0, 1, 2, 3, 4, 42, 4096, 65536, 123456789, -987654321,
                            Long.MAX_VALUE, Long.MIN_VALUE, 81985529216486895L, -1, 20261013, 341873128712L};
                    for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
                        long seed = seeds[seedIndex];
                        Random random = new Random(seed);
                        BudgetCapture generator = new BudgetCapture(ids[kind], sizes[kind]);
                        generator.generate(world, random, 8, height, 8, depth);
                        if (seedIndex != 0) out.append(',');
                        out.append("{\"seed\":\"").append(seed).append("\",\"budget\":").append(generator.budget)
                                .append(",\"next_long\":\"").append(random.nextLong()).append("\"}");
                    }
                    out.append("]}");
                }
            }
        }
        out.append("],\"growth_cases\":["); comma = false;
        for (int budget : new int[]{3, 6, 13, 32}) for (long seed : new long[]{0, 1, 42, 123456789}) {
            Carrier world = carrier(false);
            Random random = new Random(seed);
            afq generator = new afq(aqz.oreMithril.cF, budget);
            int grown = generator.growVein(world, random, budget, 8, 32, 8, false, false);
            if (comma) out.append(','); comma = true;
            out.append("{\"seed\":\"").append(seed).append("\",\"budget\":").append(budget).append(",\"start\":[8,32,8],\"grown\":")
                    .append(grown).append(",\"next_long\":\"").append(random.nextLong()).append("\",\"positions\":[");
            boolean positionComma = false;
            for (int x = -8; x <= 24; x++) for (int y = 16; y <= 48; y++) for (int z = -8; z <= 24; z++) {
                if (world.a(x, y, z) != aqz.oreMithril.cF) continue;
                if (positionComma) out.append(','); positionComma = true;
                out.append('[').append(x).append(',').append(y).append(',').append(z).append(']');
            }
            out.append("]}");
        }
        out.append("]}");
        System.out.println("ORE_FIXTURE_JSON_BEGIN\n" + out + "\nORE_FIXTURE_JSON_END");
    }
}
