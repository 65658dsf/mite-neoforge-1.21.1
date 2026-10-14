import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Pure R196 block dissolution methods, protected by the existing read-only guard. @author NingMeng123 */
public final class SphereCorrosionExport {
    @SuppressWarnings("removal")
    public static void main(String[] args) throws Exception {
        Path workspace = Path.of(args[0]).toAbsolutePath().normalize();
        var guard = new RegistryExport.ReadOnlyGuard(List.of(workspace, Path.of(System.getProperty("java.home")).toAbsolutePath().normalize()));
        Class.forName("RegistryExport");
        Class.forName("RegistryExport$GuardProbe");
        guard.checkPermission(new java.io.FilePermission(workspace.toString(), "read"));
        System.setSecurityManager(guard);
        var checks = RegistryExport.guardTests(workspace);
        Class<?> blocks = Class.forName("aqz");
        Class.forName("yc");
        Class<?> damages = Class.forName("nb");
        Object pepsin = RegistryExport.field(damages, "pepsin"), acid = RegistryExport.field(damages, "acid");
        Method period = blocks.getMethod("getDissolvePeriod", int.class, damages);
        var blockName = blocks.getDeclaredField("b"); blockName.setAccessible(true);
        List<Object> rows = new ArrayList<>();
        Object registry = RegistryExport.field(blocks, "s");
        for (int i = 0; i < Array.getLength(registry); i++) {
            Object block = Array.get(registry, i);
            if (block == null) continue;
            // aqz.getDissolvePeriod(world, ...) explicitly passes metadata zero for every source block.
            rows.add(RegistryExport.map("legacy_id", i, "class", block.getClass().getName(),
                    "name", blockName.get(block), "pepsin", period.invoke(block, 0, pepsin),
                    "acid", period.invoke(block, 0, acid)));
        }
        System.out.println("SPHERE_CORROSION_BEGIN");
        System.out.println(RegistryExport.json(RegistryExport.map("author", "NingMeng123", "guard_checks_passed", checks,
                "game_main_invoked", false, "world_created", false, "rows", rows)));
        System.out.println("SPHERE_CORROSION_END");
    }
}
