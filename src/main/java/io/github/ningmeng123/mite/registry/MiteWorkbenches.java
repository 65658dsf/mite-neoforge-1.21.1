package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.MaterialWorkbenchBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** R196 workbench materials, with modern wood variants intentionally unified. @author NingMeng123 */
public final class MiteWorkbenches {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    public static final Map<String, DeferredBlock<MaterialWorkbenchBlock>> TABLES = new LinkedHashMap<>();
    static {
        for (String material : new String[]{"flint", "obsidian", "copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium"}) {
            var block = BLOCKS.registerBlock(material + "_crafting_table",
                    properties -> new MaterialWorkbenchBlock(material, properties),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE));
            TABLES.put(material, block);
            ITEMS.registerSimpleBlockItem(block, new Item.Properties().stacksTo(4));
        }
    }
    private MiteWorkbenches() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
