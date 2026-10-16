package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.mining.LogHarvestRules;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent target/tool matrix and real server authority checks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class LogHarvestGameTests {
    private static final TagKey<Block> RESTRICTED_LOGS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("mite", "logs_requiring_axe"));

    @GameTest(template = "empty")
    public static void allFortyVanillaLogsUseTheDocumentedToolMatrix(GameTestHelper helper) {
        Player player = mockMiner(helper);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        List<Block> vanillaLogs = BuiltInRegistries.BLOCK.stream()
                .filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")
                        && block.defaultBlockState().is(BlockTags.LOGS)).toList();
        // Independently expanded from the pinned 1.21.1 resource JAR: eight wood families and two nether families.
        helper.assertValueEqual(vanillaLogs.size(), 40, "The pinned vanilla LOGS tag contains forty blocks");
        helper.assertValueEqual(BuiltInRegistries.BLOCK.stream().filter(block -> block.defaultBlockState().is(RESTRICTED_LOGS)).count(),
                40L, "The default restriction tag contains only those forty targets");
        Item[] denied = {Items.AIR, Items.STICK, Items.WOODEN_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD};
        Item[] allowed = {MiteItems.FLINT_HATCHET.get(), Items.STONE_AXE, Items.GOLDEN_AXE,
                Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};
        for (Block block : vanillaLogs) {
            BlockState state = block.defaultBlockState();
            helper.assertTrue(state.is(RESTRICTED_LOGS), "Every vanilla log, wood, stripped variant, stem and hyphae is explicitly covered");
            for (Item item : denied) {
                player.setItemSlot(EquipmentSlot.MAINHAND, item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
                helper.assertTrue(LogHarvestRules.isBlocked(player, state), "Rejected target/tool pair: " + block + "/" + item);
                helper.assertValueEqual(state.getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                        "Rejected tools cannot accumulate progress on " + block);
            }
            for (Item item : allowed) {
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
                helper.assertFalse(LogHarvestRules.isBlocked(player, state), "Qualified target/tool pair: " + block + "/" + item);
                helper.assertTrue(state.getDestroyProgress(player, helper.getLevel(), position) > 0.0F,
                        "Qualified axes retain positive mining progress");
            }
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteItems.FLINT_HATCHET.get()));
        assertBits(helper, Blocks.OAK_LOG.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 0x3b855555,
                "The new eligibility gate does not change the established flint/log progress");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void levelsHasteEfficiencyAndAttributesCannotPromoteAnInvalidTool(GameTestHelper helper) {
        Player player = mockMiner(helper);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 1000, 4));
        player.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(100.0);
        ItemStack wood = new ItemStack(Items.WOODEN_AXE);
        wood.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.EFFICIENCY), 5);
        for (int level : new int[]{-40, 0, 35, 200}) {
            PlayerExperience.setLevels(player, level);
            for (ItemStack held : new ItemStack[]{ItemStack.EMPTY, wood, new ItemStack(Items.DIAMOND_PICKAXE)}) {
                player.setItemSlot(EquipmentSlot.MAINHAND, held);
                helper.assertTrue(LogHarvestRules.isBlocked(player, log), "Bonuses cannot promote an invalid tool at level " + level);
                helper.assertValueEqual(log.getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                        "Even a large modern strength bonus cannot bypass log eligibility");
            }
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteItems.FLINT_HATCHET.get()));
            helper.assertFalse(LogHarvestRules.isBlocked(player, log), "A valid tool stays eligible even at a negative level");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unlistedFurnitureGroundPlantsAndStoneKeepTheirExistingBehavior(GameTestHelper helper) {
        Player player = mockMiner(helper);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        BlockState[] states = {Blocks.OAK_PLANKS.defaultBlockState(), Blocks.CRAFTING_TABLE.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState(), Blocks.TORCH.defaultBlockState(), Blocks.BROWN_MUSHROOM.defaultBlockState(),
                Blocks.STONE.defaultBlockState(), Blocks.BAMBOO_BLOCK.defaultBlockState(), Blocks.STRIPPED_BAMBOO_BLOCK.defaultBlockState()};
        for (BlockState state : states) {
            helper.assertFalse(state.is(RESTRICTED_LOGS), "Unlisted target stays outside the new gate: " + state);
            helper.assertFalse(LogHarvestRules.isBlocked(player, state), "The narrow log rule does not restrict " + state);
            helper.assertTrue(state.getDestroyProgress(player, helper.getLevel(), position) > 0.0F,
                    "Existing empty-hand progress survives for " + state);
        }
        assertBits(helper, Blocks.OAK_PLANKS.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 0x3a800000,
                "Level-zero empty-hand planks keep their existing progress");
        assertBits(helper, Blocks.DIRT.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 0x3b800000,
                "Dirt remains on the established base-progress vector");
        assertBits(helper, Blocks.OAK_LEAVES.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 0x3c200000,
                "Leaves remain available for starter sticks");
        assertBits(helper, Blocks.STONE.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 0x3aaaaaab,
                "This feature does not introduce a new stone mining gate");
        helper.assertFalse(Blocks.STONE.defaultBlockState().canHarvestBlock(helper.getLevel(), position, player),
                "Existing stone drop requirements remain separate from removal progress");
        helper.assertValueEqual(Blocks.TORCH.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 1.0F,
                "A zero-hardness unlisted block stays instant");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void directServerDestructionRejectsInvalidToolsAndCorrectsTheClient(GameTestHelper helper) {
        BlockPos position = target(helper);
        try (BlockFixture fixture = new BlockFixture(helper, position); PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            fixture.placeLog();
            for (Item item : new Item[]{Items.AIR, Items.STICK, Items.WOODEN_AXE, Items.DIAMOND_PICKAXE}) {
                ItemStack held = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
                player.setItemSlot(EquipmentSlot.MAINHAND, held);
                helper.assertFalse(player.gameMode.destroyBlock(position), "Direct server removal refuses " + item);
                fixture.assertIntactAndNoDrops("Rejected direct removal retains the actual log");
                helper.assertValueEqual(held.getDamageValue(), 0, "Rejected removal does not damage the held stack");
            }
            List<ClientboundBlockUpdatePacket> updates = new ArrayList<>();
            session.channel().pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    if (message instanceof ClientboundBlockUpdatePacket update) updates.add(update);
                    super.write(context, message, promise);
                }
            });
            player.gameMode.destroyAndAck(position, 9, "log harvest gate test");
            session.channel().runPendingTasks();
            helper.assertTrue(updates.stream().anyMatch(update -> update.getPos().equals(position) && update.getBlockState().is(Blocks.OAK_LOG)),
                    "A rejected predicted break sends the authoritative log state back to the client");
            ItemStack hatchet = new ItemStack(MiteItems.FLINT_HATCHET.get());
            player.setItemSlot(EquipmentSlot.MAINHAND, hatchet);
            helper.assertTrue(player.gameMode.destroyBlock(position), "A qualified axe reaches the actual server harvest path");
            helper.assertTrue(helper.getLevel().getBlockState(position).isAir(), "Allowed removal clears the log");
            helper.assertValueEqual(fixture.logDrops(), 1, "Allowed removal produces one actual log drop");
            helper.assertValueEqual(hatchet.getDamageValue(), 160, "Allowed removal spends the established 160 durability");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void swappingToolsBeforeStopCannotCompleteOrBypassDelayedMining(GameTestHelper helper) {
        BlockPos position = target(helper);
        try (BlockFixture fixture = new BlockFixture(helper, position)) {
            fixture.placeLog();
            try (PlayerSession session = newServerPlayer(helper)) {
                ServerPlayer player = session.player();
                ItemStack hatchet = player.getMainHandItem();
                action(helper, player, position, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 1);
                tickMining(player, 200);
                ItemStack woodenAxe = new ItemStack(Items.WOODEN_AXE);
                player.setItemSlot(EquipmentSlot.MAINHAND, woodenAxe);
                action(helper, player, position, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, 2);
                tickMining(player, 300);
                fixture.assertIntactAndNoDrops("Swapping to a wooden axe blocks STOP and all delayed progress");
                helper.assertFalse(player.gameMode.destroyBlock(position), "A direct final call cannot bypass the swapped tool check");
                player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                action(helper, player, position, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, 3);
                tickMining(player, 30);
                fixture.assertIntactAndNoDrops("The delayed path also stays blocked after switching to the empty hand");
                helper.assertValueEqual(hatchet.getDamageValue(), 0, "Incomplete mining does not spend the original hatchet");
                helper.assertValueEqual(woodenAxe.getDamageValue(), 0, "Rejected delayed removal does not damage the replacement tool");
            }
            // A fresh real session verifies normal delayed completion independently of the abandoned attempt.
            try (PlayerSession session = newServerPlayer(helper)) {
                ServerPlayer player = session.player();
                ItemStack hatchet = player.getMainHandItem();
                action(helper, player, position, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 4);
                tickMining(player, 1);
                action(helper, player, position, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, 5);
                tickMining(player, 243);
                fixture.assertIntactAndNoDrops("245 elapsed progress factors remain below one for the qualified hatchet");
                tickMining(player, 1);
                helper.assertTrue(helper.getLevel().getBlockState(position).isAir(), "The normal server delayed path completes at factor 246");
                helper.assertValueEqual(fixture.logDrops(), 1, "Normal delayed completion produces one log");
                helper.assertValueEqual(hatchet.getDamageValue(), 160, "Normal delayed completion spends wear once");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modePermissionsAndExistingBreakCancellationRemainAuthoritative(GameTestHelper helper) {
        BlockPos position = target(helper);
        try (BlockFixture fixture = new BlockFixture(helper, position); PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            fixture.placeLog();
            player.setGameMode(GameType.CREATIVE);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            helper.assertFalse(LogHarvestRules.isBlocked(player, Blocks.OAK_LOG.defaultBlockState()), "Creative bypasses only the new gate");
            helper.assertTrue(player.gameMode.destroyBlock(position), "Creative can actually remove a log empty-handed");
            helper.assertValueEqual(fixture.logDrops(), 0, "Creative removal retains its modern no-drop behavior");

            fixture.placeLog();
            player.setGameMode(GameType.SPECTATOR);
            helper.assertFalse(LogHarvestRules.isBlocked(player, Blocks.OAK_LOG.defaultBlockState()), "Spectator bypasses the new gate");
            helper.assertFalse(player.gameMode.destroyBlock(position), "Existing spectator permissions still prohibit destruction");

            player.setGameMode(GameType.ADVENTURE);
            ItemStack hatchet = new ItemStack(MiteItems.FLINT_HATCHET.get());
            player.setItemSlot(EquipmentSlot.MAINHAND, hatchet);
            helper.assertFalse(player.gameMode.destroyBlock(position), "A qualified tool alone does not grant adventure permission");
            AdventureModePredicate canBreakLog = new AdventureModePredicate(List.of(BlockPredicate.Builder.block().of(Blocks.OAK_LOG).build()), true);
            hatchet.set(DataComponents.CAN_BREAK, canBreakLog);
            helper.assertTrue(player.gameMode.destroyBlock(position), "A qualified axe with explicit adventure permission can harvest");
            helper.assertValueEqual(hatchet.getDamageValue(), 160, "Adventure permission retains normal tool wear");
            fixture.placeLog();
            ItemStack woodenAxe = new ItemStack(Items.WOODEN_AXE);
            woodenAxe.set(DataComponents.CAN_BREAK, canBreakLog);
            player.setItemSlot(EquipmentSlot.MAINHAND, woodenAxe);
            helper.assertFalse(player.gameMode.destroyBlock(position), "CAN_BREAK does not promote an insufficient wooden axe");

            player.setGameMode(GameType.SURVIVAL);
            ItemStack fresh = new ItemStack(MiteItems.FLINT_HATCHET.get());
            player.setItemSlot(EquipmentSlot.MAINHAND, fresh);
            Consumer<BlockEvent.BreakEvent> cancel = event -> {
                if (event.getPlayer() == player && event.getPos().equals(position)) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BlockEvent.BreakEvent.class, cancel);
            try {
                helper.assertFalse(player.gameMode.destroyBlock(position), "An earlier cancellation is not cleared for a qualified axe");
                helper.assertTrue(helper.getLevel().getBlockState(position).is(Blocks.OAK_LOG), "Canceled harvest preserves the log");
                helper.assertValueEqual(fresh.getDamageValue(), 0, "Canceled harvest does not consume durability");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(player.gameMode.destroyBlock(position), "Removing the temporary cancellation restores normal harvesting");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void toolComponentsMustAgreeWithTheAxeAbilityAndTier(GameTestHelper helper) {
        Player player = mockMiner(helper);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        Tool fastLogs = new Tool(List.of(Tool.Rule.minesAndDrops(BlockTags.LOGS, 8.0F)), 1.0F, 0);
        ItemStack disguisedPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        disguisedPickaxe.set(DataComponents.TOOL, fastLogs);
        ItemStack slowAxe = new ItemStack(Items.IRON_AXE);
        slowAxe.set(DataComponents.TOOL, new Tool(List.of(Tool.Rule.minesAndDrops(BlockTags.LOGS, 1.0F)), 1.0F, 0));
        ItemStack deniedDrops = new ItemStack(Items.IRON_AXE);
        deniedDrops.set(DataComponents.TOOL, new Tool(List.of(Tool.Rule.deniesDrops(BlockTags.LOGS),
                Tool.Rule.minesAndDrops(BlockTags.LOGS, 8.0F)), 1.0F, 0));
        ItemStack wrongTargets = new ItemStack(Items.IRON_AXE);
        wrongTargets.set(DataComponents.TOOL, new Tool(List.of(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, 8.0F)), 1.0F, 0));
        ItemStack promotedWood = new ItemStack(Items.WOODEN_AXE);
        promotedWood.set(DataComponents.TOOL, fastLogs);
        for (ItemStack invalid : new ItemStack[]{disguisedPickaxe, slowAxe, deniedDrops, wrongTargets, promotedWood}) {
            player.setItemSlot(EquipmentSlot.MAINHAND, invalid);
            helper.assertTrue(LogHarvestRules.isBlocked(player, log), "Speed, capability, drop suitability and minimum tier must agree");
        }
        ItemStack customizedAxe = new ItemStack(Items.IRON_AXE);
        customizedAxe.set(DataComponents.TOOL, fastLogs);
        player.setItemSlot(EquipmentSlot.MAINHAND, customizedAxe);
        helper.assertFalse(LogHarvestRules.isBlocked(player, log), "A customized qualifying Tool component is accepted without requiring stock component values");
        helper.succeed();
    }

    private static Player mockMiner(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PlayerExperience.setTotal(player, 0);
        player.getFoodData().setFoodLevel(1);
        player.setOnGround(true);
        return player;
    }

    private static BlockPos target(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 2, 1));
    }

    private static void action(GameTestHelper helper, ServerPlayer player, BlockPos position,
                               ServerboundPlayerActionPacket.Action action, int sequence) {
        player.gameMode.handleBlockBreakAction(position, action, Direction.UP, helper.getLevel().getMaxBuildHeight(), sequence);
    }

    private static void tickMining(ServerPlayer player, int ticks) {
        for (int i = 0; i < ticks; i++) player.gameMode.tick();
    }

    private static void assertBits(GameTestHelper helper, float actual, int expected, String context) {
        helper.assertValueEqual(Float.floatToRawIntBits(actual), expected, context);
    }

    private static PlayerSession newServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-log-test"), false);
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
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteItems.FLINT_HATCHET.get()));
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
        private final BlockPos position;
        private final BlockState previous;
        private final AABB area;
        private final Set<UUID> existing = new HashSet<>();
        private final SpawnedEntityCapture spawned;
        private final boolean oldDrops;

        private BlockFixture(GameTestHelper helper, BlockPos position) {
            this.helper = helper;
            this.position = position;
            previous = helper.getLevel().getBlockState(position);
            area = new AABB(position).inflate(1.0);
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) existing.add(item.getUUID());
            // A newly generated grid chunk can hide its entity sections until the following tick.
            // Capture accepted item spawns now, without counting old entities or another fixture's drops.
            spawned = new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ItemEntity
                    && !existing.contains(entity.getUUID()) && entity.getBoundingBox().intersects(area));
            oldDrops = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, helper.getLevel().getServer());
        }

        private void placeLog() {
            helper.getLevel().setBlockAndUpdate(position, Blocks.OAK_LOG.defaultBlockState());
        }

        private List<ItemEntity> newDrops() {
            return spawned.live(ItemEntity.class);
        }

        private int logDrops() {
            return newDrops().stream().filter(item -> item.getItem().is(Items.OAK_LOG)).mapToInt(item -> item.getItem().getCount()).sum();
        }

        private void assertIntactAndNoDrops(String context) {
            helper.assertTrue(helper.getLevel().getBlockState(position).is(Blocks.OAK_LOG), context);
            helper.assertTrue(newDrops().isEmpty(), "A blocked or incomplete operation creates no drops");
        }

        @Override
        public void close() {
            try {
                helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(oldDrops, helper.getLevel().getServer());
                helper.getLevel().setBlockAndUpdate(position, previous);
            } finally {
                spawned.close();
            }
        }
    }
}
