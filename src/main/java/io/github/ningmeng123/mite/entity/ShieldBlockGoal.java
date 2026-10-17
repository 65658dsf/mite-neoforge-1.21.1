package io.github.ningmeng123.mite.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Brief anticipatory guard windows leave time to flank, interrupt and counterattack. @author NingMeng123 */
public final class ShieldBlockGoal extends Goal {
    private final Mob mob;
    private int nextAttempt;
    private int remaining;

    public ShieldBlockGoal(Mob mob) {
        this.mob = mob;
        nextAttempt = mob.tickCount + 10;
        // This action deliberately shares movement and looking with the existing pursuit/melee goals.
    }

    private boolean threat() {
        return mob.isAlive() && mob.getTarget() instanceof Player player && player.isAlive() && !player.isSpectator()
                && !player.getAbilities().invulnerable && mob.distanceToSqr(player) <= 36D
                && mob.getSensing().hasLineOfSight(player) && MobShields.melee(mob.getMainHandItem())
                && MobShields.shield(mob.getOffhandItem()) && !MobShields.disabled(mob);
    }

    @Override public boolean canUse() {
        if (mob.tickCount < nextAttempt || mob.isUsingItem()
                || io.github.ningmeng123.mite.compat.bettercombat.BetterMobCombat.attacking(mob) || !threat()) return false;
        nextAttempt = mob.tickCount + 10;
        return mob.getRandom().nextFloat() < .55F;
    }

    @Override public void start() {
        remaining = 18 + mob.getRandom().nextInt(13);
        mob.startUsingItem(InteractionHand.OFF_HAND);
    }

    @Override public boolean canContinueToUse() { return remaining > 0 && threat() && MobShields.defending(mob); }

    @Override public void tick() {
        if (mob.getTarget() != null) mob.getLookControl().setLookAt(mob.getTarget(), 25F, 25F);
        --remaining;
    }

    @Override public void stop() {
        if (MobShields.defending(mob)) mob.stopUsingItem();
        remaining = 0;
        nextAttempt = mob.tickCount + 24 + mob.getRandom().nextInt(17);
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }
}
