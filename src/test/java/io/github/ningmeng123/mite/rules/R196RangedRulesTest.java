package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Literal source expectations, separate from the production recipe representation. @author NingMeng123 */
class R196RangedRulesTest {
    @Test void registeredArrowValuesMatchItemArrowAndAkc() {
        float[] damage = {1F, 1.5F, 2F, 2F, 1.5F, 1.5F, 2.5F, 2.5F, 3F, 3.5F};
        float[] recovery = {.3F, .4F, .6F, .6F, .5F, .5F, .7F, .8F, .8F, .9F};
        for (int i = 0; i < damage.length; i++) {
            assertEquals(damage[i], Material.values()[i].arrowDamage());
            assertEquals(recovery[i], Material.values()[i].recovery);
        }
        assertFalse(Material.RUSTED_IRON.hasCraftableArrow());
    }
    @Test void NineRodsHaveSmallUnscaledDurabilityAndMaterialEnchantability() {
        int[] durability = {3, 4, 8, 8, 8, 8, 16, 32, 128, 512};
        int[] enchantability = {0, 0, 30, 30, 0, 50, 30, 40, 100, 40};
        for (int i = 0; i < durability.length; i++) {
            assertEquals(durability[i], Material.values()[i].rodDurability());
            assertEquals(enchantability[i], Material.values()[i].enchantability);
        }
        assertFalse(Material.RUSTED_IRON.hasRod());
    }
    @Test void DawnAndDuskUseAdjustedTimeAndTheSameMinimum() {
        assertEquals(600, R196RangedRules.biteChanceDenominator(23500L, false, false, 0, false));
        assertEquals(600, R196RangedRules.biteChanceDenominator(11500L, false, false, 0, false));
        assertEquals(2400, R196RangedRules.biteChanceDenominator(6000L, false, false, 0, false));
        assertEquals(600, R196RangedRules.biteChanceDenominator(6000L, true, false, 0, false));
        assertEquals(600, R196RangedRules.biteChanceDenominator(23500L + 24000L * 200L, false, false, 0, false));
    }
    @Test void RainFortuneAndWormHaveSourceOrderWithIntegerTruncation() {
        assertEquals(300, R196RangedRules.biteChanceDenominator(23500L, false, true, 0, false));
        assertEquals(300, R196RangedRules.biteChanceDenominator(23500L, false, false, 0, true));
        assertEquals(1200, R196RangedRules.biteChanceDenominator(6000L, false, false, 0, true));
        assertEquals(109, R196RangedRules.biteChanceDenominator(23500L, false, true, 3, true)); // 300→270→243→218→109.
    }
    @Test void SilverBonusAndSkeletonWeaknessApplyBeforeCriticalMultiplier() {
        assertEquals(7.5F, R196RangedRules.impactDamage(Material.SILVER, 3F, 6F, true, false, false, false, false, false, 0F));
        assertEquals(1.875F, R196RangedRules.impactDamage(Material.SILVER, 3F, 6F, true, true, false, false, false, false, 0F));
        assertEquals(3.28125F, R196RangedRules.impactDamage(Material.SILVER, 3F, 6F, true, true, false, false, false, true, .5F));
        assertEquals(0F, R196RangedRules.impactDamage(Material.ADAMANTIUM, .99F, 50F, false, false, false, false, false, false, 0F));
    }
    @Test void SkeletonAndDispenserHaveSourceMinimaButModernPlayersKeepVelocityDamage() {
        assertEquals(9F, R196RangedRules.impactDamage(Material.ADAMANTIUM, 1F, 3.5F, false, false, false, true, false, false, 0F));
        assertEquals(4F, R196RangedRules.impactDamage(Material.FLINT, 3F, 3F, false, false, true, false, false, false, 0F));
        assertEquals(4F, R196RangedRules.impactDamage(Material.FLINT, 3F, 100F, false, false, true, false, false, false, 0F));
        assertEquals(100F, R196RangedRules.impactDamage(Material.FLINT, 3F, 100F, false, false, true, false, true, false, 0F));
        assertEquals(3F, R196RangedRules.impactDamage(Material.FLINT, 3F, 3F, false, false, false, false, false, false, 0F));
    }
    @Test void ProductionRecipeDatasetCoversAllFortyFiveRegisteredSourceRecipes() throws Exception {
        var source = JsonParser.parseString(Files.readString(Path.of("spec/ranged-027.json"))).getAsJsonObject();
        var actual = JsonParser.parseString(Files.readString(Path.of("src/main/resources/mite/ranged-recipes-027.json"))).getAsJsonObject().getAsJsonArray("recipes");
        var rules = JsonParser.parseString(Files.readString(Path.of("src/main/resources/mite/timed-crafting-027-ranged.json"))).getAsJsonObject().getAsJsonObject("recipes");
        assertEquals(45, actual.size());
        assertEquals(45, source.getAsJsonArray("registered_recipes").size());
        assertEquals(45, rules.size());
        HashSet<Integer> orders = new HashSet<>();
        for (var row : source.getAsJsonArray("registered_recipes")) orders.add(row.getAsJsonObject().get("order").getAsInt());
        int crafts = 0, dismantles = 0, conversions = 0;
        for (var row : actual) {
            var value = row.getAsJsonObject();
            assertTrue(orders.remove(value.get("source_order").getAsInt()));
            assertEquals(1, value.get("count").getAsInt());
            switch (value.get("kind").getAsString()) {
                case "shaped" -> crafts++;
                case "shapeless" -> dismantles++;
                case "rod_conversion" -> conversions++;
                default -> fail("Unknown recipe");
            }
            assertFalse(value.get("id").getAsString().contains("rusted"));
        }
        assertTrue(orders.isEmpty());
        assertEquals(18, crafts); assertEquals(9, dismantles); assertEquals(18, conversions);
    }
}
