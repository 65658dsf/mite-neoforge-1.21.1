package io.github.ningmeng123.mite.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.ningmeng123.mite.Mite;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Player-centred, incremental block clearing for operators. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class ClearBlocksCommand {
    // Work per tick, not a limit on the size of a command's region.
    private static final int BLOCKS_PER_TICK = 32768;
    private static final Map<MinecraftServer, LinkedHashMap<UUID, ClearTask>> TASKS = new LinkedHashMap<>();
    private static final SimpleCommandExceptionType ALREADY_RUNNING = new SimpleCommandExceptionType(
            Component.translatable("commands.mite.clearblocks.running"));

    private ClearBlocksCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mite").requires(source -> source.hasPermission(2))
                .then(Commands.literal("clearblocks")
                        .then(Commands.literal("cancel").executes(context -> cancel(context.getSource())))
                        .then(Commands.argument("length", IntegerArgumentType.integer(1))
                                .then(Commands.argument("width", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("block", BlockStateArgument.block(event.getBuildContext()))
                                                        .executes(context -> start(context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "length"),
                                                                IntegerArgumentType.getInteger(context, "width"),
                                                                IntegerArgumentType.getInteger(context, "height"),
                                                                BlockStateArgument.getBlock(context, "block")))))))));
    }

    private static int start(CommandSourceStack source, int length, int width, int height, BlockInput target)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        // Long arithmetic prevents large user arguments wrapping around to a different region.
        BlockPos min = position((long) center.getX() - length / 2,
                (long) center.getY() - height / 2, (long) center.getZ() - width / 2);
        BlockPos max = position((long) min.getX() + length - 1,
                (long) min.getY() + height - 1, (long) min.getZ() + width - 1);
        if (!level.isInWorldBounds(min) || !level.isInWorldBounds(max)) {
            throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
        }
        LinkedHashMap<UUID, ClearTask> queue = TASKS.computeIfAbsent(source.getServer(), server -> new LinkedHashMap<>());
        if (queue.containsKey(player.getUUID())) throw ALREADY_RUNNING.create();
        queue.put(player.getUUID(), new ClearTask(source, level, min, max, target));
        source.sendSuccess(() -> Component.translatable("commands.mite.clearblocks.queued", length, width, height,
                center.getX(), center.getY(), center.getZ(), target.getState().getBlock().getName()), false);
        return 1;
    }

    private static BlockPos position(long x, long y, long z) throws CommandSyntaxException {
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            throw BlockPosArgument.ERROR_OUT_OF_WORLD.create();
        }
        return new BlockPos((int) x, (int) y, (int) z);
    }

    private static int cancel(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ClearTask removed = removeTask(player);
        source.sendSuccess(() -> removed == null
                ? Component.translatable("commands.mite.clearblocks.idle")
                : Component.translatable("commands.mite.clearblocks.cancelled", removed.removed.toString()), false);
        return removed == null ? 0 : 1;
    }

    private static ClearTask removeTask(ServerPlayer player) {
        var queue = TASKS.get(player.getServer());
        if (queue == null) return null;
        ClearTask removed = queue.remove(player.getUUID());
        if (queue.isEmpty()) TASKS.remove(player.getServer());
        return removed;
    }

    public static boolean hasPendingTask(ServerPlayer player) {
        var queue = TASKS.get(player.getServer());
        return queue != null && queue.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        processPending(event.getServer());
    }

    /** Runs a bounded slice; also used by headless integration tests without advancing unrelated world systems. */
    public static void processPending(MinecraftServer server) {
        var queue = TASKS.get(server);
        if (queue == null || queue.isEmpty()) return;
        var entry = queue.entrySet().iterator().next();
        ClearTask task = entry.getValue();
        if (task.processChunkSlice()) {
            queue.remove(entry.getKey());
            if (queue.isEmpty()) TASKS.remove(server);
            task.source.sendSuccess(() -> Component.translatable("commands.mite.clearblocks.success",
                    task.removed.toString(), task.target.getState().getBlock().getName()), true);
        } else {
            // Round-robin prevents one enormous selection from starving other operators.
            queue.remove(entry.getKey());
            queue.put(entry.getKey(), task);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) removeTask(player);
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        TASKS.remove(event.getServer());
    }

    private static final class ClearTask {
        private final CommandSourceStack source;
        private final ServerLevel level;
        private final BlockPos min;
        private final BlockPos max;
        private final BlockInput target;
        private int chunkX;
        private int chunkZ;
        private int x;
        private int y;
        private int z;
        private BigInteger removed = BigInteger.ZERO;

        private ClearTask(CommandSourceStack source, ServerLevel level, BlockPos min, BlockPos max, BlockInput target) {
            this.source = source;
            this.level = level;
            this.min = min;
            this.max = max;
            this.target = target;
            chunkX = min.getX() >> 4;
            chunkZ = min.getZ() >> 4;
            resetCursor();
        }

        private void resetCursor() {
            x = Math.max(min.getX(), chunkX << 4);
            y = min.getY();
            z = Math.max(min.getZ(), chunkZ << 4);
        }

        private boolean processChunkSlice() {
            // Load at most one chunk per tick. A huge selection is never materialised in memory.
            level.getChunk(chunkX, chunkZ);
            int left = Math.max(min.getX(), chunkX << 4);
            int right = Math.min(max.getX(), (chunkX << 4) + 15);
            int front = Math.max(min.getZ(), chunkZ << 4);
            int back = Math.min(max.getZ(), (chunkZ << 4) + 15);
            var changed = new ArrayList<BlockPos>();
            var cursor = new BlockPos.MutableBlockPos();
            boolean chunkFinished = false;
            for (int scanned = 0; scanned < BLOCKS_PER_TICK; scanned++) {
                cursor.set(x, y, z);
                if (target.test(level, cursor) && !level.getBlockState(cursor).isAir()) {
                    Clearable.tryClear(level.getBlockEntity(cursor));
                    if (level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)) {
                        changed.add(cursor.immutable());
                    }
                }
                if (++x > right) {
                    x = left;
                    if (++z > back) {
                        z = front;
                        if (++y > max.getY()) {
                            chunkFinished = true;
                            break;
                        }
                    }
                }
            }
            removed = removed.add(BigInteger.valueOf(changed.size()));
            for (BlockPos pos : changed) level.blockUpdated(pos, level.getBlockState(pos).getBlock());
            if (!chunkFinished) return false;
            if (++chunkX > (max.getX() >> 4)) {
                chunkX = min.getX() >> 4;
                if (++chunkZ > (max.getZ() >> 4)) return true;
            }
            resetCursor();
            return false;
        }
    }
}
