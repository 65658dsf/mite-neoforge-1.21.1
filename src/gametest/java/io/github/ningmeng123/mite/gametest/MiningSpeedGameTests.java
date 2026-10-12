package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Modern strength, R196 progress, event composition and actual server breaking. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
@SuppressWarnings("deprecation")
public final class MiningSpeedGameTests {
    @GameTest(template = "empty")
    public static void emptyHandedLevelZeroMatchesOriginalProgressWithoutChangingBlockProperties(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        PlayerExperience.setTotal(player, 0);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        BlockState[] states = {Blocks.DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.SAND.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState(), Blocks.RED_SAND.defaultBlockState()};
        float[] modernHardness = {0.5F, 0.6F, 0.5F, 0.6F, 0.2F, 0.5F};
        int[] progressBits = {0x3b800000, 0x3b800000, 0x3ba00000, 0x3b555555, 0x3c200000, 0x3ba00000};
        int[] accumulationTicks = {256, 256, 205, 308, 103, 205};
        for (int i = 0; i < states.length; i++) {
            helper.assertValueEqual(states[i].getDestroySpeed(helper.getLevel(), position), modernHardness[i],
                    "Registered hardness retains its modern value before mining");
            helper.assertValueEqual(states[i].getBlock().getExplosionResistance(), modernHardness[i],
                    "Blast resistance retains its modern value");
            float progress = states[i].getDestroyProgress(player, helper.getLevel(), position);
            helper.assertValueEqual(Float.floatToRawIntBits(progress), progressBits[i],
                    "Actual block progress matches the original float vector for " + states[i]);
            helper.assertValueEqual(accumulateTicks(progress), accumulationTicks[i],
                    "Repeated float accumulation matches the original timing vector for " + states[i]);
            helper.assertValueEqual(states[i].getDestroySpeed(helper.getLevel(), position), modernHardness[i],
                    "Effective mining hardness never mutates the registered block hardness");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void progressReadsStrengthOnceAndHonorsCancellationEvenForZeroHardness(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        PlayerExperience.setTotal(player, 6650);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        int[] calls = {0};
        Float[] replacement = {null};
        boolean[] canceled = {false};
        Consumer<PlayerEvent.BreakSpeed> listener = event -> {
            if (event.getEntity() == player) {
                calls[0]++;
                if (replacement[0] != null) event.setNewSpeed(replacement[0]);
                event.setCanceled(canceled[0]);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerEvent.BreakSpeed.class, listener);
        try {
            float dirt = Blocks.DIRT.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position);
            helper.assertValueEqual(Float.floatToRawIntBits(dirt), 0x3bd9999a,
                    "Level 35 dirt applies the level multiplier once before R196 progress");
            helper.assertValueEqual(calls[0], 1, "A progress query invokes getDigSpeed exactly once");
            helper.assertValueEqual(accumulateTicks(dirt), 151, "Level 35 dirt uses the recorded float accumulation count");

            calls[0] = 0;
            helper.assertValueEqual(Blocks.BEDROCK.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                    "Negative raw hardness remains unbreakable");
            helper.assertValueEqual(calls[0], 0, "Negative raw hardness returns before evaluating player strength");

            BlockState torch = Blocks.TORCH.defaultBlockState();
            helper.assertValueEqual(torch.getDestroySpeed(helper.getLevel(), position), 0.0F, "Torch is a zero-hardness fixture");
            helper.assertValueEqual(torch.getDestroyProgress(player, helper.getLevel(), position), 1.0F,
                    "Positive strength keeps zero-hardness instant breaking");
            for (float blocked : new float[]{0.0F, -1.0F}) {
                replacement[0] = blocked;
                helper.assertValueEqual(torch.getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                        "The modern wrapper blocks zero-hardness progress for nonpositive strength");
            }
            replacement[0] = 4.0F;
            canceled[0] = true;
            helper.assertValueEqual(torch.getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                    "Canceled speed cannot be converted into zero-hardness instant breaking");
            helper.assertValueEqual(Blocks.DIRT.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                    "Cancellation blocks positive-hardness progress too");
            canceled[0] = false;
            replacement[0] = 256.0F;
            helper.assertValueEqual(Blocks.DIRT.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 1.0F,
                    "Sufficient positive final strength crosses the R196 instant threshold");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void differentToolsKeepTheirModernSpeedsAndHarvestRequirements(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        Item[] tools = {Items.AIR, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_AXE};
        BlockState[] states = {Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                Blocks.DIRT.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState()};
        for (int i = 0; i < tools.length; i++) {
            player.setItemSlot(EquipmentSlot.MAINHAND, tools[i] == Items.AIR ? ItemStack.EMPTY : new ItemStack(tools[i]));
            PlayerExperience.setTotal(player, 0);
            float baseline = player.getDestroySpeed(states[i]);
            helper.assertTrue(baseline > 0.0F, "The modern item has a positive mining speed");
            boolean canHarvest = player.hasCorrectToolForDrops(states[i], helper.getLevel(), position);
            int[] levels = {-40, -1, 5, 35, 200};
            float[] multipliers = {0.2F, 0.98F, 1.1F, 1.7F, 5.0F};
            for (int j = 0; j < levels.length; j++) {
                PlayerExperience.setLevels(player, levels[j]);
                assertClose(helper, player.getDestroySpeed(states[i]), baseline * multipliers[j],
                        tools[i] + " at level " + levels[j]);
                helper.assertValueEqual(player.hasCorrectToolForDrops(states[i], helper.getLevel(), position), canHarvest,
                        "Level speed bonuses do not alter the modern tool requirement");
            }
        }
        PlayerExperience.setLevels(player, 200);
        helper.assertValueEqual(Blocks.BEDROCK.defaultBlockState().getDestroyProgress(player, helper.getLevel(), position), 0.0F,
                "The largest speed multiplier does not make bedrock breakable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void airAndHastePenaltiesRemainInsideTheFinalMultiplier(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockState stone = Blocks.STONE.defaultBlockState();
        player.setOnGround(true);
        PlayerExperience.setTotal(player, 0);
        float ground = player.getDestroySpeed(stone);
        player.setOnGround(false);
        float air = player.getDestroySpeed(stone);
        assertClose(helper, air, ground / 5.0F, "Modern airborne mining keeps its fivefold slowdown");
        PlayerExperience.setTotal(player, 6650);
        assertClose(helper, player.getDestroySpeed(stone), air * 1.7F, "The level multiplier applies after the airborne slowdown");
        player.setOnGround(true);
        assertClose(helper, player.getDestroySpeed(stone), ground * 1.7F, "Landing restores the modern ground speed");

        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 0));
        PlayerExperience.setTotal(player, 0);
        float haste = player.getDestroySpeed(stone);
        assertClose(helper, haste, ground * 1.2F, "Haste retains its modern contribution");
        PlayerExperience.setTotal(player, 6650);
        assertClose(helper, player.getDestroySpeed(stone), haste * 1.7F, "The multiplier composes with the finished haste result");
        player.removeEffect(MobEffects.DIG_SPEED);
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 0));
        assertClose(helper, player.getDestroySpeed(stone), ground * 0.3F * 1.7F,
                "Mining fatigue is not replaced by the level multiplier");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void earlierEventChangesAndCancellationArePreserved(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        player.setOnGround(true);
        PlayerExperience.setTotal(player, 6650);
        float[] replacement = {4.0F};
        boolean[] canceled = {false};
        Consumer<PlayerEvent.BreakSpeed> earlier = event -> {
            if (event.getEntity() == player) {
                event.setNewSpeed(replacement[0]);
                event.setCanceled(canceled[0]);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerEvent.BreakSpeed.class, earlier);
        try {
            BlockState stone = Blocks.STONE.defaultBlockState();
            assertClose(helper, player.getDestroySpeed(stone), 6.8F,
                    "LOWEST uses the earlier listener's newSpeed rather than original tool speed");
            replacement[0] = 0.0F;
            helper.assertValueEqual(player.getDestroySpeed(stone), 0.0F, "Zero speed stays zero");
            replacement[0] = -3.0F;
            helper.assertValueEqual(player.getDestroySpeed(stone), -3.0F, "Negative speed remains unchanged");
            replacement[0] = 4.0F;
            canceled[0] = true;
            helper.assertValueEqual(player.getDestroySpeed(stone), -1.0F,
                    "Canceled BreakSpeed retains NeoForge's blocked-speed result");
        } finally {
            NeoForge.EVENT_BUS.unregister(earlier);
        }
        PlayerExperience.setTotal(player, 0);
        float original = player.getDestroySpeed(Blocks.STONE.defaultBlockState());
        PlayerExperience.setTotal(player, 6650);
        assertClose(helper, player.getDestroySpeed(Blocks.STONE.defaultBlockState()), original * 1.7F,
                "Removing the temporary listener restores ordinary behavior");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gameModesAndSavedExperienceChooseTheRightMultiplier(GameTestHelper helper) {
        try (PlayerSession session = newServerPlayer(helper)) {
            ServerPlayer player = session.player();
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
            player.setOnGround(true);
            PlayerExperience.setTotal(player, 0);
            float baseline = player.getDestroySpeed(Blocks.STONE.defaultBlockState());
            for (GameType mode : GameType.values()) {
                player.setGameMode(mode);
                player.setOnGround(true);
                PlayerExperience.setTotal(player, 6650);
                float multiplier = mode == GameType.CREATIVE || mode == GameType.SPECTATOR ? 1.0F : 1.7F;
                assertClose(helper, player.getDestroySpeed(Blocks.STONE.defaultBlockState()), baseline * multiplier,
                        "Level 35 mining in " + mode);
                float progress = Blocks.STONE.defaultBlockState().getDestroyProgress(player, helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
                assertClose(helper, progress, mode == GameType.CREATIVE || mode == GameType.SPECTATOR ? 0.17777778F : 0.017708333F,
                        "Creative/spectator keep modern progress while survival/adventure use R196 progress");
                PlayerExperience.setTotal(player, -800);
                float negativeMultiplier = mode == GameType.CREATIVE || mode == GameType.SPECTATOR ? 1.0F : 0.2F;
                assertClose(helper, player.getDestroySpeed(Blocks.STONE.defaultBlockState()), baseline * negativeMultiplier,
                        "Negative mining in " + mode);
            }
            player.setGameMode(GameType.SURVIVAL);
            player.setOnGround(true);
            PlayerExperience.setTotal(player, 6650);
            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            Player restored = helper.makeMockPlayer(GameType.SURVIVAL);
            restored.load(saved);
            helper.assertValueEqual(restored.experienceLevel, 35, "Full entity load restores the canonical level");
            assertClose(helper, restored.getDestroySpeed(Blocks.STONE.defaultBlockState()), baseline * 1.7F,
                    "Reloaded player uses the restored level without a separate mining cache");
            assertClose(helper, Blocks.STONE.defaultBlockState().getDestroyProgress(restored, helper.getLevel(), helper.absolutePos(BlockPos.ZERO)),
                    0.017708333F, "Reloaded player uses R196 progress as well as the level multiplier");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realStartStopAndDelayedProgressUseTheScaledSpeed(GameTestHelper helper) {
        BlockPos slowPos = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos fastPos = helper.absolutePos(new BlockPos(2, 2, 0));
        BlockState previousSlow = helper.getLevel().getBlockState(slowPos);
        BlockState previousFast = helper.getLevel().getBlockState(fastPos);
        AABB dropsArea = new AABB(slowPos).expandTowards(2, 0, 0).inflate(1.0);
        Set<UUID> existingDrops = new HashSet<>();
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropsArea)) existingDrops.add(item.getUUID());
        GameRules.BooleanValue tileDrops = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS);
        boolean oldTileDrops = tileDrops.get();
        try (PlayerSession slowSession = newServerPlayer(helper); PlayerSession fastSession = newServerPlayer(helper)) {
            tileDrops.set(true, helper.getLevel().getServer());
            ServerPlayer slow = slowSession.player();
            ServerPlayer fast = fastSession.player();
            prepareMiner(slow, slowPos, 0);
            prepareMiner(fast, fastPos, 6650);
            helper.getLevel().setBlockAndUpdate(slowPos, Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(fastPos, Blocks.STONE.defaultBlockState());
            float slowProgress = Blocks.STONE.defaultBlockState().getDestroyProgress(slow, helper.getLevel(), slowPos);
            float fastProgress = Blocks.STONE.defaultBlockState().getDestroyProgress(fast, helper.getLevel(), fastPos);
            assertClose(helper, slowProgress, 0.010416667F, "Zero-level diamond mining of stone uses R196 1/96 progress");
            assertClose(helper, fastProgress, 0.017708333F, "Level 35 uses its strength multiplier exactly once");
            action(helper, slow, slowPos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 1);
            action(helper, fast, fastPos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, 2);
            for (int tick = 0; tick < 39; tick++) {
                slow.gameMode.tick();
                fast.gameMode.tick();
            }
            action(helper, slow, slowPos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, 3);
            action(helper, fast, fastPos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, 4);
            helper.assertTrue(helper.getLevel().getBlockState(slowPos).is(Blocks.STONE),
                    "Level zero has not reached the retained modern STOP threshold after 39 ticks");
            helper.assertTrue(helper.getLevel().getBlockState(fastPos).isAir(),
                    "Level 35 reaches the modern STOP threshold earlier");
            for (int tick = 0; tick < 55; tick++) slow.gameMode.tick();
            helper.assertTrue(helper.getLevel().getBlockState(slowPos).is(Blocks.STONE),
                    "The delayed level-zero path still respects its unfinished progress");
            slow.gameMode.tick();
            helper.assertTrue(helper.getLevel().getBlockState(slowPos).isAir(),
                    "The delayed path finishes when the R196 progress reaches one");
            int cobblestone = helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropsArea).stream()
                    .filter(item -> !existingDrops.contains(item.getUUID()) && item.getItem().is(Items.COBBLESTONE))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertValueEqual(cobblestone, 2, "Both correct-tool breaks retain their normal cobblestone drops");
            slow.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            PlayerExperience.setTotal(slow, 203000);
            helper.getLevel().setBlockAndUpdate(slowPos, Blocks.STONE.defaultBlockState());
            helper.assertFalse(Blocks.STONE.defaultBlockState().canHarvestBlock(helper.getLevel(), slowPos, slow),
                    "Maximum level does not make the empty hand a valid stone harvesting tool");
            helper.assertTrue(slow.gameMode.destroyBlock(slowPos), "Modern empty-handed removal remains possible");
            int afterWrongTool = helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropsArea).stream()
                    .filter(item -> !existingDrops.contains(item.getUUID()) && item.getItem().is(Items.COBBLESTONE))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertValueEqual(afterWrongTool, 2, "The wrong tool produces no extra cobblestone despite its level bonus");
        } finally {
            tileDrops.set(oldTileDrops, helper.getLevel().getServer());
            helper.getLevel().setBlockAndUpdate(slowPos, previousSlow);
            helper.getLevel().setBlockAndUpdate(fastPos, previousFast);
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropsArea).stream()
                    .filter(item -> !existingDrops.contains(item.getUUID())).forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    private static void prepareMiner(ServerPlayer player, BlockPos position, int experience) {
        player.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 2.0);
        player.setOnGround(true);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        PlayerExperience.setTotal(player, experience);
    }

    private static void action(GameTestHelper helper, ServerPlayer player, BlockPos position,
                               ServerboundPlayerActionPacket.Action action, int sequence) {
        player.gameMode.handleBlockBreakAction(position, action, Direction.UP, helper.getLevel().getMaxBuildHeight(), sequence);
    }

    private static PlayerSession newServerPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "mite-mining-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
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

    private static void assertClose(GameTestHelper helper, float actual, float expected, String context) {
        float tolerance = Math.max(0.000001F, Math.abs(expected) * 0.000001F);
        helper.assertTrue(Float.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                context + ": expected " + expected + ", actual " + actual);
    }

    private static int accumulateTicks(float progress) {
        float accumulated = 0.0F;
        int ticks = 0;
        while (accumulated < 1.0F && ticks < 10000) {
            accumulated += progress;
            ticks++;
        }
        return ticks;
    }
}
