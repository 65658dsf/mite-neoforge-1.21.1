/* Guarded reference-data exporter. Developer: NingMeng123. */
import java.io.FilePermission;
import java.lang.reflect.*;
import java.net.SocketPermission;
import java.nio.file.*;
import java.security.Permission;
import java.util.*;

/** Runs registration only; never creates a Minecraft client, server, or world. */
public final class RegistryExport {
    @SuppressWarnings("removal")
    static final class ReadOnlyGuard extends SecurityManager {
        private final List<Path> allowedRoots;
        ReadOnlyGuard(List<Path> roots) { allowedRoots = roots; }
        @Override public void checkPermission(Permission permission) {
            if (permission instanceof FilePermission) {
                String actions = permission.getActions();
                if (actions.contains("write") || actions.contains("delete") || actions.contains("execute"))
                    throw new SecurityException("Reference export denied file mutation: " + permission);
                if (actions.contains("read")) {
                    Path path = Paths.get(permission.getName()).toAbsolutePath().normalize();
                    if (allowedRoots.stream().noneMatch(path::startsWith))
                        throw new SecurityException("Reference export denied unrelated file read: " + path);
                }
            }
            if (permission instanceof SocketPermission)
                throw new SecurityException("Reference export denied network: " + permission);
            if (permission instanceof RuntimePermission) {
                String name = permission.getName();
                if (name.startsWith("exitVM") || name.startsWith("loadLibrary.") ||
                    Set.of("setSecurityManager", "createClassLoader", "setIO", "modifyThread", "modifyThreadGroup", "shutdownHooks").contains(name))
                    throw new SecurityException("Reference export denied runtime side effect: " + name);
            }
        }
        @Override public void checkExec(String command) { throw new SecurityException("Reference export denied process execution"); }
        @Override public void checkExit(int status) { throw new SecurityException("Reference export denied System.exit"); }
        @Override public void checkLink(String library) { throw new SecurityException("Reference export denied native library: " + library); }
        @Override public void checkAccess(Thread thread) {
            if (thread != Thread.currentThread()) throw new SecurityException("Reference export denied thread access");
        }
        @Override public void checkAccess(ThreadGroup group) { throw new SecurityException("Reference export denied thread creation"); }
    }

