package io.github.ningmeng123.mite.entity.animal;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Shared server-thread food/water work slices; quota exhaustion is waiting, never failure. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class LivestockSearchBudget {
    public static final int QUERIES_PER_TICK=4;
    public static final int CELLS_PER_SLICE=2048;
    public static final int CELLS_PER_TICK=32768;
    private static final long NANOS_PER_TICK=2_000_000L;
    private static final Map<MinecraftServer,State> STATES=new IdentityHashMap<>();
    private LivestockSearchBudget() {}

    public record Stats(long scanCells,long nativeQueries,long deferred,long backoff,int queued,
                        int tickNativeQueries,long tickScanCells) {}

    static final class State {
        final MinecraftServer server;
        final ArrayDeque<LivestockSearchSession> queue=new ArrayDeque<>();
        final Map<PathfinderMob,Boolean> queried=new IdentityHashMap<>();
        int tick=Integer.MIN_VALUE,queries;
        long cells,nanos,deadline,scanCells,nativeQueries,deferred,backoff;
        State(MinecraftServer server) { this.server=server; }
        void refresh() {
            int now=server.getTickCount();
            if(tick!=now) { tick=now; queries=0; cells=0; nanos=0; queried.clear(); }
        }
        boolean queryAvailable(PathfinderMob mob) { return queries<QUERIES_PER_TICK && !queried.containsKey(mob); }
        void countQuery(PathfinderMob mob) { queries++;nativeQueries++;queried.put(mob,true); }
        void countCell() { cells++;scanCells++; }
        boolean timeAvailable() { return System.nanoTime()<deadline; }
    }

    static void request(LivestockSearchSession session) {
        if(!(session.mob.level() instanceof ServerLevel level)) return;
        State state=STATES.computeIfAbsent(level.getServer(),State::new);
        state.refresh();session.requestedAt=state.tick;session.active=true;
        if(!session.queued && session.pending()) { session.queued=true;state.queue.addLast(session); }
    }

    static void pause(@Nullable LivestockSearchSession session) {
        if(session==null) return;
        session.active=false;
        if(session.mob.level() instanceof ServerLevel level) {
            State state=STATES.get(level.getServer());
            if(state!=null && session.queued) state.queue.remove(session);
        }
        session.queued=false;
        session.clearHints();
    }

    static void countBackoff(PathfinderMob mob) {
        if(mob.level() instanceof ServerLevel level) STATES.computeIfAbsent(level.getServer(),State::new).backoff++;
    }

    /** Repeated calls within one server tick share all query, cell and soft time limits. */
    public static void pump(MinecraftServer server) {
        State state=STATES.get(server);
        if(state==null) return;
        state.refresh();
        if(!MiteConfig.animalSearchBudgetEnabled()) { clear(server);return; }
        if(state.nanos>=NANOS_PER_TICK || state.cells>=CELLS_PER_TICK) return;
        long began=System.nanoTime();
        state.deadline=began+NANOS_PER_TICK-state.nanos;
        int remaining=state.queue.size();
        try {
            while(remaining-->0 && !state.queue.isEmpty() && state.cells<CELLS_PER_TICK
                    && state.nanos+System.nanoTime()-began<NANOS_PER_TICK) {
                LivestockSearchSession session=state.queue.removeFirst();session.queued=false;
                if(!session.active || !session.mob.isAlive() || session.mob.isRemoved()
                        || !(session.mob.level() instanceof ServerLevel level) || level.getServer()!=server
                        || state.tick-session.requestedAt>=2) { session.active=false;session.clearHints();continue; }
                if(session.lastSliceAt!=state.tick) {
                    session.lastSliceAt=state.tick;
                    session.advance(state,Math.min(CELLS_PER_SLICE,(int)(CELLS_PER_TICK-state.cells)));
                }
                if(session.active && session.pending()) { session.queued=true;state.queue.addLast(session); }
            }
        } finally { state.nanos+=Math.max(0L,System.nanoTime()-began); }
    }

    public static Stats stats(MinecraftServer server) {
        State state=STATES.get(server);
        if(state==null) return new Stats(0,0,0,0,0,0,0);
        state.refresh();
        return new Stats(state.scanCells,state.nativeQueries,state.deferred,state.backoff,state.queue.size(),state.queries,state.cells);
    }

    public static void clear(MinecraftServer server) {
        State state=STATES.remove(server);
        if(state!=null) for(LivestockSearchSession session:state.queue) {
            session.active=false;session.queued=false;session.clearHints();
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { pump(event.getServer()); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        State state=STATES.get(level.getServer());
        if(state!=null) state.queue.removeIf(session -> {
            if(session.mob.level()!=level) return false;
            session.active=false;session.queued=false;session.clearHints();return true;
        });
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { clear(event.getServer()); }
}
