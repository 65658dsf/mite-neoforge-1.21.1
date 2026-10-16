package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.survival.DeathExperienceOrbs;
import io.github.ningmeng123.mite.survival.DeathOrbQueue;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Delayed owner-only death XP, using the real queue, orb and pickup paths. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DeathExperienceOrbGameTests {
    @GameTest(template = "empty")
    public static void queueSkipsEnqueueTickAndEmitsExactlyOnLoadedTickTwenty(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        DeathOrbQueue queue = new DeathOrbQueue();
        try {
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(BlockPos.containing(position(helper))),
                    "The test position must be entity ticking");
            queue.enqueue(owner, position(helper), 37, helper.getLevel().getGameTime());
            queue.tick(helper.getLevel());
            helper.assertValueEqual(remaining(helper, queue), 20, "Enqueue tick must not count toward the delay");
            for (int elapsed = 1; elapsed <= 19; elapsed++) {
                queue = advanceOneTick(helper, queue);
                helper.assertValueEqual(remaining(helper, queue), 20 - elapsed, "One loaded tick decrements the remaining delay once");
                queue.tick(helper.getLevel());
                helper.assertValueEqual(remaining(helper, queue), 20 - elapsed, "Repeated processing in one game tick cannot accelerate emission");
                helper.assertValueEqual(orbTotal(helper), 0, "No experience exists before the twentieth loaded tick");
            }
            queue = advanceOneTick(helper, queue);
            helper.assertValueEqual(queue.pendingCount(), 0, "The record is removed after emission");
            helper.assertValueEqual(orbTotal(helper), 37, "The twentieth loaded tick emits the full saved amount");
            for (ExperienceOrb orb : orbs(helper)) {
                assertOwner(helper, orb, owner);
                helper.assertValueEqual(age(orb), 0, "Newly emitted death orbs start their own lifetime at zero");
            }
            queue.tick(helper.getLevel());
            helper.assertValueEqual(orbTotal(helper), 37, "A consumed record cannot emit a second time");
        } finally {
            clearOrbs(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void queueSerializationRetainsIdentityAndPausesOutsideTickingChunks(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        Vec3 unloaded = new Vec3(29_000_000.5, 64, 29_000_000.5);
        helper.assertFalse(helper.getLevel().isPositionEntityTicking(BlockPos.containing(unloaded)),
                "The distant queue position must not be entity ticking");
        DeathOrbQueue queue = new DeathOrbQueue();
        queue.enqueue(owner, unloaded, 164, helper.getLevel().getGameTime());
        queue = advanceOneTick(helper, queue);
        helper.assertValueEqual(remaining(helper, queue), 20, "Unloaded chunks do not spend delay ticks");
        CompoundTag saved = queue.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(saved.getInt("version"), 1, "Queue schema version");
        CompoundTag drop = saved.getList("drops", Tag.TAG_COMPOUND).getCompound(0);
        helper.assertValueEqual(drop.getUUID("owner"), owner, "Owner UUID survives serialization");
        helper.assertValueEqual(drop.getDouble("x"), unloaded.x, "Death X survives serialization");
        helper.assertValueEqual(drop.getDouble("y"), unloaded.y, "Death Y survives serialization");
        helper.assertValueEqual(drop.getDouble("z"), unloaded.z, "Death Z survives serialization");
        helper.assertValueEqual(drop.getInt("amount"), 164, "Reward survives serialization");
        queue = DeathOrbQueue.load(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(queue.pendingCount(), 1, "Reload retains one pending record");
        helper.assertValueEqual(remaining(helper, queue), 20, "Reload does not consume delay time");
        queue.removeForOwner(UUID.randomUUID());
        helper.assertValueEqual(queue.pendingCount(), 1, "Removing another owner cannot remove this reward");
        queue.removeForOwner(owner);
        helper.assertValueEqual(queue.pendingCount(), 0, "Owner cleanup removes pending rewards without spawning them");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void strangerCannotReceiveExperienceRepairItemsOrStartPickupCooldown(GameTestHelper helper) {
        ServerPlayer owner = newPlayer(helper);
        ServerPlayer stranger = newPlayer(helper);
        ExperienceOrb orb = orb(helper, 5);
        try {
            helper.assertValueEqual(owner.getGameProfile().getName(), stranger.getGameProfile().getName(),
                    "The mock players intentionally have the same name");
            helper.assertFalse(owner.getUUID().equals(stranger.getUUID()), "The same-named players have different UUIDs");
            ItemStack ownerTool = mendingTool(helper);
            ItemStack strangerTool = mendingTool(helper);
            owner.setItemSlot(EquipmentSlot.MAINHAND, ownerTool);
            stranger.setItemSlot(EquipmentSlot.MAINHAND, strangerTool);
            PlayerExperience.setTotal(owner, 0);
            PlayerExperience.setTotal(stranger, 0);
            DeathExperienceOrbs.mark(orb, owner.getUUID());
            stranger.takeXpDelay = 0;
            orb.playerTouch(stranger);
            helper.assertValueEqual(stranger.totalExperience, 0, "A stranger receives no XP");
            helper.assertValueEqual(stranger.experienceLevel, 0, "A stranger's level does not change");
            helper.assertValueEqual(stranger.experienceProgress, 0.0F, "A stranger's bar does not change");
            helper.assertValueEqual(strangerTool.getDamageValue(), 4, "A stranger receives no mending repair");
            helper.assertValueEqual(stranger.takeXpDelay, 0, "Rejected pickup does not start the pickup cooldown");
            helper.assertFalse(orb.isRemoved(), "Rejected pickup leaves the orb available to its owner");
            helper.assertValueEqual(count(orb), 1, "Rejected pickup does not spend a merged count");

            owner.takeXpDelay = 0;
            orb.playerTouch(owner);
            helper.assertValueEqual(ownerTool.getDamageValue(), 0, "Owner pickup preserves modern mending");
            helper.assertValueEqual(owner.totalExperience, 3, "Five XP repairs four durability and awards the remaining three XP");
            helper.assertValueEqual(owner.takeXpDelay, 2, "Successful owner pickup uses the normal cooldown");
            helper.assertTrue(orb.isRemoved(), "Owner pickup consumes the single orb");

            ExperienceOrb ordinary = orb(helper, 5);
            stranger.takeXpDelay = 0;
            ordinary.playerTouch(stranger);
            helper.assertValueEqual(strangerTool.getDamageValue(), 0, "Ordinary orbs remain available for mending");
            helper.assertValueEqual(stranger.totalExperience, 3, "Ordinary orbs preserve their XP remainder");
            helper.assertTrue(ordinary.isRemoved(), "Ordinary pickup remains functional");
        } finally {
            orb.discard();
            removePlayer(helper, owner);
            removePlayer(helper, stranger);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ownerCanCollectAfterDeathRespawnAndSameUuidRelogin(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper);
        EmbeddedChannel relogChannel = null;
        GameRules.BooleanValue keep = rule(helper, GameRules.RULE_KEEPINVENTORY);
        GameRules.BooleanValue loot = rule(helper, GameRules.RULE_DOMOBLOOT);
        boolean oldKeep = keep.get();
        boolean oldLoot = loot.get();
        try {
            keep.set(false, helper.getLevel().getServer());
            loot.set(false, helper.getLevel().getServer());
            UUID owner = player.getUUID();
            PlayerExperience.setTotal(player, 0);
            ExperienceOrb beforeDeath = orb(helper, 5);
            DeathExperienceOrbs.mark(beforeDeath, owner);
            player.kill();
            player = respawn(player);
            helper.assertValueEqual(player.getUUID(), owner, "Death respawn retains the owner UUID");
            player.moveTo(position(helper));
            player.takeXpDelay = 0;
            beforeDeath.playerTouch(player);
            helper.assertTrue(beforeDeath.isRemoved(), "The replacement player can collect the old owner's orb");
            helper.assertValueEqual(player.totalExperience, -15, "Owned XP can repay the respawned player's debt");

            ExperienceOrb persistedOrb = orb(helper, 5);
            DeathExperienceOrbs.mark(persistedOrb, owner);
            CompoundTag savedOrb = persistedOrb.saveWithoutId(new CompoundTag());
            ExperienceOrb reloadedOrb = orb(helper, 0);
            reloadedOrb.load(savedOrb);
            assertOwner(helper, reloadedOrb, owner);
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
            removePlayer(helper, player);
            ServerPlayer returning = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            relogChannel = new EmbeddedChannel(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, returning, cookie);
            player = returning;
            helper.assertValueEqual(player.getUUID(), owner, "Relogin creates a new object with the original UUID");
            player.moveTo(position(helper));
            player.takeXpDelay = 0;
            reloadedOrb.playerTouch(player);
            helper.assertTrue(reloadedOrb.isRemoved(), "A reconnected owner can collect an NBT-restored orb");
            helper.assertValueEqual(player.totalExperience, -10, "Relogin does not clear the debt before pickup");
        } finally {
            keep.set(oldKeep, helper.getLevel().getServer());
            loot.set(oldLoot, helper.getLevel().getServer());
            removePlayer(helper, player);
            if (relogChannel != null) {
                relogChannel.finishAndReleaseAll();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ownedAndOrdinaryOrbLifetimeBoundariesSurviveReload(GameTestHelper helper) {
        ExperienceOrb owned = orb(helper, 1);
        UUID owner = UUID.randomUUID();
        DeathExperienceOrbs.mark(owned, owner);
        owned = reloadAtAge(helper, owned, 23998);
        owned.tick();
        helper.assertFalse(owned.isRemoved(), "An owned orb survives loaded tick 23999");
        helper.assertValueEqual(age(owned), 23999, "Owned age increments once per actual orb tick");
        owned = reloadAtAge(helper, owned, 23999);
        assertOwner(helper, owned, owner);
        owned.tick();
        helper.assertTrue(owned.isRemoved(), "An owned orb expires at loaded tick 24000");

        ExperienceOrb ordinary = reloadAtAge(helper, orb(helper, 1), 5998);
        ordinary.tick();
        helper.assertFalse(ordinary.isRemoved(), "An ordinary orb survives tick 5999");
        helper.assertFalse(ordinary.hasData(MiteAttachments.DEATH_ORB_OWNER), "Ordinary tick does not manufacture ownership");
        ordinary = reloadAtAge(helper, ordinary, 5999);
        ordinary.tick();
        helper.assertTrue(ordinary.isRemoved(), "An ordinary orb retains vanilla's 6000-tick lifetime");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entityScanNeverMergesOwnedOrbsButStillMergesOrdinaryOrbs(GameTestHelper helper) {
        UUID firstOwner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        UUID[][] cases = {{firstOwner, firstOwner}, {firstOwner, otherOwner}, {firstOwner, null}, {null, firstOwner}, {null, null}};
        try {
            for (UUID[] owners : cases) {
                ExperienceOrb first = orb(helper, 3);
                ExperienceOrb second = first;
                // Allocate, rather than overwrite, IDs so the vanilla modulo-40 gate is guaranteed to match.
                for (int i = 0; i < 40; i++) {
                    second = orb(helper, 3);
                }
                helper.assertValueEqual((second.getId() - first.getId()) % 40, 0, "The vanilla merge gate is eligible");
                if (owners[0] != null) DeathExperienceOrbs.mark(first, owners[0]);
                if (owners[1] != null) DeathExperienceOrbs.mark(second, owners[1]);
                helper.getLevel().addFreshEntity(first);
                helper.getLevel().addFreshEntity(second);
                first.tickCount = 1;
                first.tick();
                if (owners[0] == null && owners[1] == null) {
                    helper.assertTrue(second.isRemoved(), "Ordinary compatible orbs still merge");
                    helper.assertValueEqual(count(first), 2, "Ordinary merging preserves both counts");
                } else {
                    helper.assertFalse(first.isRemoved(), "An owned merge candidate survives separately");
                    helper.assertFalse(second.isRemoved(), "Owned, mixed and different-owner pairs cannot merge");
                    helper.assertValueEqual(count(first), 1, "No owned count is absorbed");
                    helper.assertValueEqual(count(second), 1, "No owned count is consumed");
                }
                helper.assertValueEqual(orbTotal(helper), 6, "Every merge case conserves total XP");
                clearOrbs(helper);
            }
        } finally {
            clearOrbs(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staticAwardCannotMergeIntoOwnedOrbs(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        try {
            for (int i = 0; i < 40; i++) {
                ExperienceOrb orb = orb(helper, 1);
                DeathExperienceOrbs.mark(orb, owner);
                helper.getLevel().addFreshEntity(orb);
            }
            ExperienceOrb.award(helper.getLevel(), position(helper), 1);
            helper.assertValueEqual(orbs(helper).size(), 41, "Every random merge residue must reject an owned orb");
            helper.assertValueEqual(orbTotal(helper), 41, "Static award preserves the new unowned XP");
            int unowned = 0;
            for (ExperienceOrb orb : orbs(helper)) {
                helper.assertValueEqual(count(orb), 1, "Static award cannot add to an owned count");
                if (!orb.hasData(MiteAttachments.DEATH_ORB_OWNER)) unowned++;
            }
            helper.assertValueEqual(unowned, 1, "The ordinary reward remains publicly collectible");
            clearOrbs(helper);
            for (int i = 0; i < 40; i++) helper.getLevel().addFreshEntity(orb(helper, 1));
            ExperienceOrb.award(helper.getLevel(), position(helper), 1);
            helper.assertValueEqual(orbs(helper).size(), 40, "Static award still merges compatible ordinary XP");
            helper.assertValueEqual(orbTotal(helper), 41, "Ordinary static merging conserves XP");
        } finally {
            clearOrbs(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void actualLevelTicksEmitAfterImmediateDeathRespawn(GameTestHelper helper) {
        ServerPlayer original = newPlayer(helper);
        UUID owner = original.getUUID();
        GameRules.BooleanValue keep = rule(helper, GameRules.RULE_KEEPINVENTORY);
        GameRules.BooleanValue loot = rule(helper, GameRules.RULE_DOMOBLOOT);
        boolean oldKeep = keep.get();
        boolean oldLoot = loot.get();
        boolean callbacksScheduled = false;
        Runnable cleanup = () -> {
            DeathOrbQueue.get(helper.getLevel()).removeForOwner(owner);
            clearOrbs(helper);
            removePlayer(helper, original);
        };
        try {
            keep.set(false, helper.getLevel().getServer());
            loot.set(true, helper.getLevel().getServer());
            original.moveTo(position(helper));
            PlayerExperience.setTotal(original, 111);
            long deathTick = helper.getLevel().getGameTime();
            original.kill();
            helper.assertValueEqual(orbTotal(helper), 0, "Real death does not emit XP immediately");
            ServerPlayer replacement = respawn(original);
            helper.assertValueEqual(replacement.getUUID(), owner, "Immediate respawn retains the queue owner");
            helper.assertValueEqual(replacement.totalExperience, 0, "Respawn penalty is independent of delayed drops");
            helper.runAfterDelay(19, () -> {
                try {
                    helper.assertValueEqual(helper.getLevel().getGameTime() - deathTick, 19L, "Nineteen real level ticks elapsed");
                    helper.assertValueEqual(orbTotal(helper), 0, "The live queue has not emitted at tick 19");
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
            });
            helper.runAfterDelay(20, () -> {
                try {
                    helper.assertValueEqual(helper.getLevel().getGameTime() - deathTick, 20L, "Twenty real level ticks elapsed");
                    helper.assertValueEqual(orbTotal(helper), 37, "The LevelTick queue emits once after the old player was removed");
                    for (ExperienceOrb orb : orbs(helper)) assertOwner(helper, orb, owner);
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
            callbacksScheduled = true;
        } finally {
            // Eligibility was captured at death; do not hold global gamerules during asynchronous checks.
            keep.set(oldKeep, helper.getLevel().getServer());
            loot.set(oldLoot, helper.getLevel().getServer());
            if (!callbacksScheduled) cleanup.run();
        }
    }

    private static DeathOrbQueue advanceOneTick(GameTestHelper helper, DeathOrbQueue queue) {
        CompoundTag saved = queue.save(new CompoundTag(), helper.getLevel().registryAccess());
        for (Tag entry : saved.getList("drops", Tag.TAG_COMPOUND)) {
            ((CompoundTag) entry).putLong("last_tick", helper.getLevel().getGameTime() - 1);
        }
        DeathOrbQueue restored = DeathOrbQueue.load(saved, helper.getLevel().registryAccess());
        restored.tick(helper.getLevel());
        return restored;
    }

    private static int remaining(GameTestHelper helper, DeathOrbQueue queue) {
        CompoundTag saved = queue.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertValueEqual(saved.getList("drops", Tag.TAG_COMPOUND).size(), 1, "One independent queue record exists");
        return saved.getList("drops", Tag.TAG_COMPOUND).getCompound(0).getInt("remaining_ticks");
    }

    private static ExperienceOrb reloadAtAge(GameTestHelper helper, ExperienceOrb original, int age) {
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        saved.putShort("Age", (short) age);
        ExperienceOrb restored = orb(helper, 0);
        restored.load(saved);
        restored.setNoGravity(true);
        restored.setDeltaMovement(Vec3.ZERO);
        return restored;
    }

    private static ItemStack mendingTool(GameTestHelper helper) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        tool.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.MENDING), 1);
        tool.setDamageValue(4);
        return tool;
    }

    private static void assertOwner(GameTestHelper helper, ExperienceOrb orb, UUID expected) {
        helper.assertTrue(orb.getExistingData(MiteAttachments.DEATH_ORB_OWNER).isPresent(), "The death orb has a persisted owner");
        helper.assertValueEqual(orb.getExistingData(MiteAttachments.DEATH_ORB_OWNER).orElseThrow().owner(), expected,
                "Death orb owner UUID");
    }

    private static int age(ExperienceOrb orb) {
        CompoundTag tag = new CompoundTag();
        orb.addAdditionalSaveData(tag);
        return tag.getShort("Age");
    }

    private static int count(ExperienceOrb orb) {
        CompoundTag tag = new CompoundTag();
        orb.addAdditionalSaveData(tag);
        return tag.getInt("Count");
    }

    private static ExperienceOrb orb(GameTestHelper helper, int value) {
        Vec3 position = position(helper);
        ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), position.x, position.y, position.z, value);
        orb.setNoGravity(true);
        orb.setDeltaMovement(Vec3.ZERO);
        return orb;
    }

    private static Vec3 position(GameTestHelper helper) {
        return helper.absoluteVec(new Vec3(0.5, 48.0, 0.5));
    }

    private static List<ExperienceOrb> orbs(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, AABB.ofSize(position(helper), 2, 2, 2));
    }

    private static int orbTotal(GameTestHelper helper) {
        return orbs(helper).stream().mapToInt(orb -> orb.getValue() * count(orb)).sum();
    }

    private static void clearOrbs(GameTestHelper helper) {
        orbs(helper).forEach(ExperienceOrb::discard);
    }

    @SuppressWarnings("removal")
    private static ServerPlayer newPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(position(helper));
        return player;
    }

    private static ServerPlayer respawn(ServerPlayer player) {
        player.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        return player.connection.player;
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        DeathOrbQueue.get(helper.getLevel()).removeForOwner(player.getUUID());
        ServerPlayer current = helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID());
        if (current != null) helper.getLevel().getServer().getPlayerList().remove(current);
    }

    private static GameRules.BooleanValue rule(GameTestHelper helper, GameRules.Key<GameRules.BooleanValue> key) {
        return helper.getLevel().getGameRules().getRule(key);
    }
}
