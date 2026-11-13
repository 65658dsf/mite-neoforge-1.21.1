package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Compatible source-book additions and upgrades use a fixed point fee and deterministic anvil wear. @author NingMeng123 */
public final class R196BookAnvil {
    private R196BookAnvil() {}
    private static R196EnchantmentCatalog.Definition definition(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(key -> R196EnchantmentCatalog.definition(key.location().toString())).orElse(null);
    }
    public static boolean hasSource(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet().stream().anyMatch(entry -> definition(entry.getKey()) != null);
    }
    private static boolean supportsBook(Holder<Enchantment> enchantment, ItemStack stack) {
        // Books store any enabled, compatible enchantment without equipment target restrictions.
        if (stack.is(Items.ENCHANTED_BOOK)) return true;
        var definition = definition(enchantment);
        if (definition == null) return enchantment.value().canEnchant(stack);
        // Modern anvil adaptation: leather accepts Unbreaking books, including source levels IV/V.
        // Keep the table's original metal-only candidate predicate in the source catalogue.
        return definition.supported(stack) || definition.resourceId().equals("minecraft:unbreaking")
                && (stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE)
                || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS));
    }
    private static MaterialAnvilRules.Plan rejected() { return new MaterialAnvilRules.Plan(ItemStack.EMPTY, 0, 0, false); }

    private static int maximumLevel(ItemStack stack, Holder<Enchantment> enchantment, R196EnchantmentCatalog.Definition definition) {
        int maximum = definition == null ? enchantment.value().getMaxLevel() : definition.maxLevel();
        if (definition != null && R196EnchantmentTable.vibranium(stack))
            maximum = Math.max(maximum, definition.sourceTableLevel(true));
        int materialMaximum = maximum;
        return enchantment.unwrapKey().map(key -> MiteConfig.enchantmentMaxLevel(key.location().toString(), materialMaximum)).orElse(materialMaximum);
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

    /** Every successful equipment application or book merge costs exactly 8000 points. */
    public static int experiencePoints(ItemStack before, ItemStack book, ItemStack after) {
        return changesBookEnchantments(before, book, after) ? 8000 : 0;
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
        boolean combiningBooks = first.is(Items.ENCHANTED_BOOK) && book;
        if (first.is(Items.BOOK) && book) return rejected();
        if (!combiningBooks && !R196EnchantmentTable.isSourceEquipment(first)) return null;
        // Equipment+equipment must never smuggle source enchantments past the book-only entry rule.
        if (!book) return second.isEnchanted() ? rejected() : null;
        if (first.getMaxStackSize() != 1 || !combiningBooks && R196EnchantmentTable.enchantability(first) < 1) return rejected();
        ItemEnchantments stored = second.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        boolean applicableSource = stored.entrySet().stream().anyMatch(entry -> {
            var definition = definition(entry.getKey());
            return ConfiguredEnchantments.enabled(entry.getKey()) && definition != null && supportsBook(entry.getKey(), first);
        });
        // An irrelevant source entry must not make an otherwise modern-only transfer free.
        if (!combiningBooks && !applicableSource && stored.entrySet().stream().anyMatch(entry -> definition(entry.getKey()) == null
                && entry.getKey().value().canEnchant(first))) return null;
        ItemEnchantments.Mutable resultEnchantments = new ItemEnchantments.Mutable(
                EnchantmentHelper.getEnchantmentsForCrafting(first));
        boolean changed = false;
        for (var entry : stored.entrySet()) {
            if (!ConfiguredEnchantments.enabled(entry.getKey())) continue;
            var definition = definition(entry.getKey());
            if (!supportsBook(entry.getKey(), first)) continue;
            // Check both retained and newly added entries; the same enchantment is an upgrade candidate.
            if (resultEnchantments.keySet().stream().anyMatch(previous -> !previous.equals(entry.getKey())
                    && !MiteExtensions.policy().anvilEnchantmentsCompatible(first, previous, entry.getKey(),
                            Enchantment.areCompatible(previous, entry.getKey())))) return rejected();
            int current = resultEnchantments.getLevel(entry.getKey());
            int incoming = entry.getIntValue();
            int level = Math.min(current == incoming ? incoming + 1 : Math.max(current, incoming),
                    maximumLevel(first, entry.getKey(), definition));
            if (level <= current) continue;
            resultEnchantments.set(entry.getKey(), level);
            changed = true;
        }
        if (!changed) return rejected();
        ItemStack result = first.copyWithCount(1);
        result.set(combiningBooks ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, resultEnchantments.toImmutable());
        result.set(DataComponents.REPAIR_COST, AnvilMenu.calculateIncreasedRepairCost(Math.max(
                first.getOrDefault(DataComponents.REPAIR_COST, 0), second.getOrDefault(DataComponents.REPAIR_COST, 0))));
        return new MaterialAnvilRules.Plan(result, second.getCount(), anvilWear(first, second, result), false);
    }
}
