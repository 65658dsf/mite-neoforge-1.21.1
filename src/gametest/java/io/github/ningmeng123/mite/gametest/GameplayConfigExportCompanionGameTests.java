package io.github.ningmeng123.mite.gametest;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.ningmeng123.mite.config.GameplayConfigExport;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforgespi.language.IModInfo;

/** Exercises optional ITE exports through FML's actual registered-config discovery. @author NingMeng123 */
@GameTestHolder("mite_config")
@PrefixGameTestTemplate(false)
public final class GameplayConfigExportCompanionGameTests {
    private enum Mode { NORMAL, CUSTOM }

    private GameplayConfigExportCompanionGameTests() {}

    @GameTest(template = "empty")
    public static void iteBundleDiscoversEveryCommonSpecAndCurrentValue(GameTestHelper helper) throws Exception {
        var original = List.copyOf(ModConfigs.getModConfigs("modern_ite"));
        var sourceFiles = new LinkedHashMap<Path, byte[]>();
        for (var config : original) {
            if (config.getType() == ModConfig.Type.COMMON && config.getLoadedConfig() != null) {
                Path source = sourcePath(config);
                if (source != null) sourceFiles.put(source, Files.readAllBytes(source));
            }
        }
        try (var registration = new Registration()) {
            var builder = new ModConfigSpec.Builder();
            builder.comment("A registered nested ITE section").push("Entities").push("NaturalSpawns");
            var rate = builder.comment("Current rate, not the default").defineInRange("rate", 1D, 0D, 100D);
            var ids = builder.defineListAllowEmpty("overrides", List.of(), () -> "modern_ite:mob=1.0", value -> value instanceof String);
            var mode = builder.defineEnum("mode", Mode.NORMAL);
            builder.pop(2);
            var count = builder.defineInRange("count", 2L, 0L, 100000000000L);
            var enabled = builder.define("enabled", true);
            ModConfigSpec spec = builder.build();
            registration.add(ModConfig.Type.COMMON, spec, "modern_ite-export-test-common.toml", true);
            rate.set(2.75D);
            ids.set(List.of("modern_ite:test_monster=0.0", "future_mod:retained=1.125"));
            mode.set(Mode.CUSTOM);
            count.set(7000000000L);
            enabled.set(false);
            var secondBuilder = new ModConfigSpec.Builder();
            var extra = secondBuilder.defineInRange("future_setting", 1, 0, 20);
            registration.add(ModConfig.Type.COMMON, secondBuilder.build(), "modern_ite-export-test-extra.toml", true);
            extra.set(13);
            var clientBuilder = new ModConfigSpec.Builder();
            clientBuilder.define("client_only", true);
            registration.add(ModConfig.Type.CLIENT, clientBuilder.build(), "modern_ite-export-test-client.toml", true);

            Path directory = Files.createTempDirectory("mite-ite-config-bundle-");
            var bundle = GameplayConfigExport.exportBundle(directory);
            var filenames = bundle.files().stream().map(path -> path.getFileName().toString()).toList();
            helper.assertValueEqual(bundle.primaryFile().getFileName().toString(), "mite-common.toml", "The compatible primary file remains MITE's config");
            helper.assertTrue(filenames.containsAll(List.of("mite-common.toml", "modern_ite-export-test-common.toml", "modern_ite-export-test-extra.toml")),
                    "All registered ITE COMMON specs join the MITE package");
            helper.assertFalse(filenames.contains("modern_ite-export-test-client.toml"), "Client-only settings never enter the server package");
            for (var path : bundle.files()) helper.assertValueEqual(path.getParent(), bundle.primaryFile().getParent(), "The complete package shares one fresh directory");
            var parsed = new TomlParser().parse(Files.readString(bundle.primaryFile().resolveSibling("modern_ite-export-test-common.toml")));
            helper.assertTrue(spec.isCorrect(parsed), "The ITE output passes its own real NeoForge spec");
            helper.assertValueEqual(((Number)parsed.get(rate.getPath())).doubleValue(), 2.75D, "Nested rates preserve their saved current value");
            helper.assertValueEqual(parsed.get(ids.getPath()), ids.get(), "ID overrides survive without a handwritten field or entity allowlist");
            helper.assertValueEqual(parsed.get(mode.getPath()), "CUSTOM", "Enum values remain valid TOML strings");
            helper.assertValueEqual(((Number)parsed.get(count.getPath())).longValue(), 7000000000L, "Long settings do not narrow to integers");
            helper.assertValueEqual(parsed.get(enabled.getPath()), false, "Boolean settings retain their current value");
            helper.assertValueEqual(parsed.getComment(rate.getPath()), rate.getSpec().getComment(),
                    "Native option documentation, including generated default/range notes, follows the setting");
            helper.assertValueEqual(rate.get(), 2.75D, "Exporting leaves the registered ITE values unchanged");
            var second = new TomlParser().parse(Files.readString(bundle.primaryFile().resolveSibling("modern_ite-export-test-extra.toml")));
            helper.assertValueEqual(((Number)second.get("future_setting")).intValue(), 13, "Additional registered common files are discovered automatically");
            // When this suite runs with the real ITE JAR, verify every real setting too, including
            // future fields not present in this test specimen or known to the MITE build.
            for (var config : original) {
                if (config.getType() != ModConfig.Type.COMMON) continue;
                helper.assertTrue(filenames.contains(config.getFileName()), "The installed ITE's real common file is included");
                var realSpec = (ModConfigSpec)config.getSpec();
                var realOutput = new TomlParser().parse(Files.readString(bundle.primaryFile().resolveSibling(config.getFileName())));
                helper.assertTrue(realSpec.isCorrect(realOutput), "The installed ITE validates the exported real config");
                assertDefinitionsPreserved(helper, realSpec.getValues(), realOutput);
            }
            for (var source : sourceFiles.entrySet()) helper.assertTrue(Arrays.equals(Files.readAllBytes(source.getKey()), source.getValue()),
                    "Exporting does not write the installed ITE's existing common configuration");
        }
        helper.assertValueEqual(ModConfigs.getModConfigs("modern_ite"), original, "Every synthetic registration is removed after testing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unavailableIteCommonConfigRejectsTheWholeExport(GameTestHelper helper) throws Exception {
        try (var registration = new Registration()) {
            var builder = new ModConfigSpec.Builder();
            builder.define("registered_but_not_loaded", true);
            registration.add(ModConfig.Type.COMMON, builder.build(), "modern_ite-export-test-unloaded.toml", false);
            Path directory = Files.createTempDirectory("mite-ite-unloaded-export-");
            assertFailedCleanly(helper, directory, "unavailable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidIteValueAndUnsafeFilenameNeverProduceIncompleteBundles(GameTestHelper helper) throws Exception {
        try (var registration = new Registration()) {
            var builder = new ModConfigSpec.Builder();
            var rate = builder.defineInRange("rate", 1D, 0D, 100D);
            registration.add(ModConfig.Type.COMMON, builder.build(), "modern_ite-export-test-invalid.toml", true);
            rate.set(-1D);
            assertFailedCleanly(helper, Files.createTempDirectory("mite-ite-invalid-export-"), "validation");
            helper.assertValueEqual(rate.get(), -1D, "Export validation does not repair the original ITE setting");
        }
        try (var registration = new Registration()) {
            var builder = new ModConfigSpec.Builder();
            builder.define("unsafe_name", true);
            registration.add(ModConfig.Type.COMMON, builder.build(), "../modern_ite-export-test-escape.toml", true);
            assertFailedCleanly(helper, Files.createTempDirectory("mite-ite-unsafe-export-"), "filename");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void iteWriterFailureRemovesMiteAndCompanionFilesTogether(GameTestHelper helper) throws Exception {
        try (var registration = new Registration()) {
            var builder = new ModConfigSpec.Builder();
            // This specimen deliberately accepts an object TOML cannot encode. It reaches the
            // actual writer after MITE was written and proves rollback covers the entire package.
            var unsupported = builder.define("unsupported", "normal", value -> true);
            registration.add(ModConfig.Type.COMMON, builder.build(), "modern_ite-export-test-writer.toml", true);
            @SuppressWarnings("unchecked")
            var unrestricted = (ModConfigSpec.ConfigValue<Object>)(ModConfigSpec.ConfigValue<?>)unsupported;
            unrestricted.set(new Object());
            Path directory = Files.createTempDirectory("mite-ite-writer-export-");
            Path earlier = directory.resolve("exports").resolve("previous").resolve("mite-common.toml");
            Files.createDirectories(earlier.getParent());
            Files.writeString(earlier, "# Earlier successful export\n");
            boolean failed = false;
            try { GameplayConfigExport.exportBundle(directory); }
            catch (IOException expected) { failed = true; }
            helper.assertTrue(failed, "An unencodable ITE value reports failure");
            try (var paths = Files.list(directory.resolve("exports"))) {
                helper.assertValueEqual(paths.toList(), List.of(earlier.getParent()), "Rollback removes every file and directory created by this export");
            }
            helper.assertValueEqual(Files.readString(earlier), "# Earlier successful export\n", "Rollback preserves all earlier export files");
        }
        helper.succeed();
    }

    private static void assertFailedCleanly(GameTestHelper helper, Path directory, String reason) throws Exception {
        boolean failed = false;
        try { GameplayConfigExport.exportBundle(directory); }
        catch (IOException expected) { failed = expected.getMessage().contains(reason); }
        helper.assertTrue(failed, "The complete export fails with an explicit " + reason + " reason");
        helper.assertFalse(Files.exists(directory.resolve("exports")), "A failed companion validation does not publish MITE-only output");
    }

    private static Path sourcePath(ModConfig config) {
        try { return config.getFullPath(); }
        catch (IllegalStateException inMemory) { return null; }
    }

    private static void assertDefinitionsPreserved(GameTestHelper helper, UnmodifiableConfig definitions, UnmodifiableConfig output) {
        for (var definition : definitions.entrySet()) {
            if (definition.getValue() instanceof UnmodifiableConfig child) assertDefinitionsPreserved(helper, child, output);
            else if (definition.getValue() instanceof ModConfigSpec.ConfigValue<?> value) {
                Object expected = value.getRaw();
                Object actual = output.get(value.getPath());
                if (expected instanceof Enum<?> constant) expected = constant.name();
                if (expected instanceof Number number && actual instanceof Number written)
                    helper.assertValueEqual(new java.math.BigDecimal(number.toString()), new java.math.BigDecimal(written.toString()),
                            "The real ITE numeric setting survives: " + value.getPath());
                else helper.assertValueEqual(actual, expected, "The real ITE setting survives: " + value.getPath());
            }
        }
    }

    /** Registers real FML ModConfigs with in-memory backing and removes only its own objects. */
    private static final class Registration implements AutoCloseable {
        private final List<ModConfig> added = new ArrayList<>();
        private final boolean hadModEntry;
        private final Object previousLock;
        private final ModContainer container;

        private Registration() throws Exception {
            hadModEntry = registryMap("configsByMod").containsKey("modern_ite");
            previousLock = registryMap("locksByMod").get("modern_ite");
            var info = (IModInfo)Proxy.newProxyInstance(IModInfo.class.getClassLoader(), new Class<?>[]{ IModInfo.class }, (proxy, method, args) -> {
                if (method.getName().equals("getModId")) return "modern_ite";
                if (method.getName().equals("toString")) return "ITE config export specimen";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return null;
            });
            container = new ModContainer(info) { @Override public IEventBus getEventBus() { return null; } };
        }

        private void add(ModConfig.Type type, ModConfigSpec spec, String fileName, boolean loaded) throws Exception {
            var config = ConfigTracker.INSTANCE.registerConfig(type, spec, container, fileName);
            added.add(config);
            if (loaded) {
                CommentedConfig contents = CommentedConfig.inMemory();
                spec.correct(contents);
                var implementation = Class.forName("net.neoforged.fml.config.LoadedConfig");
                var constructor = implementation.getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
                constructor.setAccessible(true);
                var backing = (IConfigSpec.ILoadedConfig)constructor.newInstance(contents, null, config);
                var field = ModConfig.class.getDeclaredField("loadedConfig");
                field.setAccessible(true);
                field.set(config, backing);
                spec.acceptConfig(backing);
            }
        }

        @Override public void close() throws Exception {
            for (var config : added) {
                config.getSpec().acceptConfig(null);
                registryMap("fileMap").remove(config.getFileName(), config);
                @SuppressWarnings("unchecked")
                var sets = (Map<ModConfig.Type, Set<ModConfig>>)(Map<?, ?>)registryMap("configSets");
                sets.get(config.getType()).remove(config);
                @SuppressWarnings("unchecked")
                var byMod = (Map<String, List<ModConfig>>)(Map<?, ?>)registryMap("configsByMod");
                var list = byMod.get("modern_ite");
                list.remove(config);
                if (!hadModEntry && list.isEmpty()) byMod.remove("modern_ite", list);
            }
            if (previousLock == null && !registryMap("configsByMod").containsKey("modern_ite"))
                registryMap("locksByMod").remove("modern_ite");
        }

        @SuppressWarnings("unchecked")
        private static Map<Object, Object> registryMap(String name) throws Exception {
            var field = ConfigTracker.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Map<Object, Object>)field.get(ConfigTracker.INSTANCE);
        }
    }
}
