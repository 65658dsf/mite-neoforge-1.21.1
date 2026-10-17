package io.github.ningmeng123.mite.integration.jade;

import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.crafting.AnvilDurability;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Optional authoritative anvil wear display; inspection never repairs, damages or loads a target. @author NingMeng123 */
public enum AnvilJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mite", "anvil_durability");
    public static final String DATA_KEY = "mite:anvil_inspection";
    public record Snapshot(int remaining, int maximum) {}

    @Override public ResourceLocation getUid() { return UID; }
    @Override public int getDefaultPriority() { return 100; }

    @Override public boolean shouldRequestData(BlockAccessor accessor) {
        return !accessor.isFakeBlock() && accessor.getBlockState().getBlock() instanceof AnvilBlock;
    }

    @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        data.remove(DATA_KEY);
        if (!(accessor.getLevel() instanceof ServerLevel level) || accessor.isFakeBlock()
                || !level.hasChunkAt(accessor.getPosition())) return;
        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof AnvilBlock) || state != level.getBlockState(accessor.getPosition())) return;
        MaterialAnvilBlockEntity anvil = AnvilDurability.entity(level, accessor.getPosition());
        if (anvil == null || anvil.getBlockState() != state) return;
        int maximum = anvil.durability(), damage = anvil.damage();
        if (maximum <= 0 || damage < 0 || damage >= maximum) return;
        CompoundTag payload = new CompoundTag();
        payload.putLong("position", accessor.getPosition().asLong());
        payload.putString("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        payload.putString("dimension", level.dimension().location().toString());
        payload.putString("state", state.toString());
        payload.putInt("remaining", maximum - damage);
        payload.putInt("maximum", maximum);
        data.put(DATA_KEY, payload);
    }

    @Nullable public static Snapshot snapshot(BlockAccessor accessor) {
        return snapshot(accessor.getBlockState(), accessor.getPosition(), accessor.getLevel().dimension().location(),
                accessor.getServerData(), accessor.isFakeBlock());
    }

    /** Only matching server values are displayed; local appearance supplies no guessed wear. */
    @Nullable public static Snapshot snapshot(BlockState state, BlockPos pos, ResourceLocation dimension,
            CompoundTag serverData, boolean fake) {
        if (fake || !(state.getBlock() instanceof AnvilBlock) || !serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag payload = serverData.getCompound(DATA_KEY);
        if (!payload.contains("position", Tag.TAG_LONG) || !payload.contains("block", Tag.TAG_STRING)
                || !payload.contains("dimension", Tag.TAG_STRING) || !payload.contains("state", Tag.TAG_STRING)
                || payload.getLong("position") != pos.asLong()
                || !payload.getString("block").equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                || !payload.getString("dimension").equals(dimension.toString())
                || !payload.getString("state").equals(state.toString())
                || !payload.contains("remaining", Tag.TAG_INT) || !payload.contains("maximum", Tag.TAG_INT)) return null;
        int remaining = payload.getInt("remaining"), maximum = payload.getInt("maximum");
        if (remaining <= 0 || remaining > maximum
                || maximum != FunctionalBlockRules.anvilDurability(MaterialAnvilRules.capacity(state))) return null;
        return new Snapshot(remaining, maximum);
    }

    public static Component line(Snapshot snapshot) {
        return Component.translatable("tooltip.mite.durability", snapshot.remaining(), snapshot.maximum());
    }

    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Snapshot snapshot = snapshot(accessor);
        if (snapshot != null) tooltip.add(line(snapshot), UID);
    }
}
