package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.R196OreBlock;
import io.github.ningmeng123.mite.block.R196InfestedNetherrackBlock;
import io.github.ningmeng123.mite.loot.R196OreBonusFunction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every missing active R196 mineral, plus modern host variants. @author NingMeng123 */
public final class MiteOreBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    public static final DeferredBlock<R196OreBlock> SILVER_ORE = ore("silver_ore", 2.5F, false, false);
    public static final DeferredBlock<R196OreBlock> DEEPSLATE_SILVER_ORE = ore("deepslate_silver_ore", 2.5F, true, false);
    public static final DeferredBlock<R196OreBlock> MITHRIL_ORE = ore("mithril_ore", 3.5F, false, false);
    public static final DeferredBlock<R196OreBlock> DEEPSLATE_MITHRIL_ORE = ore("deepslate_mithril_ore", 3.5F, true, false);
    public static final DeferredBlock<R196OreBlock> ADAMANTIUM_ORE = ore("adamantium_ore", 4F, false, true);
    public static final DeferredBlock<R196OreBlock> DEEPSLATE_ADAMANTIUM_ORE = ore("deepslate_adamantium_ore", 4F, true, true);
    // R196 metadata 3 invokes EntityNetherspawn; its AI is a later mob task. Modern silverfish is the explicit adaptation.
    public static final DeferredBlock<InfestedBlock> INFESTED_NETHERRACK = BLOCKS.register("infested_netherrack", () ->
            new R196InfestedNetherrackBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.INFESTED_STONE)));
    static { ITEMS.registerSimpleBlockItem(INFESTED_NETHERRACK, new Item.Properties().stacksTo(4)); }
    private MiteOreBlocks() {}

    private static DeferredBlock<R196OreBlock> ore(String id, float hardness, boolean deep, boolean fireProof) {
        var block = BLOCKS.register(id, () -> new R196OreBlock(BlockBehaviour.Properties
                .ofFullCopy(deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE).strength(hardness, hardness)));
        Item.Properties properties = new Item.Properties().stacksTo(4);
        if (fireProof) properties.fireResistant();
        ITEMS.registerSimpleBlockItem(block, properties);
        return block;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        R196OreBonusFunction.register(bus);
        bus.addListener(MiteOreBlocks::components);
    }
    private static void components(ModifyDefaultComponentsEvent event) {
        for (Item item : new Item[]{Items.COAL_ORE, Items.DEEPSLATE_COAL_ORE, Items.COPPER_ORE,
                Items.DEEPSLATE_COPPER_ORE, Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE, Items.GOLD_ORE,
                Items.DEEPSLATE_GOLD_ORE, Items.NETHER_GOLD_ORE, Items.LAPIS_ORE, Items.DEEPSLATE_LAPIS_ORE,
                Items.REDSTONE_ORE, Items.DEEPSLATE_REDSTONE_ORE, Items.DIAMOND_ORE, Items.DEEPSLATE_DIAMOND_ORE,
                Items.EMERALD_ORE, Items.DEEPSLATE_EMERALD_ORE, Items.NETHER_QUARTZ_ORE}) {
            event.modify(item, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 4));
        }
    }
}
