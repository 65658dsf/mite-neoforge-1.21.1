package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.rules.R196VanillaMobRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Source sunlight checks a helmet without consuming its durability. @author NingMeng123 */
@Mixin({AbstractSkeleton.class, Zombie.class})
public abstract class R196VanillaSunHelmetMixin {
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    private void mite$sourceHelmetKeepsDurability(ItemStack stack, int damage, Operation<Void> original) {
        if (!R196VanillaMobRules.managed(((LivingEntity)(Object)this).getType())) original.call(stack, damage);
    }
}
