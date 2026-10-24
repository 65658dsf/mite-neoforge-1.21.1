package io.github.ningmeng123.mite.compat.simplyswords;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolProfile;
import io.github.ningmeng123.mite.rules.SimplySwordsBalanceRules;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Optional Simply Swords 1.70 integration, retaining its item classes and combat callbacks. @author NingMeng123 */
public final class SimplySwordsCompat {
    public static final String MOD_ID = "simplyswords";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final List<String> FAMILIES = List.of("longsword", "twinblade", "rapier", "katana", "sai",
            "spear", "glaive", "warglaive", "cutlass", "claymore", "greataxe", "greathammer", "chakram", "scythe", "halberd");
    public static final List<String> MATERIALS = List.of("rusted_iron", "copper", "silver", "gold",
            "ancient_metal", "mithril", "adamantium", "vibranium");
    private static final Map<Item, Definition> DEFINITIONS = new IdentityHashMap<>();
    private record CombatBaseline(double materialDamageTotal, double damageAdjustment, double attackRate) {}
    private static final Map<Item, CombatBaseline> COMBAT_BASELINES = new IdentityHashMap<>();
    private record WeaponProfile(String family, Boolean twoHanded) {}
    private static final ClassValue<Boolean> UNIQUE_WEAPONS = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass())
                if (parent.getName().equals("net.sweenus.simplyswords.item.UniqueWeaponItem")) return true;
            return false;
        }
    };

    public record Definition(String material, String family, ToolProfile profile, boolean added) {
        public float craftingDifficulty() {
            // Same metal-work budget as source tools: 100 per component per material factor, plus handle work.
            return profile.family().components() * profile.materialDurability() * 100F + 25F;
        }
    }

    private SimplySwordsCompat() {}
    public static boolean enabled() { return ModList.get().isLoaded(MOD_ID); }
    public static List<String> families() { return FAMILIES; }
    public static String path(String material, String family) { return "simplyswords_" + material + "_" + family; }
    public static Definition definition(ItemStack stack) { return DEFINITIONS.get(stack.getItem()); }
    public static double damageBonus(ItemStack stack) {
        Definition definition = definition(stack); CombatBaseline baseline = COMBAT_BASELINES.get(stack.getItem());
        return definition == null || baseline == null ? 0D : SimplySwordsBalanceRules.damageBonus(definition.material(),
                definition.family(), baseline.materialDamageTotal(), baseline.damageAdjustment(), uniqueWeapon(stack.getItem()));
    }
    public static double attackRate(ItemStack stack) {
        Definition definition = definition(stack); CombatBaseline baseline = COMBAT_BASELINES.get(stack.getItem());
        return definition == null || baseline == null ? 4D : SimplySwordsBalanceRules.attackRate(definition.family(), baseline.attackRate());
    }
    public static double baseAttackSpeedBonus(ItemStack stack, boolean twoHanded) {
        return attackRate(stack) / (twoHanded ? SimplySwordsBalanceRules.TWO_HANDED_ATTACK_SPEED_FACTOR : 1D) - 4D;
    }
    /** Class ancestry is available before tags bind and covers awakened/evolved forms without optional linkage. */
    public static boolean uniqueWeapon(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(MOD_ID)
                && UNIQUE_WEAPONS.get(item.getClass());
    }
    public static String material(ItemStack stack) {
        Definition definition = definition(stack);
        return definition == null ? null : definition.material();
    }

    public static Item weapon(String material, String family) {
        if (!enabled() || !FAMILIES.contains(family)) return Items.AIR;
        ResourceLocation id = MATERIALS.contains(material) && !material.equals("gold")
                ? ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, path(material, family))
                : ResourceLocation.fromNamespaceAndPath(MOD_ID, material + "_" + family);
        return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
    }

    public static void register(IEventBus bus) {
        if (!enabled()) return;
        for (String material : MATERIALS) if (!material.equals("gold")) for (String family : FAMILIES)
            ITEMS.registerItem(path(material, family), properties -> create(material, family, properties));
        ITEMS.register(bus);
        bus.addListener(EventPriority.LOWEST, SimplySwordsCompat::defaults);
    }

    public static ToolFamily durabilityFamily(String family) {
        return switch (family) {
            case "sai" -> ToolFamily.DAGGER;
            case "greataxe", "halberd" -> ToolFamily.BATTLE_AXE;
            case "greathammer" -> ToolFamily.WAR_HAMMER;
            case "scythe" -> ToolFamily.SCYTHE;
            default -> ToolFamily.SWORD;
        };
    }

    private static MiteTools.Definition source(String material) {
        String id = switch (material) {
            case "iron" -> "minecraft:iron_sword";
            case "gold" -> "minecraft:golden_sword";
            default -> "mite:" + material + "_sword";
        };
        return MiteTools.definitions().stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    private static ToolProfile profile(String material, String family) {
        if (material.equals("wood")) return new ToolProfile(durabilityFamily(family), .5F, Quality.FINE);
        if (material.equals("stone")) return new ToolProfile(durabilityFamily(family), 1F, Quality.FINE);
        if (material.equals("diamond")) return new ToolProfile(durabilityFamily(family), 16F, Quality.SUPERB);
        if (material.equals("netherite") || material.equals("runic"))
            return new ToolProfile(durabilityFamily(family), 32F, Quality.LEGENDARY);
        if (material.equals("unique")) return new ToolProfile(durabilityFamily(family), 64F, Quality.LEGENDARY);
        MiteTools.Definition source = source(material);
        return new ToolProfile(durabilityFamily(family), source.materialDurability(), source.maxQuality());
    }

    private static Item create(String material, String family, Item.Properties properties) {
        ToolProfile profile = profile(material, family);
        MiteTools.Definition source = source(material);
        if (material.equals("vibranium")) properties.fireResistant();
        Tier tier = new Tier() {
            @Override public int getUses() { return EquipmentDurabilityRules.baseToolDurability(profile); }
            @Override public float getSpeed() { return source.speed(); }
            @Override public float getAttackDamageBonus() { return source.damage() - 3F; }
            @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_IRON_TOOL; }
            @Override public int getEnchantmentValue() { return source.enchantability(); }
            @Override public Ingredient getRepairIngredient() { return Ingredient.of(ingot(material)); }
        };
        float familyDamage = configured(family + "_damageModifier", damageModifier(family));
        properties.attributes(SwordItem.createAttributes(tier,
                source.damage() + familyDamage - tier.getAttackDamageBonus(),
                configured(family + "_attackSpeed", attackSpeed(family))));
        try {
            // No external classes occur in signatures: absent-mod dedicated servers never resolve them.
            Class<?> type = Class.forName("net.sweenus.simplyswords.item."
                    + (family.equals("spear") ? "SimplySwordsThrowableItem" : "SimplySwordsSwordItem"));
            Item item = family.equals("spear")
                    ? (Item)type.getConstructor(Tier.class, Item.Properties.class, String[].class)
                            .newInstance(tier, properties, new String[0])
                    : (Item)type.getConstructor(Tier.class, Item.Properties.class).newInstance(tier, properties);
            DEFINITIONS.put(item, new Definition(material, family, profile, true));
            COMBAT_BASELINES.put(item, new CombatBaseline(source.damage() + 1D,
                    familyDamage - damageModifier(family), 4D + configured(family + "_attackSpeed", attackSpeed(family))));
            return item;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Simply Swords item API is incompatible; cannot create " + path(material, family), exception);
        }
    }

    public static Item ingot(String material) {
        return switch (material) {
            case "copper" -> Items.COPPER_INGOT;
            case "silver" -> MiteMaterials.SILVER_INGOT.get();
            case "gold" -> Items.GOLD_INGOT;
            case "ancient_metal" -> MiteMaterials.ANCIENT_METAL_INGOT.get();
            case "mithril" -> MiteMaterials.MITHRIL_INGOT.get();
            case "adamantium" -> MiteMaterials.ADAMANTIUM_INGOT.get();
            case "vibranium" -> MiteMaterials.VIBRANIUM_INGOT.get();
            default -> Items.IRON_INGOT;
        };
    }

    private static float configured(String field, float fallback) {
        try {
            Object config = Class.forName("net.sweenus.simplyswords.config.Config").getField("weaponAttribute").get(null);
            Object group = config.getClass().getField(field.endsWith("_attackSpeed") ? "typeAttackSpeed" : "typeDamageModifier").get(config);
            float value = ((Number)group.getClass().getField(field).get(group)).floatValue();
            return Float.isFinite(value) ? value : fallback;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return fallback;
        }
    }

    private static float damageModifier(String family) {
        return switch (family) {
            case "sai" -> -3F;
            case "rapier", "chakram" -> -1F;
            case "scythe" -> 1F;
            case "claymore" -> 2F;
            case "greataxe", "halberd" -> 3F;
            case "greathammer" -> 4F;
            default -> 0F;
        };
    }

    private static float attackSpeed(String family) {
        return switch (family) {
            case "sai" -> -1.5F;
            case "rapier" -> -1.8F;
            case "twinblade", "katana", "cutlass" -> -2F;
            case "warglaive" -> -2.2F;
            case "glaive" -> -2.6F;
            case "scythe", "spear" -> -2.7F;
            case "claymore", "halberd" -> -2.8F;
            case "chakram" -> -3F;
            case "greataxe" -> -3.1F;
            case "greathammer" -> -3.2F;
            default -> -2.4F;
        };
    }

    private static void defaults(ModifyDefaultComponentsEvent event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof SwordItem sword)) continue;
            if (!DEFINITIONS.containsKey(item)) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (!id.getNamespace().equals(MOD_ID)) continue;
                String family = nativeFamily(id.getPath());
                Tier tier = sword.getTier();
                String material = tier == Tiers.WOOD ? "wood" : tier == Tiers.STONE ? "stone"
                        : tier == Tiers.IRON ? "iron" : tier == Tiers.GOLD ? "gold"
                        : tier == Tiers.DIAMOND ? "diamond" : tier == Tiers.NETHERITE ? "netherite"
                        : id.getPath().startsWith("runic_") ? "runic" : "unique";
                DEFINITIONS.put(item, new Definition(material, family, profile(material, family), false));
            }
            Definition definition = DEFINITIONS.get(item);
            ItemAttributeModifiers original = item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            if (!COMBAT_BASELINES.containsKey(item)) {
                double bonus = baseAttribute(original, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
                double familyDamage = configured(definition.family() + "_damageModifier", damageModifier(definition.family()));
                boolean unique = uniqueWeapon(item);
                COMBAT_BASELINES.put(item, new CombatBaseline(Math.max(1D, 1D + bonus - (unique ? 0D : familyDamage)),
                        unique ? 0D : familyDamage - damageModifier(definition.family()),
                        4D + baseAttribute(original, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID)));
            }
            WeaponProfile weaponProfile = upstreamProfile(BuiltInRegistries.ITEM.getKey(item), definition.family(), new HashSet<>());
            boolean twoHanded = weaponProfile.twoHanded() == null
                    ? SimplySwordsBalanceRules.profile(definition.family()).defaultTwoHanded() : weaponProfile.twoHanded();
            ItemStack baseStack = new ItemStack(item);
            ItemAttributeModifiers.Builder balanced = ItemAttributeModifiers.builder();
            for (var entry : original.modifiers()) {
                AttributeModifier modifier = entry.modifier();
                if (entry.attribute().equals(Attributes.ATTACK_DAMAGE) && modifier.id().equals(Item.BASE_ATTACK_DAMAGE_ID))
                    modifier = new AttributeModifier(modifier.id(), damageBonus(baseStack), AttributeModifier.Operation.ADD_VALUE);
                else if (entry.attribute().equals(Attributes.ATTACK_SPEED) && modifier.id().equals(Item.BASE_ATTACK_SPEED_ID))
                    modifier = new AttributeModifier(modifier.id(), baseAttackSpeedBonus(baseStack, twoHanded), AttributeModifier.Operation.ADD_VALUE);
                balanced.add(entry.attribute(), modifier, entry.slot());
            }
            EquipmentDurability.registerTool(item, definition.profile());
            event.modify(item, components -> components.set(DataComponents.MAX_DAMAGE,
                    EquipmentDurabilityRules.baseToolDurability(definition.profile()))
                    .set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(balanced.build().modifiers(), original.showInTooltip())));
        }
        Mite.LOGGER.info("Simply Swords compatibility enabled: {} managed weapons, {} new metal variants",
                DEFINITIONS.size(), (MATERIALS.size() - 1) * FAMILIES.size());
    }

    private static String nativeFamily(String path) {
        return FAMILIES.stream().filter(f -> path.endsWith("_" + f)).findFirst().orElseGet(() -> upstreamProfile(
                ResourceLocation.fromNamespaceAndPath(MOD_ID, path), switch (path) {
            case "soulrender", "soulstalker", "magiscythe" -> "scythe";
            case "soulpyre", "molten_edge", "livyatan" -> "greataxe";
            case "frostfall", "mjolnir", "hearthflame", "soulkeeper", "hiveheart" -> "greathammer";
            case "icewhisper", "arcanethyst", "thunderbrand" -> "halberd";
            default -> "longsword";
        }, new HashSet<>()).family());
    }

    private static double baseAttribute(ItemAttributeModifiers attributes,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, ResourceLocation id) {
        return attributes.modifiers().stream().filter(entry -> entry.attribute().equals(attribute)
                        && entry.modifier().id().equals(id) && entry.slot().test(EquipmentSlot.MAINHAND))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    /** Read only bundled profile metadata; live Better Combat handedness is resolved on every attribute query. */
    private static WeaponProfile upstreamProfile(ResourceLocation id, String fallback, java.util.Set<ResourceLocation> visited) {
        if (!visited.add(id)) return new WeaponProfile(fallback, null);
        try (var stream = SimplySwordsCompat.class.getClassLoader().getResourceAsStream(
                "data/" + id.getNamespace() + "/weapon_attributes/" + id.getPath() + ".json")) {
            if (stream == null) return new WeaponProfile(SimplySwordsBalanceRules.category(id.getPath(), fallback), null);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            WeaponProfile parent = json.has("parent") ? upstreamProfile(ResourceLocation.parse(json.get("parent").getAsString()), fallback, visited)
                    : new WeaponProfile(fallback, null);
            var attributes = json.has("attributes") ? json.getAsJsonObject("attributes") : null;
            String family = attributes != null && attributes.has("category")
                    ? SimplySwordsBalanceRules.category(attributes.get("category").getAsString(), parent.family()) : parent.family();
            Boolean twoHanded = attributes != null && attributes.has("two_handed") ? Boolean.valueOf(attributes.get("two_handed").getAsBoolean()) : parent.twoHanded();
            return new WeaponProfile(family, twoHanded);
        } catch (java.io.IOException | RuntimeException exception) {
            Mite.LOGGER.warn("Could not inspect optional weapon profile {}", id, exception);
            return new WeaponProfile(fallback, null);
        }
    }
}
