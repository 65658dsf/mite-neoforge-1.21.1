package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Both newly spawned and loaded humanoid monsters can search for better equipment. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class MonsterEquipmentScavenging {
    private MonsterEquipmentScavenging() {}

    public static void install(Mob mob) {
        if (!(mob instanceof PathfinderMob pathfinder) || !MobEquipmentUpgrades.handles(mob)) return;
        mob.setCanPickUpLoot(true);
        if (mob.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof SeekBetterEquipmentGoal))
            mob.goalSelector.addGoal(5, new SeekBetterEquipmentGoal(pathfinder));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && !event.getLevel().isClientSide && event.getEntity() instanceof Mob mob) install(mob);
    }
}
