package io.github.ningmeng123.mite.combat;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.SourcePlayerCombatRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Armed monsters retain their old limits and cannot out-reach equivalent survival weapons. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class MobWeaponReach {
    public static final double MARGIN = .2D;
    private static final ResourceLocation LEGACY_TOOL_REACH = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "tool_reach");

    private MobWeaponReach() {}

    public static boolean applies(Mob mob) {
        if (!(mob instanceof Enemy) && mob.getType().getCategory() != MobCategory.MONSTER) return false;
        var weapon = mob.getMainHandItem();
        if (weapon.isEmpty() || weapon.getItem() instanceof ProjectileWeaponItem
                || weapon.getItem() instanceof ShieldItem) return false;
        return MiteTools.definition(weapon) != null || EquipmentDurability.toolProfile(weapon) != null
                || SimplySwordsCompat.definition(weapon) != null
                || weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof AxeItem
                || weapon.getItem() instanceof TridentItem || weapon.getItem() instanceof MaceItem
                || weapon.is(Items.STICK) || weapon.is(Items.BONE);
    }

    public static double itemRange(Mob mob) {
        var attribute = mob.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attribute != null) {
            var legacy = attribute.getModifier(LEGACY_TOOL_REACH);
            return attribute.getValue() - (legacy == null ? 0D : legacy.amount());
        }
        List<AttributeModifier> modifiers = new ArrayList<>();
        mob.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (candidate, modifier) -> {
            if (candidate.is(Attributes.ENTITY_INTERACTION_RANGE) && !modifier.id().equals(LEGACY_TOOL_REACH))
                modifiers.add(modifier);
        });
        double added = 3D;
        for (AttributeModifier modifier : modifiers)
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) added += modifier.amount();
        double result = added;
        for (AttributeModifier modifier : modifiers)
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) result += added * modifier.amount();
        for (AttributeModifier modifier : modifiers)
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) result *= 1D + modifier.amount();
        return Attributes.ENTITY_INTERACTION_RANGE.value().sanitizeValue(result);
    }

    public static double sourceLimit(Mob mob, Entity target, double feetY) {
        double base = SourcePlayerReach.baseMeleeReach(mob.getMainHandItem(), itemRange(mob));
        if (!Double.isFinite(base) || base <= 0D && SimplySwordsCompat.definition(mob.getMainHandItem()) != null) return 0D;
        double playerReach = SourcePlayerCombatRules.meleeReach(false, feetY - target.getY(), base - 1.5D,
                target instanceof AbstractHurtingProjectile, 0D, MiteConfig.weaponReachMultiplier(),
                MiteConfig.weaponReachReduction(), MiteConfig.heightReachPenaltyEnabled());
        return Double.isFinite(playerReach) ? Math.max(0D, playerReach - MARGIN) : 0D;
    }

    public static boolean canReachFrom(Mob mob, Entity target, Vec3 feet) {
        if (!applies(mob)) return true;
        if (target.level() != mob.level() || !Double.isFinite(feet.x)
                || !Double.isFinite(feet.y) || !Double.isFinite(feet.z)) return false;
        double limit = sourceLimit(mob, target, feet.y);
        Vec3 eye = feet.add(0D, mob.getEyeHeight(), 0D);
        Vec3 point = SourcePlayerReach.closestPoint(target.getBoundingBox().inflate(target.getPickRadius()), eye);
        return limit > 0D && eye.distanceToSqr(point) <= limit * limit;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingIncomingDamageEvent event) {
        var source = event.getSource();
        if (event.isCanceled() || !(event.getEntity() instanceof Player) || !(source.getDirectEntity() instanceof Mob mob)
                || source.getEntity() != mob || mob.level().isClientSide
                || !source.is(DamageTypes.MOB_ATTACK) && !source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) return;
        if (!canReachFrom(mob, event.getEntity(), mob.position())) event.setCanceled(true);
    }
}
