package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Explicit registered equipment catalog shared by both real death routes. @author NingMeng123 */
final class HighTierEquipmentFixtures {
    private HighTierEquipmentFixtures() {}

    static List<Item> items() {
        List<Item> result = new ArrayList<>();
        for (String material : List.of("mithril", "adamantium", "vibranium")) {
            for (String suffix : List.of("sword", "pickaxe", "shield", "helmet", "chestplate", "leggings", "boots"))
                result.add(R196MobEquipment.item("mite:" + material + "_" + suffix));
            if (!material.equals("vibranium")) {
                for (String suffix : List.of("helmet", "chestplate", "leggings", "boots"))
                    result.add(R196MobEquipment.item("mite:" + material + "_chain_" + suffix));
                result.add(R196MobEquipment.item("mite:" + material + "_horse_armor"));
                result.add(R196MobEquipment.item("mite:fishing_rod_" + material + "_hook"));
                result.add(R196MobEquipment.item("mite:carrot_on_a_stick_" + material + "_hook"));
            }
            if (SimplySwordsCompat.enabled()) for (String family : SimplySwordsCompat.families()) {
                Item item = SimplySwordsCompat.weapon(material, family);
                if (item == Items.AIR) throw new AssertionError("Missing optional high-tier weapon " + material + " / " + family);
                result.add(item);
            }
        }
        result.add(R196MobEquipment.item("mite:mithril_bow"));
        result.add(R196MobEquipment.item("mite:vibranium_bow"));
        return result;
    }
}
