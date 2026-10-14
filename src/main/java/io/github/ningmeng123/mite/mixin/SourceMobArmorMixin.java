package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** R196 og.a: generated tiers 1–4 are rusted chain, copper chain, rusted plate and copper plate. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class SourceMobArmorMixin {
    @WrapOperation(method = "populateDefaultEquipmentSlots", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;getEquipmentForSlot(Lnet/minecraft/world/entity/EquipmentSlot;I)Lnet/minecraft/world/item/Item;"))
    private Item mite$sourceArmorTier(EquipmentSlot slot, int tier, Operation<Item> original) {
        if (tier < 1 || tier > 4 || slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return original.call(slot, tier);
        String suffix = switch (slot) { case HEAD -> "helmet"; case CHEST -> "chestplate"; case LEGS -> "leggings"; case FEET -> "boots"; default -> throw new IllegalStateException(); };
        String stem = switch (tier) { case 1 -> "rusted_iron_chain_"; case 2 -> "copper_chain_"; case 3 -> "rusted_iron_"; default -> "copper_"; };
        return MiteArmorContent.get("mite:" + stem + suffix);
    }
}
