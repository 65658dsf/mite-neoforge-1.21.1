package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.registry.MiteFoods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** The nine xx.setCookingResult pairs also apply to items destroyed by fire. @author NingMeng123 */
public final class FoodCooking {
    private FoodCooking() {}
    @Nullable public static Item result(ItemStack raw) {
        if(raw.is(Items.PORKCHOP)) return Items.COOKED_PORKCHOP;
        if(raw.is(Items.COD)) return Items.COOKED_COD;
        if(raw.is(Items.BEEF)) return Items.COOKED_BEEF;
        if(raw.is(Items.CHICKEN)) return Items.COOKED_CHICKEN;
        if(raw.is(Items.POTATO)) return Items.BAKED_POTATO;
        if(raw.is(Items.MUTTON)) return Items.COOKED_MUTTON;
        if(raw.is(MiteFoods.DOUGH.get())) return Items.BREAD;
        if(raw.is(MiteFoods.RAW_LARGE_FISH.get())) return MiteFoods.COOKED_LARGE_FISH.get();
        if(raw.is(MiteFoods.RAW_WORM.get())) return MiteFoods.COOKED_WORM.get();
        return null;
    }
}
