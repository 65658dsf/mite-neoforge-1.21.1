package io.github.ningmeng123.mite.compat.bettercombat;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.MobShields;
import io.github.ningmeng123.mite.network.MobAttackPayload;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-owned weapon windup, impact and recovery for armed hostile mobs. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class BetterMobCombat {
    private static final Map<Mob, State> STATES = new WeakHashMap<>();
    private static final ResourceLocation DAMAGE = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "mob_combo_damage");
    private static final ResourceLocation MOVEMENT = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "mob_attack_movement");
    private static final ThreadLocal<Mob> IMPACT = new ThreadLocal<>();

    private static final class State {
        int combo;
        int readyAt;
        int impactAt;
        WeakReference<LivingEntity> target;
        ItemStack weapon = ItemStack.EMPTY;
        BetterCombatBridge.Profile attack;
        boolean trackedTarget;
    }

    private BetterMobCombat() {}

    public static boolean supported(Mob mob) {
        return BetterCombatBridge.enabled() && mob instanceof Enemy && !mob.getMainHandItem().isEmpty()
                && !(mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem)
                && BetterCombatBridge.profile(mob, 0) != null;
    }

    public static boolean executing(Mob mob) { return IMPACT.get() == mob; }

    public static boolean attacking(Mob mob) {
        State state = STATES.get(mob);
        return state != null && mob.tickCount < state.readyAt;
    }

    private static boolean valid(Mob mob, LivingEntity target) {
        return target != null && target != mob && target.isAlive() && !target.isRemoved()
                && target.level() == mob.level() && !mob.isAlliedTo(target)
                && (!(target instanceof Player player) || !player.isCreative() && !player.isSpectator());
    }

    public static boolean canStrike(Mob mob, LivingEntity target) {
        return canStrikeFrom(mob, target, mob.position());
    }

    /** Shared with the source path planner, so it plans toward the actual weapon reach. */
    public static boolean canStrikeFrom(Mob mob, LivingEntity target, Vec3 feet) {
        if (!valid(mob, target)) return false;
        State state = STATES.get(mob);
        var attack = state != null && state.attack != null ? state.attack
                : BetterCombatBridge.profile(mob, state == null ? 0 : state.combo);
        return attack != null && inReach(mob, target, feet, attack, false);
    }

    private static boolean inReach(Mob mob, LivingEntity target, Vec3 feet, BetterCombatBridge.Profile attack, boolean facing) {
        Vec3 eye = feet.add(0, mob.getEyeHeight(), 0);
        Vec3 nearest = io.github.ningmeng123.mite.combat.SourcePlayerReach.closestPoint(target.getBoundingBox(), eye);
        double reach = attack.reach(mob, target, feet.y);
        if (eye.distanceToSqr(nearest) > reach * reach) return false;
        // A single intended target: no incidental friendly-fire sweep around an AI mob.
        if (facing && attack.angle() < 360D) {
            Vec3 toward = target.getBoundingBox().getCenter().subtract(eye).multiply(1, 0, 1);
            // LookControl turns the AI's head while a stationary mob keeps its last movement yaw.
            Vec3 look = Vec3.directionFromRotation(0, mob.getYHeadRot());
            double padding = Math.toDegrees(Math.atan2(target.getBbWidth() * .5D, Math.max(.001D, toward.length())));
            if (toward.lengthSqr() > .0001D && look.dot(toward.normalize())
                    < Math.cos(Math.toRadians(Math.min(180D, attack.angle() * .5D + padding)))) return false;
        }
        for (double fraction : new double[]{.5D, .9D, .1D}) {
            Vec3 point = target.position().add(0, target.getBbHeight() * fraction, 0);
            if (mob.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob))
                    .getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    /** AI requests a swing; damage is applied later through the original Mob pipeline. */
    public static boolean request(Mob mob, LivingEntity target) {
        if (mob.level().isClientSide || !mob.isAlive() || !supported(mob) || !valid(mob, target)
                || mob.isUsingItem() || MobShields.defending(mob)) return false;
        State state = STATES.computeIfAbsent(mob, ignored -> new State());
        if (mob.tickCount < state.readyAt) return false;
        if (!ItemStack.isSameItemSameComponents(state.weapon, mob.getMainHandItem())
                || mob.tickCount - state.readyAt > 20) state.combo = 0;
        var attack = BetterCombatBridge.profile(mob, state.combo);
        if (attack == null || !inReach(mob, target, mob.position(), attack, false)) return false;
        state.weapon = mob.getMainHandItem().copy();
        state.target = new WeakReference<>(target);
        state.trackedTarget = mob.getTarget() == target;
        state.attack = attack;
        state.impactAt = mob.tickCount + attack.windupTicks();
        state.readyAt = mob.tickCount + attack.cooldownTicks();
        ++state.combo;
        var movement = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.removeModifier(MOVEMENT);
            movement.addTransientModifier(new AttributeModifier(MOVEMENT, attack.movementSpeedMultiplier() - 1D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        MobAttackPayload.send(mob, attack.animation(), attack.cooldownTicks(), attack.windupTicks() / (float)attack.cooldownTicks());
        return true;
    }

    /** Cancelling a telegraphed swing still spends its recovery time. */
    public static void cancel(Mob mob) {
        State state = STATES.get(mob);
        if (state == null) return;
        state.target = null;
        state.attack = null;
        state.combo = 0;
        var movement = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.removeModifier(MOVEMENT);
        MobAttackPayload.send(mob, "", 0, 0F);
    }

    public static void tick(Mob mob) {
        State state = STATES.get(mob);
        if (state == null || state.attack == null) return;
        if (!mob.isAlive() || mob.isRemoved() || !supported(mob)
                || !ItemStack.isSameItemSameComponents(state.weapon, mob.getMainHandItem()) || mob.isUsingItem()) {
            cancel(mob);
            if (!mob.isAlive() || mob.isRemoved()) STATES.remove(mob);
            return;
        }
        // Brain melee writes a fixed interval after requesting the hit; keep it on weapon recovery too.
        if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.REGISTERED)) {
            if (mob.tickCount >= state.readyAt) mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_COOLING_DOWN);
            else mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, state.readyAt - mob.tickCount);
        }
        if (mob.tickCount >= state.readyAt) {
            var movement = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (movement != null) movement.removeModifier(MOVEMENT);
            if (state.target == null) { state.attack = null; return; }
        }
        if (state.target == null) return;
        LivingEntity target = state.target.get();
        if (!valid(mob, target) || state.trackedTarget && mob.getTarget() != target) { cancel(mob); return; }
        if (mob.tickCount < state.impactAt) return;
        state.target = null;
        if (!valid(mob, target) || mob.getTarget() != null && mob.getTarget() != target
                || !inReach(mob, target, mob.position(), state.attack, true)) return;
        mob.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, .6F, .9F + mob.getRandom().nextFloat() * .2F);
        var damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null) return;
        damage.addTransientModifier(new AttributeModifier(DAMAGE, state.attack.damageMultiplier() - 1D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        Mob previous = IMPACT.get();
        IMPACT.set(mob);
        try { mob.doHurtTarget(target); }
        finally {
            if (previous == null) IMPACT.remove(); else IMPACT.set(previous);
            damage.removeModifier(DAMAGE);
        }
    }

    @SubscribeEvent public static void afterTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide) tick(mob);
    }
}
