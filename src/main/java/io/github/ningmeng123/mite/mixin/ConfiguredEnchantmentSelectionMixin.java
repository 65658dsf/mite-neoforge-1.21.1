package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import io.github.ningmeng123.mite.enchantment.NaturalEnchantmentLimits;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Apply configured participation chances before native weighted draws and table clues. @author NingMeng123 */
@Mixin(EnchantmentHelper.class)
public abstract class ConfiguredEnchantmentSelectionMixin {
    @ModifyVariable(method = "selectEnchantment", at = @At("HEAD"), argsOnly = true)
    private static Stream<Holder<Enchantment>> mite$randomCandidates(Stream<Holder<Enchantment>> candidates,
            RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> original) {
        return candidates.filter(holder -> ConfiguredEnchantments.availableForRandomSelection(holder, random));
    }

    @ModifyVariable(method = "getAvailableEnchantmentResults", at = @At("HEAD"), argsOnly = true)
    private static Stream<Holder<Enchantment>> mite$enabledCandidates(Stream<Holder<Enchantment>> candidates) {
        return candidates.filter(ConfiguredEnchantments::enabled);
    }

    @ModifyExpressionValue(method = "enchantItem(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILjava/util/stream/Stream;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;selectEnchantment(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILjava/util/stream/Stream;)Ljava/util/List;"))
    private static List<EnchantmentInstance> mite$naturalRewardLevels(List<EnchantmentInstance> selected) {
        return selected.stream().map(entry -> new EnchantmentInstance(entry.enchantment,
                Math.min(entry.level, NaturalEnchantmentLimits.maximumLevel(entry.enchantment)))).toList();
    }
}
