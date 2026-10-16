package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Grass hardness, independent float vectors and actual server harvest paths. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class GrassMiningGameTests {
    private static final List<BlockState> GRASS_STATES = List.of(
            Blocks.SHORT_GRASS.defaultBlockState(), Blocks.FERN.defaultBlockState(), Blocks.DEAD_BUSH.defaultBlockState(),
            Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER),
            Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER),
            Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER),
            Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));

    @GameTest(template = "empty")
    public static void fiveGrassBlocksAndBothDoubleHalvesUseLocalHardnessOnly(GameTestHelper helper) {
        Player player = groundedPlayer(helper);
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        for (Item item : new Item[]{Items.AIR, Items.WOODEN_SWORD}) {
            player.setItemSlot(EquipmentSlot.MAINHAND, item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
            for (BlockState state : GRASS_STATES) {
                helper.assertValueEqual(state.getDestroySpeed(helper.getLevel(), position), 0.0F,
                        "The registered modern hardness remains zero: " + state);
                helper.assertValueEqual(MiningProgress.effectiveHardness(state, 0.0F), 0.02F,
                        "Only the effective mining hardness maps to the recorded value: " + state);
                helper.assertValueEqual(player.getDigSpeed(state, position), 1.0F, "A fed level-zero empty hand or club has strength one");
                assertProgress(helper, player, state, position, 0x3dc80000, 11,
                        "Grass uses the recorded level-zero progress with " + item);
                helper.assertValueEqual(state.getDestroySpeed(helper.getLevel(), position), 0.0F,
                        "A progress query cannot mutate registered hardness");
            }
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        BlockState ground = Blocks.GRASS_BLOCK.defaultBlockState();
        helper.assertValueEqual(ground.getDestroySpeed(helper.getLevel(), position), 0.6F, "Grass-covered dirt retains registered hardness 0.6");
        assertProgress(helper, player, ground, position, 0x3b800000, 256, "Grass blocks keep their existing effective hardness 0.5");
        for (Block block : new Block[]{Blocks.DANDELION, Blocks.POPPY, Blocks.TORCH, Blocks.BROWN_MUSHROOM}) {
            BlockState state = block.defaultBlockState();
            helper.assertValueEqual(MiningProgress.effectiveHardness(state, state.getDestroySpeed(helper.getLevel(), position)), 0.0F,
                    "Unrelated zero-hardness flowers, torches and mushrooms are not remapped");
            helper.assertValueEqual(state.getDestroyProgress(player, helper.getLevel(), position), 1.0F,
                    "Unrelated zero-hardness blocks still break instantly");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void grassLevelAndFoodVectorsUseTheExistingSurvivalOrder(GameTestHelper helper) {
        Player player = groundedPlayer(helper);
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        // These are independent float32 division/multiplication vectors, not values computed from MiningRules.
        int[] levels = {0, 35, -40, 0, 35, -40, 0};
        int[] food = {1, 1, 1, 0, 0, 0, 1};
        int[] bits = {0x3dc80000, 0x3e2a0001, 0x3ca00003, 0x3ca00001, 0x3d080001, 0x3b800003, 0x3dc80000};
        int[] additions = {11, 7, 52, 52, 31, 256, 11};
        for (int index = 0; index < levels.length; index++) {
            PlayerExperience.setLevels(player, levels[index]);
            player.getFoodData().setFoodLevel(food[index]);
            ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
            for (BlockState state : GRASS_STATES) {
                assertProgress(helper, player, state, position, bits[index], additions[index],
                        "Grass level " + levels[index] + ", food " + food[index] + ": " + state);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void grassShearsAdaptationPreservesOtherModernToolRules(GameTestHelper helper) {
        Player player = groundedPlayer(helper);
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        Item[] items = {Items.WOODEN_SWORD, Items.SHEARS, Items.IRON_SWORD, MiteItems.FLINT_HATCHET.get(),
                Items.IRON_AXE, Items.IRON_HOE, Items.IRON_SHOVEL};
        // Iron sword: zl base efficiency 2 * xj iron multiplier 2 = 4; H=.02 gives bits 3ec80000 and three additions.
        float[] strengths = {1.0F, 8.0F, 4.0F, 2.5F, 8.0F, 1.0F, 1.0F};
        int[] bits = {0x3dc80000, 0x3f800000, 0x3ec80000, 0x3e7a0001, 0x3f800000, 0x3dc80000, 0x3dc80000};
        int[] additions = {11, 1, 3, 5, 1, 11, 11};
        for (int index = 0; index < items.length; index++) {
            ItemStack tool = new ItemStack(items[index]);
            player.setItemSlot(EquipmentSlot.MAINHAND, tool);
            for (BlockState state : GRASS_STATES) {
                helper.assertValueEqual(tool.getDestroySpeed(state), strengths[index], "The declared tool component chooses its intended grass strength");
                helper.assertValueEqual(player.getDigSpeed(state, position), strengths[index], "Level zero applies that tool strength once");
                assertProgress(helper, player, state, position, bits[index], additions[index], "Grass tool behavior for " + items[index]);
            }
        }
        ItemStack shears = new ItemStack(Items.SHEARS);
        player.setItemSlot(EquipmentSlot.MAINHAND, shears);
        helper.assertValueEqual(shears.getDestroySpeed(Blocks.OAK_LEAVES.defaultBlockState()), 8.0F, "R196 shears base four times iron efficiency two applies to leaves");
        helper.assertValueEqual(shears.getDestroySpeed(Blocks.COBWEB.defaultBlockState()), 8.0F, "The same source shearing efficiency applies to cobwebs");
        helper.assertValueEqual(shears.getDestroySpeed(Blocks.WHITE_WOOL.defaultBlockState()), 8.0F, "Cloth is an effective R196 shear material");
        helper.assertValueEqual(shears.getDestroySpeed(Blocks.VINE.defaultBlockState()), 8.0F, "Vines are an effective R196 shear material");
        helper.assertValueEqual(shears.getDestroySpeed(Blocks.GLOW_LICHEN.defaultBlockState()), 8.0F, "Modern glow lichen explicitly follows the vine mapping");
        helper.assertValueEqual(shears.getDestroySpeed(Blocks.DANDELION.defaultBlockState()), 8.0F, "Plants use R196 shear efficiency; zero hardness still prevents wear");
        player.getFoodData().setFoodLevel(0);
        for (BlockState state : GRASS_STATES) {
            assertProgress(helper, player, state, position, 0x3e200001, 7,
                    "Empty food divides shears strength by five before the inverse-hardness float operations");
        }
        PlayerExperience.setLevels(player, -40);
        player.getFoodData().setFoodLevel(1);
        for (BlockState state : GRASS_STATES) {
            assertProgress(helper, player, state, position, 0x3e200003, 7, "Negative levels retain the original float rounding with shears");
        }
        player.getFoodData().setFoodLevel(0);
        for (BlockState state : GRASS_STATES) {
            assertProgress(helper, player, state, position, 0x3d000003, 32,
                    "Combined negative level and empty food retain the separately recorded shears vector");
        }
        PlayerExperience.setLevels(player, 0);
        player.getFoodData().setFoodLevel(1);
        helper.assertValueEqual(Blocks.SHORT_GRASS.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 1.0F,
                "Restoring level and food restores shears' instant grass break");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void grassProgressReadsOneCancelableSpeedAndCannotResurrectBlockedMining(GameTestHelper helper) {
        Player player = groundedPlayer(helper);
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        int[] calls = {0};
        float[] replacement = {1.0F};
        boolean[] canceled = {false};
        Consumer<PlayerEvent.BreakSpeed> listener = event -> {
            if (event.getEntity() == player) {
                calls[0]++;
                event.setNewSpeed(replacement[0]);
                event.setCanceled(canceled[0]);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerEvent.BreakSpeed.class, listener);
        try {
            for (BlockState state : GRASS_STATES) {
                calls[0] = 0;
                replacement[0] = 1.0F;
                canceled[0] = false;
                assertProgress(helper, player, state, position, 0x3dc80000, 11, "A replaced positive speed still enters the local hardness formula");
                helper.assertValueEqual(calls[0], 1, "The progress query evaluates the speed event exactly once");
                for (float blocked : new float[]{0.0F, -1.0F}) {
                    replacement[0] = blocked;
                    helper.assertValueEqual(state.getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                            "Nonpositive event speed blocks grass despite its raw hardness zero");
                }
                replacement[0] = 8.0F;
                canceled[0] = true;
                helper.assertValueEqual(state.getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                        "A canceled instant-strength grass event still cannot break the block");
            }
            helper.assertValueEqual(Blocks.TORCH.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                    "Cancellation still protects an unrelated zero-hardness torch");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        assertProgress(helper, player, Blocks.SHORT_GRASS.defaultBlockState(), position, 0x3dc80000, 11,
                "Removing the temporary listener restores normal grass progress");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualServerStartStopDelaysGrassAndShearsKeepDoublePlantDrops(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        try (PlantFixture fixture = new PlantFixture(helper, base); PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            int sequence = 1;
            for (BlockState state : GRASS_STATES) {
                BlockPos target = fixture.place(state);
                player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                action(helper, player, target, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, sequence++);
                helper.assertTrue(helper.getLevel().getBlockState(target).is(state.getBlock()), "An empty-hand START is no longer an instant grass break");
                action(helper, player, target, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, sequence++);
                helper.assertTrue(helper.getLevel().getBlockState(target).is(state.getBlock()), "An immediate STOP cannot finish unearned grass progress");
                for (int tick = 0; tick < 9; tick++) player.gameMode.tick();
                helper.assertTrue(helper.getLevel().getBlockState(target).is(state.getBlock()), "Ten effective server progress units are still insufficient");
                player.gameMode.tick();
                helper.assertTrue(helper.getLevel().getBlockState(target).isAir(), "The delayed server path finishes on its eleventh effective unit");
                if (state.hasProperty(DoublePlantBlock.HALF)) {
                    helper.assertTrue(helper.getLevel().getBlockState(base).isAir() && helper.getLevel().getBlockState(base.above()).isAir(),
                            "Breaking either double-plant half clears both halves without a floating remainder");
                }
            }
            ItemStack shears = new ItemStack(Items.SHEARS);
            player.setItemSlot(EquipmentSlot.MAINHAND, shears);
            int brokenWithShears = 0;
            for (BlockState state : GRASS_STATES) {
                BlockPos target = fixture.place(state);
                Item drop = state.is(Blocks.TALL_GRASS) ? Items.SHORT_GRASS
                        : state.is(Blocks.LARGE_FERN) ? Items.FERN : state.getBlock().asItem();
                int before = fixture.countDrops(drop);
                action(helper, player, target, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, sequence++);
                helper.assertTrue(helper.getLevel().getBlockState(target).isAir(), "Fed level-zero shears retain actual instant grass harvesting");
                helper.assertValueEqual(fixture.countDrops(drop) - before, state.hasProperty(DoublePlantBlock.HALF) ? 2 : 1,
                        "Modern shear loot yields exactly one single plant or two matching small plants from a double plant");
                helper.assertValueEqual(shears.getDamageValue(), ++brokenWithShears * 5,
                        "R196 shears spend max(trunc(.02*100),trunc(100/20)) = five per grass target");
                if (state.hasProperty(DoublePlantBlock.HALF)) {
                    helper.assertTrue(helper.getLevel().getBlockState(base).isAir() && helper.getLevel().getBlockState(base.above()).isAir(),
                            "Instant shearing also removes both double-plant halves");
                }
            }
        }
        helper.succeed();
    }

    private static Player groundedPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        PlayerExperience.setTotal(player, 0);
        player.getFoodData().setFoodLevel(1);
        ((DualFoodData) player.getFoodData()).mite$setNutrition(0);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        return player;
    }

    private static void assertProgress(GameTestHelper helper, Player player, BlockState state, BlockPos position,
            int expectedBits, int expectedAdditions, String description) {
        float progress = state.getDestroyProgress(player, helper.getLevel(), position);
        helper.assertValueEqual(Float.floatToRawIntBits(progress), expectedBits, description + ": exact float bits");
        float accumulated = 0.0F;
        int additions = 0;
        while (accumulated < 1.0F && additions < 10000) {
            accumulated += progress;
            additions++;
        }
        helper.assertValueEqual(additions, expectedAdditions, description + ": actual float accumulation count");
    }

    private static void action(GameTestHelper helper, ServerPlayer player, BlockPos position,
            ServerboundPlayerActionPacket.Action action, int sequence) {
        player.gameMode.handleBlockBreakAction(position, action, Direction.UP, helper.getLevel().getMaxBuildHeight(), sequence);
    }

    private static PlayerSession newServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-grass-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2))));
            player.setNoGravity(true);
            player.setOnGround(true);
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
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

    private static final class PlantFixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final BlockPos base;
        private final AABB area;
        private final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        private final Set<UUID> oldDrops = new HashSet<>();
        private final boolean tileDrops;

        private PlantFixture(GameTestHelper helper, BlockPos base) {
            this.helper = helper;
            this.base = base;
            this.area = new AABB(base).expandTowards(0, 1, 0).inflate(1.0);
            for (int dy = -2; dy <= 1; dy++) previous.put(base.offset(0, dy, 0), helper.getLevel().getBlockState(base.offset(0, dy, 0)));
            for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) oldDrops.add(entity.getUUID());
            tileDrops = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, helper.getLevel().getServer());
        }

        private BlockPos place(BlockState state) {
            helper.getLevel().setBlock(base.above(), Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(base, Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(base.below(2), Blocks.STONE.defaultBlockState(), 2);
            helper.getLevel().setBlock(base.below(), (state.is(Blocks.DEAD_BUSH) ? Blocks.SAND : Blocks.DIRT).defaultBlockState(), 2);
            if (state.hasProperty(DoublePlantBlock.HALF)) {
                DoublePlantBlock.placeAt(helper.getLevel(), state, base, 3);
                return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? base.above() : base;
            }
            helper.getLevel().setBlockAndUpdate(base, state);
            return base;
        }

        private int countDrops(Item item) {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                    .filter(entity -> !oldDrops.contains(entity.getUUID()) && entity.getItem().is(item))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
        }

        @Override
        public void close() {
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(tileDrops, helper.getLevel().getServer());
            previous.forEach((position, state) -> helper.getLevel().setBlock(position, state, 2));
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                    .filter(entity -> !oldDrops.contains(entity.getUUID())).forEach(ItemEntity::discard);
        }
    }
}
