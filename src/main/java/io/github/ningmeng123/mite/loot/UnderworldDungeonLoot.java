package io.github.ningmeng123.mite.loot;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.item.R196RecordItem;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.utility.UniqueMusicSavedData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import io.github.ningmeng123.mite.rules.R196SpawnRules;

/** afp/mk/xo's complete eight-roll, 27-slot underworld dungeon loot transaction. @author NingMeng123 */
public final class UnderworldDungeonLoot {
    private UnderworldDungeonLoot() {}

    public static ObjectArrayList<ItemStack> generate(LootContext context, ArmorStructureLootModifier.Pool pool) {
        RandomSource random = context.getRandom();
        // xo generates one uniform source enchantment and level before any weighted chest draw.
        var definitions = R196EnchantmentCatalog.sourceDefinitions();
        var definition = definitions.get(random.nextInt(definitions.size()));
        int level = random.nextInt(definition.sourceGeneratedLevel()) + 1;
        var holder = context.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(definition.resourceId())));
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(holder, level);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        ItemStack[] slots = new ItemStack[27];
        Arrays.fill(slots, ItemStack.EMPTY);
        UniqueMusicSavedData unique = UniqueMusicSavedData.get(context.getLevel());
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        int sourceY = R196SpawnRules.sourceY(origin == null ? 0 : (int)Math.floor(origin.y), true);
        ItemStack reservedRecord = ItemStack.EMPTY;
        for (int roll = 0; roll < pool.minimumRolls(); roll++) {
            ArmorStructureLootModifier.Entry selected = null;
            for (int attempt = 0; attempt < 4; attempt++) {
                selected = pool.select(random.nextInt(pool.sourceTotalWeight()));
                var item = MiteArmorContent.get(selected.item());
                if (item instanceof R196RecordItem record) {
                    // mk deliberately leaves the selected entry intact for an already claimed record.
                    // On retry four that literal source edge can emit the record as an ordinary stack.
                    if (!reservedRecord.isEmpty() || unique.has(record.recordName())) continue;
                    reservedRecord = new ItemStack(item);
                    selected = null;
                    continue;
                }
                // mk's yb (hoe) / xv (fishing rod) exclusion; this pool only contains the ancient-metal rod.
                if (sourceY < 48 && selected.item().equals("mite:fishing_rod_ancient_metal_hook")) {
                    selected = null;
                    continue;
                }
                break;
            }
            if (selected == null) continue;
            int count = selected.minimum() + random.nextInt(selected.maximum() - selected.minimum() + 1);
            ItemStack stack = selected.item().equals("minecraft:enchanted_book") ? book.copy()
                    : new ItemStack(MiteArmorContent.get(selected.item()));
            if (stack.getMaxStackSize() >= count) {
                stack.setCount(count);
                damageLikeSource(stack);
                slots[random.nextInt(slots.length)] = stack;
            } else for (int i = 0; i < count; i++) {
                ItemStack single = stack.copyWithCount(1);
                damageLikeSource(single);
                slots[random.nextInt(slots.length)] = single;
            }
        }
        if (!reservedRecord.isEmpty() && unique.mark(((R196RecordItem)reservedRecord.getItem()).recordName()))
            slots[random.nextInt(slots.length)] = reservedRecord;
        ObjectArrayList<ItemStack> result = new ObjectArrayList<>();
        for (ItemStack stack : slots) if (!stack.isEmpty()) result.add(stack);
        return result;
    }

    private static void damageLikeSource(ItemStack stack) {
        if (!stack.isDamageableItem()) return;
        // R196 ye.applyRandomItemStackDamageForChest uses Math.random, separate from the chest-selection stream.
        int tenths = (int)(Math.random() * 7.0);
        stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getMaxDamage() * tenths / 10));
    }
}
