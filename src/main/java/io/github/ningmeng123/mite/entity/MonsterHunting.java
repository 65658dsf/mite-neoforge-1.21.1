package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.Mite;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/** User adaptation: monsters only hunt non-player prey during an actual blood moon night. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class MonsterHunting {
    private static final String PREY = "mite_blood_moon_prey";
    private MonsterHunting() {}

    public static boolean active(Level level) {
        return LunarCalendar.state(level.getDayTime(), level.dimension().equals(Level.OVERWORLD)).bloodNight();
    }

    public static boolean monster(Mob mob) {
        return mob.getType().getCategory() == MobCategory.MONSTER || R196MonsterAi.monster(mob);
    }

    public static boolean mayAttackPrey(Mob mob, LivingEntity target) {
        return playerTarget(target) || active(mob.level()) || retaliation(mob, target);
    }

    private static boolean retaliation(Mob mob, LivingEntity target) {
        if (mob.getLastHurtByMob() == target || PiglinRetaliation.isRetaliating(mob, target)) return true;
        if (mob instanceof Vex vex && vex.getOwner() != null) {
            Mob owner = vex.getOwner();
            return owner.getLastHurtByMob() == target || PiglinRetaliation.isRetaliating(owner, target);
        }
        return false;
    }

    private static boolean playerTarget(LivingEntity target) {
        return target instanceof Player || target.getPassengers().stream().anyMatch(Player.class::isInstance);
    }

    public static void remember(Mob mob, @Nullable LivingEntity target) {
        forget(mob);
        if (target != null && !playerTarget(target) && mob.getTarget() == target
                && !retaliation(mob, target)) mob.getPersistentData().putUUID(PREY, target.getUUID());
    }

    public static void forget(Mob mob) { mob.getPersistentData().remove(PREY); }

    /** Covers accepted secondary assignments such as a vex copying its summoner's blood-night target. */
    public static void acceptedTargetChange(Mob mob, @Nullable LivingEntity target) {
        if (mob.level().isClientSide || !monster(mob)) return;
        if (target == null || playerTarget(target) || retaliation(mob, target)) forget(mob);
        else if (active(mob.level())) remember(mob, target);
    }

    public static void rememberBrainHunt(Mob mob, @Nullable LivingEntity target) {
        forget(mob);
        if (target != null && !playerTarget(target) && !retaliation(mob, target))
            mob.getPersistentData().putUUID(PREY, target.getUUID());
    }

    /** Runs before mob AI/contact attacks, including the first tick after a saved blood-night hunt loads. */
    public static void stopExpired(Mob mob) {
        var data = mob.getPersistentData();
        if (mob.level().isClientSide || !data.hasUUID(PREY) || active(mob.level())) return;
        UUID prey = data.getUUID(PREY);
        forget(mob);
        if (mob instanceof SourceMonsterTargetMemory memory) {
            if (prey.equals(memory.mite$pendingTarget())) memory.mite$pendingTarget(null);
            if (memory.mite$retarget() != null && prey.equals(memory.mite$retarget().getUUID())) memory.mite$retarget(null);
        }
        LivingEntity target = mob.getTarget();
        if (target != null && prey.equals(target.getUUID()) && !retaliation(mob, target)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        var brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            brain.getMemory(MemoryModuleType.ATTACK_TARGET).filter(entity -> prey.equals(entity.getUUID())
                    && !retaliation(mob, entity)).ifPresent(entity -> {
                        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                        mob.getNavigation().stop();
                    });
        }
    }

    /** NeoForge's brain acquisition event complements ordinary nearest-target Goal selectors. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void brainTarget(LivingChangeTargetEvent event) {
        if (event.getTargetType() != LivingChangeTargetEvent.LivingTargetType.BEHAVIOR_TARGET
                || !(event.getEntity() instanceof Mob mob) || !monster(mob)) return;
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || playerTarget(target)) return;
        if (!mayAttackPrey(mob, target)) event.setCanceled(true);
        else rememberBrainHunt(mob, target);
    }

    /** Keeps the original range, chance and sight rules, while gating acquisition and continuation. */
    public static class PreyGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        public PreyGoal(Mob mob, Class<T> type, boolean sight) { super(mob, type, sight); }
        public PreyGoal(Mob mob, Class<T> type, int chance, boolean sight, boolean nearby, @Nullable Predicate<LivingEntity> predicate) {
            super(mob, type, chance, sight, nearby, predicate);
        }
        @Override public boolean canUse() { return active(mob.level()) && super.canUse(); }
        @Override public boolean canContinueToUse() { return active(mob.level()) && super.canContinueToUse(); }
        @Override public void start() { super.start(); remember(mob, mob.getTarget()); }
    }
}
