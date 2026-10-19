package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** New loot, books, commands and anvil additions share the same explicit configuration. @author NingMeng123 */
@Mixin(ItemEnchantments.Mutable.class)
public abstract class ConfiguredItemEnchantmentsMixin {
    @ModifyVariable(method = {"set", "upgrade"}, at = @At("HEAD"), argsOnly = true)
    private int mite$configuredLevel(int level, Holder<Enchantment> enchantment, int requestedLevel) {
        return ConfiguredEnchantments.effectiveLevel(enchantment, level);
    }
}
