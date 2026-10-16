package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** oa's narrow physical pickup rays; attraction does not grant permission to collect through walls. @author NingMeng123 */
public final class ExperienceOrbPhysics {
    private ExperienceOrbPhysics() {}
    public static boolean canReach(ExperienceOrb orb,Player player) {
        if (orb.level()!=player.level() || orb.distanceToSqr(player)>64D) return false;
        BlockGetter view=nonLoadingView(orb.level());
        Vec3 origin=orb.getBoundingBox().getCenter();
        for (double fraction:new double[]{.25D,.5D,.75D}) {
            Vec3 end=player.position().add(0,player.getBbHeight()*fraction,0);
            boolean clear=true;
            for (Vec3 offset:new Vec3[]{new Vec3(-.0001,0,0),new Vec3(.0001,0,0),new Vec3(0,0,-.0001),new Vec3(0,0,.0001)}) {
            if (view.clip(new ClipContext(origin.add(offset),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,orb) {
                @Override public VoxelShape getBlockShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos) {
                    if (state.is(Blocks.IRON_BARS) || state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock
                            && MiteDecorativeContent.material(state.getBlock())!=null) return Shapes.empty();
                    if ((state.getBlock() instanceof AbstractCauldronBlock || state.getBlock() instanceof net.minecraft.world.level.block.HopperBlock)
                            && !level.getBlockState(pos.above()).isFaceSturdy(level,pos.above(),net.minecraft.core.Direction.DOWN))
                        return Shapes.empty();
                    return super.getBlockShape(state,level,pos);
                }
            }).getType()!=HitResult.Type.MISS) { clear=false;break; }
            }
            if (clear) return true;
        }
        return false;
    }
    /** A geometry query must never create a distant chunk ticket, even if invoked by a third-party direct pickup. */
    public static BlockGetter nonLoadingView(Level level) {
        return new BlockGetter() {
            private ChunkAccess chunk(BlockPos pos) {
                if (level.isOutsideBuildHeight(pos)) return null;
                return level instanceof ServerLevel server?server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)
                        :level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
            }
            @Override public BlockEntity getBlockEntity(BlockPos pos) {
                ChunkAccess chunk=chunk(pos);return chunk==null?null:chunk.getBlockEntity(pos);
            }
            @Override public BlockState getBlockState(BlockPos pos) {
                ChunkAccess chunk=chunk(pos);return chunk==null?Blocks.BARRIER.defaultBlockState():chunk.getBlockState(pos);
            }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
        };
    }
    public static void lavaMovement(ExperienceOrb orb) {
        var fluid=orb.level().getFluidState(orb.blockPosition());
        if (!fluid.is(FluidTags.LAVA) || !fluid.isSource()) return;
        Vec3 motion=orb.getDeltaMovement();
        orb.setDeltaMovement(motion.x*.95F,0D,motion.z*.95F);
    }
}
