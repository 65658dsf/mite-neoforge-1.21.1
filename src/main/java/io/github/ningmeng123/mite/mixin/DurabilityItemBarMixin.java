package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Use the stack's quality-adjusted maximum for the default durability bar. @author NingMeng123 */
@Mixin(Item.class)
public abstract class DurabilityItemBarMixin {
    @Redirect(method = {"getBarWidth", "getBarColor"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;getMaxDamage(Lnet/minecraft/world/item/ItemStack;)I"), require = 2)
    private int mite$stackMaximumForDurabilityBar(Item item, ItemStack stack) {
        return stack.getMaxDamage();
    }
}
