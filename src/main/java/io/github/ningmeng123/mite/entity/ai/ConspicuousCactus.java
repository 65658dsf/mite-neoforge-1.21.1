package io.github.ningmeng123.mite.entity.ai;

import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** ang stores 0..7 kills in cactus support sand; tf reacts to established lethal traps. @author NingMeng123 */
public final class ConspicuousCactus {
    private static final Map<Creeper,Integer> RECENT=new WeakHashMap<>();
    private ConspicuousCactus() {}
    public static int recentTicks(Creeper creeper) { return RECENT.getOrDefault(creeper,0); }
    public static void tick(Creeper creeper) {
        int ticks=recentTicks(creeper);
        if(ticks>1) RECENT.put(creeper,ticks-1); else RECENT.remove(creeper);
    }
    public static boolean forcesSwelling(Creeper creeper) {
        if(recentTicks(creeper)<=0) return false;
        BlockPos center=creeper.blockPosition();
        for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++)
            if(creeper.level().hasChunkAt(center.offset(x,y,z))&&creeper.level().getBlockState(center.offset(x,y,z)).is(Blocks.CACTUS)) return true;
        return false;
    }
    @Nullable private static BlockPos support(ServerLevel level,BlockPos cactus) {
        BlockPos pos=cactus.below();
        while(pos.getY()>=level.getMinBuildHeight()&&level.hasChunkAt(pos)&&level.getBlockState(pos).is(Blocks.CACTUS)) pos=pos.below();
        return pos.getY()>=level.getMinBuildHeight()&&level.hasChunkAt(pos)&&level.getBlockState(pos).is(Blocks.SAND)?pos:null;
    }
    public static int killCount(ServerLevel level,BlockPos cactus) {
        BlockPos base=support(level,cactus); return base==null?0:data(level).kills.getOrDefault(base.asLong(),0);
    }
    public static void afterActualContact(ServerLevel level,BlockPos cactus,Entity entity) {
        // tf's callback sees the count before ang records a death caused by this same contact.
        if(entity instanceof Creeper creeper&&killCount(level,cactus)>1&&creeper.getRandom().nextInt(2)==0) RECENT.put(creeper,120);
        if(entity.isRemoved()||entity instanceof LivingEntity living&&living.getHealth()<=0F) {
            BlockPos base=support(level,cactus);
            if(base!=null) { Data data=data(level); data.set(base,Math.min(7,data.kills.getOrDefault(base.asLong(),0)+1)); }
        }
    }
    /** Source top cactus spends half its random updates trying to reduce the shared trap count. */
    public static boolean randomDecay(ServerLevel level,BlockPos cactus) {
        if(level.getBlockState(cactus.above()).is(Blocks.CACTUS)||level.getRandom().nextFloat()>=.5F) return false;
        BlockPos base=support(level,cactus);
        if(base!=null) { Data data=data(level); data.set(base,Math.max(0,data.kills.getOrDefault(base.asLong(),0)-1)); }
        return true;
    }
    public static void removed(ServerLevel level,BlockPos pos,BlockState state) {
        if(state.is(Blocks.SAND)) data(level).set(pos,0);
        else if(state.is(Blocks.CACTUS)&&level.getBlockState(pos.below()).is(Blocks.SAND)) data(level).set(pos.below(),0);
    }
    public static Data data(ServerLevel level) { return level.getDataStorage().computeIfAbsent(Data.FACTORY,"mite_cactus_traps_035"); }
    /** SavedData replaces legacy sand metadata without changing modern sand variants or block-state palettes. */
    public static final class Data extends SavedData {
        public static final Factory<Data> FACTORY=new Factory<>(Data::new,Data::load);
        private final java.util.Map<Long,Integer> kills=new java.util.HashMap<>();
        private void set(BlockPos pos,int count) {
            int old=kills.getOrDefault(pos.asLong(),0);
            if(old==count) return;
            if(count>0) kills.put(pos.asLong(),Math.clamp(count,0,7)); else kills.remove(pos.asLong()); setDirty();
        }
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registry) {
            tag.putInt("version",1); ListTag rows=new ListTag();
            kills.forEach((pos,count) -> { CompoundTag row=new CompoundTag(); row.putLong("pos",pos); row.putInt("kills",count); rows.add(row); });
            tag.put("supports",rows); return tag;
        }
        public static Data load(CompoundTag tag,HolderLookup.Provider registry) {
            Data data=new Data();
            for(Tag value:tag.getList("supports",Tag.TAG_COMPOUND)) { CompoundTag row=(CompoundTag)value;
                int count=Math.clamp(row.getInt("kills"),0,7); if(count>0) data.kills.put(row.getLong("pos"),count);
            }
            return data;
        }
    }
}
