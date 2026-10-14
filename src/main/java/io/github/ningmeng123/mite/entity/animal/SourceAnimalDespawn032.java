package io.github.ningmeng123.mite.entity.animal;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Bat;
import io.github.ningmeng123.mite.entity.R196Wolf;
import io.github.ningmeng123.mite.ranged.ArrowRecovery;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Source nn/og/rp/sf/rx despawn gates, with modern persistence and collision shape adapters. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class SourceAnimalDespawn032 {
    public static final String STATE="mite_animal_despawn_032";
    private SourceAnimalDespawn032() {}
    public static boolean managed(Mob mob) {
        return R196AnimalAi032.managed(mob) || mob.getType()==EntityType.BAT || mob.getType()==EntityType.SQUID || mob instanceof R196Bat;
    }
    public static CompoundTag state(Mob mob) {
        var data=mob.getPersistentData();
        if(!data.contains(STATE)) { var created=new CompoundTag(); created.putInt("version",1); created.putInt("counter",0); data.put(STATE,created); }
        return data.getCompound(STATE);
    }
    public static int counter(Mob mob) { return state(mob).getInt("counter"); }
    public static void counter(Mob mob,int value) { state(mob).putInt("counter",value); }
    /** nn.refreshDespawnCounter lowers the counter; it never shortens an existing longer grace. */
    public static void refresh(Mob mob,int value) {
        if(managed(mob) && !mob.level().isClientSide) counter(mob,Math.min(counter(mob),value));
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event) {
        if(event.getEntity() instanceof Mob mob && !mob.level().isClientSide && managed(mob) && mob.isAlive()) counter(mob,counter(mob)+1);
    }
    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event) {
        if(event.getEntity().level().isClientSide || event.getNewDamage()<=0 || !(event.getEntity() instanceof Mob mob)
                || !managed(mob) || R196AnimalAi032.managed(mob)) return;
        refresh(mob,-1200);
        if(event.getSource().getEntity() instanceof Player) refresh(mob,-9600);
        if(event.getSource().getEntity() instanceof Mob attacker) refresh(attacker,-1200);
    }
    public static boolean persistent(Mob mob) {
        // These named protections retain modern player-owned entities and mounts.
        return mob.isPersistenceRequired() || mob.hasCustomName() || mob.isLeashed() || mob.requiresCustomPersistence()
                || mob.isVehicle() || mob instanceof TamableAnimal pet && pet.isTame()
                || mob instanceof AbstractHorse horse && horse.isTamed();
    }
    public static Player nearestPlayer(Mob mob) {
        Player nearest=null; double shortest=Double.POSITIVE_INFINITY;
        for(Player player:mob.level().players()) {
            // The source does not require alive or vulnerable: creative and dead players still protect animals.
            if(player.isSpectator()) continue;
            double distance=mob.distanceToSqr(player);
            if(distance<shortest) { shortest=distance; nearest=player; }
        }
        return nearest;
    }
    public static int sourceHour(long dayTime) { return (int)Math.floorMod(dayTime+6000L,24000L)/1000; }
    public static double weightedDistance(Mob mob,Player player) {
        double dx=player.getX()-mob.getX(),dy=player.getY()-mob.getY(),dz=player.getZ()-mob.getZ();
        if(sourceHour(mob.level().getDayTime())!=5) dy*=2;
        return dx*dx+dy*dy+dz*dz;
    }
    public static boolean containsItems(Mob mob) {
        if(mob.hasData(ArrowRecovery.CONTAINED) && !mob.getData(ArrowRecovery.CONTAINED).isEmpty()) return true;
        // Animals currently have no valuable-item acquisition path. Reserve the source inventory guard
        // in their persistent state, independently of equipped items or the passive production counter.
        var contents=state(mob).get("contained_items");
        return contents instanceof ListTag list && !list.isEmpty() || contents instanceof IntArrayTag array && array.size()>0;
    }
    /** og.tryDespawnEntity performs species/view gates only after distance and contained-items checks. */
    public static boolean tryDespawn(Mob mob) {
        if(mob.level().isClientSide || !managed(mob) || !mob.isAlive()) return false;
        if(persistent(mob)) { counter(mob,0); return false; }
        if(counter(mob)<200) return false;
        Player nearest=nearestPlayer(mob);
        if(nearest==null) return false;
        if(weightedDistance(mob,nearest)>256D && !containsItems(mob) && speciesAllows(mob)) {
            mob.discard(); return true;
        }
        // Intentional source ordering: this replaces a species/view refresh during a rejected attempt.
        counter(mob,(int)(Math.random()*100D)-50);
        return false;
    }
    public static boolean speciesAllows(Mob mob) {
        boolean hell=mob instanceof R196Wolf wolf && wolf.miteKind()==MobKind.HELLHOUND;
        if(mob instanceof Wolf wolf && (mob.getPersistentData().getBoolean("mite_wolf_witch_ally_032")
                || !hell && mob.tickCount<=2400 || wolf.isTame())) return false;
        if((mob instanceof Ocelot || mob instanceof Cat) && (mob.tickCount<=2400 || mob instanceof Cat cat && cat.isTame())) return false;
        if(mob instanceof Animal && !hell) {
            if(counter(mob)<800) return false;
            Player nearest=nearestPlayer(mob);
            if(nearest!=null && mob.distanceToSqr(nearest)<1024D*1024D || oakFenceNear(mob)) { counter(mob,0); return false; }
            if(counter(mob)<=2400) return false;
        }
        for(Player player:mob.level().players()) if(inView(mob,player)) {
            refresh(mob,mob.getType()==EntityType.SQUID?-9600:-400); return false;
        }
        return true;
    }
    public static boolean oakFenceNear(Mob mob) {
        BlockPos center=mob.blockPosition();
        for(int dx=-16;dx<=16;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-16;dz<=16;dz++) {
            BlockPos pos=center.offset(dx,dy,dz);
            if(mob.level().hasChunkAt(pos) && mob.level().getBlockState(pos).is(Blocks.OAK_FENCE)) return true;
        }
        return false;
    }
    public static boolean inView(Mob mob,Player player) {
        if(player.isSpectator()) return false;
        double distance=mob.distanceToSqr(player), limit=mob.getBoundingBox().getSize()*64D*1.25D;
        if(distance>4096D && !(distance<limit*limit)) return false;
        Vec3 eye=player.getEyePosition(),foot=mob.position();
        Vec3[] destinations={foot.add(0,mob.getBbHeight()*.75F,0),foot.add(0,.1D,0),foot.add(0,mob.getBbHeight()+.5D,0)};
        for(Vec3 destination:destinations) if(visionRay(mob,eye,destination)) return true;
        return visionRay(mob,eye.add(0,1,0),destinations[2]);
    }
    /** R196 RaycastPolicies.for_vision(false), applied to modern block shapes and loaded height bounds. */
    public static boolean visionRay(Mob mob,Vec3 origin,Vec3 destination) {
        // Source Raycast.performVsBlocks casts four epsilon-shifted origins, taking any obstruction.
        for(Vec3 shift:new Vec3[]{new Vec3(-.0001,0,0),new Vec3(.0001,0,0),new Vec3(0,0,-.0001),new Vec3(0,0,.0001)}) {
            ClipContext context=new ClipContext(origin.add(shift),destination,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mob) {
                private int impedance;
                @Override public VoxelShape getBlockShape(BlockState state,BlockGetter level,BlockPos pos) {
                    if(!mob.level().hasChunkAt(pos)) return Shapes.block();
                    var block=state.getBlock();
                    if(state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || block instanceof StainedGlassBlock
                            || block instanceof StainedGlassPaneBlock || block instanceof IceBlock || block instanceof FenceBlock
                            || block instanceof FenceGateBlock || block instanceof IronBarsBlock || block instanceof DoorBlock
                            || block instanceof TrapDoorBlock || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)) return Shapes.empty();
                    if(state.is(BlockTags.LEAVES) || Livestock032.shortVegetation(state) || block instanceof CropBlock
                            || state.is(BlockTags.FLOWERS) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.VINE)) {
                        impedance+=50; if(impedance<100) return Shapes.empty();
                    }
                    return super.getBlockShape(state,level,pos);
                }
            };
            if(mob.level().clip(context).getType()!=HitResult.Type.MISS) return false;
        }
        return true;
    }
}
