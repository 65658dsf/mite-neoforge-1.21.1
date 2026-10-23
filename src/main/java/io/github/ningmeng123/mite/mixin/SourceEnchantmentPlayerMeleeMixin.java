package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.ningmeng123.mite.combat.SourcePlayerCombat;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.SourcePlayerCombatRules;
import java.util.function.Consumer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Source uf:1101 disarms before hurt; proc effects follow the real primary hit, not modern sweep copies. @author NingMeng123 */
@Mixin(Player.class)
public abstract class SourceEnchantmentPlayerMeleeMixin {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;getAttackDamageBonus(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;)F"))
    private float mite$deferSourceMaterialBonus(Item item, Entity target, float base,
            DamageSource source, Operation<Float> original) {
        // uf applies silver/blunt AFTER critical and level, before the additive enchantment term.
        return MiteTools.definition(((Player)(Object)this).getMainHandItem()) != null ? 0F
                : original.call(item, target, base, source);
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mite$sourceMelee(Entity target, DamageSource source, float amount, Operation<Boolean> original,
            @Local(ordinal = 0) float criticalBase, @Local(ordinal = 1) float enchantmentDamage,
            @Local(ordinal = 3) LocalFloatRef nativeTotal) {
        Player player = (Player)(Object)this;
        if (!(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) > 0D)) return false;
        var tool = MiteTools.definition(player.getMainHandItem());
        boolean silver = tool != null && tool.material().equals("silver") && target.getType().is(EntityTypeTags.UNDEAD);
        boolean blunt = tool != null && target instanceof AbstractSkeleton && switch (tool.family()) {
            case CLUB, CUDGEL, WAR_HAMMER -> true; default -> false;
        };
        float damage = io.github.ningmeng123.mite.progression.GearModifierEffects.meleeDamage(player,
                SourcePlayerCombatRules.primaryDamage(criticalBase, player.experienceLevel,
                        silver, blunt, enchantmentDamage, SourcePlayerCombat.suspended(player)));
        // Modern sweep/stat/particle calculations reuse f3 after this call; give them the same final primary basis.
        nativeTotal.set(damage);
        return SourcePlayerCombat.primaryHit(player, target, source, damage,
                () -> original.call(target, source, damage));
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private void mite$deferSweepKnockback(LivingEntity target, double strength, double x, double z,
            Operation<Void> original,
            @Share("mite$sweepKnockback") LocalRef<Consumer<LivingEntity>> pendingKnockback) {
        // Native sweeping pushes before hurt, even when reach or hurt immunity rejects the hit.
        pendingKnockback.set(harmed -> {
            if (harmed == target) original.call(target, strength, x, z);
        });
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mite$sweepCannotBypassSourceReach(LivingEntity target, DamageSource source, float amount,
            Operation<Boolean> original,
            @Share("mite$sweepKnockback") LocalRef<Consumer<LivingEntity>> pendingKnockback) {
        Consumer<LivingEntity> knockback = pendingKnockback.get();
        // Consume before hurt: callbacks may recursively attack, and the next target needs its own operation.
        pendingKnockback.set(null);
        if (!SourcePlayerReach.canMelee((Player)(Object)this, target)) return false;
        float health = target.getHealth(), absorption = target.getAbsorptionAmount();
        boolean hit = original.call(target, source, amount);
        if (hit && knockback != null && (target.getHealth() < health || target.getAbsorptionAmount() < absorption))
            knockback.accept(target);
        return hit;
    }
}
