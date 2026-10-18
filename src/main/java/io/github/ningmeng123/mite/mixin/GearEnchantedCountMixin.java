package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.progression.EnchantmentSources;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Looting rolls each source's numerical contribution, retaining one base stack and result limit. @author NingMeng123 */
@Mixin(EnchantedCountIncreaseFunction.class)
public abstract class GearEnchantedCountMixin {
    @Shadow @Final private Holder<Enchantment> enchantment;
    @Shadow @Final private NumberProvider value;
    @Shadow @Final private int limit;
    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void mite$addLootingContributions(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        if (EnchantmentSources.mode() != EnchantmentSources.Mode.COMBINED
                || !(context.getParamOrNull(LootContextParams.ATTACKING_ENTITY) instanceof LivingEntity attacker)
                || !EnchantmentSources.hasAffixes(attacker)) return;
        var levels = EnchantmentSources.highestLevels(attacker, enchantment);
        int extra = (int)Math.round(EnchantmentSources.number(levels, level -> level * value.getFloat(context)));
        stack.grow(extra);
        if (limit > 0) stack.limitSize(limit);
        cir.setReturnValue(stack);
    }
}
