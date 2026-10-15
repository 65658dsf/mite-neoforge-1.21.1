package io.github.ningmeng123.mite.integration.jade;

import io.github.ningmeng123.mite.integration.CropInspection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IBoxElement;

/** Read-only crop inspection with server context checks and a block-state-only fallback. @author NingMeng123 */
public enum CropJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mite", "crop_status");
    public static final String DATA_KEY = "mite:crop_inspection";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return 100;
    }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return !accessor.isFakeBlock() && CropInspection.state(accessor.getBlockState()) != null;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level) || accessor.isFakeBlock()
                || !level.hasChunkAt(accessor.getPosition())) return;
        BlockState state = accessor.getBlockState();
        if (state != level.getBlockState(accessor.getPosition())) return;
        CropInspection.Snapshot snapshot = CropInspection.inspect(level, accessor.getPosition(), state);
        if (snapshot == null) return;

        CompoundTag payload = new CompoundTag();
        payload.putLong("position", accessor.getPosition().asLong());
        payload.putString("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        payload.putString("dimension", level.dimension().location().toString());
        payload.putString("state", state.toString());
        payload.put("data", CropInspection.save(snapshot));
        data.put(DATA_KEY, payload);
    }

    /** Rejects late packets from another target or growth stage without guessing missing server data. */
    public static CropInspection.Snapshot snapshot(BlockAccessor accessor) {
        BlockState state = accessor.getBlockState();
        CompoundTag serverData = accessor.getServerData();
        if (accessor.isFakeBlock() || !serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            return CropInspection.state(state);
        }
        return snapshot(state, accessor.getPosition(), accessor.getLevel().dimension().location(), serverData, false);
    }

    /** Context validation also accepts plain values, without requiring a second world or accessor. */
    public static CropInspection.Snapshot snapshot(BlockState state, BlockPos pos, ResourceLocation dimension,
            CompoundTag serverData, boolean fake) {
        CropInspection.Snapshot fallback = CropInspection.state(state);
        if (fallback == null || fake || !serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return fallback;
        CompoundTag payload = serverData.getCompound(DATA_KEY);
        if (!payload.contains("position", Tag.TAG_LONG) || !payload.contains("block", Tag.TAG_STRING)
                || !payload.contains("dimension", Tag.TAG_STRING) || !payload.contains("state", Tag.TAG_STRING)
                || !payload.contains("data", Tag.TAG_COMPOUND)
                || payload.getLong("position") != pos.asLong()
                || !payload.getString("block").equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                || !payload.getString("dimension").equals(dimension.toString())
                || !payload.getString("state").equals(state.toString())) return fallback;

        CropInspection.Snapshot received = CropInspection.load(payload.getCompound("data"));
        if (received == null || received.kind() != fallback.kind() || received.health() != fallback.health()
                || received.age() != fallback.age() || received.maxAge() != fallback.maxAge()) return fallback;
        return received;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CropInspection.Snapshot snapshot = snapshot(accessor);
        if (snapshot == null) return;
        var lines = CropInspection.lines(snapshot);
        if (lines.isEmpty()) return;
        tooltip.remove(JadeIds.MC_CROP_PROGRESS);
        lines.forEach(line -> tooltip.add(line, UID));
    }

    /** Runs after all providers, including when a user changes Jade's provider sort order. */
    public static void removeVanillaProgress(IBoxElement box, Accessor<?> accessor) {
        if (accessor instanceof BlockAccessor blockAccessor
                && CropInspection.state(blockAccessor.getBlockState()) != null
                && !box.getTooltip().get(UID).isEmpty()) {
            // With our option disabled, no tagged lines exist and Jade's normal progress remains intact.
            box.getTooltip().remove(JadeIds.MC_CROP_PROGRESS);
        }
    }
}
