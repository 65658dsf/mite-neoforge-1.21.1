package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Source book transfers retain free costs with modern compatible additions and upgrades. @author NingMeng123 */
public final class R196BookAnvil {
    private R196BookAnvil() {}
    private static R196EnchantmentCatalog.Definition definition(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(key -> R196EnchantmentCatalog.definition(key.location().toString())).orElse(null);
    }
    public static boolean hasSource(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet().stream().anyMatch(entry -> definition(entry.getKey()) != null);
    }
    private static boolean supportsBook(R196EnchantmentCatalog.Definition definition, ItemStack stack) {
        // Modern anvil adaptation: leather accepts Unbreaking books, including source levels IV/V.
        // Keep the table's original metal-only candidate predicate in the source catalogue.
        return definition.supported(stack) || definition.resourceId().equals("minecraft:unbreaking")
                && (stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE)
                || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS));
    }
    private static MaterialAnvilRules.Plan rejected() { return new MaterialAnvilRules.Plan(ItemStack.EMPTY, 0, 0, false); }

    /** Null leaves modern-only enchants/equipment with their native costs and merge behavior. */
    public static MaterialAnvilRules.Plan plan(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) return null;
        boolean source = hasSource(first) || hasSource(second);
        if (!source) return null;
        boolean book = second.has(DataComponents.STORED_ENCHANTMENTS);
        if ((first.is(Items.BOOK) || first.is(Items.ENCHANTED_BOOK)) && book) return rejected();
        if (!R196EnchantmentTable.isSourceEquipment(first)) return null;
        // Equipment+equipment must never smuggle source enchantments past the book-only entry rule.
        if (!book) return second.isEnchanted() ? rejected() : null;
        if (first.getMaxStackSize() != 1 || R196EnchantmentTable.enchantability(first) < 1) return rejected();
        ItemEnchantments stored = second.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        boolean applicableSource = stored.entrySet().stream().anyMatch(entry -> {
            var definition = definition(entry.getKey());
            return definition != null && supportsBook(definition, first);
        });
        // An irrelevant source entry must not make an otherwise modern-only transfer free.
        if (!applicableSource && stored.entrySet().stream().anyMatch(entry -> definition(entry.getKey()) == null
                && entry.getKey().value().canEnchant(first))) return null;
        ItemEnchantments.Mutable resultEnchantments = new ItemEnchantments.Mutable(
                first.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        boolean changed = false;
        for (var entry : stored.entrySet()) {
            var definition = definition(entry.getKey());
            if (definition != null ? !supportsBook(definition, first) : !entry.getKey().value().canEnchant(first)) continue;
            // Check both retained and newly added entries; the same enchantment is an upgrade candidate.
            if (resultEnchantments.keySet().stream().anyMatch(previous -> !previous.equals(entry.getKey())
                    && !Enchantment.areCompatible(previous, entry.getKey()))) return rejected();
            int current = resultEnchantments.getLevel(entry.getKey());
            int incoming = entry.getIntValue();
            int level = Math.min(current == incoming ? incoming + 1 : Math.max(current, incoming),
                    definition == null ? entry.getKey().value().getMaxLevel() : definition.maxLevel());
            if (level <= current) continue;
            resultEnchantments.set(entry.getKey(), level);
            changed = true;
        }
        if (!changed) return rejected();
        ItemStack result = first.copyWithCount(1);
        result.set(DataComponents.ENCHANTMENTS, resultEnchantments.toImmutable());
        return new MaterialAnvilRules.Plan(result, second.getCount(), 0, false);
    }
}
