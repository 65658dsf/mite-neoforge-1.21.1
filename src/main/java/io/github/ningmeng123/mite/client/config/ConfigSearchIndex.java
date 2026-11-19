package io.github.ningmeng123.mite.client.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.config.EnchantmentCatalog;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.config.MonsterSpawnCatalog;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.worldgen.R196OreFeature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Search metadata comes from config definitions and the same registries as the editors. @author NingMeng123 */
public final class ConfigSearchIndex {
    public record Entry(String id, Component label, Component category, Component description,
                        boolean available, Supplier<Screen> open) {
        public boolean matches(String query) {
            return ConfigSearchQuery.matches(query, id, label.getString(), category.getString(), description.getString());
        }
    }

    private static final Map<String, Function<Screen, List<Entry>>> PROVIDERS = new LinkedHashMap<>();
    private ConfigSearchIndex() {}

    /** Optional addons can preserve their own editing and server-authority rules. */
    public static void registerProvider(String modId, Function<Screen, List<Entry>> provider) {
        PROVIDERS.put(modId, provider);
    }

    public static List<Entry> entries(Screen parent) {
        var result = new ArrayList<>(configurationEntries(Mite.MOD_ID, true,
                (config, path) -> openMite(parent, config, path)));
        // Without an updated addon provider, its registered settings remain searchable locally.
        if (!PROVIDERS.containsKey("modern_ite")) result.addAll(configurationEntries("modern_ite",
                MiteConfigClient.localEditable(), (config, path) -> SingleConfigValueScreen.create(parent, config, path,
                        label("modern_ite", path, ((ModConfigSpec)config.getSpec()).getSpec().get(path)))));
        PROVIDERS.values().forEach(provider -> result.addAll(provider.apply(parent)));
        addEnchantments(result, parent);
        addMonsters(result, parent);
        result.sort(Comparator.comparing(entry -> entry.category().getString() + " " + entry.label().getString() + " " + entry.id()));
        return List.copyOf(result);
    }

    /** Every declared leaf is indexed, including advanced values hidden by category landing pages. */
    public static List<Entry> configurationEntries(String modId, boolean available,
            BiFunction<ModConfig, List<String>, Screen> open) {
        var result = new ArrayList<Entry>();
        for (var type : ModConfig.Type.values()) for (var config : ModConfigs.getConfigSet(type)) {
            if (config.getModId().equals(modId) && config.getSpec() instanceof ModConfigSpec spec)
                collect(result, config, spec.getValues(), new ArrayList<>(), available, open);
        }
        return List.copyOf(result);
    }

    private static void collect(List<Entry> result, ModConfig config, UnmodifiableConfig values, List<String> prefix,
            boolean available, BiFunction<ModConfig, List<String>, Screen> open) {
        var spec = (ModConfigSpec)config.getSpec();
        for (var value : values.entrySet()) {
            var path = new ArrayList<>(prefix);
            path.add(value.getKey());
            if (value.getValue() instanceof UnmodifiableConfig section) collect(result, config, section, path, available, open);
            else if (value.getValue() instanceof ModConfigSpec.ConfigValue<?>) {
                List<String> target = List.copyOf(path);
                if (config.getModId().equals(Mite.MOD_ID) && oreDimension(target.getFirst()) == Dimension.NETHER
                        && ((available && !MiteConfigClient.netherOreSettingsAvailable())
                                || R196OreFeature.generationBlock(oreKind(target)) == Blocks.AIR)) continue;
                ModConfigSpec.ValueSpec definition = spec.getSpec().get(path);
                String translation = definition.getTranslationKey();
                Component description = translation != null && Language.getInstance().has(translation + ".tooltip")
                        ? Component.translatable(translation + ".tooltip")
                        : Component.literal(definition.getComment() == null ? "" : definition.getComment());
                boolean canOpen = available && (!config.getModId().equals(Mite.MOD_ID) || miteAvailable(target));
                result.add(new Entry(config.getModId() + ":" + String.join(".", path),
                        label(config.getModId(), target, definition), category(config.getModId(), target), description,
                        canOpen, () -> open.apply(config, target)));
            }
        }
    }

