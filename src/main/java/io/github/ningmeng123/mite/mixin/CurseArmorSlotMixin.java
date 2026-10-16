package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.curse.CursePlayerEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source vw rejects direct and shift-click placement, including non-armor helmets. @author NingMeng123 */
@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class CurseArmorSlotMixin {
    @Shadow @Final private LivingEntity owner;
    @Shadow @Final private EquipmentSlot slot;
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void mite$cannotWearArmor(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (owner instanceof Player player && !stack.isEmpty() && CursePlayerEffects.cannotEquip(player, slot))
            cir.setReturnValue(false);
    }
}
