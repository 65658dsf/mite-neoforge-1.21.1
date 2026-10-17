package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.R196ShieldItem;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ShieldProfile;
import io.github.ningmeng123.mite.rules.VibraniumEquipmentRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Modern shields with source metal durability, quality and repair identities. @author NingMeng123 */
public final class MiteShields {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    public record Definition(String id, String material, float materialDurability, int enchantability,
            Quality maxQuality) {
        public ShieldProfile profile() { return new ShieldProfile(materialDurability, maxQuality); }
        public int baseDurability() { return EquipmentDurabilityRules.baseShieldDurability(profile()); }
        public boolean isNew() { return !id.equals("minecraft:shield"); }
        /** Modern six-plank/one-ingot recipe: source components cost 80 per plank and 100 per metal durability. */
        public float craftingDifficulty() { return 6F * 80F + materialDurability * 100F; }
    }

    static {
        define("rusted_iron", 4F, 0, Quality.POOR);
        define("copper", 4F, 30, Quality.EXCELLENT);
        define("silver", 4F, 30, Quality.EXCELLENT);
        define("gold", 4F, 50, Quality.SUPERB);
        define("iron", 8F, 30, Quality.MASTERWORK);
        define("ancient_metal", 16F, 40, Quality.MASTERWORK);
        define("mithril", 64F, 100, Quality.LEGENDARY);
        define("adamantium", 256F, 40, Quality.LEGENDARY);
        // Shields are the project's modern extension, retaining the same source material ratios.
        define("vibranium", VibraniumEquipmentRules.MATERIAL_DURABILITY,
                VibraniumEquipmentRules.ENCHANTABILITY, VibraniumEquipmentRules.MAXIMUM_QUALITY);
    }

    private MiteShields() {}

    private static void define(String material, float durability, int enchantability, Quality maxQuality) {
        String id = material.equals("iron") ? "minecraft:shield" : "mite:" + material + "_shield";
        Definition definition = new Definition(id, material, durability, enchantability, maxQuality);
        DEFINITIONS.put(id, definition);
        if (definition.isNew()) ITEMS.registerItem(material + "_shield", props -> new R196ShieldItem(definition,
                material.equals("vibranium") ? props.fireResistant() : props));
    }

    public static List<Definition> definitions() { return List.copyOf(DEFINITIONS.values()); }

    /** Material names are the shared contract used by generated loot, AI and recipes. */
    public static Item get(String material) {
        if (material.equals("iron") || material.equals("minecraft:shield")) return Items.SHIELD;
        String id = material.indexOf(':') >= 0 ? material : "mite:" + (material.endsWith("_shield") ? material : material + "_shield");
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    public static Definition definition(ItemStack stack) {
        return DEFINITIONS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(MiteShields::modifyDefaults);
    }

    private static void modifyDefaults(ModifyDefaultComponentsEvent event) {
        for (Definition definition : definitions()) {
            Item item = get(definition.material());
            EquipmentDurability.registerShield(item, definition.profile());
            if (!definition.isNew())
                event.modify(item, components -> components.set(DataComponents.MAX_DAMAGE, definition.baseDurability()));
        }
    }
}
