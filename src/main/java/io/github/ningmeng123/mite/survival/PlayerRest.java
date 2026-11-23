package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.world.LunarWeather;
import io.github.ningmeng123.mite.worldgen.R196OutdoorChecks;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Source shelter, pathable threats, hunger and simulated sleeping metabolism. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PlayerRest {
    public static final String COUNTER = "mite_sleep_consciousness";
    private PlayerRest() {}
    public static int counter(Player player) { return Math.clamp(player.getPersistentData().getInt(COUNTER), 0, 110); }
    public static boolean allInBed(ServerLevel level) {
        boolean one = false;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (!player.isSleeping()) return false;
            one = true;
        }
        return one;
    }
    public static boolean allDeeplyAsleep(List<ServerPlayer> players) {
        boolean one = false;
        for (ServerPlayer player : players) {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (!player.isSleepingLongEnough()) return false;
            one = true;
        }
        return one;
    }
    public static boolean canForward(ServerLevel level) {
        return level.dimension().equals(Level.OVERWORLD) && !LunarWeather.bloodPeriod(level)
                && RestRules.sleepingTime(level.getDayTime()) && allInBed(level);
    }
    public static boolean hostile(Mob mob) {
        return io.github.ningmeng123.mite.worldgen.R196MobSpawning.category(mob)
                == io.github.ningmeng123.mite.rules.R196SpawnRules.Category.MONSTER;
    }
    public static Mob threat(ServerPlayer player, BlockPos bed) {
        Mob closest = null; double best = Double.MAX_VALUE;
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class,
                new AABB(bed.getX()-8, bed.getY()-5, bed.getZ()-8, bed.getX()+8, bed.getY()+5, bed.getZ()+8), m -> m.isAlive() && hostile(m))) {
            Path path = mob.getNavigation().createPath(bed, 1);
            if (path == null || path.getEndNode() == null) continue;
            var end = path.getEndNode();
            if (Math.abs(end.x-bed.getX())>1 || Math.abs(end.y-bed.getY())>1 || Math.abs(end.z-bed.getZ())>1) continue;
            double distance = mob.blockPosition().distSqr(bed);
            if (distance < best) { closest=mob; best=distance; }
        }
        return closest;
    }
    public static boolean digging(ServerPlayer player, BlockPos bed) {
        return player.serverLevel().getEntitiesOfClass(PathfinderMob.class,
                new AABB(bed.getX()-8,bed.getY()-5,bed.getZ()-8,bed.getX()+8,bed.getY()+5,bed.getZ()+8), mob -> mob instanceof net.minecraft.world.entity.monster.Zombie)
                .stream().anyMatch(mob -> { var goal=R196DiggerGoal.first(mob); return goal!=null && goal.targetBlock()!=null && goal.isDestroying(goal.targetBlock()); });
    }
    public static String problem(ServerPlayer player, BlockPos bed, boolean starting) {
        if (threat(player, bed)!=null) return "mobs";
        if (starting && R196OutdoorChecks.outdoors(player.serverLevel(),bed)) return "shelter";
        if (digging(player,bed)) return "digging";
        if (!FoodConsumption.hasEnergy(player)) return "hungry";
        if (starting && player.hasEffect(MobEffects.POISON)) return "poison";
        return null;
    }
    private static void message(ServerPlayer player,String problem) {
        player.displayClientMessage(Component.translatable("message.mite.sleep_"+problem),true);
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void start(CanPlayerSleepEvent event) {
        if (!event.getLevel().dimension().equals(Level.OVERWORLD) || !event.getEntity().isAlive()) return;
        var old=event.getProblem();
        if (old!=null && old!=Player.BedSleepingProblem.NOT_POSSIBLE_NOW && old!=Player.BedSleepingProblem.NOT_SAFE) return;
        String problem=problem(event.getEntity(),event.getPos(),true);
        if(problem==null) event.setProblem(null);
        else { event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM); message(event.getEntity(),problem); }
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void continuing(CanContinueSleepingEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(Level.OVERWORLD)) return;
        var bed=player.getSleepingPos();
        if(bed.isEmpty()) return;
        if(event.getProblem()!=null && event.getProblem()!=Player.BedSleepingProblem.NOT_POSSIBLE_NOW) return;
        String problem=problem(player,bed.get(),false);
        if(problem!=null) { event.setContinueSleeping(false); message(player,problem); }
        else event.setContinueSleeping(true); // Lying in bed during daylight is allowed, without forwarding time.
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        advanceConsciousness(player,player.isSleeping() && canForward(player.serverLevel()));
    }
    public static void advanceConsciousness(ServerPlayer player,boolean falling) {
        int before=counter(player);
        int after=RestRules.consciousness(before,falling);
        if(falling && before==99) after=110;
        if(after!=before) {
            player.getPersistentData().putInt(COUNTER,after);
            io.github.ningmeng123.mite.network.SleepConsciousnessPayload.send(player,after);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void finish(SleepFinishedTimeEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !canForward(level)) return;
        int requested=RestRules.untilSunrise(level.getDayTime());
        int elapsed=simulate(level,requested);
        event.setTimeAddition(level.getDayTime()+elapsed);
    }
    public static int simulate(ServerLevel level,int ticks) {
        List<ServerPlayer> sleepers=level.players().stream().filter(p->p.isAlive()&&!p.isSpectator()).toList();
        return simulate(level,ticks,sleepers);
    }
    public static int simulate(ServerLevel level,int ticks,List<ServerPlayer> sleepers) {
        return simulate(level,ticks,sleepers,elapsed -> level.random.nextInt(1000)==0 && SleepThreats.interrupt(level,sleepers));
    }
    /** Server-side interruption policy; each callback follows one completed food-update tick. */
    public static int simulate(ServerLevel level,int ticks,List<ServerPlayer> sleepers,IntPredicate interruptionAtElapsed) {
        int elapsed=0;
        boolean interrupted=false;
        while(elapsed<ticks) {
            boolean woke=false;
            for(ServerPlayer player:sleepers) {
                player.getFoodData().tick(player);
                if(!FoodConsumption.hasEnergy(player) || !player.isSleeping()) {
                    if(player.isSleeping()) { player.stopSleepInBed(true,true); message(player,"hungry"); }
                    woke=true;
                }
            }
            elapsed++;
            if(woke || interruptionAtElapsed.test(elapsed)) { interrupted=true; break; }
        }
        if(elapsed==ticks && !interrupted && ticks>=6000) for(ServerPlayer player:sleepers)
            if(player.isAlive() && player.isSleeping())
                io.github.ningmeng123.mite.advancement.MiteAchievements.award(player,"wellRested");
        if(elapsed>=1000)for(ServerPlayer player:sleepers)if(player.isAlive()&&player.isSleeping())player.removeAllEffects();
        return elapsed;
    }
}
