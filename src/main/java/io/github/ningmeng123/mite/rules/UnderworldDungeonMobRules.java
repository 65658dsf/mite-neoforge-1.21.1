package io.github.ningmeng123.mite.rules;

import java.util.List;

/** A balanced modern dungeon roster, with neither source bone lord permitted. @author NingMeng123 */
public final class UnderworldDungeonMobRules {
    public record DungeonMob(String entityId, int weight, boolean spiderJockey) {}

    private static final List<DungeonMob> MOBS = List.of(
            new DungeonMob("mite:longdead_guardian", 20, false),
            new DungeonMob("mite:revenant", 25, false),
            new DungeonMob("mite:longdead", 10, false),
            new DungeonMob("mite:demon_spider", 15, true),
            new DungeonMob("mite:demon_spider", 15, false),
            new DungeonMob("mite:phase_spider", 10, false),
            new DungeonMob("mite:ghoul", 5, false));
    private static final int TOTAL_WEIGHT = MOBS.stream().mapToInt(DungeonMob::weight).sum();

    private UnderworldDungeonMobRules() {}

    public static List<DungeonMob> mobs() { return MOBS; }
    public static int totalWeight() { return TOTAL_WEIGHT; }

    /** Selects one entry for a zero-based weighted draw. */
    public static DungeonMob select(int draw) {
        if (draw < 0 || draw >= TOTAL_WEIGHT) throw new IllegalArgumentException("Dungeon mob draw out of range: " + draw);
        for (DungeonMob mob : MOBS) {
            if (draw < mob.weight()) return mob;
            draw -= mob.weight();
        }
        throw new IllegalStateException("Dungeon mob weights do not cover their total");
    }
}
