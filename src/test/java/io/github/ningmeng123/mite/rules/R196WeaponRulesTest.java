package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent source literals and archive bytes, not mirrors of the generated runtime data. @author NingMeng123 */
class R196WeaponRulesTest {
    @Test void allMaterialsRetainTheExactSourceBonusAndItsSeparatePlayerBasePoint() {
        assertEquals(1F, R196WeaponRules.DEFAULT_MELEE_DAMAGE_FACTOR);
        for (String material : new String[]{"wood", "stone", "flint", "obsidian", "rusted_iron", "gold",
                "copper", "silver", "iron", "ancient_metal", "mithril", "adamantium", "vibranium", "diamond", "netherite"}) {
            assertEquals(1F, R196WeaponRules.meleeDamageFactor(material));
            for (float bonus : new float[]{-.75F, 0F, .25F, 3F, 5F, 6F, 7F, 10F, 11F, Float.MAX_VALUE})
                assertEquals(bonus, R196WeaponRules.meleeDamageBonus(material, bonus));
        }
        assertEquals(8F, R196WeaponRules.meleeDamageBonus("copper", 7F) + 1F);
        assertEquals(8F, R196WeaponRules.meleeDamageBonus("silver", 7F) + 1F);
        assertEquals(9F, R196WeaponRules.meleeDamageBonus("iron", 8F) + 1F);
        assertEquals(14F, R196WeaponRules.meleeDamageBonus("vibranium", 13F) + 1F);
    }
    @Test void sphereImpactIncludesTheSourceProjectileExtraPoint() {
        assertArrayEquals(new int[]{2,3,4,4,5}, java.util.stream.IntStream.range(0,5).map(R196WeaponRules::sphereDamage).toArray());
        assertThrows(IllegalArgumentException.class, () -> R196WeaponRules.sphereDamage(5));
    }
    @Test void onlyGrayAndBlackHaveAcidicBlockContact() {
        for (int i = 0; i < 5; i++) assertEquals(i >= 3, R196WeaponRules.acidic(i));
    }
    @Test void reinforcedVelocityAndExperienceSpreadAreLiteralUhValues() {
        assertEquals(1F, R196WeaponRules.velocityMultiplier("wood"));
        assertEquals(1.1F, R196WeaponRules.velocityMultiplier("ancient_metal"));
        assertEquals(1.25F, R196WeaponRules.velocityMultiplier("mithril"));
        assertEquals(1.75F, R196WeaponRules.velocityMultiplier("vibranium"));
        assertEquals(5F, R196WeaponRules.inaccuracy(0, false));
        assertEquals(5.5F, R196WeaponRules.inaccuracy(-1, false));
        assertEquals(10F, R196WeaponRules.inaccuracy(0, true));
        assertEquals((float)(.5 + 4.5 / Math.pow(.8F + 11F/5F, 2)), R196WeaponRules.inaccuracy(10, false));
        assertTrue(R196WeaponRules.inaccuracy(Integer.MAX_VALUE, false) >= .5F);
    }
    @Test void bowArchiveRecipesPreserveBothDifficultiesAndGuardedCorrosionHasNoWorld() throws Exception {
        var spec = JsonParser.parseString(Files.readString(Path.of("spec/weapons-028.json"))).getAsJsonObject();
        var bows = spec.getAsJsonArray("bows");
        assertEquals(1750F, bows.get(0).getAsJsonObject().get("difficulty").getAsFloat());
        assertEquals(6550F, bows.get(1).getAsJsonObject().get("difficulty").getAsFloat());
        assertEquals(64, bows.get(0).getAsJsonObject().get("durability").getAsInt());
        assertEquals(128, bows.get(1).getAsJsonObject().get("durability").getAsInt());
        assertEquals("masterwork", bows.get(0).getAsJsonObject().get("maximum_quality").getAsString());
        assertEquals("legendary", bows.get(1).getAsJsonObject().get("maximum_quality").getAsString());
        var contact = JsonParser.parseString(Files.readString(Path.of("spec/sphere-corrosion-028.json"))).getAsJsonObject();
        assertFalse(contact.get("world_created").getAsBoolean()); assertFalse(contact.get("game_main_invoked").getAsBoolean());
        assertEquals(7, contact.getAsJsonArray("guard_checks_passed").size());
        for (var row : contact.getAsJsonArray("rows")) {
            var data = row.getAsJsonObject(); int id = data.get("legacy_id").getAsInt();
            if (id == 30) { assertEquals(0, data.get("pepsin").getAsInt()); assertEquals(0, data.get("acid").getAsInt()); }
            if (id == 35) { assertEquals(400, data.get("pepsin").getAsInt()); assertEquals(0, data.get("acid").getAsInt()); }
            if (id == 42) assertEquals(400, data.get("acid").getAsInt());
            if (id == 41) assertEquals(-1, data.get("acid").getAsInt());
        }
    }
    @Test void allSixtySevenTextureCopiesMatchSourceHashesAndTheAvailableArchive() throws Exception {
        var textures = JsonParser.parseString(Files.readString(Path.of("spec/weapons-028.json"))).getAsJsonObject().getAsJsonArray("textures");
        assertEquals(67, textures.size());
        for (var element : textures) {
            var row = element.getAsJsonObject();
            byte[] actual = Files.readAllBytes(Path.of("src/main/resources").resolve(row.get("target").getAsString()));
            assertEquals(row.get("sha256").getAsString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual)));
        }
        // Original analysis archives are deliberately not distributed with the repository.
        if (Files.exists(Path.of("MITE Resource Pack 1.6.4.zip"))) try (var archive = new ZipFile("MITE Resource Pack 1.6.4.zip")) {
            for (var element : textures) {
                var row = element.getAsJsonObject();
                assertArrayEquals(archive.getInputStream(archive.getEntry(row.get("source").getAsString())).readAllBytes(),
                        Files.readAllBytes(Path.of("src/main/resources").resolve(row.get("target").getAsString())));
            }
        }
    }
}
