package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Saved source stock is initialized once on fresh spawn, never rerolled on reconnect or server restart. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class SourceVanillaMobLootState {
    private SourceVanillaMobLootState() {}
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide && event.getEntity() instanceof Mob mob) SourceVanillaMobLoot.initialize(mob);
    }
}
