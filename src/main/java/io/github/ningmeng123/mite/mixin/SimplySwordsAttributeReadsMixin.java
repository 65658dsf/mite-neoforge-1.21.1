package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Native thrown/special attacks read the same normalized weapon attributes as melee. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.sweenus.simplyswords.util.HelperMethods", remap = false)
public abstract class SimplySwordsAttributeReadsMixin {
    @WrapOperation(method = {"getAttackFromSlot", "getAttackFromStack"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;",
            remap = false), require = 1, remap = false)
    private static Object mite$normalizedWeaponAttributes(ItemStack stack, DataComponentType<?> component,
            Object fallback, Operation<Object> original) {
        return component == DataComponents.ATTRIBUTE_MODIFIERS ? stack.getAttributeModifiers() : original.call(stack, component, fallback);
    }
}
