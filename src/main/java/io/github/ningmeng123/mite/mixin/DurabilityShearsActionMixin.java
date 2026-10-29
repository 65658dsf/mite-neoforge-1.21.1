package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.R196ToolItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 sheep shearing costs fifty points; modern block clipping retains its original action. @author NingMeng123 */
@Mixin(ShearsItem.class)
public abstract class DurabilityShearsActionMixin {
    @Inject(method = "useOn", at = @At("RETURN"), cancellable = true)
    private void mite$clipLeafBlocks(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        if (callback.getReturnValue() == InteractionResult.PASS) callback.setReturnValue(R196ToolItem.clipBlock(context));
    }

    @WrapOperation(method = "interactLivingEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void mite$shearingWear(ItemStack stack, int amount, LivingEntity owner,
            EquipmentSlot slot, Operation<Void> original) {
        if (EquipmentDurability.toolProfile(stack) != null) EquipmentDurability.hurt(stack, 50, owner, slot);
        else original.call(stack, amount, owner, slot);
    }
}
