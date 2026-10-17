package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The retained native shield is the iron member of the source metal adaptation. @author NingMeng123 */
@Mixin(ShieldItem.class)
public abstract class ShieldItemMixin extends Item {
    protected ShieldItemMixin(Properties properties) { super(properties); }

    @Override public int getEnchantmentValue() {
        return (Object)this == Items.SHIELD ? 30 : super.getEnchantmentValue();
    }

    @Inject(method = "isValidRepairItem", at = @At("HEAD"), cancellable = true)
    private void mite$repairIronShieldWithIronNuggets(ItemStack shield, ItemStack material, CallbackInfoReturnable<Boolean> cir) {
        if (shield.is(Items.SHIELD)) cir.setReturnValue(material.is(Items.IRON_NUGGET));
    }
}
