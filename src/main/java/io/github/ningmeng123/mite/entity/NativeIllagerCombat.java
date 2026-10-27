package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.CrossbowItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Native illagers retain usable combat after saved attributes or early equipment restrictions. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class NativeIllagerCombat {
    public static final double MIN_VINDICATOR_FOLLOW_RANGE = 32D;

    private NativeIllagerCombat() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        if (mob.getType() == EntityType.VINDICATOR) {
            var range = mob.getAttribute(Attributes.FOLLOW_RANGE);
            // Repair old saved twelve-block bases too. Keep all modifiers and larger custom bases.
            if (range != null && range.getBaseValue() < MIN_VINDICATOR_FOLLOW_RANGE)
                range.setBaseValue(MIN_VINDICATOR_FOLLOW_RANGE);
        } else if (mob.getType() == EntityType.PILLAGER && mob instanceof Pillager pillager
                && mob.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof UnarmedPillagerMeleeGoal)) {
            mob.goalSelector.addGoal(4, new UnarmedPillagerMeleeGoal(pillager));
        }
    }

    /** Losing a crossbow permits melee; gaining any real CrossbowItem returns control to native ranged AI. */
    public static final class UnarmedPillagerMeleeGoal extends MeleeAttackGoal {
        public UnarmedPillagerMeleeGoal(Pillager pillager) { super(pillager, 1D, true); }

        private boolean hasCrossbow() { return mob.isHolding(stack -> stack.getItem() instanceof CrossbowItem); }

        @Override public boolean canUse() { return !hasCrossbow() && super.canUse(); }

        @Override public boolean canContinueToUse() { return !hasCrossbow() && super.canContinueToUse(); }

        // The selector checks continuation every other server tick; never swing during that gap after rearming.
        @Override public void tick() { if (!hasCrossbow()) super.tick(); }
    }
}
