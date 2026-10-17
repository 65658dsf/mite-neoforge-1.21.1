package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only source repair index using the same material preview as physical anvils. @author NingMeng123 */
public final class AnvilJeiRecipes {
    private static final List<String> MATERIALS = List.of("copper", "silver", "gold", "iron",
            "ancient_metal", "mithril", "adamantium");
    public record Profile(String id, float capacity, BlockState state) {}
    public record Index(List<AnvilJeiRecipe> recipes, Set<ResourceLocation> coveredItems) {
        public Index { recipes = List.copyOf(recipes); coveredItems = Set.copyOf(coveredItems); }
    }

    private AnvilJeiRecipes() {}

    public static Map<String, Profile> profiles() {
        Map<String, Profile> result = new LinkedHashMap<>();
        for (String material : MATERIALS) {
            if (material.equals("iron")) {
                for (var block : List.of(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL))
                    addProfile(result, BuiltInRegistries.BLOCK.getKey(block).toString(), block.defaultBlockState());
            } else {
                var block = MiteFunctionalBlocks.ANVILS.get(material);
                addProfile(result, block.getId().toString(), block.get().defaultBlockState());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static void addProfile(Map<String, Profile> profiles, String id, BlockState state) {
        profiles.put(id, new Profile(id, MaterialAnvilRules.capacity(state), state));
    }

    /** JEI discovers plugins during mod construction, before deferred blocks are bound. */
    public static List<String> categoryIds() {
        List<String> result = new ArrayList<>();
        Set<Float> capacities = new LinkedHashSet<>();
        for (String material : MATERIALS) {
            if (!capacities.add(WorkbenchRules.materialCapacity(material))) continue;
            result.add(material.equals("iron") ? "minecraft:anvil"
                    : "mite:" + (material.equals("gold") ? "golden" : material) + "_anvil");
        }
        return List.copyOf(result);
    }

    /** Minimum-capacity tiers for repair lookup, not separate JEI recipe types. */
    public static Map<String, Profile> categoryProfiles() {
        Map<String, Profile> result = new LinkedHashMap<>();
        Map<String, Profile> profiles = profiles();
        for (String id : categoryIds()) result.put(id, profiles.get(id));
        return Collections.unmodifiableMap(result);
    }

    /** Every anvil listed on the left is sufficient for every repair in this category. */
    public static List<String> categoryAnvils(String categoryId) {
        Profile minimum = categoryProfiles().get(categoryId);
        if (minimum == null) throw new IllegalArgumentException("Unknown anvil category: " + categoryId);
        return profiles().values().stream().filter(profile -> profile.capacity() >= minimum.capacity())
                .map(Profile::id).toList();
    }

    /** Examples are ordinary quality and remain discoverable regardless of the viewer's current experience. */
    public static Index build() {
        List<AnvilJeiRecipe> recipes = new ArrayList<>();
        Set<ResourceLocation> covered = new LinkedHashSet<>();
        List<Profile> categories = List.copyOf(categoryProfiles().values());
        for (var item : BuiltInRegistries.ITEM.stream()
                .sorted(Comparator.comparing(value -> BuiltInRegistries.ITEM.getKey(value).toString())).toList()) {
            ItemStack base = new ItemStack(item);
            if (base.isEmpty() || RemovedDiamondEquipment.isRemoved(base) || !base.isDamageableItem()) continue;
            var material = MaterialAnvilRules.material(base);
            if (material == null) continue;
            EquipmentDurability.setQuality(base, Quality.AVERAGE);
            if (base.getMaxDamage() < 2) continue;
            float required = WorkbenchRules.materialCapacity(material.name());
            Profile anvil = categories.stream().filter(profile -> profile.capacity() >= required).findFirst().orElse(null);
            if (anvil == null) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

            ItemStack left = base.copy();
            left.setDamageValue((int)((long)left.getMaxDamage() * 3L / 4L));
            add(recipes, covered, anvil, itemId, AnvilJeiRecipe.Kind.COMBINATION,
                    left, left.copy(), required);

            var repairItem = MaterialAnvilRules.repairItem(material.name());
            if (repairItem != null) {
                ItemStack damaged = base.copy();
                damaged.setDamageValue(damaged.getMaxDamage() - 1);
                add(recipes, covered, anvil, itemId, AnvilJeiRecipe.Kind.MATERIAL,
                        damaged, new ItemStack(repairItem), required);
            }
        }
        return new Index(recipes, covered);
    }

    private static void add(List<AnvilJeiRecipe> recipes, Set<ResourceLocation> covered, Profile anvil,
            ResourceLocation itemId, AnvilJeiRecipe.Kind kind, ItemStack left, ItemStack right, float required) {
        var plan = MaterialAnvilRules.previewRepair(anvil.state(), left, right);
        if (plan == null || !plan.allowed() || !plan.repair()) return;
        recipes.add(new AnvilJeiRecipe(anvil.id(), itemId, kind, left, right, plan.output(),
                plan.consumed(), plan.anvilWear(), required));
        covered.add(itemId);
    }

    /** Only JEI's confirmed generated repair IDs are superseded; unknown operations and books remain visible. */
    public static boolean isCoveredNativeRepair(ResourceLocation uid, List<ItemStack> left,
            List<ItemStack> right, Set<ResourceLocation> covered) {
        if (uid == null || left.isEmpty() || right.isEmpty()
                || right.stream().anyMatch(stack -> stack.has(DataComponents.STORED_ENCHANTMENTS))) return false;
        String path = uid.getPath();
        String prefix = path.startsWith("anvil.self_repair.") ? "anvil.self_repair."
                : path.startsWith("anvil.materials_repair.") ? "anvil.materials_repair." : null;
        if (prefix == null) return false;
        return left.stream().allMatch(stack -> {
            if (stack.isEmpty()) return false;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return covered.contains(itemId) && uid.getNamespace().equals(itemId.getNamespace())
                    && path.equals(prefix + itemId.toString().replace(':', '.'));
        });
    }
}
