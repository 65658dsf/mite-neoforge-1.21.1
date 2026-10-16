package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Block;

/** Edible berries also plant bushes, while sheared bushes keep their own item identity. @author NingMeng123 */
public final class BlueberriesItem extends ItemNameBlockItem {
    public BlueberriesItem(Properties properties) {
        super(MiteFoodPlants.BLUEBERRY_BUSH.get(), properties);
    }

    @Override public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        // The separate bush item remains the canonical Block.asItem() and pick-block result.
    }
}
