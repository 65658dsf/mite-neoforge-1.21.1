package io.github.ningmeng123.mite.gametest.client;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.config.MiteSettingsScreen;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen.Context;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen.Element;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Opt-in real client linkage and language checks without a Minecraft instance or rendering. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GameplayConfigClientAudit implements DataProvider {
    private static final String CLIENT = "io.github.ningmeng123.mite.client.config.";
    private static final String MOD_MENU = "com.terraformersmc.mod_menu.ModMenu";
    private final Path output;

    private GameplayConfigClientAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.gameplayConfigClientAudit")) return;
        event.getGenerator().addProvider(true, new GameplayConfigClientAudit(Path.of(System.getProperty(
                "mite.gameplayConfigClientAuditOutput", "build/verification/gameplay-config-client-audit.json"))));
    }

    @Override public String getName() { return "Gameplay config client extension, Mod Menu and native language audit without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        var report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("screen_init_invoked", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("visual_appearance_passed", false);
        report.addProperty("user_configuration_files_written", false);
        report.addProperty("full_client_setup_lifecycle_dispatched", false);
        try {
            auditScreens(report);
            auditLanguages(report);
            report.addProperty("status", report.has("mod_menu_datagen_limitation")
                    ? "passed_available_client_checks_with_explicit_mod_menu_datagen_limitation_without_rendering"
                    : "passed_client_factory_and_native_languages_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void auditScreens(JsonObject report) throws Throwable {
        ClassLoader loader = GameplayConfigClientAudit.class.getClassLoader();
        Class<?> landing = Class.forName(CLIENT + "MiteSettingsScreen", false, loader);
        Class<?> enchantments = Class.forName(CLIENT + "EnchantmentSettingsScreen", false, loader);
        MethodHandles.publicLookup().findConstructor(landing, MethodType.methodType(void.class, ModContainer.class, Screen.class));
        MethodHandles.publicLookup().findConstructor(enchantments, MethodType.methodType(void.class, Screen.class));
        check(landing.getDeclaredMethod("init").getReturnType() == void.class
                && enchantments.getDeclaredMethod("init").getReturnType() == void.class, "Both screen init signatures resolve");
        report.addProperty("both_screen_constructors_and_init_signatures_resolved", true);
        for (String name : List.of("MonsterGrowthSettingsScreen", "OreGenerationSettingsScreen")) {
            Class<?> settings = Class.forName(CLIENT + name, false, loader);
            MethodHandles.publicLookup().findConstructor(settings, MethodType.methodType(void.class, Screen.class));
            check(settings.getDeclaredMethod("init").getReturnType() == void.class, name + " init signature resolves");
        }
        report.addProperty("growth_formula_and_actual_ore_screen_linkage_verified", true);
        Class<?> spawnSettings = Class.forName(CLIENT + "MonsterSpawnSettingsScreen", false, loader);
        MethodHandles.publicLookup().findConstructor(spawnSettings,
                MethodType.methodType(void.class, Screen.class, String.class, Supplier.class, Consumer.class));
        MethodHandles.publicLookup().findConstructor(spawnSettings,
                MethodType.methodType(void.class, Screen.class, String.class, Supplier.class, Consumer.class, Function.class));
        check(spawnSettings.getDeclaredMethod("init").getReturnType() == void.class, "Shared monster spawn screen init resolves");
        Class<?> spawnCatalog = Class.forName("io.github.ningmeng123.mite.config.MonsterSpawnCatalog", false, loader);
        Class<?> spawnEntry = Class.forName(spawnCatalog.getName() + "$Entry", false, loader);
        MethodHandles.publicLookup().findStatic(spawnCatalog, "entries", MethodType.methodType(List.class, String.class, RegistryAccess.class));
        MethodHandles.publicLookup().findStatic(spawnCatalog, "ownedBy", MethodType.methodType(boolean.class, String.class, String.class));
        MethodHandles.publicLookup().findConstructor(spawnEntry,
                MethodType.methodType(void.class, String.class, Component.class, EntityType.class, List.class));
        check(calls(bytecode(spawnSettings.getName()), "<init>", spawnCatalog.getName().replace('.', '/'), "entries")
                        && callsOrLinkedLambda(bytecode(spawnSettings.getName()), "<init>", spawnCatalog.getName().replace('.', '/'), "ownedBy"),
                "The shared browser discovers current registry entries and retains overrides under their owning mod");
        boolean entityRegistry = false;
        for (var method : bytecode(spawnCatalog.getName()).methods) for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.owner.equals("net/minecraft/core/registries/BuiltInRegistries")
                    && field.name.equals("ENTITY_TYPE")) entityRegistry = true;
        check(entityRegistry, "Monster settings enumerate the actual entity registry without a UI entity allowlist");
        report.addProperty("shared_monster_spawn_screen_and_registry_catalog_linkage_verified", true);
        report.addProperty("monster_spawn_screen_constructor_or_init_invoked", false);
        Class<?> exportResult = Class.forName(CLIENT + "ConfigExportResultScreen", false, loader);
        MethodHandles.publicLookup().findConstructor(exportResult,
                MethodType.methodType(void.class, Screen.class, Path.class, String.class));
        check(exportResult.getDeclaredMethod("init").getReturnType() == void.class, "Export result screen init resolves");
        MethodHandles.publicLookup().findStatic(Class.forName("io.github.ningmeng123.mite.config.GameplayConfigExport", false, loader),
                "export", MethodType.methodType(Path.class, Path.class));
        Class<?> exportedBundle = Class.forName("io.github.ningmeng123.mite.config.GameplayConfigExport$ExportedBundle", false, loader);
        MethodHandles.publicLookup().findStatic(Class.forName("io.github.ningmeng123.mite.config.GameplayConfigExport", false, loader),
                "exportBundle", MethodType.methodType(exportedBundle, Path.class));
        MethodHandles.publicLookup().findStatic(exportResult, "success", MethodType.methodType(exportResult, Screen.class, exportedBundle));
        check(calls(bytecode(landing.getName()), "exportConfiguration", "io/github/ningmeng123/mite/config/GameplayConfigExport", "exportBundle"),
                "The settings page calls the complete MITE and optional ITE configuration bundle exporter");
        report.addProperty("config_export_screen_and_backend_linkage_verified", true);
        report.addProperty("config_export_invoked_by_client_audit", false);
        report.addProperty("enchantment_screen_constructor_invoked", false);
        report.addProperty("enchantment_screen_initialization_limitation",
                "Its constructor reads Minecraft connection/config/world state; init requires native font and widgets. Datagen supplies none of these, so catalog linkage, bundled defaults and bytecode are checked without rendering.");

        ClassNode registration = bytecode(CLIENT + "MiteConfigClient");
        boolean landingConstructor = false;
        for (var method : registration.methods) if (method.name.equals("register")) for (var instruction : method.instructions)
            if (instruction instanceof InvokeDynamicInsnNode dynamic) for (Object argument : dynamic.bsmArgs)
                if (argument instanceof Handle handle && handle.getOwner().equals(landing.getName().replace('.', '/'))
                        && handle.getName().equals("<init>")) landingConstructor = true;
        check(landingConstructor, "The production config factory is bound to MiteSettingsScreen's constructor");
        report.addProperty("production_factory_constructor_handle_verified", true);

        ModContainer container = ModList.get().getModContainerById(Mite.MOD_ID).orElseThrow();
        boolean present = container.getCustomExtension(IConfigScreenFactory.class).isPresent();
        report.addProperty("factory_present_at_audit_start", present);
        // Construction is included in datagen; audit code must not manufacture a missing registration.
        check(present, "The production mod constructor must already have registered its config factory");
        report.addProperty("factory_registered_during_mod_construction", true);
        report.addProperty("production_registration_callback_invoked_by_audit", false);
        IConfigScreenFactory factory = IConfigScreenFactory.getForMod(container.getModInfo()).orElseThrow(
                () -> new IllegalStateException("The actual NeoForge config extension is missing"));
        Screen screen = factory.createScreen(container, null);
        check(screen.getClass() == MiteSettingsScreen.class, "The actual registered factory returns MiteSettingsScreen");
        report.addProperty("registered_factory_class", factory.getClass().getName());
        report.addProperty("actual_factory_screen_class", screen.getClass().getName());
        report.addProperty("landing_screen_constructor_invoked", true);
        auditAdvancedSections(report, screen);

        ClassNode browser = bytecode(enchantments.getName());
        String catalogName = "io.github.ningmeng123.mite.config.EnchantmentCatalog";
        String catalogOwner = catalogName.replace('.', '/');
        check(calls(browser, "<init>", catalogOwner, "entries") && calls(browser, "<init>", catalogOwner, "observe"),
                "The browser always reads the persistent catalog and refreshes it when a world is available");
        Class<?> catalog = Class.forName(catalogName, false, loader);
        Class<?> catalogEntry = Class.forName(catalogName + "$Entry", false, loader);
        MethodHandles.publicLookup().findStatic(catalog, "entries", MethodType.methodType(List.class));
        MethodHandles.publicLookup().findStatic(catalog, "observe", MethodType.methodType(void.class, RegistryAccess.class));
        MethodHandles.publicLookup().findConstructor(catalogEntry, MethodType.methodType(void.class, String.class, int.class, String.class));
        MethodHandles.publicLookup().findVirtual(catalogEntry, "name", MethodType.methodType(net.minecraft.network.chat.Component.class));
        report.addProperty("offline_enchantment_catalog_screen_linkage_verified", true);
        report.addProperty("catalog_or_enchantment_screen_initialized", false);

        boolean registry = false, registryRead = false, definition = false, rawMaximum = false, configuredMaximum = false;
        for (var method : bytecode(catalogName).methods) for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals("net/minecraft/core/registries/Registries")
                    && field.name.equals("ENCHANTMENT")) registry = true;
            if (instruction instanceof MethodInsnNode call && (call.name.equals("registryOrThrow") || call.name.equals("registry"))) registryRead = true;
            if (instruction instanceof MethodInsnNode call && call.name.equals("definition")) definition = true;
            if (instruction instanceof MethodInsnNode call && call.name.equals("maxLevel")) rawMaximum = true;
            if (instruction instanceof MethodInsnNode call && call.name.equals("getMaxLevel")) configuredMaximum = true;
        }
        check(registry && registryRead && definition && rawMaximum && !configuredMaximum,
                "Catalog discovery reads current ENCHANTMENT definitions independently of configured maximum levels");
        report.addProperty("live_enchantment_registry_enumeration_bytecode_verified", true);
        auditEnchantmentSeed(report);

        boolean modMenu = loader.getResource(MOD_MENU.replace('.', '/') + ".class") != null;
        report.addProperty("mod_menu_class_available", modMenu);
        report.addProperty("mod_menu_get_config_screen_returned_expected_screen", false);
        if (!modMenu) {
            report.addProperty("mod_menu_check", "optional_mod_absent");
            return;
        }
        Class<?> menu = Class.forName(MOD_MENU, false, loader);
        var getConfigScreen = menu.getMethod("getConfigScreen", ModContainer.class, Screen.class);
        MethodHandles.publicLookup().unreflect(getConfigScreen);
        check(getConfigScreen.getReturnType() == Screen.class, "Mod Menu's actual entry point has the native screen signature");
        ClassNode menuCode = bytecode(MOD_MENU);
        check(calls(menuCode, "getConfigScreen", "net/neoforged/neoforge/client/gui/IConfigScreenFactory", "createScreen")
                && calls(menuCode, "getConfigScreenFactory", "net/neoforged/neoforge/client/gui/IConfigScreenFactory", "getForMod"),
                "The installed Mod Menu entry point delegates to the actual NeoForge extension point");
        report.addProperty("mod_menu_entry_point_handle_and_extension_delegation_verified", true);
        report.addProperty("mod_menu_class_resource_sha256", sha256(resource(MOD_MENU.replace('.', '/') + ".class")));
        report.addProperty("mod_menu_get_config_screen_invocation_attempted", true);
        try {
            Object selected = getConfigScreen.invoke(null, container, (Object)null);
            check(selected != null && selected.getClass() == MiteSettingsScreen.class,
                    "The installed Mod Menu getConfigScreen returns MiteSettingsScreen");
            report.addProperty("mod_menu_get_config_screen_returned_expected_screen", true);
            report.addProperty("mod_menu_check", "actual_entry_point_returned_expected_screen");
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IllegalStateException && cause.getMessage() != null
                    && cause.getMessage().contains("Cannot get config value before config is loaded")) {
                report.addProperty("mod_menu_initial_unloaded_config_failure", cause.toString());
                if (!auditModMenuWithInMemoryDefaults(menu, container, report)) {
                    report.addProperty("mod_menu_check", "blocked_by_datagen_configuration_lifecycle");
                    report.addProperty("mod_menu_datagen_limitation",
                            "The actual entry point reads its unloaded CLIENT configuration first. Datagen does not load configs, and module access prevented supplying temporary in-memory defaults. No user config was loaded or changed. Its registered factory and extension delegation were verified separately.");
                }
            } else throw cause;
        }
    }

    private static void auditAdvancedSections(JsonObject report, Screen parent) throws Exception {
        String name = CLIENT + "MiteSettingsScreen$GameplaySectionScreen";
        String owner = name.replace('.', '/');
        String nativeOwner = ConfigurationSectionScreen.class.getName().replace('.', '/');
        Class<?> section = Class.forName(name, false, GameplayConfigClientAudit.class.getClassLoader());
        check(section.getSuperclass() == ConfigurationSectionScreen.class,
                "The advanced page uses the native section screen with its save and restart handling");
        check(section.getDeclaredMethod("createSection", String.class, UnmodifiableConfig.class, UnmodifiableConfig.class)
                .getReturnType() == Element.class, "The advanced page overrides native subsection creation");
        ClassNode code = bytecode(name);
        check(references(bytecode(CLIENT + "MiteSettingsScreen"), "gameplayScreen", owner, "<init>"),
                "The advanced config factory is bound to the filtered section constructor");
        check(references(code, "<init>", owner, "filterValue"),
                "The filtered section constructor installs its leaf-value filter");
        check(calls(code, "createSection", owner, "hidesOreSection")
                && calls(code, "createSection", Context.class.getName().replace('.', '/'), "keylist")
                && calls(code, "createSection", "java/util/List", "isEmpty"),
                "Subsection creation checks the native context's root path and ore-section policy");
        boolean delegatesToNative = false;
        for (var method : code.methods) if (method.name.equals("createSection")) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.owner.equals(nativeOwner) && call.name.equals("createSection")) delegatesToNative = true;
        check(delegatesToNative, "Retained subsections delegate to native section creation");

        var hiddenPolicy = section.getDeclaredMethod("hidesOreSection", String.class);
        var leafPolicy = section.getDeclaredMethod("filterValue", Context.class, String.class, Element.class);
        check(hiddenPolicy.trySetAccessible() && leafPolicy.trySetAccessible(), "Production section policies are accessible to the audit");
        var matchingConfigs = new ArrayList<ModConfig>();
        for (var type : ModConfig.Type.values()) for (var config : ModConfigs.getConfigSet(type))
            if (config.getModId().equals(Mite.MOD_ID) && config.getSpec() == MiteConfig.SPEC) matchingConfigs.add(config);
        check(matchingConfigs.size() == 1, "The audit uses the actual registered MITE gameplay config spec");
        ModConfig config = matchingConfigs.getFirst();
        var originalLoadedConfig = config.getLoadedConfig();
        boolean originallyLoaded = MiteConfig.SPEC.isLoaded();
        var originalValues = new LinkedHashMap<>(MiteConfig.SPEC.getValues().valueMap());
        var originalSpecs = new LinkedHashMap<>(MiteConfig.SPEC.getSpec().valueMap());
        Context root = Context.top(Mite.MOD_ID, parent, config, (context, key, original) -> {
            try { return (Element) leafPolicy.invoke(null, context, key, original); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot invoke the production leaf filter", failure); }
        });
        check(root.keylist().isEmpty() && root.modSpec() == MiteConfig.SPEC,
                "The native top context exposes the actual root entries with an empty path");
        Set<String> expectedHidden = Set.of("underworld_ores", "overworld_ores", "underworld_generation", "overworld_generation", "monster_spawning");
        Set<String> expectedVisible = Set.of("monsters", "combat", "structures", "enchantments");
        Set<String> hidden = new TreeSet<>(), visible = new TreeSet<>(), monsterValues = new TreeSet<>();
        Element sentinel = new Element(null, null, null, null, true);
        int suppressedMonsterValues = 0;
        for (var entry : root.entries()) {
            String key = entry.getKey();
            check(entry.getRawValue() instanceof UnmodifiableConfig && root.valueSpecs().get(key) instanceof UnmodifiableConfig,
                    "The registered root entry is a native config subsection: " + key);
            if ((boolean) hiddenPolicy.invoke(null, key)) {
                hidden.add(key);
                continue;
            }
            visible.add(key);
            UnmodifiableConfig values = (UnmodifiableConfig) entry.getRawValue();
            UnmodifiableConfig specs = (UnmodifiableConfig) root.valueSpecs().get(key);
            Context child = Context.section(root, parent, values.entrySet(), specs.valueMap(), key);
            check(child.keylist().equals(List.of(key)) && child.filter() == root.filter(),
                    "Native subsection contexts preserve their absolute path and inherited leaf filter: " + key);
            for (var value : child.entries()) {
                check(value.getRawValue() instanceof ModConfigSpec.ConfigValue<?>, "A retained section contains a native config value");
                Element selected = child.filter().filterEntry(child, value.getKey(), sentinel);
                if (key.equals("monsters")) {
                    if (selected == null) suppressedMonsterValues++;
                    else {
                        check(selected == sentinel, "Retained monster settings preserve the original native element");
                        monsterValues.add(value.getKey());
                    }
                } else check(selected == sentinel, "Other gameplay and advanced enchantment values remain visible: " + key + "." + value.getKey());
            }
        }
        check(hidden.equals(expectedHidden) && visible.equals(expectedVisible),
                "Dedicated ore and monster-spawn sections are suppressed; all four other gameplay sections remain");
        check(monsterValues.equals(Set.of("growth_multiplier", "health_growth_multiplier", "damage_growth_multiplier"))
                && suppressedMonsterValues > 0, "Only the three monster multipliers remain in the advanced page");
        check(originalValues.equals(MiteConfig.SPEC.getValues().valueMap()) && originalSpecs.equals(MiteConfig.SPEC.getSpec().valueMap())
                && config.getLoadedConfig() == originalLoadedConfig && MiteConfig.SPEC.isLoaded() == originallyLoaded,
                "Section visibility checks do not modify the registered spec, config values or loaded config");
        var hiddenNames = new JsonArray(); hidden.forEach(hiddenNames::add);
        var visibleNames = new JsonArray(); visible.forEach(visibleNames::add);
        var monsterNames = new JsonArray(); monsterValues.forEach(monsterNames::add);
        report.add("advanced_hidden_ore_sections", hiddenNames);
        report.add("advanced_visible_sections", visibleNames);
        report.add("advanced_visible_monster_values", monsterNames);
        report.addProperty("advanced_suppressed_monster_values", suppressedMonsterValues);
        report.addProperty("advanced_section_factory_and_native_override_bytecode_verified", true);
        report.addProperty("advanced_section_policies_invoked_on_native_config_contexts", true);
        report.addProperty("advanced_section_config_spec_and_values_unchanged", true);
        report.addProperty("advanced_section_screen_constructor_invoked", false);
        report.addProperty("advanced_section_widgets_created", false);
    }

    private static void auditEnchantmentSeed(JsonObject report) throws Exception {
        byte[] bytes = resource("mite/enchantment-defaults.json");
        var seed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        check(seed.get("schema").getAsInt() == 1, "The packaged enchantment catalog uses the supported schema");
        Set<String> ids = new TreeSet<>();
        int vanilla = 0, mite = 0;
        for (var element : seed.getAsJsonArray("enchantments")) {
            var row = element.getAsJsonObject();
            String id = row.get("id").getAsString();
            check(net.minecraft.resources.ResourceLocation.tryParse(id) != null && id.contains(":"), "Valid bundled enchantment ID: " + id);
            check(ids.add(id), "Duplicate bundled enchantment ID: " + id);
            int maximum = row.get("defaultMaxLevel").getAsInt();
            check(maximum >= 1 && maximum <= 255, "Valid bundled default maximum: " + id);
            check(!JsonParser.parseString(row.get("descriptionJson").getAsString()).isJsonNull(), "Valid bundled component JSON: " + id);
            if (id.startsWith("minecraft:")) vanilla++;
            if (id.startsWith("mite:")) mite++;
        }
        check(vanilla > 0 && mite > 0, "Offline defaults include both native and MITE enchantments");
        report.addProperty("bundled_enchantment_catalog_entries", ids.size());
        report.addProperty("bundled_native_enchantment_entries", vanilla);
        report.addProperty("bundled_mite_enchantment_entries", mite);
        report.addProperty("bundled_enchantment_catalog_sha256", sha256(bytes));
        report.addProperty("bundled_offline_enchantment_catalog_validated", true);
    }

    private static boolean auditModMenuWithInMemoryDefaults(Class<?> menu, ModContainer container, JsonObject report) throws Exception {
        String modId = (String)menu.getField("MOD_ID").get(null);
        List<ModConfig> configs = ModConfigs.getConfigSet(ModConfig.Type.CLIENT).stream()
                .filter(config -> config.getModId().equals(modId)).toList();
        check(configs.size() == 1 && configs.getFirst().getSpec() instanceof ModConfigSpec,
                "The installed Mod Menu exposes its single native client config spec");
        ModConfig config = configs.getFirst();
        ModConfigSpec spec = (ModConfigSpec)config.getSpec();
        check(config.getLoadedConfig() == null && !spec.isLoaded(), "Temporary defaults must not replace any loaded user config");
        // ILoadedConfig is sealed. Reuse FML's real record with a null file path; never use ConfigTracker or dispatch config events.
        Class<?> loaded = Class.forName("net.neoforged.fml.config.LoadedConfig", false, ModConfig.class.getClassLoader());
        var constructor = loaded.getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        if (!constructor.trySetAccessible()) {
            report.addProperty("mod_menu_in_memory_config_constructor_accessible", false);
            return false;
        }
        report.addProperty("mod_menu_in_memory_config_constructor_accessible", true);
        CommentedConfig defaults = CommentedConfig.inMemory();
        spec.correct(defaults);
        var temporary = (IConfigSpec.ILoadedConfig)constructor.newInstance(defaults, null, config);
        report.addProperty("mod_menu_in_memory_defaults_used", true);
        report.addProperty("mod_menu_config_events_dispatched", false);
        try {
            spec.acceptConfig(temporary);
            Object selected = menu.getMethod("getConfigScreen", ModContainer.class, Screen.class).invoke(null, container, (Object)null);
            check(selected != null && selected.getClass() == MiteSettingsScreen.class,
                    "The installed Mod Menu getConfigScreen returns MiteSettingsScreen with temporary native defaults");
            report.addProperty("mod_menu_get_config_screen_returned_expected_screen", true);
            report.addProperty("mod_menu_check", "actual_entry_point_returned_expected_screen_with_ephemeral_default_config");
        } finally {
            spec.acceptConfig(null);
            report.addProperty("mod_menu_config_restored_to_unloaded", config.getLoadedConfig() == null && !spec.isLoaded());
        }
        check(config.getLoadedConfig() == null && !spec.isLoaded(), "Temporary Mod Menu defaults must be removed after the entry-point check");
        return true;
    }

    private static void auditLanguages(JsonObject report) throws Exception {
        Map<String, Map<String, String>> languages = new LinkedHashMap<>();
        Set<String> required = new TreeSet<>();
        var facts = new JsonArray();
        for (String locale : List.of("en_us", "zh_cn")) {
            byte[] bytes = resource("assets/mite/lang/" + locale + ".json");
            Map<String, String> translations = new LinkedHashMap<>();
            try (var stream = new java.io.ByteArrayInputStream(bytes)) { Language.loadFromJson(stream, translations::put); }
            translations.keySet().stream().filter(GameplayConfigClientAudit::configKey).forEach(required::add);
            languages.put(locale, translations);
            var fact = new JsonObject();
            fact.addProperty("locale", locale);
            fact.addProperty("parser", "net.minecraft.locale.Language.loadFromJson");
            fact.addProperty("sha256", sha256(bytes));
            facts.add(fact);
        }
        for (String name : List.of(CLIENT + "MiteSettingsScreen", CLIENT + "EnchantmentSettingsScreen",
                CLIENT + "MonsterGrowthSettingsScreen", CLIENT + "MonsterSpawnSettingsScreen", CLIENT + "OreGenerationSettingsScreen",
                CLIENT + "ConfigExportResultScreen", MiteConfig.class.getName()))
            for (var method : bytecode(name).methods) for (var instruction : method.instructions)
                if (instruction instanceof LdcInsnNode constant && constant.cst instanceof String key && configKey(key)) required.add(key);
        collectSpecKeys(MiteConfig.SPEC.getSpec(), List.of(), required);
        for (var language : languages.entrySet()) {
            for (String key : required) {
                String value = language.getValue().get(key);
                check(value != null && !value.isBlank() && !value.equals(key), "Missing native-parsed " + language.getKey() + " translation: " + key);
            }
            for (var format : Map.of("mite.config.page", 3, "mite.config.maximum.tooltip", 1, "mite.config.invalid_maximums", 1,
                    "mite.config.invalid_enchantment_entries", 1, "mite.config.enchantment_chance.tooltip", 0,
                    "mite.config.enchantment_chances.tooltip", 0, "mite.config.enchantment_reset.tooltip", 0,
                    "mite.config.enchantment_hint", 0).entrySet()) {
                String value = language.getValue().get(format.getKey());
                String formatted = String.format(Locale.ROOT, value, java.util.stream.IntStream.rangeClosed(1, format.getValue()).boxed().toArray());
                check(!formatted.contains("%s"), "Unresolved config placeholder in " + language.getKey() + ": " + format.getKey());
            }
        }
        var keys = new JsonArray(); required.forEach(keys::add);
        report.add("languages", facts);
        report.add("config_translation_keys", keys);
        report.addProperty("required_keys_checked_per_locale", required.size());
        report.addProperty("native_config_translations_and_placeholders_verified", true);
    }

    private static void collectSpecKeys(UnmodifiableConfig config, List<String> parent, Set<String> required) {
        for (var entry : config.entrySet()) {
            var path = new ArrayList<>(parent); path.add(entry.getKey());
            String group = MiteConfig.SPEC.getLevelTranslationKey(path);
            if (group != null) required.add(group);
            if (entry.getValue() instanceof UnmodifiableConfig child) collectSpecKeys(child, path, required);
            else if (entry.getValue() instanceof ModConfigSpec.ValueSpec value && value.getTranslationKey() != null)
                required.add(value.getTranslationKey());
        }
    }
    private static boolean configKey(String key) { return key.startsWith("mite.config.") || key.equals("mite.configuration.title"); }
    private static boolean calls(ClassNode type, String methodName, String owner, String name) {
        for (var method : type.methods) if (method.name.equals(methodName)) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        return false;
    }
    private static boolean callsOrLinkedLambda(ClassNode type, String methodName, String owner, String name) {
        if (calls(type, methodName, owner, name)) return true;
        for (var method : type.methods) if (method.name.equals(methodName)) for (var instruction : method.instructions)
            if (instruction instanceof InvokeDynamicInsnNode dynamic) for (Object argument : dynamic.bsmArgs)
                if (argument instanceof Handle handle && handle.getOwner().equals(type.name)
                        && calls(type, handle.getName(), owner, name)) return true;
        return false;
    }
    private static boolean references(ClassNode type, String methodName, String owner, String name) {
        for (var method : type.methods) if (method.name.equals(methodName)) for (var instruction : method.instructions)
            if (instruction instanceof InvokeDynamicInsnNode dynamic) for (Object argument : dynamic.bsmArgs)
                if (argument instanceof Handle handle && handle.getOwner().equals(owner) && handle.getName().equals(name)) return true;
        return false;
    }
    private static ClassNode bytecode(String name) throws Exception {
        var node = new ClassNode(); new ClassReader(resource(name.replace('.', '/') + ".class")).accept(node, 0); return node;
    }
    private static byte[] resource(String name) throws Exception {
        try (var stream = GameplayConfigClientAudit.class.getClassLoader().getResourceAsStream(name)) {
            check(stream != null, "Missing runtime resource " + name); return stream.readAllBytes();
        }
    }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void write(JsonObject report) throws Exception {
        Path absolute = output.toAbsolutePath().normalize(); Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
