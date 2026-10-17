package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.item.MiteBowlItem;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.registry.MiteFoods;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Live worms consume compostable food in ordinary chests and leave manure and containers. @author NingMeng123 */
public final class ChestWormComposting {
    public static final String COMPOST_KEY = "mite_worm_compost";
    private ChestWormComposting() {}
    public static float compostingValue(ItemStack stack) {
        if (stack.isEmpty() || stack.is(MiteFoods.get("raw_worm")) || stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.GOLDEN_CARROT)
                || stack.is(Items.EGG) || stack.is(Items.MILK_BUCKET)
                || stack.is(Items.WHEAT_SEEDS) || stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS)
                || stack.is(Items.BEETROOT_SEEDS) || stack.is(Items.TORCHFLOWER_SEEDS) || stack.is(Items.PITCHER_POD)
                || stack.getItem() instanceof MiteBowlItem bowl && bowl.contents() == MiteBowlItem.Contents.MILK
                || stack.getItem() instanceof MiteBucketItem bucket && bucket.contents() == MiteBucketItem.Contents.MILK) return 0F;
        if (stack.is(MiteFoods.get("cooked_worm"))) return .1F;
        if (stack.is(MiteFoods.get("flour"))) return .8F;
        if (stack.is(Items.WHEAT)) return .5F;
        if (stack.is(Items.SUGAR) || stack.is(Items.PAPER)) return .1F;
        // zh.getCompostingValue: block items have independent values, even without FOOD.
        if (stack.is(Items.HAY_BLOCK)) return 4.5F;
        if (stack.is(ItemTags.LEAVES) || stack.is(Items.VINE)) return 1F;
        if (stack.is(Items.MELON) || stack.is(Items.PUMPKIN) || stack.is(Items.CARVED_PUMPKIN)
                || stack.is(Items.JACK_O_LANTERN)) return 2F;
        if (stack.is(Items.BROWN_MUSHROOM) || stack.is(Items.RED_MUSHROOM)
                || stack.is(Items.SHORT_GRASS) || stack.is(Items.FERN) || stack.is(Items.LILY_PAD)) return .5F;
        if (stack.is(ItemTags.FLOWERS)) return .25F;
        if (stack.is(Items.CAKE)) return 2.4F;
        var item = stack.getItem();
        if (item == Items.COOKED_BEEF) return compostingValue(new ItemStack(Items.BEEF));
        if (item == Items.COOKED_PORKCHOP) return compostingValue(new ItemStack(Items.PORKCHOP));
        if (item == Items.COOKED_CHICKEN) return compostingValue(new ItemStack(Items.CHICKEN));
        if (item == Items.COOKED_COD) return compostingValue(new ItemStack(Items.COD));
        if (item == Items.COOKED_SALMON) return compostingValue(new ItemStack(Items.SALMON));
        if (item == Items.COOKED_MUTTON) return compostingValue(new ItemStack(Items.MUTTON));
        if (item == Items.COOKED_RABBIT) return compostingValue(new ItemStack(Items.RABBIT));
        if (stack.is(MiteFoods.get("cooked_large_fish"))) return compostingValue(new ItemStack(MiteFoods.get("raw_large_fish")));
        var food = MiteFoodCatalog.profile(stack);
        return food == null ? 0F : (food.satiation() + food.nutrition()) * .1F;
    }
    public static void process(ChestBlockEntity chest, RandomSource random) {
        // Inspecting an unopened modern loot chest must not generate its player-context loot.
        if (chest.getLootTable() != null) return;
        int worms = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++)
            if (chest.getItem(slot).is(MiteFoods.get("raw_worm"))) worms += chest.getItem(slot).getCount();
        if (worms == 0) return;
        float compost = chest.getPersistentData().getFloat(COMPOST_KEY);
        if (!Float.isFinite(compost) || compost < 0F) compost = 0F;
        if (compost >= 1F) { save(chest, flushManure(chest, compost)); return; }
        for (int worm = 0; worm < worms; worm++) {
            List<Integer> foods = new ArrayList<>();
            for (int slot = 0; slot < chest.getContainerSize(); slot++) if (compostingValue(chest.getItem(slot)) > 0F) foods.add(slot);
            if (foods.isEmpty()) break;
            int sourceSlot = foods.get(random.nextInt(foods.size()));
            ItemStack food = chest.getItem(sourceSlot);
            float value = compostingValue(food);
            if (random.nextInt(Math.max(1, (int) (100F * value))) != 0) continue;
            ItemStack[] after = new ItemStack[chest.getContainerSize()];
            for (int slot = 0; slot < after.length; slot++) after[slot] = chest.getItem(slot).copy();
            after[sourceSlot].shrink(1);
            boolean fits = true;
            for (ItemStack remainder : remains(food)) if (!insert(after, remainder)) { fits = false; break; }
            if (!fits) continue;
            // Require room for the manure as the source does, even for a fractional
            // input. The consumed food slot can provide the necessary empty slot.
            ItemStack[] probe = new ItemStack[after.length];
            for (int slot = 0; slot < after.length; slot++) probe[slot] = after[slot].copy();
            if (!insert(probe, new ItemStack(MiteFoods.get("manure")))) continue;
            for (int slot = 0; slot < after.length; slot++) chest.setItem(slot, after[slot]);
            compost += value;
            compost = flushManure(chest, compost);
        }
        save(chest, compost);
    }
    private static List<ItemStack> remains(ItemStack food) {
        if (food.is(Items.MELON)) return List.of(new ItemStack(Items.MELON_SEEDS));
        if (food.is(Items.JACK_O_LANTERN)) return List.of(new ItemStack(Items.PUMPKIN_SEEDS), new ItemStack(Items.TORCH));
        if (food.is(Items.PUMPKIN) || food.is(Items.CARVED_PUMPKIN)) return List.of(new ItemStack(Items.PUMPKIN_SEEDS));
        FoodProperties properties = food.get(DataComponents.FOOD);
        return properties == null ? List.of() : properties.usingConvertsTo().map(stack -> List.of(stack.copy())).orElseGet(List::of);
    }
    private static float flushManure(ChestBlockEntity chest, float compost) {
        while (compost >= 1F) {
            ItemStack[] contents = new ItemStack[chest.getContainerSize()];
            for (int slot = 0; slot < contents.length; slot++) contents[slot] = chest.getItem(slot).copy();
            if (!insert(contents, new ItemStack(MiteFoods.get("manure")))) break;
            for (int slot = 0; slot < contents.length; slot++) chest.setItem(slot, contents[slot]);
            compost -= 1F;
        }
        return compost;
    }
    private static boolean insert(ItemStack[] contents, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        for (ItemStack stack : contents) if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, incoming)) {
            int amount = Math.min(incoming.getCount(), stack.getMaxStackSize() - stack.getCount());
            if (amount > 0) { stack.grow(amount); incoming.shrink(amount); }
            if (incoming.isEmpty()) return true;
        }
        for (int slot = 0; slot < contents.length; slot++) if (contents[slot].isEmpty()) {
            int amount = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            contents[slot] = incoming.copyWithCount(amount);
            incoming.shrink(amount);
            if (incoming.isEmpty()) return true;
        }
        return false;
    }
    private static void save(ChestBlockEntity chest, float compost) {
        chest.getPersistentData().putFloat(COMPOST_KEY, compost);
        chest.setChanged();
    }
}
