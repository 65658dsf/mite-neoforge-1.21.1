package io.github.ningmeng123.mite.compat.bettercombat;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.SourcePlayerCombatRules;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** One range policy for Better Combat selection and its actual server attack. @author NingMeng123 */
public final class BetterCombatPlayerReach {
    private record Attack(Player player, ItemStack weapon, double moveMultiplier) {}
    private static final ThreadLocal<Attack> ATTACK = new ThreadLocal<>();

    private BetterCombatPlayerReach() {}

    /** The captured move predates Better Combat's temporary main/offhand swap. */
    public static void duringAttack(Player player, ItemStack weapon, double moveMultiplier, Runnable action) {
        Attack previous = ATTACK.get();
        ATTACK.set(new Attack(player, weapon, moveMultiplier));
        try { action.run(); }
        finally {
            if (previous == null) ATTACK.remove();
            else ATTACK.set(previous);
        }
    }

    public static boolean isAttackInProgress(Player player) {
        Attack attack = ATTACK.get();
        return attack != null && attack.player() == player;
    }

    /** Null preserves the source rules for ordinary packets, fake players and absent optional mods. */
    public static Double reach(Player player) {
        if (!BetterCombatBridge.enabled() || BetterCombatPlayerMode.isVanilla(player)) return null;
        Attack attack = ATTACK.get();
        if (attack != null && attack.player() == player)
            return Loaded.range(player, attack.weapon(), attack.moveMultiplier(), false);
        return player.level().isClientSide ? ClientLoaded.reach(player) : null;
    }

    /** Use the selected hand for MITE's tool bonus, before the client swaps equipment. */
    public static ItemStack attackingWeapon(Player player) {
        if (BetterCombatBridge.enabled() && !BetterCombatPlayerMode.isVanilla(player)) {
            Attack attack = ATTACK.get();
            if (attack != null && attack.player() == player) return attack.weapon();
            if (player.level().isClientSide) {
                ItemStack weapon = ClientLoaded.weapon(player);
                if (weapon != null) return weapon;
            }
        }
        return player.getMainHandItem();
    }

    /** Read both value and obsolete modifier from the same actual attacking hand. */
    public static double rangeAttribute(Player player, ResourceLocation ignoredModifier) {
        if (BetterCombatBridge.enabled() && player.level().isClientSide && !BetterCombatPlayerMode.isVanilla(player))
            return ClientLoaded.rangeAttribute(player, ignoredModifier);
        return currentRangeAttribute(player, ignoredModifier);
    }

    private static double currentRangeAttribute(Player player, ResourceLocation ignoredModifier) {
        var attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attribute == null) return 3D;
        var modifier = attribute.getModifier(ignoredModifier);
        return attribute.getValue() - (modifier == null ? 0D : modifier.amount());
    }

    public static Boolean canMelee(Player player, Entity target) {
        Double range = reach(player);
        if (range == null) return null;
        if (range <= 0D || target.level() != player.level()) return false;
        double sourceRange = SourcePlayerReach.sourceMeleeReach(player, target);
        Vec3 eye = player.getEyePosition();
        Vec3 sourcePoint = SourcePlayerReach.closestPoint(target.getBoundingBox().inflate(target.getPickRadius()), eye);
        if (sourceRange <= 0D || eye.distanceToSqr(sourcePoint) > sourceRange * sourceRange) return false;
        Vec3 origin = origin(player);
        Vec3 nearest = SourcePlayerReach.closestPoint(target.getBoundingBox(), origin);
        return origin.distanceToSqr(nearest) <= range * range;
    }

    /** Matches TargetFinder.getInitialTracingPoint, including crouching and age scaling. */
    public static Vec3 origin(Player player) {
        return player.getEyePosition().add(0D, -player.getBbHeight() * .15D * player.getAgeScale(), 0D);
    }

    /** Apply range settings after move/scale extensions, then add the player's capped level bonus. */
    public static double configure(Player player, double range) {
        if (!Double.isFinite(range) || range <= 0D) return 0D;
        double configured = player.getAbilities().instabuild ? range : SourcePlayerCombatRules.configuredWeaponReach(range,
                MiteConfig.weaponReachMultiplier(), MiteConfig.weaponReachReduction());
        return configured + ProgressionRules.meleeReachBonusForLevel(player.experienceLevel);
    }

    public static double searchRange(Player player, double rawRange) {
        if (!Double.isFinite(rawRange) || rawRange <= 0D) return 0D;
        return Math.max(rawRange, configure(player, ClientLoaded.extensions(player, rawRange)));
    }

    /** Optional types stay behind the mod-presence boundary. */
    private static final class Loaded {
        private static double range(Player player, ItemStack weapon, double moveMultiplier, boolean client) {
            double range = net.bettercombat.logic.PlayerAttackHelper.getRangeForItem(player, weapon) * moveMultiplier;
            range = client ? ClientLoaded.extensions(player, range) : range * net.bettercombat.BetterCombatMod.getConfig()
                    .getAttackRangeMultiplierForScale(player.getScale());
            return configure(player, range);
        }
    }

    private static final class ClientLoaded {
        private static double rangeAttribute(Player player, ResourceLocation ignoredModifier) {
            int combo = ((net.bettercombat.logic.PlayerAttackProperties)player).getComboCount();
            if (net.bettercombat.client.BetterCombatClientMod.ENABLED && combo > 0
                    && net.bettercombat.logic.PlayerAttackHelper.shouldAttackWithOffHand(player, combo)) {
                double[] range = new double[1];
                net.bettercombat.logic.PlayerAttackHelper.swapHandAttributes(player,
                        () -> range[0] = currentRangeAttribute(player, ignoredModifier));
                return range[0];
            }
            return currentRangeAttribute(player, ignoredModifier);
        }

        private static ItemStack weapon(Player player) {
            if (!net.bettercombat.client.BetterCombatClientMod.ENABLED) return null;
            int combo = ((net.bettercombat.logic.PlayerAttackProperties)player).getComboCount();
            var hand = net.bettercombat.logic.PlayerAttackHelper.getCurrentAttack(player, Math.max(0, combo));
            return hand == null ? null : hand.itemStack();
        }

        private static Double reach(Player player) {
            if (!net.bettercombat.client.BetterCombatClientMod.ENABLED) return null;
            int combo = ((net.bettercombat.logic.PlayerAttackProperties)player).getComboCount();
            var hand = net.bettercombat.logic.PlayerAttackHelper.getCurrentAttack(player, Math.max(0, combo));
            if (hand == null) return null;
            return Loaded.range(player, hand.itemStack(), hand.attack().rangeMultiplier(), true);
        }

        private static double extensions(Player player, double range) {
            var context = new net.bettercombat.api.client.AttackRangeExtensions.Context(player, range);
            var modifiers = net.bettercombat.api.client.AttackRangeExtensions.sources().stream()
                    .map(source -> source.apply(context))
                    .sorted(java.util.Comparator.comparingInt(
                            net.bettercombat.api.client.AttackRangeExtensions.Modifier::operationOrder)).toList();
            for (var modifier : modifiers) range = switch (modifier.operation()) {
                case ADD -> range + modifier.value();
                case MULTIPLY -> range * modifier.value();
            };
            return range;
        }
    }
}
