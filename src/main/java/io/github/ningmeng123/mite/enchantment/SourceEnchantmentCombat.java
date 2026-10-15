package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.rules.EnchantmentCombatRules;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Reachable R196 melee, armor speed and paralysis effects; ordinary loot already owns Butchering. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class SourceEnchantmentCombat {
    private static final String DISARMED = "mite_enchantment_disarmed_ticks";
    private static final String SCALED_SLOW = "mite_enchantment_free_action_scaled";
    private static final ResourceLocation SPEED = ResourceLocation.fromNamespaceAndPath("mite", "enchantment_speed");
    private static final ResourceLocation SLOWNESS = ResourceLocation.withDefaultNamespace("effect.slowness");
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private SourceEnchantmentCombat() {}

    /** Called at the actual native hurt invocation, not at the earlier cancellable attack event. */
    public static boolean melee(LivingEntity attacker, Entity target, DamageSource source, float damage, BooleanSupplier nativeDamage) {
        if (attacker.level().isClientSide || !(target instanceof LivingEntity living)) return nativeDamage.getAsBoolean();
        if (attacker instanceof Player && damage > 0F) disarm(attacker, living);
        HarmSnapshot before = new HarmSnapshot(living);
        boolean affected = nativeDamage.getAsBoolean();
        if (!before.negativelyAffected(living)) return affected;
        if (!(attacker instanceof Player)) thorns(attacker, living);
        int stun = R196Enchantments.level(attacker.getMainHandItem(), attacker.level(), "stun");
        if (stun > 0 && EnchantmentCombatRules.meleeProc(stun, attacker.getRandom().nextDouble()))
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    EnchantmentCombatRules.stunTicks(stun), EnchantmentCombatRules.stunAmplifier(stun)), attacker);
        int vampiric = R196Enchantments.level(attacker.getMainHandItem(), attacker.level(), "vampiric");
        float basis = attacker instanceof Player ? damage : Math.max(0F, before.health - living.getHealth());
        int healed = vampiric > 0 && basis > 0F && biological(living)
                ? EnchantmentCombatRules.vampiricTransfer(vampiric, basis, true,
                        attacker.getRandom().nextDouble(), attacker.getRandom().nextDouble()) : 0;
        if (healed > 0) {
            float old = attacker.getHealth();
            attacker.heal(healed);
            if (attacker.getHealth() > old && attacker.level() instanceof ServerLevel level)
                level.sendParticles(ParticleTypes.HEART, attacker.getX(), attacker.getY() + attacker.getBbHeight() * .6D,
                        attacker.getZ(), 2, .2D, .2D, .2D, .01D);
        }
        if (attacker instanceof Player) thorns(attacker, living);
        return affected;
    }

    public static boolean biological(LivingEntity target) {
        if (target instanceof R196Mob source) return source.biologicallyAlive();
        return !target.getType().is(EntityTypeTags.UNDEAD) && !(target instanceof AbstractGolem)
                && !(target instanceof EnderMan) && !(target instanceof MagmaCube);
    }

    private static void disarm(LivingEntity attacker, LivingEntity target) {
        if (!(target instanceof Mob mob) || mob.getMainHandItem().isEmpty()) return;
        int level = R196Enchantments.level(attacker.getMainHandItem(), attacker.level(), "disarming");
        if (level <= 0 || attacker.getRandom().nextFloat() >= level / 5F) return;
        ItemStack item = mob.getMainHandItem();
        mob.spawnAtLocation(item.copy(), mob.getBbHeight() / 2F);
        mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        mob.getPersistentData().putInt(DISARMED, 40);
    }

    public static boolean disarmed(Mob mob) { return mob.getPersistentData().getInt(DISARMED) > 0; }

    /** abh uses highest level but aaw:175 returns the FIRST enchanted item, not a random item. */
    public static void thorns(Entity attacker, LivingEntity defender) {
        if (attacker != null) thorns(attacker, defender, attacker.getRandom());
    }
    /** Arrow collisions supply the projectile RNG, as uh does, rather than its owner's RNG. */
    public static void thorns(Entity attacker, LivingEntity defender, RandomSource random) {
        if (attacker == null || defender.level().isClientSide) return;
        var enchantment = defender.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.THORNS);
        int maximum = 0;
        ItemStack wearing = ItemStack.EMPTY;
        EquipmentSlot wearingSlot = EquipmentSlot.CHEST;
        List<EquipmentSlot> slots = new ArrayList<>();
        if (!(defender instanceof Player)) slots.add(EquipmentSlot.MAINHAND);
        slots.addAll(List.of(ARMOR));
        for (EquipmentSlot slot : slots) {
            ItemStack stack = defender.getItemBySlot(slot);
            int level = stack.getEnchantmentLevel(enchantment);
            maximum = Math.max(maximum, level);
            if (level > 0 && wearing.isEmpty()) { wearing = stack; wearingSlot = slot; }
        }
        if (maximum <= 0 || wearing.isEmpty()) return;
        boolean proc = EnchantmentCombatRules.thornsProc(maximum, random.nextFloat());
        if (proc) attacker.hurt(defender.damageSources().thorns(defender),
                EnchantmentCombatRules.thornsDamage(maximum, maximum > 10 ? 0 : random.nextInt(4)));
        wearing.hurtAndBreak(proc ? 3 : 1, defender, wearingSlot);
    }

    @SubscribeEvent public static void equipmentChanged(LivingEquipmentChangeEvent event) { refreshMovement(event.getEntity()); }
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living.level().isClientSide) return;
        if (living instanceof Mob mob && disarmed(mob)) {
            int ticks = mob.getPersistentData().getInt(DISARMED) - 1;
            if (ticks > 0) mob.getPersistentData().putInt(DISARMED, ticks); else mob.getPersistentData().remove(DISARMED);
        }
        refreshMovement(living);
    }

    public static void refreshMovement(LivingEntity entity) { refreshMovement(entity, null); }
    /** removeAllEffects calls onEffectRemoved before deleting its map entry; exclude that in-flight entry. */
    public static void afterEffectRemoved(LivingEntity entity, MobEffectInstance removed) { refreshMovement(entity, removed); }
    private static void refreshMovement(LivingEntity entity, MobEffectInstance removed) {
        if (entity.level().isClientSide) return;
        if (!(entity instanceof Player) && !entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                && !entity.getPersistentData().getBoolean(SCALED_SLOW)) return;
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        int speedLevel = entity instanceof Player ? R196Enchantments.equipped(entity, "speed") : 0;
        modifier(speed, SPEED, speedLevel * .05D);
        int free = R196Enchantments.equipped(entity, "free_action");
        MobEffectInstance slow = removed != null && removed.getEffect().equals(MobEffects.MOVEMENT_SLOWDOWN)
                ? null : entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        boolean previous = entity.getPersistentData().getBoolean(SCALED_SLOW);
        if (slow == null) {
            if (previous) speed.removeModifier(SLOWNESS);
            entity.getPersistentData().remove(SCALED_SLOW); return;
        }
        if (free <= 0 && !previous) return;
        if (free > 0) {
            int haste = removed != null && removed.getEffect().equals(MobEffects.MOVEMENT_SPEED)
                    ? -1 : amplifier(entity.getEffect(MobEffects.MOVEMENT_SPEED));
            double amount = entity instanceof Player
                    ? EnchantmentCombatRules.playerSlownessAmount(slow.getAmplifier(), haste, free)
                    : -.2D * (slow.getAmplifier() + 1D) * (1D - EnchantmentCombatRules.freeActionResistance(free));
            replaceSlow(speed, amount);
            entity.getPersistentData().putBoolean(SCALED_SLOW, true);
        } else {
            // Restore the native effect template rather than hardcoding the modern default.
            MobEffects.MOVEMENT_SLOWDOWN.value().createModifiers(slow.getAmplifier(), (attribute, value) -> {
                if (attribute.equals(Attributes.MOVEMENT_SPEED)) replaceSlow(speed, value.amount());
            });
            entity.getPersistentData().remove(SCALED_SLOW);
        }
    }
    private static void replaceSlow(AttributeInstance speed, double amount) {
        AttributeModifier old = speed.getModifier(SLOWNESS);
        if (old != null && old.amount() == amount) return;
        speed.removeModifier(SLOWNESS);
        speed.addPermanentModifier(new AttributeModifier(SLOWNESS, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static void modifier(AttributeInstance attribute, ResourceLocation id, double amount) {
        AttributeModifier old = attribute.getModifier(id);
        if (old != null && old.amount() == amount) return;
        if (old != null) attribute.removeModifier(id);
        if (amount != 0D) attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static int amplifier(MobEffectInstance effect) { return effect == null ? -1 : effect.getAmplifier(); }
    /** Keep Entity's original web friction, adding of's separate movement-factor term on both logical sides. */
    public static Vec3 webMotion(Entity entity, Vec3 movement) {
        if (!(entity instanceof Player player)) return movement;
        int free = R196Enchantments.equipped(player, "free_action");
        if (free <= 0) return movement;
        double ratio = EnchantmentCombatRules.webMovementRatio(amplifier(player.getEffect(MobEffects.MOVEMENT_SLOWDOWN)),
                amplifier(player.getEffect(MobEffects.MOVEMENT_SPEED)), free);
        return movement.multiply(ratio, 1D, ratio);
    }

    /** R196 negative effects include absorbed hits, armor/held wear and knockback, not only health loss. */
    public static final class HarmSnapshot {
        private final float health, absorption;
        private final Vec3 motion;
        private final List<ItemStack> equipment = new ArrayList<>();
        public HarmSnapshot(LivingEntity target) {
            health = target.getHealth(); absorption = target.getAbsorptionAmount(); motion = target.getDeltaMovement();
            equipment.add(target.getMainHandItem().copy());
            for (EquipmentSlot slot : ARMOR) equipment.add(target.getItemBySlot(slot).copy());
        }
        public boolean negativelyAffected(LivingEntity target) {
            if (target.getHealth() < health || target.getAbsorptionAmount() < absorption || !target.isAlive()
                    || !target.getDeltaMovement().equals(motion)) return true;
            if (worn(equipment.getFirst(), target.getMainHandItem())) return true;
            for (int index = 0; index < ARMOR.length; index++)
                if (worn(equipment.get(index + 1), target.getItemBySlot(ARMOR[index]))) return true;
            return false;
        }
        private static boolean worn(ItemStack before, ItemStack after) {
            return !before.isEmpty() && (after.isEmpty() || after.getCount() < before.getCount()
                    || before.is(after.getItem()) && after.getDamageValue() > before.getDamageValue());
        }
    }
}
