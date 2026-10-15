package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Natural trial blocks keep their identity through remove-before-loot transactions. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class TrialBlockDrops {
    private record Context(ServerLevel level, BlockPos pos, Context parent) {}
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();
    private static final Scope EMPTY = () -> {};

    private TrialBlockDrops() {}

    public static boolean suppresses(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return false;
        for (Context context = CONTEXT.get(); context != null; context = context.parent()) {
            if (context.level() == server && context.pos().equals(pos)) return true;
        }
        return NaturalTrialBlocks.isNatural(server, pos);
    }

    public static Scope destruction(Level level, BlockPos pos) {
        return suppresses(level, pos) ? knownNaturalDestruction(level, pos) : EMPTY;
    }

    /** A verified falling entity can reach an empty destination that has no chunk marker yet. */
    public static Scope knownNaturalDestruction(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return EMPTY;
        Context previous = CONTEXT.get();
        CONTEXT.set(new Context(server, pos.immutable(), previous));
        return () -> {
            if (previous == null) CONTEXT.remove();
            else CONTEXT.set(previous);
        };
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void blockDrops(BlockDropsEvent event) {
        if (suppresses(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}
