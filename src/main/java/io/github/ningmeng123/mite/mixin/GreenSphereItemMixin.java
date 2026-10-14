package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.ranged.SphereThrowing;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retain the green sphere's native ID, slime drops and crafting uses. @author NingMeng123 */
@Mixin(Item.class)
public abstract class GreenSphereItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mite$throwGreen(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if ((Object)this == Items.SLIME_BALL) cir.setReturnValue(SphereThrowing.use(level, player, hand));
    }
}
