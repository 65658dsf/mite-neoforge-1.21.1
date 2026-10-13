package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteTools;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IItemStackExtension;
import org.spongepowered.asm.mixin.Mixin;

/** Adapt retained vanilla item identities through NeoForge's stack-aware contract. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class ToolEnchantmentValueMixin implements IItemStackExtension {
    @Override
    public int getEnchantmentValue() {
        ItemStack stack = (ItemStack)(Object)this;
        var definition = MiteTools.definition(stack);
        return definition == null ? stack.getItem().getEnchantmentValue(stack) : definition.enchantability();
    }
}
