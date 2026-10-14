package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.R196StoneBucketItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ItemVessel/ss keep the impervious vessel after its lava-vulnerable stone contents are destroyed. @author NingMeng123 */
@Mixin(ItemEntity.class)
public abstract class StoneBucketDestructionMixin {
    @Shadow private int health;
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void mite$keepAdamantiumContainer(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity entity = (ItemEntity)(Object)this;
        if (entity.level().isClientSide || entity.isInvulnerableTo(source) || !source.is(DamageTypes.LAVA)) return;
        ItemStack stack = entity.getItem();
        if (!(stack.getItem() instanceof R196StoneBucketItem bucket) || !bucket.material().equals("adamantium") || health - amount > 0) return;
        entity.setItem(new ItemStack(R196StoneBucketItem.empty("adamantium"), stack.getCount()));
        health = 5;
        cir.setReturnValue(true);
    }
}
