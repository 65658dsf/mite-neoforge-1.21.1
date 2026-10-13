package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real material menus, the server use-item path and persistent nether gravel. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ShardAndGemGameTests {
    private record Pair(Item shard, Item whole) {}

    private static Pair[] pairs() {
        return new Pair[]{
                new Pair(MiteShards.OBSIDIAN_SHARD.get(), Items.OBSIDIAN),
                new Pair(MiteShards.EMERALD_SHARD.get(), Items.EMERALD),
                new Pair(MiteShards.DIAMOND_SHARD.get(), Items.DIAMOND),
                new Pair(MiteShards.QUARTZ_SHARD.get(), Items.QUARTZ),
                new Pair(MiteShards.GLASS_SHARD.get(), Items.GLASS_PANE)
        };
    }

    @GameTest(template = "empty")
    public static void allFiveShardPairsUseRealMenusAndConserveEveryIngredient(GameTestHelper helper) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState previous = helper.getLevel().getBlockState(table);
        helper.getLevel().setBlockAndUpdate(table, TimedCraftingTestSupport.flintTable().defaultBlockState());
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            for (Item item : new Item[]{Items.FLINT, Items.QUARTZ, Items.LAPIS_LAZULI}) {
                helper.assertValueEqual(new ItemStack(item).getMaxStackSize(), 16, "R196 small whole-rock stack limit: " + item);
            }
            for (Item item : new Item[]{Items.DIAMOND, Items.EMERALD}) {
                helper.assertValueEqual(new ItemStack(item).getMaxStackSize(), 32, "R196 whole-gem stack limit: " + item);
            }
            for (Pair pair : pairs()) {
                helper.assertValueEqual(new ItemStack(pair.shard()).getMaxStackSize(), 64, "Fragments stack to 64: " + pair.shard());
                player.getInventory().clearContent();
                player.containerMenu = player.inventoryMenu;
                InventoryMenu backpack = player.inventoryMenu;
                backpack.clearCraftingContent();
                backpack.setCarried(ItemStack.EMPTY);
                backpack.getCraftSlots().setItem(0, new ItemStack(pair.whole(), 2));
                helper.assertTrue(backpack.getSlot(0).getItem().is(pair.shard()), "A real 2x2 menu can dismantle the whole item");
                helper.assertValueEqual(backpack.getSlot(0).getItem().getCount(), 9, "One whole item previews exactly nine fragments");
                helper.assertTrue(backpack.quickMoveStack(player, 0).isEmpty(), "Direct quick move cannot bypass the server manufacturing period");
                TimedCraftingTestSupport.craftOne(helper, player, backpack, ClickType.PICKUP);
                helper.assertValueEqual(backpack.getCraftSlots().getItem(0).getCount(), 1, "One menu transfer consumes one whole item");
                helper.assertValueEqual(count(player.getInventory(), pair.shard()), 9, "Exactly nine fragments reached inventory");
                TimedCraftingTestSupport.craftOne(helper, player, backpack, ClickType.QUICK_MOVE);
                helper.assertValueEqual(count(player.getInventory(), pair.shard()), 18, "A normal Shift click consumes the remaining whole item once");
                helper.assertTrue(backpack.getCraftSlots().isEmpty() && backpack.getSlot(0).getItem().isEmpty(), "No phantom dismantling result remains");

                // Reuse the actual output stack, first demonstrating that a count of nine is not nine occupied cells.
                ItemStack fragments = takeInventory(player, pair.shard(), 18);
                backpack.getCraftSlots().setItem(0, fragments.split(9));
                helper.assertTrue(backpack.getSlot(0).getItem().isEmpty(), "Nine fragments in one 2x2 cell cannot pack a whole item");
                backpack.clicked(0, 0, ClickType.QUICK_MOVE, player);
                helper.assertValueEqual(backpack.getCraftSlots().getItem(0).getCount(), 9, "Rejected packing consumes nothing");
                ItemStack returned = backpack.getCraftSlots().removeItem(0, 9);
                fragments.grow(returned.getCount());
                helper.assertValueEqual(fragments.getCount(), 18, "All real fragments remain available for the workbench");

                CraftingMenu bench = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
                player.containerMenu = bench;
                try {
                    for (int slot = 1; slot <= 9; slot++) bench.getSlot(slot).set(fragments.split(2));
                    helper.assertTrue(fragments.isEmpty(), "All eighteen dismantled fragments are now in the actual 3x3 grid");
                    helper.assertTrue(bench.getSlot(0).getItem().is(pair.whole()) && bench.getSlot(0).getItem().getCount() == 1,
                            "Nine occupied cells restore exactly the corresponding whole item, including glass pane and obsidian block");
                    TimedCraftingTestSupport.craftOne(helper, player, bench, ClickType.PICKUP);
                    helper.assertTrue(bench.getCarried().isEmpty() && count(player.getInventory(), pair.whole()) == 1, "One completed manufacturing period restores one whole item");
                    for (int slot = 1; slot <= 9; slot++) helper.assertValueEqual(bench.getSlot(slot).getItem().getCount(), 1, "Each occupied cell consumes exactly one fragment");
                    TimedCraftingTestSupport.craftOne(helper, player, bench, ClickType.QUICK_MOVE);
                    helper.assertValueEqual(count(player.getInventory(), pair.whole()), 2, "The complete round trip restores both original whole items");
                    helper.assertValueEqual(count(player.getInventory(), pair.shard()), 0, "No fragments are duplicated into inventory");
                    for (int slot = 0; slot <= 9; slot++) helper.assertTrue(bench.getSlot(slot).getItem().isEmpty(), "Exhausted packing has no remaining ingredient or output");
                    helper.assertTrue(bench.getCarried().isEmpty(), "The cursor is empty after the round trip");
                    helper.assertTrue(bench.quickMoveStack(player, 0).isEmpty(), "An extra transfer cannot create another item");
                } finally {
                    bench.removed(player);
                    player.containerMenu = player.inventoryMenu;
                }
            }
        } finally {
            helper.getLevel().setBlockAndUpdate(table, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fourWholeGemsUseTheRealServerInteractionWithoutHealing(GameTestHelper helper) {
        Item[] items = {Items.LAPIS_LAZULI, Items.QUARTZ, Items.EMERALD, Items.DIAMOND};
        int[] rewards = {25, 50, 250, 500};
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            for (int index = 0; index < items.length; index++) {
                PlayerExperience.setTotal(player, 0);
                player.setHealth(2.0F);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(items[index], 2));
                InteractionResult result = use(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(result.consumesAction(), "The real use-item path accepts the whole gem");
                helper.assertValueEqual(player.totalExperience, rewards[index], "The independently specified reward is added once");
                helper.assertTrue(player.getMainHandItem().is(items[index]) && player.getMainHandItem().getCount() == 1, "One of the two held gems is consumed");
                helper.assertValueEqual(player.getHealth(), 2.0F, "Sacrifice does not heal the player");
            }
            helper.assertValueEqual(player.experienceLevel, 8, "Five hundred total points correspond to R196 level eight");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void debtMaximumCreativeAndModernOffhandKeepSacrificeSemantics(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            PlayerExperience.setTotal(player, -600);
            player.setHealth(2.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
            helper.assertTrue(use(player, InteractionHand.MAIN_HAND).consumesAction(), "A negative-level player can sacrifice a whole gem");
            helper.assertValueEqual(player.totalExperience, -100, "Five hundred points repay signed experience debt");
            helper.assertValueEqual(player.experienceLevel, -5, "Remaining negative one hundred points are level minus five");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Debt repayment still spends one gem");

            PlayerExperience.setTotal(player, 203000);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
            use(player, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(player.totalExperience, 203000, "The total remains at the explicit maximum");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "A sacrifice at maximum experience still consumes one gem");

            player.setGameMode(GameType.CREATIVE);
            PlayerExperience.setTotal(player, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
            helper.assertTrue(use(player, InteractionHand.MAIN_HAND).consumesAction(), "The actual creative use path accepts sacrifice");
            helper.assertValueEqual(player.totalExperience, 500, "Creative mode still receives the reward");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Creative mode does not restore the consumed gem");

            player.setGameMode(GameType.SURVIVAL);
            PlayerExperience.setTotal(player, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 7));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 2));
            helper.assertTrue(use(player, InteractionHand.OFF_HAND).consumesAction(), "Modern offhand support is an explicit extension, exercised through the same server use path");
            helper.assertValueEqual(player.totalExperience, 500, "Offhand sacrifice receives exactly one reward");
            helper.assertTrue(player.getOffhandItem().is(Items.DIAMOND) && player.getOffhandItem().getCount() == 1, "Only the used offhand loses one gem");
            helper.assertTrue(player.getMainHandItem().is(Items.STICK) && player.getMainHandItem().getCount() == 7, "The unrelated main hand is unchanged");
            helper.assertValueEqual(player.getHealth(), 2.0F, "None of debt repayment, maximum, creative or offhand sacrifice heals");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fragmentsUnrelatedItemsAndEarlierCancellationCannotLoseItems(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            PlayerExperience.setTotal(player, 71);
            player.setHealth(2.0F);
            for (Pair pair : pairs()) assertPassUnchanged(helper, player, pair.shard());
            assertPassUnchanged(helper, player, Items.FLINT);
            assertPassUnchanged(helper, player, Items.STICK);
            Consumer<PlayerInteractEvent.RightClickItem> cancel = event -> {
                if (event.getEntity() == player) {
                    event.setCancellationResult(InteractionResult.FAIL);
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerInteractEvent.RightClickItem.class, cancel);
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
                helper.assertValueEqual(use(player, InteractionHand.MAIN_HAND), InteractionResult.FAIL, "Earlier cancellation is preserved by the real server interaction");
                helper.assertTrue(player.getMainHandItem().is(Items.DIAMOND) && player.getMainHandItem().getCount() == 2, "Canceled use keeps both gems");
                helper.assertValueEqual(player.totalExperience, 71, "Canceled use changes no experience");
                helper.assertValueEqual(player.getHealth(), 2.0F, "Canceled use changes no health");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(use(player, InteractionHand.MAIN_HAND).consumesAction(), "Removing the temporary listener restores normal behavior");
            helper.assertValueEqual(player.totalExperience, 571, "The post-cancellation control receives one reward");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "The control spends exactly one gem");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void netherGravelFallsWithItsIdentityAndSilkHarvestsItself(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(new BlockPos(1, 0, 1));
        BlockPos source = floor.above(4);
        BlockPos landed = floor.above();
        FallingBlockEntity falling = null;
        try (BlockFixture fixture = new BlockFixture(helper, floor); PlayerSession session = newServerPlayer(helper)) {
            for (int y = 1; y <= 4; y++) helper.getLevel().setBlockAndUpdate(floor.above(y), Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            BlockState state = MiteResourceBlocks.NETHER_GRAVEL.get().defaultBlockState();
            helper.getLevel().setBlockAndUpdate(source, state);
            falling = FallingBlockEntity.fall(helper.getLevel(), source, state);
            helper.assertTrue(falling.getBlockState().is(MiteResourceBlocks.NETHER_GRAVEL.get()), "The actual falling entity stores the distinct nether block identity");
            // Exercise actual entity physics and placement synchronously so cleanup also runs on assertion failures.
            int ticks = 0;
            while (!falling.isRemoved() && ticks++ < 80) falling.tick();
            helper.assertTrue(falling.isRemoved(), "The real falling entity finishes landing");
            helper.assertTrue(helper.getLevel().getBlockState(source).isAir(), "Falling removed the source block");
            helper.assertTrue(helper.getLevel().getBlockState(landed).is(MiteResourceBlocks.NETHER_GRAVEL.get()), "Landing preserves nether gravel rather than converting to ordinary gravel");
            helper.assertTrue(fixture.newDrops().isEmpty(), "Normal landing places the block without a duplicate item");
            helper.assertValueEqual(new ItemStack(MiteResourceBlocks.NETHER_GRAVEL.get()).getMaxStackSize(), 4, "Nether gravel has the declared four-block stack limit");
            ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
            shovel.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
            session.player().setItemInHand(InteractionHand.MAIN_HAND, shovel);
            helper.assertTrue(session.player().gameMode.destroyBlock(landed), "A real server harvest removes the landed block");
            helper.assertTrue(helper.getLevel().getBlockState(landed).isAir(), "The silk-harvested block is gone");
            var drops = fixture.newDrops();
            helper.assertValueEqual(drops.size(), 1, "Silk harvest produces exactly one item entity");
            helper.assertTrue(drops.getFirst().getItem().is(MiteResourceBlocks.NETHER_GRAVEL.get().asItem())
                    && drops.getFirst().getItem().getCount() == 1, "Silk harvest preserves the nether gravel item identity");
        } finally {
            if (falling != null) falling.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void netherGravelChangesOnlyTheConfiguredTargetAndKeepsVanillaPlacement(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.withDefaultNamespace("ore_gravel_nether");
        var configured = helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(id);
        helper.assertTrue(configured != null && configured.feature() == Feature.ORE
                && configured.config() instanceof OreConfiguration, "The existing vanilla configured-feature identity remains an ore feature");
        OreConfiguration ore = (OreConfiguration) configured.config();
        helper.assertValueEqual(ore.size, 33, "The original nether gravel vein size remains thirty-three");
        helper.assertValueEqual(ore.discardChanceOnAirExposure, 0.0F, "The original exposure discard chance remains zero");
        helper.assertValueEqual(ore.targetStates.size(), 1, "There is still one netherrack replacement target");
        helper.assertTrue(ore.targetStates.getFirst().state.is(MiteResourceBlocks.NETHER_GRAVEL.get()), "The configured output now has the persistent nether-gravel identity");
        var targetJson = OreConfiguration.TargetBlockState.CODEC.encodeStart(
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), ore.targetStates.getFirst()).getOrThrow();
        helper.assertValueEqual(targetJson, JsonParser.parseString("""
                {"target":{"predicate_type":"minecraft:block_match","block":"minecraft:netherrack"},
                 "state":{"Name":"mite:nether_gravel"}}
                """), "The replacement rule itself still targets vanilla netherrack");
        var placed = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(id);
        helper.assertTrue(placed != null, "The existing vanilla placed feature remains registered");
        var placementJson = PlacedFeature.DIRECT_CODEC.encodeStart(
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), placed).getOrThrow();
        // Literal source: 1.21.1 client-extra jar data/minecraft/worldgen/placed_feature/ore_gravel_nether.json.
        helper.assertValueEqual(placementJson, JsonParser.parseString("""
                {"feature":"minecraft:ore_gravel_nether","placement":[
                  {"type":"minecraft:count","count":2},
                  {"type":"minecraft:in_square"},
                  {"type":"minecraft:height_range","height":{"type":"minecraft:uniform",
                    "max_inclusive":{"absolute":41},"min_inclusive":{"absolute":5}}},
                  {"type":"minecraft:biome"}]}
                """), "The complete vanilla placement list, count and height range remain unchanged");
        helper.succeed();
    }

    private static InteractionResult use(ServerPlayer player, InteractionHand hand) {
        return player.gameMode.useItem(player, player.serverLevel(), player.getItemInHand(hand), hand);
    }

    private static void assertPassUnchanged(GameTestHelper helper, ServerPlayer player, Item item) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
        helper.assertValueEqual(use(player, InteractionHand.MAIN_HAND), InteractionResult.PASS, "This item has no sacrifice action: " + item);
        helper.assertTrue(player.getMainHandItem().is(item) && player.getMainHandItem().getCount() == 2, "Unsupported use consumes no item");
        helper.assertValueEqual(player.totalExperience, 71, "Unsupported use awards no experience");
        helper.assertValueEqual(player.getHealth(), 2.0F, "Unsupported use changes no health");
    }

    private static int count(Container inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static ItemStack takeInventory(ServerPlayer player, Item item, int amount) {
        ItemStack moved = new ItemStack(item, amount);
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                remaining -= player.getInventory().removeItem(slot, remaining).getCount();
            }
        }
        if (remaining != 0) throw new IllegalStateException("The preceding real crafting output is missing");
        return moved;
    }

    private static PlayerSession newServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-gem-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2))));
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            player.removeAllEffects();
            return new PlayerSession(player, channel);
        } catch (RuntimeException | Error failure) {
            ServerPlayer registered = helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID());
            if (registered != null) helper.getLevel().getServer().getPlayerList().remove(registered);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record PlayerSession(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override
        public void close() {
            try {
                ServerPlayer registered = player.server.getPlayerList().getPlayer(player.getUUID());
                if (registered != null) player.server.getPlayerList().remove(registered);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    private static final class BlockFixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final Set<UUID> existingDrops = new HashSet<>();
        private final AABB area;
        private final boolean previousTileDrops;

        private BlockFixture(GameTestHelper helper, BlockPos floor) {
            this.helper = helper;
            this.area = new AABB(floor).expandTowards(0, 5, 0).inflate(1.0);
            for (int y = 4; y >= 0; y--) originals.put(floor.above(y), helper.getLevel().getBlockState(floor.above(y)));
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) existingDrops.add(item.getUUID());
            var rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS);
            previousTileDrops = rule.get();
            rule.set(true, helper.getLevel().getServer());
        }

        private java.util.List<ItemEntity> newDrops() {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                    .filter(item -> !existingDrops.contains(item.getUUID())).toList();
        }

        @Override
        public void close() {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(previousTileDrops, helper.getLevel().getServer());
            originals.forEach((pos, state) -> helper.getLevel().setBlockAndUpdate(pos, state));
            newDrops().forEach(ItemEntity::discard);
        }
    }
}
