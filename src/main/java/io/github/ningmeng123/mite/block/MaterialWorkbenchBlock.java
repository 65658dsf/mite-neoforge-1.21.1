package io.github.ningmeng123.mite.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** A real placed workbench, sharing the modern crafting screen. @author NingMeng123 */
public final class MaterialWorkbenchBlock extends CraftingTableBlock {
    public static final MapCodec<MaterialWorkbenchBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("material").forGetter(MaterialWorkbenchBlock::material), propertiesCodec())
            .apply(instance, MaterialWorkbenchBlock::new));
    private final String material;

    public MaterialWorkbenchBlock(String material, BlockBehaviour.Properties properties) {
        super(properties);
        this.material = material;
    }

    public String material() { return material; }
    @Override public MapCodec<MaterialWorkbenchBlock> codec() { return CODEC; }
}
