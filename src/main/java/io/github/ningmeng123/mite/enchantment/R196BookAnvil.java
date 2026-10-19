package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Compatible source-book additions and upgrades retain native level fees and deterministic anvil wear. @author NingMeng123 */
public final class R196BookAnvil {
    private R196BookAnvil() {}
    private static R196EnchantmentCatalog.Definition definition(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(key -> R196EnchantmentCatalog.definition(key.location().toString())).orElse(null);
    }
    public static boolean hasSource(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet().stream().anyMatch(entry -> definition(entry.getKey()) != null);
    }
    private static boolean supportsBook(R196EnchantmentCatalog.Definition definition, ItemStack stack) {
        if (stack.is(Items.ENCHANTED_BOOK)) return definition.resourceId().equals("minecraft:protection");
        // Modern anvil adaptation: leather accepts Unbreaking books, including source levels IV/V.
        // Keep the table's original metal-only candidate predicate in the source catalogue.
        return definition.supported(stack) || definition.resourceId().equals("minecraft:unbreaking")
                && (stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE)
                || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS));
    }
    private static MaterialAnvilRules.Plan rejected() { return new MaterialAnvilRules.Plan(ItemStack.EMPTY, 0, 0, false); }

    private static boolean protectionBook(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) && stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                .entrySet().stream().anyMatch(entry -> {
                    var definition = definition(entry.getKey());
                    return definition != null && definition.resourceId().equals("minecraft:protection") && entry.getIntValue() > 0;
                });
    }

    private static boolean changesBookEnchantments(ItemStack before, ItemStack book, ItemStack after) {
        if (before.isEmpty() || after.isEmpty()) return false;
        ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments previous = EnchantmentHelper.getEnchantmentsForCrafting(before);
        ItemEnchantments result = EnchantmentHelper.getEnchantmentsForCrafting(after);
        for (var entry : stored.entrySet()) {
            int level = result.getLevel(entry.getKey());
            if (level > previous.getLevel(entry.getKey())) return true;
        }
        return false;
    }

    /** Native book rarity/level fees, input work penalties and an actual rename surcharge. */
    public static int experienceLevels(ItemStack before, ItemStack book, ItemStack after) {
        if (!changesBookEnchantments(before, book, after)) return 0;
        ItemEnchantments result = EnchantmentHelper.getEnchantmentsForCrafting(after);
        long cost = (long)Math.max(0, before.getOrDefault(DataComponents.REPAIR_COST, 0))
                + Math.max(0, book.getOrDefault(DataComponents.REPAIR_COST, 0));
        for (var entry : book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            if (!ConfiguredEnchantments.enabled(entry.getKey())) continue;
            var definition = definition(entry.getKey());
            if (definition != null ? !supportsBook(definition, before) : !entry.getKey().value().canEnchant(before)) continue;
            int level = result.getLevel(entry.getKey());
            if (level > 0) cost += (long)Math.max(1, entry.getKey().value().getAnvilCost() / 2) * level;
        }
        if (!Objects.equals(before.get(DataComponents.CUSTOM_NAME), after.get(DataComponents.CUSTOM_NAME))) cost++;
        return (int)Math.min(cost, Integer.MAX_VALUE);
    }

    /** Successful book changes charge all result levels at 100 each, plus 10% per type after the first. */
    public static int anvilWear(ItemStack before, ItemStack book, ItemStack after) {
        if (!changesBookEnchantments(before, book, after)) return 0;
        ItemEnchantments result = EnchantmentHelper.getEnchantmentsForCrafting(after);
        long totalLevels = 0;
        int types = 0;
        for (var entry : result.entrySet()) {
            if (entry.getIntValue() <= 0) continue;
            totalLevels += entry.getIntValue();
            types++;
        }
        // 100 * (1 + 0.1 * (types - 1)) = 10 * (types + 9), using exact integer arithmetic.
        long wear = totalLevels * 10L * (types + 9L);
        return (int)Math.min(wear, Integer.MAX_VALUE);
    }

    /** Null leaves modern-only enchants/equipment with their native costs and merge behavior. */
    public static MaterialAnvilRules.Plan plan(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) return null;
        boolean source = hasSource(first) || hasSource(second);
        if (!source) return null;
        boolean book = second.has(DataComponents.STORED_ENCHANTMENTS);
        boolean protectionBooks = protectionBook(first) && protectionBook(second);
        if ((first.is(Items.BOOK) || first.is(Items.ENCHANTED_BOOK)) && book && !protectionBooks) return rejected();
        if (!protectionBooks && !R196EnchantmentTable.isSourceEquipment(first)) return null;
        // Equipment+equipment must never smuggle source enchantments past the book-only entry rule.
        if (!book) return second.isEnchanted() ? rejected() : null;
        if (first.getMaxStackSize() != 1 || !protectionBooks && R196EnchantmentTable.enchantability(first) < 1) return rejected();
        ItemEnchantments stored = second.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        boolean applicableSource = stored.entrySet().stream().anyMatch(entry -> {
            var definition = definition(entry.getKey());
            return ConfiguredEnchantments.enabled(entry.getKey()) && definition != null && supportsBook(definition, first);
        });
        // An irrelevant source entry must not make an otherwise modern-only transfer free.
        if (!applicableSource && stored.entrySet().stream().anyMatch(entry -> definition(entry.getKey()) == null
                && entry.getKey().value().canEnchant(first))) return null;
        ItemEnchantments.Mutable resultEnchantments = new ItemEnchantments.Mutable(
                EnchantmentHelper.getEnchantmentsForCrafting(first));
        boolean changed = false;
        for (var entry : stored.entrySet()) {
            if (!ConfiguredEnchantments.enabled(entry.getKey())) continue;
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
        result.set(protectionBooks ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, resultEnchantments.toImmutable());
        result.set(DataComponents.REPAIR_COST, AnvilMenu.calculateIncreasedRepairCost(Math.max(
                first.getOrDefault(DataComponents.REPAIR_COST, 0), second.getOrDefault(DataComponents.REPAIR_COST, 0))));
        return new MaterialAnvilRules.Plan(result, second.getCount(), anvilWear(first, second, result), false);
    }
}
