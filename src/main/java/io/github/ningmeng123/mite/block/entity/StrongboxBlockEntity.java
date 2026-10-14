package io.github.ningmeng123.mite.block.entity;

import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** UUID ownership adapts old account-name ownership to modern authenticated players. @author NingMeng123 */
public final class StrongboxBlockEntity extends ChestBlockEntity {
    private java.util.UUID owner;
    private String ownerName = "";
    public StrongboxBlockEntity(BlockPos pos, BlockState state) { super(MiteFunctionalBlocks.STRONGBOX_ENTITY.get(), pos, state); }
    public void setOwner(Player player) {
        owner = player.getUUID(); ownerName = player.getGameProfile().getName(); setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public boolean isOwner(Player player) { return owner != null && owner.equals(player.getUUID()); }
    public String ownerName() { return ownerName; }
    @Override public boolean canOpen(Player player) {
        if (!player.isCreative() && !isOwner(player)) {
            player.displayClientMessage(Component.translatable("mite.strongbox.locked", ownerName), true);
            player.playNotifySound(SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, .2F, 1F);
            return false;
        }
        return super.canOpen(player);
    }
    @Override public boolean stillValid(Player player) {
        return (player.isCreative() || isOwner(player)) && super.stillValid(player);
    }
    @Override protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putInt("mite_version", 1);
        if (owner != null) tag.putUUID("mite_owner", owner);
        tag.putString("mite_owner_name", ownerName);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("mite_owner") ? tag.getUUID("mite_owner") : null;
        ownerName = tag.getString("mite_owner_name");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (owner != null) tag.putUUID("mite_owner", owner);
        tag.putString("mite_owner_name", ownerName);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
