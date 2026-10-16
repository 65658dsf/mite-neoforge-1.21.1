package io.github.ningmeng123.mite.advancement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Shared world-first facts, independent of player deaths, dimensions and revoked advancements. @author NingMeng123 */
public final class WorldAchievementData extends SavedData {
    public record First(UUID player,String name,long day){}
    private final Map<String,First> firsts=new LinkedHashMap<>();
    public static WorldAchievementData get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(WorldAchievementData::new,WorldAchievementData::load),"mite_world_achievements");}
    public Map<String,First> firsts(){return Map.copyOf(firsts);}
    public boolean record(String id,UUID uuid,String name,long day){
        if(firsts.putIfAbsent(id,new First(uuid,name,day))!=null)return false;
        setDirty();return true;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        tag.putInt("version",1);ListTag rows=new ListTag();
        firsts.forEach((id,first)->{CompoundTag row=new CompoundTag();row.putString("id",id);row.putUUID("player",first.player);row.putString("name",first.name);row.putLong("day",first.day);rows.add(row);});
        tag.put("firsts",rows);return tag;
    }
    public static WorldAchievementData load(CompoundTag tag,HolderLookup.Provider registries){
        var result=new WorldAchievementData();
        for(var value:tag.getList("firsts",10)){
            CompoundTag row=(CompoundTag)value;
            if(row.hasUUID("player"))result.firsts.putIfAbsent(row.getString("id"),new First(row.getUUID("player"),row.getString("name"),row.getLong("day")));
        }
        return result;
    }
}
