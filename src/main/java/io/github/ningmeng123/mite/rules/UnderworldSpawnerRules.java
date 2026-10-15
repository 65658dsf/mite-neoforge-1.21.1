package io.github.ningmeng123.mite.rules;

import java.util.Map;

/** Stable modern dungeon adaptation; neither bone lord is a permitted caged identity. @author NingMeng123 */
public final class UnderworldSpawnerRules {
    private static final Map<String, String> REPLACEMENTS = Map.ofEntries(
            Map.entry("minecraft:zombie", "mite:revenant"),
            Map.entry("minecraft:husk", "mite:revenant"),
            Map.entry("minecraft:zombie_villager", "mite:revenant"),
            Map.entry("minecraft:drowned", "mite:ghoul"),
            Map.entry("minecraft:skeleton", "mite:longdead"),
            Map.entry("minecraft:stray", "mite:longdead_guardian"),
            Map.entry("minecraft:bogged", "mite:longdead_guardian"),
            Map.entry("minecraft:wither_skeleton", "mite:longdead_guardian"),
            Map.entry("minecraft:spider", "mite:demon_spider"),
            Map.entry("minecraft:cave_spider", "mite:phase_spider"),
            Map.entry("minecraft:slime", "mite:ooze"),
            Map.entry("minecraft:creeper", "mite:infernal_creeper"),
            Map.entry("minecraft:silverfish", "mite:hoary_silverfish"),
            Map.entry("mite:bone_lord", "mite:longdead_guardian"),
            Map.entry("mite:ancient_bone_lord", "mite:longdead_guardian"));

    private UnderworldSpawnerRules() {}

    public static String replacement(String entityId) { return REPLACEMENTS.getOrDefault(entityId, entityId); }
    public static Map<String, String> replacements() { return REPLACEMENTS; }
}