    private static Component label(String modId, List<String> path, ModConfigSpec.ValueSpec spec) {
        String key = path.getLast();
        if (modId.equals(Mite.MOD_ID) && oreDimension(path.getFirst()) != null) {
            var kind = oreKind(path);
            boolean deep = key.startsWith("deepslate_");
            Component name = R196OreFeature.generationBlock(kind, deep).getName();
            String translation = path.getFirst().endsWith("_ores") ? "mite.config.ore_multiplier" : spec.getTranslationKey();
            return name.copy().append(" · ").append(Component.translatable(translation));
        }
        String translation = spec.getTranslationKey();
        if (translation != null) return Component.translatable(translation);
        String fallback = modId + ".config." + key;
        return Language.getInstance().has(fallback) ? Component.translatable(fallback) : Component.literal(key);
    }

    private static Component category(String modId, List<String> path) {
        var parts = new ArrayList<String>();
        parts.add(modId.equals(Mite.MOD_ID) ? "MITE" : "ITE");
        for (String key : path.subList(0, path.size() - 1)) {
            String translation = modId.equals("modern_ite") ? modId + ".config.category." + key : "mite.config." + key;
            if (Mite.MOD_ID.equals(modId)) {
                if (key.equals("underworld_generation")) translation = "mite.config.underworld_ores";
                else if (Language.getInstance().has("mite.config.ore." + key)) translation = "mite.config.ore." + key;
            }
            parts.add(Language.getInstance().has(translation) ? Component.translatable(translation).getString() : key);
        }
        return Component.literal(String.join(" / ", parts));
    }

    private static boolean miteAvailable(List<String> path) {
        if (!MiteConfigClient.remoteServer()) return true;
        String section = path.getFirst(), key = path.getLast();
        if (section.endsWith("_generation") && (key.equals("candidates") || key.equals("chance_percent"))) return false;
        if (legacyDamage(key) || section.equals("monster_spawning") && !key.equals("rates") && !key.equals("weights")
                && !MiteExtensions.policy().spawningControls().isEmpty()) return false;
        if (section.equals("performance") || section.equals("combat") || section.equals("structures")
                || section.equals("monsters") && key.endsWith("_multiplier") && !key.equals("damage_growth_multiplier"))
            return MiteConfigClient.editable() && MiteConfigClient.gameplayValues().containsKey(String.join(".", path));
        return MiteConfigClient.editable();
    }

    private static Screen openMite(Screen parent, ModConfig config, List<String> path) {
        String section = path.getFirst(), key = path.getLast();
        Dimension oreDimension = oreDimension(section);
        if (oreDimension != null) {
            // Candidate/probability controls are advanced native values; the ore page edits rates and heights.
            if (key.equals("candidates") || key.equals("chance_percent"))
                return SingleConfigValueScreen.create(parent, config, path, label(Mite.MOD_ID, path,
                        ((ModConfigSpec)config.getSpec()).getSpec().get(path)));
            boolean deep = key.startsWith("deepslate_");
            String id = BuiltInRegistries.BLOCK.getKey(R196OreFeature.generationBlock(oreKind(path), deep)).toString();
            return new OreGenerationSettingsScreen(parent, oreDimension, id);
        }
        if (section.equals("enchantments")) return new EnchantmentSettingsScreen(parent);
        if (section.equals("monster_spawning")) {
            if (key.equals("rates") || key.equals("weights")) return monsterScreen(parent, "");
            if (!MiteExtensions.policy().spawningControls().isEmpty()) return SingleConfigValueScreen.create(parent, config, path,
                    label(Mite.MOD_ID, path, ((ModConfigSpec)config.getSpec()).getSpec().get(path)));
            return new MonsterSpawningRulesScreen(parent, key);
        }
        if (legacyDamage(key)) return SingleConfigValueScreen.create(parent, config, path,
                label(Mite.MOD_ID, path, ((ModConfigSpec)config.getSpec()).getSpec().get(path)));
        if (section.equals("monsters") && !key.endsWith("_multiplier"))
            return new MonsterGrowthSettingsScreen(parent, key);
        if (MiteConfigClient.remoteServer()) return new ServerGameplaySettingsScreen(parent, String.join(".", path));
        return SingleConfigValueScreen.create(parent, config, path, label(Mite.MOD_ID, path,
                ((ModConfigSpec)config.getSpec()).getSpec().get(path)));
    }

