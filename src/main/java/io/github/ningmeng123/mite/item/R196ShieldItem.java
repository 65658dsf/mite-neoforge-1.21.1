package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

/** Shields retain native blocking while using the same source metal/quality rules as equipment. @author NingMeng123 */
public final class R196ShieldItem extends ShieldItem implements EquipmentDurability.QualityProfiled {
    private final MiteShields.Definition definition;

    public R196ShieldItem(MiteShields.Definition definition, Properties properties) {
        super(properties.durability(definition.baseDurability()));
        this.definition = definition;
    }

    public MiteShields.Definition definition() { return definition; }
    @Override public Quality maximumQuality() { return definition.maxQuality(); }
    @Override public int getEnchantmentValue() { return definition.enchantability(); }

    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        Item material = MaterialAnvilRules.repairItem(definition.material());
        return material != null && repair.is(material);
    }
}
