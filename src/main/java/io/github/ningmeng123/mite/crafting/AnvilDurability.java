package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.block.MaterialAnvilBlock;
import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Read-only views of the existing source wear component and block entity. @author NingMeng123 */
public final class AnvilDurability {
    private AnvilDurability() {}

    public record View(int remaining, int maximum) {
        public Component text() { return Component.translatable("tooltip.mite.durability", remaining, maximum); }
    }

    public static int remaining(int damage, int maximum) {
        return (int)Math.max(0L, (long)Math.max(maximum, 0) - Math.max(damage, 0));
    }

    @Nullable public static View of(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return null;
        BlockState state = item.getBlock().defaultBlockState();
        if (!(state.getBlock() instanceof MaterialAnvilBlock)
                && !state.is(Blocks.ANVIL) && !state.is(Blocks.CHIPPED_ANVIL) && !state.is(Blocks.DAMAGED_ANVIL)) return null;
        state = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(state);
        int maximum = FunctionalBlockRules.anvilDurability(MaterialAnvilRules.capacity(state));
        int stage = state.hasProperty(MaterialAnvilBlock.DAMAGE_STAGE) ? state.getValue(MaterialAnvilBlock.DAMAGE_STAGE)
                : state.is(Blocks.DAMAGED_ANVIL) ? 2 : state.is(Blocks.CHIPPED_ANVIL) ? 1 : 0;
        int damage = stack.getOrDefault(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), FunctionalBlockRules.minimumDamage(stage, maximum));
        return new View(remaining(damage, maximum), maximum);
    }

    public static View of(MaterialAnvilBlockEntity entity) {
        return new View(remaining(entity.damage(), entity.durability()), entity.durability());
    }

    /** Ordinary getBlockEntity would create pristine data for a missing entity; inspection must not. */
    @Nullable public static MaterialAnvilBlockEntity entity(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        var entity = level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
        return entity instanceof MaterialAnvilBlockEntity anvil && !anvil.isRemoved()
                && anvil.getBlockState() == level.getBlockState(pos) ? anvil : null;
    }
}
