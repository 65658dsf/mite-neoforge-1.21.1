package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Main-thread pursuit checkpoints; only the engine's native A* bookkeeping runs on a worker. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class AsyncMeleePursuit {
    private static final Map<PathfinderMob,State> STATES=new WeakHashMap<>();
    private static final ClassValue<Boolean> DEFAULT_CALLBACKS=new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("onPathfindingStart").getDeclaringClass()==Mob.class
                        && type.getMethod("onPathfindingDone").getDeclaringClass()==Mob.class;
            } catch(ReflectiveOperationException ignored) { return false; }
        }
    };

    /** A control signal caught exclusively at the public high-level pursuit boundary. */
    static final class Pending extends RuntimeException {
        private Pending() { super(null,null,false,false); }
    }
    private static final Pending PENDING=new Pending();
    private AsyncMeleePursuit() {}

    static Pending pendingSignal() { return PENDING; }

    static final class State {
        final Fingerprint fingerprint;
        final Map<Query,Memo> completed=new LinkedHashMap<>();
        @Nullable AsyncPathSearchEngine.Task task;
        @Nullable Query query;
        @Nullable LoadedPathNavigationRegion region;
        boolean pending,disabled;
        final long createdAt;
        long requestedAt;
        State(PathfinderMob mob,LivingEntity target,boolean vanilla) {
            fingerprint=new Fingerprint(mob,target,vanilla);
            createdAt=requestedAt=mob.level().getGameTime();
        }
        void cancel() {
            if(task!=null) task.cancel();
            task=null; query=null; region=null; completed.clear(); pending=false; disabled=true;
        }
    }

    private record Query(Set<BlockPos> targets,int nodes,float range,int accuracy,float multiplier) {}
    private record Memo(@Nullable Path path,LoadedPathNavigationRegion region) {}

    @Nullable static State begin(PathfinderMob mob,LivingEntity target,boolean vanilla) {
        State previous=STATES.get(mob);
        if(previous!=null && previous.disabled) return previous;
        if(!eligible(mob,target)) { if(previous!=null) previous.cancel(); return previous; }
        if(previous!=null && mob.level().getGameTime()-previous.createdAt>200L) { previous.cancel(); return previous; }
        if(previous!=null && !previous.fingerprint.matches(mob,target,vanilla)) {
            // An obsolete or unsupported request takes the native synchronous path this time.
            previous.cancel();
            return previous;
        }
        State state=previous==null?new State(mob,target,vanilla):previous;
        state.requestedAt=mob.level().getGameTime();
        STATES.put(mob,state);
        return state;
    }

    private static boolean eligible(PathfinderMob mob,LivingEntity target) {
        return MiteConfig.asyncPathfindingEnabled() && mob.level() instanceof ServerLevel
                && mob.isAlive() && !mob.isRemoved() && mob.getTarget()==target
                && target.isAlive() && target.level()==mob.level() && MeleePursuitPaths.applies(mob)
                && !mob.isPassenger() && mob.getNavigation().isDone()
                && mob.getDeltaMovement().horizontalDistanceSqr()<.0025D
                && (mob.onGround() || Math.abs(mob.getDeltaMovement().y)<.05D)
                && DEFAULT_CALLBACKS.get(mob.getClass());
    }

    static boolean pending(PathfinderMob mob) {
        State state=STATES.get(mob);
        return state!=null && state.pending;
    }

    @Nullable static State state(PathfinderMob mob) { return STATES.get(mob); }

    static void complete(PathfinderMob mob,@Nullable State state) {
        if(state!=null && STATES.get(mob)==state) { STATES.remove(mob); state.cancel(); }
    }

    static void cancel(PathfinderMob mob) {
        State state=STATES.remove(mob);
        if(state!=null) state.cancel();
    }

    @Nullable static Path query(PathfinderMob mob,LoadedPathNavigationRegion region,NodeEvaluator evaluator,
            Set<BlockPos> targets,int maxNodes,float range,int accuracy,float multiplier,
            Runnable countSearch,Supplier<Path> synchronous) {
        State state=STATES.get(mob);
        if(state==null || state.disabled) { return synchronous.get(); }
        Query query=new Query(Collections.unmodifiableSet(new LinkedHashSet<>(targets)),maxNodes,range,accuracy,multiplier);
        Memo memo=state.completed.get(query);
        if(memo!=null) {
            if(memo.region().terrainUnchanged()) return memo.path();
            state.cancel(); return synchronous.get();
        }
        if(state.task!=null) {
            if(!query.equals(state.query)) {
                state.cancel(); return synchronous.get();
            }
            if(!state.task.done()) throw PENDING;
            if(state.task.failure()!=null || state.region==null || !state.region.terrainUnchanged()
                    || !state.fingerprint.matchesCurrent(mob)) {
                state.cancel(); return synchronous.get();
            }
            Path path=state.task.result();
            state.completed.put(query,new Memo(path,state.region));
            state.task=null; state.query=null; state.region=null;
            return path;
        }
        if(state.completed.size()>=3) {
            state.cancel(); return synchronous.get();
        }
        AsyncPathSearchEngine.Task task=AsyncPathSearchEngine.submit((ServerLevel)mob.level(),region,mob,evaluator,
                query.targets(),maxNodes,range,accuracy,multiplier,() -> state.fingerprint.matchesCurrent(mob));
        if(task==null) {
            if(MiteConfig.pursuitSearchBudgetEnabled()
                    && AsyncPathSearchEngine.saturated(((ServerLevel)mob.level()).getServer())) {
                state.pending=true; throw PENDING;
            }
            state.cancel(); return synchronous.get();
        }
        countSearch.run();
        state.task=task; state.query=query; state.region=region; state.pending=true;
        throw PENDING;
    }

    /** No strong entity, navigation or world references survive in a completed request fingerprint. */
    private static final class Fingerprint {
        final WeakReference<Level> level;
        final WeakReference<PathNavigation> navigation;
        final int targetId,maxFall;
        final UUID targetUuid;
        final BlockPos destination,restriction;
        final Vec3 origin;
        final float width,height,step,targetWidth,targetHeight,restrictionRadius;
        final boolean vanilla,floats,opens,passes,onGround,inWater,inLava,descending;
        final int[] malus;
        final ItemStack[] gear;
        Fingerprint(PathfinderMob mob,LivingEntity target,boolean vanilla) {
            level=new WeakReference<>(mob.level()); navigation=new WeakReference<>(mob.getNavigation());
            targetId=target.getId(); targetUuid=target.getUUID(); destination=target.blockPosition().immutable();
            origin=mob.position(); width=mob.getBbWidth(); height=mob.getBbHeight(); step=mob.maxUpStep();
            maxFall=mob.getMaxFallDistance(); targetWidth=target.getBbWidth(); targetHeight=target.getBbHeight();
            restriction=mob.getRestrictCenter().immutable(); restrictionRadius=mob.getRestrictRadius();
            this.vanilla=vanilla;
            var nodes=mob.getNavigation().getNodeEvaluator();
            floats=nodes.canFloat(); opens=nodes.canOpenDoors(); passes=nodes.canPassDoors();
            onGround=mob.onGround(); inWater=mob.isInWater(); inLava=mob.isInLava(); descending=mob.isDescending();
            malus=malus(mob);
            EquipmentSlot[] slots=EquipmentSlot.values(); gear=new ItemStack[slots.length];
            for(int i=0;i<slots.length;i++) gear[i]=mob.getItemBySlot(slots[i]).copy();
        }
        boolean matchesCurrent(PathfinderMob mob) {
            LivingEntity target=mob.getTarget();
            return target!=null && eligible(mob,target) && matches(mob,target,vanilla);
        }
        boolean matches(PathfinderMob mob,LivingEntity target,boolean vanilla) {
            var nodes=mob.getNavigation().getNodeEvaluator();
            if(level.get()!=mob.level() || navigation.get()!=mob.getNavigation() || this.vanilla!=vanilla
                    || target.getId()!=targetId || !target.getUUID().equals(targetUuid)
                    || !target.blockPosition().equals(destination) || !mob.position().equals(origin)
                    || mob.getBbWidth()!=width || mob.getBbHeight()!=height || mob.maxUpStep()!=step
                    || mob.getMaxFallDistance()!=maxFall || target.getBbWidth()!=targetWidth || target.getBbHeight()!=targetHeight
                    || !mob.getRestrictCenter().equals(restriction) || mob.getRestrictRadius()!=restrictionRadius
                    || nodes.canFloat()!=floats || nodes.canOpenDoors()!=opens || nodes.canPassDoors()!=passes
                    || mob.onGround()!=onGround || mob.isInWater()!=inWater || mob.isInLava()!=inLava || mob.isDescending()!=descending
                    || !Arrays.equals(malus,malus(mob))) return false;
            EquipmentSlot[] slots=EquipmentSlot.values();
            for(int i=0;i<slots.length;i++) {
                ItemStack current=mob.getItemBySlot(slots[i]);
                if(current.getCount()!=gear[i].getCount() || !ItemStack.isSameItemSameComponents(current,gear[i])) return false;
            }
            return true;
        }
        private static int[] malus(PathfinderMob mob) {
            PathType[] types=PathType.values(); int[] result=new int[types.length];
            for(int i=0;i<types.length;i++) result[i]=Float.floatToIntBits(mob.getPathfindingMalus(types[i]));
            return result;
        }
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if(!event.getLevel().isClientSide && event.getEntity() instanceof PathfinderMob mob) cancel(mob);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel().isClientSide()) return;
        var entries=STATES.entrySet().iterator();
        while(entries.hasNext()) {
            var entry=entries.next();
            if(entry.getValue().fingerprint.level.get()==event.getLevel()) { entry.getValue().cancel(); entries.remove(); }
        }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        var entries=STATES.entrySet().iterator();
        while(entries.hasNext()) {
            var entry=entries.next(); PathfinderMob mob=entry.getKey(); State state=entry.getValue();
            if(state.fingerprint.level.get()!=level) continue;
            if(level.getGameTime()-state.createdAt>200L) state.cancel();
            if(mob==null || mob.isRemoved() || !mob.isAlive() || mob.getTarget()==null
                    || level.getGameTime()-state.requestedAt>200L) { state.cancel(); entries.remove(); }
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        var entries=STATES.entrySet().iterator();
        while(entries.hasNext()) {
            var entry=entries.next(); Level level=entry.getValue().fingerprint.level.get();
            if(level instanceof ServerLevel serverLevel && serverLevel.getServer()==event.getServer()) {
                entry.getValue().cancel(); entries.remove();
            }
        }
    }
}
