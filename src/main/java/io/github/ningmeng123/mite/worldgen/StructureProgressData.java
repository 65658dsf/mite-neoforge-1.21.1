package io.github.ningmeng123.mite.worldgen;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-shared structure prerequisites, independent of any player's lifetime. @author NingMeng123 */
public final class StructureProgressData extends SavedData {
    public static final String NAME = "mite_structure_progress";
    public static final int WHEAT = 1, CARROT = 2, POTATO = 4, ONION = 8, OBSIDIAN_TOOL = 16;
    public static final int UNDERWORLD_VISITED = 32, BOW_CRAFTED = 64, MITHRIL_EQUIPMENT_CRAFTED = 128;
    public static final int ALL_CONDITIONS = 255;
    private static final int VILLAGE_CONDITIONS = 31;
    public static final Factory<StructureProgressData> FACTORY =
            new Factory<>(StructureProgressData::new, StructureProgressData::load);
    private int conditions;
    private ServerLevel owner;

    public static StructureProgressData get(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Structure progress must be loaded on the server thread");
        ServerLevel overworld = level.getServer().overworld();
        StructureProgressData data = overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
        data.owner = overworld;
        return data;
    }

    public int conditions() { return conditions; }

    public void fulfill(int bits) { restoreConditions(conditions | bits); }

    /** Also used by save migrations; ordinary gameplay only adds prerequisite bits. */
    public void restoreConditions(int bits) {
        int valid = bits & ALL_CONDITIONS;
        if (conditions == valid) return;
        conditions = valid;
        setDirty();
        if (owner != null) R196StructureGeneration.refresh(owner);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 2);
        tag.putInt("structure_conditions", conditions);
        tag.putInt("village_conditions", conditions & VILLAGE_CONDITIONS);
        return tag;
    }

    public static StructureProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
        StructureProgressData data = new StructureProgressData();
        data.conditions = tag.contains("structure_conditions", CompoundTag.TAG_INT)
                ? tag.getInt("structure_conditions") & ALL_CONDITIONS
                : tag.getInt("village_conditions") & VILLAGE_CONDITIONS;
        return data;
    }
}
