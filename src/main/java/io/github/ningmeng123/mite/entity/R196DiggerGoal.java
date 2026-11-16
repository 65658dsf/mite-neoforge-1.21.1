package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.entity.ai.MonsterFarmland;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import io.github.ningmeng123.mite.registry.MiteMetalBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.rules.OreBlockRules;
import io.github.ningmeng123.mite.world.CollapsePhysics;
import io.github.ningmeng123.mite.world.CollapseSecondaryTriggers;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** EntityAnimalWatcher/EntityAIWatchAnimal with explicit modern height, material-family and grief safeguards. @author NingMeng123 */
public final class R196DiggerGoal extends Goal {
    private static final String SAVE = "mite_digging_031";
    private final PathfinderMob mob;
    private BlockPos target;
    private int progress = -1, cooldown = 40, pause;
    private boolean active;
    private boolean overheadDigging, started;
    private final ZombieRoutePlanner pursuit;
    public R196DiggerGoal(PathfinderMob mob) { this.mob=mob; pursuit=new ZombieRoutePlanner(mob,this);setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
    public static boolean watcher(PathfinderMob mob) {
        String source=io.github.ningmeng123.mite.worldgen.R196MobSpawning.sourceClass(mob);
        if(source==null || source.equals("tn")) return false; // Pigman executes oldAi, not its inherited selectors.
        for(var value:io.github.ningmeng123.mite.rules.R196MobRules.sourceData().getAsJsonArray("entities")) {
            var row=value.getAsJsonObject();
            if(source.equals(row.get("source_class").getAsString()))
                return row.getAsJsonArray("hierarchy").asList().stream().anyMatch(v->"EntityAnimalWatcher".equals(v.getAsString()));
        }
        return false;
    }
    public static R196DiggerGoal first(PathfinderMob mob) {
        return mob.goalSelector.getAvailableGoals().stream().map(w->w.getGoal()).filter(R196DiggerGoal.class::isInstance)
                .map(R196DiggerGoal.class::cast).findFirst().orElse(null);
    }
    public BlockPos targetBlock() { return target; }
    public int progress() { return progress; }
    public int cooldown() { return cooldown; }
    public int pauseTicks() { return pause; }
    public boolean isDestroying(BlockPos pos) { return active && pos.equals(target); }
    private boolean blockingWeapon() {
        var tool=EquipmentDurability.toolProfile(mob.getMainHandItem());
        return tool!=null && (tool.family()==ToolFamily.SWORD || tool.family()==ToolFamily.CLUB
                || tool.family()==ToolFamily.CUDGEL || tool.family()==ToolFamily.SCYTHE);
    }
    /** Source levels; modern hosts and new mineral blocks use the existing source-tier mapping. */
    public static int minimumHarvestLevel(BlockState state) {
        if(OreBlockRules.isManaged(state)) return OreBlockRules.minimumHarvestLevel(state);
        if(state.getBlock() instanceof net.minecraft.world.level.block.AnvilBlock) return 0;
        if(state.getBlock() instanceof io.github.ningmeng123.mite.block.StrongboxBlock strongbox)
            return io.github.ningmeng123.mite.mining.FunctionalBlockMining.requiredHarvest(strongbox);
        if(state.getBlock() instanceof io.github.ningmeng123.mite.block.RunestoneBlock) return 3;
        if(state.is(io.github.ningmeng123.mite.registry.MiteFurnaces.CLAY_OVEN.get())
                ||state.is(io.github.ningmeng123.mite.registry.MiteFurnaces.LARGE_CLAY_OVEN.get())
                ||state.is(io.github.ningmeng123.mite.registry.MiteFurnaces.SANDSTONE_FURNACE.get())) return 0;
        if(state.is(io.github.ningmeng123.mite.registry.MiteFurnaces.OBSIDIAN_FURNACE.get())) return 3;
        if(state.is(Blocks.IRON_DOOR)||state.is(Blocks.IRON_BARS)) return 3;
        if(state.is(Blocks.GOLD_BLOCK)) return 3;
        if(state.is(Blocks.IRON_BLOCK)||state.is(Blocks.EMERALD_BLOCK)) return 4;
        if(state.is(Blocks.DIAMOND_BLOCK)) return 5;
        int decorative=MiteDecorativeContent.requiredHarvest(state.getBlock());
        if(decorative>0) return decorative;
        String storage=MiteMetalBlocks.material(state);
        if(storage!=null) return MiteMetalBlocks.requiredHarvest(storage);
        if(state.is(Blocks.OBSIDIAN)||state.is(Blocks.CRYING_OBSIDIAN)) return 3;
        if(state.is(BlockTags.LOGS)||state.is(Blocks.GLASS)||state.is(Blocks.ICE)||state.is(Blocks.PACKED_ICE)||state.is(Blocks.BLUE_ICE)) return 1;
        if(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("sandstone")) return 1;
        if(state.is(BlockTags.TERRACOTTA)) return 1;
        if(state.is(Blocks.CLAY)||state.is(Blocks.GLASS)||state.is(Blocks.GLASS_PANE)
                ||state.is(Blocks.ICE)||state.is(Blocks.PACKED_ICE)||state.is(Blocks.BLUE_ICE)
                ||state.is(Blocks.PUMPKIN)||state.is(Blocks.CARVED_PUMPKIN)||state.is(Blocks.JACK_O_LANTERN)
                ||state.is(Blocks.MELON)||state.is(BlockTags.WOOL)||state.is(Blocks.SNOW_BLOCK)
                ||state.is(Blocks.SNOW)||state.is(Blocks.SPONGE)||state.is(Blocks.WET_SPONGE)) return 0;
        if(state.is(BlockTags.NEEDS_DIAMOND_TOOL)||state.is(BlockTags.NEEDS_IRON_TOOL)) return 3;
        if(state.is(BlockTags.NEEDS_STONE_TOOL)||state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return 2;
        return 0;
    }
    public static boolean requiresSourceTool(BlockState state) { return minimumHarvestLevel(state)>0; }
    /** Source xj returns zero when ineffective; modern Tool's baseline one must not grant digging speed. */
    public static float sourceStrength(PathfinderMob mob,BlockState state) {
        ItemStack held=mob.getMainHandItem();
        if(held.isEmpty()||EquipmentDurability.toolProfile(held)==null) return 0F;
        var definition=MiteTools.definition(held);
        if(definition!=null && definition.harvestLevel()<minimumHarvestLevel(state)) return 0F;
        float speed=held.getDestroySpeed(state);
        return speed>1F?speed:0F;
    }
    public ZombieRoutePlanner.Plan pursuitPlan() { return pursuit.plan(); }
    public void invalidatePursuit() { pursuit.invalidate(); }
    public boolean destructionRejected(BlockPos pos) { return pursuit.rejected(pos); }
    public void rejectDestruction(BlockPos pos) {
        pursuit.reject(pos);
        BlockState state=mob.level().getBlockState(pos);
        if(state.getBlock() instanceof DoorBlock) {
            BlockPos partner=state.getValue(DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                    ?pos.below():pos.above();
            if(mob.level().getBlockState(partner).is(state.getBlock()))pursuit.reject(partner);
        }
    }
    public long remainingBreakTicks(BlockPos pos) {
        return io.github.ningmeng123.mite.rules.ZombieRouteRules.remainingBreakTicks(
                active&&pos.equals(target)?cooldown:40,active&&pos.equals(target)?progress:-1,stageDelay(pos));
    }
    /** Planning can inspect a future obstacle, but execution still requires physical reach and grief permission. */
    public boolean canDestroyMaterial(BlockPos pos) {
        if(blockingWeapon()||!mob.level().hasChunkAt(pos)||pursuit.rejected(pos)) return false;
        BlockState state=mob.level().getBlockState(pos);
        if(state.getBlock() instanceof FarmBlock&&!MonsterFarmland.allowed(mob)) return false;
        if(state.isAir()||!state.getFluidState().isEmpty()||state.getDestroySpeed(mob.level(),pos)<0F
                || !state.canEntityDestroy(mob.level(),pos,mob)) return false;
        if(mob instanceof R196Elemental earth&&minimumHarvestLevel(state)<=earth.getBlockHarvestLevel()) return true;
        boolean cactus=state.is(Blocks.CACTUS)&&(mob.isInvulnerable()||mob.getType().is(EntityTypeTags.UNDEAD));
        boolean exception=state.is(BlockTags.SAND)||state.is(BlockTags.DIRT)||state.is(Blocks.GRAVEL)
                ||state.is(BlockTags.LEAVES)||state.is(BlockTags.WOOL)||state.is(Blocks.SNOW_BLOCK)
                ||state.is(Blocks.FARMLAND)||state.is(Blocks.CLAY)||state.is(Blocks.SPONGE)||state.is(Blocks.WET_SPONGE)
                ||state.is(Blocks.PUMPKIN)||state.is(Blocks.CARVED_PUMPKIN)||state.is(Blocks.JACK_O_LANTERN)
                ||state.is(Blocks.MELON)||state.is(Blocks.MYCELIUM)||state.is(Blocks.HAY_BLOCK)||state.is(Blocks.GLASS_PANE)||cactus;
        return !requiresSourceTool(state)||(R196MonsterAi.frenzied(mob)&&minimumHarvestLevel(state)<2)
                ||sourceStrength(mob,state)>0F||exception;
    }
    public boolean canDestroy(BlockPos pos,boolean clipping) {
        if(!canDestroyMaterial(pos)||!EventHooks.canEntityGrief(mob.level(),mob)) return false;
        var tool=EquipmentDurability.toolProfile(mob.getMainHandItem()); int foot=mob.blockPosition().getY();
        boolean overhead=pos.getY()>foot+1&&overheadObstruction(pos);
        if(pos.getY()>foot+1&&!overhead||pos.getY()<foot&&(tool==null||tool.family()!=ToolFamily.SHOVEL)) return false;
        Vec3 reach=overhead?mob.getEyePosition():mob.position().add(0,mob.getBbHeight()*.5,0);
        if(reach.distanceToSqr(pos.getCenter())>3.25D) return false;
        BlockState state=mob.level().getBlockState(pos);
        if(clipping&&blocked(mob.getEyePosition(),pos)&&blocked(legs(),pos)) return false;
        if(mob instanceof R196Elemental earth&&minimumHarvestLevel(state)<=earth.getBlockHarvestLevel()) return true;
        for(var peer:mob.level().getEntitiesOfClass(PathfinderMob.class,new net.minecraft.world.phys.AABB(
                mob.getX()-4,mob.getY()-4,mob.getZ()-4,mob.getX()+4,mob.getY()+4,mob.getZ()+4))) {
            if(peer==mob) continue;
            var digger=first(peer); if(digger!=null&&digger.isDestroying(pos)) return false;
        }
        return true;
    }

    /** Only the close player's actual support directly over this mob gets the upward reach exception. */
    private boolean overheadObstruction(BlockPos pos) {
        if(!(mob.getTarget() instanceof Player player)||!player.isAlive()||player.level()!=mob.level()
                ||!R196MonsterAi.managed(mob)||!watcher(mob)||!mob.level().hasChunkAt(pos)
                ||SourceMeleeGoal.canStrike(mob,player)) return false;
        var body=mob.getBoundingBox();var prey=player.getBoundingBox();
        if(pos.getX()+1D<=body.minX||pos.getX()>=body.maxX||pos.getZ()+1D<=body.minZ||pos.getZ()>=body.maxZ
                ||pos.getX()+1D<=prey.minX||pos.getX()>=prey.maxX||pos.getZ()+1D<=prey.minZ||pos.getZ()>=prey.maxZ) return false;
        double dx=player.getX()-mob.getX(),dz=player.getZ()-mob.getZ();
        if(dx*dx+dz*dz>2.25D||mob.getEyePosition().distanceToSqr(pos.getCenter())>3.25D) return false;
        for(var shape:mob.level().getBlockState(pos).getCollisionShape(mob.level(),pos).toAabbs()) {
            var obstacle=shape.move(pos);
            if(obstacle.minY<body.maxY-.01D||obstacle.minY-body.maxY>1.25D||Math.abs(obstacle.maxY-prey.minY)>.125D
                    ||obstacle.maxX<=body.minX||obstacle.minX>=body.maxX||obstacle.maxZ<=body.minZ||obstacle.minZ>=body.maxZ
                    ||obstacle.maxX<=prey.minX||obstacle.minX>=prey.maxX||obstacle.maxZ<=prey.minZ||obstacle.minZ>=prey.maxZ) continue;
            var hit=clip(mob.getEyePosition(),player.position().add(0,.1D,0));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos)) return true;
        }
        return false;
    }

    private BlockPos overheadBlock() {
        var prey=mob.getTarget();
        if(!(prey instanceof Player)||!prey.isAlive()) return null;
        var bounds=prey.getBoundingBox();int top=net.minecraft.util.Mth.floor(bounds.minY-.001D);
        for(int y=top;y>=top-1;y--)
            for(int x=net.minecraft.util.Mth.floor(bounds.minX+.001D);x<=net.minecraft.util.Mth.floor(bounds.maxX-.001D);x++)
                for(int z=net.minecraft.util.Mth.floor(bounds.minZ+.001D);z<=net.minecraft.util.Mth.floor(bounds.maxZ-.001D);z++) {
                    BlockPos candidate=new BlockPos(x,y,z);
                    if(overheadObstruction(candidate)&&canDestroy(candidate,true)) return candidate;
                }
        return null;
    }

    /** Digging keeps an already acquired hidden player only while this goal is actually doing valid work. */
    public boolean isBreakingOverheadFor(LivingEntity prey) {
        return started&&active&&overheadDigging&&target!=null&&mob.getTarget()==prey
                &&overheadObstruction(target)&&canDestroy(target,true);
    }
    private Vec3 legs() { return mob.position().add(0,mob.getBbHeight()*.25F,0); }
    private BlockHitResult clip(Vec3 from,Vec3 to) {
        return mob.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob));
    }
    private boolean blocked(Vec3 from,BlockPos pos) {
        var hit=clip(from,pos.getCenter());return hit.getType()==HitResult.Type.BLOCK&&!hit.getBlockPos().equals(pos);
    }
    private boolean passable(BlockPos pos) {
        if(!mob.level().hasChunkAt(pos)) return false;
        var state=mob.level().getBlockState(pos);
        return state.getFluidState().isEmpty()&&(state.getCollisionShape(mob.level(),pos).isEmpty()
                ||state.hasProperty(BlockStateProperties.OPEN)&&state.getValue(BlockStateProperties.OPEN));
    }
    public boolean blockWillFall(BlockPos pos) {
        var state=mob.level().getBlockState(pos);
        return state.getBlock() instanceof FallingBlock||CollapsePhysics.managed(state)
                ||state.is(Blocks.CACTUS)||state.is(Blocks.SNOW)||state.is(Blocks.LILY_PAD);
    }
    /** Also used by qa's overhead-target support branch; same target keeps its existing progress/cooldown. */
    public boolean setBlockToDig(BlockPos pos,boolean clipping) {
        if(!canDestroy(pos,clipping)) return false;
        active=true;
        if(pos.equals(target)) { overheadDigging|=overheadObstruction(pos);save();return true; }
        if(pos.getY()==mob.blockPosition().getY()+1&&mob.level().getBlockState(pos).is(Blocks.CACTUS)&&canDestroy(pos.below(),clipping)) pos=pos.below();
        target=pos.immutable(); overheadDigging=overheadObstruction(pos);progress=-1; save(); return true;
    }
    private void restore() {
        if(target!=null||!mob.getPersistentData().contains(SAVE)) return;
        var tag=mob.getPersistentData().getCompound(SAVE);
        if(!tag.contains("position")) return;
        target=BlockPos.of(tag.getLong("position"));progress=Math.clamp(tag.getInt("progress"),-1,9);
        cooldown=Math.max(0,tag.getInt("cooldown"));active=true;
        overheadDigging=tag.getBoolean("overhead");
    }
    @Override public boolean canUse() {
        restore();var prey=mob.getTarget();
        // A delayed walking decision is not evidence that an overhead or legacy mining route is needed.
        if(MeleePursuitPaths.pending(mob)) {
            if(ZombieRoutePlanner.applies(mob)) pursuit.plan();
            if(MeleePursuitPaths.pending(mob)) return false;
        }
        // Restored target UUIDs may be resolved a few ticks after the goal's saved work is loaded.
        if(overheadDigging&&prey==null&&!started) return false;
        if(overheadDigging&&(target==null||!overheadObstruction(target))) stop();
        BlockPos overhead=overheadBlock();
        if(overhead!=null) return setBlockToDig(overhead,true);
        if(ZombieRoutePlanner.applies(mob)) {
            var plan=pursuit.plan();
            if(MeleePursuitPaths.pending(mob)) return false;
            if(plan.choosesBreaking()) {
                if(plan.portal()) {if(active)stop();return false;}
                return setBlockToDig(plan.firstBlock(),true);
            }
            if(plan.completeWalkingRoute()) { if(active) stop();return false; }
        }
        if(prey==null||!prey.isAlive()||blockingWeapon()||!R196MonsterAi.diggingEnabled(mob)&&!mob.getSensing().hasLineOfSight(prey)
                ||mob.blockPosition().equals(prey.blockPosition())) return false;
        if(active&&target!=null&&canDestroy(target,true)) return true;
        if(!active&&mob.getRandom().nextInt(20)!=0) return false;
        double distance=mob.distanceTo(prey);
        if(distance>16D) return false;
        if(distance*distance>2D)
            for(int y=prey.blockPosition().getY()-1;y>=mob.blockPosition().getY();y--)
                if(setBlockToDig(new BlockPos(prey.blockPosition().getX(),y,prey.blockPosition().getZ()),true)) return true;
        if(distance>8D) return false;
        Vec3 highEye=mob.getEyePosition().add(0,1,0);
        boolean over=passable(BlockPos.containing(highEye))&&clip(highEye,prey.getEyePosition()).getType()==HitResult.Type.MISS;
        if(distance>(over?8D:R196MonsterAi.frenzied(mob)?6D:4D)) return false;
        pathToTarget();
        if(!mob.getNavigation().isDone()||SourceMeleeGoal.canStrike(mob,prey)) return false;
        Vec3 center=prey.position().add(0,prey.getBbHeight()*.5F,0);
        if(passable(BlockPos.containing(prey.getX(),prey.getY()+prey.getBbHeight(),prey.getZ()).above())&&scan(clip(mob.getEyePosition(),center.add(0,1,0)))) return true;
        if(scan(clip(mob.getEyePosition(),center))) return true;
        var hit=clip(legs(),center);
        return hit.getType()==HitResult.Type.BLOCK&&!restricted(hit.getBlockPos())
                &&(passable(hit.getBlockPos().above())||blockWillFall(hit.getBlockPos().above()))&&setBlockToDig(hit.getBlockPos(),false);
    }
    private boolean restricted(BlockPos pos) {
        return mob.level().getBlockState(pos).getBlock() instanceof FenceBlock
                &&sourceStrength(mob,mob.level().getBlockState(pos))<=0F&&!(mob.getTarget() instanceof net.minecraft.world.entity.player.Player);
    }
    private boolean scan(BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK||restricted(hit.getBlockPos())) return false;
        for(int y=hit.getBlockPos().getY()+1;y>=mob.blockPosition().getY();y--)
            if(setBlockToDig(new BlockPos(hit.getBlockPos().getX(),y,hit.getBlockPos().getZ()),true)) return true;
        return false;
    }
    private Path pathToTarget() {
        var prey=mob.getTarget();
        // The active digging checks must share the same route and retry budget as melee and
        // portal breaking; a second vanilla query here bypasses failed-route backoff.
        if(ZombieRoutePlanner.applies(mob)) return pursuit.plan().path();
        return prey==null?null:mob.getNavigation().createPath(prey.blockPosition(),0,16);
    }
    private boolean pathIsCloser() {
        var path=pathToTarget();var prey=mob.getTarget();
        if(path==null||path.getEndNode()==null||prey==null) return false;
        var n=path.getEndNode();return new Vec3(n.x+.5,n.y,n.z+.5).distanceTo(prey.position())<mob.distanceTo(prey)-2D;
    }
    private boolean pathCanHit() {
        var path=pathToTarget();var prey=mob.getTarget();
        if(path==null||path.getEndNode()==null||prey==null) return false;
        var n=path.getEndNode();Vec3 foot=new Vec3(n.x+.5,n.y,n.z+.5);
        return foot.distanceTo(prey.position())<=1D&&clip(foot,prey.position().add(0,prey.getBbHeight()*.5F,0)).getType()==HitResult.Type.MISS;
    }
    @Override public void start() { active=true;started=true;if(overheadDigging)mob.getNavigation().stop();save(); }
    @Override public boolean canContinueToUse() {
        if(blockingWeapon()||!mob.isAlive()) return false;
        if(MeleePursuitPaths.pending(mob) && ZombieRoutePlanner.applies(mob)) {
            var plan=pursuit.plan();
            if(MeleePursuitPaths.pending(mob)) return active && target!=null && canDestroy(target,true);
            if(plan.completeWalkingRoute()&&!plan.choosesBreaking()) return false;
        }
        if(overheadDigging&&(target==null||!overheadObstruction(target))) return false;
        var melee=SourceMeleeGoal.first(mob);
        if(melee!=null&&melee.tickWhenDigging()) return false;
        if(pause>0) return pause!=1||!pathIsCloser();
        var prey=mob.getTarget();
        if(!active||target==null||!canDestroy(target,true)||prey==null||!prey.isAlive()||mob.blockPosition().equals(prey.blockPosition())) return false;
        if(!overheadDigging&&ZombieRoutePlanner.applies(mob)&&MobCombat.ticksWithOffset(mob)%ZombieRoutePlanner.REPLAN_TICKS==0) {
            var plan=pursuit.plan();
            if(plan.portal()||plan.completeWalkingRoute()&&!plan.choosesBreaking()) return false;
        }
        return overheadDigging||MobCombat.ticksWithOffset(mob)%10!=0||!pathCanHit();
    }
    /** Actual registered source values; unmapped modern blocks retain the project's modern hardness adaptation. */
    public float hardness(BlockPos pos) {
        var s=mob.level().getBlockState(pos);
        if(SourceBreakPortalGoal.woodenPortal(s)) return s.getBlock() instanceof DoorBlock?.25F:s.getBlock() instanceof TrapDoorBlock?.8F:2F;
        if(s.is(Blocks.STONE)) return 2.4F;
        if(s.is(Blocks.NETHERRACK)) return 1.6F;
        if(s.is(Blocks.ICE)||s.is(Blocks.PACKED_ICE)||s.is(Blocks.BLUE_ICE)) return 1F;
        if(s.is(BlockTags.FENCES)) return s.is(BlockTags.WOODEN_FENCES)?.4F:2F;
        if(s.is(BlockTags.PLANKS)) return .8F;
        if(s.is(BlockTags.LEAVES)) return .2F;
        if(s.is(Blocks.GLASS)) return 2F;
        if(s.is(Blocks.GLASS_PANE)) return .1F;
        if(s.is(Blocks.SNOW_BLOCK)) return .4F;
        if(s.is(Blocks.SPONGE)||s.is(Blocks.WET_SPONGE)) return .6F;
        if(s.is(Blocks.CLAY)) return .8F;
        if(s.is(BlockTags.WOOL)) return .8F;
        if(s.is(Blocks.CACTUS)) return .4F;
        if(s.is(Blocks.MELON)) return .6F;
        if(s.is(Blocks.HAY_BLOCK)) return .5F;
        if(s.is(Blocks.FARMLAND)||s.is(Blocks.MYCELIUM)) return .6F;
        return MiningProgress.effectiveHardness(s,s.getDestroySpeed(mob.level(),pos));
    }
    public int stageDelay(BlockPos pos) {
        int delay=(int)(300F*hardness(pos));
        if(R196MonsterAi.frenzied(mob)) delay/=2;
        if(mob instanceof R196Elemental earth) delay/=earth.isNormalClay()?4:earth.isHardenedClay()?6:8;
        if(EquipmentDurability.toolProfile(mob.getMainHandItem())!=null) delay=(int)(delay/(1F+sourceStrength(mob,mob.level().getBlockState(pos))*.5F));
        if(mob instanceof R196Undead undead&&undead.miteKind()==MobKind.GHOUL) delay/=2;
        return delay;
    }
    @Override public void tick() {
        // A pending route recheck keeps this already selected goal running.
        // Advance its valid work after canUse admits the initial route decision.
        if(!active||target==null||!(mob.level() instanceof ServerLevel level)) return;
        if(pause>0) { pause--;return; }
        if(overheadDigging&&!overheadObstruction(target)||!canDestroy(target,true)) { stop();return; }
        mob.getLookControl().setLookAt(target.getX()+.5,target.getY()+.5,target.getZ()+.5,10,mob.getMaxHeadXRot());
        if(cooldown==10) mob.swing(InteractionHand.MAIN_HAND);
        if(--cooldown>0) { save();return; }
        cooldown=stageDelay(target);mob.setNoActionTime(-400);
        var state=level.getBlockState(target);
        if(state.is(Blocks.CACTUS)&&EquipmentDurability.toolProfile(mob.getMainHandItem())==null) mob.hurt(level.damageSources().cactus(),1F);
        BlockPos broken=target;
        if(++progress>=10) {
            progress=-1;
            if(!state.canEntityDestroy(level,broken,mob)||!EventHooks.onEntityDestroyBlock(mob,broken,state)) { rejectDestruction(broken);stop();return; }
            if(state.getBlock() instanceof DoorBlock) {
                BlockPos partner=state.getValue(DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                        ?broken.below():broken.above();
                BlockState other=level.getBlockState(partner);
                if(other.is(state.getBlock())&&(!other.canEntityDestroy(level,partner,mob)
                        ||!EventHooks.onEntityDestroyBlock(mob,partner,other))) {rejectDestruction(partner);stop();return;}
            }
            // A protection callback may end the blood moon or disable griefing before the actual mutation.
            if(state.getBlock() instanceof FarmBlock&&(!MonsterFarmland.allowed(mob)||!EventHooks.canEntityGrief(level,mob))) { stop();return; }
            if(!level.destroyBlock(broken,true,mob)) { stop();return; }
            if(!mob.getMainHandItem().isEmpty()) EquipmentDurability.mineBlock(mob.getMainHandItem(),level,state,broken,mob);
            active=false;
            if(blockWillFall(broken.above()))
                for(var peer:level.getEntitiesOfClass(PathfinderMob.class,mob.getBoundingBox().inflate(3,1,3))) {
                    var melee=SourceMeleeGoal.first(peer);if(melee!=null) melee.suppress(10);
                }
            var above=level.getBlockState(broken.above());
            if(above.getBlock() instanceof FallingBlock||CollapsePhysics.managed(above)) { active=true;pause=10; }
            else if(!above.isAir()&&!blockWillFall(broken.above())) {
                target=broken.getY()==mob.blockPosition().getY()&&canDestroy(broken.above(),true)?broken.above():broken.below();
                active=true;pause=10;
            } else if(broken.getY()==mob.blockPosition().getY()+1&&!passable(mob.blockPosition().above(2))&&canDestroy(broken.below(),true)) {
                target=broken.below();active=true;pause=10;
            }
            CollapseSecondaryTriggers.removedSupport(level,broken);
        }
        level.destroyBlockProgress(mob.getId(),broken,progress);
        level.levelEvent(2001,broken,Block.getId(state));
        if(!active) target=null;
        save();
    }
    private void save() {
        if(!active||target==null) { mob.getPersistentData().remove(SAVE);return; }
        var tag=new CompoundTag();tag.putLong("position",target.asLong());tag.putInt("progress",progress);tag.putInt("cooldown",cooldown);
        tag.putBoolean("overhead",overheadDigging);
        mob.getPersistentData().put(SAVE,tag); // Source does not persist the transient pause.
    }
    @Override public void stop() {
        if(target!=null&&mob.level() instanceof ServerLevel level) level.destroyBlockProgress(mob.getId(),target,-1);
        target=null;active=false;overheadDigging=false;started=false;progress=-1;cooldown=40;pause=0;save();
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
