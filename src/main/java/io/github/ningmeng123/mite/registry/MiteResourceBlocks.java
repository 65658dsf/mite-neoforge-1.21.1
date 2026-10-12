package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The old metadata-2 gravel is a persistent block identity in modern worlds. @author NingMeng123 */
public final class MiteResourceBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    public static final DeferredBlock<ColoredFallingBlock> NETHER_GRAVEL = BLOCKS.registerBlock(
            "nether_gravel", properties -> new ColoredFallingBlock(new ColorRGBA(0xFF956C67), properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAVEL));

    static {
        ITEMS.registerSimpleBlockItem(NETHER_GRAVEL, new Item.Properties().stacksTo(4));
    }

    private MiteResourceBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(MiteResourceBlocks::components);
        bus.addListener(MiteResourceBlocks::creativeContents);
    }

    private static void components(ModifyDefaultComponentsEvent event) {
        event.modify(Items.GRAVEL, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 4));
    }

    private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.NATURAL_BLOCKS)) event.accept(NETHER_GRAVEL.get());
    }
}
