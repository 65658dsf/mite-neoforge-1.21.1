package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Fortune contributes independent bonus counts from the same original drop count. @author NingMeng123 */
@Mixin(ApplyBonusCount.class)
public abstract class GearApplyBonusCountMixin {
    @Shadow @Final private Holder<Enchantment> enchantment;

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/functions/ApplyBonusCount$Formula;calculateNewCount(Lnet/minecraft/util/RandomSource;II)I"))
    private int mite$independentBonuses(@Coerce Object formula, RandomSource random, int count, int ignoredLevel,
            Operation<Integer> original, @Local(argsOnly = true) LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (!EnchantmentSources.hasAffixes(tool) || EnchantmentSources.mode() != EnchantmentSources.Mode.COMBINED)
            return original.call(formula, random, count, ignoredLevel);
        var levels = EnchantmentSources.levels(tool, enchantment);
        // This private built-in formula adds L Bernoulli trials plus one shared fixed trial budget.
        // Combining its trial counts is exact; replaying it would duplicate its unenchanted extra rounds.
        if (formula.getClass().getSimpleName().equals("BinomialWithBonusCount"))
            return original.call(formula, random, count, levels.totalLevel());
        long result = count;
        if (levels.normal() > 0) result += (long)original.call(formula, random, count, levels.normal()) - count;
        if (levels.affix() > 0) result += (long)original.call(formula, random, count, levels.affix()) - count;
        return (int)Math.clamp(result, 0L, Integer.MAX_VALUE);
    }
}
