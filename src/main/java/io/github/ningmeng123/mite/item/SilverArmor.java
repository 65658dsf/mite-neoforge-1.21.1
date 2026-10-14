package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.mixin.MobEffectDurationAccessor;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** R196 silver coverage halves incoming poison duration at full plate coverage. @author NingMeng123 */
public final class SilverArmor {
    private SilverArmor() {}
    public static float coverage(LivingEntity owner) {
        float coverage = 0F;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR && slot != EquipmentSlot.BODY) continue;
            ItemStack stack = owner.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            var definition = MiteArmorContent.definition(stack.getItem());
            if (definition == null || !definition.material().equals("silver")) continue;
            if (definition.horse()) coverage += 1F;
            else coverage += (float)definition.components() / 24F * (definition.chainmail() ? .5F : 1F)
                    * EquipmentDurabilityRules.armorProtectionFactor(stack.getDamageValue(), stack.getMaxDamage(), owner instanceof Player);
        }
        return Math.clamp(coverage, 0F, 1F);
    }
    public static MobEffectInstance incomingPoison(LivingEntity owner, MobEffectInstance effect) {
        if (owner.level().isClientSide || !effect.is(MobEffects.POISON) || effect.isInfiniteDuration()) return effect;
        float coverage = coverage(owner);
        if (!(coverage > 0F)) return effect;
        MobEffectInstance copy = copyTree(effect);
        ((MobEffectDurationAccessor)(Object)copy).mite$setDuration((int)((float)effect.getDuration() * (1F - coverage * .5F)));
        return copy;
    }
    private static MobEffectInstance copyTree(MobEffectInstance effect) {
        MobEffectInstance copy = new MobEffectInstance(effect);
        copy.copyBlendState(effect);
        var hidden = ((MobEffectDurationAccessor)(Object)effect).mite$getHiddenEffect();
        if (hidden != null) ((MobEffectDurationAccessor)(Object)copy).mite$setHiddenEffect(copyTree(hidden));
        return copy;
    }
}
