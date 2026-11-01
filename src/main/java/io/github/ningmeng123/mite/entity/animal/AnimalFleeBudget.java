package io.github.ningmeng123.mite.entity.animal;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Fair, main-thread escape queries; one fresh victim cannot unleash an entire herd's searches. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class AnimalFleeBudget {
    public static final int QUERIES_PER_TICK=4;
    public static final int URGENT_QUERIES_PER_TICK=1;
    private static final long NANOS_PER_TICK=2_000_000L;
    private static final Map<MinecraftServer,State> STATES=new IdentityHashMap<>();
    private static final Map<Mob,Stimulus> STIMULI=new WeakHashMap<>();
    private AnimalFleeBudget() {}

    private static final class Stimulus {
        long generation,alarmGeneration,at;
    }

    /** Damage epochs are transient and never retain their weak animal key or a level. */
    static void damaged(Mob mob) {
        Stimulus stimulus=STIMULI.computeIfAbsent(mob,ignored->new Stimulus());
        ++stimulus.generation;stimulus.at=mob.level().getGameTime();
    }
    static long generation(Mob mob) {
        Stimulus stimulus=STIMULI.get(mob);return stimulus==null?0L:stimulus.generation;
    }
    /** A new real herd warning is independent of its possibly unchanged saved expiry. */
    static void alarmed(Mob mob) {
        ++STIMULI.computeIfAbsent(mob,ignored->new Stimulus()).alarmGeneration;
    }
    static long alarmGeneration(Mob mob) {
        Stimulus stimulus=STIMULI.get(mob);return stimulus==null?0L:stimulus.alarmGeneration;
    }
    static boolean freshDamage(Mob mob) {
        Stimulus stimulus=STIMULI.get(mob);
        return stimulus!=null && mob.level().getGameTime()-stimulus.at<=20L;
    }

    public record Stats(long nativeQueries,long urgentQueries,long deferred,long backoff,int queued,
                        int tickNativeQueries,int tickUrgentQueries,int peakTickNativeQueries) {}

    static final class State {
        final MinecraftServer server;
        final ArrayDeque<AnimalFleeSession> queue=new ArrayDeque<>();
        final Map<PathfinderMob,Boolean> queried=new IdentityHashMap<>();
        int tick=Integer.MIN_VALUE,regular,urgent,peak;
        long nanos,nativeQueries,urgentQueries,deferred,backoff;
        State(MinecraftServer server) {this.server=server;}
        void refresh() {
            int now=server.getTickCount();
            if(now!=tick) {tick=now;regular=urgent=0;nanos=0L;queried.clear();}
        }
        boolean available(AnimalFleeSession session) {
            return !queried.containsKey(session.mob) && (session.urgent && urgent<URGENT_QUERIES_PER_TICK
                    || regular<QUERIES_PER_TICK && nanos<NANOS_PER_TICK);
        }
        void query(AnimalFleeSession session) {
            boolean emergency=session.urgent && urgent<URGENT_QUERIES_PER_TICK;
            if(emergency) {++urgent;++urgentQueries;} else ++regular;
            session.urgent=false;queried.put(session.mob,true);++nativeQueries;peak=Math.max(peak,regular+urgent);
        }
        void deferred(AnimalFleeSession session) {
            if(session.deferredAt!=tick) {session.deferredAt=tick;++deferred;}
        }
        void advance(AnimalFleeSession session) {
            if(session.lastAdvancedAt==tick || !available(session)) {deferred(session);return;}
            session.lastAdvancedAt=tick;
            long started=System.nanoTime();
            try {session.advance(this);} finally {nanos+=Math.max(0L,System.nanoTime()-started);}
        }
    }

    static void request(AnimalFleeSession session) {
        if(!(session.mob.level() instanceof ServerLevel level) || !session.pending()) return;
        State state=STATES.computeIfAbsent(level.getServer(),State::new);state.refresh();
        session.requestedAt=state.tick;session.active=true;
        if(!session.queued) {session.queued=true;state.queue.addLast(session);}
        // A genuinely hit animal has a bounded reserved first chance. Ordinary herd
        // warnings share FIFO admission rather than all claiming this exception.
        if((state.queue.peekFirst()==session || session.urgent && state.urgent<URGENT_QUERIES_PER_TICK)
                && session.lastAdvancedAt!=state.tick && state.available(session)) {
            state.queue.remove(session);session.queued=false;state.advance(session);
            if(session.active && session.pending()) {session.queued=true;state.queue.addLast(session);}
        } else state.deferred(session);
    }

    static void cancel(AnimalFleeSession session) {
        if(session==null) return;
        session.active=false;
        if(session.mob.level() instanceof ServerLevel level) {
            State state=STATES.get(level.getServer());if(state!=null) state.queue.remove(session);
        }
        session.queued=false;
    }

    static void countBackoff(PathfinderMob mob) {
        if(mob.level() instanceof ServerLevel level) STATES.computeIfAbsent(level.getServer(),State::new).backoff++;
    }

    public static void pump(MinecraftServer server) {
        State state=STATES.get(server);if(state==null) return;state.refresh();
        if(!MiteConfig.animalSearchBudgetEnabled()) {clear(server);return;}
        int remaining=state.queue.size();
        while(remaining-->0 && !state.queue.isEmpty() && state.regular<QUERIES_PER_TICK && state.nanos<NANOS_PER_TICK) {
            AnimalFleeSession session=state.queue.removeFirst();session.queued=false;
            if(!session.active || !session.mob.isAlive() || session.mob.isRemoved()
                    || !(session.mob.level() instanceof ServerLevel level) || level.getServer()!=server
                    || state.tick-session.requestedAt>=2) {session.active=false;continue;}
            state.advance(session);
            if(session.active && session.pending()) {session.queued=true;state.queue.addLast(session);}
        }
    }

    public static Stats stats(MinecraftServer server) {
        State state=STATES.get(server);if(state==null) return new Stats(0,0,0,0,0,0,0,0);state.refresh();
        return new Stats(state.nativeQueries,state.urgentQueries,state.deferred,state.backoff,state.queue.size(),
                state.regular+state.urgent,state.urgent,state.peak);
    }
    private static void clear(MinecraftServer server) {
        State state=STATES.remove(server);
        if(state!=null) for(AnimalFleeSession session:state.queue) {session.active=false;session.queued=false;}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {pump(event.getServer());}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if(event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        STIMULI.remove(mob);
        if(event.getLevel() instanceof ServerLevel level) {
            State state=STATES.get(level.getServer());
            if(state!=null) state.queue.removeIf(session->{
                if(session.mob!=mob) return false;session.active=false;session.queued=false;return true;
            });
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        State state=STATES.get(level.getServer());
        if(state!=null) state.queue.removeIf(session->{
            if(session.mob.level()!=level) return false;session.active=false;session.queued=false;return true;
        });
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {clear(event.getServer());}
}
