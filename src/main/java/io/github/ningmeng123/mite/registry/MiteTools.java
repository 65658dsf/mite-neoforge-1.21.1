package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.R196ToolItem;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolProfile;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import static io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Source-registered R196 tools; deleted metal knives are deliberately absent. @author NingMeng123 */
public final class MiteTools {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    public record Definition(String id, ToolFamily family, String material, int legacyId, float speed,
            float damage, int enchantability, int harvestLevel, float reach, float materialDurability,
            Quality maxQuality) {
        public ToolProfile profile() { return new ToolProfile(family, materialDurability, maxQuality); }
        public boolean isNew() { return id.startsWith("mite:") && !id.equals("mite:flint_hatchet"); }
    }

    static {
        define("mite:flint_hatchet", HATCHET, "flint", 978, 2.5F, 3.0F, 0, 1, 0.5F, 1F, Quality.FINE);
        define("mite:obsidian_hatchet", HATCHET, "obsidian", 1204, 3.0F, 4.0F, 0, 2, 0.5F, 2F, Quality.FINE);
        define("mite:copper_hatchet", HATCHET, "copper", 1175, 3.5F, 5.0F, 30, 2, 0.5F, 4F, Quality.EXCELLENT);
        define("mite:silver_hatchet", HATCHET, "silver", 1176, 3.5F, 5.0F, 30, 2, 0.5F, 4F, Quality.EXCELLENT);
        define("mite:golden_hatchet", HATCHET, "gold", 1177, 3.5F, 4.0F, 50, 2, 0.5F, 4F, Quality.SUPERB);
        define("mite:rusted_iron_hatchet", HATCHET, "rusted_iron", 1181, 2.5F, 4.0F, 0, 2, 0.5F, 4F, Quality.POOR);
        define("mite:iron_hatchet", HATCHET, "iron", 1178, 4F, 6.0F, 30, 3, 0.5F, 8F, Quality.MASTERWORK);
        define("mite:ancient_metal_hatchet", HATCHET, "ancient_metal", 1262, 4F, 6.0F, 40, 3, 0.5F, 16F, Quality.MASTERWORK);
        define("mite:mithril_hatchet", HATCHET, "mithril", 1179, 5.0F, 7.0F, 100, 4, 0.5F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_hatchet", HATCHET, "adamantium", 1180, 6F, 8.0F, 40, 5, 0.5F, 256F, Quality.LEGENDARY);
        define("minecraft:golden_axe", AXE, "gold", 286, 7.0F, 5.0F, 50, 2, 0.75F, 4F, Quality.SUPERB);
        define("minecraft:iron_axe", AXE, "iron", 258, 8F, 7.0F, 30, 3, 0.75F, 8F, Quality.MASTERWORK);
        define("mite:flint_axe", AXE, "flint", 979, 5.0F, 4.0F, 0, 1, 0.75F, 1F, Quality.FINE);
        define("mite:obsidian_axe", AXE, "obsidian", 1205, 6.0F, 5.0F, 0, 2, 0.75F, 2F, Quality.FINE);
        define("mite:copper_axe", AXE, "copper", 980, 7.0F, 6.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:silver_axe", AXE, "silver", 981, 7.0F, 6.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:rusted_iron_axe", AXE, "rusted_iron", 984, 5.0F, 5.0F, 0, 2, 0.75F, 4F, Quality.POOR);
        define("mite:ancient_metal_axe", AXE, "ancient_metal", 1241, 8F, 7.0F, 40, 3, 0.75F, 16F, Quality.MASTERWORK);
        define("mite:mithril_axe", AXE, "mithril", 982, 10.0F, 8.0F, 100, 4, 0.75F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_axe", AXE, "adamantium", 983, 12F, 9.0F, 40, 5, 0.75F, 256F, Quality.LEGENDARY);
        define("minecraft:golden_pickaxe", PICKAXE, "gold", 285, 7.0F, 4.0F, 50, 2, 0.75F, 4F, Quality.SUPERB);
        define("minecraft:iron_pickaxe", PICKAXE, "iron", 257, 8F, 6.0F, 30, 3, 0.75F, 8F, Quality.MASTERWORK);
        define("mite:copper_pickaxe", PICKAXE, "copper", 967, 7.0F, 5.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:silver_pickaxe", PICKAXE, "silver", 968, 7.0F, 5.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:rusted_iron_pickaxe", PICKAXE, "rusted_iron", 971, 5.0F, 4.0F, 0, 2, 0.75F, 4F, Quality.POOR);
        define("mite:ancient_metal_pickaxe", PICKAXE, "ancient_metal", 1239, 8F, 6.0F, 40, 3, 0.75F, 16F, Quality.MASTERWORK);
        define("mite:mithril_pickaxe", PICKAXE, "mithril", 969, 10.0F, 7.0F, 100, 4, 0.75F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_pickaxe", PICKAXE, "adamantium", 970, 12F, 8.0F, 40, 5, 0.75F, 256F, Quality.LEGENDARY);
        define("minecraft:golden_shovel", SHOVEL, "gold", 284, 7.0F, 3.0F, 50, 2, 0.75F, 4F, Quality.SUPERB);
        define("minecraft:iron_shovel", SHOVEL, "iron", 256, 8F, 5.0F, 30, 3, 0.75F, 8F, Quality.MASTERWORK);
        define("mite:flint_shovel", SHOVEL, "flint", 972, 5.0F, 2.0F, 0, 1, 0.75F, 1F, Quality.FINE);
        define("mite:obsidian_shovel", SHOVEL, "obsidian", 1202, 6.0F, 3.0F, 0, 2, 0.75F, 2F, Quality.FINE);
        define("mite:copper_shovel", SHOVEL, "copper", 973, 7.0F, 4.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:silver_shovel", SHOVEL, "silver", 974, 7.0F, 4.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:rusted_iron_shovel", SHOVEL, "rusted_iron", 977, 5.0F, 3.0F, 0, 2, 0.75F, 4F, Quality.POOR);
        define("mite:ancient_metal_shovel", SHOVEL, "ancient_metal", 1240, 8F, 5.0F, 40, 3, 0.75F, 16F, Quality.MASTERWORK);
        define("mite:mithril_shovel", SHOVEL, "mithril", 975, 10.0F, 6.0F, 100, 4, 0.75F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_shovel", SHOVEL, "adamantium", 976, 12F, 7.0F, 40, 5, 0.75F, 256F, Quality.LEGENDARY);
        define("minecraft:golden_hoe", HOE, "gold", 294, 3.5F, 3.0F, 50, 2, 0.75F, 4F, Quality.SUPERB);
        define("minecraft:iron_hoe", HOE, "iron", 292, 4F, 5.0F, 30, 3, 0.75F, 8F, Quality.MASTERWORK);
        define("mite:copper_hoe", HOE, "copper", 985, 3.5F, 4.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:silver_hoe", HOE, "silver", 986, 3.5F, 4.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:rusted_iron_hoe", HOE, "rusted_iron", 989, 2.5F, 3.0F, 0, 2, 0.75F, 4F, Quality.POOR);
        define("mite:ancient_metal_hoe", HOE, "ancient_metal", 1242, 4F, 5.0F, 40, 3, 0.75F, 16F, Quality.MASTERWORK);
        define("mite:mithril_hoe", HOE, "mithril", 987, 5.0F, 6.0F, 100, 4, 0.75F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_hoe", HOE, "adamantium", 988, 6F, 7.0F, 40, 5, 0.75F, 256F, Quality.LEGENDARY);
        define("mite:copper_mattock", MATTOCK, "copper", 997, 5.25F, 4.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:silver_mattock", MATTOCK, "silver", 998, 5.25F, 4.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:golden_mattock", MATTOCK, "gold", 999, 5.25F, 3.0F, 50, 2, 0.75F, 4F, Quality.SUPERB);
        define("mite:rusted_iron_mattock", MATTOCK, "rusted_iron", 1003, 3.75F, 3.0F, 0, 2, 0.75F, 4F, Quality.POOR);
        define("mite:iron_mattock", MATTOCK, "iron", 1000, 6F, 5.0F, 30, 3, 0.75F, 8F, Quality.MASTERWORK);
        define("mite:ancient_metal_mattock", MATTOCK, "ancient_metal", 1244, 6F, 5.0F, 40, 3, 0.75F, 16F, Quality.MASTERWORK);
        define("mite:mithril_mattock", MATTOCK, "mithril", 1001, 7.5F, 6.0F, 100, 4, 0.75F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_mattock", MATTOCK, "adamantium", 1002, 9F, 7.0F, 40, 5, 0.75F, 256F, Quality.LEGENDARY);
        define("mite:copper_scythe", SCYTHE, "copper", 1011, 7.0F, 4.0F, 30, 2, 1.0F, 4F, Quality.EXCELLENT);
        define("mite:silver_scythe", SCYTHE, "silver", 1012, 7.0F, 4.0F, 30, 2, 1.0F, 4F, Quality.EXCELLENT);
        define("mite:golden_scythe", SCYTHE, "gold", 1013, 7.0F, 3.0F, 50, 2, 1.0F, 4F, Quality.SUPERB);
        define("mite:rusted_iron_scythe", SCYTHE, "rusted_iron", 1017, 5.0F, 3.0F, 0, 2, 1.0F, 4F, Quality.POOR);
        define("mite:iron_scythe", SCYTHE, "iron", 1014, 8F, 5.0F, 30, 3, 1.0F, 8F, Quality.MASTERWORK);
        define("mite:ancient_metal_scythe", SCYTHE, "ancient_metal", 1246, 8F, 5.0F, 40, 3, 1.0F, 16F, Quality.MASTERWORK);
        define("mite:mithril_scythe", SCYTHE, "mithril", 1015, 10.0F, 6.0F, 100, 4, 1.0F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_scythe", SCYTHE, "adamantium", 1016, 12F, 7.0F, 40, 5, 1.0F, 256F, Quality.LEGENDARY);
        define("mite:copper_shears", SHEARS, "copper", 1018, 7.0F, 3.0F, 30, 2, 0.5F, 4F, Quality.EXCELLENT);
        define("mite:silver_shears", SHEARS, "silver", 1019, 7.0F, 3.0F, 30, 2, 0.5F, 4F, Quality.EXCELLENT);
        define("mite:golden_shears", SHEARS, "gold", 1020, 7.0F, 2.0F, 50, 2, 0.5F, 4F, Quality.SUPERB);
        define("mite:rusted_iron_shears", SHEARS, "rusted_iron", 1182, 5.0F, 2.0F, 0, 2, 0.5F, 4F, Quality.POOR);
        define("mite:ancient_metal_shears", SHEARS, "ancient_metal", 1247, 8F, 4.0F, 40, 3, 0.5F, 16F, Quality.MASTERWORK);
        define("mite:mithril_shears", SHEARS, "mithril", 1021, 10.0F, 5.0F, 100, 4, 0.5F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_shears", SHEARS, "adamantium", 1022, 12F, 6.0F, 40, 5, 0.5F, 256F, Quality.LEGENDARY);
        define("mite:wooden_cudgel", CUDGEL, "wood", 1024, 2F, 1.0F, 10, 0, 0.25F, 0.5F, Quality.FINE);
        define("minecraft:wooden_sword", CLUB, "wood", 1025, 2F, 2.0F, 10, 0, 0.5F, 0.5F, Quality.FINE);
        define("mite:obsidian_knife", KNIFE, "obsidian", 1203, 3.0F, 3.0F, 0, 2, 0.25F, 2F, Quality.FINE);
        define("mite:flint_knife", KNIFE, "flint", 1023, 2.5F, 2.0F, 0, 1, 0.25F, 1F, Quality.FINE);
        define("mite:copper_dagger", DAGGER, "copper", 1033, 3.5F, 5.0F, 30, 2, 0.5F, 4F, Quality.EXCELLENT);
        define("mite:silver_dagger", DAGGER, "silver", 1034, 3.5F, 5.0F, 30, 2, 0.5F, 4F, Quality.EXCELLENT);
        define("mite:golden_dagger", DAGGER, "gold", 1035, 3.5F, 4.0F, 50, 2, 0.5F, 4F, Quality.SUPERB);
        define("mite:rusted_iron_dagger", DAGGER, "rusted_iron", 1039, 2.5F, 4.0F, 0, 2, 0.5F, 4F, Quality.POOR);
        define("mite:iron_dagger", DAGGER, "iron", 1036, 4F, 6.0F, 30, 3, 0.5F, 8F, Quality.MASTERWORK);
        define("mite:ancient_metal_dagger", DAGGER, "ancient_metal", 1249, 4F, 6.0F, 40, 3, 0.5F, 16F, Quality.MASTERWORK);
        define("mite:mithril_dagger", DAGGER, "mithril", 1037, 5.0F, 7.0F, 100, 4, 0.5F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_dagger", DAGGER, "adamantium", 1038, 6F, 8.0F, 40, 5, 0.5F, 256F, Quality.LEGENDARY);
        define("mite:copper_sword", SWORD, "copper", 1028, 3.5F, 7.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("minecraft:golden_sword", SWORD, "gold", 283, 3.5F, 6.0F, 50, 2, 0.75F, 4F, Quality.SUPERB);
        define("minecraft:iron_sword", SWORD, "iron", 267, 4F, 8.0F, 30, 3, 0.75F, 8F, Quality.MASTERWORK);
        define("mite:silver_sword", SWORD, "silver", 1029, 3.5F, 7.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:rusted_iron_sword", SWORD, "rusted_iron", 1032, 2.5F, 6.0F, 0, 2, 0.75F, 4F, Quality.POOR);
        define("mite:ancient_metal_sword", SWORD, "ancient_metal", 1248, 4F, 8.0F, 40, 3, 0.75F, 16F, Quality.MASTERWORK);
        define("mite:mithril_sword", SWORD, "mithril", 1030, 5.0F, 9.0F, 100, 4, 0.75F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_sword", SWORD, "adamantium", 1031, 6F, 10.0F, 40, 5, 0.75F, 256F, Quality.LEGENDARY);
        define("mite:copper_battle_axe", BATTLE_AXE, "copper", 1004, 5.25F, 7.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:silver_battle_axe", BATTLE_AXE, "silver", 1005, 5.25F, 7.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:golden_battle_axe", BATTLE_AXE, "gold", 1006, 5.25F, 6.0F, 50, 2, 0.75F, 4F, Quality.SUPERB);
        define("mite:rusted_iron_battle_axe", BATTLE_AXE, "rusted_iron", 1010, 3.75F, 6.0F, 0, 2, 0.75F, 4F, Quality.POOR);
        define("mite:iron_battle_axe", BATTLE_AXE, "iron", 1007, 6F, 8.0F, 30, 3, 0.75F, 8F, Quality.MASTERWORK);
        define("mite:ancient_metal_battle_axe", BATTLE_AXE, "ancient_metal", 1245, 6F, 8.0F, 40, 3, 0.75F, 16F, Quality.MASTERWORK);
        define("mite:mithril_battle_axe", BATTLE_AXE, "mithril", 1008, 7.5F, 9.0F, 100, 4, 0.75F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_battle_axe", BATTLE_AXE, "adamantium", 1009, 9F, 10.0F, 40, 5, 0.75F, 256F, Quality.LEGENDARY);
        define("mite:copper_war_hammer", WAR_HAMMER, "copper", 990, 5.25F, 5.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:silver_war_hammer", WAR_HAMMER, "silver", 991, 5.25F, 5.0F, 30, 2, 0.75F, 4F, Quality.EXCELLENT);
        define("mite:golden_war_hammer", WAR_HAMMER, "gold", 992, 5.25F, 4.0F, 50, 2, 0.75F, 4F, Quality.SUPERB);
        define("mite:rusted_iron_war_hammer", WAR_HAMMER, "rusted_iron", 996, 3.75F, 4.0F, 0, 2, 0.75F, 4F, Quality.POOR);
        define("mite:iron_war_hammer", WAR_HAMMER, "iron", 993, 6F, 6.0F, 30, 3, 0.75F, 8F, Quality.MASTERWORK);
        define("mite:ancient_metal_war_hammer", WAR_HAMMER, "ancient_metal", 1243, 6F, 6.0F, 40, 3, 0.75F, 16F, Quality.MASTERWORK);
        define("mite:mithril_war_hammer", WAR_HAMMER, "mithril", 994, 7.5F, 7.0F, 100, 4, 0.75F, 64F, Quality.LEGENDARY);
        define("mite:adamantium_war_hammer", WAR_HAMMER, "adamantium", 995, 9F, 8.0F, 40, 5, 0.75F, 256F, Quality.LEGENDARY);
        define("minecraft:shears", SHEARS, "iron", 359, 8F, 4.0F, 30, 3, 0.5F, 8F, Quality.MASTERWORK);
        define("minecraft:wooden_shovel", SHOVEL, "wood", 269, 4F, 1.0F, 10, 0, 0.75F, 0.5F, Quality.FINE);
    }

    private MiteTools() {}

    private static void define(String id, ToolFamily family, String material, int legacyId, float speed,
            float damage, int enchantability, int harvestLevel, float reach, float materialDurability, Quality maxQuality) {
        Definition definition = new Definition(id, family, material, legacyId, speed, damage, enchantability,
                harvestLevel, reach, materialDurability, maxQuality);
        DEFINITIONS.put(id, definition);
        if (definition.isNew()) ITEMS.registerItem(id.substring(5), props -> new R196ToolItem(definition, props));
    }

    public static List<Definition> definitions() { return List.copyOf(DEFINITIONS.values()); }

    public static Item get(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id.contains(":") ? id : "mite:" + id));
    }

    public static Definition definition(ItemStack stack) {
        return DEFINITIONS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static String requiredBenchMaterial(ItemStack result) {
        Definition definition = definition(result);
        if (definition == null) return null;
        return switch (definition.material()) {
            case "wood", "flint", "obsidian", "rusted_iron" -> null;
            default -> definition.material();
        };
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(EventPriority.LOWEST, MiteTools::modifyDefaults);
        bus.addListener(MiteTools::creative);
    }

    private static void modifyDefaults(ModifyDefaultComponentsEvent event) {
        for (Definition definition : definitions()) {
            Item item = get(definition.id());
            EquipmentDurability.registerTool(item, definition.profile());
            if (definition.isNew()) continue;
            event.modify(item, components -> {
                components.set(DataComponents.MAX_DAMAGE, EquipmentDurabilityRules.baseToolDurability(definition.profile()));
                // Keep starter behavior and modern wooden-shovel behavior, and keep modern shears' full target set.
                if (!definition.material().equals("wood") && !definition.id().equals("mite:flint_hatchet")) {
                    components.set(DataComponents.TOOL, R196ToolItem.tool(definition));
                    components.set(DataComponents.ATTRIBUTE_MODIFIERS, R196ToolItem.attributes(definition));
                }
            });
        }
        modern(event, "stone", 1.0F, Quality.FINE);
        modern(event, "diamond", 16.0F, Quality.SUPERB);
        modern(event, "netherite", 32.0F, Quality.LEGENDARY);
        ToolProfile woodenHoe = new ToolProfile(HOE, 0.5F, Quality.FINE);
        EquipmentDurability.registerTool(Items.WOODEN_HOE, woodenHoe);
        event.modify(Items.WOODEN_HOE, c -> c.set(DataComponents.MAX_DAMAGE, EquipmentDurabilityRules.baseToolDurability(woodenHoe)));
    }

    private static void modern(ModifyDefaultComponentsEvent event, String material, float factor, Quality quality) {
        for (ToolFamily family : List.of(PICKAXE, AXE, SHOVEL, HOE, SWORD)) {
            Item item = get("minecraft:" + material + "_" + family.name().toLowerCase(java.util.Locale.ROOT));
            ToolProfile profile = new ToolProfile(family, factor, quality);
            EquipmentDurability.registerTool(item, profile);
            event.modify(item, components -> components.set(DataComponents.MAX_DAMAGE,
                    EquipmentDurabilityRules.baseToolDurability(profile)));
        }
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        for (Definition definition : definitions()) {
            if (!definition.isNew()) continue;
            boolean combat = switch (definition.family()) {
                case SWORD, DAGGER, BATTLE_AXE, WAR_HAMMER, CLUB, CUDGEL -> true;
                default -> false;
            };
            if (event.getTabKey().equals(combat ? CreativeModeTabs.COMBAT : CreativeModeTabs.TOOLS_AND_UTILITIES)) {
                event.accept(get(definition.id()));
            }
        }
    }
}
