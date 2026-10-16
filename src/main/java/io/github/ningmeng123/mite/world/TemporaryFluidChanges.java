package io.github.ningmeng123.mite.world;

import io.github.ningmeng123.mite.Mite;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Per-dimension absolute-time block changes, surviving chunk unload and server restart. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class TemporaryFluidChanges extends SavedData {
    public static final String NAME="mite_temporary_fluids";
    public static final Factory<TemporaryFluidChanges> FACTORY=new Factory<>(TemporaryFluidChanges::new,TemporaryFluidChanges::load);
    public record Change(boolean water,long due) {}
    private final Map<Long,Change> pending=new HashMap<>();
    public static TemporaryFluidChanges get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY,NAME); }
    public Map<Long,Change> pending() { return Map.copyOf(pending); }
    public Change at(BlockPos pos) { return pending.get(pos.asLong()); }
    public void schedule(BlockPos pos,boolean water,long due) {
        pending.put(pos.asLong(),new Change(water,due));setDirty();
    }
    public void cancel(BlockPos pos) { if (pending.remove(pos.asLong())!=null) setDirty(); }
    public void tick(ServerLevel level) {
        var iterator=pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry=iterator.next();
            if (entry.getValue().due()>level.getGameTime()) continue;
            BlockPos pos=BlockPos.of(entry.getKey());
            if (!level.hasChunkAt(pos)) continue;
            var state=level.getFluidState(pos);
            boolean water=entry.getValue().water();
            if (state.isSource() && state.is(water?FluidTags.WATER:FluidTags.LAVA)) {
                var fluid=water?Fluids.FLOWING_WATER:Fluids.FLOWING_LAVA;
                level.setBlock(pos,fluid.getFlowing(7,false).createLegacyBlock(),3);
                level.scheduleTick(pos,fluid,fluid.getTickDelay(level));
            }
            iterator.remove();setDirty();
        }
    }
    @SubscribeEvent public static void afterLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel server) get(server).tick(server);
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("version",1);ListTag changes=new ListTag();
        pending.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            CompoundTag row=new CompoundTag();row.putLong("position",entry.getKey());
            row.putBoolean("water",entry.getValue().water());row.putLong("due",entry.getValue().due());changes.add(row);
        });
        tag.put("changes",changes);return tag;
    }
    public static TemporaryFluidChanges load(CompoundTag tag,HolderLookup.Provider registries) {
        TemporaryFluidChanges data=new TemporaryFluidChanges();
        for (Tag value:tag.getList("changes",Tag.TAG_COMPOUND)) {
            CompoundTag row=(CompoundTag)value;
            data.pending.put(row.getLong("position"),new Change(row.getBoolean("water"),row.getLong("due")));
        }
        return data;
    }
}
