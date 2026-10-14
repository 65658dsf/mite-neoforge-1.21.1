package io.github.ningmeng123.mite.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.R196ArmorItem;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ArmorProfile;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Armor identities and exact source profiles; horse armor deliberately has no invented crafting recipe. @author NingMeng123 */
public final class MiteArmorContent {
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final Map<String, DeferredHolder<ArmorMaterial, ArmorMaterial>> ARMOR_MATERIALS = new LinkedHashMap<>();
    private static final List<Definition> DEFINITIONS = new ArrayList<>();
    private static final List<ChainDefinition> CHAINS = new ArrayList<>();

    public record Definition(String id, int legacyId, String material, ArmorItem.Type type, int components,
            boolean chainmail, boolean horse, float materialDurability, int materialProtection,
            int enchantability, Quality maximumQuality, int horseProtection, int baseDurability) {
        public boolean isNew() { return id.startsWith("mite:"); }
        public ArmorProfile profile() {
            return new ArmorProfile(components, materialDurability, materialProtection, chainmail, maximumQuality);
        }
        public String materialKey() { return material + (chainmail ? "_chainmail" : ""); }
    }
    public record ChainDefinition(String id, int legacyId, String material, int maximumStack) {}

    static {
        JsonObject source = source();
        source.getAsJsonArray("items").forEach(element -> {
            JsonObject item = element.getAsJsonObject();
            Definition definition = new Definition(item.get("id").getAsString(), item.get("legacy_id").getAsInt(),
                    item.get("material").getAsString(), ArmorItem.Type.valueOf(item.get("slot").getAsString().toUpperCase(java.util.Locale.ROOT)),
                    item.get("components").getAsInt(), item.get("chainmail").getAsBoolean(), item.get("horse").getAsBoolean(),
                    item.get("material_durability").getAsFloat(), item.get("material_protection").getAsInt(),
                    item.get("enchantability").getAsInt(), Quality.valueOf(item.get("maximum_quality").getAsString()),
                    item.get("horse_protection").getAsInt(), item.get("base_durability").getAsInt());
            DEFINITIONS.add(definition);
            if (!definition.isNew()) return;
            DeferredHolder<ArmorMaterial, ArmorMaterial> material = ARMOR_MATERIALS.computeIfAbsent(definition.materialKey(), key ->
                    MATERIALS.register(key, () -> armorMaterial(definition)));
            ITEMS.registerItem(definition.id().substring(5), properties -> definition.horse()
                    ? new AnimalArmorItem(material, AnimalArmorItem.BodyType.EQUESTRIAN, false, properties.stacksTo(1))
                    : new R196ArmorItem(material, definition, properties));
        });
        source.getAsJsonArray("chains").forEach(element -> {
            JsonObject item = element.getAsJsonObject();
            ChainDefinition definition = new ChainDefinition(item.get("id").getAsString(), item.get("legacy_id").getAsInt(),
                    item.get("material").getAsString(), item.get("maximum_stack").getAsInt());
            CHAINS.add(definition);
            if (definition.id().startsWith("mite:")) ITEMS.registerSimpleItem(definition.id().substring(5), new Item.Properties().stacksTo(definition.maximumStack()));
        });
    }

    private MiteArmorContent() {}
    public static List<Definition> definitions() { return List.copyOf(DEFINITIONS); }
    public static List<ChainDefinition> chains() { return List.copyOf(CHAINS); }
    public static Definition definition(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return DEFINITIONS.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }
    public static Item get(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    /** ItemHorseArmor.getProtection; native iron/gold are outside the added-item definition list. */
    public static int horseProtection(ItemStack stack) {
        if (stack.is(Items.GOLDEN_HORSE_ARMOR)) return 3;
        if (stack.is(Items.IRON_HORSE_ARMOR)) return 5;
        Definition source=definition(stack.getItem());
        if (source!=null && source.horse()) return source.horseProtection();
        return stack.getItem() instanceof AnimalArmorItem modern ? modern.getMaterial().value().getDefense(ArmorItem.Type.BODY) : 0;
    }
    public static Item chain(String material) {
        return get(CHAINS.stream().filter(d -> d.material().equals(material)).findFirst().orElseThrow().id());
    }
    public static JsonObject source() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(MiteArmorContent.class.getResourceAsStream(
                "/mite/armor-content-027.json")), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load R196 armor evidence", exception);
        }
    }
    private static ArmorMaterial armorMaterial(Definition definition) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            int components = switch (type) { case HELMET -> 5; case CHESTPLATE -> 8; case LEGGINGS -> 7; case BOOTS -> 4; case BODY -> 0; };
            defense.put(type, type == ArmorItem.Type.BODY ? definition.horseProtection()
                    : Math.max((components * (definition.materialProtection() - (definition.chainmail() ? 2 : 0))) / 24, 0));
        }
        return new ArmorMaterial(Map.copyOf(defense), definition.enchantability(), SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(repairMaterial(definition)),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, definition.materialKey()))), 0F, 0F);
    }
    public static Item repairMaterial(Definition definition) {
        if (definition.chainmail()) return chain(definition.material());
        return switch (definition.material()) {
            case "copper" -> Items.COPPER_INGOT;
            case "gold" -> Items.GOLD_INGOT;
            case "iron", "rusted_iron" -> Items.IRON_INGOT;
            default -> get("mite:" + definition.material() + "_ingot");
        };
    }
    public static void register(IEventBus bus) {
        MATERIALS.register(bus);
        ITEMS.register(bus);
        bus.addListener(MiteArmorContent::modifyDefaults);
        bus.addListener(MiteArmorContent::creative);
        ArmorLootIntegration.register(bus);
    }
    private static void modifyDefaults(ModifyDefaultComponentsEvent event) {
        // The retained BlockItem still places modern chains. Source chains are eight-stack metal components.
        event.modify(Items.CHAIN, builder -> builder.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 8));
        // R196 gold barding is three, while modern gold is seven. Keep displayed attributes and damage in agreement.
        var gold=net.minecraft.world.item.component.ItemAttributeModifiers.builder();
        for (var entry:Items.GOLDEN_HORSE_ARMOR.getDefaultAttributeModifiers().modifiers()) {
            var modifier=entry.modifier();
            gold.add(entry.attribute(),entry.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
                    ? new net.minecraft.world.entity.ai.attributes.AttributeModifier(modifier.id(),3D,modifier.operation()) : modifier,entry.slot());
        }
        event.modify(Items.GOLDEN_HORSE_ARMOR,builder->builder.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,gold.build()));
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.COMBAT))
            definitions().stream().filter(Definition::isNew).forEach(d -> event.accept(get(d.id())));
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS))
            chains().stream().filter(d -> d.id().startsWith("mite:")).forEach(d -> event.accept(get(d.id())));
    }
}