    private static Dimension oreDimension(String section) {
        return switch (section) {
            case "overworld_ores", "overworld_generation" -> Dimension.OVERWORLD;
            case "underworld_ores", "underworld_generation" -> Dimension.UNDERWORLD;
            case "nether_ores", "nether_generation" -> Dimension.NETHER;
            default -> null;
        };
    }

    private static R196OreRules.Kind oreKind(List<String> path) {
        String key = path.getFirst().endsWith("_ores") ? path.getLast() : path.get(1);
        return R196OreRules.Kind.valueOf(key.replaceFirst("^deepslate_", "").toUpperCase(Locale.ROOT));
    }

    private static boolean legacyDamage(String key) {
        return List.of("damage_growth_multiplier", "damage_daily_percent", "damage_formula", "damage_blood_moon_formula").contains(key);
    }

    private static void addEnchantments(List<Entry> result, Screen parent) {
        var known = new LinkedHashMap<String, Component>();
        var mc = Minecraft.getInstance();
        if (mc.level != null) EnchantmentCatalog.observe(mc.level.registryAccess());
        EnchantmentCatalog.entries().forEach(entry -> known.put(entry.id(), entry.name()));
        var snapshot = MiteConfig.effectiveSnapshot();
        var configured = new HashSet<>(snapshot.caps().keySet());
        configured.addAll(snapshot.disabled());
        configured.addAll(snapshot.enchantmentChances().keySet());
        configured.forEach(id -> known.putIfAbsent(id, Component.literal(id)));
        known.forEach((id, name) -> result.add(new Entry("enchantments:" + id, name,
                Component.translatable("mite.config.registry_enchantments"),
                Component.translatable("mite.config.registry_enchantments.tooltip"), true,
                () -> new EnchantmentSettingsScreen(parent, id))));
    }

    private static void addMonsters(List<Entry> result, Screen parent) {
        var mc = Minecraft.getInstance();
        var known = new LinkedHashMap<String, Component>();
        MonsterSpawnCatalog.entries(Mite.MOD_ID, mc.level == null ? RegistryAccess.EMPTY : mc.level.registryAccess())
                .forEach(entry -> known.put(entry.id(), entry.name()));
        var snapshot = MiteConfig.effectiveSnapshot();
        var configured = new HashSet<>(snapshot.monsterSpawnRates().keySet());
        configured.addAll(snapshot.monsterSpawnWeights().keySet());
        configured.stream().filter(id -> MonsterSpawnCatalog.ownedBy(Mite.MOD_ID, id))
                .forEach(id -> known.putIfAbsent(id, Component.literal(id)));
        known.forEach((id, name) -> result.add(new Entry("monster_spawning:" + id, name,
                Component.translatable("mite.config.monster_spawning"),
                Component.translatable("mite.config.monster_spawning.tooltip"), true, () -> monsterScreen(parent, id))));
    }

    private static MonsterSpawnSettingsScreen monsterScreen(Screen parent, String query) {
        var screen = new MonsterSpawnSettingsScreen(parent, Mite.MOD_ID,
                () -> MiteConfig.effectiveSnapshot().monsterSpawnRates(), () -> MiteConfig.effectiveSnapshot().monsterSpawnWeights(),
                (rates, weights) -> {
                    if (!MiteConfig.saveMonsterSpawnSettings(rates, weights)) throw new IllegalArgumentException("Invalid monster spawn settings");
                }, null, () -> MiteConfig.factorySnapshot().monsterSpawnRates(), () -> MiteConfig.factorySnapshot().monsterSpawnWeights());
        screen.setSearchQuery(query);
        return screen;
    }
}
