package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatBridge;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Real ItemStack tooltip dispatch on the client event bus without creating a world or window. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class StandaloneWeaponReachClientAudit implements DataProvider {
    private static final String REACH_KEY = "tooltip.mite.attack_reach";
    private static final TooltipFlag NO_KEYS = new TooltipFlag() {
        @Override public boolean isAdvanced() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean hasControlDown() { return false; }
        @Override public boolean hasShiftDown() { return false; }
        @Override public boolean hasAltDown() { return false; }
    };
    private final Path output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    private StandaloneWeaponReachClientAudit(Path output, CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.standaloneWeaponReachAudit")) return;
        event.getGenerator().addProvider(true, new StandaloneWeaponReachClientAudit(Path.of(System.getProperty(
                "mite.standaloneWeaponReachAuditOutput", "build/verification/standalone-weapon-reach-client-audit.json")),
                event.getLookupProvider()));
    }

    @Override public String getName() { return "Standalone weapon reach actual client item tooltip event path without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        var report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("simply_swords_loaded", SimplySwordsCompat.enabled());
        report.addProperty("better_combat_loaded", BetterCombatBridge.enabled());
        report.addProperty("minecraft_instance_created_by_audit", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("visual_appearance_tested", false);
        report.addProperty("manual_listener_registrations", 0);
        report.addProperty("tooltip_player_is_null", true);
        report.addProperty("scope", "ItemStack.getTooltipLines and automatically registered client event subscribers; no visual or interactive test");
        report.addProperty("headless_tooltip_limitations", "Audit copies hide additional item hover text and native attribute tooltip rendering because those unchanged paths require a Minecraft window or level; ItemStack.getTooltipLines still dispatches its real final ItemTooltipEvent and the independent MITE reach subscriber");
        report.addProperty("native_attribute_tooltip_rendering_tested", false);
        report.addProperty("native_attribute_modifiers_retained", true);
        var cases = new JsonArray();
        report.add("cases", cases);
        try {
            audit(registries.join(), report, cases);
            report.addProperty("status", "passed_actual_client_item_tooltip_path_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void audit(HolderLookup.Provider lookup, JsonObject report, JsonArray cases) throws Exception {
        Field shutdown = busField("shutdown");
        boolean originalShutdown = shutdown.getBoolean(NeoForge.EVENT_BUS);
        Map<?, ?> listeners = (Map<?, ?>)busField("listeners").get(NeoForge.EVENT_BUS);
        check(listeners.keySet().stream().anyMatch(target -> target instanceof Class<?> type
                        && type.getName().equals("io.github.ningmeng123.mite.client.CraftingTooltips")),
                "The actual client event bus automatically registered CraftingTooltips");
        report.addProperty("automatic_crafting_tooltip_subscriber_registered", true);
        report.addProperty("event_bus_original_shutdown", originalShutdown);
        var base = MiteConfig.effectiveSnapshot();
        var previousRemote = base == MiteConfig.localSnapshot() ? null : base;
        try {
            if (originalShutdown) NeoForge.EVENT_BUS.start();
            // Datagen does not load COMMON config files. Use the same immutable in-memory
            // snapshot boundary as an authoritative server, without reading ConfigValue.get().
            MiteConfig.receiveServerSnapshot(new MiteConfig.Snapshot(base.ores(), base.growth(), base.healthGrowth(),
                    base.damageGrowth(), 1D, 0D, base.heightPenalty(), base.underworldTrials(), base.overworldTrials(),
                    base.caps(), base.disabled(), base.monsterSettings(), base.oreCandidates(), base.oreChances(),
                    base.overworldOreCandidates(), base.overworldOreChances(), base.overworldOres(), base.enchantmentChances(),
                    base.monsterSpawnRates(), base.oreHeights(), base.overworldOreHeights(), base.spawningSettings(),
                    base.monsterSpawnWeights(), base.betterCombatMobs()));
            report.addProperty("neutral_reach_fixture_uses_memory_snapshot_only", true);
            boolean betterCombat = BetterCombatBridge.enabled();
            double swordReach = 2.25D;
            verifyNewAndSaved(new ItemStack(Items.IRON_SWORD), swordReach, "minecraft:iron_sword", lookup, cases);
            verifyNewAndSaved(new ItemStack(MiteTools.get("mite:copper_sword")), swordReach, "mite:copper_sword", lookup, cases);
            verifyNewAndSaved(new ItemStack(MiteTools.get("mite:copper_dagger")), 2D, "mite:copper_dagger", lookup, cases);
            verifyNewAndSaved(new ItemStack(MiteTools.get("mite:copper_war_hammer")), 2D, "mite:copper_war_hammer", lookup, cases);
            verifyNewAndSaved(new ItemStack(MiteTools.get("mite:copper_scythe")), betterCombat ? 3.25D : 2.5D, "mite:copper_scythe", lookup, cases);
            verifyNewAndSaved(new ItemStack(Items.TRIDENT), betterCombat ? 3.25D : 2.75D, "minecraft:trident", lookup, cases);
            verifyNewAndSaved(new ItemStack(Items.MACE), betterCombat ? 2D : 1.75D, "minecraft:mace", lookup, cases);
            report.addProperty("native_and_mite_weapons_checked", true);
            if (SimplySwordsCompat.enabled()) {
                String[] families = {"rapier", "glaive", "spear", "chakram", "longsword", "sai", "claymore", "greataxe", "greathammer"};
                double[] reaches = betterCombat
                        ? new double[]{2.1D, 3.5D, 3.75D, 2.8D, 2.5D, 2D, 3.25D, 3.25D, 3.25D}
                        : new double[]{2D, 2.75D, 3D, 2.8D, 2.5D, 2D, 3.25D, 3.25D, 3.25D};
                for (int index = 0; index < families.length; ++index) {
                    verifyNewAndSaved(new ItemStack(SimplySwordsCompat.weapon("copper", families[index])), reaches[index],
                            "simplyswords_copper_" + families[index], lookup, cases);
                }
                var rapier = new ItemStack(SimplySwordsCompat.weapon("copper", "rapier"));
                rapier.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
                verify(rapier, reaches[0], "simplyswords_copper_rapier", "hover_again", cases);
            }
            // Return to a short native weapon after unrelated/long hovered items, without any player state.
            verify(new ItemStack(Items.IRON_SWORD), swordReach, "minecraft:iron_sword", "hover_again", cases);
            report.addProperty("simply_swords_specific_cases_skipped", !SimplySwordsCompat.enabled());
            report.addProperty("actual_itemstack_get_tooltip_lines_called", true);
            report.addProperty("old_saved_components_preserved", true);
            report.addProperty("hovered_weapon_independent_of_other_weapon", true);
            report.addProperty("user_configuration_files_written", false);
        } finally {
            MiteConfig.clearServerSnapshot();
            if (previousRemote != null) MiteConfig.receiveServerSnapshot(previousRemote);
            report.addProperty("original_effective_config_snapshot_restored", MiteConfig.effectiveSnapshot() == base);
            shutdown.setBoolean(NeoForge.EVENT_BUS, originalShutdown);
            report.addProperty("event_bus_original_shutdown_restored", shutdown.getBoolean(NeoForge.EVENT_BUS) == originalShutdown);
        }
    }

    private static void verifyNewAndSaved(ItemStack stack, double expected, String name,
            HolderLookup.Provider lookup, JsonArray cases) {
        check(!stack.isEmpty(), "The actual weapon is registered: " + name);
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        verify(stack, expected, name, "new", cases);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stack.getAttributeModifiers());
        var saved = stack.save(lookup);
        var restored = ItemStack.parse(lookup, saved).orElseThrow();
        verify(restored, expected, name, "saved_explicit_components", cases);
        check(saved.equals(restored.save(lookup)), "Tooltip queries preserve every saved component");
    }

    private static void verify(ItemStack stack, double expected, String family, String state, JsonArray cases) {
        stack = stack.copy();
        stack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        // AttributeUtil.getSortedModifiers accesses Minecraft.level even with a null tooltip
        // player. Hide only its presentation on the audit copy; keep every real modifier.
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
                new ItemAttributeModifiers(stack.getAttributeModifiers().modifiers(), false));
        var before = stack.copy();
        List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.EMPTY, null, NO_KEYS);
        var reach = tooltip.stream().filter(line -> translated(line, REACH_KEY)).toList();
        check(reach.size() == 1, "The actual client ItemTooltipEvent appends effective melee reach exactly once in every integration environment");
        if (EquipmentDurability.isManaged(stack))
            check(tooltip.stream().anyMatch(line -> translated(line, "tooltip.mite.quality")),
                    "Managed equipment also retains its quality line through the real client event");
        var fact = new JsonObject();
        fact.addProperty("family", family);
        fact.addProperty("stack_state", state);
        fact.addProperty("expected_base_reach", expected);
        fact.addProperty("standalone_reach_line_count", reach.size());
        var text = (TranslatableContents)reach.getFirst().getContents();
        String formatted = ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(expected);
        check(text.getArgs().length == 1 && formatted.equals(text.getArgs()[0]),
                "The real reach tooltip carries the expected numeric argument for " + family);
        fact.addProperty("translation_key", text.getKey());
        fact.addProperty("numeric_argument", String.valueOf(text.getArgs()[0]));
        check(ItemStack.matches(stack, before), "Building a tooltip never mutates the hovered weapon");
        cases.add(fact);
    }

    private static boolean translated(Component component, String key) {
        return component.getContents() instanceof TranslatableContents text && text.getKey().equals(key);
    }

    private static Field busField(String name) throws ReflectiveOperationException {
        Field field = NeoForge.EVENT_BUS.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void write(JsonObject report) throws Exception {
        Path target = output.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
    }
}
