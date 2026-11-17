package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.mining.FlintGravelDrops;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import net.minecraft.world.item.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual block loot/event/entity flow and safe replacement boundaries. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FlintGravelGameTests {
    @GameTest(template = "empty")
    public static void actualGravelDropsCreateExactlyOneResultPerRoll(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        long nextTestSeed = helper.getLevel().random.nextLong();
        try (DropFixture fixture = new DropFixture(helper, position)) {
            // All 256 rolls happen synchronously: no entity ticks, merging or other world ticks intervene.
            helper.getLevel().random.setSeed(196L);
            int chips = 0;
            int flint = 0;
            int gravel = 0;
            int minerals = 0;
            for (int roll = 0; roll < 256; roll++) {
                Block.dropResources(Blocks.GRAVEL.defaultBlockState(), helper.getLevel(), position, null, player, ItemStack.EMPTY);
                List<ItemEntity> drops = fixture.newDrops();
                helper.assertValueEqual(drops.size(), 1, "The actual loot/event path creates one entity for roll " + roll);
                ItemStack result = drops.getFirst().getItem();
                helper.assertValueEqual(result.getCount(), 1, "Replacement does not duplicate the ordinary drop");
                if (result.is(MiteItems.FLINT_CHIP.get())) chips++;
                else if (result.is(Items.FLINT)) flint++;
                else if (result.is(Items.GRAVEL)) gravel++;
                else { helper.assertTrue(minerals().contains(result.getItem()), "The completed R196 mineral branch emits only its eight results"); minerals++; }
                drops.forEach(ItemEntity::discard);
            }
            helper.assertValueEqual(chips + flint + gravel + minerals, 256, "All actual drops are accounted for exactly once");
            helper.assertTrue(minerals > 0, "Mineral outcomes no longer fall back to gravel");
            helper.assertTrue(chips > 0, "The seeded real drop path produces registered flint-chip entities");
            helper.assertTrue(flint > 0, "The seeded real drop path also produces whole flint");
            helper.assertTrue(gravel > 0, "The seeded real drop path retains gravel results");
        } finally {
            // Do not leave subsequent tests on the intentionally fixed stream; this is a reseed, not an RNG-state snapshot.
            helper.getLevel().random.setSeed(nextTestSeed);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualSilkTouchAndSuppressedDropsRemainIntact(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        ItemStack silkTool = new ItemStack(Items.IRON_SHOVEL);
        silkTool.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        Consumer<BlockDropsEvent> cancel = event -> {
            if (event.getLevel() == helper.getLevel() && event.getPos().equals(position) && event.getBreaker() == player) {
                event.setCanceled(true);
            }
        };
        try (DropFixture fixture = new DropFixture(helper, position)) {
            for (int roll = 0; roll < 16; roll++) {
                Block.dropResources(Blocks.GRAVEL.defaultBlockState(), helper.getLevel(), position, null, player, silkTool);
                List<ItemEntity> drops = fixture.newDrops();
                helper.assertValueEqual(drops.size(), 1, "Actual silk-touch loot creates exactly one drop");
                helper.assertTrue(drops.getFirst().getItem().is(Items.GRAVEL), "Silk Touch retains gravel instead of rerolling it");
                helper.assertValueEqual(drops.getFirst().getItem().getCount(), 1, "Silk Touch retains the normal stack count");
                drops.forEach(ItemEntity::discard);
            }
            fixture.setTileDrops(false);
            Block.dropResources(Blocks.GRAVEL.defaultBlockState(), helper.getLevel(), position, null, player, ItemStack.EMPTY);
            helper.assertTrue(fixture.newDrops().isEmpty(), "Disabled tile drops are not recreated by the replacement event");
            fixture.setTileDrops(true);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BlockDropsEvent.class, cancel);
            try {
                Block.dropResources(Blocks.GRAVEL.defaultBlockState(), helper.getLevel(), position, null, player, ItemStack.EMPTY);
                helper.assertTrue(fixture.newDrops().isEmpty(), "A canceled actual drop event creates no entities");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replacementRespectsContextsAndPreservesOtherModsDrops(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        try (DropFixture fixture = new DropFixture(helper, position)) {
            List<BlockDropsEvent> unchanged = new ArrayList<>();
            unchanged.add(event(helper, position, Blocks.GRAVEL.defaultBlockState(), null, ordinary(helper, position)));
            // A datapack may make a non-gravel block drop gravel; the block-state guard must still apply.
            unchanged.add(event(helper, position, Blocks.STONE.defaultBlockState(), player, ordinary(helper, position)));
            for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
                unchanged.add(event(helper, position, Blocks.GRAVEL.defaultBlockState(), helper.makeMockPlayer(mode), ordinary(helper, position)));
            }
            BlockDropsEvent canceled = event(helper, position, Blocks.GRAVEL.defaultBlockState(), player, ordinary(helper, position));
            canceled.setCanceled(true);
            unchanged.add(canceled);
            for (BlockDropsEvent ignored : unchanged) {
                ItemEntity original = ignored.getDrops().getFirst();
                FlintGravelDrops.onBlockDrops(ignored);
                helper.assertValueEqual(ignored.getDrops().size(), 1, "Ignored contexts retain the original list length");
                helper.assertTrue(ignored.getDrops().getFirst() == original, "Ignored contexts retain the original entity unchanged");
            }

            BlockDropsEvent empty = event(helper, position, Blocks.GRAVEL.defaultBlockState(), player, new ArrayList<>());
            FlintGravelDrops.onBlockDrops(empty);
            helper.assertTrue(empty.getDrops().isEmpty(), "A datapack-suppressed ordinary drop is not recreated");

            ItemEntity extra = item(helper, position, new ItemStack(Items.DIAMOND, 3));
            BlockDropsEvent onlyExtra = event(helper, position, Blocks.GRAVEL.defaultBlockState(), player, new ArrayList<>(List.of(extra)));
            FlintGravelDrops.onBlockDrops(onlyExtra);
            helper.assertValueEqual(onlyExtra.getDrops().size(), 1, "An unrelated-only drop list gains no ordinary result");
            helper.assertTrue(onlyExtra.getDrops().getFirst() == extra, "An unrelated drop keeps its original entity identity");

            List<ItemEntity> mixed = ordinary(helper, position);
            mixed.add(item(helper, position, new ItemStack(Items.FLINT)));
            mixed.add(extra);
            BlockDropsEvent replacement = event(helper, position, Blocks.GRAVEL.defaultBlockState(), player, mixed);
            FlintGravelDrops.onBlockDrops(replacement);
            helper.assertValueEqual(replacement.getDrops().size(), 2, "Only the ordinary gravel/flint entries are replaced by one result");
            helper.assertTrue(replacement.getDrops().contains(extra), "Unrelated mod-added loot retains its original entity");
            helper.assertValueEqual(extra.getItem().getCount(), 3, "Unrelated loot retains its stack count");
            ItemStack ordinaryResult = replacement.getDrops().stream().filter(drop -> drop != extra).findFirst().orElseThrow().getItem();
            helper.assertValueEqual(ordinaryResult.getCount(), 1, "Multiple ordinary inputs do not create duplicate replacement output");
            helper.assertTrue(ordinaryResult.is(Items.GRAVEL) || ordinaryResult.is(Items.FLINT)
                            || ordinaryResult.is(MiteItems.FLINT_CHIP.get()) || minerals().contains(ordinaryResult.getItem()),
                    "The replacement remains inside the full R196 gravel outcome set");
            helper.assertTrue(fixture.newDrops().isEmpty(), "Direct event mutation alone does not spawn any entities");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void netherGravelDropsFollowBlockIdentityEvenInTheOverworld(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        long nextSeed = helper.getLevel().random.nextLong();
        try (DropFixture fixture = new DropFixture(helper, position)) {
            helper.getLevel().random.setSeed(196L);
            int nuggets = 0;
            int fragments = 0;
            for (int roll = 0; roll < 2048; roll++) {
                Block.dropResources(MiteResourceBlocks.NETHER_GRAVEL.get().defaultBlockState(), helper.getLevel(), position, null, player, ItemStack.EMPTY);
                List<ItemEntity> drops = fixture.newDrops();
                helper.assertValueEqual(drops.size(), 1, "Nether gravel emits one entity per player harvest");
                ItemStack stack = drops.getFirst().getItem();
                helper.assertValueEqual(stack.getCount(), 1, "Nether gravel never duplicates the roll");
                if (stack.is(Items.GOLD_NUGGET)) nuggets++;
                else if (stack.is(MiteShards.QUARTZ_SHARD.get())) fragments++;
                else helper.assertTrue(stack.is(MiteResourceBlocks.NETHER_GRAVEL.asItem()) || stack.is(Items.FLINT)
                                || stack.is(MiteItems.FLINT_CHIP.get()), "Only gold/quartz can replace the Nether mineral branch");
                drops.forEach(ItemEntity::discard);
            }
            helper.assertTrue(nuggets > 0 && fragments > 0, "Both converted mineral families are exercised in the overworld");
        } finally {
            helper.getLevel().random.setSeed(nextSeed);
        }
        helper.succeed();
    }

    private static Set<Item> minerals() {
        return Set.of(MiteMaterials.COPPER_NUGGET.get(), MiteMaterials.SILVER_NUGGET.get(), Items.GOLD_NUGGET,
                MiteShards.OBSIDIAN_SHARD.get(), MiteShards.EMERALD_SHARD.get(), MiteShards.DIAMOND_SHARD.get(),
                MiteMaterials.MITHRIL_NUGGET.get(), MiteMaterials.ADAMANTIUM_NUGGET.get());
    }

    private static BlockDropsEvent event(GameTestHelper helper, BlockPos position, BlockState state, Entity breaker, List<ItemEntity> drops) {
        return new BlockDropsEvent(helper.getLevel(), position, state, null, drops, breaker, ItemStack.EMPTY);
    }

    private static List<ItemEntity> ordinary(GameTestHelper helper, BlockPos position) {
        return new ArrayList<>(List.of(item(helper, position, new ItemStack(Items.GRAVEL))));
    }

    private static ItemEntity item(GameTestHelper helper, BlockPos position, ItemStack stack) {
        return new ItemEntity(helper.getLevel(), position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5, stack);
    }

    private static final class DropFixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final AABB area;
        private final SpawnedEntityCapture capture;
        private final boolean oldTileDrops;

        private DropFixture(GameTestHelper helper, BlockPos position) {
            this.helper = helper;
            area = new AABB(position).inflate(1.0);
            capture = new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ItemEntity && area.contains(entity.position()));
            oldTileDrops = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            setTileDrops(true);
        }

        private void setTileDrops(boolean enabled) {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(enabled, helper.getLevel().getServer());
        }

        private List<ItemEntity> newDrops() {
            return capture.live(ItemEntity.class);
        }

        @Override
        public void close() {
            setTileDrops(oldTileDrops);
            capture.close();
        }
    }
}