    static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object instanceof Class<?> ? (Class<?>) object : object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object instanceof Class<?> ? null : object);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    static Object invoke(Object object, String name) throws Exception {
        Class<?> type = object instanceof Class<?> ? (Class<?>) object : object.getClass();
        Method method = type.getMethod(name);
        method.setAccessible(true);
        return method.invoke(object instanceof Class<?> ? null : object);
    }
    static Map<String,Object> map(Object... pairs) {
        Map<String,Object> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) map.put((String)pairs[index], pairs[index+1]);
        return map;
    }
    static Object scalar(Object value) {
        if (value instanceof Enum<?>) return ((Enum<?>)value).name();
        if (value instanceof Character) return value.toString();
        return value;
    }
    static boolean scalarType(Class<?> type) { return type.isPrimitive() || type == String.class || type.isEnum(); }
    static Map<String,Object> scalars(Object value) throws Exception {
        Map<String,Object> values = new TreeMap<>();
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            if (type.getPackageName().startsWith("java.")) break;
            for (Field member : type.getDeclaredFields()) {
                if (Modifier.isStatic(member.getModifiers()) || !scalarType(member.getType())) continue;
                member.setAccessible(true);
                values.put(type.getName() + "." + member.getName(), scalar(member.get(value)));
            }
        }
        return values;
    }
    static Object material(Object value) throws Exception {
        return value == null ? null : map("name", field(value, "name"), "scalars", scalars(value));
    }
    static Object stack(Object value) throws Exception {
        if (value == null) return null;
        return map("item_id", field(value, "d"), "count", field(value, "b"), "subtype", field(value, "subtype"),
                   "damage", field(value, "damage"), "quality", scalar(field(value, "quality")),
                   "has_nbt", field(value, "e") != null);
    }
    static Object stackArray(Object value) throws Exception {
        if (value == null) return null;
        List<Object> entries = new ArrayList<>();
        for (int index = 0; index < Array.getLength(value); ++index) entries.add(stack(Array.get(value,index)));
        return entries;
    }
    static List<Object> registry(Class<?> base, String arrayField, String idField) throws Exception {
        Object all = field(base, arrayField);
        IdentityHashMap<Object,List<String>> symbols = new IdentityHashMap<>();
        for (Field declaration : base.getDeclaredFields()) {
            if (!Modifier.isStatic(declaration.getModifiers()) || !base.isAssignableFrom(declaration.getType())) continue;
            declaration.setAccessible(true);
            Object value = declaration.get(null);
            if (value != null) symbols.computeIfAbsent(value, ignored -> new ArrayList<>()).add(base.getName()+"."+declaration.getName());
        }
        List<Object> values = new ArrayList<>();
        for (int index = 0; index < Array.getLength(all); ++index) {
            Object value = Array.get(all,index);
            if (value == null) continue;
            Map<String,Object> entry = map("id", idField == null ? index : field(value,idField), "class", value.getClass().getName(),
                                          "symbols", symbols.getOrDefault(value,Collections.emptyList()), "scalars", scalars(value));
            if (base.getName().equals("yc")) {
                List<Object> materials = new ArrayList<>();
                for (Object part : (List<?>)field(value,"materials")) materials.add(field(part,"name"));
                entry.put("materials", materials);
            }
            values.add(entry);
        }
        return values;
    }
    static List<Object> detachedStatics(Class<?> base, String arrayField) throws Exception {
        Object all = field(base,arrayField);
        Set<Object> registered = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int index=0;index<Array.getLength(all);++index) registered.add(Array.get(all,index));
        List<Object> values = new ArrayList<>();
        for (Field declaration : base.getDeclaredFields()) {
            if (!Modifier.isStatic(declaration.getModifiers()) || !base.isAssignableFrom(declaration.getType())) continue;
            declaration.setAccessible(true);
            Object value = declaration.get(null);
            if (value != null && !registered.contains(value)) values.add(map("symbol",base.getName()+"."+declaration.getName(),"class",value.getClass().getName(),"scalars",scalars(value),"status","not_in_registry_array"));
        }
        return values;
    }
    static List<Object> recipes(Object manager) throws Exception {
        List<Object> values = new ArrayList<>();
        List<?> list = (List<?>)invoke(manager,"b");
        for (int index = 0; index < list.size(); ++index) {
            Object recipe = list.get(index);
            Object output = invoke(recipe,"b");
            Object components = invoke(recipe,"getComponents");
            Map<String,Object> entry = map("order", index, "class", recipe.getClass().getName(), "output", stack(output), "components", stackArray(components),
                "difficulty", invoke(recipe,"getUnmodifiedDifficulty"), "skillsets", invoke(recipe,"getSkillsets"),
                "include_in_lowest_difficulty", invoke(recipe,"getIncludeInLowestCraftingDifficultyDetermination"),
                "bench_material", material(invoke(recipe,"getMaterialToCheckToolBenchHardnessAgainst")),
                "dynamic", !Set.of("aai","aaj").contains(recipe.getClass().getName()) || output == null || components == null, "scalars", scalars(recipe));
            if (Class.forName("aai").isInstance(recipe)) { entry.put("width",field(recipe,"b")); entry.put("height",field(recipe,"c")); }
            values.add(entry);
        }
        return values;
    }
    static List<Object> smelting(Object manager) throws Exception {
        Map<?,?> all = (Map<?,?>)invoke(manager,"b");
        List<Integer> ids = new ArrayList<>();
        for (Object key : all.keySet()) ids.add((Integer)key);
        Collections.sort(ids);
        Class<?> furnace = Class.forName("asg");
        Method heat = furnace.getMethod("getHeatLevelRequired",int.class);
        List<Object> values = new ArrayList<>();
        for (Integer id : ids) values.add(map("input_item_id",id,"output",stack(all.get(id)),"required_heat",heat.invoke(null,id)));
        return values;
    }
    static List<Object> entities() throws Exception {
        Class<?> entityList = Class.forName("nt");
        Map<?,?> eggs = (Map<?,?>)field(entityList,"a");
        List<Object> values = new ArrayList<>();
        // EntityListEntry stores Class literals only: no entity constructor call.
        for (Object entry : (List<?>)field(entityList,"entries")) {
            int id = (Integer)field(entry,"id");
            Class<?> type = (Class<?>)field(entry,"_class");
            values.add(map("id",id,"registered_name",field(entry,"name"),"class",type.getName(),
                "spawn_egg",eggs.containsKey(id) ? scalars(eggs.get(id)) : null,"entity_instantiated",false));
        }
        return values;
    }
    @SuppressWarnings("unchecked")
    static List<Object> enchantments() throws Exception {
        Class<?> base = Class.forName("aau");
        List<Object> values = registry(base,"b","z");
        Object all = field(base,"b");
        for (Object row : values) {
            Map<String,Object> record = (Map<String,Object>)row;
            Object value = Array.get(all,(Integer)record.get("id"));
            record.put("name_suffix",invoke(value,"getNameSuffix"));
            record.put("max_level",invoke(value,"getNumLevels"));
            record.put("has_levels",invoke(value,"hasLevels"));
            record.put("rarity",scalars(field(value,"rarity")));
        }
        return values;
    }
    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number) {
            double number = ((Number)value).doubleValue();
            return Double.isFinite(number) ? value.toString() : quote(value.toString());
        }
        if (value instanceof String) return quote((String)value);
        if (value instanceof Map<?,?>) {
            StringJoiner text = new StringJoiner(",","{","}");
            ((Map<?,?>)value).forEach((key,item) -> text.add(quote((String)key)+":"+json(item)));
            return text.toString();
        }
        if (value instanceof Iterable<?>) {
            StringJoiner text = new StringJoiner(",","[","]");
            for (Object item : (Iterable<?>)value) text.add(json(item));
            return text.toString();
        }
        if (value.getClass().isArray()) {
            StringJoiner text = new StringJoiner(",","[","]");
            for (int index=0;index<Array.getLength(value);++index) text.add(json(Array.get(value,index)));
            return text.toString();
        }
        throw new IllegalArgumentException("Unsupported export type: " + value.getClass().getName());
    }
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char letter : value.toCharArray()) {
            switch (letter) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default: if (letter < 32) out.append(String.format("\\u%04x",(int)letter)); else out.append(letter);
            }
        }
        return out.append('"').toString();
    }
    @FunctionalInterface interface GuardProbe { void run() throws Exception; }
    static void expectDenied(String name, GuardProbe action, List<String> results) throws Exception {
        try { action.run(); }
        catch (SecurityException denied) { results.add(name); return; }
        throw new AssertionError("Guard allowed forbidden action: " + name);
    }
    @SuppressWarnings("removal")
    static List<String> guardTests(Path workspace) throws Exception {
        List<String> results = new ArrayList<>();
        expectDenied("write_file",() -> Files.writeString(workspace.resolve("guard-must-not-write.txt"),"forbidden"),results);
        expectDenied("process",() -> new ProcessBuilder("guard-must-not-execute").start(),results);
        expectDenied("network",() -> System.getSecurityManager().checkConnect("127.0.0.1",25565),results);
        expectDenied("native_load",() -> System.loadLibrary("guard_must_not_load"),results);
        expectDenied("thread_create",() -> new Thread(() -> {}).start(),results);
        expectDenied("disable_guard",() -> System.setSecurityManager(null),results);
        expectDenied("unrelated_read",() -> Files.readString(workspace.getParent().resolve("AGENTS.md")),results);
        return results;
    }
    @SuppressWarnings("removal")
    public static void main(String[] args) throws Exception {
        Path workspace = Paths.get(args[0]).toAbsolutePath().normalize();
        Path javaHome = Paths.get(System.getProperty("java.home")).toAbsolutePath().normalize();
        // Preload helper nested class and JDK utility classes before restricting loader creation.
        ReadOnlyGuard guard = new ReadOnlyGuard(List.of(workspace,javaHome));
        System.setSecurityManager(guard);
        List<String> guardChecks = guardTests(workspace);
        Class<?> blocks = Class.forName("aqz");
        Class<?> items = Class.forName("yc");
        Object crafting = invoke(Class.forName("aaf"),"a");
        Object furnace = invoke(Class.forName("aab"),"a");
        Map<String,Object> data = map("author","NingMeng123","java_version",System.getProperty("java.version"),
            "mode","guarded_registration_only", "game_main_invoked",false,
            "guard_checks_passed",guardChecks,
            "activation_flags",map("professions_suppressed",field(Class.forName("atv"),"professions_suppressed"),"in_dev_mode",field(Class.forName("atv"),"in_dev_mode"),"allow_new_sand_physics",field(Class.forName("atv"),"allow_new_sand_physics")),
            "items",registry(items,"g","cv"), "blocks",registry(blocks,"s","cF"),
            "unregistered_static_items",detachedStatics(items,"g"),
            "materials",registry(Class.forName("akc"),"materials",null),
            "crafting_recipes",recipes(crafting), "furnace_recipes",smelting(furnace),
            "entities",entities(),"enchantments",enchantments(),
            "registration_error_message",invoke(Class.forName("atv"),"getErrorMessage"));
        System.out.println("MITE_EXPORT_JSON_BEGIN");
        System.out.println(json(data));
        System.out.println("MITE_EXPORT_JSON_END");
    }
}
