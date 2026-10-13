package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.survival.DeathExperience;
import io.github.ningmeng123.mite.survival.DeathOrbQueue;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real server death, clone, save/load and packet paths. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DeathExperienceGameTests {
    private static final String PACKET_CAPTURE = "mite_death_experience_test_capture";

    @GameTest(template = "empty")
    public static void realDeathsApplyThresholdsAndConsumeSnapshotsOnce(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper);
        GameRules.BooleanValue keep = keepInventory(helper);
        boolean oldKeep = keep.get();
        GameRules.BooleanValue loot = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean oldLoot = loot.get();
        try {
            keep.set(false, helper.getLevel().getServer());
            loot.set(false, helper.getLevel().getServer());
            int[][] cases = {{819520, 0}, {491840, 0}, {20, 0}, {19, -1}, {0, -20}, {-1, -21}, {-20, -40},
                    {-779, -799}, {-780, -800}, {-799, -800}, {-800, -800}};
            for (int[] entry : cases) {
                PlayerExperience.setTotal(player, entry[0]);
                prepareDeathPosition(helper, player);
                player.kill();
                helper.assertTrue(player.isDeadOrDying(), "kill must use the actual death path");
                assertPending(helper, player, entry[1]);
                helper.assertValueEqual(player.totalExperience, entry[0], "Death snapshots do not change the old player balance");
                clearDeathOrbs(helper);
                ServerPlayer original = player;
                player = respawn(helper, original, false);
                assertTotal(helper, player, entry[1]);
                assertNoPending(helper, original, "Old player snapshot is consumed");
                assertNoPending(helper, player, "New player must not retain a consumed snapshot");
                ServerPlayer alive = player;
                player.connection.handleClientCommand(respawnRequest());
                helper.assertTrue(player.connection.player == alive, "A repeated respawn request cannot respawn a living player");
                assertTotal(helper, alive, entry[1]);
            }
        } finally {
            keep.set(oldKeep, helper.getLevel().getServer());
            loot.set(oldLoot, helper.getLevel().getServer());
            clearDeathOrbs(helper);
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void keepInventoryAndSpectatorDeathsPreserveSignedExperience(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper);
        GameRules.BooleanValue keep = keepInventory(helper);
        boolean oldKeep = keep.get();
        GameRules.BooleanValue loot = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean oldLoot = loot.get();
        try {
            keep.set(true, helper.getLevel().getServer());
            loot.set(true, helper.getLevel().getServer());
            for (int total : new int[]{819520, 491840, 493, -1, -800}) {
                PlayerExperience.setTotal(player, total);
                prepareDeathPosition(helper, player);
                helper.assertValueEqual(player.getExperienceReward(helper.getLevel(), null), 0,
                        "keepInventory suppresses death experience drops");
                player.kill();
                helper.assertValueEqual(deathOrbTotal(helper), 0, "keepInventory death produces no XP orbs");
                helper.assertValueEqual(captureDropQueue(helper, player).pendingCount(), 0,
                        "keepInventory death does not create delayed rewards");
                player = respawn(helper, player, false);
                assertTotal(helper, player, total);
                assertNoPending(helper, player, "Exempt death clears pending state");
            }
            keep.set(false, helper.getLevel().getServer());
            player.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(player.isSpectator(), "The replacement is a real spectator ServerPlayer");
            PlayerExperience.setTotal(player, -21);
            prepareDeathPosition(helper, player);
            helper.assertValueEqual(player.getExperienceReward(helper.getLevel(), null), 0,
                    "Spectators do not drop experience");
            player.kill();
            helper.assertValueEqual(captureDropQueue(helper, player).pendingCount(), 0,
                    "Spectator death does not create delayed rewards");
            player = respawn(helper, player, false);
            assertTotal(helper, player, -21);
            assertNoPending(helper, player, "Spectator death clears pending state");
        } finally {
            keep.set(oldKeep, helper.getLevel().getServer());
            loot.set(oldLoot, helper.getLevel().getServer());
            clearDeathOrbs(helper);
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void endReturnCopiesExperienceWithoutADeathPenalty(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper);
        try {
            for (int total : new int[]{819520, 491840, 493, -1, -800}) {
                PlayerExperience.setTotal(player, total);
                // A stale saved snapshot must neither charge an End return nor survive it.
                player.setData(MiteAttachments.RESPAWN_EXPERIENCE,
                        new DeathExperience(DeathExperience.CURRENT_VERSION, -800));
                ServerPlayer original = player;
                player = respawn(helper, original, true);
                assertTotal(helper, player, total);
                assertNoPending(helper, original, "End return clears the original stale snapshot");
                assertNoPending(helper, player, "End return clears any snapshot copied by NeoForge");
            }
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deathSnapshotSurvivesFullEntitySaveAndLoad(GameTestHelper helper) {
        ServerPlayer original = newPlayer(helper);
        ServerPlayer loaded = null;
        GameRules.BooleanValue keep = keepInventory(helper);
        boolean oldKeep = keep.get();
        GameRules.BooleanValue loot = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean oldLoot = loot.get();
        try {
            keep.set(false, helper.getLevel().getServer());
            loot.set(false, helper.getLevel().getServer());
            PlayerExperience.setTotal(original, 19);
            prepareDeathPosition(helper, original);
            original.kill();
            assertPending(helper, original, -1);
            clearDeathOrbs(helper);
            // A post-death command must not alter the already decided result.
            PlayerExperience.setTotal(original, 6650);
            CompoundTag saved = original.saveWithoutId(new CompoundTag());
            removePlayer(helper, original);
            loaded = newPlayer(helper);
            loaded.load(saved);
            helper.assertTrue(loaded.isDeadOrDying(), "Full Entity NBT preserves the death-screen state");
            helper.assertValueEqual(loaded.totalExperience, 6650, "The post-death balance also survives saving");
            assertPending(helper, loaded, -1);
            loaded = respawn(helper, loaded, false);
            assertTotal(helper, loaded, -1);
            assertNoPending(helper, loaded, "Reloaded snapshot is consumed once");
            // A later death takes a fresh snapshot instead of replaying the saved one.
            prepareDeathPosition(helper, loaded);
            loaded.kill();
            assertPending(helper, loaded, -21);
            loaded = respawn(helper, loaded, false);
            assertTotal(helper, loaded, -21);
        } finally {
            keep.set(oldKeep, helper.getLevel().getServer());
            loot.set(oldLoot, helper.getLevel().getServer());
            clearDeathOrbs(helper);
            removePlayer(helper, original);
            if (loaded != null) {
                removePlayer(helper, loaded);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledDeathDoesNotCreateAPendingPenalty(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper);
        GameRules.BooleanValue keep = keepInventory(helper);
        boolean oldKeep = keep.get();
        GameRules.BooleanValue loot = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean oldLoot = loot.get();
        PlayerExperience.setTotal(player, 19);
        ServerPlayer target = player;
        Consumer<LivingDeathEvent> cancel = event -> {
            if (event.getEntity() == target) {
                target.setHealth(1.0F);
                event.setCanceled(true);
            }
        };
        try {
            keep.set(false, helper.getLevel().getServer());
            loot.set(false, helper.getLevel().getServer());
            NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, cancel);
            try {
                prepareDeathPosition(helper, player);
                player.kill();
                helper.assertFalse(player.isDeadOrDying(), "Canceled death listener restores the player to life");
                assertTotal(helper, player, 19);
                assertNoPending(helper, player, "The canceled early return must not run the death-tail snapshot");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            // The canceled fatal hit still leaves the normal hurt cooldown and
            // lastHurt=Float.MAX_VALUE. genericKill does not bypass that cooldown.
            player.invulnerableTime = 0;
            player.kill();
            helper.assertTrue(player.isDeadOrDying(), "The second uncanceled kill must actually kill the player");
            assertPending(helper, player, -1);
            clearDeathOrbs(helper);
            player = respawn(helper, player, false);
            assertTotal(helper, player, -1);
        } finally {
            keep.set(oldKeep, helper.getLevel().getServer());
            loot.set(oldLoot, helper.getLevel().getServer());
            clearDeathOrbs(helper);
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deathDropsOneThirdOfTheOriginalTotalWithoutTheVanillaCap(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper);
        GameRules.BooleanValue keep = keepInventory(helper);
        boolean oldKeep = keep.get();
        GameRules.BooleanValue loot = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean oldLoot = loot.get();
        try {
            keep.set(false, helper.getLevel().getServer());
            loot.set(true, helper.getLevel().getServer());
            int[][] cases = {{819520, 273173}, {491840, 163946}, {203000, 67666}, {493, 164}, {20, 6}, {19, 6}, {2, 0}, {0, 0}, {-1, 0}, {-800, 0}};
            for (int[] entry : cases) {
                PlayerExperience.setTotal(player, entry[0]);
                prepareDeathPosition(helper, player);
                helper.assertValueEqual(player.getExperienceReward(helper.getLevel(), null), entry[1],
                        "Death reward uses integer total/3 before any respawn penalty");
                player.kill();
                helper.assertValueEqual(deathOrbTotal(helper), 0, "Death XP is not emitted immediately");
                DeathOrbQueue captured = captureDropQueue(helper, player);
                helper.assertValueEqual(captured.pendingCount(), entry[1] > 0 ? 1 : 0,
                        "Death creates exactly one positive reward record");
                for (int tick = 1; tick <= 20; tick++) {
                    captured = advanceQueueOneTick(helper, captured);
                    helper.assertValueEqual(deathOrbTotal(helper), tick < 20 ? 0 : entry[1],
                            "The captured death reward emits only after twenty loaded ticks");
                }
                helper.assertValueEqual(captured.pendingCount(), 0, "The emitted reward is consumed from its queue");
                clearDeathOrbs(helper);
                player = respawn(helper, player, false);
            }
            loot.set(false, helper.getLevel().getServer());
            PlayerExperience.setTotal(player, 493);
            prepareDeathPosition(helper, player);
            helper.assertValueEqual(player.getExperienceReward(helper.getLevel(), null), 0,
                    "Disabling mob loot suppresses the death experience reward");
            player.kill();
            helper.assertValueEqual(deathOrbTotal(helper), 0, "Disabling mob loot produces no actual XP orbs");
            helper.assertValueEqual(captureDropQueue(helper, player).pendingCount(), 0,
                    "Disabling mob loot also suppresses delayed XP");
            player = respawn(helper, player, false);
            assertTotal(helper, player, 0);
        } finally {
            keep.set(oldKeep, helper.getLevel().getServer());
            loot.set(oldLoot, helper.getLevel().getServer());
            clearDeathOrbs(helper);
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void experienceOrbsRecoverThroughNegativeAndZeroBoundaries(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper);
        try {
            PlayerExperience.setTotal(player, -20);
            collectOrb(helper, player, 19);
            assertTotal(helper, player, -1);
            collectOrb(helper, player, 1);
            assertTotal(helper, player, 0);
            collectOrb(helper, player, 20);
            assertTotal(helper, player, 20);
            PlayerExperience.setTotal(player, -800);
            collectOrb(helper, player, 1);
            assertTotal(helper, player, -799);
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void negativeOneSyncAndFirstRespawnPacketCarrySignedExperience(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper);
        GameRules.BooleanValue keep = keepInventory(helper);
        boolean oldKeep = keep.get();
        GameRules.BooleanValue loot = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean oldLoot = loot.get();
        List<ClientboundSetExperiencePacket> packets = new ArrayList<>();
        ChannelPipeline pipeline = player.connection.getConnection().channel().pipeline();
        pipeline.addLast(PACKET_CAPTURE, new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                if (message instanceof ClientboundSetExperiencePacket packet) {
                    packets.add(packet);
                }
                super.write(context, message, promise);
            }
        });
        try {
            keep.set(false, helper.getLevel().getServer());
            loot.set(false, helper.getLevel().getServer());
            PlayerExperience.setTotal(player, 491840);
            player.doTick();
            assertLatestPacket(helper, packets, 491840, 312, 440F / 3140F, "A legendary pick balance synchronizes its continued level and exact total");
            packets.clear();
            player.giveExperiencePoints(327680);
            player.doTick();
            assertLatestPacket(helper, packets, 819520, 403, 1430F / 4050F, "Growing past 200 uses the existing XP packet without truncation");
            PlayerExperience.setTotal(player, 0);
            player.doTick();
            packets.clear();
            player.giveExperiencePoints(-1);
            player.doTick();
            assertLatestPacket(helper, packets, -1, -1, 0.95F, "0 to -1 must not collide with a dirty sentinel");
            PlayerExperience.setTotal(player, -2);
            player.doTick();
            packets.clear();
            player.giveExperiencePoints(1);
            player.doTick();
            assertLatestPacket(helper, packets, -1, -1, 0.95F, "-2 to -1 must send the changed balance");
            PlayerExperience.setTotal(player, 19);
            prepareDeathPosition(helper, player);
            player.kill();
            clearDeathOrbs(helper);
            packets.clear();
            player = respawn(helper, player, false);
            helper.assertFalse(packets.isEmpty(), "Respawn sends an experience packet before the next tick");
            assertPacket(helper, packets.getFirst(), -1, -1, 0.95F, "The first respawn XP packet already contains the penalty");
        } finally {
            pipeline.remove(PACKET_CAPTURE);
            keep.set(oldKeep, helper.getLevel().getServer());
            loot.set(oldLoot, helper.getLevel().getServer());
            clearDeathOrbs(helper);
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void experiencePacketCodecRoundTripsNegativeValues(GameTestHelper helper) {
        int[][] cases = {{-800, -40, 0, 20}, {-799, -40, 1, 20}, {-21, -2, 19, 20}, {-20, -1, 0, 20}, {-1, -1, 19, 20}, {0, 0, 0, 20},
                {491840, 312, 440, 3140}, {819520, 403, 1430, 4050}, {2147483647, 20722, 166397, 207240}};
        for (int[] entry : cases) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                float progress = (float)entry[2] / entry[3];
                ClientboundSetExperiencePacket.STREAM_CODEC.encode(buffer,
                        new ClientboundSetExperiencePacket(progress, entry[0], entry[1]));
                ClientboundSetExperiencePacket decoded = ClientboundSetExperiencePacket.STREAM_CODEC.decode(buffer);
                assertPacket(helper, decoded, entry[0], entry[1], progress, "Signed XP packet codec round trip");
                helper.assertValueEqual(buffer.readableBytes(), 0, "The codec consumes the complete payload");
            } finally {
                buffer.release();
            }
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static ServerPlayer newPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ServerPlayer respawn(GameTestHelper helper, ServerPlayer original, boolean returningFromEnd) {
        original.wonGame = returningFromEnd;
        original.connection.handleClientCommand(respawnRequest());
        ServerPlayer replacement = original.connection.player;
        helper.assertTrue(replacement != original, "The respawn packet must replace the player through PlayerList");
        helper.assertFalse(replacement.isDeadOrDying(), "The replacement must be alive");
        return replacement;
    }

    private static ServerboundClientCommandPacket respawnRequest() {
        return new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        DeathOrbQueue.get(helper.getLevel()).removeForOwner(player.getUUID());
        ServerPlayer current = helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID());
        if (current != null) {
            helper.getLevel().getServer().getPlayerList().remove(current);
        }
    }

    private static GameRules.BooleanValue keepInventory(GameTestHelper helper) {
        return helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
    }

    private static DeathOrbQueue captureDropQueue(GameTestHelper helper, ServerPlayer owner) {
        DeathOrbQueue live = DeathOrbQueue.get(helper.getLevel());
        CompoundTag saved = live.save(new CompoundTag(), helper.getLevel().registryAccess());
        ListTag selected = new ListTag();
        for (Tag entry : saved.getList("drops", Tag.TAG_COMPOUND)) {
            CompoundTag drop = (CompoundTag) entry;
            if (drop.hasUUID("owner") && drop.getUUID("owner").equals(owner.getUUID())) {
                selected.add(drop.copy());
            }
        }
        saved.put("drops", selected);
        live.removeForOwner(owner.getUUID());
        return DeathOrbQueue.load(saved, helper.getLevel().registryAccess());
    }

    private static DeathOrbQueue advanceQueueOneTick(GameTestHelper helper, DeathOrbQueue queue) {
        CompoundTag saved = queue.save(new CompoundTag(), helper.getLevel().registryAccess());
        for (Tag entry : saved.getList("drops", Tag.TAG_COMPOUND)) {
            ((CompoundTag) entry).putLong("last_tick", helper.getLevel().getGameTime() - 1);
        }
        DeathOrbQueue restored = DeathOrbQueue.load(saved, helper.getLevel().registryAccess());
        restored.tick(helper.getLevel());
        return restored;
    }

    private static AABB deathArea(GameTestHelper helper) {
        Vec3 center = helper.absoluteVec(new Vec3(0.5, 48.0, 0.5));
        return new AABB(center.x - 1.0, center.y - 1.0, center.z - 1.0,
                center.x + 1.0, center.y + 1.0, center.z + 1.0);
    }

    private static void prepareDeathPosition(GameTestHelper helper, ServerPlayer player) {
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, deathArea(helper)).isEmpty(),
                "This test's isolated death area must not contain prior XP orbs");
        player.moveTo(helper.absoluteVec(new Vec3(0.5, 48.0, 0.5)));
    }

    private static int deathOrbTotal(GameTestHelper helper) {
        int total = 0;
        for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, deathArea(helper))) {
            CompoundTag saved = new CompoundTag();
            orb.addAdditionalSaveData(saved);
            total += orb.getValue() * saved.getInt("Count");
        }
        return total;
    }

    private static void clearDeathOrbs(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, deathArea(helper)).forEach(ExperienceOrb::discard);
    }

    private static void collectOrb(GameTestHelper helper, ServerPlayer player, int amount) {
        ExperienceOrb orb = new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), amount);
        player.takeXpDelay = 0;
        orb.playerTouch(player);
        helper.assertTrue(orb.isRemoved(), "A collected single orb must be consumed");
    }

    private static void assertPending(GameTestHelper helper, ServerPlayer player, int total) {
        DeathExperience pending = player.getExistingData(MiteAttachments.RESPAWN_EXPERIENCE).orElse(null);
        helper.assertTrue(pending != null, "Non-canceled death creates a serialized pending snapshot");
        helper.assertValueEqual(pending.total(), total, "Pending snapshot contains the death-time result");
    }

    private static void assertNoPending(GameTestHelper helper, ServerPlayer player, String context) {
        helper.assertTrue(player.getExistingData(MiteAttachments.RESPAWN_EXPERIENCE).isEmpty(), context);
    }

    private static void assertTotal(GameTestHelper helper, ServerPlayer player, int expectedTotal) {
        int level;
        int points;
        int width;
        if (expectedTotal < 0) {
            level = Math.floorDiv(expectedTotal, 20);
            points = Math.floorMod(expectedTotal, 20);
            width = 20;
        } else if (expectedTotal == 819520 || expectedTotal == 491840) {
            level = expectedTotal == 819520 ? 403 : 312;
            points = expectedTotal == 819520 ? 1430 : 440;
            width = expectedTotal == 819520 ? 4050 : 3140;
        } else if (expectedTotal == 493) {
            level = 8;
            points = 53;
            width = 100;
        } else if (expectedTotal == 20) {
            level = 1;
            points = 0;
            width = 30;
        } else {
            helper.assertTrue(expectedTotal >= 0 && expectedTotal < 20, "This assertion uses only independently specified vectors");
            level = 0;
            points = expectedTotal;
            width = 20;
        }
        helper.assertValueEqual(player.totalExperience, expectedTotal, "Canonical experience total");
        helper.assertValueEqual(player.experienceLevel, level, "Experience level");
        helper.assertValueEqual(PlayerExperience.pointsWithinLevel(player), points, "Exact points within the level");
        helper.assertValueEqual(player.getXpNeededForNextLevel(), width, "Experience bar width");
        helper.assertValueEqual(player.experienceProgress, (float) points / width, "Experience bar fraction");
    }

    private static void assertLatestPacket(GameTestHelper helper, List<ClientboundSetExperiencePacket> packets,
                                           int total, int level, float progress, String context) {
        helper.assertFalse(packets.isEmpty(), context + " (packet missing)");
        assertPacket(helper, packets.getLast(), total, level, progress, context);
    }

    private static void assertPacket(GameTestHelper helper, ClientboundSetExperiencePacket packet,
                                     int total, int level, float progress, String context) {
        helper.assertValueEqual(packet.getTotalExperience(), total, context + " (total)");
        helper.assertValueEqual(packet.getExperienceLevel(), level, context + " (level)");
        helper.assertValueEqual(packet.getExperienceProgress(), progress, context + " (progress)");
    }
}
