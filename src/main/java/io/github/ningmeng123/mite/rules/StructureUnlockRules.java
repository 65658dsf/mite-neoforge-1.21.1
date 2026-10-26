package io.github.ningmeng123.mite.rules;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared server, advancement and tooltip definitions for exploration unlocks. @author NingMeng123 */
public final class StructureUnlockRules {
    public static final long MINIMUM_DAY = 32L;

    public enum Metric {
        OCEAN_SAIL_CM(200_001, true), OCEAN_DIVE_CM(30_001, true), FISH_CAUGHT(5, false),
        DESERT_WALK_CM(50_001, true), JUNGLE_WALK_CM(50_001, true), SWAMP_TRAVEL_CM(30_001, true),
        SNOW_WALK_CM(50_001, true), UNDERGROUND_WALK_CM(50_001, true), DARK_FOREST_WALK_CM(100_001, true),
        DEEP_SNEAK_CM(30_001, true), NETHER_WALK_CM(100_001, true), SOUL_VALLEY_WALK_CM(30_001, true),
        END_ISLAND_WALK_CM(50_001, true), NATURAL_STONE_MINED(256, false),
        BRUSH_CRAFTED(1, false), EYE_CRAFTED(1, false), GOLD_ARMOR_CRAFTED(1, false), DRAGON_KILLED(1, false);

        private final long cap;
        private final boolean distance;
        Metric(long cap, boolean distance) { this.cap = cap; this.distance = distance; }
        public long cap() { return cap; }
        public boolean distance() { return distance; }
        public String key() { return name().toLowerCase(Locale.ROOT); }
        public long clamp(long value) { return Math.max(0L, Math.min(cap, value)); }
    }

    /** Distances use centimeters; 50,001 means strictly more than 500 blocks. */
    public record Requirement(Metric metric, long target) {
        public String criterion() { return metric.key(); }
        public boolean met(Map<Metric, Long> values) { return values.getOrDefault(metric, 0L) >= target; }
    }

    public record Definition(String path, String icon, long minimumDay, int flags, List<Requirement> requirements) {}

    private static Requirement need(Metric metric, long target) { return new Requirement(metric, target); }
    private static Definition entry(String path, String icon, int flags, Requirement... needs) {
        return new Definition(path, "minecraft:" + icon, MINIMUM_DAY, flags, List.of(needs));
    }

    private static final List<Definition> DEFINITIONS = List.of(
            entry("ruined_portal", "crying_obsidian", 32),
            new Definition("pillager_outpost", "minecraft:crossbow", 45L, 64 | 128, List.of()),
            new Definition("village", "minecraft:bell", 60L, 16, List.of()),
            entry("stronghold", "end_portal_frame", 0, need(Metric.EYE_CRAFTED, 1)),
            entry("shipwreck", "oak_boat", 0, need(Metric.OCEAN_SAIL_CM, 50_001)),
            entry("ocean_ruin", "prismarine_shard", 0, need(Metric.OCEAN_DIVE_CM, 10_001)),
            entry("buried_treasure", "chest", 0, need(Metric.OCEAN_SAIL_CM, 50_001), need(Metric.FISH_CAUGHT, 5)),
            entry("desert_pyramid", "chiseled_sandstone", 0, need(Metric.DESERT_WALK_CM, 50_001)),
            entry("jungle_pyramid", "mossy_cobblestone", 0, need(Metric.JUNGLE_WALK_CM, 50_001)),
            entry("swamp_hut", "cauldron", 0, need(Metric.SWAMP_TRAVEL_CM, 30_001)),
            entry("igloo", "snow_block", 0, need(Metric.SNOW_WALK_CM, 50_001)),
            entry("trail_ruins", "brush", 0, need(Metric.BRUSH_CRAFTED, 1)),
            entry("mineshaft", "rail", 0, need(Metric.UNDERGROUND_WALK_CM, 50_001)),
            entry("mansion", "dark_oak_log", 128, need(Metric.DARK_FOREST_WALK_CM, 100_001)),
            entry("monument", "prismarine", 0, need(Metric.OCEAN_SAIL_CM, 200_001), need(Metric.OCEAN_DIVE_CM, 30_001)),
            entry("ancient_city", "sculk", 128, need(Metric.DEEP_SNEAK_CM, 30_001)),
            entry("fortress", "nether_bricks", 0, need(Metric.NETHER_WALK_CM, 50_001)),
            entry("bastion_remnant", "gilded_blackstone", 0, need(Metric.NETHER_WALK_CM, 100_001), need(Metric.GOLD_ARMOR_CRAFTED, 1)),
            entry("nether_fossil", "bone_block", 0, need(Metric.SOUL_VALLEY_WALK_CM, 30_001)),
            entry("overworld_fossil", "bone", 0, need(Metric.NATURAL_STONE_MINED, 256)),
            entry("end_city", "purpur_block", 0, need(Metric.DRAGON_KILLED, 1), need(Metric.END_ISLAND_WALK_CM, 50_001)));

    private StructureUnlockRules() {}
    public static List<Definition> definitions() { return DEFINITIONS; }
    public static Definition find(String path) {
        return DEFINITIONS.stream().filter(entry -> entry.path().equals(path)).findFirst().orElse(null);
    }
    public static boolean exempt(String namespace, String path) {
        return namespace.equals("minecraft") && path.equals("trial_chambers")
                || namespace.equals("mite") && path.equals("underworld_trial_chambers");
    }
    public static Definition forStructure(String namespace, String path) {
        if (!namespace.equals("minecraft")) return null;
        String family = path.startsWith("village_") ? "village"
                : path.equals("ruined_portal") || path.startsWith("ruined_portal_") ? "ruined_portal"
                : path.equals("mineshaft_mesa") ? "mineshaft"
                : path.equals("shipwreck_beached") ? "shipwreck"
                : path.startsWith("ocean_ruin_") ? "ocean_ruin" : path;
        return find(family);
    }
    public static boolean requirementsMet(Definition definition, int flags, Map<Metric, Long> values) {
        return (flags & definition.flags()) == definition.flags()
                && definition.requirements().stream().allMatch(need -> need.met(values));
    }

    public static boolean dayUnlocked(String namespace, String path, long displayDay) {
        if (exempt(namespace, path)) return true;
        Definition definition = forStructure(namespace, path);
        return displayDay >= (definition == null ? MINIMUM_DAY : definition.minimumDay());
    }
}
