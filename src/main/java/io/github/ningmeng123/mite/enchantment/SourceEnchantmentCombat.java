package io.github.ningmeng123.mite.enchantment;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.MobEquipmentDrops;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.mixin.SourceMobDropChanceAccess;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import io.github.ningmeng123.mite.rules.EnchantmentCombatRules;
import io.github.ningmeng123.mite.survival.SourceStatusEffects;
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
        if(affected&&attacker instanceof Player player&&living instanceof Player defender
                &&(defender.getHealth()<before.health||defender.getAbsorptionAmount()<before.absorption))disarmPlayer(player,defender);
        if (!(attacker instanceof Player)) thorns(attacker, living);
        var stun = R196Enchantments.sources(attacker.getMainHandItem(), attacker.level(), "stun");
        if (EnchantmentSources.present(stun) && attacker.getRandom().nextDouble()
                < EnchantmentSources.probability(stun, value -> value / 10D))
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    (int)EnchantmentSources.number(stun, EnchantmentCombatRules::stunTicks),
                    (int)EnchantmentSources.number(stun, EnchantmentCombatRules::stunAmplifier)), attacker);
        var vampiric = R196Enchantments.sources(attacker.getMainHandItem(), attacker.level(), "vampiric");
        float basis = attacker instanceof Player ? damage : Math.max(0F, before.health - living.getHealth());
        int healed = 0;
        if (EnchantmentSources.present(vampiric) && basis > 0F && biological(living)) {
            double procRoll = attacker.getRandom().nextDouble(), healRoll = attacker.getRandom().nextDouble();
            if (vampiric.affix() == 0) {
                healed = EnchantmentCombatRules.vampiricTransfer(vampiric.normal(), basis, true, procRoll, healRoll);
            } else if (procRoll <= EnchantmentSources.probability(vampiric, value -> value / 10D)) {
                // Each present source grants its own transfer; one merged chance and one healing roll.
                healed = (int)EnchantmentSources.number(vampiric,
                        value -> EnchantmentCombatRules.vampiricTransfer(value, basis, true, 0D, healRoll));
            }
        }
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
        var sources = R196Enchantments.sources(attacker.getMainHandItem(), attacker.level(), "disarming");
        if (!EnchantmentSources.present(sources) || attacker.getRandom().nextFloat()
                >= EnchantmentSources.probability(sources, value -> value / 5F)) return;
        ItemStack item = mob.getMainHandItem();
        float chance = ((SourceMobDropChanceAccess)mob).mite$equipmentDropChance(EquipmentSlot.MAINHAND);
        if (MobEquipmentDrops.canDrop(mob, chance, item)) mob.spawnAtLocation(item.copy(), mob.getBbHeight() / 2F);
        mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        mob.getPersistentData().putInt(DISARMED, 40);
    }

    private static void disarmPlayer(Player attacker,Player target) {
        if(attacker==target||attacker.isCreative()||attacker.isSpectator()||target.isCreative()||target.isSpectator()
                ||!target.isAlive()||!attacker.canHarmPlayer(target)||!target.canHarmPlayer(attacker)||target.isBlocking()
                ||target.isUsingItem()&&target.getUseItem().getUseAnimation()==net.minecraft.world.item.UseAnim.BLOCK
                ||target.getMainHandItem().isEmpty()
                ||!io.github.ningmeng123.mite.api.MiteExtensions.policy().canDisarmPlayer(attacker,target,false))return;
        var sources=R196Enchantments.sources(attacker.getMainHandItem(),attacker.level(),"disarming");
        if(!EnchantmentSources.present(sources)||attacker.getRandom().nextFloat()>=EnchantmentSources.probability(sources,value->value/5F))return;
        ItemStack held=target.getMainHandItem();
        var dropped=target.drop(held.copy(),false,true);
        if(dropped!=null){
            dropped.setPickUpDelay(20);var look=target.getLookAngle();dropped.setDeltaMovement(dropped.getDeltaMovement().add(-look.z*.4,0,look.x*.4));
            target.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);target.getInventory().setChanged();
        }
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
        List<EnchantmentSources.Levels> sourcePairs = new ArrayList<>();
        boolean hasAffix = false;
        ItemStack wearing = ItemStack.EMPTY;
        EquipmentSlot wearingSlot = EquipmentSlot.CHEST;
        List<EquipmentSlot> slots = new ArrayList<>();
        if (!(defender instanceof Player)) slots.add(EquipmentSlot.MAINHAND);
        slots.addAll(List.of(ARMOR));
        for (EquipmentSlot slot : slots) {
            ItemStack stack = defender.getItemBySlot(slot);
            var pair = EnchantmentSources.levels(stack, enchantment);
            sourcePairs.add(pair);
            hasAffix |= pair.affix() > 0;
            int level = pair.totalLevel();
            maximum = Math.max(maximum, level);
            if (level > 0 && wearing.isEmpty()) { wearing = stack; wearingSlot = slot; }
        }
        if (maximum <= 0 || wearing.isEmpty()) return;
        float chance = hasAffix ? (float)sourcePairs.stream().mapToDouble(pair ->
                EnchantmentSources.probability(pair, value -> .15F * value)).max().orElse(0D) : .15F * maximum;
        boolean proc = random.nextFloat() < chance;
        if (proc) {
            if (!hasAffix) attacker.hurt(defender.damageSources().thorns(defender),
                    EnchantmentCombatRules.thornsDamage(maximum, maximum > 10 ? 0 : random.nextInt(4)));
            else {
                boolean needsRoll = sourcePairs.stream().anyMatch(pair -> pair.normal() > 0 && pair.normal() <= 10
                        || pair.affix() > 0 && pair.affix() <= 10);
                int damageRoll = needsRoll ? random.nextInt(4) : 0;
                float damage = (float)sourcePairs.stream().mapToDouble(pair -> EnchantmentSources.number(pair,
                        value -> EnchantmentCombatRules.thornsDamage(value, damageRoll))).max().orElse(0D);
                attacker.hurt(defender.damageSources().thorns(defender), damage);
            }
        }
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
        SourceStatusEffects.refreshAttack(entity, removed);
        if (SourceStatusEffects.managed(entity)) {
            AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) modifier(speed, SPEED, entity instanceof Player ? R196Enchantments.equipped(entity, "speed") * .05D : 0D);
            entity.getPersistentData().remove(SCALED_SLOW);
            SourceStatusEffects.refreshMovement(entity, removed);
            return;
        }
        if (!(entity instanceof Player) && !entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                && !entity.getPersistentData().getBoolean(SCALED_SLOW)) return;
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        int speedLevel = entity instanceof Player ? R196Enchantments.equipped(entity, "speed") : 0;
        modifier(speed, SPEED, speedLevel * .05D);
        float resistance = R196Enchantments.freeActionResistance(entity);
        MobEffectInstance slow = removed != null && removed.getEffect().equals(MobEffects.MOVEMENT_SLOWDOWN)
                ? null : entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        boolean previous = entity.getPersistentData().getBoolean(SCALED_SLOW);
        if (slow == null) {
            if (previous) speed.removeModifier(SLOWNESS);
            entity.getPersistentData().remove(SCALED_SLOW); return;
        }
        if (resistance <= 0 && !previous) return;
        if (resistance > 0) {
            int haste = removed != null && removed.getEffect().equals(MobEffects.MOVEMENT_SPEED)
                    ? -1 : amplifier(entity.getEffect(MobEffects.MOVEMENT_SPEED));
            double amount = entity instanceof Player
                    ? EnchantmentCombatRules.playerSlownessAmountWithResistance(slow.getAmplifier(), haste, resistance)
                    : -.2D * (slow.getAmplifier() + 1D) * (1D - resistance);
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
        float resistance = R196Enchantments.freeActionResistance(player);
        double ratio = EnchantmentCombatRules.webMovementRatioWithResistance(amplifier(player.getEffect(MobEffects.MOVEMENT_SLOWDOWN)),
                amplifier(player.getEffect(MobEffects.MOVEMENT_SPEED)), resistance);
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
