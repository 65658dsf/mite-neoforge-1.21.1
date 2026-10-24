package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;

/** Independently audited source identities and exact-class item rules. @author NingMeng123 */
public final class R196EnchantmentCatalog {
    public record Definition(int legacyId, String id, String resourceId, int weight, int difficulty, int maxLevel) {
        @Override public int maxLevel() {
            return io.github.ningmeng123.mite.config.MiteConfig.enchantmentMaxLevel(resourceId, maxLevel);
        }

        /** Protection V is reserved for player enchanting and anvil upgrades. */
        public int maxGeneratedLevel() {
            int fallback = resourceId.equals("minecraft:protection") ? Math.min(4, maxLevel) : maxLevel;
            return io.github.ningmeng123.mite.config.MiteConfig.enchantmentMaxLevel(resourceId, fallback);
        }

        /** Fixed R196 chest-book range; player configuration cannot rewrite the historical reward pool. */
        public int sourceGeneratedLevel() {
            return switch (legacyId) {
                case 0, 1, 2, 3, 4, 87, 95 -> 4;
                case 5, 7, 21, 35, 94 -> 3;
                case 19, 20, 49 -> 2;
                case 6, 33, 50 -> 1;
                default -> 5; // aau.getNumLevels: also applies to source sharpness and unbreaking.
            };
        }

        public int minCost(int level) {
            if (level < 1 || level > maxLevel()) throw new IllegalArgumentException("Invalid source enchantment level: " + id + " " + level);
            // Preserve a single-level enchantment's original first-rank cost while charging for explicit extra ranks.
            return Math.max(difficulty - 10, 0) + difficulty * (level - 1 + (maxLevel == 1 ? 2 : 0)) + 1;
        }

        public boolean supported(ItemStack stack) { return R196EnchantmentCatalog.supported(id, stack); }
    }

    private static final List<Definition> DEFINITIONS = List.of(
            nativeEntry(0, "protection", 100, 10, 5),
            nativeEntry(1, "fire_protection", 25, 10, 4),
            nativeEntry(2, "feather_falling", 25, 10, 4),
            nativeEntry(3, "blast_protection", 25, 10, 4),
            nativeEntry(4, "projectile_protection", 25, 10, 4),
            nativeEntry(5, "respiration", 5, 10, 3),
            nativeEntry(6, "aqua_affinity", 5, 10, 1),
            nativeEntry(7, "thorns", 5, 20, 3),
            nativeEntry(16, "sharpness", 100, 10, 7),
            nativeEntry(17, "smite", 25, 10, 5),
            nativeEntry(18, "bane_of_arthropods", 25, 10, 5),
            nativeEntry(19, "knockback", 25, 10, 2),
            nativeEntry(20, "fire_aspect", 5, 20, 2),
            nativeEntry(21, "looting", 25, 10, 3),
            nativeEntry(32, "efficiency", 100, 10, 5),
            nativeEntry(33, "silk_touch", 5, 10, 1),
            nativeEntry(34, "unbreaking", 25, 10, 5),
            nativeEntry(35, "fortune", 5, 10, 3),
            nativeEntry(48, "power", 100, 10, 5),
            nativeEntry(49, "punch", 25, 10, 2),
            nativeEntry(50, "flame", 5, 20, 1),
            miteEntry(51, "arrow_recovery", 25, 10, 5),
            miteEntry(80, "stun", 25, 15, 5),
            miteEntry(81, "fishing_fortune", 100, 10, 5),
            miteEntry(82, "fertility", 25, 10, 5),
            miteEntry(83, "tree_felling", 25, 10, 5),
            miteEntry(84, "vampiric", 1, 20, 5),
            miteEntry(85, "speed", 5, 10, 5),
            miteEntry(86, "regeneration", 5, 20, 5),
            miteEntry(87, "free_action", 25, 10, 4),
            miteEntry(88, "quickness", 25, 10, 5),
            miteEntry(89, "true_flight", 100, 10, 5),
            miteEntry(90, "poison", 5, 10, 5),
            miteEntry(91, "disarming", 5, 10, 5),
            miteEntry(92, "piercing", 5, 10, 5),
            miteEntry(93, "harvesting", 25, 10, 5),
            miteEntry(94, "butchering", 25, 10, 3),
            miteEntry(95, "endurance", 25, 10, 4));
    private static final Set<String> DAMAGE = Set.of("sharpness", "smite", "bane_of_arthropods");
    private static final Set<String> PROTECTION = Set.of("protection", "fire_protection", "blast_protection", "projectile_protection");

