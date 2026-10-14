package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.registry.MiteArmorContent.Definition;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Player armor with source durability, fractional protection and material-limited quality. @author NingMeng123 */
public final class R196ArmorItem extends ArmorItem {
    private final Definition definition;
    public R196ArmorItem(Holder<ArmorMaterial> material, Definition definition, Properties properties) {
        super(material, definition.type(), properties.durability(definition.baseDurability()).attributes(attributes(definition)));
        this.definition = definition;
        EquipmentDurability.registerArmor(this, definition.profile());
    }
    public static ItemAttributeModifiers attributes(Definition definition) {
        double protection = (double)(definition.components() * (definition.materialProtection() - (definition.chainmail() ? 2 : 0))) / 24D;
        return ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                new AttributeModifier(ResourceLocation.withDefaultNamespace("armor." + definition.type().getName()),
                        protection, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.bySlot(definition.type().getSlot())).build();
    }
}
