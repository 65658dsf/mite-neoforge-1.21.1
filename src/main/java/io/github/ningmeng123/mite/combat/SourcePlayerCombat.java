package io.github.ningmeng123.mite.combat;

import io.github.ningmeng123.mite.compat.bettercombat.BetterCombatPlayerReach;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.MobCorrosion;
import io.github.ningmeng123.mite.entity.R196Cube;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearModifierEffects;
import io.github.ningmeng123.mite.rules.SourcePlayerCombatRules;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Primary player melee pipeline, including source knockback-only blows and hand injury. @author NingMeng123 */
public final class SourcePlayerCombat {
    public static final ResourceKey<DamageType> HAND_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("mite", "hand_damage"));
    private SourcePlayerCombat() {}

    public static boolean handProtected(ItemStack stack) {
        // xj (source tools), stick and bone. Bows, blocks and arbitrary held items are not gloves.
        // Modern tiered tools are explicitly adapted through the same registered durability profiles.
        return !stack.isEmpty() && (EquipmentDurability.toolProfile(stack) != null || stack.is(Items.STICK) || stack.is(Items.BONE)
                || stack.is(Items.TRIDENT) || stack.is(Items.MACE));
    }
    public static boolean weak(Player player) {
        return SourcePlayerCombatRules.weakStrike(handProtected(player.getMainHandItem()), player.getHealth(),
                FoodConsumption.hasEnergy(player), player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
    }
    public static boolean suspended(Player player) {
        // nn.getBlockRestingOn ignores its allowance and immediately returns null when not onGround.
        return !player.isPassenger() && !player.onGround() && (player.isInWater() || player.isInLava());
    }

    public static float meleeDamage(Player player, float sourceDamage) {
        float damage = GearModifierEffects.meleeDamage(player, sourceDamage);
        return BetterCombatPlayerReach.isAttackInProgress(player)
                ? (float)(damage * MiteConfig.betterCombatDamageMultiplier()) : damage;
    }

    /** uf:1107-1109: native damage, then the target's melee callback, then enchantment procs. */
    public static boolean primaryHit(Player player, Entity target, DamageSource source, float amount, BooleanSupplier nativeHit) {
        if (player.level().isClientSide) return nativeHit.getAsBoolean();
        if (!(amount > 0F)) return false;
        boolean weak = weak(player);
        return SourceEnchantmentCombat.melee(player, target, source, amount, () -> {
            boolean hit;
            if (weak && target instanceof LivingEntity living) {
                // Source ordinary knockback is .4, reduced to .6 for a weak strike and .8 for NPCs.
                // Keep NeoForge's resistance/cancellable knockback event and modern movement synchronization.
                double strength = .4D * .6D * (living instanceof Player ? 1D : .8D);
                if (living instanceof R196Mob mob && mob.miteKind() == MobKind.EARTH_ELEMENTAL) strength *= .4D;
                if (!living.isInvulnerableTo(source) && living.isAlive()
                        && (!(living instanceof Player victim) || player.canHarmPlayer(victim))) {
                    var beforeMotion = living.getDeltaMovement();
                    living.knockback(strength, player.getX() - living.getX(), player.getZ() - living.getZ());
                    // Native Player.attack sends motion to a player victim only in its hurt=true branch.
                    // Weak blows skip that health branch; this invokes the native tracker broadcastAndSend path instead.
                    if (!beforeMotion.equals(living.getDeltaMovement())) living.hurtMarked = true;
                }
                hit = false; // No native health/armor/invulnerability/weapon-wear or sweep transaction.
            } else hit = nativeHit.getAsBoolean();
            handInjury(player, target, weak);
            // EntityGelatinousCube:377 follows og's hand injury even after an immune or weak hit.
            if (target instanceof R196Cube cube) {
                boolean corroded = MobCorrosion.equipment(player.getMainHandItem(), cube.acidic(),
                        R196Cube.damageMultiplier(cube.miteKind()), player, EquipmentSlot.MAINHAND);
                // Acidic contact still hisses when creative mode or resistant/empty hands prevent wear.
                // Successful corrosion already emitted the shared effect, so never play it twice.
                if (cube.acidic() && !corroded) MobCorrosion.feedback(player);
            }
            else if (target instanceof Slime && !(target instanceof MagmaCube) && !player.getMainHandItem().isEmpty())
                MobCorrosion.equipment(player.getMainHandItem(), false, 1F, player, EquipmentSlot.MAINHAND);
            return hit;
        });
    }

    public static void handInjury(Player attacker, Entity target, boolean weak) {
        if (!(target instanceof Mob mob) || handProtected(attacker.getMainHandItem())) return;
        boolean fire = mob instanceof Blaze || mob instanceof R196Mob source && source.miteKind() == MobKind.FIRE_ELEMENTAL;
        float chance = SourcePlayerCombatRules.handInjuryChance(mob instanceof Slime, fire, mob.getTarget() == attacker, weak);
        // Source consumes one target RNG draw even for zero chance.
        if (mob.getRandom().nextFloat() >= chance) return;
        float amount = mob instanceof R196Cube cube ? R196Cube.damageMultiplier(cube.miteKind()) : mob instanceof MagmaCube ? 2F : 1F;
        attacker.hurt(new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(HAND_DAMAGE), mob), amount);
    }
    public static boolean isHandDamage(DamageSource source) { return source.is(HAND_DAMAGE); }
}
