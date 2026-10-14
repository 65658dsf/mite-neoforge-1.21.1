package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.R196CarrotOnAStickItem;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retained vanilla carrot-on-a-stick is the R196 iron-hook variant. @author NingMeng123 */
@Mixin(FoodOnAStickItem.class)
public abstract class RangedCarrotRodMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mite$sourceIronRod(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if ((Object)this == Items.CARROT_ON_A_STICK)
            cir.setReturnValue(R196CarrotOnAStickItem.useSource(level, player, hand, Material.IRON));
    }
}