    private R196EnchantmentCatalog() {}

    private static Definition nativeEntry(int legacyId, String id, int weight, int difficulty, int levels) {
        return new Definition(legacyId, id, "minecraft:" + id, weight, difficulty, levels);
    }

    private static Definition miteEntry(int legacyId, String id, int weight, int difficulty, int levels) {
        return new Definition(legacyId, id, "mite:" + id, weight, difficulty, levels);
    }

    public static List<Definition> definitions() {
        var all=new java.util.ArrayList<>(DEFINITIONS);
        all.addAll(io.github.ningmeng123.mite.api.MiteExtensions.externalEnchantments());
        return List.copyOf(all);
    }

    public static List<Definition> sourceDefinitions() { return DEFINITIONS; }

    /** Namespaced IDs are matched exactly, so minecraft:piercing is never confused with the source enchantment. */
    public static Definition definition(String id) {
        if (id == null) return null;
        return definitions().stream().filter(d -> id.indexOf(':') >= 0 ? d.resourceId().equals(id) : d.id().equals(id)).findFirst().orElse(null);
    }

    public static boolean compatible(String left, String right) {
        return io.github.ningmeng123.mite.api.MiteExtensions.policy().enchantmentsCompatible(left,right,nativeCompatible(left,right));
    }
    private static boolean nativeCompatible(String left, String right) {
        Definition first = definition(left), second = definition(right);
        if (first == null || second == null) return !java.util.Objects.equals(left, right);
        String a = first.id(), b = second.id();
        if (a.equals(b) || DAMAGE.contains(a) && DAMAGE.contains(b) || PROTECTION.contains(a) && PROTECTION.contains(b)) return false;
        return !(a.equals("silk_touch") && (b.equals("fortune") || b.equals("looting"))
                || b.equals("silk_touch") && (a.equals("fortune") || a.equals("looting")));
    }

