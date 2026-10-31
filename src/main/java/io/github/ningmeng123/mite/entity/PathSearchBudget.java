package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Fair main-thread admission for complete synchronous pursuit decisions. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PathSearchBudget {
    private static final int MAX_DECISIONS = 2;
    private static final long MAX_NANOS = 4_000_000L;
    // A stopped goal must not leave a valid-but-idle target at the head of the whole server.
    private static final int ABANDONED_TICKS = 2;
    private static final Map<MinecraftServer, State> STATES = new IdentityHashMap<>();
    // Entries exist only for the duration of a main-thread call, never between ticks.
    private static final Map<PathfinderMob, Decision> CURRENT = new IdentityHashMap<>();

    private PathSearchBudget() {}

    public record Stats(long granted, long deferred, long decisionNanos, long peakDecisionNanos,
                        int backlog, int tickGranted, long tickNanos) {}

    private static final class Request {
        final WeakReference<PathfinderMob> mob;
        int requestedAt;
        Request(PathfinderMob mob, int tick) { this.mob = new WeakReference<>(mob); requestedAt = tick; }
    }

    private static final class State {
        final LinkedHashMap<UUID, Request> waiting = new LinkedHashMap<>();
        int tick = Integer.MIN_VALUE, tickGranted;
        long tickNanos, granted, deferred, decisionNanos, peakDecisionNanos;

        void refresh(int now) {
            if (tick != now) { tick = now; tickGranted = 0; tickNanos = 0L; }
            waiting.entrySet().removeIf(entry -> {
                Request request = entry.getValue(); PathfinderMob mob = request.mob.get();
                return mob == null || mob.isRemoved() || !mob.isAlive() || mob.getTarget() == null
                        || now - request.requestedAt >= ABANDONED_TICKS;
            });
        }
    }

    /** Nested planner/pursuit calls share the same lazily acquired decision permit. */
    static Decision open(PathfinderMob mob) {
        Decision parent = CURRENT.get(mob);
        Decision decision = new Decision(mob, parent);
        if (parent == null) CURRENT.put(mob, decision);
        return decision;
    }

    static final class Decision implements AutoCloseable {
        final PathfinderMob mob;
        final Decision parent;
        State admitted;
        long nanos;
        boolean closed;
        Decision(PathfinderMob mob, Decision parent) { this.mob = mob; this.parent = parent; }
        Decision root() { return parent == null ? this : parent.root(); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (parent != null) return;
            CURRENT.remove(mob, this);
            if (admitted != null) admitted.peakDecisionNanos = Math.max(admitted.peakDecisionNanos, nanos);
        }
    }

    static <T> T synchronous(PathfinderMob mob, Supplier<T> work) {
        if (!MiteConfig.pursuitSearchBudgetEnabled() || !(mob.level() instanceof ServerLevel level)) return work.get();
        Decision current = CURRENT.get(mob);
        if (current == null) {
            try (Decision ignored = open(mob)) { return synchronous(mob, work); }
        }
        Decision root = current.root();
        State state = STATES.computeIfAbsent(level.getServer(), ignored -> new State());
        int tick = level.getServer().getTickCount();
        state.refresh(tick);
        if (root.admitted == null) {
            UUID id = mob.getUUID();
            Request request = state.waiting.computeIfAbsent(id, ignored -> new Request(mob, tick));
            request.requestedAt = tick;
            boolean first = state.waiting.keySet().iterator().next().equals(id);
            if (!first || state.tickGranted >= MAX_DECISIONS || state.tickNanos >= MAX_NANOS) {
                state.deferred++;
                throw AsyncMeleePursuit.pendingSignal();
            }
            state.waiting.remove(id);
            state.granted++; state.tickGranted++;
            root.admitted = state;
        }
        long began = System.nanoTime();
        try { return work.get(); }
        finally {
            long elapsed = Math.max(0L, System.nanoTime() - began);
            root.nanos += elapsed;
            state.tickNanos += elapsed; state.decisionNanos += elapsed;
        }
    }

    public static boolean pending(PathfinderMob mob) {
        if (!MiteConfig.pursuitSearchBudgetEnabled() || !(mob.level() instanceof ServerLevel level)) return false;
        State state = STATES.get(level.getServer());
        if (state == null) return false;
        state.refresh(level.getServer().getTickCount());
        return state.waiting.containsKey(mob.getUUID());
    }

    public static void cancel(PathfinderMob mob) {
        if (mob.level() instanceof ServerLevel level) {
            State state = STATES.get(level.getServer());
            if (state != null) state.waiting.remove(mob.getUUID());
        }
    }

    public static Stats stats(MinecraftServer server) {
        State state = STATES.get(server);
        if (state == null) return new Stats(0, 0, 0, 0, 0, 0, 0);
        state.refresh(server.getTickCount());
        return new Stats(state.granted, state.deferred, state.decisionNanos, state.peakDecisionNanos,
                state.waiting.size(), state.tickGranted, state.tickNanos);
    }

    public static void clear(MinecraftServer server) { STATES.remove(server); }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof PathfinderMob mob) cancel(mob);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = STATES.get(level.getServer());
        if (state != null) state.waiting.values().removeIf(request -> {
            PathfinderMob mob = request.mob.get(); return mob == null || mob.level() == level;
        });
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { clear(event.getServer()); }
}
