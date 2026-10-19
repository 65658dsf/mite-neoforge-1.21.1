package io.github.ningmeng123.mite.config;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.GrowthFormula;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Editable gameplay defaults; remote clients receive an authoritative snapshot. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class MiteConfig {
    public static final ModConfigSpec SPEC;
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> UNDERWORLD_ORES = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> UNDERWORLD_ORE_CANDIDATES = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> UNDERWORLD_ORE_CHANCES = new EnumMap<>(R196OreRules.Kind.class);
    public static final ModConfigSpec.DoubleValue MONSTER_GROWTH, MONSTER_HEALTH_GROWTH, MONSTER_DAMAGE_GROWTH;
    public static final ModConfigSpec.DoubleValue MONSTER_HEALTH_BASE, MONSTER_DAMAGE_BASE, MONSTER_SKELETON_REFERENCE;
    public static final ModConfigSpec.DoubleValue MONSTER_HEALTH_DAILY_PERCENT, MONSTER_DAMAGE_DAILY_PERCENT;
    public static final ModConfigSpec.ConfigValue<String> MONSTER_HEALTH_FORMULA, MONSTER_DAMAGE_FORMULA;
    public static final ModConfigSpec.ConfigValue<String> MONSTER_HEALTH_BLOOD_MOON_FORMULA, MONSTER_DAMAGE_BLOOD_MOON_FORMULA;
    public static final ModConfigSpec.DoubleValue WEAPON_REACH_MULTIPLIER, WEAPON_REACH_REDUCTION;
    public static final ModConfigSpec.BooleanValue HEIGHT_REACH_PENALTY, UNDERWORLD_TRIALS, OVERWORLD_TRIALS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_ENCHANTMENTS, ENCHANTMENT_MAX_LEVELS;
    private static volatile Snapshot local;
    private static volatile Snapshot remote;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.translation("mite.config.underworld_ores").comment("Underworld ore vein attempt multipliers. Only newly generated chunks are affected. 0 disables; 1 preserves MITE defaults.").push("underworld_ores");
        // This is the generator's source of truth, not a second list of mineral names maintained by the GUI.
        for (var kind : R196OreRules.UNDERWORLD) {
            String key = kind.name().toLowerCase(Locale.ROOT);
            UNDERWORLD_ORES.put(kind, builder.translation("mite.config.ore." + key)
                    .comment("Generation multiplier for " + key + "; not a percentage of existing blocks.")
                    .defineInRange(key, 1D, 0D, 100D));
        }
        builder.pop();
        builder.translation("mite.config.ore_generation").push("underworld_generation");
        for (var kind : R196OreRules.UNDERWORLD) {
            builder.push(kind.name().toLowerCase(Locale.ROOT));
            UNDERWORLD_ORE_CANDIDATES.put(kind, builder.translation("mite.config.ore_candidates")
                    .comment("Actual candidate vein attempts before the probability filter, per new chunk. The original generator supplies the default.")
                    .defineInRange("candidates", (double)R196OreRules.candidateCount(kind, R196OreRules.Dimension.UNDERWORLD), 0D, 100000D));
            UNDERWORLD_ORE_CHANCES.put(kind, builder.translation("mite.config.ore_chance")
                    .comment("Chance in percent that each candidate proceeds to a vein attempt. Terrain and vein size still determine placed blocks.")
                    .defineInRange("chance_percent", 100D * R196OreRules.candidateChance(R196OreRules.Dimension.UNDERWORLD), 0D, 100D));
            builder.pop();
        }
        builder.pop();
        builder.translation("mite.config.monsters").push("monsters");
        MONSTER_GROWTH = number(builder, "growth_multiplier", 1D, 100D, "Overall multiplier of the formula's rate variable; 0 disables rate-based growth in the default formula. Custom formulas may also use day or days directly. Existing monsters refresh periodically.");
        MONSTER_HEALTH_GROWTH = number(builder, "health_growth_multiplier", 1D, 100D, "Daily health growth multiplier, multiplied by the overall growth multiplier.");
        MONSTER_DAMAGE_GROWTH = number(builder, "damage_growth_multiplier", 1D, 100D, "Daily attack damage growth multiplier, multiplied by the overall growth multiplier.");
        MONSTER_HEALTH_BASE = number(builder, "health_base", 0D, 1024D, "Initial unscaled maximum health. 0 preserves each entity's native health, including independent modifiers.");
        MONSTER_DAMAGE_BASE = number(builder, "damage_base", 0D, 2048D, "Initial innate melee attack damage. 0 preserves each entity's native base; weapon bonuses are added afterward.");
        MONSTER_SKELETON_REFERENCE = number(builder, "skeleton_reference", 20D, 1024D, "Minimum initial health growth reference for skeletons. After each blood moon the actual settled value becomes the new reference.");
        MONSTER_HEALTH_DAILY_PERCENT = number(builder, "health_daily_percent", .5D, 100D, "Daily health growth in percent of the current blood-moon baseline: 0.5 means 0.5%, or rate=0.005 in the formula.");
        MONSTER_DAMAGE_DAILY_PERCENT = number(builder, "damage_daily_percent", .25D, 100D, "Daily innate attack growth in percent of the current blood-moon baseline; 0.25 means rate=0.0025.");
        MONSTER_HEALTH_FORMULA = builder.translation("mite.config.health_formula")
                .comment("Health within the current growth period. Variables: base, initial, reference, rate, days, day, blood_moons. The separate health_blood_moon_formula settles the new base/reference when a blood moon ends. Only validated arithmetic is accepted.")
                .define("health_formula", MonsterGrowthRules.DEFAULTS.healthFormula(), MiteConfig::validFormulaSyntax);
        MONSTER_DAMAGE_FORMULA = builder.translation("mite.config.damage_formula")
                .comment("Innate attack within the current growth period. The separate damage_blood_moon_formula settles the next period's base/reference. Saved and reloaded formulas are validated before publication.")
                .define("damage_formula", MonsterGrowthRules.DEFAULTS.damageFormula(), MiteConfig::validFormulaSyntax);
        MONSTER_HEALTH_BLOOD_MOON_FORMULA = builder.translation("mite.config.health_blood_moon_formula")
                .comment("Health baseline settlement when each blood moon ends (after days 32/64/96/160). The result becomes both base and reference for the next growth period; blue moons are skipped. Uses the same variables as health_formula. Empty inherits health_formula, preserving existing custom configurations.")
                .define("health_blood_moon_formula", "", MiteConfig::validOptionalFormulaSyntax);
        MONSTER_DAMAGE_BLOOD_MOON_FORMULA = builder.translation("mite.config.damage_blood_moon_formula")
                .comment("Innate attack baseline settlement when each blood moon ends. The result becomes both base and reference for the next growth period; blue moons are skipped. Empty inherits damage_formula, preserving existing custom configurations.")
                .define("damage_blood_moon_formula", "", MiteConfig::validOptionalFormulaSyntax);
        builder.pop();
        builder.translation("mite.config.combat").push("combat");
        WEAPON_REACH_MULTIPLIER = number(builder, "weapon_reach_multiplier", 1D, 10D, "Survival melee attack reach multiplier. Creative reach remains unchanged. Better Combat also has its own weapon reach limit.");
        WEAPON_REACH_REDUCTION = number(builder, "weapon_reach_reduction", 0D, 16D, "Subtract this many blocks after multiplying survival melee reach. Minimum resulting reach is 0.");
        HEIGHT_REACH_PENALTY = builder.translation("mite.config.height_reach_penalty").comment("Apply MITE target-height reach adjustments.").define("height_reach_penalty", true);
        builder.pop();
        builder.translation("mite.config.structures").push("structures");
        UNDERWORLD_TRIALS = builder.translation("mite.config.underworld_trials").comment("Allow new underworld trial dungeons. Existing structures are retained.").define("underworld_trials", true);
        OVERWORLD_TRIALS = builder.translation("mite.config.overworld_trials").comment("Allow new overworld trial chambers. Existing structures are retained.").define("overworld_trials", true);
        builder.pop();
        builder.translation("mite.config.enchantments").push("enchantments");
        DISABLED_ENCHANTMENTS = builder.translation("mite.config.disabled_enchantments")
                .comment("Disabled enchantment registry IDs. The registry browser fills these automatically. Existing item data is retained, but disabled effects and new acquisition are blocked.")
                .defineListAllowEmpty(List.of("disabled"), List::of, () -> "minecraft:sharpness", MiteConfig::validEnchantmentId);
        ENCHANTMENT_MAX_LEVELS = builder.translation("mite.config.enchantment_max_levels")
                .comment("Only explicit overrides: namespace:enchantment=maximum (1..255). Omitted IDs retain their original rules. Unknown IDs are retained for temporarily removed mods.")
                .defineListAllowEmpty(List.of("max_levels"), List::of, () -> "minecraft:sharpness=5", MiteConfig::validLevelOverride);
        builder.pop();
        SPEC = builder.build();
        // Publish defaults before configuration loading; workers never read ModConfigSpec's mutable caches.
        local = readLocalSnapshot();
    }

    private MiteConfig() {}

    private static ModConfigSpec.DoubleValue number(ModConfigSpec.Builder b, String key, double defaultValue, double maximum, String comment) {
        return b.translation("mite.config." + key).comment(comment).defineInRange(key, defaultValue, 0D, maximum);
    }

    private static <T> T value(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static boolean validEnchantmentId(Object input) {
        return input instanceof String id && id.contains(":") && ResourceLocation.tryParse(id) != null;
    }

    private static boolean validFormulaSyntax(Object input) {
        if (!(input instanceof String formula)) return false;
        try { GrowthFormula.compile(formula); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }

    private static boolean validOptionalFormulaSyntax(Object input) {
        return input instanceof String formula && (formula.isBlank() || validFormulaSyntax(formula));
    }

    private static String inheritedFormula(String bloodMoonFormula, String periodFormula) {
        return bloodMoonFormula.isBlank() ? periodFormula : bloodMoonFormula;
    }

    public static boolean validLevelOverride(Object input) {
        if (!(input instanceof String entry)) return false;
        int separator = entry.indexOf('=');
        if (separator < 0 || !validEnchantmentId(entry.substring(0, separator))) return false;
        try {
            int maximum = Integer.parseInt(entry.substring(separator + 1));
            return maximum >= 1 && maximum <= 255;
        } catch (NumberFormatException ignored) { return false; }
    }

    public static double underworldOreMultiplier(R196OreRules.Kind kind) {
        return effectiveSnapshot().ores().getOrDefault(kind.name(), 1D);
    }
    public static double underworldOreCandidates(R196OreRules.Kind kind) {
        var snapshot = effectiveSnapshot();
        return snapshot.oreCandidates().getOrDefault(kind.name(),
                (double)R196OreRules.candidateCount(kind, R196OreRules.Dimension.UNDERWORLD))
                * snapshot.ores().getOrDefault(kind.name(), 1D);
    }
    public static double underworldOrePassChance(R196OreRules.Kind kind) {
        return effectiveSnapshot().oreChances().getOrDefault(kind.name(),
                R196OreRules.candidateChance(R196OreRules.Dimension.UNDERWORLD));
    }
    public static MonsterGrowthRules.Settings monsterGrowthSettings() { return effectiveSnapshot().monsterSettings(); }
    public static double monsterGrowthMultiplier() { return effectiveSnapshot().growth(); }
    public static double monsterHealthGrowthMultiplier() { return effectiveSnapshot().healthGrowth(); }
    public static double monsterDamageGrowthMultiplier() { return effectiveSnapshot().damageGrowth(); }
    public static double weaponReachMultiplier() { return effectiveSnapshot().reachMultiplier(); }
    public static double weaponReachReduction() { return effectiveSnapshot().reachReduction(); }
    public static boolean heightReachPenaltyEnabled() { return effectiveSnapshot().heightPenalty(); }
    public static boolean underworldTrialsEnabled() { return effectiveSnapshot().underworldTrials(); }
    public static boolean overworldTrialsEnabled() { return effectiveSnapshot().overworldTrials(); }

    public static int enchantmentMaxLevel(String resourceId, int fallback) {
        return effectiveSnapshot().caps().getOrDefault(resourceId, fallback);
    }
    public static boolean enchantmentEnabled(String resourceId) {
        return !effectiveSnapshot().disabled().contains(resourceId);
    }

    /** Immutable state is also used by the read-only remote settings browser. */
    public record Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
            double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
            boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
            MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances) {
        public Snapshot {
            ores = Map.copyOf(ores);
            caps = Map.copyOf(caps);
            disabled = Set.copyOf(disabled);
            monsterSettings = monsterSettings == null ? MonsterGrowthRules.DEFAULTS : monsterSettings;
            oreCandidates = oreCandidates == null ? Map.of() : Map.copyOf(oreCandidates);
            oreChances = oreChances == null ? Map.of() : Map.copyOf(oreChances);
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, MonsterGrowthRules.DEFAULTS, Map.of(), Map.of());
        }
    }

    private static Snapshot readLocalSnapshot() {
        var ores = new HashMap<String, Double>();
        UNDERWORLD_ORES.forEach((kind, setting) -> ores.put(kind.name(), value(setting)));
        var candidates = new HashMap<String, Double>();
        var chances = new HashMap<String, Double>();
        UNDERWORLD_ORE_CANDIDATES.forEach((kind, setting) -> candidates.put(kind.name(), value(setting)));
        UNDERWORLD_ORE_CHANCES.forEach((kind, setting) -> chances.put(kind.name(), value(setting) / 100D));
        var caps = new HashMap<String, Integer>();
        for (String entry : value(ENCHANTMENT_MAX_LEVELS)) {
            if (validLevelOverride(entry)) {
                int split = entry.indexOf('=');
                caps.put(entry.substring(0, split), Integer.parseInt(entry.substring(split + 1)));
            }
        }
        String healthFormula = value(MONSTER_HEALTH_FORMULA), damageFormula = value(MONSTER_DAMAGE_FORMULA);
        var growth = new MonsterGrowthRules.Settings(value(MONSTER_HEALTH_BASE), value(MONSTER_DAMAGE_BASE),
                value(MONSTER_SKELETON_REFERENCE), value(MONSTER_HEALTH_DAILY_PERCENT) / 100D,
                value(MONSTER_DAMAGE_DAILY_PERCENT) / 100D, healthFormula, damageFormula,
                inheritedFormula(value(MONSTER_HEALTH_BLOOD_MOON_FORMULA), healthFormula),
                inheritedFormula(value(MONSTER_DAMAGE_BLOOD_MOON_FORMULA), damageFormula));
        double overall = value(MONSTER_GROWTH), healthMultiplier = value(MONSTER_HEALTH_GROWTH), damageMultiplier = value(MONSTER_DAMAGE_GROWTH);
        var validation = MonsterGrowthRules.validate(growth, overall * healthMultiplier, overall * damageMultiplier);
        if (!validation.valid()) {
            Mite.LOGGER.warn("Rejected monster growth configuration; retaining last valid formulas: {}", validation.message());
            growth = local == null ? MonsterGrowthRules.DEFAULTS : local.monsterSettings();
            overall = local == null ? 1D : local.growth();
            healthMultiplier = local == null ? 1D : local.healthGrowth();
            damageMultiplier = local == null ? 1D : local.damageGrowth();
        }
        return new Snapshot(ores, overall, healthMultiplier, damageMultiplier,
                value(WEAPON_REACH_MULTIPLIER), value(WEAPON_REACH_REDUCTION), value(HEIGHT_REACH_PENALTY),
                value(UNDERWORLD_TRIALS), value(OVERWORLD_TRIALS), caps, new HashSet<>(value(DISABLED_ENCHANTMENTS)), growth, candidates, chances);
    }
    public static Snapshot localSnapshot() { return local; }
    public static Snapshot effectiveSnapshot() {
        Snapshot received = remote;
        return received == null ? local : received;
    }
    public static void receiveServerSnapshot(Snapshot snapshot) { remote = snapshot; }
    public static void clearServerSnapshot() { remote = null; }

    public static void refreshEnchantmentRules() {
        // Retain the original entry point while publishing every setting as one immutable revision.
        local = readLocalSnapshot();
        io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments.rulesChanged();
    }

    public static void saveEnchantments(Map<String, Integer> caps, Set<String> disabled) {
        var entries = new ArrayList<String>();
        caps.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entries.add(entry.getKey() + "=" + entry.getValue()));
        ENCHANTMENT_MAX_LEVELS.set(entries);
        DISABLED_ENCHANTMENTS.set(disabled.stream().sorted().toList());
        refreshEnchantmentRules();
        SPEC.save();
    }

    public static GrowthFormula.Validation validateMonsterGrowth(MonsterGrowthRules.Settings settings) {
        var snapshot = effectiveSnapshot();
        return MonsterGrowthRules.validate(settings, snapshot.growth() * snapshot.healthGrowth(),
                snapshot.growth() * snapshot.damageGrowth());
    }

    public static GrowthFormula.Validation saveMonsterGrowth(MonsterGrowthRules.Settings settings) {
        var validation = validateMonsterGrowth(settings);
        if (!validation.valid()) return validation;
        MONSTER_HEALTH_BASE.set(settings.healthBase());
        MONSTER_DAMAGE_BASE.set(settings.damageBase());
        MONSTER_SKELETON_REFERENCE.set(settings.skeletonReference());
        MONSTER_HEALTH_DAILY_PERCENT.set(settings.healthRate() * 100D);
        MONSTER_DAMAGE_DAILY_PERCENT.set(settings.damageRate() * 100D);
        MONSTER_HEALTH_FORMULA.set(settings.healthFormula());
        MONSTER_DAMAGE_FORMULA.set(settings.damageFormula());
        MONSTER_HEALTH_BLOOD_MOON_FORMULA.set(settings.healthBloodMoonFormula());
        MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set(settings.damageBloodMoonFormula());
        refreshEnchantmentRules();
        SPEC.save();
        return validation;
    }

    public static boolean saveUnderworldOreGeneration(Map<R196OreRules.Kind, Double> candidates,
            Map<R196OreRules.Kind, Double> chancePercent) {
        for (var kind : R196OreRules.UNDERWORLD) {
            Double count = candidates.get(kind), chance = chancePercent.get(kind);
            if (count == null || chance == null || !Double.isFinite(count) || count < 0D || count > 100000D
                    || !Double.isFinite(chance) || chance < 0D || chance > 100D) return false;
        }
        for (var kind : R196OreRules.UNDERWORLD) {
            UNDERWORLD_ORE_CANDIDATES.get(kind).set(candidates.get(kind));
            UNDERWORLD_ORE_CHANCES.get(kind).set(chancePercent.get(kind));
            UNDERWORLD_ORES.get(kind).set(1D);
        }
        refreshEnchantmentRules();
        SPEC.save();
        return true;
    }

    @SubscribeEvent public static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) return;
        EnchantmentCatalog.entries();
        refreshEnchantmentRules();
        GameplayConfigSync.broadcast();
    }
}
