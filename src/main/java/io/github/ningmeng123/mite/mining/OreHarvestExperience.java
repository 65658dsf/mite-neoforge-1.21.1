package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Native drops fire after loot evaluation, allowing XP to exclude R196's fortune copies. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class OreHarvestExperience {
    private record Pending(ServerLevel level, BlockPos pos, BlockState state, int reward) {}
    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();
    private OreHarvestExperience() {}
    public static void record(LootContext context, int reward) {
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        var state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (origin != null && state != null) PENDING.set(new Pending(context.getLevel(), BlockPos.containing(origin), state, reward));
    }
    @SubscribeEvent
    public static void drops(BlockDropsEvent event) {
        Pending pending = PENDING.get();
        PENDING.remove();
        if (pending != null && pending.level() == event.getLevel() && pending.pos().equals(event.getPos())
                && pending.state() == event.getState()) event.setDroppedExperience(pending.reward());
    }
}
