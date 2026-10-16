package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Attribute construction needs the wearer to keep NPC loot wear separate from protection. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class ArmorAttributeScopeMixin {
    @WrapMethod(method = "collectEquipmentChanges")
    private Map<EquipmentSlot, ItemStack> mite$armorWearer(Operation<Map<EquipmentSlot, ItemStack>> original) {
        try (var scope = EquipmentDurability.armorAttributeScope((LivingEntity)(Object)this)) {
            return original.call();
        }
    }
}
