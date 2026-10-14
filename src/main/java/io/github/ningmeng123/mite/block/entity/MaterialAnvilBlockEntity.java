package io.github.ningmeng123.mite.block.entity;

import io.github.ningmeng123.mite.block.MaterialAnvilBlock;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Source tile damage survives unloading, harvesting, placing and falling. @author NingMeng123 */
public final class MaterialAnvilBlockEntity extends BlockEntity {
    private int damage;
    public MaterialAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(MiteFunctionalBlocks.ANVIL_ENTITY.get(), pos, state);
        damage = FunctionalBlockRules.minimumDamage(stage(state), durability());
    }
    public int damage() { return damage; }
    public int durability() { return FunctionalBlockRules.anvilDurability(MaterialAnvilRules.capacity(getBlockState())); }
    private static int stage(BlockState state) {
        if (state.hasProperty(MaterialAnvilBlock.DAMAGE_STAGE)) return state.getValue(MaterialAnvilBlock.DAMAGE_STAGE);
        return state.is(Blocks.DAMAGED_ANVIL) ? 2 : state.is(Blocks.CHIPPED_ANVIL) ? 1 : 0;
    }
    public void addDamage(int amount) {
        if (level == null || level.isClientSide || amount < 1) return;
        damage = (int)Math.min((long)damage + amount, Integer.MAX_VALUE);
        setChanged();
        refreshAppearance();
    }
    public void refreshAppearance() {
        if (level == null || level.isClientSide) return;
        int stage = FunctionalBlockRules.damageStage(damage, durability());
        if (stage == 3) {
            // Worn out source anvils disappear; there is no reusable pristine drop.
            level.removeBlock(worldPosition, false);
            level.levelEvent(1029, worldPosition, 0);
            return;
        }
        BlockState current = getBlockState();
        BlockState replacement = current.hasProperty(MaterialAnvilBlock.DAMAGE_STAGE)
                ? current.setValue(MaterialAnvilBlock.DAMAGE_STAGE, stage)
                : (stage == 2 ? Blocks.DAMAGED_ANVIL : stage == 1 ? Blocks.CHIPPED_ANVIL : Blocks.ANVIL)
                    .defaultBlockState().setValue(AnvilBlock.FACING, current.getValue(AnvilBlock.FACING));
        if (replacement != current) {
            // Native iron state transitions replace the block identity and recreate its entity.
            CompoundTag saved = saveWithoutMetadata(level.registryAccess());
            level.setBlock(worldPosition, replacement, 3);
            if (level.getBlockEntity(worldPosition) instanceof MaterialAnvilBlockEntity entity)
                entity.loadWithComponents(saved, level.registryAccess());
        }
        level.sendBlockUpdated(worldPosition, current, replacement, 3);
    }
    public void applyToItem(ItemStack stack) {
        stack.set(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), damage);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("mite_version", 1);
        tag.putInt("mite_damage", damage);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        damage = Math.max(tag.contains("mite_damage") ? tag.getInt("mite_damage")
                : FunctionalBlockRules.minimumDamage(stage(getBlockState()), durability()), 0);
    }
    @Override protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), damage);
    }
    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        damage = input.getOrDefault(MiteFunctionalBlocks.ANVIL_DAMAGE.get(),
                FunctionalBlockRules.minimumDamage(stage(getBlockState()), durability()));
    }
    @Override public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("mite_damage");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
