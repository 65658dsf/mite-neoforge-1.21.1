package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.progression.EnchantmentSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Loot probabilities count their unenchanted baseline once and make a single trial. @author NingMeng123 */
@Mixin({BonusLevelTableCondition.class, LootItemRandomChanceWithEnchantedBonusCondition.class})
public abstract class GearLootProbabilityMixin {
    @Inject(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z", at = @At("HEAD"), cancellable = true)
    private void mite$addProbabilityContributions(LootContext context, CallbackInfoReturnable<Boolean> cir) {
        if (EnchantmentSources.mode() != EnchantmentSources.Mode.COMBINED) return;
        Object self = this;
        double chance;
        if (self instanceof BonusLevelTableCondition condition) {
            ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
            if (!EnchantmentSources.hasAffixes(tool)) return;
            chance = EnchantmentSources.delta(EnchantmentSources.levels(tool, condition.enchantment()), condition.values().getFirst(),
                    level -> condition.values().get(Math.min(level, condition.values().size() - 1)));
        } else if (self instanceof LootItemRandomChanceWithEnchantedBonusCondition condition) {
            if (!(context.getParamOrNull(LootContextParams.ATTACKING_ENTITY) instanceof LivingEntity attacker)
                    || !EnchantmentSources.hasAffixes(attacker)) return;
            chance = EnchantmentSources.equippedDelta(attacker, condition.enchantment(), condition.unenchantedChance(),
                    level -> condition.enchantedChance().calculate(level));
        } else return;
        cir.setReturnValue(context.getRandom().nextFloat() < Math.clamp(chance, 0D, 1D));
    }
}
