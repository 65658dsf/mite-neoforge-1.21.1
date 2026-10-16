package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.curse.WitchCursing;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The real target-goal, native persistent entity NBT and accepted-death paths. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WitchCursingGameTests {
    private static Witch witch(GameTestHelper helper) {
        Witch witch = EntityType.WITCH.create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 10, 1));
        witch.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        witch.setNoAi(true);
        witch.setNoGravity(true);
        helper.getLevel().addFreshEntity(witch);
        return witch;
    }

    private static void clear(Witch... witches) {
        for (Witch witch : witches) {
            PlayerCurses.removeForWitch(witch);
            witch.discard();
        }
    }

    private static void wall(GameTestHelper helper, Witch witch, boolean solid) {
        for (int y = 0; y < 4; ++y) for (int z = -1; z <= 1; ++z)
            helper.getLevel().setBlock(witch.blockPosition().offset(1, y, z), (solid ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
    }

    @GameTest(template = "empty")
    public static void nativeEntitySavePreservesItsIndependentCurseSeed(GameTestHelper helper) {
        Witch witch = witch(helper);
        Witch loaded = EntityType.WITCH.create(helper.getLevel());
        try {
            int seed = WitchCursing.seed(witch);
            loaded.load(witch.saveWithoutId(new CompoundTag()));
            helper.assertValueEqual(WitchCursing.seed(loaded), seed, "Entity NBT preserves the witch's source curse seed");
            witch.getRandom().setSeed(0L);
            WitchCursing.seed(witch);
            helper.assertValueEqual(witch.getRandom().nextInt(4), 2, "Reading the independent curse seed does not consume entity AI randomness");
        } finally { clear(witch, loaded); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void validCandidateUsesOneQuarterChanceAndCannotQueueTwice(GameTestHelper helper) {
        Witch witch = witch(helper);
        try (var fixture = new SurvivalPlayer(helper)) {
            ServerPlayer player = fixture.player;
            player.moveTo(witch.getX() + 3, witch.getY(), witch.getZ());
            witch.getRandom().setSeed(0L);
            helper.assertFalse(WitchCursing.tryCurse(witch, player), "Source random draw 2 does not curse");
            witch.getRandom().setSeed(4096L);
            helper.assertTrue(WitchCursing.tryCurse(witch, player), "Source random draw 0 queues the delayed curse");
            witch.getRandom().setSeed(4096L);
            helper.assertFalse(WitchCursing.tryCurse(witch, player), "A pending curse cannot be overwritten by the same witch");
        } finally { clear(witch); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceGoalAttemptsTheCurseOnlyAfterLegalityAndSightChecks(GameTestHelper helper) {
        Witch witch = witch(helper);
        try (var fixture = new SurvivalPlayer(helper)) {
            ServerPlayer player = fixture.player;
            player.moveTo(witch.getX() + 3, witch.getY(), witch.getZ());
            var goal = new SourceMonsterTargeting.Nearest(witch, Player.class, 0, true);
            wall(helper, witch, true);
            witch.getRandom().setSeed(4096L);
            helper.assertFalse(goal.canUse(), "A stone wall hides the source witch's candidate");
            helper.assertValueEqual(witch.getRandom().nextInt(4), 0, "A blocked candidate never consumes the curse roll");
            wall(helper, witch, false);
            player.setGameMode(GameType.CREATIVE);
            witch.getRandom().setSeed(4096L);
            helper.assertFalse(goal.canUse(), "Creative players remain excluded from hostile acquisition");
            helper.assertValueEqual(witch.getRandom().nextInt(4), 0, "An excluded player never consumes the curse roll");
            player.setGameMode(GameType.SURVIVAL);
            witch.getRandom().setSeed(4096L);
            helper.assertTrue(goal.canUse(), "An unobstructed survival player is suitable");
            helper.assertFalse(PlayerCurses.schedule(player, witch, R196Curse.fromId(1), 6000),
                    "The actual suitable-candidate path has already scheduled exactly one curse");
        } finally { wall(helper, witch, false); clear(witch); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void witchesSurviveTheNativeFarDistanceDespawnCheck(GameTestHelper helper) {
        Witch witch = witch(helper);
        try (var fixture = new SurvivalPlayer(helper)) {
            fixture.player.moveTo(witch.getX() + 256, witch.getY(), witch.getZ());
            witch.setNoActionTime(1000);
            witch.checkDespawn();
            helper.assertFalse(witch.isRemoved(), "Source tv.t() forbids distance-based natural despawning");
        } finally { clear(witch); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyAnAcceptedWitchDeathReleasesHerPendingCurse(GameTestHelper helper) {
        Witch witch = witch(helper);
        Witch other = witch(helper);
        Consumer<LivingDeathEvent> cancel = event -> {
            if (event.getEntity() == witch) { witch.setHealth(1F); event.setCanceled(true); }
        };
        try (var fixture = new SurvivalPlayer(helper)) {
            helper.assertTrue(PlayerCurses.schedule(fixture.player, witch, R196Curse.fromId(1), 6000), "Fixture queues a witch-owned curse");
            NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, cancel);
            try { witch.kill(); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertFalse(witch.isDeadOrDying(), "The cancellation rescues the original caster");
            helper.assertFalse(PlayerCurses.schedule(fixture.player, other, R196Curse.fromId(2), 6000),
                    "Canceled death must not remove the first caster's pending curse");
            witch.invulnerableTime = 0;
            witch.kill();
            helper.assertTrue(witch.isDeadOrDying(), "The uncanceled kill reaches the death tail");
            helper.assertTrue(PlayerCurses.schedule(fixture.player, other, R196Curse.fromId(2), 6000),
                    "The accepted death releases the pending curse so a new caster can schedule one");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); clear(witch, other); }
        helper.succeed();
    }

    private static final class SurvivalPlayer implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        SurvivalPlayer(GameTestHelper helper) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "curse-" + UUID.randomUUID().toString().substring(0, 8)), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
        }
        @Override public void close() { player.server.getPlayerList().remove(player); channel.finishAndReleaseAll(); }
    }
}
