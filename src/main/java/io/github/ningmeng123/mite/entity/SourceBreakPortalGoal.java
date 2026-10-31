package io.github.ningmeng123.mite.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.EventHooks;

/** Actual pj/pm portal pounding; modern wood families and grief hooks are explicit adaptations. @author NingMeng123 */
public final class SourceBreakPortalGoal extends Goal {
    private final PathfinderMob mob;
    private BlockPos target;
    private int progress;
    private int lastStage = -1;

    public SourceBreakPortalGoal(PathfinderMob mob) {
        this.mob = mob;
        // pm and pj never set the source mutex bits: this task can run alongside movement/mining.
    }

    public static SourceBreakPortalGoal first(PathfinderMob mob) {
        return mob.goalSelector.getAvailableGoals().stream().map(w->w.getGoal()).filter(SourceBreakPortalGoal.class::isInstance)
                .map(SourceBreakPortalGoal.class::cast).findFirst().orElse(null);
    }

    public static boolean woodenPortal(BlockState state) {
        return state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES);
    }

    public static boolean closedPortal(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!woodenPortal(state)) return false;
        if (state.getBlock() instanceof DoorBlock) {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                BlockState lower = level.getBlockState(pos.below());
                return lower.is(state.getBlock()) && !lower.getValue(DoorBlock.OPEN);
            }
            return !state.getValue(DoorBlock.OPEN);
        }
        if (state.getBlock() instanceof TrapDoorBlock) return !state.getValue(TrapDoorBlock.OPEN);
        return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN);
    }

    private boolean candidate(BlockPos pos) {
        if (!(mob.level() instanceof ServerLevel level) || !level.hasChunkAt(pos)) return false;
        var digger=R196DiggerGoal.first(mob);if(digger!=null&&digger.destructionRejected(pos))return false;
        BlockState state = level.getBlockState(pos);
        if (!woodenPortal(state)) return false;
        target = pos.immutable();
        // pm selects the first wooden portal, even open: pj subsequently rejects it, as in the source.
        return true;
    }

    @Override public boolean canUse() {
        target = null;
        if(mob.level() instanceof ServerLevel level&&ZombieRoutePlanner.applies(mob)) {
            var digger=R196DiggerGoal.first(mob);
            if(digger!=null) {
                var plan=digger.pursuitPlan();
                if(MeleePursuitPaths.pending(mob)) return false;
                if(plan.portal()&&plan.firstBlock()!=null&&mob.distanceToSqr(plan.firstBlock().getCenter())<4D) {
                    target=plan.firstBlock();return permitted(level);
                }
            }
        }
        if (!(mob.level() instanceof ServerLevel level) || !mob.horizontalCollision
                || !(mob.getNavigation() instanceof GroundPathNavigation navigation)) return false;
        var path = navigation.getPath();
        if (path == null || path.isDone() || !navigation.canOpenDoors()) return false;
        for (int i = 0; i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); i++) {
            var node = path.getNode(i);
            if (mob.distanceToSqr(node.x, mob.getY(), node.z) > 2.25D) continue;
            BlockPos position = new BlockPos(node.x, node.y + 1, node.z);
            if (candidate(position) || candidate(position.below())) return permitted(level);
        }
        BlockPos position = mob.blockPosition().above();
        return (candidate(position) || candidate(position.below())) && permitted(level);
    }

    private boolean permitted(ServerLevel level) {
        return target != null && (!(level.getBlockState(target).getBlock() instanceof FenceGateBlock)
                || mob.getTarget() instanceof Player) && !rejected() && !fasterWalkingRoute() && EventHooks.canEntityGrief(level, mob) && closedPortal(level, target);
    }

    private void reject(BlockPos pos) {
        var digger=R196DiggerGoal.first(mob);if(digger!=null)digger.rejectDestruction(pos);
    }

    private boolean rejected() {
        var digger=R196DiggerGoal.first(mob);return target!=null&&digger!=null&&digger.destructionRejected(target);
    }

    private boolean fasterWalkingRoute() {
        if(!ZombieRoutePlanner.applies(mob)) return false;
        var digger=R196DiggerGoal.first(mob);if(digger==null) return false;
        var plan=digger.pursuitPlan();
        return plan.choosesBreaking()?!plan.portal():plan.completeWalkingRoute();
    }

    public int threshold(BlockState state) {
        int ticks = state.getBlock() instanceof DoorBlock ? 1920 : 480;
        if (mob instanceof R196Elemental elemental)
            ticks /= elemental.isNormalClay() ? 4 : elemental.isHardenedClay() ? 6 : 8;
        return R196MonsterAi.frenzied(mob) ? ticks / 2 : ticks;
    }

    public int progress() { return progress; }
    public BlockPos targetBlock() { return target; }
    @Override public void start() { progress = 0; }
    @Override public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel level) || target == null || !mob.isAlive()) return false;
        // Source checks griefing only on start. Rechecking it is the modern protection-hook adaptation.
        return !rejected() && !fasterWalkingRoute() && EventHooks.canEntityGrief(level, mob) && progress <= threshold(level.getBlockState(target))
                && closedPortal(level, target) && mob.distanceToSqr(target.getX(), target.getY(), target.getZ()) < 4D;
    }

    @Override public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || target == null || !closedPortal(level, target)
                || !EventHooks.canEntityGrief(level, mob) || rejected() || fasterWalkingRoute()) return;
        if(MeleePursuitPaths.pending(mob)) return;
        BlockState state = level.getBlockState(target);
        mob.setNoActionTime(-400);
        if (mob.getRandom().nextInt(20) == 0) {
            // Legacy event 1010 is jukebox playback in 1.21.1; use the named modern door event.
            level.levelEvent(LevelEvent.SOUND_ZOMBIE_WOODEN_DOOR, target, 0);
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, target, Block.getId(state));
        }
        int threshold = threshold(state);
        progress = Math.min(threshold, progress + (int)(Math.max(R196DiggerGoal.sourceStrength(mob, state), 1F) + .5F));
        int stage = (int)((float)progress / threshold * 10F);
        if (stage != lastStage) { level.destroyBlockProgress(mob.getId(), target, stage); lastStage = stage; }
        if (progress != threshold || level.getDifficulty() != Difficulty.HARD) return;
        if(!state.canEntityDestroy(level,target,mob)||!EventHooks.onEntityDestroyBlock(mob,target,state)) { reject(target);stop();return; }
        // pj sets air directly: no item drop and no mining-tool wear.
        if (state.getBlock() instanceof DoorBlock) {
            BlockPos partner = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? target.below() : target.above();
            BlockState other=level.getBlockState(partner);
            if(other.is(state.getBlock())) {
                if(!other.canEntityDestroy(level,partner,mob)||!EventHooks.onEntityDestroyBlock(mob,partner,other)) {reject(partner);stop();return;}
                // Shape updates must not remove and drop the still-present half before the next line removes it.
                level.setBlock(partner,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            }
        }
        level.setBlock(target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.gameEvent(mob, GameEvent.BLOCK_DESTROY, target);
        level.levelEvent(LevelEvent.SOUND_ZOMBIE_DOOR_CRASH, target, 0);
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, target, Block.getId(state));
    }

    @Override public void stop() {
        if (target != null && mob.level() instanceof ServerLevel level) level.destroyBlockProgress(mob.getId(), target, -1);
        target = null;
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
