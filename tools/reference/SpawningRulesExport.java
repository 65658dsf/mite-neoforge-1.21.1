/* Guarded R196 biome and spawn-surface oracle. Developer: NingMeng123. */
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Registration, inert spawn tables and pure block predicates only; never constructs a living entity or world. */
public final class SpawningRulesExport {
    private static Object row(Object... values) { return RegistryExport.map(values); }

    private static List<Object> pool(List<?> entries) throws Exception {
        List<Object> rows = new ArrayList<>();
        for (Object entry : entries) rows.add(row("entity_class", ((Class<?>) RegistryExport.field(entry, "b")).getName(),
                "weight", RegistryExport.field(entry, "a"), "minimum_group", RegistryExport.field(entry, "c"),
                "maximum_group", RegistryExport.field(entry, "d")));
        return rows;
    }

    private static String declaration(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
            try { return parent.getDeclaredMethod(name, parameters).getDeclaringClass().getName(); }
            catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    @SuppressWarnings("removal")
    public static void main(String[] args) throws Exception {
        Path workspace = Path.of(args[0]).toAbsolutePath().normalize();
        var guard = new RegistryExport.ReadOnlyGuard(List.of(workspace,
                Path.of(System.getProperty("java.home")).toAbsolutePath().normalize()));
        Class.forName("RegistryExport$GuardProbe");
        guard.checkPermission(new java.io.FilePermission(workspace.toString(), "read"));
        System.setSecurityManager(guard);
        var checks = RegistryExport.guardTests(workspace);
        Class.forName("aqz"); Class.forName("yc"); aaf.a();
        List<Object> categories = new ArrayList<>();
        for (oh category : oh.values()) categories.add(row("name", category.name(), "base_class", category.a().getName(),
                "cap", category.b(), "spawn_material", RegistryExport.field(category.c(), "name")));
        List<Object> biomes = new ArrayList<>();
        for (acq biome : acq.a) if (biome != null) {
            List<Object> lists = new ArrayList<>();
            for (oh category : oh.values()) lists.add(row("category", category.name(), "entries", pool(biome.a(category))));
            biomes.add(row("legacy_id", biome.N, "class", biome.getClass().getName(), "name", biome.y,
                    "temperature", biome.F, "rainfall", biome.G, "freezing", biome.isFreezing(),
                    "desert", biome.isDesertBiome(), "jungle", biome.isJungleBiome(), "swamp", biome.isSwampBiome(),
                    "initial_spawn_probability", biome.f(), "pools", lists));
        }
        List<Object> grounds = new ArrayList<>();
        for (aqz block : aqz.s) if (block != null) {
            List<Object> metadata = new ArrayList<>();
            for (int subtype = 0; subtype < 16; subtype++) if (block.isValidMetadata(subtype)) {
                List<Object> decisions = new ArrayList<>();
                for (oh category : oh.values()) {
                    try {
                        decisions.add(row("category", category.name(), "natural", aci.canCreatureTypeSpawnOn(category, block, subtype, false),
                                "initial", aci.canCreatureTypeSpawnOn(category, block, subtype, true)));
                    } catch (RuntimeException error) {
                        decisions.add(row("category", category.name(), "natural", null, "initial", null,
                                "exception", error.getClass().getName(), "message", error.getMessage()));
                    }
                }
                metadata.add(row("metadata", subtype, "decisions", decisions));
            }
            grounds.add(row("legacy_id", block.cF, "class", block.getClass().getName(), "metadata", metadata));
        }
        List<Object> entities = new ArrayList<>();
        for (Object entry : (List<?>) RegistryExport.field(nt.class, "entries")) {
            Class<?> type = (Class<?>) RegistryExport.field(entry, "_class");
            if (!og.class.isAssignableFrom(type)) continue;
            String category = rn.class.isAssignableFrom(type) ? oh.c.name() : se.class.isAssignableFrom(type) ? oh.aquatic.name()
                    : th.class.isAssignableFrom(type) ? oh.a.name() : rp.class.isAssignableFrom(type) ? oh.animal.name() : null;
            entities.add(row("legacy_id", RegistryExport.field(entry, "id"), "class", type.getName(),
                    "registered_name", RegistryExport.field(entry, "name"), "category", category,
                    "spawn_check_declared_by", declaration(type, "getCanSpawnHere", boolean.class),
                    "mob_light_check_declared_by", declaration(type, "i_"),
                    "special_light_check_declared_by", declaration(type, "isValidLightLevel"),
                    "maximum_pack_declared_by", declaration(type, "bv"), "entity_constructed", false));
        }
        List<Object> structurePools = new ArrayList<>();
        structurePools.add(row("provider_class", "agn", "structure", "Fortress", "entries", pool(new agn().b())));
        structurePools.add(row("provider_class", "ahh", "structure", "Temple", "entries", pool(new ahh().b())));
        System.out.println("SPAWNING_ORACLE_BEGIN");
        System.out.println(RegistryExport.json(row("author", "NingMeng123", "java_version", System.getProperty("java.version"),
                "guard_checks_passed", checks, "game_main_invoked", false, "world_created", false, "entity_constructed", false,
                "activation_flags", row("professions_suppressed", RegistryExport.field(atv.class, "professions_suppressed"),
                        "in_dev_mode", RegistryExport.field(atv.class, "in_dev_mode"),
                        "spawning_disabled", RegistryExport.field(aa.class, "spawning_disabled"),
                        "tournament_type", RegistryExport.scalar(RegistryExport.field(Class.forName("is"), "tournament_type"))),
                "categories", categories, "biomes", biomes, "grounds", grounds, "living_entities", entities, "structure_pools", structurePools)));
        System.out.println("SPAWNING_ORACLE_END");
    }
}
