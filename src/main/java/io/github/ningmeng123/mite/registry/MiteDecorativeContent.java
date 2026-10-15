package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.DahliaBlock;
import io.github.ningmeng123.mite.block.WitherwoodBushBlock;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.world.R196DecorativeFeature;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** R196 doors, bars and non-native decorative plants. @author NingMeng123 */
public final class MiteDecorativeContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Mite.MOD_ID);
    public static final Map<String, DeferredBlock<DoorBlock>> DOORS = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<IronBarsBlock>> BARS = new LinkedHashMap<>();
    static {
        for (String material : List.of("silver", "gold", "ancient_metal", "mithril", "adamantium")) {
            String prefix = material.equals("gold") ? "golden" : material;
            var door = BLOCKS.register(prefix + "_door", () -> new DoorBlock(BlockSetType.IRON,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_DOOR).strength(WorkbenchRules.materialCapacity(material) * .5F)));
            DOORS.put(material, door);
            ITEMS.register(prefix + "_door", () -> new DoubleHighBlockItem(door.get(), new Item.Properties().stacksTo(1)));
        }
        for (String material : List.of("copper", "gold", "ancient_metal", "mithril", "adamantium")) {
            String prefix = material.equals("gold") ? "golden" : material;
            var bars = BLOCKS.register(prefix + "_bars", () -> new IronBarsBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).strength(WorkbenchRules.materialCapacity(material) * .4F)));
            BARS.put(material, bars);
            ITEMS.registerSimpleBlockItem(bars, new Item.Properties().stacksTo(16));
        }
    }
    public static final DeferredBlock<DahliaBlock> DAHLIA = BLOCKS.register("dahlia", () -> new DahliaBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).randomTicks()));
    public static final DeferredBlock<WitherwoodBushBlock> WITHERWOOD_BUSH = BLOCKS.register("witherwood_bush", () -> new WitherwoodBushBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEAD_BUSH).strength(.02F).randomTicks()));
    public static final DeferredBlock<FlowerPotBlock> POTTED_DAHLIA = BLOCKS.register("potted_dahlia", () -> new FlowerPotBlock(
            () -> (FlowerPotBlock) Blocks.FLOWER_POT, DAHLIA, BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_POPPY)));
    static {
        ITEMS.registerSimpleBlockItem(DAHLIA, new Item.Properties().stacksTo(32));
        ITEMS.registerSimpleBlockItem(WITHERWOOD_BUSH, new Item.Properties().stacksTo(32));
    }
    public static final DeferredHolder<Feature<?>, R196DecorativeFeature> DECORATIVE_PATCH = FEATURES.register("r196_decorative_patch",
            () -> new R196DecorativeFeature(BlockStateConfiguration.CODEC));
    private MiteDecorativeContent() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); FEATURES.register(bus);
        bus.addListener(MiteDecorativeContent::components);
        bus.addListener(MiteDecorativeContent::setup);
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "dahlia"), POTTED_DAHLIA));
    }
    private static void components(ModifyDefaultComponentsEvent event) {
        for (Item door : List.of(Items.COPPER_DOOR, Items.EXPOSED_COPPER_DOOR, Items.WEATHERED_COPPER_DOOR, Items.OXIDIZED_COPPER_DOOR,
                Items.WAXED_COPPER_DOOR, Items.WAXED_EXPOSED_COPPER_DOOR, Items.WAXED_WEATHERED_COPPER_DOOR, Items.WAXED_OXIDIZED_COPPER_DOOR))
            event.modify(door, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 1));
        for (Item flower : List.of(Items.ORANGE_TULIP, Items.BLUE_ORCHID, Items.ALLIUM, Items.OXEYE_DAISY))
            event.modify(flower, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 32));
    }
    public static String material(Block block) {
        if (isCopperDoor(block)) return "copper";
        for (var row : DOORS.entrySet()) if (row.getValue().isBound() && row.getValue().get() == block) return row.getKey();
        for (var row : BARS.entrySet()) if (row.getValue().isBound() && row.getValue().get() == block) return row.getKey();
        return null;
    }
    public static String material(ItemStack stack) { return stack.getItem() instanceof BlockItem item ? material(item.getBlock()) : null; }
    public static boolean isMetalDoor(Block block) { return block instanceof DoorBlock && material(block) != null; }
    public static boolean isCopperDoor(Block block) {
        return block == Blocks.COPPER_DOOR || block == Blocks.EXPOSED_COPPER_DOOR || block == Blocks.WEATHERED_COPPER_DOOR
                || block == Blocks.OXIDIZED_COPPER_DOOR || block == Blocks.WAXED_COPPER_DOOR || block == Blocks.WAXED_EXPOSED_COPPER_DOOR
                || block == Blocks.WAXED_WEATHERED_COPPER_DOOR || block == Blocks.WAXED_OXIDIZED_COPPER_DOOR;
    }
    public static int requiredHarvest(Block block) {
        String material = material(block);
        return material == null ? 0 : switch (material) { case "ancient_metal" -> 3; case "mithril" -> 4; case "adamantium" -> 5; default -> 2; };
    }
    public static int flowerSubtype(Block block) {
        if (DAHLIA.isBound() && block == DAHLIA.get()) return 7;
        if (block == Blocks.BLUE_ORCHID) return 1;
        if (block == Blocks.ALLIUM) return 2;
        if (block == Blocks.ORANGE_TULIP) return 5;
        if (block == Blocks.OXEYE_DAISY) return 8;
        return -1;
    }
}
