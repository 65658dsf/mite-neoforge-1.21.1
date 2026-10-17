package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteShields;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShieldDecorationRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Banner decoration preserves the actual metal shield and all of its components. @author NingMeng123 */
@Mixin(ShieldDecorationRecipe.class)
public abstract class ShieldDecorationMixin {
    @Redirect(method = {"matches", "assemble"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean mite$decorateAnyMetalShield(ItemStack stack, Item expected) {
        return expected == Items.SHIELD && MiteShields.definition(stack) != null || stack.is(expected);
    }
}
