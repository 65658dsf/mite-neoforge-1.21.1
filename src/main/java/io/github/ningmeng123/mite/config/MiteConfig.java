package io.github.ningmeng123.mite.config;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.github.ningmeng123.mite.rules.GrowthFormula;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings;
import java.math.BigDecimal;
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
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> OVERWORLD_ORES = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> NETHER_ORES = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> UNDERWORLD_DEEPSLATE_ORES = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> OVERWORLD_DEEPSLATE_ORES = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> UNDERWORLD_ORE_CANDIDATES = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> UNDERWORLD_ORE_CHANCES = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> OVERWORLD_ORE_CANDIDATES = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> OVERWORLD_ORE_CHANCES = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.ConfigValue<String>> UNDERWORLD_ORE_HEIGHTS = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.ConfigValue<String>> OVERWORLD_ORE_HEIGHTS = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.ConfigValue<String>> NETHER_ORE_HEIGHTS = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.ConfigValue<String>> UNDERWORLD_DEEPSLATE_ORE_HEIGHTS = new EnumMap<>(R196OreRules.Kind.class);
    public static final Map<R196OreRules.Kind, ModConfigSpec.ConfigValue<String>> OVERWORLD_DEEPSLATE_ORE_HEIGHTS = new EnumMap<>(R196OreRules.Kind.class);
    public static final ModConfigSpec.DoubleValue MONSTER_GROWTH, MONSTER_HEALTH_GROWTH, MONSTER_DAMAGE_GROWTH;
    public static final ModConfigSpec.DoubleValue MONSTER_HEALTH_BASE, MONSTER_DAMAGE_BASE, MONSTER_SKELETON_REFERENCE;
    public static final ModConfigSpec.DoubleValue MONSTER_HEALTH_DAILY_PERCENT, MONSTER_DAMAGE_DAILY_PERCENT;
    private static final ModConfigSpec.BooleanValue DAMAGE_RATE_MIGRATED;
    public static final ModConfigSpec.ConfigValue<String> MONSTER_HEALTH_FORMULA, MONSTER_DAMAGE_FORMULA;
    public static final ModConfigSpec.ConfigValue<String> MONSTER_HEALTH_BLOOD_MOON_FORMULA, MONSTER_DAMAGE_BLOOD_MOON_FORMULA;
    public static final ModConfigSpec.DoubleValue WEAPON_REACH_MULTIPLIER, WEAPON_REACH_REDUCTION;
    public static final ModConfigSpec.DoubleValue BETTER_COMBAT_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue HEIGHT_REACH_PENALTY, UNDERWORLD_TRIALS, OVERWORLD_TRIALS;
    public static final ModConfigSpec.BooleanValue BETTER_COMBAT_MOBS;
    public static final ModConfigSpec.BooleanValue ASYNC_PATHFINDING, AI_LOAD_REDUCTION;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_ENCHANTMENTS, ENCHANTMENT_MAX_LEVELS, ENCHANTMENT_CHANCES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NATURAL_SPAWN_RATES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NATURAL_SPAWN_WEIGHTS;
    public static final Map<MonsterSpawningSettings.Option, ModConfigSpec.ConfigValue<? extends Number>> SPAWNING_CONTROLS =
            new EnumMap<>(MonsterSpawningSettings.Option.class);
    private static final Snapshot FACTORY;
    private static volatile Snapshot local;
    private static volatile Snapshot remote;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.translation("mite.config.underworld_ores").comment("Underworld ore vein attempt multipliers. Only newly generated chunks are affected. 0 disables; 1 preserves MITE defaults.").push("underworld_ores");
        // This is the generator's source of truth, not a second list of mineral names maintained by the GUI.
        for (var kind : R196OreRules.configurableKinds(R196OreRules.Dimension.UNDERWORLD)) {
            String key = kind.name().toLowerCase(Locale.ROOT);
            UNDERWORLD_ORES.put(kind, builder.translation("mite.config.ore." + key)
                    .comment("Generation multiplier for " + key + "; not a percentage of existing blocks.")
                    .defineInRange(key, BundledGameplayDefaults.number("underworld_ores." + key, 1D), 0D, 100D));
        }
        defineDeepslateMultipliers(builder, R196OreRules.Dimension.UNDERWORLD, UNDERWORLD_DEEPSLATE_ORES);
        builder.pop();
        builder.translation("mite.config.overworld_ores").comment("Overworld mineral generation multipliers. 1 preserves the original rate, 2 doubles attempts, 0.5 halves them, and 0 disables. Existing advanced candidate/probability values are preserved.").push("overworld_ores");
        for (var kind : R196OreRules.configurableKinds(R196OreRules.Dimension.OVERWORLD)) {
            String key = kind.name().toLowerCase(Locale.ROOT);
            OVERWORLD_ORES.put(kind, builder.translation("mite.config.ore." + key)
                    .comment("Candidate count multiplier for overworld " + key + "; applied once before probability filtering.")
                    .defineInRange(key, BundledGameplayDefaults.number("overworld_ores." + key, 1D), 0D, 100D));
        }
        defineDeepslateMultipliers(builder, R196OreRules.Dimension.OVERWORLD, OVERWORLD_DEEPSLATE_ORES);
        builder.pop();
        builder.translation("mite.config.nether_ores")
                .comment("Nether ore vein attempt multipliers. 1 preserves the original generator and 0 disables generation in new chunks. Nether adamantium uses the active Modern ITE count when the addon is installed.")
                .push("nether_ores");
        for (var kind : R196OreRules.configurableKinds(R196OreRules.Dimension.NETHER)) {
            String key = kind.name().toLowerCase(Locale.ROOT);
            NETHER_ORES.put(kind, builder.translation("mite.config.ore." + key)
                    .defineInRange(key, 1D, 0D, 100D));
        }
        builder.pop();
        builder.translation("mite.config.ore_generation").push("underworld_generation");
        for (var kind : R196OreRules.configurableKinds(R196OreRules.Dimension.UNDERWORLD)) {
            builder.push(kind.name().toLowerCase(Locale.ROOT));
            UNDERWORLD_ORE_CANDIDATES.put(kind, builder.translation("mite.config.ore_candidates")
                    .comment("Actual candidate vein attempts before the probability filter, per new chunk. The original generator supplies the default.")
                    .defineInRange("candidates", (double)R196OreRules.configuredCandidateCount(kind, R196OreRules.Dimension.UNDERWORLD), 0D, 100000D));
            UNDERWORLD_ORE_CHANCES.put(kind, builder.translation("mite.config.ore_chance")
                    .comment("Candidate pass chance in percent. Modern underworld emeralds use 3..8 singleton attempts at 100%; other ores default to 10%.")
                    .defineInRange("chance_percent", 100D * R196OreRules.configuredCandidateChance(kind, R196OreRules.Dimension.UNDERWORLD), 0D, 100D));
            defineOreHeights(builder, kind, R196OreRules.Dimension.UNDERWORLD, UNDERWORLD_ORE_HEIGHTS, UNDERWORLD_DEEPSLATE_ORE_HEIGHTS);
            builder.pop();
        }
        builder.pop();
        builder.translation("mite.config.overworld_generation").push("overworld_generation");
        for (var kind : R196OreRules.configurableKinds(R196OreRules.Dimension.OVERWORLD)) {
            builder.push(kind.name().toLowerCase(Locale.ROOT));
            OVERWORLD_ORE_CANDIDATES.put(kind, builder.translation("mite.config.ore_candidates")
                    .comment("Actual candidate vein attempts per new overworld chunk, before chance_percent; not a relative weight. Surface adamantium defaults to 0, preserving its original absence.")
                    .defineInRange("candidates", (double)R196OreRules.configuredCandidateCount(kind, R196OreRules.Dimension.OVERWORLD), 0D, 100000D));
            OVERWORLD_ORE_CHANCES.put(kind, builder.translation("mite.config.ore_chance")
                    .comment("Complete candidate pass chance in percent. Emerald defaults to 100; other minerals default to 10. No second rarity roll is applied.")
                    .defineInRange("chance_percent", 100D * R196OreRules.configuredCandidateChance(kind, R196OreRules.Dimension.OVERWORLD), 0D, 100D));
            defineOreHeights(builder, kind, R196OreRules.Dimension.OVERWORLD, OVERWORLD_ORE_HEIGHTS, OVERWORLD_DEEPSLATE_ORE_HEIGHTS);
            builder.pop();
        }
        builder.pop();
        builder.translation("mite.config.nether_generation").push("nether_generation");
        for (var kind : R196OreRules.configurableKinds(R196OreRules.Dimension.NETHER)) {
            builder.push(kind.name().toLowerCase(Locale.ROOT));
            defineOreHeights(builder, kind, R196OreRules.Dimension.NETHER, NETHER_ORE_HEIGHTS, Map.of());
            builder.pop();
        }
        builder.pop();
        builder.translation("mite.config.monsters").push("monsters");
        MONSTER_GROWTH = number(builder, "growth_multiplier", 1D, 100D, "Overall multiplier of the formula's rate variable; 0 disables rate-based growth in the default formula. Custom formulas may also use day or days directly. Existing monsters refresh periodically.");
        MONSTER_HEALTH_GROWTH = number(builder, "health_growth_multiplier", 1D, 100D, "Daily health growth multiplier, multiplied by the overall growth multiplier.");
        MONSTER_DAMAGE_GROWTH = number(builder, "damage_growth_multiplier", 1D, 100D, "Daily attack damage growth multiplier, multiplied by the overall growth multiplier.");
        MONSTER_HEALTH_BASE = number(builder, "health_base", 20D, 1024D, "First-period health growth reference shared by all monsters. 0 uses the default 20. Each entity retains its native initial health, including independent modifiers. Later blood-moon periods reference actual settled health.");
        MONSTER_DAMAGE_BASE = number(builder, "damage_base", 0D, 2048D, "Initial innate melee attack damage. 0 uses the entity's native base. Weapon bonuses are added after growth.");
        MONSTER_SKELETON_REFERENCE = number(builder, "skeleton_reference", 20D, 1024D, "Legacy compatibility value. All monsters now share health_base as their first-period growth reference.");
        MONSTER_HEALTH_DAILY_PERCENT = number(builder, "health_daily_percent", .5D, 100D, "Daily health growth in percent of the current blood-moon baseline: 0.5 means 0.5%, or rate=0.005 in the formula.");
        MONSTER_DAMAGE_DAILY_PERCENT = number(builder, "damage_daily_percent", MonsterGrowthRules.DEFAULTS.damageRate() * 100D, 100D, "Daily innate attack growth as a percentage of the current period's reference attack. 0.5 means rate=0.005.");
        DAMAGE_RATE_MIGRATED = builder.comment("Set automatically after upgrading the old default daily attack rate.")
                .define("damage_rate_migrated", false);
        MONSTER_HEALTH_FORMULA = builder.translation("mite.config.health_formula")
                .comment("Health within the current growth period. Variables: base, initial, reference, rate, days, day, blood_moons. The separate health_blood_moon_formula settles the new base/reference when a blood moon ends. Only validated arithmetic is accepted.")
                .define("health_formula", MonsterGrowthRules.DEFAULTS.healthFormula(), MiteConfig::validFormulaSyntax);
        MONSTER_DAMAGE_FORMULA = builder.translation("mite.config.damage_formula")
                .comment("Innate attack within the current growth period. The separate damage_blood_moon_formula settles the next period's base and reference.")
                .define("damage_formula", MonsterGrowthRules.DEFAULTS.damageFormula(), MiteConfig::validFormulaSyntax);
        MONSTER_HEALTH_BLOOD_MOON_FORMULA = builder.translation("mite.config.health_blood_moon_formula")
                .comment("Health baseline settlement when each blood moon ends (after days 32/64/96/160). The result becomes both base and reference for the next growth period; blue moons are skipped. Uses the same variables as health_formula. Empty inherits health_formula, preserving existing custom configurations.")
                .define("health_blood_moon_formula", BundledGameplayDefaults.text("monsters.health_blood_moon_formula", ""), MiteConfig::validOptionalFormulaSyntax);
        MONSTER_DAMAGE_BLOOD_MOON_FORMULA = builder.translation("mite.config.damage_blood_moon_formula")
                .comment("Innate attack baseline settlement when each blood moon ends. The result becomes the next period's base and reference. Empty inherits damage_formula.")
                .define("damage_blood_moon_formula", BundledGameplayDefaults.text("monsters.damage_blood_moon_formula", ""), MiteConfig::validOptionalFormulaSyntax);
        builder.pop();
        builder.translation("mite.config.monster_spawning").push("monster_spawning");
        NATURAL_SPAWN_RATES = builder.translation("mite.config.natural_spawn_rates")
                .comment("Natural spawn weight overrides: namespace:entity=multiplier (0..100). 1 preserves the original weight; 0 disables natural spawning of that entity. Unknown IDs are retained. Existing biome, date, depth, population and spawn rules still apply. ITE-owned entities use the ITE configuration instead.")
                .defineListAllowEmpty(List.of("rates"), List::of, () -> "minecraft:zombie=1.0", MiteConfig::validMonsterSpawnOverride);
        NATURAL_SPAWN_WEIGHTS = builder.translation("mite.config.natural_spawn_weights")
                .comment("Absolute natural pool weight overrides: namespace:entity=number (0..1000000, decimals allowed). Omitted IDs retain each pool's original weight; overrides replace the weight in every existing candidate pool, before rate multipliers. 0 removes that candidate family, including its conversion branches. Does not add spawn entries or bypass date, biome, dimension or population rules. ITE-owned entities use ITE settings.")
                .defineListAllowEmpty(List.of("weights"), () -> BundledGameplayDefaults.list("monster_spawning.weights"), () -> "minecraft:zombie=100", MiteConfig::validMonsterSpawnWeightOverride);
        for (var option : MonsterSpawningSettings.Option.values()) {
            builder.translation("mite.config.spawning_rules." + option.key())
                    .comment("reference_rules=1 (default) restores R196 natural caps, crowd guards and timing; 0 enables the saved custom controls. Reference mode retains but ignores custom numbers. Custom caps are per 289 chunks; day 1 has no daily increase; maximum_cap=0 means unlimited growth. Interval is in ticks (20 ticks/second); chances are percentages. ITE can supply its own preset.");
            SPAWNING_CONTROLS.put(option, option.wholeNumber()
                    ? builder.defineInRange(option.key(), (int)factorySpawningDefault(option), (int)option.minimum(), (int)option.maximum())
                    : builder.defineInRange(option.key(), factorySpawningDefault(option), option.minimum(), option.maximum()));
        }
        builder.pop();
        builder.translation("mite.config.performance").push("performance");
        ASYNC_PATHFINDING = builder.translation("mite.config.async_pathfinding").comment("Spread supported stationary hostile melee searches across server ticks. Native world and evaluator hooks stay on the server thread; A* bookkeeping runs on bounded workers. Delays path decisions to reduce tick spikes.")
                .define("async_pathfinding", true);
        AI_LOAD_REDUCTION = builder.translation("mite.config.ai_load_reduction").comment("Limit and stagger expensive AI searches across server ticks. Animals resume food/water scans in batches; hostile mobs queue synchronous decisions and retry impossible routes less often. Preserves immediate attacks and real danger responses, but delays some decisions and reduces hostile search node budgets. False restores the previous search frequency and node limits.")
                .define("ai_load_reduction", true);
        builder.pop();
        builder.translation("mite.config.combat").push("combat");
        WEAPON_REACH_MULTIPLIER = number(builder, "weapon_reach_multiplier", 1D, 10D, "Multiplier of balanced melee reach before the fixed reduction. MITE and Simply Swords select separate family ranges according to Better Combat availability.");
        WEAPON_REACH_REDUCTION = number(builder, "weapon_reach_reduction", 0D, 16D, "Subtract this many blocks after multiplying survival melee reach. Minimum resulting reach is 0.");
        HEIGHT_REACH_PENALTY = builder.translation("mite.config.height_reach_penalty").comment("Apply MITE target-height reach adjustments.").define("height_reach_penalty", true);
        BETTER_COMBAT_DAMAGE_MULTIPLIER = number(builder, "better_combat_damage_multiplier",
                BundledGameplayDefaults.number("combat.better_combat_damage_multiplier", .8D), 10D,
                "Damage multiplier for player melee attacks performed through Better Combat.");
        BETTER_COMBAT_MOBS = builder.translation("mite.config.better_combat_mobs")
                .comment("Enable Better Combat weapon attacks and animations for hostile mobs when Better Combat is installed. False preserves their original attacks. Does not affect player compatibility.")
                .define("better_combat_mobs", false);
        builder.pop();
        builder.translation("mite.config.structures").push("structures");
        UNDERWORLD_TRIALS = builder.translation("mite.config.underworld_trials").comment("Allow new underworld trial dungeons. Existing structures are retained.").define("underworld_trials", true);
        OVERWORLD_TRIALS = builder.translation("mite.config.overworld_trials").comment("Allow new overworld trial chambers. Existing structures are retained.").define("overworld_trials", true);
        builder.pop();
        builder.translation("mite.config.enchantments").push("enchantments");
        DISABLED_ENCHANTMENTS = builder.translation("mite.config.disabled_enchantments")
                .comment("Disabled enchantment registry IDs. The registry browser fills these automatically. Existing item data is retained, but disabled effects and new acquisition are blocked.")
                .defineListAllowEmpty(List.of("disabled"), () -> BundledGameplayDefaults.list("enchantments.disabled"), () -> "minecraft:sharpness", MiteConfig::validEnchantmentId);
        ENCHANTMENT_MAX_LEVELS = builder.translation("mite.config.enchantment_max_levels")
                .comment("Only explicit overrides: namespace:enchantment=maximum (1..255). Omitted IDs retain their original rules. Unknown IDs are retained for temporarily removed mods.")
                .defineListAllowEmpty(List.of("max_levels"), () -> BundledGameplayDefaults.list("enchantments.max_levels"), () -> "minecraft:sharpness=5", MiteConfig::validLevelOverride);
        ENCHANTMENT_CHANCES = builder.translation("mite.config.enchantment_chances")
                .comment("Random enchanting candidate admission chance: namespace:enchantment=percent (0..100). Omitted IDs and 100 retain vanilla selection; 0 excludes the enchantment from random enchanting. This does not change existing enchantments, anvil application, or commands. Unknown IDs are retained for temporarily removed mods.")
                .defineListAllowEmpty(List.of("chance_percent"), () -> BundledGameplayDefaults.list("enchantments.chance_percent"), () -> "minecraft:sharpness=50", MiteConfig::validEnchantmentChanceOverride);
        builder.pop();
        SPEC = builder.build();
        // Publish defaults before configuration loading; workers never read ModConfigSpec's mutable caches.
        local = readLocalSnapshot();
        FACTORY = local;
    }

    private MiteConfig() {}

    /** Read only on the server thread. A JVM override also permits synchronous regression runs. */
    public static boolean asyncPathfindingEnabled() {
        String override = System.getProperty("mite.asyncPathfinding");
        return override == null ? value(ASYNC_PATHFINDING) : Boolean.parseBoolean(override);
    }

    /** Server-only work scheduling; JVM overrides also allow independent strict regressions. */
    public static boolean aiLoadReductionEnabled() {
        String override = System.getProperty("mite.aiLoadReduction");
        return override == null ? value(AI_LOAD_REDUCTION) : Boolean.parseBoolean(override);
    }

    public static boolean animalSearchBudgetEnabled() {
        String override = System.getProperty("mite.animalSearchBudget");
        return override == null ? aiLoadReductionEnabled() : Boolean.parseBoolean(override);
    }

    public static boolean pursuitSearchBudgetEnabled() {
        String override = System.getProperty("mite.pursuitSearchBudget");
        return override == null ? aiLoadReductionEnabled() : Boolean.parseBoolean(override);
    }

    private static String generationPath(R196OreRules.Kind kind, R196OreRules.Dimension dimension) {
        return dimension.name().toLowerCase(Locale.ROOT) + "_generation." + kind.name().toLowerCase(Locale.ROOT);
    }
    public static double factorySpawningDefault(MonsterSpawningSettings.Option option) {
        return BundledGameplayDefaults.number("monster_spawning." + option.key(), option.defaultValue());
    }
    public static double factoryOreMultiplier(R196OreRules.Kind kind, R196OreRules.Dimension dimension, boolean deepslate) {
        var deep = deepslate ? oreMultiplierSettings(dimension, true).get(kind) : null;
        double override = deep == null ? -1D : deep.getDefault();
        return override < 0D ? oreMultiplierSettings(dimension, false).get(kind).getDefault() : override;
    }
    public static OreHeightRange factoryOreHeight(R196OreRules.Kind kind, R196OreRules.Dimension dimension, boolean deepslate) {
        String text = oreHeightSettings(dimension, deepslate).get(kind).getDefault();
        return text.isBlank() ? OreHeightRange.defaults(kind, dimension) : OreHeightRange.parse(text);
    }

    private static Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> oreMultiplierSettings(R196OreRules.Dimension dimension, boolean deepslate) {
        return switch (dimension) {
            case OVERWORLD -> deepslate ? OVERWORLD_DEEPSLATE_ORES : OVERWORLD_ORES;
            case UNDERWORLD -> deepslate ? UNDERWORLD_DEEPSLATE_ORES : UNDERWORLD_ORES;
            case NETHER -> deepslate ? Map.of() : NETHER_ORES;
        };
    }

    private static Map<R196OreRules.Kind, ModConfigSpec.ConfigValue<String>> oreHeightSettings(R196OreRules.Dimension dimension, boolean deepslate) {
        return switch (dimension) {
            case OVERWORLD -> deepslate ? OVERWORLD_DEEPSLATE_ORE_HEIGHTS : OVERWORLD_ORE_HEIGHTS;
            case UNDERWORLD -> deepslate ? UNDERWORLD_DEEPSLATE_ORE_HEIGHTS : UNDERWORLD_ORE_HEIGHTS;
            case NETHER -> deepslate ? Map.of() : NETHER_ORE_HEIGHTS;
        };
    }

    private static void defineOreHeights(ModConfigSpec.Builder builder, R196OreRules.Kind kind, R196OreRules.Dimension dimension,
            Map<R196OreRules.Kind, ModConfigSpec.ConfigValue<String>> regular,
            Map<R196OreRules.Kind, ModConfigSpec.ConfigValue<String>> deepslate) {
        regular.put(kind, builder.translation("mite.config.ore_height_range")
                .comment("Absolute inclusive Y range, for example [-100,100]. Empty preserves the original height distribution. Values are clipped to dimension build limits; applies to new chunks and the entire vein.")
                .define("height_range", BundledGameplayDefaults.text(generationPath(kind, dimension) + ".height_range", ""), MiteConfig::validOreHeight));
        if (dimension != R196OreRules.Dimension.NETHER && R196OreRules.hasDeepslateOre(kind))
            deepslate.put(kind, builder.translation("mite.config.ore_height_range")
                    .comment("Independent absolute inclusive Y range for deepslate ore. Empty preserves the original height distribution.")
                    .define("deepslate_height_range", BundledGameplayDefaults.text(generationPath(kind, dimension) + ".deepslate_height_range", ""), MiteConfig::validOreHeight));
    }

    public static boolean validOreHeight(Object input) {
        if (!(input instanceof String text)) return false;
        if (text.isBlank()) return true;
        try { OreHeightRange.parse(text); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }

    public static OreHeightRange oreHeightOverride(R196OreRules.Kind kind, R196OreRules.Dimension dimension, boolean deepslate) {
        var snapshot = effectiveSnapshot();
        var heights = switch (dimension) {
            case OVERWORLD -> snapshot.overworldOreHeights();
            case UNDERWORLD -> snapshot.oreHeights();
            case NETHER -> snapshot.netherOreHeights();
        };
        String text = heights.getOrDefault(deepslate ? R196OreRules.deepslateMultiplierKey(kind) : kind.name(), "");
        return text.isBlank() ? null : OreHeightRange.parse(text);
    }

    public static OreHeightRange oreHeightRange(R196OreRules.Kind kind, R196OreRules.Dimension dimension, boolean deepslate) {
        var override = oreHeightOverride(kind, dimension, deepslate);
        return override == null ? OreHeightRange.defaults(kind, dimension) : override;
    }

    private static void defineDeepslateMultipliers(ModConfigSpec.Builder builder, R196OreRules.Dimension dimension,
            Map<R196OreRules.Kind, ModConfigSpec.DoubleValue> settings) {
        for (var kind : R196OreRules.configurableKinds(dimension)) {
            if (!R196OreRules.hasDeepslateOre(kind)) continue;
            String key = "deepslate_" + kind.name().toLowerCase(Locale.ROOT);
            settings.put(kind, builder.translation("mite.config.ore_deepslate_multiplier")
                    .comment("Independent deepslate ore multiplier, 0..100. -1 inherits the original mineral multiplier for compatibility with existing configurations; saving the multiplier screen writes the displayed independent rate.")
                    .defineInRange(key, BundledGameplayDefaults.number((dimension == R196OreRules.Dimension.OVERWORLD ? "overworld_ores." : "underworld_ores.") + key, -1D), -1D, 100D));
        }
    }

    private static ModConfigSpec.DoubleValue number(ModConfigSpec.Builder b, String key, double defaultValue, double maximum, String comment) {
        return b.translation("mite.config." + key).comment(comment).defineInRange(key, defaultValue, 0D, maximum);
    }

    private static <T> T value(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static boolean validEnchantmentId(Object input) {
        return input instanceof String id && id.contains(":") && ResourceLocation.tryParse(id) != null;
    }

    public static boolean validMonsterSpawnOverride(Object input) {
        if (!(input instanceof String entry)) return false;
        int separator = entry.indexOf('=');
        if (separator < 0 || !validEnchantmentId(entry.substring(0, separator))) return false;
        try {
            double multiplier = Double.parseDouble(entry.substring(separator + 1));
            return Double.isFinite(multiplier) && multiplier >= 0D && multiplier <= 100D;
        } catch (NumberFormatException ignored) { return false; }
    }

    private static Map<String, Double> spawnRateOverrides(Map<String, Double> values) {
        if (values == null) return Map.of();
        var valid = new HashMap<String, Double>();
        values.forEach((id, multiplier) -> {
            if (validEnchantmentId(id) && multiplier != null && Double.isFinite(multiplier) && multiplier >= 0D && multiplier <= 100D)
                valid.put(id, multiplier);
        });
        return Map.copyOf(valid);
    }

    public static boolean validMonsterSpawnWeightOverride(Object input) {
        if (!(input instanceof String entry)) return false;
        int separator = entry.indexOf('=');
        if (separator < 0 || !validEnchantmentId(entry.substring(0, separator))) return false;
        try { return validMonsterSpawnWeight(Double.parseDouble(entry.substring(separator + 1))); }
        catch (NumberFormatException ignored) { return false; }
    }

    private static boolean validMonsterSpawnWeight(Double weight) {
        return weight != null && Double.isFinite(weight) && weight >= 0D && weight <= 1000000D;
    }

    private static Map<String, Double> spawnWeightOverrides(Map<String, Double> values) {
        if (values == null) return Map.of();
        var valid = new HashMap<String, Double>();
        values.forEach((id, weight) -> {
            if (validEnchantmentId(id) && validMonsterSpawnWeight(weight)) valid.put(id, weight);
        });
        return Map.copyOf(valid);
    }

    public static double monsterSpawnMultiplier(String entityId) {
        return effectiveSnapshot().monsterSpawnRates().getOrDefault(entityId, 1D);
    }

    public static double monsterSpawnWeight(String entityId, double fallback) {
        return effectiveSnapshot().monsterSpawnWeights().getOrDefault(entityId, fallback);
    }

    public static MonsterSpawningSettings.Settings monsterSpawningSettings() { return effectiveSnapshot().spawningSettings(); }

    public static boolean saveMonsterSpawningSettings(Map<String, Double> values) {
        if (values == null) return false;
        final MonsterSpawningSettings.Settings settings;
        try { settings = MonsterSpawningSettings.Settings.from(values); }
        catch (IllegalArgumentException exception) { return false; }
        for (var option : MonsterSpawningSettings.Option.values()) {
            var setting = SPAWNING_CONTROLS.get(option);
            if (setting instanceof ModConfigSpec.IntValue integer) integer.set((int)settings.value(option));
            else if (setting instanceof ModConfigSpec.DoubleValue decimal) decimal.set(settings.value(option));
        }
        refreshEnchantmentRules();
        SPEC.save();
        GameplayConfigSync.broadcast();
        return true;
    }

    public static boolean saveMonsterSpawnRates(Map<String, Double> values) {
        return saveMonsterSpawnSettings(values, localSnapshot().monsterSpawnWeights());
    }

    public static boolean saveMonsterSpawnSettings(Map<String, Double> rates, Map<String, Double> weights) {
        if (rates == null || weights == null || spawnRateOverrides(rates).size() != rates.size()
                || spawnWeightOverrides(weights).size() != weights.size()) return false;
        NATURAL_SPAWN_RATES.set(rates.entrySet().stream().filter(entry -> entry.getValue() != 1D)
                .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue()).toList());
        NATURAL_SPAWN_WEIGHTS.set(weights.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + BigDecimal.valueOf(entry.getValue()).stripTrailingZeros().toPlainString()).toList());
        refreshEnchantmentRules();
        SPEC.save();
        GameplayConfigSync.broadcast();
        return true;
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

    public static boolean validEnchantmentChanceOverride(Object input) {
        if (!(input instanceof String entry)) return false;
        int separator = entry.indexOf('=');
        if (separator < 0 || !validEnchantmentId(entry.substring(0, separator))) return false;
        try { return validEnchantmentChance(Double.parseDouble(entry.substring(separator + 1))); }
        catch (NumberFormatException ignored) { return false; }
    }

    private static boolean validEnchantmentChance(Double percent) {
        return percent != null && Double.isFinite(percent) && percent >= 0D && percent <= 100D;
    }

    private static Map<String, Double> enchantmentChanceOverrides(Map<String, Double> chances) {
        if (chances == null) return Map.of();
        var overrides = new HashMap<String, Double>();
        chances.forEach((id, percent) -> {
            if (!validEnchantmentId(id) || !validEnchantmentChance(percent))
                throw new IllegalArgumentException("Invalid enchantment chance override: " + id + "=" + percent);
            if (percent < 100D) overrides.put(id, percent);
        });
        return Map.copyOf(overrides);
    }

    public static double underworldOreMultiplier(R196OreRules.Kind kind) {
        return oreMultiplier(kind, R196OreRules.Dimension.UNDERWORLD);
    }
    public static double oreMultiplier(R196OreRules.Kind kind, R196OreRules.Dimension dimension) {
        return oreMultipliers(effectiveSnapshot(), dimension).getOrDefault(kind.name(), 1D);
    }
    private static Map<String, Double> oreMultipliers(Snapshot snapshot, R196OreRules.Dimension dimension) {
        return switch (dimension) {
            case OVERWORLD -> snapshot.overworldOres();
            case UNDERWORLD -> snapshot.ores();
            case NETHER -> snapshot.netherOres();
        };
    }
    /** A missing/-1 override inherits the old setting so loading an existing world never changes its deep ore rate. */
    public static double deepslateOreMultiplierOverride(R196OreRules.Kind kind, R196OreRules.Dimension dimension) {
        return oreMultipliers(effectiveSnapshot(), dimension).getOrDefault(R196OreRules.deepslateMultiplierKey(kind), -1D);
    }
    public static double oreMultiplier(R196OreRules.Kind kind, R196OreRules.Dimension dimension, boolean deepslate) {
        double override = deepslate && R196OreRules.hasDeepslateOre(kind) ? deepslateOreMultiplierOverride(kind, dimension) : -1D;
        return override < 0D ? oreMultiplier(kind, dimension) : override;
    }
    public static double underworldOreCandidates(R196OreRules.Kind kind) {
        return oreCandidates(kind, R196OreRules.Dimension.UNDERWORLD);
    }
    public static double oreBaseCandidates(R196OreRules.Kind kind, R196OreRules.Dimension dimension) {
        return oreBaseCandidates(effectiveSnapshot(), kind, dimension);
    }
    private static double oreBaseCandidates(Snapshot snapshot, R196OreRules.Kind kind, R196OreRules.Dimension dimension) {
        var configured = dimension == R196OreRules.Dimension.OVERWORLD ? snapshot.overworldOreCandidates()
                : dimension == R196OreRules.Dimension.UNDERWORLD ? snapshot.oreCandidates() : Map.<String, Double>of();
        return configured.getOrDefault(kind.name(), (double)R196OreRules.configuredCandidateCount(kind, dimension));
    }
    public static double oreCandidates(R196OreRules.Kind kind, R196OreRules.Dimension dimension) {
        var snapshot = effectiveSnapshot();
        return oreBaseCandidates(snapshot, kind, dimension) * oreMultipliers(snapshot, dimension).getOrDefault(kind.name(), 1D);
    }
    public static double underworldOrePassChance(R196OreRules.Kind kind) {
        return orePassChance(kind, R196OreRules.Dimension.UNDERWORLD);
    }
    public static double orePassChance(R196OreRules.Kind kind, R196OreRules.Dimension dimension) {
        if (dimension == R196OreRules.Dimension.OVERWORLD)
            return effectiveSnapshot().overworldOreChances().getOrDefault(kind.name(), R196OreRules.configuredCandidateChance(kind, dimension));
        if (dimension != R196OreRules.Dimension.UNDERWORLD) return R196OreRules.candidateChance(dimension);
        return effectiveSnapshot().oreChances().getOrDefault(kind.name(),
                R196OreRules.configuredCandidateChance(kind, R196OreRules.Dimension.UNDERWORLD));
    }
    public static MonsterGrowthRules.Settings monsterGrowthSettings() { return effectiveSnapshot().monsterSettings(); }
    public static double monsterGrowthMultiplier() { return effectiveSnapshot().growth(); }
    public static double monsterHealthGrowthMultiplier() { return effectiveSnapshot().healthGrowth(); }
    public static double monsterDamageGrowthMultiplier() { return effectiveSnapshot().damageGrowth(); }
    public static double weaponReachMultiplier() { return effectiveSnapshot().reachMultiplier(); }
    public static double weaponReachReduction() { return effectiveSnapshot().reachReduction(); }
    public static boolean heightReachPenaltyEnabled() { return effectiveSnapshot().heightPenalty(); }
    public static double betterCombatDamageMultiplier() { return effectiveSnapshot().betterCombatDamageMultiplier(); }
    public static boolean betterCombatMobsEnabled() { return effectiveSnapshot().betterCombatMobs(); }
    public static boolean underworldTrialsEnabled() { return effectiveSnapshot().underworldTrials(); }
    public static boolean overworldTrialsEnabled() { return effectiveSnapshot().overworldTrials(); }

    public static int enchantmentMaxLevel(String resourceId, int fallback) {
        return effectiveSnapshot().caps().getOrDefault(resourceId, fallback);
    }
    public static boolean enchantmentEnabled(String resourceId) {
        return !effectiveSnapshot().disabled().contains(resourceId);
    }
    /** Candidate admission percentage; vanilla weighting and compatibility still select the result. */
    public static double enchantmentChancePercent(String resourceId) {
        return effectiveSnapshot().enchantmentChances().getOrDefault(resourceId, 100D);
    }

    /** Immutable state is also used by the read-only remote settings browser. */
    public record Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
            double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
            boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
            MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances,
            Map<String, Double> overworldOreCandidates, Map<String, Double> overworldOreChances, Map<String, Double> overworldOres,
            Map<String, Double> enchantmentChances, Map<String, Double> monsterSpawnRates,
            Map<String, String> oreHeights, Map<String, String> overworldOreHeights,
            MonsterSpawningSettings.Settings spawningSettings, Map<String, Double> monsterSpawnWeights,
            boolean betterCombatMobs, Map<String, Double> netherOres, Map<String, String> netherOreHeights,
            Double betterCombatDamageMultiplier) {
        public Snapshot {
            ores = Map.copyOf(ores);
            caps = Map.copyOf(caps);
            disabled = Set.copyOf(disabled);
            monsterSettings = monsterSettings == null ? MonsterGrowthRules.DEFAULTS : monsterSettings;
            oreCandidates = oreCandidates == null ? Map.of() : Map.copyOf(oreCandidates);
            oreChances = oreChances == null ? Map.of() : Map.copyOf(oreChances);
            overworldOreCandidates = overworldOreCandidates == null ? Map.of() : Map.copyOf(overworldOreCandidates);
            overworldOreChances = overworldOreChances == null ? Map.of() : Map.copyOf(overworldOreChances);
            overworldOres = overworldOres == null ? Map.of() : Map.copyOf(overworldOres);
            enchantmentChances = enchantmentChanceOverrides(enchantmentChances);
            monsterSpawnRates = spawnRateOverrides(monsterSpawnRates);
            oreHeights = heightOverrides(oreHeights);
            overworldOreHeights = heightOverrides(overworldOreHeights);
            spawningSettings = spawningSettings == null ? MonsterSpawningSettings.DEFAULTS : spawningSettings;
            monsterSpawnWeights = spawnWeightOverrides(monsterSpawnWeights);
            netherOres = netherOres == null ? Map.of() : Map.copyOf(netherOres);
            netherOreHeights = heightOverrides(netherOreHeights);
            if (betterCombatDamageMultiplier == null || !Double.isFinite(betterCombatDamageMultiplier)
                    || !BETTER_COMBAT_DAMAGE_MULTIPLIER.getSpec().test(betterCombatDamageMultiplier))
                betterCombatDamageMultiplier = BETTER_COMBAT_DAMAGE_MULTIPLIER.getDefault();
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
                MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances,
                Map<String, Double> overworldOreCandidates, Map<String, Double> overworldOreChances, Map<String, Double> overworldOres,
                Map<String, Double> enchantmentChances, Map<String, Double> monsterSpawnRates,
                Map<String, String> oreHeights, Map<String, String> overworldOreHeights,
                MonsterSpawningSettings.Settings spawningSettings, Map<String, Double> monsterSpawnWeights,
                boolean betterCombatMobs, Map<String, Double> netherOres, Map<String, String> netherOreHeights) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, monsterSettings, oreCandidates, oreChances,
                    overworldOreCandidates, overworldOreChances, overworldOres, enchantmentChances, monsterSpawnRates,
                    oreHeights, overworldOreHeights, spawningSettings, monsterSpawnWeights, betterCombatMobs, netherOres,
                    netherOreHeights, null);
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
                MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances,
                Map<String, Double> overworldOreCandidates, Map<String, Double> overworldOreChances, Map<String, Double> overworldOres,
                Map<String, Double> enchantmentChances, Map<String, Double> monsterSpawnRates,
                Map<String, String> oreHeights, Map<String, String> overworldOreHeights,
                MonsterSpawningSettings.Settings spawningSettings, Map<String, Double> monsterSpawnWeights, boolean betterCombatMobs) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, monsterSettings, oreCandidates, oreChances,
                    overworldOreCandidates, overworldOreChances, overworldOres, enchantmentChances, monsterSpawnRates,
                    oreHeights, overworldOreHeights, spawningSettings, monsterSpawnWeights, betterCombatMobs, Map.of(), Map.of());
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
                MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances,
                Map<String, Double> overworldOreCandidates, Map<String, Double> overworldOreChances, Map<String, Double> overworldOres,
                Map<String, Double> enchantmentChances, Map<String, Double> monsterSpawnRates,
                Map<String, String> oreHeights, Map<String, String> overworldOreHeights,
                MonsterSpawningSettings.Settings spawningSettings, Map<String, Double> monsterSpawnWeights) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, monsterSettings, oreCandidates, oreChances,
                    overworldOreCandidates, overworldOreChances, overworldOres, enchantmentChances, monsterSpawnRates,
                    oreHeights, overworldOreHeights, spawningSettings, monsterSpawnWeights, false);
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
                MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances,
                Map<String, Double> overworldOreCandidates, Map<String, Double> overworldOreChances, Map<String, Double> overworldOres,
                Map<String, Double> enchantmentChances, Map<String, Double> monsterSpawnRates,
                Map<String, String> oreHeights, Map<String, String> overworldOreHeights,
                MonsterSpawningSettings.Settings spawningSettings) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, monsterSettings, oreCandidates, oreChances,
                    overworldOreCandidates, overworldOreChances, overworldOres, enchantmentChances, monsterSpawnRates,
                    oreHeights, overworldOreHeights, spawningSettings, Map.of());
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
                MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances,
                Map<String, Double> overworldOreCandidates, Map<String, Double> overworldOreChances, Map<String, Double> overworldOres,
                Map<String, Double> enchantmentChances, Map<String, Double> monsterSpawnRates,
                Map<String, String> oreHeights, Map<String, String> overworldOreHeights) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, monsterSettings, oreCandidates, oreChances,
                    overworldOreCandidates, overworldOreChances, overworldOres, enchantmentChances, monsterSpawnRates,
                    oreHeights, overworldOreHeights, MonsterSpawningSettings.DEFAULTS);
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
                MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances,
                Map<String, Double> overworldOreCandidates, Map<String, Double> overworldOreChances, Map<String, Double> overworldOres,
                Map<String, Double> enchantmentChances, Map<String, Double> monsterSpawnRates) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, monsterSettings, oreCandidates, oreChances,
                    overworldOreCandidates, overworldOreChances, overworldOres, enchantmentChances, monsterSpawnRates, Map.of(), Map.of());
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
                MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances,
                Map<String, Double> overworldOreCandidates, Map<String, Double> overworldOreChances, Map<String, Double> overworldOres,
                Map<String, Double> enchantmentChances) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, monsterSettings, oreCandidates, oreChances,
                    overworldOreCandidates, overworldOreChances, overworldOres, enchantmentChances, Map.of());
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
                MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances,
                Map<String, Double> overworldOreCandidates, Map<String, Double> overworldOreChances, Map<String, Double> overworldOres) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, monsterSettings, oreCandidates, oreChances,
                    overworldOreCandidates, overworldOreChances, overworldOres, Map.of());
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
                MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances,
                Map<String, Double> overworldOreCandidates, Map<String, Double> overworldOreChances) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, monsterSettings, oreCandidates, oreChances,
                    overworldOreCandidates, overworldOreChances, Map.of());
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled,
                MonsterGrowthRules.Settings monsterSettings, Map<String, Double> oreCandidates, Map<String, Double> oreChances) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, monsterSettings, oreCandidates, oreChances, Map.of(), Map.of());
        }
        public Snapshot(Map<String, Double> ores, double growth, double healthGrowth, double damageGrowth,
                double reachMultiplier, double reachReduction, boolean heightPenalty, boolean underworldTrials,
                boolean overworldTrials, Map<String, Integer> caps, Set<String> disabled) {
            this(ores, growth, healthGrowth, damageGrowth, reachMultiplier, reachReduction, heightPenalty,
                    underworldTrials, overworldTrials, caps, disabled, MonsterGrowthRules.DEFAULTS, Map.of(), Map.of());
        }
    }

    private static Snapshot readLocalSnapshot() {
        var spawnWeights = new HashMap<String, Double>();
        for (String entry : value(NATURAL_SPAWN_WEIGHTS)) if (validMonsterSpawnWeightOverride(entry)) {
            int split = entry.indexOf('=');
            spawnWeights.put(entry.substring(0, split), Double.parseDouble(entry.substring(split + 1)));
        }
        var spawnRates = new HashMap<String, Double>();
        for (String entry : value(NATURAL_SPAWN_RATES)) if (validMonsterSpawnOverride(entry)) {
            int split = entry.indexOf('=');
            spawnRates.put(entry.substring(0, split), Double.parseDouble(entry.substring(split + 1)));
        }
        var ores = new HashMap<String, Double>();
        UNDERWORLD_ORES.forEach((kind, setting) -> ores.put(kind.name(), value(setting)));
        UNDERWORLD_DEEPSLATE_ORES.forEach((kind, setting) -> ores.put(R196OreRules.deepslateMultiplierKey(kind), value(setting)));
        var candidates = new HashMap<String, Double>();
        var chances = new HashMap<String, Double>();
        UNDERWORLD_ORE_CANDIDATES.forEach((kind, setting) -> candidates.put(kind.name(), value(setting)));
        UNDERWORLD_ORE_CHANCES.forEach((kind, setting) -> chances.put(kind.name(), value(setting) / 100D));
        var surfaceCandidates = new HashMap<String, Double>();
        var surfaceChances = new HashMap<String, Double>();
        OVERWORLD_ORE_CANDIDATES.forEach((kind, setting) -> surfaceCandidates.put(kind.name(), value(setting)));
        OVERWORLD_ORE_CHANCES.forEach((kind, setting) -> surfaceChances.put(kind.name(), value(setting) / 100D));
        var surfaceMultipliers = new HashMap<String, Double>();
        OVERWORLD_ORES.forEach((kind, setting) -> surfaceMultipliers.put(kind.name(), value(setting)));
        OVERWORLD_DEEPSLATE_ORES.forEach((kind, setting) -> surfaceMultipliers.put(R196OreRules.deepslateMultiplierKey(kind), value(setting)));
        var netherMultipliers = new HashMap<String, Double>();
        NETHER_ORES.forEach((kind, setting) -> netherMultipliers.put(kind.name(), value(setting)));
        var caps = new HashMap<String, Integer>();
        for (String entry : value(ENCHANTMENT_MAX_LEVELS)) {
            if (validLevelOverride(entry)) {
                int split = entry.indexOf('=');
                caps.put(entry.substring(0, split), Integer.parseInt(entry.substring(split + 1)));
            }
        }
        var enchantmentChances = new HashMap<String, Double>();
        for (String entry : value(ENCHANTMENT_CHANCES)) {
            if (validEnchantmentChanceOverride(entry)) {
                int split = entry.indexOf('=');
                enchantmentChances.put(entry.substring(0, split), Double.parseDouble(entry.substring(split + 1)));
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
                value(UNDERWORLD_TRIALS), value(OVERWORLD_TRIALS), caps, new HashSet<>(value(DISABLED_ENCHANTMENTS)), growth, candidates, chances,
                surfaceCandidates, surfaceChances, surfaceMultipliers, enchantmentChances, spawnRates,
                readHeights(UNDERWORLD_ORE_HEIGHTS, UNDERWORLD_DEEPSLATE_ORE_HEIGHTS),
                readHeights(OVERWORLD_ORE_HEIGHTS, OVERWORLD_DEEPSLATE_ORE_HEIGHTS), readSpawningSettings(), spawnWeights,
                value(BETTER_COMBAT_MOBS), netherMultipliers, readHeights(NETHER_ORE_HEIGHTS, Map.of()),
                value(BETTER_COMBAT_DAMAGE_MULTIPLIER));
    }

    private static MonsterSpawningSettings.Settings readSpawningSettings() {
        var values = new HashMap<String, Double>();
        SPAWNING_CONTROLS.forEach((option, setting) -> values.put(option.key(), value(setting).doubleValue()));
        return MonsterSpawningSettings.Settings.from(values);
    }

    private static Map<String, String> readHeights(Map<R196OreRules.Kind, ModConfigSpec.ConfigValue<String>> regular,
            Map<R196OreRules.Kind, ModConfigSpec.ConfigValue<String>> deepslate) {
        var heights = new HashMap<String, String>();
        regular.forEach((kind, setting) -> { if (!value(setting).isBlank()) heights.put(kind.name(), value(setting)); });
        deepslate.forEach((kind, setting) -> { if (!value(setting).isBlank()) heights.put(R196OreRules.deepslateMultiplierKey(kind), value(setting)); });
        return heights;
    }

    private static Map<String, String> heightOverrides(Map<String, String> values) {
        if (values == null) return Map.of();
        var result = new HashMap<String, String>();
        values.forEach((key, text) -> { if (validOreHeight(text) && !text.isBlank()) result.put(key, OreHeightRange.parse(text).toString()); });
        return Map.copyOf(result);
    }
    public static Snapshot factorySnapshot() { return FACTORY; }
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
        saveEnchantments(caps, disabled, localSnapshot().enchantmentChances());
    }

    public static void saveEnchantments(Map<String, Integer> caps, Set<String> disabled, Map<String, Double> chances) {
        var chanceOverrides = enchantmentChanceOverrides(chances);
        var entries = new ArrayList<String>();
        caps.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entries.add(entry.getKey() + "=" + entry.getValue()));
        var chanceEntries = chanceOverrides.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue()).toList();
        ENCHANTMENT_MAX_LEVELS.set(entries);
        DISABLED_ENCHANTMENTS.set(disabled.stream().sorted().toList());
        ENCHANTMENT_CHANCES.set(chanceEntries);
        refreshEnchantmentRules();
        SPEC.save();
        GameplayConfigSync.broadcast();
    }

    public static GrowthFormula.Validation validateMonsterGrowth(MonsterGrowthRules.Settings settings) {
        var snapshot = effectiveSnapshot();
        return MonsterGrowthRules.validate(settings, snapshot.growth() * snapshot.healthGrowth(), snapshot.growth() * snapshot.damageGrowth());
    }

    public static GrowthFormula.Validation validateMonsterGrowth(MonsterGrowthRules.Settings settings, double healthMultiplier) {
        var snapshot = effectiveSnapshot();
        return MonsterGrowthRules.validate(settings, healthMultiplier, snapshot.growth() * snapshot.damageGrowth());
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
        return saveOreGeneration(R196OreRules.Dimension.UNDERWORLD, candidates, chancePercent);
    }

    /** The simple UI edits multipliers only; existing advanced rates are never rewritten. */
    public static boolean saveOreMultipliers(Map<R196OreRules.Dimension, ? extends Map<R196OreRules.Kind, Double>> multipliers) {
        return saveOreMultipliers(multipliers, Map.of());
    }

    /** Save deep overrides atomically; omitted deep rows retain their previous override or inheritance. */
    public static boolean saveOreMultipliers(Map<R196OreRules.Dimension, ? extends Map<R196OreRules.Kind, Double>> multipliers,
            Map<R196OreRules.Dimension, ? extends Map<R196OreRules.Kind, Double>> deepslateMultipliers) {
        return saveOreMultipliers(multipliers, deepslateMultipliers, Map.of(), Map.of());
    }

    public static boolean saveOreMultipliers(Map<R196OreRules.Dimension, ? extends Map<R196OreRules.Kind, Double>> multipliers,
            Map<R196OreRules.Dimension, ? extends Map<R196OreRules.Kind, Double>> deepslateMultipliers,
            Map<R196OreRules.Dimension, ? extends Map<R196OreRules.Kind, String>> heights,
            Map<R196OreRules.Dimension, ? extends Map<R196OreRules.Kind, String>> deepslateHeights) {
        if (multipliers.isEmpty()) return false;
        for (var dimension : multipliers.keySet()) {
            var kinds = R196OreRules.configurableKinds(dimension);
            if (kinds.isEmpty() || multipliers.get(dimension) == null) return false;
            for (var kind : kinds) {
                Double multiplier = multipliers.get(dimension).get(kind);
                if (multiplier == null || !Double.isFinite(multiplier) || multiplier < 0D || multiplier > 100D) return false;
            }
        }
        if (!multipliers.keySet().containsAll(deepslateMultipliers.keySet())) return false;
        for (var dimension : deepslateMultipliers.keySet()) {
            var deepValues = deepslateMultipliers.get(dimension);
            if (deepValues == null) return false;
            var settings = oreMultiplierSettings(dimension, true);
            for (var entry : deepValues.entrySet()) {
                Double multiplier = entry.getValue();
                if (entry.getKey() == null || !settings.containsKey(entry.getKey())
                        || multiplier == null || !Double.isFinite(multiplier) || multiplier > 100D || multiplier < 0D && multiplier != -1D) return false;
            }
        }
        if (!validHeightDrafts(multipliers.keySet(), heights, false)
                || !validHeightDrafts(multipliers.keySet(), deepslateHeights, true)) return false;
        for (var dimension : multipliers.keySet()) {
            var settings = oreMultiplierSettings(dimension, false);
            for (var kind : R196OreRules.configurableKinds(dimension)) settings.get(kind).set(multipliers.get(dimension).get(kind));
        }
        deepslateMultipliers.forEach((dimension, values) -> {
            var settings = oreMultiplierSettings(dimension, true);
            values.forEach((kind, multiplier) -> settings.get(kind).set(multiplier));
        });
        heights.forEach((dimension, values) -> values.forEach((kind, text) ->
                oreHeightSettings(dimension, false).get(kind).set(text)));
        deepslateHeights.forEach((dimension, values) -> values.forEach((kind, text) ->
                oreHeightSettings(dimension, true).get(kind).set(text)));
        refreshEnchantmentRules();
        SPEC.save();
        return true;
    }

    private static boolean validHeightDrafts(Set<R196OreRules.Dimension> dimensions,
            Map<R196OreRules.Dimension, ? extends Map<R196OreRules.Kind, String>> heights, boolean deepslate) {
        if (!dimensions.containsAll(heights.keySet())) return false;
        for (var entry : heights.entrySet()) {
            if (entry.getValue() == null) return false;
            var settings = oreHeightSettings(entry.getKey(), deepslate);
            for (var height : entry.getValue().entrySet())
                if (height.getKey() == null || !settings.containsKey(height.getKey()) || !validOreHeight(height.getValue())) return false;
        }
        return true;
    }

    public static boolean saveOreGeneration(R196OreRules.Dimension dimension, Map<R196OreRules.Kind, Double> candidates,
            Map<R196OreRules.Kind, Double> chancePercent) {
        return saveOreGeneration(Map.of(dimension, candidates), Map.of(dimension, chancePercent));
    }

    /** Validate every dimension before publishing either page's drafts. */
    public static boolean saveOreGeneration(Map<R196OreRules.Dimension, ? extends Map<R196OreRules.Kind, Double>> candidates,
            Map<R196OreRules.Dimension, ? extends Map<R196OreRules.Kind, Double>> chancePercent) {
        if (candidates.isEmpty() || !candidates.keySet().equals(chancePercent.keySet())) return false;
        for (var dimension : candidates.keySet()) {
            if (dimension == R196OreRules.Dimension.NETHER) return false;
            var kinds = R196OreRules.configurableKinds(dimension);
            if (kinds.isEmpty() || candidates.get(dimension) == null || chancePercent.get(dimension) == null) return false;
            for (var kind : kinds) {
                Double count = candidates.get(dimension).get(kind), chance = chancePercent.get(dimension).get(kind);
                if (count == null || chance == null || !Double.isFinite(count) || count < 0D || count > 100000D
                        || !Double.isFinite(chance) || chance < 0D || chance > 100D) return false;
            }
        }
        for (var dimension : candidates.keySet()) {
            var counts = dimension == R196OreRules.Dimension.OVERWORLD ? OVERWORLD_ORE_CANDIDATES : UNDERWORLD_ORE_CANDIDATES;
            var chances = dimension == R196OreRules.Dimension.OVERWORLD ? OVERWORLD_ORE_CHANCES : UNDERWORLD_ORE_CHANCES;
            for (var kind : R196OreRules.configurableKinds(dimension)) {
                counts.get(kind).set(candidates.get(dimension).get(kind));
                chances.get(kind).set(chancePercent.get(dimension).get(kind));
                if (dimension == R196OreRules.Dimension.UNDERWORLD) UNDERWORLD_ORES.get(kind).set(1D);
            }
        }
        refreshEnchantmentRules();
        SPEC.save();
        return true;
    }

    private static void migrateAttackGrowth() {
        String formula = MONSTER_DAMAGE_FORMULA.get();
        String settlement = MONSTER_DAMAGE_BLOOD_MOON_FORMULA.get();
        String defaultFormula = MonsterGrowthRules.DEFAULT_FORMULA;
        if (defaultFormula.equals(formula) && defaultFormula.equals(inheritedFormula(settlement, formula))) {
            if (MONSTER_DAMAGE_DAILY_PERCENT.get() == .25D && MONSTER_DAMAGE_GROWTH.get() == 1D)
                MONSTER_DAMAGE_DAILY_PERCENT.set(MONSTER_DAMAGE_DAILY_PERCENT.getDefault());
        } else if (!validLegacyAttack(formula, settlement)) {
            // The previous release allowed arbitrary attack formulas because it never executed them.
            Mite.LOGGER.warn("Repairing unusable monster attack formulas from the previous configuration");
            if (!validLegacyAttack(formula, defaultFormula)) {
                formula = defaultFormula;
                MONSTER_DAMAGE_FORMULA.set(formula);
            }
            if (!validLegacyAttack(formula, settlement)) MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set(defaultFormula);
        }
        DAMAGE_RATE_MIGRATED.set(true);
        SPEC.save();
    }

    private static boolean validLegacyAttack(String formula, String settlement) {
        var defaults = MonsterGrowthRules.DEFAULTS;
        var attack = new MonsterGrowthRules.Settings(defaults.healthBase(), MONSTER_DAMAGE_BASE.get(), defaults.skeletonReference(),
                defaults.healthRate(), MONSTER_DAMAGE_DAILY_PERCENT.get() / 100D, defaults.healthFormula(), formula,
                defaults.healthBloodMoonFormula(), inheritedFormula(settlement, formula));
        return MonsterGrowthRules.validate(attack, 1D, MONSTER_GROWTH.get() * MONSTER_DAMAGE_GROWTH.get()).valid();
    }

    @SubscribeEvent public static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) return;
        if (!DAMAGE_RATE_MIGRATED.get()) migrateAttackGrowth();
        EnchantmentCatalog.entries();
        refreshEnchantmentRules();
        GameplayConfigSync.broadcast();
    }
}
