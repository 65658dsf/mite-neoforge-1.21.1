package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit user adaptation is checked separately from independently recorded R196 source behavior. @author NingMeng123 */
class BloodMoonInvasionRulesTest {
    @Test void thirdBloodMoonEndsBeforeTheExactDayNinetySevenUnlock() {
        for (long time : new long[]{Long.MIN_VALUE, -1L, 0L, 31L * 24_000L, 63L * 24_000L, 95L * 24_000L + 13_000L, 2_303_999L})
            assertFalse(BloodMoonInvasionRules.isUnlocked(time, true));
        for (long time : new long[]{2_304_000L, 2_304_001L, 128L * 24_000L, Long.MAX_VALUE})
            assertTrue(BloodMoonInvasionRules.isUnlocked(time, true));
        assertEquals(97L, BloodMoonInvasionRules.UNLOCK_DAY);
        assertEquals(2_304_000L, BloodMoonInvasionRules.UNLOCK_TIME);
    }

    @Test void otherDimensionsNeverAcquireAnOverworldUnlockFromTheirOwnAge() {
        for (long time : new long[]{0L, 2_304_000L, Long.MAX_VALUE}) assertFalse(BloodMoonInvasionRules.isUnlocked(time, false));
    }

    @Test void firstSecondAndThirdBloodMoonSourceExceptionsHaveNoHiddenNinetySixDayGate() {
        for (int day : new int[]{32, 64, 96}) {
            long time = (day - 1L) * 24_000L + 13_000L;
            assertTrue(R196SpawnRules.moon(time, true).blood());
            assertFalse(R196SpawnRules.daytime(13_000));
            for (String mob : List.of("EntityGhoul", "EntityVampireBat", "EntityRevenant", "EntityNightwing", "EntityBoneLord"))
                assertTrue(BloodMoonInvasionRules.depthAllows(mob, 100, true, true, false, false, false));
            assertFalse(BloodMoonInvasionRules.depthAllows("EntityInvisibleStalker", 100, true, true, false, false, false));
            assertFalse(BloodMoonInvasionRules.depthAllows("EntityDemonSpider", 100, true, true, false, false, false));
            assertFalse(BloodMoonInvasionRules.depthAllows("EntityPhaseSpider", 100, true, true, false, false, false));
        }
    }

    @Test void beforeUnlockEveryRecordedSourceDepthBoundaryAndBiomeExceptionIsUnchanged() throws Exception {
        JsonObject spec = JsonParser.parseString(Files.readString(Path.of("spec/blood-moon-invasion.json"))).getAsJsonObject();
        for (var value : spec.getAsJsonObject("r196_source").getAsJsonArray("depth_limits")) {
            var row = value.getAsJsonObject();
            String mob = row.get("source_class").getAsString(); int y = row.get("maximum_ordinary_y").getAsInt();
            assertTrue(BloodMoonInvasionRules.depthAllows(mob, y, true, false, false, false, false), mob);
            assertFalse(BloodMoonInvasionRules.depthAllows(mob, y + 1, true, false, false, false, false), mob);
        }
        assertTrue(BloodMoonInvasionRules.depthAllows("EntityWight", 100, true, true, true, false, false));
        assertFalse(BloodMoonInvasionRules.depthAllows("EntityWight", 100, true, true, false, false, false));
        assertTrue(BloodMoonInvasionRules.depthAllows("EntityShadow", 100, true, true, false, true, false));
        assertFalse(BloodMoonInvasionRules.depthAllows("EntityShadow", 100, true, true, false, false, false));
    }

    @Test void unlockedSurfaceAccessPersistsBeyondBloodNightWithoutFlatteningBiomeNiches() {
        for (boolean bloodNight : new boolean[]{false, true}) {
            for (String mob : List.of("EntityGhoul", "EntityRevenant", "EntityInvisibleStalker", "EntityEarthElemental", "EntityBlob",
                    "EntityOoze", "EntityPudding", "EntityDemonSpider", "EntityPhaseSpider", "EntityHellhound", "EntityBoneLord",
                    "EntityAncientBoneLord", "EntityLongdead", "EntityNightwing", "EntityVampireBat"))
                assertTrue(BloodMoonInvasionRules.depthAllows(mob, 200, true, bloodNight, false, false, true), mob);
            assertFalse(BloodMoonInvasionRules.depthAllows("EntityWight", 200, true, bloodNight, false, false, true));
            assertTrue(BloodMoonInvasionRules.depthAllows("EntityWight", 200, true, bloodNight, true, false, true));
            assertFalse(BloodMoonInvasionRules.depthAllows("EntityShadow", 200, true, bloodNight, true, false, true));
            assertTrue(BloodMoonInvasionRules.depthAllows("EntityShadow", 200, true, bloodNight, false, true, true));
        }
    }

    @Test void originalUndergroundAccessRemainsAvailableOutsideSurfaceBiomeNiches() {
        assertTrue(BloodMoonInvasionRules.depthAllows("EntityWight", 48, true, false, false, false, true));
        assertTrue(BloodMoonInvasionRules.depthAllows("EntityShadow", 32, true, false, false, false, true));
        for (boolean unlocked : new boolean[]{false, true}) {
            assertTrue(BloodMoonInvasionRules.depthAllows("EntityWight", 200, false, false, false, false, unlocked));
            assertTrue(BloodMoonInvasionRules.depthAllows("EntityShadow", 200, false, false, false, false, unlocked));
        }
    }

    @Test void recordedExtraEntriesComeFromTheRealUnderworldTableAndKeepTheirOriginalWeights() throws Exception {
        JsonObject spec = JsonParser.parseString(Files.readString(Path.of("spec/blood-moon-invasion.json"))).getAsJsonObject();
        var source = JsonParser.parseString(Files.readString(Path.of("src/main/resources/mite/spawning-031.json"))).getAsJsonObject();
        JsonObject underworld = null;
        for (var value : source.getAsJsonArray("biomes")) if (value.getAsJsonObject().get("legacy_id").getAsInt() == 26) underworld = value.getAsJsonObject();
        assertNotNull(underworld);
        var extras = spec.getAsJsonObject("modern_adaptation").getAsJsonArray("ordinary_biome_additions");
        assertEquals(3, extras.size());
        int addedWeight = 0;
        for (var value : extras) {
            JsonObject extra = value.getAsJsonObject(); addedWeight += extra.get("weight").getAsInt();
            JsonObject found = null;
            for (var pool : underworld.getAsJsonArray("pools")) if (pool.getAsJsonObject().get("category").getAsString().equals("monster"))
                for (var row : pool.getAsJsonObject().getAsJsonArray("entries"))
                    if (row.getAsJsonObject().get("entity_class").equals(extra.get("source_class"))) found = row.getAsJsonObject();
            assertNotNull(found, extra.toString());
            assertEquals(found.get("weight"), extra.get("weight"));
            assertEquals(found.get("minimum_group"), extra.get("minimum_group"));
            assertEquals(found.get("maximum_group"), extra.get("maximum_group"));
        }
        assertEquals(85, addedWeight);
        assertEquals("user_requested_extension", spec.getAsJsonObject("modern_adaptation").get("provenance").getAsString());
        assertFalse(spec.getAsJsonObject("r196_source").get("third_blood_moon_permanent_invasion").getAsBoolean());
    }
}
