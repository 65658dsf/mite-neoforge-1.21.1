package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Source storage blocks and the retained modern copper identity. @author NingMeng123 */
public final class MiteMetalBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    public static final Map<String, DeferredBlock<Block>> STORAGE = new LinkedHashMap<>();
    static {
        for (String material : new String[] {"silver", "ancient_metal", "mithril", "adamantium", "vibranium"}) {
            var block = BLOCKS.registerSimpleBlock(material + "_block", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(io.github.ningmeng123.mite.crafting.WorkbenchRules.materialCapacity(material) * 1.2F)
                    .requiresCorrectToolForDrops());
            STORAGE.put(material, block);
            ITEMS.registerSimpleBlockItem(block, material.equals("vibranium") ? new Item.Properties().stacksTo(4).fireResistant() : new Item.Properties().stacksTo(4));
        }
    }
    private MiteMetalBlocks() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(MiteMetalBlocks::components);
    }
    private static void components(ModifyDefaultComponentsEvent event) {
        event.modify(Items.COPPER_BLOCK, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 4));
    }
    public static String material(net.minecraft.world.level.block.state.BlockState state) {
        if (state.is(Blocks.COPPER_BLOCK)) return "copper";
        for (var entry : STORAGE.entrySet()) if (state.is(entry.getValue().get())) return entry.getKey();
        return null;
    }
    public static int requiredHarvest(String material) {
        return switch (material) { case "copper", "silver" -> 3; case "ancient_metal" -> 4;
            case "mithril" -> 5; case "adamantium", "vibranium" -> 6; default -> throw new IllegalArgumentException(material); };
    }
}
