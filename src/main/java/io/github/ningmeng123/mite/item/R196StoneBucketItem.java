package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.registry.MiteFoods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A solidified lava vessel cannot pour liquid or place blocks. @author NingMeng123 */
public final class R196StoneBucketItem extends Item {
    private final String material;
    public R196StoneBucketItem(String material) {
        super(new Properties().stacksTo(1).craftRemainder(empty(material)));
        this.material = material;
    }
    public String material() { return material; }
    public static Item empty(String material) { return material.equals("iron") ? Items.BUCKET : MiteFoods.get(material + "_bucket"); }
    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
    @Override public ItemStack getCraftingRemainingItem(ItemStack stack) { return new ItemStack(empty(material)); }
}
