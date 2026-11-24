package io.github.ningmeng123.mite.compat.bettercombat;

import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.combat.MobWeaponReach;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.SourcePlayerCombatRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.fml.ModList;

/** Optional Better Combat weapon data, isolated from the common mob controller. @author NingMeng123 */
public final class BetterCombatBridge {
    public static final String MOD_ID = "bettercombat";

    private BetterCombatBridge() {}

    public static boolean enabled() { return ModList.get().isLoaded(MOD_ID); }

    /** Simply Swords declares its own weapon length; its durability family must not replace it. */
    public static Double weaponBaseReach(ItemStack stack, double itemRange) {
        if (!enabled() || io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat.definition(stack) == null)
            return null;
        return Loaded.weaponBaseReach(stack, itemRange);
    }

    /** No optional types escape this boundary, including on servers without Better Combat. */
    public static Profile profile(Mob mob, int combo) {
        if (!enabled()) return null;
        ItemStack stack = mob.getMainHandItem();
        if (stack.isEmpty() || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof ShieldItem
                || stack.getItem() instanceof DiggerItem && !(stack.getItem() instanceof AxeItem)
                || stack.getItem() instanceof ShearsItem) return null;
        var tool = EquipmentDurability.toolProfile(stack);
        if (tool != null && switch (tool.family()) {
            case PICKAXE, SHOVEL, HOE, MATTOCK, SHEARS, KNIFE -> true;
            case SCYTHE -> io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat.definition(stack) == null;
            default -> false;
        }) return null;
        return Loaded.profile(mob, stack, combo);
    }

    public static double playerEquivalentRange(Mob mob, ItemStack weapon, double moveMultiplier) {
        if (!enabled() || weapon.isEmpty() || !Double.isFinite(moveMultiplier) || moveMultiplier <= 0D) return 0D;
        return Loaded.playerEquivalentRange(mob, weapon, moveMultiplier);
    }

    public record Profile(double range, double angle, double upswing, String animation,
            double damageMultiplier, int cooldownTicks, boolean twoHanded, float movementSpeedMultiplier,
            String hitbox, double itemRange, double rangeMultiplier) {
        /** The move's own reach may be shorter than the armed monster's attack range. */
        public double reach(Mob mob, Entity target) {
            return reach(mob, target, mob.getY());
        }

        public double reach(Mob mob, Entity target, double feetY) {
            double sourceReach = SourcePlayerCombatRules.meleeReach(false, feetY - target.getY(),
                    SourcePlayerReach.baseMeleeReach(mob.getMainHandItem(), itemRange) - 1.5D, false, 0D,
                    MiteConfig.weaponReachMultiplier(), MiteConfig.weaponReachReduction(), MiteConfig.heightReachPenaltyEnabled());
            double previousLimit = Math.max(0D, Math.min(range, sourceReach));
            return MobWeaponReach.applies(mob)
                    ? Math.min(MobWeaponReach.sourceMeleeRange(mob), Math.min(previousLimit, MobWeaponReach.sourceLimit(mob, target, feetY)))
                    : previousLimit;
        }

        public int windupTicks() { return Math.max(1, (int)Math.round(cooldownTicks * upswing)); }
    }

