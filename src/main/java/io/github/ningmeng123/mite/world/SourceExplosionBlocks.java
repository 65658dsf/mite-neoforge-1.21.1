package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.worldgen.TrialBlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** abr's 1,352 low-resistance rays precede, but do not replace, the native main blast. @author NingMeng123 */
public final class SourceExplosionBlocks {
    private SourceExplosionBlocks() {}
    public static void prepare(Level level, Explosion explosion) {
        var source=explosion.getDirectSourceEntity();
        prepare(level,explosion,source==null?new net.minecraft.world.level.ExplosionDamageCalculator()
                :new net.minecraft.world.level.EntityBasedExplosionDamageCalculator(source));
    }
    public static void prepare(Level level, Explosion explosion,net.minecraft.world.level.ExplosionDamageCalculator calculator) {
        if (!(level instanceof ServerLevel server) || explosion.radius() <= 0F
                || explosion.getBlockInteraction() != Explosion.BlockInteraction.DESTROY
                    && explosion.getBlockInteraction() != Explosion.BlockInteraction.DESTROY_WITH_DECAY) return;
        var center = explosion.center();
        for (int x=0; x<16; x++) for (int y=0; y<16; y++) for (int z=0; z<16; z++) {
            if (x!=0 && x!=15 && y!=0 && y!=15 && z!=0 && z!=15) continue;
            double dx=(float)x/15F*2F-1F, dy=(float)y/15F*2F-1F, dz=(float)z/15F*2F-1F;
            double length=Math.sqrt(dx*dx+dy*dy+dz*dz); dx/=length;dy/=length;dz/=length;
            double px=center.x,py=center.y,pz=center.z;
            float budget=SourceExplosionRules.preBlastBudget(explosion.radius(), level.random.nextFloat());
            while (budget>0F) {
                BlockPos pos=BlockPos.containing(px,py,pz);
                if (server.isOutsideBuildHeight(pos) || !server.hasChunkAt(pos)) break;
                BlockState state=server.getBlockState(pos);
                if (!state.isAir()) {
                    float resistance=SourceExplosionItems.resistance(new ItemStack(state.getBlock()),
                            MiningProgress.effectiveHardness(state,state.getDestroySpeed(server,pos)));
                    budget=SourceExplosionRules.stopsPreBlast(resistance)?0F:budget-(resistance+.3F)*.3F;
                }
                if (budget>0F && calculator.shouldBlockExplode(explosion,server,pos,state,budget)) {
                    if (!state.isAir()) destroy(server,pos,state,explosion);
                } else if (budget>=0F && center.y>pos.getY() && state.is(Blocks.MYCELIUM)) {
                    boolean natural=io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks.isNatural(server,pos);
                    server.setBlock(pos,Blocks.DIRT.defaultBlockState(),3);
                    if (natural) io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks.record(server.getChunkAt(pos),pos);
                    CollapsePhysics.undermine(server,pos,null);
                }
                px+=dx*.3F;py+=dy*.3F;pz+=dz*.3F;budget-=.3F*.75F;
            }
        }
    }
    public static void destroy(ServerLevel level,BlockPos pos,BlockState state,Explosion explosion) {
        // The native block callback preserves source ore drops, containers, trial provenance and other mods' loot hooks.
        try (var scope=TrialBlockDrops.destruction(level,pos)) {
            state.onExplosionHit(level,pos,explosion,(stack,at)->Block.popResource(level,at,stack));
        }
        CollapseSecondaryTriggers.exploded(level,pos,state,explosion);
    }
}
