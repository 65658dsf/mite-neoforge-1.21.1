package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.block.StrongboxBlock;
import io.github.ningmeng123.mite.block.entity.StrongboxBlockEntity;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.DeathOrbQueue;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native player removal and outbound chat packets verify strongbox break notices. @author NingMeng123 */
@GameTestHolder("mite_strongbox_announcement")
@PrefixGameTestTemplate(false)
public final class StrongboxBreakAnnouncementGameTests {
    private static final String MESSAGE = "mite.strongbox.destroyed_by_non_owner";
    private static final BlockPos SITE = new BlockPos(1, 1, 1);
    private StrongboxBreakAnnouncementGameTests() {}

    @GameTest(template = "empty")
    public static void everyMetalAnnouncesSuccessfulNonownerSurvivalBreakAfterOwnershipReload(GameTestHelper helper) {
        try (Observer observer = new Observer(helper)) {
            var owner = player(helper, "BoxOwner");
            var breaker = player(helper, "BoxBreaker");
            for (var entry : MiteFunctionalBlocks.STRONGBOXES.entrySet()) {
                StrongboxBlock block = entry.getValue().get();
                BlockPos pos = place(helper, block, owner);
                var entity = (StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos);
                var restored = (StrongboxBlockEntity)BlockEntity.loadStatic(pos, entity.getBlockState(),
                        entity.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
                helper.getLevel().setBlockEntity(restored);
                breaker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mite:vibranium_pickaxe")));
                observer.drain();
                helper.assertTrue(breaker.gameMode.destroyBlock(pos), "Qualified nonowner removes " + entry.getKey());
                helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "Notice follows real block removal");
                assertNotice(helper, observer.drain(), breaker, owner, block);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeNonownerBreakAlsoBroadcastsInChat(GameTestHelper helper) {
        try (Observer observer = new Observer(helper)) {
            var owner = player(helper, "CreativeBoxOwner");
            var breaker = player(helper, "CreativeBreaker");
            breaker.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            StrongboxBlock block = MiteFunctionalBlocks.STRONGBOXES.get("adamantium").get();
            BlockPos pos = place(helper, block, owner);
            observer.drain();
            helper.assertTrue(breaker.gameMode.destroyBlock(pos), "Creative nonowner removes actual owned box");
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "Creative notice follows removal");
            assertNotice(helper, observer.drain(), breaker, owner, block);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ownerOwnerlessAndOrdinaryChestBreaksRemainSilent(GameTestHelper helper) {
        try (Observer observer = new Observer(helper)) {
            var owner = player(helper, "QuietBoxOwner");
            StrongboxBlock block = MiteFunctionalBlocks.STRONGBOXES.get("copper").get();
            BlockPos pos = place(helper, block, owner);
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            observer.drain();
            helper.assertTrue(owner.gameMode.destroyBlock(pos), "Owner removes own box with bare hands");
            helper.assertTrue(observer.drain().isEmpty(), "Owner removal sends no nonowner warning");
            helper.setBlock(SITE, block);
            var unowned = (StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos);
            helper.assertFalse(unowned.hasOwner(), "Natural box has no player ownership");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mite:vibranium_pickaxe")));
            helper.assertTrue(owner.gameMode.destroyBlock(pos), "Qualified player removes unowned box");
            helper.assertTrue(observer.drain().isEmpty(), "Unowned box sends no player ownership warning");
            helper.setBlock(SITE, Blocks.CHEST);
            helper.assertTrue(owner.gameMode.destroyBlock(pos), "Ordinary chest removal succeeds");
            helper.assertTrue(observer.drain().isEmpty(), "Ordinary chest sends no metal box warning");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedAndCanceledNonownerBreaksNeverBroadcast(GameTestHelper helper) {
        try (Observer observer = new Observer(helper)) {
            var owner = player(helper, "ProtectedBoxOwner");
            var breaker = player(helper, "BlockedBoxBreaker");
            StrongboxBlock block = MiteFunctionalBlocks.STRONGBOXES.get("adamantium").get();
            BlockPos pos = place(helper, block, owner);
            breaker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            observer.drain();
            helper.assertFalse(breaker.gameMode.destroyBlock(pos), "Bare hands cannot break another player's box");
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(block), "Blocked break retains box");
            helper.assertTrue(observer.drain().isEmpty(), "Blocked break sends no warning");
            breaker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mite:vibranium_pickaxe")));
            boolean[] reached = {false};
            Consumer<BlockEvent.BreakEvent> cancel = event -> {
                if (event.getPlayer() == breaker && event.getPos().equals(pos)) {
                    reached[0] = true;
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BlockEvent.BreakEvent.class, cancel);
            try {
                helper.assertFalse(breaker.gameMode.destroyBlock(pos), "External protection cancels qualified break");
                helper.assertTrue(reached[0], "Real break reaches the external protection listener");
                helper.assertTrue(helper.getLevel().getBlockState(pos).is(block), "Canceled break retains box");
                helper.assertTrue(observer.drain().isEmpty(), "Canceled break sends no warning");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(SITE)));
        return player;
    }

    private static BlockPos place(GameTestHelper helper, StrongboxBlock block, ServerPlayer owner) {
        helper.setBlock(SITE.below(), Blocks.STONE);
        helper.setBlock(SITE, block);
        BlockPos pos = helper.absolutePos(SITE);
        block.setPlacedBy(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), owner, new ItemStack(block));
        return pos;
    }

    private static void assertNotice(GameTestHelper helper, List<ClientboundSystemChatPacket> packets,
            ServerPlayer breaker, ServerPlayer owner, StrongboxBlock block) {
        helper.assertValueEqual(packets.size(), 1, "One server chat warning per successful break");
        var packet = packets.getFirst();
        helper.assertFalse(packet.overlay(), "Notice appears in chat, not the action bar");
        var args = ((TranslatableContents)packet.content().getContents()).getArgs();
        helper.assertValueEqual(((Component)args[0]).getString(), breaker.getDisplayName().getString(), "Notice names breaker");
        helper.assertValueEqual(args[1], owner.getGameProfile().getName(), "Notice identifies saved owner");
        helper.assertValueEqual(((Component)args[2]).getString(), block.getName().getString(), "Notice identifies broken metal box");
    }

    private static final class Observer implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        Observer(GameTestHelper helper) {
            var profile = new GameProfile(UUID.randomUUID(), "box-" + UUID.randomUUID().toString().substring(0, 8));
            var cookie = CommonListenerCookie.createInitial(profile, false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setNoGravity(true);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 24, 1))));
        }
        List<ClientboundSystemChatPacket> drain() {
            channel.runPendingTasks();
            channel.flushOutbound();
            List<ClientboundSystemChatPacket> result = new ArrayList<>();
            Object packet;
            while ((packet = channel.readOutbound()) != null) {
                if (packet instanceof ClientboundSystemChatPacket chat
                        && chat.content().getContents() instanceof TranslatableContents text && text.getKey().equals(MESSAGE))
                    result.add(chat);
            }
            return result;
        }
        @Override public void close() {
            player.getInventory().clearContent();
            for (ServerLevel level : player.server.getAllLevels()) DeathOrbQueue.get(level).removeForOwner(player.getUUID());
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
