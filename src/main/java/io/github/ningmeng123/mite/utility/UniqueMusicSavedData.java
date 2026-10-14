package io.github.ningmeng123.mite.utility;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** yr/ye signatures are shared across dimensions and released when a dropped record is destroyed. @author NingMeng123 */
public final class UniqueMusicSavedData extends SavedData {
    public static final String NAME = "mite_unique_music";
    public static final Factory<UniqueMusicSavedData> FACTORY = new Factory<>(UniqueMusicSavedData::new, UniqueMusicSavedData::load);
    private final Set<String> generated = new LinkedHashSet<>();
    public static UniqueMusicSavedData get(ServerLevel level) { return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME); }
    public boolean has(String record) { return generated.contains(record); }
    public boolean mark(String record) { boolean added = generated.add(record); if (added) setDirty(); return added; }
    public void release(String record) { if (generated.remove(record)) setDirty(); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registry) {
        tag.putInt("version", 1); ListTag names = new ListTag(); generated.forEach(name -> names.add(StringTag.valueOf(name))); tag.put("records", names); return tag;
    }
    public static UniqueMusicSavedData load(CompoundTag tag, HolderLookup.Provider registry) {
        UniqueMusicSavedData data = new UniqueMusicSavedData();
        for (Tag name : tag.getList("records", Tag.TAG_STRING)) data.generated.add(name.getAsString());
        return data;
    }
}
