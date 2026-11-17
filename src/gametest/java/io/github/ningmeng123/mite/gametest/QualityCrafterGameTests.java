package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.crafting.RemovedCrafter;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Retired automation returns exact stored ingredients through real tick, redstone and interaction entrypoints. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class QualityCrafterGameTests {
    private QualityCrafterGameTests() {}

    @GameTest(template = "empty")
    public static void serverTickerRetiresCraftableMaterialBatchesWithoutManufacturing(GameTestHelper helper) {
        for (List<ItemStack> inputs : List.of(
                List.of(new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.STRIPPED_BAMBOO_BLOCK, 2)),
                List.of(new ItemStack(Items.COPPER_BLOCK, 2), new ItemStack(Items.IRON_BLOCK, 2)),
                List.of(new ItemStack(Items.FLINT, 2), new ItemStack(Items.STICK, 4), new ItemStack(Items.STRING, 2)),
                List.of(new ItemStack(Items.COPPER_INGOT, 6), new ItemStack(Items.STICK, 4)))) {
            assertRetirement(helper, inputs, machine -> {
                var ticker = machine.state.getTicker(helper.getLevel(), BlockEntityType.CRAFTER);
                helper.assertTrue(ticker != null, "Saved server crafters receive their retirement ticker");
                ticker.tick(helper.getLevel(), machine.pos, machine.state, machine.crafter);
            });
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scheduledRedstoneActivationReturnsQualityDyeInputsWithoutChangingComponents(GameTestHelper helper) {
        for (Quality quality : List.of(Quality.AVERAGE, Quality.FINE)) {
            assertRetirement(helper, List.of(decoratedChest(helper, quality), new ItemStack(Items.RED_DYE, 2)),
                    machine -> machine.state.tick(helper.getLevel(), machine.pos, helper.getLevel().getRandom()));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void interactionRetiresRepairInventoriesAndRepeatedTriggersNeverDuplicateTheirContents(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack damaged = decoratedChest(helper, Quality.FINE);
        damaged.setDamageValue(9);
        for (List<ItemStack> inputs : List.of(List.of(damaged.copy(), damaged.copy()),
                List.of(damaged.copy(), new ItemStack(Items.STRING, 3)),
                List.of(damaged.copy(), new ItemStack(Items.BLUE_DYE, 2)))) {
            assertRetirement(helper, inputs, machine -> {
                machine.state.useWithoutItem(helper.getLevel(), player,
                        new BlockHitResult(machine.pos.getCenter(), Direction.UP, machine.pos, false));
                helper.assertTrue(player.containerMenu == player.inventoryMenu,
                        "Interacting with a retired crafter cannot open its old inventory menu");
            });
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void packedCrafterInventoryLoadsBeforeRetirementAndReturnsEveryComponentExactlyOnce(GameTestHelper helper) {
        ItemStack damaged = decoratedChest(helper, Quality.FINE);
        damaged.setDamageValue(9);
        assertRetirement(helper, List.of(damaged, new ItemStack(Items.BLUE_DYE, 2), new ItemStack(Items.IRON_BLOCK, 2)), machine -> {
            if (!machine.crafter.isRemoved()) {
                var packed = machine.crafter.saveWithFullMetadata(helper.getLevel().registryAccess());
                packed.putBoolean("keepPacked", true);
                var chunk = helper.getLevel().getChunkAt(machine.pos);
                chunk.removeBlockEntity(machine.pos);
                chunk.setBlockEntityNbt(packed);
                helper.assertTrue(!chunk.getBlockEntities().containsKey(machine.pos) && chunk.getBlockEntityNbt(machine.pos) == packed,
                        "The saved crafter inventory is pending packed NBT with no instantiated block entity");
            }
            RemovedCrafter.remove(helper.getLevel(), machine.pos);
        });
        helper.succeed();
    }

    private static void assertRetirement(GameTestHelper helper, List<ItemStack> inputs, Consumer<Machine> trigger) {
        BlockPos local = new BlockPos(1, 1, 1);
        BlockPos pos = helper.absolutePos(local);
        BlockState state = Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION, FrontAndTop.EAST_UP);
        helper.setBlock(local, state);
        helper.setBlock(local.east(), Blocks.CHEST);
        helper.setBlock(local.south(), MiteWorkbenches.TABLES.get("vibranium").get());
        CrafterBlockEntity crafter = (CrafterBlockEntity)helper.getLevel().getBlockEntity(pos);
        Container output = (Container)helper.getLevel().getBlockEntity(pos.east());
        for (int slot = 0; slot < inputs.size(); slot++) crafter.setItem(slot, inputs.get(slot).copy());
        Machine machine = new Machine(pos, state, crafter);
        try (var spawned = new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ItemEntity)) {
            for (int attempt = 0; attempt < 3; attempt++) {
                trigger.accept(machine);
                helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(),
                        "A saved crafter becomes air before automatic manufacture can run");
                helper.assertTrue(output.isEmpty(), "Even a neighboring vibranium workbench cannot enable automatic output");
                List<ItemStack> returned = spawned.live(ItemEntity.class).stream().map(ItemEntity::getItem).toList();
                helper.assertValueEqual(returned.stream().mapToInt(ItemStack::getCount).sum(),
                        inputs.stream().mapToInt(ItemStack::getCount).sum(),
                        "Each stored item is returned exactly once across repeated retirement triggers");
                for (ItemStack actual : returned) helper.assertTrue(!actual.is(Items.CRAFTER)
                                && inputs.stream().anyMatch(expected -> ItemStack.isSameItemSameComponents(expected, actual)),
                        "Retirement emits only original inventory items, without a crafter block or manufactured product");
                for (ItemStack expected : inputs) {
                    int expectedCount = inputs.stream().filter(stack -> ItemStack.isSameItemSameComponents(expected, stack))
                            .mapToInt(ItemStack::getCount).sum();
                    int actualCount = returned.stream().filter(stack -> ItemStack.isSameItemSameComponents(expected, stack))
                            .mapToInt(ItemStack::getCount).sum();
                    helper.assertValueEqual(actualCount, expectedCount,
                            "Returned stacks preserve damage, quality, enchantments, custom name, color and exact count");
                }
            }
        }
    }

    private static ItemStack decoratedChest(GameTestHelper helper, Quality quality) {
        ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
        EquipmentDurability.setQuality(chest, quality);
        chest.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699, false));
        chest.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 inventory preservation"));
        chest.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.VANISHING_CURSE), 1);
        return chest;
    }

    private record Machine(BlockPos pos, BlockState state, CrafterBlockEntity crafter) {}
}
