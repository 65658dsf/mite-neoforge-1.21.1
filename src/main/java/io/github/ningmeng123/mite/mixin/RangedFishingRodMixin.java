package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.ranged.FishingCastContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 casting requires footing or a boat/cart, and no submerged head. @author NingMeng123 */
@Mixin(FishingRodItem.class)
public abstract class RangedFishingRodMixin {
    @WrapMethod(method = "use")
    private InteractionResultHolder<ItemStack> mite$actualCastHand(Level level, Player player, InteractionHand hand,
            Operation<InteractionResultHolder<ItemStack>> original) {
        var previous = FishingCastContext.begin(player, hand);
        try { return original.call(level, player, hand); }
        finally { FishingCastContext.end(previous); }
    }
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mite$castRestrictions(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (MiteRangedItems.rodMaterial(stack) == null || player.fishing != null) return;
        boolean vehicle = player.getVehicle() instanceof Boat || player.getVehicle() instanceof AbstractMinecart;
        if (!vehicle && (!player.onGround() || player.isInWater() || player.isUnderWater()
                || !level.getFluidState(net.minecraft.core.BlockPos.containing(player.getX(), player.getEyeY(), player.getZ())).isEmpty()))
            cir.setReturnValue(InteractionResultHolder.fail(stack));
    }
    @Inject(method = "getEnchantmentValue", at = @At("HEAD"), cancellable = true)
    private void mite$ironEnchantmentValue(CallbackInfoReturnable<Integer> cir) {
        if ((Object)this == Items.FISHING_ROD) cir.setReturnValue(30);
    }
}
