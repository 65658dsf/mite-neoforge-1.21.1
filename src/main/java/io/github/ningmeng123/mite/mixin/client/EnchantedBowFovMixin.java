package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.enchantment.RangedEnchantments;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.BowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Pull FOV follows the same full-draw threshold as server shots. @author NingMeng123 */
@Mixin(AbstractClientPlayer.class)
public abstract class EnchantedBowFovMixin {
    @ModifyExpressionValue(method = "getFieldOfViewModifier", at = @At(value = "CONSTANT", args = "floatValue=20.0"))
    private float mite$drawDuration(float original) {
        AbstractClientPlayer player = (AbstractClientPlayer)(Object)this;
        return RangedEnchantments.pullTicks(player.getUseItem(), player.level());
    }
    @ModifyExpressionValue(method = "getFieldOfViewModifier", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean mite$reinforcedBow(boolean original) {
        AbstractClientPlayer player = (AbstractClientPlayer)(Object)this;
        return original || player.getUseItem().getItem() instanceof BowItem;
    }
}
