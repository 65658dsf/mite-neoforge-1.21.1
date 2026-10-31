package io.github.ningmeng123.mite.entity;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.Target;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native A* worker ownership, bounded callback pumping and cancellation cleanup. @author NingMeng123 */
@GameTestHolder("mite_path_perf")
@PrefixGameTestTemplate(false)
public final class AsyncPathSearchEngineGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();

    @GameTest(template = "empty", batch = "async_engine_route", timeoutTicks = 200)
    public static void nativeAStarRunsOffThreadWhileAllLiveEvaluatorCallsRemainOnTheMainThread(GameTestHelper h) {
        Fixture f = new Fixture(h, 256);
        long began = f.server.getTickCount();
        var task = f.submit();
        h.assertTrue(task != null && !task.done(), "Submitting a search does not synchronously run the native search");
        h.startSequence().thenWaitUntil(() -> {
            AsyncPathSearchEngine.pump(f.server, TimeUnit.MILLISECONDS.toNanos(4));
            h.assertTrue(task.done(), "Waiting for the real 256-edge native A* route");
        }).thenExecute(() -> {
            h.assertTrue(task.failure() == null && task.result() != null && task.result().canReach(), "The native worker search produces a complete route");
            h.assertTrue(task.result().getNodeCount() == 257, "All actual line-route nodes are preserved");
            h.assertTrue(f.evaluator.workerDistances.get() > 0 && f.evaluator.mainDistances.get() == 0,
                    "The real native A* distance bookkeeping runs on a worker rather than inside a main callback");
            h.assertTrue(f.evaluator.prepares == 1 && f.evaluator.starts == 1 && f.evaluator.targets == 1
                            && f.evaluator.neighbors == 256 && f.evaluator.cleanups == 1,
                    "Every live evaluator operation runs exactly once per required native operation and cleans up once");
            h.assertTrue(f.server.getTickCount() - began <= 80L,
                    "Bounded queue polling permits many ownership handshakes per tick, rather than one node per tick");
            f.close();
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "async_engine_null_start", timeoutTicks = 100)
    public static void aNullNativeStartStillCleansUpThePreparedEvaluatorExactlyOnce(GameTestHelper h) {
        Fixture f = new Fixture(h, 4);
        f.evaluator.nullStart = true;
        var task = f.submit();
        h.assertTrue(task != null, "The null-start fixture is submitted");
        h.startSequence().thenWaitUntil(() -> {
            AsyncPathSearchEngine.pump(f.server, TimeUnit.MILLISECONDS.toNanos(2));
            h.assertTrue(task.done(), "Waiting for native's early null-start return");
        }).thenExecute(() -> {
            h.assertTrue(task.result() == null && task.failure() == null, "A completed search with no path is distinct from failure or pending work");
            h.assertTrue(f.evaluator.prepares == 1 && f.evaluator.starts == 1 && f.evaluator.cleanups == 1,
                    "The engine closes the native early-return lifecycle exactly once");
            f.close();
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "async_engine_failure", timeoutTicks = 100)
    public static void aMainEvaluatorExceptionReachesTheTaskAndCleansUpWithoutBlockingWorkers(GameTestHelper h) {
        Fixture f = new Fixture(h, 4);
        f.evaluator.failNeighbors = true;
        var task = f.submit();
        h.assertTrue(task != null, "The throwing evaluator is submitted");
        h.startSequence().thenWaitUntil(() -> {
            AsyncPathSearchEngine.pump(f.server, TimeUnit.MILLISECONDS.toNanos(2));
            h.assertTrue(task.done(), "Waiting for the evaluator exception to cross the worker handoff");
        }).thenExecute(() -> {
            h.assertTrue(task.failure() != null && task.result() == null, "The caller receives failure rather than a stale path");
            h.assertTrue(f.evaluator.cleanups == 1, "A throwing neighbor callback still closes the original evaluator once");
            f.close();
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "async_engine_reentrant_cancel", timeoutTicks = 100)
    public static void cancellationInsideANeighborCallbackDefersCleanupUntilTheCallbackReturns(GameTestHelper h) {
        Fixture f = new Fixture(h, 8);
        AsyncPathSearchEngine.Task[] holder = new AsyncPathSearchEngine.Task[1];
        f.evaluator.onNeighbor = () -> {
            holder[0].cancel();
            h.assertTrue(f.evaluator.cleanups == 0, "Cancellation must not clean up an evaluator whose callback is still running");
        };
        holder[0] = f.submit();
        h.assertTrue(holder[0] != null, "The reentrant cancellation fixture is submitted");
        h.startSequence().thenWaitUntil(() -> {
            AsyncPathSearchEngine.pump(f.server, TimeUnit.MILLISECONDS.toNanos(2));
            h.assertTrue(holder[0].done(), "Waiting for worker acknowledgement and main-thread cleanup");
        }).thenExecute(() -> {
            h.assertTrue(holder[0].cancelled() && holder[0].result() == null && f.evaluator.cleanups == 1,
                    "Reentrant cancellation returns no path and cleans up exactly once after the ownership handoff");
            h.assertTrue(f.evaluator.neighbors == 1, "No further live callbacks run after cancellation");
            f.close();
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "async_engine_budget", timeoutTicks = 100)
    public static void aSlowUninterruptibleHookDoesNotCauseThePumpToDrainMoreHooksAfterItsBudget(GameTestHelper h) {
        Fixture f = new Fixture(h, 64);
        f.evaluator.neighborDelayNanos = TimeUnit.MILLISECONDS.toNanos(4);
        var task = f.submit();
        h.assertTrue(task != null, "The slow-hook fixture is submitted");
        h.startSequence().thenWaitUntil(() -> {
            int before = f.evaluator.neighbors;
            AsyncPathSearchEngine.pump(f.server, TimeUnit.MILLISECONDS.toNanos(1));
            int delta = f.evaluator.neighbors - before;
            h.assertTrue(delta <= 1, "The pump cannot start a second four-millisecond hook after a one-millisecond phase budget");
            h.assertTrue(delta == 1, "Waiting to measure an actual callback rather than an empty queue");
        }).thenExecute(task::cancel).thenWaitUntil(() -> {
            AsyncPathSearchEngine.pump(f.server, TimeUnit.MILLISECONDS.toNanos(1));
            h.assertTrue(task.done(), "Waiting for slow-hook cancellation cleanup");
        }).thenExecute(() -> {
            h.assertTrue(f.evaluator.cleanups == 1 && task.result() == null, "Budgeted pending work can be cancelled without a stranded evaluator");
            f.close();
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "async_engine_close", timeoutTicks = 100)
    public static void closingThePoolInsideACallbackWaitsForWorkersAndDefersLiveCleanup(GameTestHelper h) {
        Fixture f = new Fixture(h, 8);
        f.evaluator.onNeighbor = () -> {
            AsyncPathSearchEngine.close(f.server);
            h.assertTrue(f.evaluator.cleanups == 0, "Pool shutdown must not reenter a live evaluator callback's cleanup");
        };
        var task = f.submit();
        h.assertTrue(task != null, "The closing-pool fixture is submitted");
        h.startSequence().thenWaitUntil(() -> {
            AsyncPathSearchEngine.pump(f.server, TimeUnit.MILLISECONDS.toNanos(2));
            h.assertTrue(task.done(), "Waiting for shutdown acknowledgement and callback return");
        }).thenExecute(() -> {
            h.assertTrue(task.cancelled() && f.evaluator.cleanups == 1 && task.result() == null,
                    "Closing the pool cancels the worker and performs one main-thread cleanup");
            f.close();
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "async_engine_terminal_validity", timeoutTicks = 100)
    public static void terminalValidityIsRecheckedEvenWhenTheLastCallbackChangedInputsInTheSameTick(GameTestHelper h) {
        Fixture f = new Fixture(h, 4);
        AtomicInteger checks = new AtomicInteger();
        var task = f.submit(() -> {
            f.evaluator.assertMain();
            checks.incrementAndGet();
            return f.evaluator.cleanups == 0;
        });
        Fixture noStart = new Fixture(h, 4);
        noStart.evaluator.nullStart = true;
        AtomicInteger noStartChecks = new AtomicInteger();
        var noStartTask = noStart.submit(() -> {
            noStart.evaluator.assertMain();
            noStartChecks.incrementAndGet();
            return noStart.evaluator.cleanups == 0;
        });
        h.assertTrue(task != null, "The terminal-validity fixture is submitted");
        h.assertTrue(noStartTask != null, "The null-start terminal-validity fixture is submitted");
        h.startSequence().thenWaitUntil(() -> {
            AsyncPathSearchEngine.pump(f.server, TimeUnit.MILLISECONDS.toNanos(2));
            h.assertTrue(task.done() && noStartTask.done(), "Waiting for both final main-thread validity checks");
        }).thenExecute(() -> {
            h.assertTrue(checks.get() >= 2 && task.cancelled() && task.result() == null,
                    "The terminal check rejects inputs changed by the last callback even if the ordinary tick check was cached");
            h.assertTrue(f.evaluator.cleanups == 1, "Terminal invalidation does not duplicate cleanup");
            h.assertTrue(noStartChecks.get() >= 2 && noStartTask.cancelled() && noStart.evaluator.cleanups == 1,
                    "Native's null-start early return also validates after its final cleanup callback changes the inputs");
            f.close();
            noStart.close();
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "async_engine_capacity", timeoutTicks = 100)
    public static void queuedTasksAreBoundedAndClosingCancelsThemBeforeAnyLivePreparation(GameTestHelper h) {
        Fixture f = new Fixture(h, 4);
        List<AsyncPathSearchEngine.Task> tasks = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            var task = f.submit();
            h.assertTrue(task != null, "The bounded engine accepts each of its 64 task slots");
            tasks.add(task);
        }
        h.assertTrue(f.submit() == null, "The 65th concurrent task requests a synchronous caller fallback rather than growing the queue");
        tasks.getLast().cancel();
        AsyncPathSearchEngine.close(f.server);
        h.assertTrue(tasks.stream().allMatch(task -> task.done() && task.cancelled() && task.result() == null),
                "Shutdown acknowledges and releases queued and running cancellations");
        h.assertTrue(f.evaluator.prepares == 0 && f.evaluator.cleanups == 0,
                "Cancelling before the main-thread handoff never prepares or cleans up the live evaluator");
        f.close();
        h.succeed();
    }

    @GameTest(template = "empty", batch = "async_engine_cancel_queue_cleanup", timeoutTicks = 100)
    public static void repeatedCancellationReleasesQueuedCallbackClosuresWithoutClosingThePool(GameTestHelper h) {
        Fixture f = new Fixture(h, 4);
        for (int cycle = 0; cycle < 8; cycle++) {
            var task = f.submit();
            h.assertTrue(task != null, "Repeated cancelled work never consumes a permanent queue slot");
            awaitWorkerState(() -> queuedCallbacks(f.server) > 0);
            h.assertTrue(queuedCallbacks(f.server) > 0, "The worker actually queued a main-thread callback before cancellation");
            task.cancel();
            awaitWorkerState(() -> workerStopped(task) && completionQueued(f.server, task));
            h.assertTrue(workerStopped(task) && completionQueued(f.server, task),
                    "Cancellation releases the waiting worker and publishes completion before terminal pumping");
            // Completions are deliberately ready before pumping, reproducing the completion-before-callback ordering.
            AsyncPathSearchEngine.pump(f.server, TimeUnit.MILLISECONDS.toNanos(4));
            h.assertTrue(task.done() && queuedCallbacks(f.server) == 0,
                    "Terminal cleanup releases the cancelled callback closure even when the active-task collection becomes empty");
        }
        h.assertTrue(f.evaluator.prepares == 0 && f.evaluator.cleanups == 0,
                "Queue cleanup never executes a cancelled live callback or an unprepared evaluator cleanup");
        f.close();
        h.succeed();
    }

    private static void awaitWorkerState(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) LockSupport.parkNanos(50_000L);
    }

    private static boolean workerStopped(AsyncPathSearchEngine.Task task) {
        try {
            var field = AsyncPathSearchEngine.Task.class.getDeclaredField("workerStopped");
            field.setAccessible(true);
            return field.getBoolean(task);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static int queuedCallbacks(MinecraftServer server) {
        try {
            var states = AsyncPathSearchEngine.class.getDeclaredField("STATES");
            states.setAccessible(true);
            Object state = ((Map<?, ?>)states.get(null)).get(server);
            if (state == null) return 0;
            var callbacks = state.getClass().getDeclaredField("callbacks");
            callbacks.setAccessible(true);
            return ((BlockingQueue<?>)callbacks.get(state)).size();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static boolean completionQueued(MinecraftServer server, AsyncPathSearchEngine.Task task) {
        try {
            var states = AsyncPathSearchEngine.class.getDeclaredField("STATES");
            states.setAccessible(true);
            Object state = ((Map<?, ?>)states.get(null)).get(server);
            if (state == null) return false;
            var completed = state.getClass().getDeclaredField("completed");
            completed.setAccessible(true);
            return ((java.util.Collection<?>)completed.get(state)).contains(task);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static final class Fixture {
        final MinecraftServer server;
        final Zombie mob;
        final PathNavigationRegion region;
        final LineEvaluator evaluator;
        final List<AsyncPathSearchEngine.Task> submitted = new ArrayList<>();
        boolean closed;

        Fixture(GameTestHelper h, int length) {
            server = h.getLevel().getServer();
            BlockPos origin = new BlockPos(-3_440_000 + IDS.getAndIncrement() * 1024, h.absolutePos(BlockPos.ZERO).getY() + 8, -3_440_000);
            mob = EntityType.ZOMBIE.create(h.getLevel());
            h.assertTrue(mob != null, "The native mob exists for main-thread evaluator preparation");
            mob.moveTo(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D, 0F, 0F);
            region = new PathNavigationRegion(h.getLevel(), origin, origin);
            evaluator = new LineEvaluator(server, origin, length);
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }

        AsyncPathSearchEngine.Task submit() {
            return submit(() -> {
                evaluator.assertMain();
                return true;
            });
        }

        AsyncPathSearchEngine.Task submit(BooleanSupplier current) {
            var task = AsyncPathSearchEngine.submit((net.minecraft.server.level.ServerLevel)mob.level(), region, mob, evaluator,
                    Set.of(evaluator.origin.east(evaluator.length)), 2048, 1024F, 0, 1F, current);
            if (task != null) submitted.add(task);
            return task;
        }

        void close() {
            if (closed) return;
            closed = true;
            for (var task : submitted) if (!task.done()) task.cancel();
            mob.discard();
        }
    }

    private static final class LineEvaluator extends NodeEvaluator {
        final MinecraftServer server;
        final Thread mainThread = Thread.currentThread();
        final BlockPos origin;
        final int length;
        final Map<Long, Node> line = new HashMap<>();
        final AtomicInteger workerDistances = new AtomicInteger(), mainDistances = new AtomicInteger();
        int prepares, starts, targets, neighbors, cleanups;
        boolean nullStart, failNeighbors, inside;
        long neighborDelayNanos;
        Runnable onNeighbor;

        LineEvaluator(MinecraftServer server, BlockPos origin, int length) { this.server = server; this.origin = origin; this.length = length; }
        void assertMain() { if (!server.isSameThread()) throw new AssertionError("A live evaluator callback ran off the server thread"); }

        @Override public void prepare(PathNavigationRegion region, Mob mob) { assertMain(); prepares++; super.prepare(region, mob); line.clear(); }
        @Override public Node getStart() { assertMain(); starts++; return nullStart ? null : node(origin.getX()); }
        @Override public Target getTarget(double x, double y, double z) { assertMain(); targets++; return new Target(node((int)x)); }
        @Override public int getNeighbors(Node[] output, Node node) {
            assertMain(); neighbors++; inside = true;
            try {
                if (onNeighbor != null) onNeighbor.run();
                if (neighborDelayNanos > 0L) LockSupport.parkNanos(neighborDelayNanos);
                if (failNeighbors) throw new IllegalStateException("Expected main evaluator fixture failure");
                if (node.x >= origin.getX() + length) return 0;
                output[0] = node(node.x + 1);
                return 1;
            } finally { inside = false; }
        }
        private Node node(int x) {
            return line.computeIfAbsent(BlockPos.asLong(x, origin.getY(), origin.getZ()), ignored -> {
                Node node = new Node(x, origin.getY(), origin.getZ()) {
                    @Override public float distanceTo(Node other) {
                        (Thread.currentThread() == mainThread ? mainDistances : workerDistances).incrementAndGet();
                        return super.distanceTo(other);
                    }
                };
                node.type = PathType.WALKABLE;
                return node;
            });
        }
        @Override public void done() {
            assertMain();
            if (inside) throw new AssertionError("Cleanup reentered the live evaluator callback");
            cleanups++;
            super.done();
        }
        @Override public PathType getPathType(PathfindingContext context, int x, int y, int z) { assertMain(); return PathType.WALKABLE; }
        @Override public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) { assertMain(); return PathType.WALKABLE; }
    }
}