    public static boolean supported(String id, ItemStack stack) {
        return io.github.ningmeng123.mite.api.MiteExtensions.policy().enchantmentApplicable(id, stack, nativeSupported(id, stack));
    }
    private static boolean nativeSupported(String id, ItemStack stack) {
        Definition definition = definition(id);
        if (definition == null || stack.isEmpty() || io.github.ningmeng123.mite.item.RemovedDiamondEquipment.isRemoved(stack)) return false;
        ToolFamily family = family(stack);
        boolean armor = stack.getItem() instanceof ArmorItem && !(stack.getItem() instanceof AnimalArmorItem);
        ArmorItem.Type type = armor ? ((ArmorItem)stack.getItem()).getType() : null;
        boolean sword = family == ToolFamily.SWORD || family == ToolFamily.DAGGER || family == ToolFamily.KNIFE;
        boolean cudgel = family == ToolFamily.CUDGEL || family == ToolFamily.CLUB;
        return switch (definition.id()) {
            case "protection", "fire_protection" -> armor;
            case "feather_falling", "speed" -> type == ArmorItem.Type.BOOTS;
            case "blast_protection", "projectile_protection" -> (type == ArmorItem.Type.CHESTPLATE || type == ArmorItem.Type.LEGGINGS) && solidMetal(stack);
            case "respiration", "aqua_affinity" -> type == ArmorItem.Type.HELMET;
            case "thorns", "regeneration", "endurance" -> type == ArmorItem.Type.CHESTPLATE;
            case "free_action" -> type == ArmorItem.Type.LEGGINGS;
            case "sharpness" -> sword || family == ToolFamily.BATTLE_AXE || family == ToolFamily.SCYTHE;
            case "smite" -> family == ToolFamily.WAR_HAMMER;
            case "bane_of_arthropods", "fire_aspect" -> sword;
            case "knockback", "stun" -> cudgel || family == ToolFamily.WAR_HAMMER;
            case "looting" -> cudgel || sword;
            case "efficiency" -> in(family, ToolFamily.PICKAXE, ToolFamily.AXE, ToolFamily.HATCHET, ToolFamily.BATTLE_AXE, ToolFamily.SHOVEL, ToolFamily.MATTOCK, ToolFamily.HOE);
            case "silk_touch" -> in(family, ToolFamily.PICKAXE, ToolFamily.SHOVEL, ToolFamily.SHEARS, ToolFamily.KNIFE, ToolFamily.DAGGER);
            case "unbreaking" -> armor ? solidMetal(stack) : cudgel || in(family, ToolFamily.PICKAXE, ToolFamily.WAR_HAMMER, ToolFamily.AXE,
                    ToolFamily.HATCHET, ToolFamily.BATTLE_AXE, ToolFamily.SHOVEL, ToolFamily.MATTOCK, ToolFamily.HOE)
                    || stack.getItem() instanceof FishingRodItem || stack.getItem() instanceof BowItem
                    || MiteShields.definition(stack) != null;
            case "fortune" -> family == ToolFamily.PICKAXE || family == ToolFamily.SHOVEL;
            case "power", "punch", "flame", "arrow_recovery", "quickness", "true_flight", "poison" -> stack.getItem() instanceof BowItem;
            case "fishing_fortune" -> stack.getItem() instanceof FishingRodItem;
            case "fertility", "harvesting" -> in(family, ToolFamily.HOE, ToolFamily.MATTOCK, ToolFamily.SCYTHE);
            case "tree_felling" -> family == ToolFamily.AXE || family == ToolFamily.BATTLE_AXE;
            case "vampiric" -> (sword || family == ToolFamily.SCYTHE) && !vampiricExcludedMaterial(stack);
            case "disarming" -> family == ToolFamily.SWORD;
            case "piercing" -> family == ToolFamily.PICKAXE || family == ToolFamily.BATTLE_AXE;
            case "butchering" -> family == ToolFamily.KNIFE || family == ToolFamily.DAGGER;
            default -> false;
        };
    }

    public static ToolFamily family(ItemStack stack) {
        var simply = io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat.definition(stack);
        if (simply != null) return simply.profile().family();
        MiteTools.Definition source = MiteTools.definition(stack);
        if (source != null) return source.family();
        if (stack.getItem() instanceof PickaxeItem) return ToolFamily.PICKAXE;
        if (stack.getItem() instanceof AxeItem) return ToolFamily.AXE;
        if (stack.getItem() instanceof ShovelItem) return ToolFamily.SHOVEL;
        if (stack.getItem() instanceof HoeItem) return ToolFamily.HOE;
        if (stack.getItem() instanceof SwordItem) return ToolFamily.SWORD;
        if (stack.getItem() instanceof ShearsItem) return ToolFamily.SHEARS;
        return null;
    }

    private static boolean in(ToolFamily actual, ToolFamily... allowed) {
        for (ToolFamily family : allowed) if (family == actual) return true;
        return false;
    }

    public static boolean solidMetal(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem) || stack.getItem() instanceof AnimalArmorItem) return false;
        MiteArmorContent.Definition definition = MiteArmorContent.definition(stack.getItem());
        if (definition != null) return !definition.horse() && !definition.chainmail() && !definition.material().equals("leather");
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // Retained modern netherite equipment shares source solid-armor semantics.
        return id.startsWith("minecraft:iron_") || id.startsWith("minecraft:golden_")
                || id.startsWith("minecraft:netherite_");
    }

    private static boolean vampiricExcludedMaterial(ItemStack stack) {
        var simply = io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat.definition(stack);
        if (simply != null) return simply.material().equals("silver") || simply.material().equals("mithril");
        MiteTools.Definition source = MiteTools.definition(stack);
        return source != null && (source.material().equals("silver") || source.material().equals("mithril"));
    }
}
