package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.progression.EnchantmentSources;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.EnchantmentLevelProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One trial with summed source probabilities, including location-based durability rolls. @author NingMeng123 */
@Mixin(LootItemRandomChanceCondition.class)
public abstract class GearEnchantmentChanceMixin {
    @Inject(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z", at = @At("HEAD"), cancellable = true)
    private void mite$oneProbabilityTrial(LootContext context, CallbackInfoReturnable<Boolean> cir) {
        var frame = EnchantmentSources.currentFrame();
        if (frame == null || frame.levels().normal() <= 0 || frame.levels().affix() <= 0) return;
        var chance = ((LootItemRandomChanceCondition)(Object)this).chance();
        double probability = EnchantmentSources.probability(frame.levels(), level -> chance instanceof EnchantmentLevelProvider provider
                ? provider.amount().calculate(level) : chance.getFloat(context));
        cir.setReturnValue(context.getRandom().nextFloat() < probability);
    }
}
