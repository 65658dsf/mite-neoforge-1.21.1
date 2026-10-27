package io.github.ningmeng123.mite.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.levelgen.Heightmap;

/** Low-frequency cave discovery only through proven, already loaded walking routes. @author NingMeng123 */
public final class CavePlayerDiscovery {
    public static final int MAX_PROBES_PER_SERVER_TICK = 2;
    public static final int PROBE_INTERVAL_TICKS = 40;
    public static final int RETRY_STAGGER_TICKS = 10;
    public static final double MAX_PLAYER_RANGE = 24D;
    private static final double MIN_COVER_DEPTH = 4D;
    private static final int MAX_WAITING_MOBS = 1024;
    private static final Map<PathfinderMob, ProbeState> MOBS = new WeakHashMap<>();
    private static final Map<MinecraftServer, ServerBudget> SERVERS = new WeakHashMap<>();

    // Values contain no entities, levels or server references that could retain their weak key.
    private static final class ProbeState {
        long nextProbeAt = Long.MIN_VALUE, probes, cacheReuses, budgetDeferrals;
        int cursor, lastCandidateId = -1;
    }

    private static final class ServerBudget {
        int tick = Integer.MIN_VALUE, used, peak;
        long probes, nativeQueries, cacheReuses, budgetDeferrals;
        final LinkedHashMap<Integer, Waiting> waiting = new LinkedHashMap<>();

        int oldestReady(int now) {
            var entries = waiting.entrySet().iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                if (now >= entry.getValue().expiresAt()) { entries.remove(); continue; }
                if (now >= entry.getValue().readyAt()) return entry.getKey();
            }
            return -1;
        }
    }

    private record Waiting(int readyAt, int expiresAt) {}

    public record ProbeStats(long probes, long nativeQueries, long cacheReuses, long budgetDeferrals,
            int probesThisTick, int peakProbesPerTick) {}

    public record MobProbeStats(long probes, long cacheReuses, long budgetDeferrals, long nextProbeAt,
            int nextCandidate, int lastCandidateId) {}

    private CavePlayerDiscovery() {}

    public static ProbeStats probeStats(ServerLevel level) {
        ServerBudget budget = SERVERS.get(level.getServer());
        return budget == null ? new ProbeStats(0, 0, 0, 0, 0, 0)
                : new ProbeStats(budget.probes, budget.nativeQueries, budget.cacheReuses, budget.budgetDeferrals,
                        budget.tick == level.getServer().getTickCount() ? budget.used : 0, budget.peak);
    }

    public static MobProbeStats probeStats(PathfinderMob mob) {
        ProbeState state = MOBS.get(mob);
        return state == null ? new MobProbeStats(0, 0, 0, Long.MIN_VALUE, 0, -1)
                : new MobProbeStats(state.probes, state.cacheReuses, state.budgetDeferrals, state.nextProbeAt,
                        state.cursor, state.lastCandidateId);
    }

    /** Uses only a loaded chunk's heightmap; no absolute Y cutoff or vertical block scan. */
    public static boolean underground(ServerLevel level, LivingEntity entity) {
        var pos = entity.blockPosition();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        if (!level.dimensionType().hasSkyLight()) return true;
        int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() & 15, pos.getZ() & 15) + 1;
        return surface - entity.getEyeY() >= MIN_COVER_DEPTH;
    }

    /** Never assigns targets, starts navigation, breaks blocks or treats a refused budget as failure. */
    @Nullable public static Player find(PathfinderMob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.getTarget() != null
                || !(mob.getNavigation() instanceof GroundPathNavigation) || !R196MonsterAi.managed(mob)
                || mob instanceof Creeper || mob instanceof Witch || mob.getMainHandItem().getItem() instanceof BowItem) return null;
        ProbeState state = MOBS.computeIfAbsent(mob, ignored -> new ProbeState());
        long now = level.getGameTime();
        if (now < state.nextProbeAt) return null;
        if (SourceMeleeGoal.first(mob) == null || !underground(level, mob)) {
            state.nextProbeAt = now + interval(mob);
            cancelWaiting(level.getServer(), mob.getId());
            return null;
        }
        double range = Math.min(MAX_PLAYER_RANGE, SourceMonsterTargeting.effectiveRange(mob));
        var candidates = new ArrayList<Player>();
        for (Player player : level.players()) {
            if (mob.distanceToSqr(player) <= range * range && SourceMonsterTargeting.suitable(mob, player)
                    && underground(level, player)) candidates.add(player);
        }
        if (candidates.isEmpty()) {
            state.nextProbeAt = now + interval(mob);
            cancelWaiting(level.getServer(), mob.getId());
            return null;
        }
        candidates.sort(Comparator.<Player>comparingDouble(mob::distanceToSqr).thenComparingInt(Player::getId));
        int selected = Math.floorMod(state.cursor, candidates.size());
        Player candidate = candidates.get(selected);
        // Ordinary visible discovery never needs a hidden-route permit.
        if (SourceMonsterTargeting.canSee(mob, candidate, false)) {
            cancelWaiting(level.getServer(), mob.getId());
            return candidate;
        }
        ServerBudget budget = SERVERS.computeIfAbsent(level.getServer(), ignored -> new ServerBudget());
        int tick = level.getServer().getTickCount();
        if (budget.tick != tick) { budget.tick = tick; budget.used = 0; }
        int priority = budget.oldestReady(tick);
        if (budget.used >= MAX_PROBES_PER_SERVER_TICK || priority != -1 && priority != mob.getId()) {
            ++budget.budgetDeferrals; ++state.budgetDeferrals;
            int delay = 2 + Math.floorMod(mob.getId(), 5);
            state.nextProbeAt = now + delay;
            if (budget.waiting.containsKey(mob.getId()) || budget.waiting.size() < MAX_WAITING_MOBS)
                budget.waiting.put(mob.getId(), new Waiting(tick + delay, tick + delay + 8));
            return null;
        }
        budget.waiting.remove(mob.getId());
        ++budget.used;
        state.cursor = (selected + 1) % candidates.size();
        state.lastCandidateId = candidate.getId();
        state.nextProbeAt = now + interval(mob);
        LivingEntity actualTarget = SourceMonsterTargeting.mountedTarget(candidate);
        if (!SourceMonsterTargeting.suitable(mob, actualTarget)) { --budget.used; return null; }
        long before = MeleePursuitPaths.searchStats(mob).searches();
        var path = MeleePursuitPaths.find(mob, actualTarget, null);
        long queries = MeleePursuitPaths.searchStats(mob).searches() - before;
        if (queries > 0) {
            ++budget.probes; ++state.probes; budget.nativeQueries += queries;
            budget.peak = Math.max(budget.peak, budget.used);
        } else {
            // A shared cached failure/success did no native search, so it consumes no search permit.
            --budget.used; ++budget.cacheReuses; ++state.cacheReuses;
        }
        if (MeleePursuitPaths.completeRouteClear(mob, path, actualTarget)) return candidate;
        // A short-lived successful cache can outlive a newly placed wall beyond its next six
        // nodes. Discovery validates the whole remaining route before establishing new aggro.
        if (MeleePursuitPaths.reaches(mob, path, actualTarget)) MeleePursuitPaths.invalidate(mob);
        return null;
    }

    private static int interval(PathfinderMob mob) {
        return PROBE_INTERVAL_TICKS + Math.floorMod(mob.getId(), RETRY_STAGGER_TICKS);
    }

    private static void cancelWaiting(MinecraftServer server, int mobId) {
        ServerBudget budget = SERVERS.get(server);
        if (budget != null) budget.waiting.remove(mobId);
    }
}
