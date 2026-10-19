package io.github.ningmeng123.mite.compat.bettercombat;

import io.github.ningmeng123.mite.combat.SourcePlayerReach;
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

    public record Profile(double range, double angle, double upswing, String animation,
            double damageMultiplier, int cooldownTicks, boolean twoHanded, float movementSpeedMultiplier,
            String hitbox, double itemRange, double rangeMultiplier) {
        /** Match the real player reach guard, including height and external item range modifiers. */
        public double reach(Mob mob, Entity target) {
            return reach(mob, target, mob.getY());
        }

        public double reach(Mob mob, Entity target, double feetY) {
            double sourceReach = SourcePlayerCombatRules.meleeReach(false, feetY - target.getY(),
                    SourcePlayerReach.toolBonus(mob.getMainHandItem()), false, itemRange - 3D,
                    MiteConfig.weaponReachMultiplier(), MiteConfig.weaponReachReduction(), MiteConfig.heightReachPenaltyEnabled());
            return Math.max(0D, Math.min(range, sourceReach));
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
            double itemRange = heldAttribute(mob, stack, Attributes.ENTITY_INTERACTION_RANGE, 3D);
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
