package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.R196CopperspineBlock;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Source metadata4 is a distinct modern identity so its inhabitant persists after nearby ore disappears. @author NingMeng123 */
public final class MiteMobBlocks {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(Mite.MOD_ID);
    public static final DeferredBlock<R196CopperspineBlock> COPPERSPINE_STONE=BLOCKS.register("copperspine_stone",()->new R196CopperspineBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.INFESTED_STONE).randomTicks()));
    static { ITEMS.registerSimpleBlockItem(COPPERSPINE_STONE,new Item.Properties().stacksTo(4)); }
    private MiteMobBlocks() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); bus.addListener(MiteMobBlocks::creative); }
    private static void creative(BuildCreativeModeTabContentsEvent event) { if (event.getTabKey().equals(CreativeModeTabs.NATURAL_BLOCKS)) event.accept(COPPERSPINE_STONE.get()); }
}
