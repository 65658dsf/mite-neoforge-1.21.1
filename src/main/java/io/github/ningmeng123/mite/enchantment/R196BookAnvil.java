package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import java.util.ArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** va/vc transfers a source book to an unenchanted target once, free of XP and anvil wear. @author NingMeng123 */
public final class R196BookAnvil {
    private R196BookAnvil() {}
    private static R196EnchantmentCatalog.Definition definition(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(key -> R196EnchantmentCatalog.definition(key.location().toString())).orElse(null);
    }
    public static boolean hasSource(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet().stream().anyMatch(entry -> definition(entry.getKey()) != null);
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
        if (!R196EnchantmentTable.enchantable(first) || first.isEnchanted()) return rejected();
        ItemEnchantments stored = second.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        boolean applicableSource = stored.entrySet().stream().anyMatch(entry -> {
            var definition = definition(entry.getKey());
            return definition != null && definition.supported(first);
        });
        // An irrelevant source entry must not make an otherwise modern-only transfer free.
        if (!applicableSource && stored.entrySet().stream().anyMatch(entry -> definition(entry.getKey()) == null
                && entry.getKey().value().canEnchant(first))) return null;
        ItemEnchantments.Mutable resultEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        var accepted = new ArrayList<Holder<Enchantment>>();
        for (var entry : stored.entrySet()) {
            var definition = definition(entry.getKey());
            if (definition != null ? !definition.supported(first) : !entry.getKey().value().canEnchant(first)) continue;
            // Source-source transfer historically omits conflict tests. Modern additions retain modern conflicts.
            if (accepted.stream().anyMatch(previous -> (definition == null || definition(previous) == null)
                    && !Enchantment.areCompatible(previous, entry.getKey()))) return rejected();
            int level = Math.min(entry.getIntValue(), definition == null ? entry.getKey().value().getMaxLevel() : definition.maxLevel());
            if (level < 1) continue;
            resultEnchantments.set(entry.getKey(), level);
            accepted.add(entry.getKey());
        }
        if (accepted.isEmpty()) return rejected();
        ItemStack result = first.copyWithCount(1);
        result.set(DataComponents.ENCHANTMENTS, resultEnchantments.toImmutable());
        return new MaterialAnvilRules.Plan(result, second.getCount(), 0, false);
    }
}
