package io.github.ningmeng123.mite.rules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Weighted dungeon roles must all be reachable without permitting either bone lord. @author NingMeng123 */
class UnderworldDungeonMobRulesTest {
    @Test void rosterIncludesTheRequestedUnderworldRolesWithOnlyPositiveWeights() {
        var mobs = UnderworldDungeonMobRules.mobs();
        assertFalse(mobs.isEmpty());
        assertEquals(mobs.size(), new HashSet<>(mobs).size(), "Roles must not be duplicated");
        for (var mob : mobs) {
            assertTrue(mob.entityId().matches("mite:[a-z0-9_]+"), "Only registered-project entity identifiers belong in this roster");
            assertTrue(mob.weight() > 0, "Every advertised dungeon role must be reachable");
            assertFalse(Set.of("mite:bone_lord", "mite:ancient_bone_lord").contains(mob.entityId()));
        }
        Set<String> ids = new HashSet<>();
        mobs.forEach(mob -> ids.add(mob.entityId()));
        assertTrue(ids.containsAll(Set.of("mite:longdead_guardian", "mite:revenant", "mite:longdead",
                "mite:demon_spider", "mite:phase_spider", "mite:ghoul")));
        assertEquals(1L, mobs.stream().filter(UnderworldDungeonMobRules.DungeonMob::spiderJockey).count());
        assertTrue(mobs.stream().anyMatch(mob -> mob.spiderJockey() && mob.entityId().equals("mite:demon_spider")));
        assertTrue(mobs.stream().anyMatch(mob -> !mob.spiderJockey() && mob.entityId().equals("mite:demon_spider")),
                "Plain demon spiders remain distinct from the spider-rider role");
    }

    @Test void everyWeightedTicketSelectsItsRoleAndTheWholePoolIsCovered() {
        var mobs = UnderworldDungeonMobRules.mobs();
        int total = UnderworldDungeonMobRules.totalWeight();
        assertEquals(mobs.stream().mapToInt(UnderworldDungeonMobRules.DungeonMob::weight).sum(), total);
        Map<UnderworldDungeonMobRules.DungeonMob, Integer> occurrences = new HashMap<>();
        for (int draw = 0; draw < total; draw++) {
            var selected = UnderworldDungeonMobRules.select(draw);
            assertTrue(mobs.contains(selected), "A draw cannot select an enemy outside the dungeon pool");
            occurrences.merge(selected, 1, Integer::sum);
        }
        assertEquals(new HashSet<>(mobs), occurrences.keySet());
        mobs.forEach(mob -> assertEquals(mob.weight(), occurrences.get(mob).intValue(),
                "Ticket coverage must match the configured relative weight of " + mob));
    }

    @Test void adjacentWeightBoundariesHaveNoGapsOrOffByOneOverlap() {
        int first = 0;
        for (var mob : UnderworldDungeonMobRules.mobs()) {
            assertEquals(mob, UnderworldDungeonMobRules.select(first));
            assertEquals(mob, UnderworldDungeonMobRules.select(first + mob.weight() - 1));
            first += mob.weight();
        }
        assertEquals(UnderworldDungeonMobRules.totalWeight(), first);
    }

    @Test void invalidTicketsCannotWrapIntoAnUnintendedEnemy() {
        for (int draw : new int[]{Integer.MIN_VALUE, -1, UnderworldDungeonMobRules.totalWeight(), Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> UnderworldDungeonMobRules.select(draw));
    }

    @Test void callersCannotChangeTheSharedDungeonRoster() {
        var mobs = UnderworldDungeonMobRules.mobs();
        assertThrows(UnsupportedOperationException.class, () -> mobs.add(mobs.getFirst()));
        assertThrows(UnsupportedOperationException.class, mobs::clear);
    }
}
