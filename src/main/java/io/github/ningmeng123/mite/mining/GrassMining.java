package io.github.ningmeng123.mite.mining;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/** R196 single-block grasses plus explicit modern double-plant counterparts. @author NingMeng123 */
public final class GrassMining {
    private static final List<Block> PLANTS = List.of(Blocks.SHORT_GRASS, Blocks.FERN,
            Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.DEAD_BUSH);

    private GrassMining() {}

    public static boolean matches(BlockState state) {
        return PLANTS.contains(state.getBlock()) || state.getBlock() == io.github.ningmeng123.mite.registry.MiteDecorativeContent.WITHERWOOD_BUSH.get();
    }

    public static void modifyShears(ModifyDefaultComponentsEvent event) {
        // Modern shears are iron: preserve every existing mining/drop rule outside these plants.
        Tool original = Items.SHEARS.components().get(DataComponents.TOOL);
        if (original == null) return;
        List<Tool.Rule> rules = new ArrayList<>(original.rules());
        List<Block> plants = new ArrayList<>(PLANTS);
        plants.add(io.github.ningmeng123.mite.registry.MiteDecorativeContent.WITHERWOOD_BUSH.get());
        rules.addFirst(Tool.Rule.overrideSpeed(plants, 8.0F));
        event.modify(Items.SHEARS, builder -> builder.set(DataComponents.TOOL,
                new Tool(rules, original.defaultMiningSpeed(), original.damagePerBlock())));
    }
}
