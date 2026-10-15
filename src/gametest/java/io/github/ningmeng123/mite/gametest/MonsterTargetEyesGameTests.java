package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.PlayerTargetEyes;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Player-target eye state is verified on real entities and actual metadata packets. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MonsterTargetEyesGameTests {
    private static final List<MobKind> RED_EYED_KINDS = List.of(
            MobKind.LONGDEAD_GUARDIAN, MobKind.BONE_LORD, MobKind.ANCIENT_BONE_LORD, MobKind.REVENANT);

    private static Mob mob(GameTestHelper h, MobKind kind) {
        Mob entity = MiteMobs.type(kind).create(h.getLevel());
        BlockPos position = h.absolutePos(new BlockPos(1, 10, 1));
        entity.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5, 0, 0);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        h.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static Player player(GameTestHelper h, Mob owner, GameType gameType) {
        Player player = h.makeMockPlayer(gameType);
        player.moveTo(owner.getX() + 4, owner.getY(), owner.getZ(), 0, 0);
        return player;
    }

    private static boolean red(Mob entity) { return ((PlayerTargetEyes) entity).hasRedEyes(); }

    @GameTest(template = "empty")
    public static void allFourSourceMonstersChangeEyesWithTheirActualPlayerTarget(GameTestHelper h) {
        for (MobKind kind : RED_EYED_KINDS) {
            Mob owner = mob(h, kind);
            Player player = player(h, owner, GameType.SURVIVAL);
            h.assertFalse(red(owner), kind + " starts with black eyes and no target");
            owner.setTarget(player);
            h.assertTrue(owner.getTarget() == player && red(owner), kind + " turns red immediately after accepting a survival player");
            owner.setTarget(null);
            h.assertFalse(red(owner), kind + " returns to black immediately after clearing its player target");
            owner.discard();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void villagersAndAnimalsNeverActivatePlayerTargetEyes(GameTestHelper h) {
        var villager = EntityType.VILLAGER.create(h.getLevel());
        var cow = EntityType.COW.create(h.getLevel());
        for (MobKind kind : RED_EYED_KINDS) {
            Mob owner = mob(h, kind);
            Player player = player(h, owner, GameType.SURVIVAL);
            owner.setTarget(player);
            h.assertTrue(red(owner), kind + " fixture first acquires a red-eye player target");
            owner.setTarget(villager);
            h.assertTrue(owner.getTarget() == villager && !red(owner), kind + " village target clears player-only red eyes");
            owner.setTarget(cow);
            h.assertTrue(owner.getTarget() == cow && !red(owner), kind + " animal target also keeps black eyes");
            owner.discard();
            player.discard();
        }
        villager.discard();
        cow.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryLongdeadDoesNotGainGuardianRedEyes(GameTestHelper h) {
        Mob owner = mob(h, MobKind.LONGDEAD);
        Player player = player(h, owner, GameType.SURVIVAL);
        h.assertFalse(red(owner), "Ordinary longdead has no idle red-eye state");
        owner.setTarget(player);
        h.assertTrue(owner.getTarget() == player && !red(owner), "Ordinary longdead remains excluded even while targeting a player");
        owner.aiStep();
        h.assertFalse(red(owner), "A server update cannot give an ordinary longdead guardian eyes");
        owner.discard();
        player.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledTargetChangesPreserveTheAcceptedEyeState(GameTestHelper h) {
        for (MobKind kind : RED_EYED_KINDS) {
            Mob owner = mob(h, kind);
            Player player = player(h, owner, GameType.SURVIVAL);
            Consumer<LivingChangeTargetEvent> cancel = event -> {
                if (event.getEntity() == owner) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                owner.setTarget(player);
                h.assertTrue(owner.getTarget() == null && !red(owner), kind + " canceled player acquisition does not turn red");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            owner.setTarget(player);
            h.assertTrue(red(owner), kind + " accepts the player after cancellation is removed");
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                owner.setTarget(null);
                h.assertTrue(owner.getTarget() == player && red(owner), kind + " canceled target clearing preserves the real player target and red eyes");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            owner.discard();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void replacedNeoForgeTargetsUseTheFinalTargetForTheirEyeColor(GameTestHelper h) {
        var villager = EntityType.VILLAGER.create(h.getLevel());
        for (MobKind kind : RED_EYED_KINDS) {
            Mob owner = mob(h, kind);
            Player player = player(h, owner, GameType.SURVIVAL);
            Consumer<LivingChangeTargetEvent> replaceWithVillager = event -> {
                if (event.getEntity() == owner) event.setNewAboutToBeSetTarget(villager);
            };
            NeoForge.EVENT_BUS.addListener(replaceWithVillager);
            try {
                owner.setTarget(player);
                h.assertTrue(owner.getTarget() == villager && !red(owner), kind + " requested player replaced by a villager keeps black eyes");
            } finally {
                NeoForge.EVENT_BUS.unregister(replaceWithVillager);
            }
            Consumer<LivingChangeTargetEvent> replaceWithPlayer = event -> {
                if (event.getEntity() == owner) event.setNewAboutToBeSetTarget(player);
            };
            NeoForge.EVENT_BUS.addListener(replaceWithPlayer);
            try {
                owner.setTarget(villager);
                h.assertTrue(owner.getTarget() == player && red(owner), kind + " requested villager replaced by a player turns red");
            } finally {
                NeoForge.EVENT_BUS.unregister(replaceWithPlayer);
            }
            owner.discard();
            player.discard();
        }
        villager.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeadPlayerTargetReturnsEyesToBlackOnTheNextServerUpdate(GameTestHelper h) {
        for (MobKind kind : RED_EYED_KINDS) {
            Mob owner = mob(h, kind);
            Player player = player(h, owner, GameType.SURVIVAL);
            owner.setTarget(player);
            h.assertTrue(red(owner), kind + " starts with a live targeted player");
            player.setHealth(0);
            h.assertFalse(player.isAlive(), "The target really died without a new setTarget call");
            owner.aiStep();
            h.assertFalse(red(owner), kind + " clears red eyes after discovering the existing target is dead");
            owner.discard();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeAndSpectatorPlayersNeverActivateTheRedEyeState(GameTestHelper h) {
        for (MobKind kind : RED_EYED_KINDS) {
            Mob owner = mob(h, kind);
            Player survival = player(h, owner, GameType.SURVIVAL);
            Player creative = player(h, owner, GameType.CREATIVE);
            Player spectator = player(h, owner, GameType.SPECTATOR);
            owner.setTarget(survival);
            h.assertTrue(red(owner), kind + " fixture first targets a valid survival player");
            owner.setTarget(creative);
            h.assertTrue(creative.isCreative() && !red(owner), kind + " creative target does not activate hostile eyes");
            owner.setTarget(spectator);
            h.assertTrue(spectator.isSpectator() && !red(owner), kind + " spectator target does not activate hostile eyes");
            owner.aiStep();
            h.assertFalse(red(owner), kind + " server refresh preserves black eyes for a spectator target");
            owner.discard();
            survival.discard();
            creative.discard();
            spectator.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualMetadataPacketsSynchronizeRedEyesWithoutSynchronizingTheTarget(GameTestHelper h) {
        for (MobKind kind : RED_EYED_KINDS) {
            Mob owner = mob(h, kind);
            Mob observer = MiteMobs.type(kind).create(h.getLevel());
            Player player = player(h, owner, GameType.SURVIVAL);
            owner.getEntityData().packDirty();
            h.assertTrue(observer.getTarget() == null && !red(observer), kind + " observer starts without a target or red eyes");
            owner.setTarget(player);
            transferEyeMetadata(h, owner, observer);
            h.assertTrue(observer.getTarget() == null && red(observer), kind + " received watcher metadata turns eyes red without any client target reference");
            owner.setTarget(null);
            transferEyeMetadata(h, owner, observer);
            h.assertTrue(observer.getTarget() == null && !red(observer), kind + " a subsequent false watcher value restores black eyes");
            owner.discard();
            observer.discard();
            player.discard();
        }
        h.succeed();
    }

    private static void transferEyeMetadata(GameTestHelper h, Mob source, Mob observer) {
        var dirty = source.getEntityData().packDirty();
        h.assertTrue(dirty != null && dirty.size() == 1 && dirty.getFirst().serializer() == EntityDataSerializers.BOOLEAN,
                "Changing only the accepted player target emits exactly one Boolean eye watcher");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer, new ClientboundSetEntityDataPacket(source.getId(), dirty));
            var packet = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
            h.assertTrue(packet.id() == source.getId() && !buffer.isReadable(), "Actual entity metadata codec preserves the entity ID and complete payload");
            observer.getEntityData().assignValues(packet.packedItems());
        } finally {
            buffer.release();
        }
    }
}
