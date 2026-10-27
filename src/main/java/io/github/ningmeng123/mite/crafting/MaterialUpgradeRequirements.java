package io.github.ningmeng123.mite.crafting;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.furnace.EquipmentRecycling;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
import io.github.ningmeng123.mite.rules.MaterialUpgradeCostRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemStack;

/** Shared material requirements for server smithing, previews and optional addons. @author NingMeng123 */
public final class MaterialUpgradeRequirements {
    private static final Map<String, Integer> SIMPLY_SWORDS_COSTS = simplySwordsCosts();

    private MaterialUpgradeRequirements() {}

    public static boolean usesTemplate(ItemStack template) {
        return template.is(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get());
    }

    public static int ingots(ItemStack result) {
        if ("ancient_metal".equals(EquipmentRecycling.material(result))) return 2;
        int original = 1;
        var simply = SimplySwordsCompat.definition(result);
        var armor = EquipmentDurability.armorProfile(result);
        var tool = EquipmentDurability.toolProfile(result);
        if (simply != null) original = SIMPLY_SWORDS_COSTS.getOrDefault(simply.family(), 1);
        else if (result.getItem() instanceof AnimalArmorItem) original = 1;
        else if (armor != null) original = armor.chainmail()
                ? MaterialUpgradeCostRules.roundedIngots(0, armor.components() * 4) : armor.components();
        else if (tool != null) original = tool.family().components();
        return Math.max(1, MiteExtensions.policy().materialUpgradeIngots(result, original));
    }

    public static float difficulty(ItemStack result) {
        float difficulty = CraftingQuality.lowestDifficulty(result);
        return Float.isFinite(difficulty) && difficulty > 0F ? difficulty : 100F;
    }

    private static Map<String, Integer> simplySwordsCosts() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(MaterialUpgradeRequirements.class.getResourceAsStream(
                "/mite/simplyswords-compat-assets.json")), StandardCharsets.UTF_8)) {
            Map<String, Integer> costs = new HashMap<>();
            for (var element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("items")) {
                var item = element.getAsJsonObject();
                int cost = MaterialUpgradeCostRules.roundedIngots(item.get("ingots").getAsInt(), item.get("nuggets").getAsInt());
                String family = item.get("family").getAsString();
                Integer previous = costs.putIfAbsent(family, cost);
                if (previous != null && previous != cost) throw new IllegalStateException("Inconsistent weapon metal cost: " + family);
            }
            return Map.copyOf(costs);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load Simply Swords equipment metal costs", exception);
        }
    }
}
