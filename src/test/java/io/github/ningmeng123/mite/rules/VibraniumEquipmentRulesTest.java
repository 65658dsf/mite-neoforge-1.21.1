package io.github.ningmeng123.mite.rules;

import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ShieldProfile;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolProfile;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.*;
import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent source material, family and later-expansion capacity expectations. @author NingMeng123 */
class VibraniumEquipmentRulesTest {
    @Test void sourceMaterialRetainsItsLegendaryCapAndExactDurabilityScale() {
        assertEquals(512F, VibraniumEquipmentRules.MATERIAL_DURABILITY);
        assertEquals(120, VibraniumEquipmentRules.ENCHANTABILITY);
        assertEquals(6, VibraniumEquipmentRules.HARVEST_LEVEL);
        assertEquals(15, VibraniumEquipmentRules.MATERIAL_PROTECTION);
        assertEquals(Quality.LEGENDARY, VibraniumEquipmentRules.MAXIMUM_QUALITY);
        assertEquals(614400, baseToolDurability(new ToolProfile(PICKAXE, 512F, Quality.LEGENDARY)));
        assertEquals(2150400, maxDurability(614400, Quality.LEGENDARY));
        assertEquals(10752, baseShieldDurability(new ShieldProfile(512F, Quality.LEGENDARY)));
    }

    @Test void armorUsesProjectSourceCapacityInsteadOfCopyingModernVanillaDurability() {
        int[] components = {5, 8, 7, 4};
        int[] capacities = {5120, 8192, 7168, 4096};
        for (int i = 0; i < components.length; i++) {
            var profile = VibraniumEquipmentRules.armorProfile(components[i]);
            assertEquals(capacities[i], baseArmorDurability(profile));
            assertFalse(profile.chainmail());
            assertEquals(15, profile.materialProtection());
            assertEquals(Quality.LEGENDARY, profile.maxQuality());
        }
    }

    @Test void expansionCoversTheTwelveExistingMetalFamiliesAndItsNativeKnife() {
        assertEquals(13, VibraniumEquipmentRules.TOOL_FAMILIES.size());
        assertEquals(13, new HashSet<>(VibraniumEquipmentRules.TOOL_FAMILIES).size());
        assertEquals(new VibraniumEquipmentRules.ToolStats(7F, 13F, 0.75F), VibraniumEquipmentRules.toolStats(SWORD));
        assertEquals(new VibraniumEquipmentRules.ToolStats(14F, 11F, 0.75F), VibraniumEquipmentRules.toolStats(PICKAXE));
        assertEquals(new VibraniumEquipmentRules.ToolStats(10.5F, 11F, 0.75F), VibraniumEquipmentRules.toolStats(WAR_HAMMER));
        assertEquals(new VibraniumEquipmentRules.ToolStats(7F, 10F, 0.25F), VibraniumEquipmentRules.toolStats(KNIFE));
        assertEquals(new VibraniumEquipmentRules.ToolStats(14F, 9F, 0.5F), VibraniumEquipmentRules.toolStats(SHEARS));
        assertThrows(IllegalArgumentException.class, () -> VibraniumEquipmentRules.toolStats(CLUB));
        assertThrows(IllegalArgumentException.class, () -> VibraniumEquipmentRules.toolStats(CUDGEL));
    }
}
