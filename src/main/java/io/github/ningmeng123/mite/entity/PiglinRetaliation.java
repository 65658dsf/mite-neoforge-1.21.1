package io.github.ningmeng123.mite.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/** Tracks genuine piglin retaliation separately from proactive hoglin hunting. @author NingMeng123 */
public final class PiglinRetaliation {
    private static final String TARGET = "mite_piglin_retaliation_target";
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private PiglinRetaliation() {}

    public static void begin() { DEPTH.set(DEPTH.get() + 1); }

    public static void end() {
        int depth = DEPTH.get() - 1;
        if (depth == 0) DEPTH.remove();
        else DEPTH.set(depth);
    }

    /** Called after vanilla has accepted an anger target, including synchronous ally broadcasts. */
    public static void angerTargetSet(AbstractPiglin piglin, LivingEntity target) {
        if (!piglin.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(target.getUUID()::equals).isPresent()) return;
        if (DEPTH.get() > 0) piglin.getPersistentData().putUUID(TARGET, target.getUUID());
        else piglin.getPersistentData().remove(TARGET);
    }

    public static boolean isRetaliating(Mob mob, LivingEntity target) {
        return mob instanceof AbstractPiglin && mob.getPersistentData().hasUUID(TARGET)
                && target.getUUID().equals(mob.getPersistentData().getUUID(TARGET))
                && mob.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(target.getUUID()::equals).isPresent();
    }
}
