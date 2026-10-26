package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in audit of actual client creative collections and the published JEI runtime. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class SimplySwordsUniqueClientAudit {
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(180);
    private static final List<String> FAMILIES = List.of("longsword", "twinblade", "rapier", "katana", "sai",
            "spear", "glaive", "warglaive", "cutlass", "claymore", "greataxe", "greathammer", "chakram", "scythe", "halberd");
    private static final List<String> ADDED_MATERIALS = List.of("rusted_iron", "copper", "silver",
            "ancient_metal", "mithril", "adamantium", "vibranium");
    private static final List<String> MATERIAL_ITEMS = List.of("runic_tablet", "runefused_gem", "netherfused_gem",
            "empowered_remnant", "contained_remnant", "tampered_remnant");
    private static long started;
    private static int readyTicks;
    private static boolean finished;
    private static boolean uiAutomated;
    private static String waitingFor = "world and player";

    private SimplySwordsUniqueClientAudit() {}

    @SubscribeEvent
    public static void afterTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mite.simplySwordsUniqueClientAudit") || finished) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        JsonObject report = null;
        try {
            check(SimplySwordsCompat.enabled(), "This client audit requires Simply Swords");
            check(ModList.get().isLoaded("jei"), "This client audit requires the actual JEI mod");
            if (System.nanoTime() - started > TIMEOUT_NANOS)
                throw new IllegalStateException("Timed out after 180 seconds waiting for " + waitingFor);
            if (minecraft.getOverlay() == null && minecraft.screen instanceof AccessibilityOnboardingScreen onboarding) {
                uiAutomated = true;
                onboarding.onClose();
                return;
            }
            // This opt-in audit runs in a disposable copy; let Minecraft make its normal backup before loading it.
            if (minecraft.getOverlay() == null && minecraft.screen instanceof BackupConfirmScreen backup) {
                for (var child : backup.children()) if (child instanceof Button button
                        && button.getMessage().equals(Component.translatable("selectWorld.backupJoinConfirmButton"))) {
                    uiAutomated = true;
                    button.onPress();
                    return;
                }
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.gameMode == null) {
                waitingFor = "world and player";
                readyTicks = 0;
                return;
            }
            if (minecraft.getOverlay() != null) {
                waitingFor = "resource-loading overlay to close";
                readyTicks = 0;
                return;
            }
            // In JEI 19.57, publication follows every plugin's onRuntimeAvailable callback.
            Optional<?> runtime = (Optional<?>)api("mezz.jei.common.Internal")
                    .getMethod("getOptionalJeiRuntime").invoke(null);
            if (runtime.isEmpty()) {
                waitingFor = "JEI runtime publication after plugin callbacks";
                readyTicks = 0;
                return;
            }
            if (++readyTicks < 10) {
                waitingFor = "10 stable client ticks after JEI publication";
                return;
            }
            report = report(minecraft);
            check(minecraft.level.getRecipeManager().byKey(ResourceLocation.parse("simplyswords:unique_melting")).isEmpty(),
                    "Unique melting must not reintroduce disabled weapons into material recipe pages");
            JsonArray failures = report.getAsJsonArray("failures");
            Class<?> uniqueType = api("net.sweenus.simplyswords.item.UniqueWeaponItem");
            List<Item> uniques = BuiltInRegistries.ITEM.stream()
                    .filter(item -> id(item).getNamespace().equals("simplyswords") && uniqueType.isInstance(item))
                    .sorted(Comparator.comparing(item -> id(item).toString())).toList();
            check(!uniques.isEmpty(), "The real Simply Swords UniqueWeaponItem registry must be nonempty");
            check(uniques.contains(required("simplyswords", "stormbringer")), "Screenshot weapon Stormbringer belongs to the unique registry");
            report.addProperty("unique_weapon_count", uniques.size());
            report.add("unique_weapon_ids", ids(uniques));
            List<Item> diamonds = new ArrayList<>();
            for (String family : FAMILIES) diamonds.add(required("simplyswords", "diamond_" + family));
            report.addProperty("diamond_weapon_count", diamonds.size());
            report.add("diamond_weapon_ids", ids(diamonds));
            List<Item> retired = new ArrayList<>(uniques);
            retired.addAll(diamonds);
            report.addProperty("retired_weapon_count", retired.size());
            List<Item> retained = retainedItems();
            report.addProperty("retained_weapon_count", FAMILIES.size() * (3 + ADDED_MATERIALS.size()));
            report.addProperty("retained_material_count", MATERIAL_ITEMS.size());
            report.add("retained_item_ids", ids(retained));
            auditCreative(minecraft, retired, retained, report, failures);
            auditJei(runtime.orElseThrow(), retired, retained, report, failures);
            report.addProperty("status", failures.isEmpty() ? "passed" : "failed");
        } catch (Throwable failure) {
            if (report == null) report = report(minecraft);
            if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null)
                failure = invocation.getCause();
            report.getAsJsonArray("failures").add(failure.toString());
            report.addProperty("status", "failed");
            Mite.LOGGER.error("Simply Swords unique client audit failed", failure);
        }
        finished = true;
        report.addProperty("safe_client_shutdown_requested", true);
        try {
            Path output = Path.of(System.getProperty("mite.simplySwordsUniqueClientAuditOutput",
                    "build/verification/simplyswords-unique-client-audit.json")).toAbsolutePath().normalize();
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
            Mite.LOGGER.info("Simply Swords unique client audit {}: {}", report.get("status"), output);
        } catch (Exception failure) {
            Mite.LOGGER.error("Cannot save Simply Swords unique client audit", failure);
        } finally {
            minecraft.stop();
        }
    }

    private static JsonObject report(Minecraft minecraft) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("scope", "actual loaded client creative category/search collections and JEI ingredient manager/sidebar filter; no mocked runtime or pixel-render claim");
        report.addProperty("world_loaded", minecraft.level != null);
        report.addProperty("simply_swords_loaded", SimplySwordsCompat.enabled());
        report.addProperty("jei_loaded", ModList.get().isLoaded("jei"));
        report.addProperty("elapsed_seconds", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started));
        report.addProperty("last_wait_condition", waitingFor);
        report.addProperty("screen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        report.addProperty("actual_jei_runtime", false);
        report.addProperty("game_ui_automated", uiAutomated);
        report.addProperty("visual_render_verified", false);
        report.add("failures", new JsonArray());
        return report;
    }

    private static List<Item> retainedItems() {
        List<Item> result = new ArrayList<>();
        for (String material : List.of("iron", "gold", "runic"))
            for (String family : FAMILIES) result.add(required("simplyswords", material + "_" + family));
        for (String material : ADDED_MATERIALS)
            for (String family : FAMILIES) result.add(required("mite", "simplyswords_" + material + "_" + family));
        for (String path : MATERIAL_ITEMS) result.add(required("simplyswords", path));
        return List.copyOf(result);
    }

    private static void auditCreative(Minecraft minecraft, List<Item> retired, List<Item> retained,
            JsonObject report, JsonArray failures) {
        JsonArray builds = new JsonArray();
        report.add("creative_builds", builds);
        for (boolean permissions : new boolean[]{false, true}) {
            CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(), permissions, minecraft.level.registryAccess());
            JsonObject build = new JsonObject();
            build.addProperty("operator_permissions", permissions);
            JsonArray tabs = new JsonArray();
            build.add("tabs", tabs);
            Set<Item> categories = new HashSet<>();
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                Set<Item> displayed = items(tab.getDisplayItems());
                Set<Item> searchable = items(tab.getSearchTabDisplayItems());
                JsonObject fact = new JsonObject();
                fact.addProperty("tab", String.valueOf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab)));
                fact.addProperty("type", tab.getType().name());
                fact.addProperty("displayed_stack_count", tab.getDisplayItems().size());
                fact.addProperty("search_stack_count", tab.getSearchTabDisplayItems().size());
                tabs.add(fact);
                for (Item item : retired) {
                    expect(!displayed.contains(item), failures, "Retired weapon visible in creative tab " + fact.get("tab") + ": " + id(item));
                    expect(!searchable.contains(item), failures, "Retired weapon visible in creative search entries " + fact.get("tab") + ": " + id(item));
                }
                if (tab.getType() == CreativeModeTab.Type.CATEGORY) categories.addAll(displayed);
            }
            Set<Item> globalSearch = items(CreativeModeTabs.searchTab().getDisplayItems());
            for (Item item : retained) {
                expect(categories.contains(item), failures, "Retained item missing from creative categories (permissions=" + permissions + "): " + id(item));
                expect(globalSearch.contains(item), failures, "Retained item missing from global creative search (permissions=" + permissions + "): " + id(item));
            }
            build.addProperty("checked_retired_weapon_count", retired.size());
            build.addProperty("checked_retained_count", retained.size());
        }
    }

    private static void auditJei(Object runtime, List<Item> retired, List<Item> retained,
            JsonObject report, JsonArray failures) throws ReflectiveOperationException {
        Class<?> runtimeApi = api("mezz.jei.api.runtime.IJeiRuntime");
        Object manager = runtimeApi.getMethod("getIngredientManager").invoke(runtime);
        Object filter = runtimeApi.getMethod("getIngredientFilter").invoke(runtime);
        Class<?> managerApi = api("mezz.jei.api.runtime.IIngredientManager");
        Class<?> filterApi = api("mezz.jei.api.runtime.IIngredientFilter");
        Set<Item> registered = reflectedItems(managerApi.getMethod("getAllItemStacks").invoke(manager));
        String originalText = (String)filterApi.getMethod("getFilterText").invoke(filter);
        Set<Item> filtered;
        try {
            filterApi.getMethod("setFilterText", String.class).invoke(filter, "");
            filtered = reflectedItems(filterApi.getMethod("getFilteredItemStacks").invoke(filter));
        } finally {
            filterApi.getMethod("setFilterText", String.class).invoke(filter, originalText);
        }
        report.addProperty("actual_jei_runtime", true);
        report.addProperty("jei_runtime_class", runtime.getClass().getName());
        report.addProperty("jei_ingredient_manager_class", manager.getClass().getName());
        report.addProperty("jei_ingredient_filter_class", filter.getClass().getName());
        report.addProperty("jei_registered_item_id_count", registered.size());
        report.addProperty("jei_unfiltered_sidebar_item_id_count", filtered.size());
        report.addProperty("jei_original_filter_text_restored", originalText.equals(filterApi.getMethod("getFilterText").invoke(filter)));
        for (Item item : retired) {
            expect(!registered.contains(item), failures, "Retired weapon remains registered in actual JEI ingredient manager: " + id(item));
            expect(!filtered.contains(item), failures, "Retired weapon visible in actual JEI unfiltered sidebar: " + id(item));
        }
        for (Item item : retained) {
            expect(registered.contains(item), failures, "Retained item missing from actual JEI ingredient manager: " + id(item));
            expect(filtered.contains(item), failures, "Retained item missing from actual JEI unfiltered sidebar: " + id(item));
        }
    }

    private static Set<Item> reflectedItems(Object value) {
        check(value instanceof Collection<?>, "Actual JEI item stack operation returns a collection");
        Set<Item> result = new HashSet<>();
        for (Object entry : (Collection<?>)value) {
            check(entry instanceof ItemStack, "Actual JEI ingredient is an ItemStack");
            if (!((ItemStack)entry).isEmpty()) result.add(((ItemStack)entry).getItem());
        }
        return result;
    }

    private static Set<Item> items(Collection<ItemStack> stacks) {
        Set<Item> result = new HashSet<>();
        for (ItemStack stack : stacks) if (!stack.isEmpty()) result.add(stack.getItem());
        return result;
    }

    private static Item required(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        check(item != Items.AIR, "Expected registry identity is present: " + id);
        return item;
    }

    private static JsonArray ids(List<Item> items) {
        JsonArray result = new JsonArray();
        items.stream().map(item -> id(item).toString()).sorted().forEach(result::add);
        return result;
    }

    private static ResourceLocation id(Item item) { return BuiltInRegistries.ITEM.getKey(item); }
    private static Class<?> api(String name) throws ClassNotFoundException {
        return Class.forName(name, true, SimplySwordsUniqueClientAudit.class.getClassLoader());
    }
    private static void expect(boolean condition, JsonArray failures, String message) { if (!condition) failures.add(message); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
