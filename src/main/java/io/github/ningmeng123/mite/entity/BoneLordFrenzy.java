package io.github.ningmeng123.mite.entity;

import net.minecraft.world.entity.LivingEntity;

/** Short source frenzy shared by ordinary and ancient skeleton troops. @author NingMeng123 */
public interface BoneLordFrenzy {
    void mite$setBoneLordFrenzy(LivingEntity target, int ticks);
    int mite$boneLordFrenzyTicks();
}
