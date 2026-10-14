package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.registry.MiteMobBlocks;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded six-neighbor ore-sensitive veins preserve the source variant identity and responsible target. @author NingMeng123 */
public final class R196Infestation {
    private R196Infestation() {}
    public static boolean copperNearby(Level level,BlockPos pos) {
        for (BlockPos p:BlockPos.betweenClosed(pos.offset(-3,-3,-3),pos.offset(3,3,3))) {
            if (!level.hasChunkAt(p)) continue;
            BlockState state=level.getBlockState(p);
            if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) return true;
        }
        return false;
    }
    public static boolean upgradeVein(ServerLevel level,BlockPos start) {
        ArrayDeque<BlockPos> queue=new ArrayDeque<>(); Set<BlockPos> seen=new HashSet<>();
        queue.add(start.immutable()); boolean changed=false;
        while (!queue.isEmpty()) {
            BlockPos pos=queue.removeFirst(); if (!seen.add(pos) || !level.hasChunkAt(pos)) continue;
            BlockState state=level.getBlockState(pos);
            boolean sourceStone=state.is(Blocks.INFESTED_STONE);
            if (sourceStone) { level.setBlock(pos,MiteMobBlocks.COPPERSPINE_STONE.get().defaultBlockState(),2); changed=true; }
            // The broken seed can already be air; only its immediate six neighbors are still eligible.
            if (sourceStone || pos.equals(start)) for (Direction direction:Direction.values()) queue.addLast(pos.relative(direction));
        }
        return changed;
    }
    public static Silverfish spawn(ServerLevel level,BlockPos pos,BlockState original,LivingEntity target) {
        BlockState identity=original;
        if (original.is(Blocks.INFESTED_STONE) && copperNearby(level,pos)) {
            upgradeVein(level,pos);
            identity=MiteMobBlocks.COPPERSPINE_STONE.get().defaultBlockState();
        }
        Silverfish fish=R196Silverfish.createInfestedChild(level,pos,identity);
        if (fish==null) throw new IllegalStateException("Missing source silverfish entity type");
        fish.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        fish.setTarget(target);
        fish.setNoActionTime(-9600);
        level.addFreshEntity(fish); fish.spawnAnim();
        return fish;
    }
    public static void randomTick(ServerLevel level,BlockPos pos,BlockState state) {
        if (level.dimension().equals(Level.NETHER) || !state.is(Blocks.INFESTED_STONE) || !level.isAreaLoaded(pos,16)) return;
        if (level.getNearestPlayer(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,16,false)!=null && copperNearby(level,pos)) upgradeVein(level,pos);
    }
}
