package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Disabled enchantments are omitted before random loot selection. @author NingMeng123 */
@Mixin(EnchantRandomlyFunction.class)
public abstract class ConfiguredRandomEnchantmentMixin {
    @ModifyExpressionValue(method = "run", at = @At(value = "INVOKE",
            target = "Ljava/util/stream/Stream;toList()Ljava/util/List;"), require = 1, allow = 1)
    private List<Holder<Enchantment>> mite$enabledLootEnchantments(List<Holder<Enchantment>> candidates) {
        return candidates.stream().filter(ConfiguredEnchantments::enabled).toList();
    }
}
