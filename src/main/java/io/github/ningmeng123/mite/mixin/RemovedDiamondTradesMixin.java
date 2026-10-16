package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Filter generated and already-saved offers, including retired equipment required as payment. @author NingMeng123 */
@Mixin(AbstractVillager.class)
public abstract class RemovedDiamondTradesMixin {
    @Inject(method = "getOffers", at = @At("RETURN"))
    private void mite$removeDiamondTrades(CallbackInfoReturnable<MerchantOffers> cir) {
        cir.getReturnValue().removeIf(offer -> RemovedDiamondEquipment.isRemoved(offer.getResult())
                || RemovedDiamondEquipment.isRemoved(offer.getBaseCostA())
                || RemovedDiamondEquipment.isRemoved(offer.getCostB()));
    }
}
