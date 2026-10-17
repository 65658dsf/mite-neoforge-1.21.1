package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.SourceVillagerTrades;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The native getter covers fresh stock, attained levels and saved villagers without ticking every trade. @author NingMeng123 */
@Mixin(AbstractVillager.class)
public abstract class SourceVillagerTradesMixin {
    @Inject(method = "getOffers", at = @At("RETURN"))
    private void mite$sourceStockAlongsideModernProfessions(CallbackInfoReturnable<MerchantOffers> cir) {
        if ((Object)this instanceof Villager villager) SourceVillagerTrades.ensure(villager,cir.getReturnValue());
    }
}
