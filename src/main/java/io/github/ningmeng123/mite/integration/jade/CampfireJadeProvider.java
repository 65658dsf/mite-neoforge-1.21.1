package io.github.ningmeng123.mite.integration.jade;

import io.github.ningmeng123.mite.campfire.CampfireController;
import io.github.ningmeng123.mite.campfire.CampfireFuelAccess;
import io.github.ningmeng123.mite.campfire.CampfireRules;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Optional read-only fuel inspection; late packets never display another campfire's reserve. @author NingMeng123 */
public enum CampfireJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mite", "campfire");
    public static final String DATA_KEY = "mite:campfire_inspection";

    public record Snapshot(int fuelPoints, int remainingTicks, int remainingMeat, boolean burning) {
        public int seconds() { return (remainingTicks + 19) / 20; }
    }

    @Override public ResourceLocation getUid() { return UID; }
    @Override public int getDefaultPriority() { return 100; }

    @Override public boolean shouldRequestData(BlockAccessor accessor) {
        return !accessor.isFakeBlock() && accessor.getBlockState().getBlock() instanceof CampfireBlock;
    }

    @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level) || accessor.isFakeBlock()
                || !level.hasChunkAt(accessor.getPosition())) return;
        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof CampfireBlock) || state != level.getBlockState(accessor.getPosition())
                || !(level.getBlockEntity(accessor.getPosition()) instanceof CampfireFuelAccess fuel)) return;
        int points = Math.clamp(fuel.mite$fuelPoints(), 0, CampfireRules.MAX_FUEL);
        CompoundTag payload = new CompoundTag();
        payload.putLong("position", accessor.getPosition().asLong());
        payload.putString("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        payload.putString("dimension", level.dimension().location().toString());
        payload.putString("state", state.toString());
        payload.putInt("fuel", points);
        payload.putInt("ticks", points == 0 ? 0 : Math.clamp(fuel.mite$fuelTicks(), 0, CampfireRules.FUEL_TICKS));
        payload.putInt("meat", points == 0 ? 0 : Math.clamp(CampfireRules.MEAT_PER_FUEL - fuel.mite$cookedMeat(), 0, CampfireRules.MEAT_PER_FUEL));
        payload.putBoolean("on", CampfireController.isBurning(level, accessor.getPosition()));
        data.put(DATA_KEY, payload);
    }

    @Nullable public static Snapshot snapshot(BlockAccessor accessor) {
        return snapshot(accessor.getBlockState(), accessor.getPosition(), accessor.getLevel().dimension().location(),
                accessor.getServerData(), accessor.isFakeBlock());
    }

    @Nullable public static Snapshot snapshot(BlockState state, BlockPos pos, ResourceLocation dimension,
            CompoundTag serverData, boolean fake) {
        if (fake || !(state.getBlock() instanceof CampfireBlock) || !serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag payload = serverData.getCompound(DATA_KEY);
        if (!payload.contains("position", Tag.TAG_LONG) || !payload.contains("block", Tag.TAG_STRING)
                || !payload.contains("dimension", Tag.TAG_STRING) || !payload.contains("state", Tag.TAG_STRING)
                || payload.getLong("position") != pos.asLong()
                || !payload.getString("block").equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                || !payload.getString("dimension").equals(dimension.toString())
                || !payload.getString("state").equals(state.toString())
                || !payload.contains("fuel", Tag.TAG_INT) || !payload.contains("ticks", Tag.TAG_INT)
                || !payload.contains("meat", Tag.TAG_INT) || !payload.contains("on", Tag.TAG_BYTE)) return null;
        int points = payload.getInt("fuel"), ticks = payload.getInt("ticks"), meat = payload.getInt("meat");
        boolean burning = payload.getBoolean("on");
        if (points < 0 || points > CampfireRules.MAX_FUEL || ticks < 0 || ticks > CampfireRules.FUEL_TICKS
                || meat < 0 || meat > CampfireRules.MEAT_PER_FUEL || points == 0 && (ticks != 0 || meat != 0 || burning)
                || burning && !state.getValue(CampfireBlock.LIT)) return null;
        return new Snapshot(points, ticks, meat, burning);
    }

    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Snapshot snapshot = snapshot(accessor);
        if (snapshot == null) return;
        tooltip.add(Component.translatable("jade.mite.campfire_fuel", snapshot.fuelPoints()), UID);
        tooltip.add(Component.translatable("jade.mite.campfire_time", snapshot.seconds()), UID);
        tooltip.add(Component.translatable("jade.mite.campfire_meat", snapshot.remainingMeat()), UID);
    }
}
