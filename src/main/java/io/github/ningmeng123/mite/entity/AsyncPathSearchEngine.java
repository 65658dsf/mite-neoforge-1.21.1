package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.Target;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native A* bookkeeping on workers; every live evaluator operation stays on the server thread. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class AsyncPathSearchEngine {
    private static final int MAX_TASKS = 64;
    private static final long MAX_TASK_AGE_TICKS = 200L;
    private static final long HANDSHAKE_WAIT_NANOS = 50_000L;
    private static final Map<MinecraftServer, State> STATES = new IdentityHashMap<>();
    private static final Set<MinecraftServer> STOPPING = Collections.newSetFromMap(new WeakHashMap<>());

    private AsyncPathSearchEngine() {}

    /** Main thread only. A null return asks the caller to use its existing synchronous fallback. */
    @Nullable public static Task submit(ServerLevel level, PathNavigationRegion region, Mob mob, NodeEvaluator evaluator,
            Set<BlockPos> targets, int maxNodes, float range, int accuracy, float multiplier, BooleanSupplier stillCurrent) {
        MinecraftServer server = level.getServer();
        requireMain(server);
        if (STOPPING.contains(server) || targets.isEmpty() || maxNodes < 1 || maxNodes > 8192 || targets.size() > 4096)
            return null;
        State state = STATES.computeIfAbsent(server, State::new);
        if (state.stopping || state.tasks.size() >= MAX_TASKS) return null;
        Task task = new Task(state, level, region, mob, evaluator, targets, maxNodes, range, accuracy, multiplier, stillCurrent);
        state.tasks.add(task);
        try {
            state.workers.execute(task);
        } catch (RejectedExecutionException rejected) {
            state.tasks.remove(task);
            task.releaseLiveReferences();
            return null;
        }
        return task;
    }

    /** Main thread only. Distinguishes capacity backpressure from unsupported requests or server shutdown. */
    public static boolean saturated(MinecraftServer server) {
        requireMain(server);
        State state = STATES.get(server);
        return !STOPPING.contains(server) && state != null && !state.stopping && state.tasks.size() >= MAX_TASKS;
    }

    /** Main thread only. A single uninterruptible evaluator callback may exceed the supplied budget. */
    public static int pump(MinecraftServer server, long budgetNanos) {
        requireMain(server);
        State state = STATES.get(server);
        return state == null ? 0 : state.pump(Math.max(0L, budgetNanos));
    }

    /** Main thread only. Cancels work and releases the current pool; a running server may create a new one later. */
    public static void close(MinecraftServer server) {
        requireMain(server);
        State state = STATES.remove(server);
        if (state != null) state.close();
    }

    @SubscribeEvent public static void beforeTick(ServerTickEvent.Pre event) {
        pump(event.getServer(), phaseBudgetNanos());
    }

    @SubscribeEvent public static void afterTick(ServerTickEvent.Post event) {
        pump(event.getServer(), phaseBudgetNanos());
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        STOPPING.add(event.getServer());
        close(event.getServer());
    }

    private static long phaseBudgetNanos() {
        return TimeUnit.MILLISECONDS.toNanos(Math.clamp(Integer.getInteger("mite.asyncPathfinding.budgetMillis", 4), 1, 20));
    }

    private static void requireMain(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Live pathfinding operations require the server thread");
    }

    /** Completion and cancellation are separate from a completed search that legitimately found no path. */
    public static final class Task implements Runnable {
        private final State state;
        private final Set<BlockPos> targets;
        private final int maxNodes, accuracy;
        private final float range, multiplier;
        private final long createdTick;
        private volatile boolean started, workerStopped, cancelled, done;
        @Nullable private volatile Path result;
        @Nullable private volatile Throwable failure;
        @Nullable private volatile MainCall<?> waiting;
        @Nullable private ServerLevel level;
        @Nullable private PathNavigationRegion region;
        @Nullable private Mob mob;
        @Nullable private NodeEvaluator evaluator;
        @Nullable private BooleanSupplier stillCurrent;
        @Nullable private WorkerRegion workerRegion;
        private boolean prepared, cleaned, finishing;
        private int callbackDepth;
        private long checkedTick = Long.MIN_VALUE;

        private Task(State state, ServerLevel level, PathNavigationRegion region, Mob mob, NodeEvaluator evaluator,
                Set<BlockPos> targets, int maxNodes, float range, int accuracy, float multiplier, BooleanSupplier stillCurrent) {
            this.state = state; this.level = level; this.region = region; this.mob = mob; this.evaluator = evaluator;
            createdTick = state.server.getTickCount();
            this.stillCurrent = stillCurrent; this.maxNodes = maxNodes; this.range = range;
            this.accuracy = accuracy; this.multiplier = multiplier;
            LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
            for (BlockPos target : targets) positions.add(target.immutable());
            this.targets = Collections.unmodifiableSet(positions);
            // The native outer method only calls getProfiler on this main-thread-created wrapper.
            workerRegion = new WorkerRegion(level);
        }

        public boolean done() { return done; }
        public boolean cancelled() { return cancelled; }
        public long createdTick() { return createdTick; }
        @Nullable public Path result() { return done && !cancelled && failure == null ? result : null; }
        @Nullable public Throwable failure() { return failure; }

        /** Never waits for a worker. Cleanup follows its last node access and the current callback's return. */
        public void cancel() {
            requireMain(state.server);
            cancelMain(new CancellationException("Path search cancelled"));
        }

        private void cancelMain(Throwable reason) {
            if (done) return;
            cancelled = true;
            recordFailure(reason);
            MainCall<?> pending = waiting;
            if (pending != null) pending.future.completeExceptionally(reason);
            if (!started && state.workers.remove(this)) {
                workerStopped = true;
                state.completed.add(this);
            }
            // Normal cancellation never performs an unbudgeted live cleanup in its caller.
            if (workerStopped) state.completed.add(this);
        }

        private boolean current(boolean terminal) {
            if (cancelled || state.stopping) return false;
            long tick = state.server.getTickCount();
            if (tick - createdTick >= MAX_TASK_AGE_TICKS) {
                cancelMain(new CancellationException("Path search exceeded its absolute tick lifetime"));
                return false;
            }
            if (!terminal && checkedTick == tick) return true;
            checkedTick = tick;
            try {
                if (stillCurrent != null && stillCurrent.getAsBoolean()) return true;
                cancelMain(new CancellationException("Path search inputs changed"));
            } catch (Throwable error) { cancelMain(error); }
            return false;
        }

        private synchronized void recordFailure(Throwable error) {
            if (failure == null) failure = error;
            else if (failure != error && !(error instanceof CancellationException)) failure.addSuppressed(error);
        }

        @Override public void run() {
            started = true;
            try {
                if (cancelled) throw new CancellationException("Path search cancelled before starting");
                ProxyEvaluator proxy = new ProxyEvaluator(this);
                // The real Mob and world are never passed into the worker's native search.
                result = new PathFinder(proxy, maxNodes).findPath(workerRegion, null, targets, range, accuracy, multiplier);
            } catch (Throwable error) {
                recordFailure(error);
            } finally {
                // There are no further worker accesses to nodes or the live evaluator after this publication.
                workerStopped = true;
                state.completed.add(this);
            }
        }

        private <T> T main(Callable<T> action, boolean cleanup) {
            if (cancelled && !cleanup) throw new CancellationException("Path search cancelled");
            MainCall<T> call = new MainCall<>(this, action, cleanup);
            waiting = call;
            state.callbacks.add(call);
            try {
                return call.future.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Path worker interrupted");
            } catch (ExecutionException error) {
                Throwable cause = error.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error fatal) throw fatal;
                throw new IllegalStateException("Main-thread path evaluator failed", cause);
            } finally {
                waiting = null;
            }
        }

        private void cleanupMain() {
            if (!prepared || cleaned) return;
            cleaned = true;
            try { evaluator.done(); } catch (Throwable error) { recordFailure(error); }
        }

        private void finishMain() {
            if (done || finishing || !workerStopped || callbackDepth != 0) return;
            finishing = true;
            try {
                cleanupMain();
                // Validate after every live callback, including cleanup on native's null-start early return.
                if (!cancelled && !state.stopping) current(true);
                if (cancelled || failure != null) result = null;
                // A cancelled wait can leave its already-completed call queued behind this terminal event.
                // The worker has stopped, so it can no longer enqueue another call for this task.
                state.callbacks.removeIf(call -> call.task == this);
                releaseLiveReferences();
                done = true;
                state.tasks.remove(this);
            } finally { finishing = false; }
        }

        private void releaseLiveReferences() {
            level = null; region = null; mob = null; evaluator = null; stillCurrent = null; workerRegion = null;
        }
    }

    private static final class MainCall<T> {
        private final Task task;
        private final Callable<T> action;
        private final boolean cleanup;
        private final CompletableFuture<T> future = new CompletableFuture<>();

        MainCall(Task task, Callable<T> action, boolean cleanup) { this.task = task; this.action = action; this.cleanup = cleanup; }

        void execute() {
            if (future.isDone()) return;
            if (!cleanup && !task.current(false)) {
                future.completeExceptionally(new CancellationException("Path inputs no longer current"));
                return;
            }
            task.callbackDepth++;
            try {
                T result = action.call();
                if (task.cancelled) future.completeExceptionally(new CancellationException("Path search cancelled in evaluator"));
                else future.complete(result);
            } catch (Throwable error) {
                task.recordFailure(error);
                future.completeExceptionally(error);
            } finally {
                task.callbackDepth--;
                if (task.state.stopping && task.workerStopped) task.finishMain();
            }
        }
    }

    /** Only the native A* side touches nodes between blocking main-thread ownership handoffs. */
    private static final class ProxyEvaluator extends NodeEvaluator {
        private final Task task;
        private Node start;
        private final Map<Long, Target> targets = new java.util.HashMap<>();

        ProxyEvaluator(Task task) { this.task = task; }

        @Override public void prepare(PathNavigationRegion ignoredRegion, Mob ignoredMob) {
            task.main(() -> {
                task.prepared = true;
                task.evaluator.prepare(task.region, task.mob);
                start = task.evaluator.getStart();
                if (start != null) for (BlockPos pos : task.targets)
                    targets.put(pos.asLong(), task.evaluator.getTarget(pos.getX(), pos.getY(), pos.getZ()));
                return null;
            }, false);
        }

        @Override public Node getStart() { return start; }
        @Override public Target getTarget(double x, double y, double z) { return targets.get(BlockPos.asLong((int)x, (int)y, (int)z)); }
        @Override public int getNeighbors(Node[] neighbors, Node node) {
            return task.main(() -> task.evaluator.getNeighbors(neighbors, node), false);
        }
        @Override public void done() { task.main(() -> { task.cleanupMain(); return null; }, true); }
        @Override public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            throw new UnsupportedOperationException("The worker never evaluates live path types");
        }
        @Override public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
            throw new UnsupportedOperationException("The worker never evaluates live mob geometry");
        }
    }

    private static final class WorkerRegion extends PathNavigationRegion {
        WorkerRegion(ServerLevel level) { super(level, BlockPos.ZERO, BlockPos.ZERO); }
        @Override public ProfilerFiller getProfiler() { return InactiveProfiler.INSTANCE; }
    }

    private static final class State {
        private final MinecraftServer server;
        private final ThreadPoolExecutor workers;
        private final LinkedHashSet<Task> tasks = new LinkedHashSet<>();
        private final LinkedBlockingQueue<MainCall<?>> callbacks = new LinkedBlockingQueue<>();
        private final ConcurrentLinkedQueue<Task> completed = new ConcurrentLinkedQueue<>();
        private boolean stopping;

        State(MinecraftServer server) {
            this.server = server;
            AtomicInteger ids = new AtomicInteger();
            int count = Math.clamp(Integer.getInteger("mite.asyncPathfinding.workers", 2), 1, 8);
            workers = new ThreadPoolExecutor(count, count, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAX_TASKS), runnable -> {
                Thread thread = new Thread(runnable, "MITE Pathfinding Worker " + ids.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
        }

        int pump(long budget) {
            long start = System.nanoTime();
            int calls = 0;
            for (Task task : new ArrayList<>(tasks))
                if (server.getTickCount() - task.createdTick >= MAX_TASK_AGE_TICKS)
                    task.cancelMain(new CancellationException("Path search exceeded its absolute tick lifetime"));
            while (System.nanoTime() - start < budget) {
                Task terminal = completed.poll();
                if (terminal != null) { terminal.finishMain(); continue; }
                if (tasks.isEmpty()) break;
                try {
                    long remaining = budget - (System.nanoTime() - start);
                    if (remaining <= 0L) break;
                    MainCall<?> call = callbacks.poll(Math.min(HANDSHAKE_WAIT_NANOS, remaining), TimeUnit.NANOSECONDS);
                    if (call != null) { call.execute(); calls++; }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return calls;
        }

        void close() {
            stopping = true;
            for (Task task : new ArrayList<>(tasks)) task.cancelMain(new CancellationException("Pathfinding engine closed"));
            for (Runnable queued : workers.shutdownNow()) {
                Task task = (Task)queued;
                task.workerStopped = true;
                completed.add(task);
            }
            try {
                // No worker can call live code: cancellation releases every main-thread wait.
                if (!workers.awaitTermination(5L, TimeUnit.SECONDS))
                    throw new IllegalStateException("Bounded pathfinding workers did not stop after cancellation");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping pathfinding workers", interrupted);
            }
            for (Task task : new ArrayList<>(tasks)) task.finishMain();
            callbacks.clear();
            completed.clear();
        }
    }
}
