package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.survival.AutoRestock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/** The actual finished result, including a returned vessel, determines depletion. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class AutoRestockConsumptionMixin {
    @WrapMethod(method = "completeUsingItem")
    private void mite$finishedIngestion(Operation<Void> original) {
        LivingEntity entity = (LivingEntity)(Object)this;
        var before = entity instanceof Player player ? AutoRestock.before(player, entity.getUsedItemHand()) : null;
        original.call();
        AutoRestock.after(before);
    }
    @WrapMethod(method = "releaseUsingItem")
    private void mite$releasedTool(Operation<Void> original) {
        LivingEntity entity = (LivingEntity)(Object)this;
        var before = entity instanceof Player player ? AutoRestock.before(player, entity.getUsedItemHand()) : null;
        original.call();
        AutoRestock.after(before);
    }
}
