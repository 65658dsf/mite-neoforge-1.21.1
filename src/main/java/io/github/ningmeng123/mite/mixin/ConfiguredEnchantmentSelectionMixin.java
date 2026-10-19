package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Omit disabled entries before native weighted draws and table clue generation. @author NingMeng123 */
@Mixin(EnchantmentHelper.class)
public abstract class ConfiguredEnchantmentSelectionMixin {
    @ModifyVariable(method = "getAvailableEnchantmentResults", at = @At("HEAD"), argsOnly = true)
    private static Stream<Holder<Enchantment>> mite$enabledCandidates(Stream<Holder<Enchantment>> candidates) {
        return candidates.filter(ConfiguredEnchantments::enabled);
    }
}
