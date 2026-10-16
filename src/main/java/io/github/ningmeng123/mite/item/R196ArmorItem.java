package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.registry.MiteArmorContent.Definition;
import io.github.ningmeng123.mite.rules.ArmorBalanceRules;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Source durability and quality with modern material armor and toughness. @author NingMeng123 */
public final class R196ArmorItem extends ArmorItem {
    private final Definition definition;
    public R196ArmorItem(Holder<ArmorMaterial> material, Definition definition, Properties properties) {
        super(material, definition.type(), properties.durability(definition.baseDurability()).attributes(attributes(definition)));
        this.definition = definition;
        EquipmentDurability.registerArmor(this, definition.profile());
    }
    public static ItemAttributeModifiers attributes(Definition definition) {
        var stats = ArmorBalanceRules.stats(definition.material(), definition.chainmail());
        var id = ResourceLocation.withDefaultNamespace("armor." + definition.type().getName());
        var slot = EquipmentSlotGroup.bySlot(definition.type().getSlot());
        var builder = ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                new AttributeModifier(id, stats.protection(definition.components()), AttributeModifier.Operation.ADD_VALUE), slot);
        if (stats.toughness() > 0F) builder.add(Attributes.ARMOR_TOUGHNESS,
                new AttributeModifier(id, stats.toughness(), AttributeModifier.Operation.ADD_VALUE), slot);
        return builder.build();
    }
}
