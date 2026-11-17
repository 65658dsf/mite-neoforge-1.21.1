package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.Mite;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.TickTask;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Retire automatic crafting at acquisition, placement and saved-world boundaries. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class RemovedCrafter {
    private RemovedCrafter() {}

    public static void remove(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.CRAFTER)) {
            // Packed block-entity NBT must load against the original state before onRemove reads its inventory.
            level.getBlockEntity(pos);
            // Native onRemove drops the stored ingredients exactly once.
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    @SubscribeEvent
    public static void load(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        level.getServer().tell(new TickTask(level.getServer().getTickCount(), () -> {
            if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) return;
            for (BlockPos pos : List.copyOf(chunk.getBlockEntitiesPos())) remove(level, pos);
        }));
    }

    @SubscribeEvent
    public static void place(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().is(Blocks.CRAFTER)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().is(Items.CRAFTER)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class CreativeEvents {
        private CreativeEvents() {}

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void creative(BuildCreativeModeTabContentsEvent event) {
            java.util.stream.Stream.concat(event.getParentEntries().stream(), event.getSearchEntries().stream())
                    .filter(stack -> stack.is(Items.CRAFTER)).toList()
                    .forEach(stack -> event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        }
    }
}
