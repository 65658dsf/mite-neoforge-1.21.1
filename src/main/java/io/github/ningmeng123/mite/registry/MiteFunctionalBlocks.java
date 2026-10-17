package io.github.ningmeng123.mite.registry;

import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.MaterialAnvilBlock;
import io.github.ningmeng123.mite.block.StrongboxBlock;
import io.github.ningmeng123.mite.block.EmeraldEnchantingTableBlock;
import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.block.entity.StrongboxBlockEntity;
import io.github.ningmeng123.mite.block.entity.EmeraldEnchantingTableBlockEntity;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Additional source functional blocks; native iron anvils retain their IDs. @author NingMeng123 */
public final class MiteFunctionalBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mite.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mite.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mite.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Mite.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ANVIL_DAMAGE =
            COMPONENTS.registerComponentType("anvil_damage", builder -> builder.persistent(Codec.intRange(0, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final Map<String, DeferredBlock<MaterialAnvilBlock>> ANVILS = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<StrongboxBlock>> STRONGBOXES = new LinkedHashMap<>();
    static {
        for (String material : List.of("copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium")) {
            String prefix = material.equals("gold") ? "golden" : material;
            if (!material.equals("iron")) {
                var anvil = BLOCKS.register(prefix + "_anvil", () -> new MaterialAnvilBlock(material,
                        BlockBehaviour.Properties.of().mapColor(Blocks.ANVIL.defaultMapColor())
                                .sound(net.minecraft.world.level.block.SoundType.ANVIL)
                                .strength(WorkbenchRules.materialCapacity(material) * 1.2F).noOcclusion()));
                ANVILS.put(material, anvil);
                ITEMS.registerSimpleBlockItem(anvil, material.equals("vibranium") ? new Item.Properties().stacksTo(1).fireResistant() : new Item.Properties().stacksTo(1));
            }
            if (material.equals("vibranium")) continue;
            var strongbox = BLOCKS.register(prefix + "_strongbox", () -> new StrongboxBlock(material,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST).strength(WorkbenchRules.materialCapacity(material) * 1.2F)));
            STRONGBOXES.put(material, strongbox);
            ITEMS.registerSimpleBlockItem(strongbox, new Item.Properties().stacksTo(4));
        }
    }
    public static final DeferredBlock<EmeraldEnchantingTableBlock> EMERALD_ENCHANTING_TABLE =
            BLOCKS.register("emerald_enchanting_table", () -> new EmeraldEnchantingTableBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.ENCHANTING_TABLE).strength(2.4F, 20F)));
    static { ITEMS.registerSimpleBlockItem(EMERALD_ENCHANTING_TABLE, new Item.Properties().stacksTo(4)); }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaterialAnvilBlockEntity>> ANVIL_ENTITY =
            TYPES.register("material_anvil", () -> BlockEntityType.Builder.of(MaterialAnvilBlockEntity::new,
                    anvilBlocks()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StrongboxBlockEntity>> STRONGBOX_ENTITY =
            TYPES.register("strongbox", () -> BlockEntityType.Builder.of(StrongboxBlockEntity::new,
                    STRONGBOXES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EmeraldEnchantingTableBlockEntity>> EMERALD_ENTITY =
            TYPES.register("emerald_enchanting_table", () -> BlockEntityType.Builder.of(EmeraldEnchantingTableBlockEntity::new,
                    EMERALD_ENCHANTING_TABLE.get()).build(null));
    private MiteFunctionalBlocks() {}
    private static Block[] anvilBlocks() {
        var values = new java.util.ArrayList<Block>();
        ANVILS.values().forEach(block -> values.add(block.get()));
        values.addAll(List.of(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL));
        return values.toArray(Block[]::new);
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); TYPES.register(bus); COMPONENTS.register(bus);
        bus.addListener(MiteFunctionalBlocks::capabilities);
        bus.addListener(MiteFunctionalBlocks::components);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        // Source ownership controls player use; hoppers continue using ordinary inventory access.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STRONGBOX_ENTITY.get(), (entity, side) -> new InvWrapper(entity));
    }
    private static void components(ModifyDefaultComponentsEvent event) {
        for (Item item : List.of(Items.ANVIL, Items.CHIPPED_ANVIL, Items.DAMAGED_ANVIL))
            event.modify(item, builder -> builder.set(DataComponents.MAX_STACK_SIZE, 1));
    }
}
