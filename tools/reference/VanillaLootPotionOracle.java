/* Guarded pure R196 potion lookup for source witch death rewards. Author: NingMeng123. */
import java.nio.file.Path;
import java.util.*;
public final class VanillaLootPotionOracle {
    @SuppressWarnings("removal") public static void main(String[] args) throws Exception {
        Path workspace=Path.of(args[0]).toAbsolutePath().normalize(); UUID.randomUUID(); Class.forName("RegistryExport$GuardProbe");
        var guard=new RegistryExport.ReadOnlyGuard(List.of(workspace,Path.of(System.getProperty("java.home")).toAbsolutePath().normalize()));
        guard.checkPermission(new java.io.FilePermission(workspace.toString(),"read"));
        System.setSecurityManager(guard); var checks=RegistryExport.guardTests(workspace);
        Class.forName("aqz"); Class.forName("yc"); aaf.a();
        List<Object> rows=new ArrayList<>();
        for(int subtype:new int[]{8227,8261,16388,16424,16426,16460}) {
            List<Object> effects=new ArrayList<>();
            for(Object value:yc.bu.c(subtype)) { nj effect=(nj)value; effects.add(RegistryExport.map("id",effect.a(),"duration",effect.b(),"amplifier",effect.c())); }
            rows.add(RegistryExport.map("subtype",subtype,"splash",yp.f(subtype),"effects",effects));
        }
        System.out.println("VANILLA_LOOT_POTIONS_BEGIN");
        System.out.println(RegistryExport.json(RegistryExport.map("author","NingMeng123","guard_checks_passed",checks,"world_created",false,"potions",rows)));
        System.out.println("VANILLA_LOOT_POTIONS_END");
    }
}
