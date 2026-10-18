package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.rules.EnchantmentCombatRules;
import io.github.ningmeng123.mite.rules.R196VanillaMobRules;
import io.github.ningmeng123.mite.rules.SourceStatusEffectRules;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Source effect semantics on actual source identities, leaving modern-only species and effect templates intact. @author NingMeng123 */
public final class SourceStatusEffects {
    private static final ResourceLocation STRENGTH = ResourceLocation.withDefaultNamespace("effect.strength");
    private static final ResourceLocation WEAKNESS = ResourceLocation.withDefaultNamespace("effect.weakness");
    private static final ResourceLocation SLOWNESS = ResourceLocation.withDefaultNamespace("effect.slowness");
    private static final ResourceLocation SPEED = ResourceLocation.withDefaultNamespace("effect.speed");
    private static final ResourceLocation PLAYER_MOVEMENT = ResourceLocation.fromNamespaceAndPath("mite", "source_potion_movement");
    // of.aM plus the explicit rv/sb/tg/tl overrides, including source horse/skeleton/zombie split identities.
    private static final Set<EntityType<?>> NON_BIOLOGICAL = Set.of(EntityType.SKELETON, EntityType.WITHER_SKELETON,
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN, EntityType.WITHER,
            EntityType.ZOMBIE_HORSE, EntityType.SKELETON_HORSE, EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM,
            EntityType.ENDERMAN, EntityType.MAGMA_CUBE);

    private SourceStatusEffects() {}

    public static boolean managed(LivingEntity entity) {
        return entity instanceof Player || entity instanceof R196Mob source && source.miteKind() != null
                || R196VanillaMobRules.managed(entity.getType());
    }

    public static boolean biologicallyAlive(LivingEntity entity) {
        if (entity instanceof R196Mob source && source.miteKind() != null) return source.biologicallyAlive();
        return !NON_BIOLOGICAL.contains(entity.getType());
    }

    /** Native spider poison immunity and other existing native restrictions still run after this extra source gate. */
    public static boolean rejects(LivingEntity entity, MobEffectInstance effect) {
        return managed(entity) && !biologicallyAlive(entity)
                && (effect.is(MobEffects.POISON) || effect.is(MobEffects.REGENERATION) || effect.is(MobEffects.WITHER));
    }

    /** Native NBT loading writes the effect map directly, so upgrades need the same eligibility gate before the first tick. */
    public static void migrateLoadedEffects(LivingEntity entity) {
        if (entity.level().isClientSide || !managed(entity)) return;
        for (var type : List.of(MobEffects.POISON, MobEffects.REGENERATION, MobEffects.WITHER)) {
            MobEffectInstance loaded = entity.getEffect(type);
            if (loaded != null && rejects(entity, loaded)) entity.removeEffect(type);
        }
    }

    public static boolean sourcePoison(LivingEntity entity, MobEffect effect) {
        return managed(entity) && effect == MobEffects.POISON.value();
    }

    /** Same NeoForge poison damage identity as the pinned native effect, retaining damage cancellation and attribution hooks. */
    public static boolean poisonTick(LivingEntity entity) {
        if (!entity.level().isClientSide) {
            var types = entity.damageSources().damageTypes;
            var type = types.getHolder(net.neoforged.neoforge.common.NeoForgeMod.POISON_DAMAGE)
                    .orElse(types.getHolderOrThrow(DamageTypes.MAGIC));
            entity.hurt(new DamageSource(type), 1F);
        }
        return true;
    }

    /** Operation1 includes operation0 weapon bonuses; retaining the native effect IDs also preserves clean removal. */
    public static void refreshAttack(LivingEntity entity, MobEffectInstance removed) {
        if (entity.level().isClientSide || !managed(entity)) return;
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return;
        attack(attack, STRENGTH, effect(entity, MobEffects.DAMAGE_BOOST, removed), false);
        attack(attack, WEAKNESS, effect(entity, MobEffects.WEAKNESS, removed), true);
    }

    private static void attack(AttributeInstance attribute, ResourceLocation id, MobEffectInstance effect, boolean weakness) {
        replace(attribute, id, effect == null ? 0D : SourceStatusEffectRules.attackModifier(effect.getAmplifier(), weakness),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    /** ni skips speed/slow attributes on players; of combines them before applying paralysis resistance. */
    public static void refreshMovement(LivingEntity entity, MobEffectInstance removed) {
        if (entity.level().isClientSide || !managed(entity)) return;
        AttributeInstance movement = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) return;
        MobEffectInstance slow = effect(entity, MobEffects.MOVEMENT_SLOWDOWN, removed);
        if (entity instanceof Player) {
            MobEffectInstance haste = effect(entity, MobEffects.MOVEMENT_SPEED, removed);
            movement.removeModifier(SPEED);
            movement.removeModifier(SLOWNESS);
            double factor = EnchantmentCombatRules.movementFactorWithResistance(amplifier(slow), amplifier(haste), false,
                    R196Enchantments.freeActionResistance(entity));
            replace(movement, PLAYER_MOVEMENT, factor - 1D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            replace(movement, SLOWNESS, slow == null ? 0D : -.2F * (slow.getAmplifier() + 1D)
                    * (1F - R196Enchantments.freeActionResistance(entity)), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    /** During bulk removal the native map still contains the in-flight instance. */
    private static MobEffectInstance effect(LivingEntity entity, Holder<MobEffect> type, MobEffectInstance removed) {
        return removed != null && removed.is(type) ? null : entity.getEffect(type);
    }

    private static int amplifier(MobEffectInstance effect) { return effect == null ? -1 : effect.getAmplifier(); }

    private static void replace(AttributeInstance attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeModifier previous = attribute.getModifier(id);
        if (amount == 0D ? previous == null : previous != null && previous.amount() == amount && previous.operation() == operation) return;
        attribute.removeModifier(id);
        if (amount != 0D) attribute.addPermanentModifier(new AttributeModifier(id, amount, operation));
    }
}
