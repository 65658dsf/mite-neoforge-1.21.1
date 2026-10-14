package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import net.minecraft.world.item.FishingRodItem;

/** R196 rods have material durability and enchantability, and no equipment quality. @author NingMeng123 */
public final class R196FishingRodItem extends FishingRodItem implements R196RepairMaterial {
    private final Material material;
    public R196FishingRodItem(Material material, Properties properties) {
        super(properties.durability(material.rodDurability())); this.material = material;
    }
    public Material material() { return material; }
    @Override public String repairMaterial() { return material.id; }
    @Override public int repairComponents() { return 1; }
    @Override public boolean repairFishingRod() { return true; }
    @Override public int getEnchantmentValue() { return material.enchantability; }
}
