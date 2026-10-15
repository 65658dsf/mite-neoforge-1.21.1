package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.R196FurnaceBlock;
import io.github.ningmeng123.mite.block.entity.R196FurnaceBlockEntity;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The source's six furnace stages retain the vanilla cobblestone furnace identity. @author NingMeng123 */
public final class MiteFurnaces {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mite.MOD_ID);
    public static final DeferredBlock<R196FurnaceBlock> CLAY_OVEN = furnace("clay_oven", .5F, MapColor.CLAY, false);
    public static final DeferredBlock<R196FurnaceBlock> LARGE_CLAY_OVEN = furnace("large_clay_oven", 1F, MapColor.COLOR_ORANGE, false);
    public static final DeferredBlock<R196FurnaceBlock> SANDSTONE_FURNACE = furnace("sandstone_furnace", 1F, MapColor.SAND, false);
    public static final DeferredBlock<R196FurnaceBlock> OBSIDIAN_FURNACE = furnace("obsidian_furnace", 4F, MapColor.COLOR_BLACK, true);
    public static final DeferredBlock<R196FurnaceBlock> NETHERRACK_FURNACE = furnace("netherrack_furnace", 8F, MapColor.NETHER, true);
    public static final List<DeferredBlock<R196FurnaceBlock>> ALL = List.of(CLAY_OVEN, LARGE_CLAY_OVEN,
            SANDSTONE_FURNACE, OBSIDIAN_FURNACE, NETHERRACK_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<R196FurnaceBlockEntity>> BLOCK_ENTITY =
            TYPES.register("r196_furnace", () -> BlockEntityType.Builder.of(R196FurnaceBlockEntity::new,
                    ALL.stream().map(DeferredBlock::get).toArray(R196FurnaceBlock[]::new)).build(null));

    private MiteFurnaces() {}
    private static DeferredBlock<R196FurnaceBlock> furnace(String name, float hardness, MapColor color, boolean needsTool) {
        var block = BLOCKS.register(name, () -> {
            var properties = BlockBehaviour.Properties.of().mapColor(color).strength(hardness, hardness)
                    .lightLevel(state -> state.getValue(AbstractFurnaceBlock.LIT) ? 13 : 0);
            if (needsTool) properties.requiresCorrectToolForDrops();
            return new R196FurnaceBlock(properties);
        });
        ITEMS.registerSimpleBlockItem(block, new Item.Properties().stacksTo(1));
        return block;
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener(MiteFurnaces::capabilities);
        bus.addListener(MiteFurnaces::components);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY.get(), SidedInvWrapper::new);
    }
    private static void components(ModifyDefaultComponentsEvent event) {
        event.modify(Items.FURNACE, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 1));
    }
}