    private static double heldAttribute(Mob mob, ItemStack stack, Holder<Attribute> attribute, double base) {
        if (mob.getAttribute(attribute) != null) return mob.getAttributeValue(attribute);
        List<AttributeModifier> modifiers = new ArrayList<>();
        stack.forEachModifier(EquipmentSlot.MAINHAND, (candidate, modifier) -> {
            if (candidate.is(attribute)) modifiers.add(modifier);
        });
        double added = base;
        for (AttributeModifier modifier : modifiers)
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) added += modifier.amount();
        double result = added;
        for (AttributeModifier modifier : modifiers)
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) result += added * modifier.amount();
        for (AttributeModifier modifier : modifiers)
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) result *= 1D + modifier.amount();
        return attribute.value().sanitizeValue(result);
    }

    /** Loaded only after the mod-presence guard; all Better Combat references stay here. */
    private static final class Loaded {
        private static double playerEquivalentRange(Mob mob, ItemStack weapon, double moveMultiplier) {
            var attributes = net.bettercombat.logic.WeaponRegistry.getAttributes(weapon);
            double itemRange = MobWeaponReach.itemRange(mob);
            double range = net.bettercombat.logic.EntityAttributeHelper.itemHasRangeAttribute(weapon)
                    ? itemRange : net.bettercombat.logic.PlayerAttackHelper.combineAttackRange(attributes, itemRange);
            range *= moveMultiplier;
            range *= net.bettercombat.BetterCombatMod.getConfig().getAttackRangeMultiplierForScale(mob.getScale());
            if (!Double.isFinite(range) || range <= 0D) return 0D;
            return SourcePlayerCombatRules.configuredWeaponReach(range, MiteConfig.weaponReachMultiplier(), MiteConfig.weaponReachReduction());
        }

        private static Double weaponBaseReach(ItemStack stack, double itemRange) {
            var attributes = net.bettercombat.logic.WeaponRegistry.getAttributes(stack);
            if (attributes == null) return null;
            // Explicit range components override weapon metadata, including legacy absolute attack_range.
            double range = net.bettercombat.logic.EntityAttributeHelper.itemHasRangeAttribute(stack)
                    ? itemRange : net.bettercombat.logic.PlayerAttackHelper.combineAttackRange(attributes, itemRange);
            return Double.isFinite(range) && range > 0D ? range : 0D;
        }

        private static Profile profile(Mob mob, ItemStack stack, int combo) {
            var attributes = net.bettercombat.logic.WeaponRegistry.getAttributes(stack);
            if (attributes == null || attributes.attacks() == null) return null;
            var offhand = net.bettercombat.logic.WeaponRegistry.getAttributes(mob.getOffhandItem());
            boolean dual = net.bettercombat.logic.PlayerAttackHelper.isDualWielding(attributes, offhand);
            List<net.bettercombat.api.WeaponAttributes.Attack> attacks = new ArrayList<>();
            for (var attack : attributes.attacks()) {
                if (attack == null || !conditions(attack.conditions(), mob, attributes, offhand, dual)) continue;
                attacks.add(attack);
            }
            if (attacks.isEmpty()) return null;
            var attack = attacks.get(Math.max(0, combo) % attacks.size());
            double speed = heldAttribute(mob, stack, Attributes.ATTACK_SPEED, 4D);
            double itemRange = MobWeaponReach.applies(mob) ? MobWeaponReach.itemRange(mob)
                    : heldAttribute(mob, stack, Attributes.ENTITY_INTERACTION_RANGE, 3D);
            if (!Double.isFinite(speed) || speed <= 0D || !Double.isFinite(itemRange)) return null;
            var config = net.bettercombat.BetterCombatMod.getConfig();
            double duration = Math.max(20D / speed, config.attack_interval_cap);
            if (!Double.isFinite(duration) || duration > Integer.MAX_VALUE) return null;
            int cooldownTicks = Math.max(1, (int)Math.round(duration));
            double range = net.bettercombat.logic.EntityAttributeHelper.itemHasRangeAttribute(stack)
                    ? itemRange : net.bettercombat.logic.PlayerAttackHelper.combineAttackRange(attributes, itemRange);
            range *= attack.rangeMultiplier();
            if (!Double.isFinite(range) || range <= 0D || !Double.isFinite(attack.damageMultiplier())
                    || attack.damageMultiplier() < 0D) return null;
            double upswing = Mth.clamp(attack.upswing(), 0D, 1D) * config.getUpswingMultiplier();
            if (!Double.isFinite(upswing)) return null;
            return new Profile(range, attack.angle(), upswing, attack.animation(), attack.damageMultiplier(),
                    cooldownTicks, attributes.isTwoHanded(), (float)(attack.movementSpeedMultiplier() * config.movement_speed_while_attacking),
                    attack.hitbox() == null ? "FORWARD_BOX" : attack.hitbox().name(), itemRange, attack.rangeMultiplier());
        }

        private static boolean conditions(net.bettercombat.api.WeaponAttributes.Condition[] conditions,
                Mob mob, net.bettercombat.api.WeaponAttributes main,
                net.bettercombat.api.WeaponAttributes offhand, boolean dual) {
            if (conditions == null) return true;
            for (var condition : conditions) {
                if (condition == null) continue;
                boolean matches = switch (condition) {
                    case NOT_DUAL_WIELDING -> !dual;
                    case DUAL_WIELDING_ANY -> dual;
                    case DUAL_WIELDING_SAME -> dual && mob.getMainHandItem().is(mob.getOffhandItem().getItem());
                    case DUAL_WIELDING_SAME_CATEGORY -> dual && main.category() != null
                            && !main.category().isEmpty() && main.category().equals(offhand.category());
                    case NO_OFFHAND_ITEM -> mob.getOffhandItem().isEmpty();
                    case OFF_HAND_SHIELD -> mob.getOffhandItem().getItem() instanceof ShieldItem;
                    case MAIN_HAND_ONLY -> true;
                    case OFF_HAND_ONLY -> false;
                    case MOUNTED -> mob.isPassenger();
                    case NOT_MOUNTED -> !mob.isPassenger();
                };
                if (!matches) return false;
            }
            return true;
        }
    }
}
