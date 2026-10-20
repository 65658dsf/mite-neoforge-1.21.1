package io.github.ningmeng123.mite.progression;

import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The exact tool/armor modifier catalog from MITE-ITE 2.0.28. @author NingMeng123 */
public enum GearModifier {
    TOOL_EFFICIENCY("tool/efficiency", 20, 10, .25F),
    TOOL_AQUADYNAMIC("tool/aquadynamic", 10, 5, 1.25F),
    TOOL_DURABILITY("tool/durability", 40, 5, .1F),
    TOOL_DAMAGE("tool/damage", 20, 10, 1F),
    TOOL_SLOWDOWN("tool/slowdown", 10, 5, 1F),
    TOOL_UNNATURAL("tool/unnatural", 6, 5, .1F),
    TOOL_DEMON_POWER("tool/demon", 4, 1, .25F),
    TOOL_GEOLOGY("tool/geology", 2, 1, .5F),
    ARMOR_DURABILITY("armor/durability", 20, 5, .1F),
    ARMOR_PROJECTILE_PROTECTION("armor/projectile_protection", 7, 5, .5F),
    ARMOR_EXPLOSION_PROTECTION("armor/explosion_protection", 7, 5, .5F),
    ARMOR_FIRE_PROTECTION("armor/fire_protection", 7, 5, .5F),
    ARMOR_PROTECTION("armor/protection", 5, 5, .25F),
    ARMOR_STEADY("armor/steady", 8, 5, .15F),
    ARMOR_BLESSED("armor/blessed", 8, 5, 1F),
    ARMOR_INDOMITABLE("armor/indomitable", 5, 4, .25F),
    ARMOR_IMMUNITY("armor/immunity", 3, 4, .15F);

    private final ResourceLocation id;
    private final int weight;
    private final int maximumLevel;
    private final float addition;

    GearModifier(String path, int weight, int maximumLevel, float addition) {
        id = ResourceLocation.fromNamespaceAndPath("mite", "modifier/" + path);
        this.weight = weight;
        this.maximumLevel = maximumLevel;
        this.addition = addition;
    }

    public ResourceLocation id() { return id; }
    public int weight() { return weight; }
    public int maximumLevel() { return maximumLevel; }
    public int level(ItemStack stack) { return stack.isEmpty() ? 0 : GearProgression.state(stack).affixes().getOrDefault(id, 0); }
    public float value(ItemStack stack) { return addition * level(stack); }
    public Component displayName() { return Component.translatable("modifier.mite." + id.getPath().substring(9).replace('/', '.')); }
    public static GearModifier fromId(ResourceLocation id) {
        for (GearModifier modifier : values()) if (modifier.id.equals(id)) return modifier;
        return null;
    }

    /** Separate source weapon identity from the broader modern kill-XP eligibility. */
    public static boolean isWeapon(ItemStack stack) {
        if (SimplySwordsCompat.definition(stack) != null || stack.is(Items.TRIDENT) || stack.is(Items.MACE)) return true;
        ToolFamily family = R196EnchantmentCatalog.family(stack);
        return family == ToolFamily.SWORD || family == ToolFamily.DAGGER || family == ToolFamily.KNIFE
                || family == ToolFamily.BATTLE_AXE || family == ToolFamily.WAR_HAMMER;
    }

    public boolean applies(ItemStack stack) {
        if (GearProgression.maxLevel(stack) == 0) return false;
        boolean armor = GearProgression.isArmor(stack);
        if (name().startsWith("ARMOR_")) {
            if (!armor) return false;
            var type = ((ArmorItem) stack.getItem()).getType();
            return switch (this) {
                case ARMOR_INDOMITABLE -> type == ArmorItem.Type.CHESTPLATE;
                case ARMOR_IMMUNITY -> type == ArmorItem.Type.HELMET;
                default -> true;
            };
        }
        if (armor) return false;
        // Modern ranged equipment and shields retain their existing growth, using durability only.
        if (this == TOOL_DURABILITY) return true;
        if (this == TOOL_GEOLOGY) return R196EnchantmentCatalog.family(stack) == ToolFamily.PICKAXE;
        if (this == TOOL_DAMAGE || this == TOOL_SLOWDOWN || this == TOOL_DEMON_POWER) return isWeapon(stack);
        return !isWeapon(stack) && R196EnchantmentCatalog.family(stack) != null;
    }

    private boolean protection() {
        return this == ARMOR_PROTECTION || this == ARMOR_PROJECTILE_PROTECTION
                || this == ARMOR_EXPLOSION_PROTECTION || this == ARMOR_FIRE_PROTECTION;
    }

    /** Rebuilt after every rank, including each upgrade in a single experience award. */
    public static List<GearModifier> candidates(ItemStack stack, Map<ResourceLocation, Integer> ranks) {
        return Arrays.stream(values()).filter(modifier -> modifier.applies(stack)
                && ranks.getOrDefault(modifier.id, 0) < modifier.maximumLevel
                && (!modifier.protection() || Arrays.stream(values()).noneMatch(other -> other != modifier
                        && other.protection() && ranks.containsKey(other.id)))).toList();
    }
}
