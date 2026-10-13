package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.QualityRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** R196 aaf:323-353 leather repair; quality eligibility and timing belong to the server controller. @author NingMeng123 */
public final class LeatherMendingRecipe extends CustomRecipe {
    public LeatherMendingRecipe(CraftingBookCategory category) { super(category); }

    @Override
    public boolean matches(CraftingInput input, Level level) { return evaluate(input) != null; }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Repair repair = evaluate(input);
        if (repair == null) return ItemStack.EMPTY;
        // aaf copies the original armor stack, preserving quality, dye, enchantments and all other data.
        ItemStack result = repair.armor().copyWithCount(1);
        result.setDamageValue(Math.max(repair.armor().getDamageValue() - repair.consumption() * repair.step(), 0));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    @Override
    public RecipeSerializer<?> getSerializer() { return QualityRecipeSerializers.LEATHER_MENDING.get(); }

    /** Number consumed from the single string/sinew slot; the one armor item is naturally capped at one. */
    public static int materialConsumption(CraftingInput input) {
        Repair repair = evaluate(input);
        return repair == null ? 0 : repair.consumption();
    }

    /** R196 sets difficulty to num_sinews_to_use * 50, before the fixed input quality multiplier. */
    public static float difficulty(CraftingInput input) { return materialConsumption(input) * 50F; }

    /** Existing input object for immutable inspection; callers must not mutate it. Empty for invalid input. */
    public static ItemStack inputArmor(CraftingInput input) {
        Repair repair = evaluate(input);
        return repair == null ? ItemStack.EMPTY : repair.armor();
    }

    /** wh:305-309: leather repair cost equals its component count, not the vanilla material-repair fraction. */
    public static int repairCost(ItemStack armor) {
        if (armor.is(Items.LEATHER_HELMET)) return 5;
        if (armor.is(Items.LEATHER_CHESTPLATE)) return 8;
        if (armor.is(Items.LEATHER_LEGGINGS)) return 7;
        if (armor.is(Items.LEATHER_BOOTS)) return 4;
        return 0;
    }

    private static Repair evaluate(CraftingInput input) {
        if (input.ingredientCount() != 2) return null;
        ItemStack armor = ItemStack.EMPTY;
        ItemStack thread = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (isThread(stack)) {
                if (!thread.isEmpty()) return null;
                thread = stack;
            } else if (repairCost(stack) > 0 && stack.getCount() == 1 && stack.isDamaged()) {
                if (!armor.isEmpty()) return null;
                armor = stack;
            } else return null;
        }
        if (armor.isEmpty() || thread.isEmpty()) return null;
        int step = armor.getMaxDamage() / repairCost(armor);
        // Normal R196 leather always has a positive step, even at wretched quality. Reject malformed stacks.
        if (step < 1) return null;
        int damage = armor.getDamageValue();
        int count = damage / step;
        if (damage % step != 0) count++;
        // Intentionally keep the final partial step for another batch, except when only one step is needed.
        if (count > 1 && (long)count * step > damage) count--;
        count = Math.min(count, thread.getCount());
        return count > 0 ? new Repair(armor, step, count) : null;
    }

    private static boolean isThread(ItemStack stack) {
        return stack.is(Items.STRING) || stack.is(MiteItems.SINEW.get());
    }

    private record Repair(ItemStack armor, int step, int consumption) {}
}
