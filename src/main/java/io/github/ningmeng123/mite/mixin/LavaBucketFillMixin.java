package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.LavaBucketFilling;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Retains the original iron bucket identity while sharing source pickup melting. @author NingMeng123 */
@Mixin(BucketItem.class)
public abstract class LavaBucketFillMixin {
    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemUtils;createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mite$sourceMelting(ItemStack empty, Player player, ItemStack filled) {
        return LavaBucketFilling.convert(empty, player, filled);
    }
}
