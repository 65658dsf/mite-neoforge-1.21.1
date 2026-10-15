package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.MantleBlock;
import io.github.ningmeng123.mite.block.RunestoneBlock;
import io.github.ningmeng123.mite.item.RunestoneItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Dimension boundaries and source runestones; no ore is registered here. @author NingMeng123 */
public final class MiteWorldBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    public static final DeferredBlock<MantleBlock> MANTLE = BLOCKS.register("mantle", () -> new MantleBlock(boundary()));
    public static final DeferredBlock<MantleBlock> CORE = BLOCKS.register("core", () -> new MantleBlock(boundary()));
    public static final DeferredBlock<RunestoneBlock> MITHRIL_RUNESTONE = BLOCKS.register("mithril_runestone", () -> new RunestoneBlock(runes()));
    public static final DeferredBlock<RunestoneBlock> ADAMANTIUM_RUNESTONE = BLOCKS.register("adamantium_runestone", () -> new RunestoneBlock(runes()));
    public static final DeferredItem<RunestoneItem> MITHRIL_RUNESTONE_ITEM = ITEMS.register("mithril_runestone", () ->
            new RunestoneItem(MITHRIL_RUNESTONE.get(), new Item.Properties().stacksTo(4)));
    public static final DeferredItem<RunestoneItem> ADAMANTIUM_RUNESTONE_ITEM = ITEMS.register("adamantium_runestone", () ->
            new RunestoneItem(ADAMANTIUM_RUNESTONE.get(), new Item.Properties().stacksTo(4)));
    private static BlockBehaviour.Properties boundary() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).strength(-1F, 3_600_000F).lightLevel(state -> 13).randomTicks();
    }
    private static BlockBehaviour.Properties runes() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(2.4F, 2.4F);
    }
    private MiteWorldBlocks() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus);
        bus.addListener(MiteWorldBlocks::components);
    }
    private static void components(ModifyDefaultComponentsEvent event) {
        event.modify(Items.FLINT_AND_STEEL, builder -> builder.set(DataComponents.MAX_DAMAGE, 16));
    }
}
