package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.BlueberryBushBlock;
import io.github.ningmeng123.mite.block.LemonCropBlock;
import io.github.ningmeng123.mite.block.OnionCropBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Plant blocks; onion's edible planting item is registered by MiteFoods. @author NingMeng123 */
public final class MiteFoodPlants {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    public static final DeferredBlock<OnionCropBlock> ONIONS = BLOCKS.register("onion_crop", () ->
            new OnionCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POTATOES).strength(.02F)));
    public static final DeferredBlock<BlueberryBushBlock> BLUEBERRY_BUSH = BLOCKS.register("blueberry_bush", () ->
            new BlueberryBushBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SWEET_BERRY_BUSH).strength(.05F)));
    public static final DeferredItem<BlockItem> BLUEBERRY_BUSH_ITEM = ITEMS.register("blueberry_bush", () ->
            new BlockItem(BLUEBERRY_BUSH.get(), new Item.Properties().stacksTo(8)));
    public static final DeferredBlock<LemonCropBlock> LEMON_CROP = BLOCKS.register("lemon_crop", () ->
            new LemonCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BEETROOTS).strength(.02F)));
    public static final DeferredItem<ItemNameBlockItem> LEMON_SEEDS = ITEMS.register("lemon_seeds", () ->
            new ItemNameBlockItem(LEMON_CROP.get(), new Item.Properties()));
    private MiteFoodPlants() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
}
