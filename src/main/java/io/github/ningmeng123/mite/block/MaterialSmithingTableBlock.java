package io.github.ningmeng123.mite.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Material-specific station using the native smithing menu. @author NingMeng123 */
public final class MaterialSmithingTableBlock extends SmithingTableBlock {
    private static final MapCodec<SmithingTableBlock> MATERIAL_CODEC = RecordCodecBuilder.<MaterialSmithingTableBlock>mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("material").forGetter(MaterialSmithingTableBlock::material), propertiesCodec())
            .apply(instance, MaterialSmithingTableBlock::new)).xmap(block -> block, MaterialSmithingTableBlock.class::cast);
    private final String material;

    public MaterialSmithingTableBlock(String material, Properties properties) {
        super(properties);
        this.material = material;
    }

    public String material() { return material; }

    @Override public MapCodec<SmithingTableBlock> codec() { return MATERIAL_CODEC; }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) -> new SmithingMenu(id, inventory,
                ContainerLevelAccess.create(level, pos)), Component.translatable(getDescriptionId()));
    }
}
