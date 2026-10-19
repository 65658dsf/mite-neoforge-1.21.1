package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native librarian candidates respect disabled enchantments. @author NingMeng123 */
@Mixin(VillagerTrades.EnchantBookForEmeralds.class)
public abstract class ConfiguredVillagerEnchantmentMixin {
    @WrapOperation(method = "getOffer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Registry;getRandomElementOf(Lnet/minecraft/tags/TagKey;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"))
    private Optional<Holder<Enchantment>> mite$enabledTradeEnchantments(Registry<Enchantment> registry, TagKey<Enchantment> tag,
            RandomSource random, Operation<Optional<Holder<Enchantment>>> original) {
        List<Holder<Enchantment>> candidates = registry.getTag(tag)
                .map(set -> set.stream().filter(ConfiguredEnchantments::enabled).toList()).orElse(List.of());
        return net.minecraft.Util.getRandomSafe(candidates, random);
    }
}
