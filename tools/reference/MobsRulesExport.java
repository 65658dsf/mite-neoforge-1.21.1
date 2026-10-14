/* Guarded R196 mob constructor and pure-stat oracle. Developer: NingMeng123. */
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;

/** No World, client, server, ticks, spawn callbacks, death callbacks or renderer is created. */
public final class MobsRulesExport {
    private static Object row(Object... values) { return RegistryExport.map(values); }
    private static Method method(Class<?> type, String name, Class<?>... arguments) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, arguments);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    private static Object pure(Object entity, String name) {
        try { return method(entity.getClass(), name).invoke(entity); }
        catch (Exception failure) { return row("unavailable", failure.getCause() == null ? failure.toString() : failure.getCause().toString()); }
    }
    private static Object snapshot(of entity, String variant) throws Exception {
        List<Object> attributes = new ArrayList<>();
        Class<?> shared = Class.forName("tp");
        for (Field field : shared.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !or.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            or attribute = (or) field.get(null);
            if (!entity.hasEntityAttribute(attribute)) continue;
            attributes.add(row("symbol", "tp." + field.getName(), "name", attribute.a(),
                    "base", entity.getEntityAttributeBaseValue(attribute), "effective", entity.getEntityAttributeValue(attribute)));
        }
        Object configuredDimensions = null;
        if (Class.forName("nk").isInstance(entity)) {
            Field adultWidth = Class.forName("nk").getDeclaredField("bp"), adultHeight = Class.forName("nk").getDeclaredField("bq");
            adultWidth.setAccessible(true); adultHeight.setAccessible(true);
            configuredDimensions = row("width", adultWidth.getFloat(entity), "height", adultHeight.getFloat(entity),
                    "source", "vanilla nk.a(float,float) stores the adult shape; age rescaling applies it later");
        }
        return row("variant", variant, "width", entity.O, "height", entity.P,
                "configured_adult_dimensions", configuredDimensions,
                "size_method_owner", method(entity.getClass(), "a", float.class, float.class).getDeclaringClass().getName(),
                "actual_cubic_size", entity instanceof EntityCubic ? ((EntityCubic) entity).getSize() : null,
                "maximum_health", entity.aT(), "attributes", attributes,
                "experience", pure(entity, "getExperienceValue"), "primary_drop_getter_single_sample", pure(entity, "s"),
                "natural_defense_without_damage_context", pure(entity, "getNaturalDefense"),
                "harmed_by_fire", pure(entity, "isHarmedByFire"), "harmed_by_lava", pure(entity, "isHarmedByLava"),
                "resistance_poison", pure(entity, "getResistanceToPoison"), "resistance_paralysis", pure(entity, "getResistanceToParalysis"),
                "resistance_drain", pure(entity, "getResistanceToDrain"), "resistance_shadow", pure(entity, "getResistanceToShadow"));
    }
    @SuppressWarnings("removal")
    public static void main(String[] args) throws Exception {
        Path workspace = Path.of(args[0]).toAbsolutePath().normalize();
        // Initialize the JDK entropy provider before installing the same reference guard.
        UUID.randomUUID();
        Class.forName("RegistryExport$GuardProbe");
        var guard = new RegistryExport.ReadOnlyGuard(List.of(workspace,
                Path.of(System.getProperty("java.home")).toAbsolutePath().normalize()));
        guard.checkPermission(new java.io.FilePermission(workspace.toString(), "read"));
        System.setSecurityManager(guard);
        var checks = RegistryExport.guardTests(workspace);
        Class.forName("aqz"); Class.forName("yc"); aaf.a();
        List<Object> corrosion = new ArrayList<>();
        for (yc item : yc.g) if (item != null) corrosion.add(row("legacy_id", item.cv,
                "pepsin", item.isHarmedByPepsin(), "acid", item.isHarmedByAcid(),
                "fire", item.isHarmedByFire(), "lava", item.isHarmedByLava()));
        List<Object> entities = new ArrayList<>();
        for (Object entry : (List<?>) RegistryExport.field(Class.forName("nt"), "entries")) {
            int id = (Integer) RegistryExport.field(entry, "id");
            Class<?> type = (Class<?>) RegistryExport.field(entry, "_class");
            boolean living = of.class.isAssignableFrom(type);
            List<String> hierarchy = new ArrayList<>();
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) hierarchy.add(parent.getName());
            List<Object> variants = new ArrayList<>();
            String failure = null;
            boolean instantiated = false;
            if (living && !Modifier.isAbstract(type.getModifiers())) {
                try {
                    of entity = (of) type.getConstructor(abw.class).newInstance((Object) null);
                    instantiated = true;
                    if (entity instanceof EntityCubic cubic) {
                        for (int size : new int[]{1, 2, 4}) {
                            method(type, "setSize", int.class).invoke(entity, size);
                            variants.add(snapshot(entity, "size_" + size));
                        }
                    } else if (entity instanceof EntityEarthElemental) {
                        String[] names = entity instanceof EntityClayGolem ? new String[]{"CLAY_NORMAL", "CLAY_HARDENED"}
                                : new String[]{"STONE_NORMAL", "STONE_MAGMA", "OBSIDIAN_NORMAL", "OBSIDIAN_MAGMA", "NETHERRACK_NORMAL", "NETHERRACK_MAGMA", "END_STONE_NORMAL", "END_STONE_MAGMA"};
                        for (String name : names) {
                            method(type, "setType", int.class).invoke(entity, EntityEarthElemental.class.getField(name).getInt(null));
                            variants.add(snapshot(entity, name.toLowerCase(Locale.ROOT)));
                        }
                    } else variants.add(snapshot(entity, "constructor_state_without_world"));
                } catch (Exception error) {
                    failure = error.getCause() == null ? error.toString() : error.getCause().toString();
                }
            }
            entities.add(row("legacy_id", id, "registered_name", RegistryExport.field(entry, "name"),
                    "source_class", type.getName(), "hierarchy", hierarchy, "living", living,
                    "abstract", Modifier.isAbstract(type.getModifiers()), "entity_instantiated_with_null_world", instantiated,
                    "constructor_error", failure, "variants", variants));
        }
        System.out.println("MOBS_ORACLE_BEGIN");
        System.out.println(RegistryExport.json(row("author", "NingMeng123", "java_version", System.getProperty("java.version"),
                "guard_checks_passed", checks, "game_main_invoked", false, "world_created", false,
                "spawn_or_tick_or_death_callbacks_invoked", false, "entities", entities, "items_corrosion", corrosion)));
        System.out.println("MOBS_ORACLE_END");
    }
}
