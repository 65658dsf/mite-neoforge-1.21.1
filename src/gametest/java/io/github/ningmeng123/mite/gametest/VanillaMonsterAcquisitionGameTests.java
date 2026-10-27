package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.MonsterHunting;
import io.github.ningmeng123.mite.entity.NativeIllagerCombat;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Full native world ticks discover real creative-to-survival players, with no assigned targets. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class VanillaMonsterAcquisitionGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_vanilla_acquisition_test", Integer::compareTo);
    private VanillaMonsterAcquisitionGameTests() {}

    @GameTest(template="empty", batch="illager_acquisition_14", timeoutTicks=400)
    public static void vindicatorNaturallyFindsAndAttacksFourteenBlocksAfterSurvivalSwitch(GameTestHelper h) {
        new Fixture(h, EntityType.VINDICATOR, 14D, null, false, false).combat(false);
    }

    @GameTest(template="empty", batch="illager_acquisition_16", timeoutTicks=400)
    public static void vindicatorNaturallyFindsAndAttacksSixteenBlocksAfterSurvivalSwitch(GameTestHelper h) {
        new Fixture(h, EntityType.VINDICATOR, 16D, null, false, false).combat(false);
    }

    @GameTest(template="empty", batch="illager_acquisition_24", timeoutTicks=400)
    public static void vindicatorNaturallyFindsAndAttacksTwentyFourBlocksAfterSurvivalSwitch(GameTestHelper h) {
        new Fixture(h, EntityType.VINDICATOR, 24D, null, false, false).combat(false);
    }

    @GameTest(template="empty", batch="illager_old_range", timeoutTicks=400)
    public static void oldSavedTwelveBlockVindicatorRepairsItsLivePlayerSearchAndActuallyAttacks(GameTestHelper h) {
        new Fixture(h, EntityType.VINDICATOR, 24D, 12D, false, false).combat(false);
    }

    @GameTest(template="empty", batch="illager_custom_range", timeoutTicks=240)
    public static void largerSavedVindicatorRangeIsPreservedAndUsedByItsExistingNativeSelector(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, 40D, 48D, false, false);
        f.begin(true).thenWaitUntil(() -> h.assertTrue(f.mob.getTarget() == f.player,
                "The existing native selector must use the preserved forty-eight-block saved range: " + f.diagnostics()))
                .thenExecute(() -> {
                    h.assertValueEqual(f.mob.getAttribute(Attributes.FOLLOW_RANGE).getBaseValue(), 48D,
                            "The compatibility repair never lowers a larger custom base");
                    f.close();
                }).thenSucceed();
    }

    @GameTest(template="empty", batch="illager_range_and_sight", timeoutTicks=320)
    public static void increasedRangeStillHonorsItsActualBoundaryAndSolidWallSight(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.VINDICATOR, 40D, null, true, false);
        f.begin(false).thenExecute(() -> {
            h.assertValueEqual(f.mob.getAttribute(Attributes.FOLLOW_RANGE).getBaseValue(), 32D,
                    "The native base is repaired to thirty-two while spawn modifiers remain intact");
            f.player.setPos(f.mob.position().add(f.mob.getAttributeValue(Attributes.FOLLOW_RANGE) + 1D, 0D, 0D));
        }).thenIdle(60).thenExecute(() -> {
            h.assertTrue(f.mob.getTarget() == null, "A visible player outside the actual follow range is not acquired: " + f.diagnostics());
            f.player.setPos(f.mob.position().add(f.mob.getAttributeValue(Attributes.FOLLOW_RANGE) - .25D, 0D, 0D));
            f.wall(true);
            h.assertFalse(f.mob.hasLineOfSight(f.player), "The inside-range player is genuinely hidden by the test wall");
        }).thenIdle(60).thenExecute(() -> {
            h.assertTrue(f.mob.getTarget() == null, "A larger follow range grants no through-wall first acquisition");
            f.wall(false);
        }).thenWaitUntil(() -> h.assertTrue(f.mob.getTarget() == f.player,
                "Removing the wall permits natural acquisition just inside the actual follow boundary: " + f.diagnostics()))
                .thenExecute(f::close).thenSucceed();
    }

    @GameTest(template="empty", batch="unarmed_pillager_acquisition", timeoutTicks=400)
    public static void earlyPillagerKeepsItsNaturalCrossbowAndActuallyShootsAfterSurvivalSwitch(GameTestHelper h) {
        new Fixture(h, EntityType.PILLAGER, 10D, null, false, false).crossbowCombat();
    }

    @GameTest(template="empty", batch="unarmed_pillager_two_steps", timeoutTicks=400)
    public static void aPillagerLosingItsCrossbowClimbsTwoRealStepsHitsThenReturnsToRangedCombat(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.PILLAGER, 10D, null, false, true);
        h.assertTrue(f.mob.getMainHandItem().getItem() instanceof CrossbowItem, "The actual spawn first supplies its original crossbow");
        f.mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        f.mob.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        f.combat(true);
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final int key = IDS.getAndIncrement();
        final BlockPos origin;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final PathfinderMob mob;
        final List<ChunkPos> chunks = new ArrayList<>();
        final boolean pillager, platform;
        final Double savedBase;
        final long oldTime, oldHighestDay;
        int creativeTick;
        float survivalHealth;
        boolean closed;

        Fixture(GameTestHelper h, EntityType<? extends PathfinderMob> type, double distance, Double savedBase,
                boolean stationary, boolean platform) {
            this.h = h; this.savedBase = savedBase; this.platform = platform; pillager = type == EntityType.PILLAGER;
            oldTime = h.getLevel().getDayTime(); oldHighestDay = BloodMoonInvasionData.get(h.getLevel()).highestDay();
            h.getLevel().setDayTime(0L); BloodMoonInvasionData.get(h.getLevel()).restoreProgress(0L);
            origin = new BlockPos(645_000 + key * 96, h.absolutePos(BlockPos.ZERO).getY() + 12, 645_000);
            for (int x = -6; x <= 48; x++) for (int z = -7; z <= 7; z++) for (int y = -1; y <= 5; y++)
                h.getLevel().setBlock(origin.offset(x, y, z), (y == -1 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            if (platform) {
                int edge = (int)distance - 3;
                for (int x = edge - 2; x < edge; x++) for (int z = -3; z <= 3; z++)
                    h.getLevel().setBlock(origin.offset(x, 0, z), Blocks.BEDROCK.defaultBlockState(), 2);
                for (int x = edge; x <= edge + 6; x++) for (int z = -3; z <= 3; z++) for (int y = 0; y <= 1; y++)
                    h.getLevel().setBlock(origin.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
            ChunkPos low = new ChunkPos(origin.offset(-6, 0, -7)), high = new ChunkPos(origin.offset(48, 0, 7));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, key);
                for (int cx = x - 2; cx <= x + 2; cx++) for (int cz = z - 2; cz <= z + 2; cz++)
                    h.getLevel().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
            }
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "acquire-" + key), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.CREATIVE);
            // PlayerHealthEvents legitimately refreshes its level-based base each tick; a modifier keeps this damage fixture alive.
            player.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "acquisition_test_health"), 100D, AttributeModifier.Operation.ADD_VALUE));
            player.setHealth(player.getMaxHealth());
            player.moveTo(Vec3.atBottomCenterOf(origin).add(distance, platform ? 2D : 0D, 0D));
            if (savedBase == null) {
                mob = type.create(h.getLevel());
                mob.moveTo(Vec3.atBottomCenterOf(origin));
                EventHooks.finalizeMobSpawn(mob, h.getLevel(), h.getLevel().getCurrentDifficultyAt(origin), MobSpawnType.SPAWN_EGG, null);
            } else {
                PathfinderMob old = type.create(h.getLevel());
                old.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(savedBase);
                CompoundTag save = old.saveWithoutId(new CompoundTag());
                mob = type.create(h.getLevel()); mob.load(save); mob.setUUID(UUID.randomUUID());
                mob.moveTo(Vec3.atBottomCenterOf(origin));
            }
            if (stationary) mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0D);
            mob.setPersistenceRequired();
            if (savedBase == null) h.getLevel().addFreshEntity(mob);
            else {
                // A deserialized inhabitant enters through the real legacy loader. Treating it as a new
                // unfinalized birth would correctly trigger addons' independent automatic-spawn date gates.
                h.getLevel().addLegacyChunkEntities(java.util.stream.Stream.of(mob));
            }
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }

        GameTestSequence begin(boolean requireInitiallyVisibleTarget) {
            return h.startSequence().thenWaitUntil(this::ready).thenExecute(() -> {
                h.assertFalse(mob.isNoAi(), "Full native AI and physics remain enabled");
                h.assertTrue(player.isCreative() && mob.getTarget() == null,
                        "The monster is spawned beside a creative player without an assigned target");
                if (!pillager) h.assertValueEqual(mob.getAttribute(Attributes.FOLLOW_RANGE).getBaseValue(),
                        savedBase != null && savedBase > 32D ? savedBase : 32D, "New and old saved bases are repaired without lowering a custom range");
                if (pillager) {
                    h.assertTrue(platform ? mob.getMainHandItem().isEmpty() && mob.getOffhandItem().isEmpty()
                                    : mob.isHolding(stack -> stack.getItem() instanceof CrossbowItem),
                            "Natural early births keep their crossbow; only the explicit lost-weapon fixture is empty-handed");
                    assertOneFallback();
                    NativeIllagerCombat.join(new EntityJoinLevelEvent(mob, h.getLevel()));
                    NativeIllagerCombat.join(new EntityJoinLevelEvent(mob, h.getLevel()));
                    assertOneFallback();
                }
                creativeTick = mob.tickCount;
            }).thenIdle(30).thenExecute(() -> {
                h.assertTrue(mob.isAlive() && mob.tickCount >= creativeTick + 25,
                        "At least twenty-five complete natural monster ticks occur before the mode change: " + diagnostics());
                h.assertTrue(mob.getTarget() == null, "Natural player acquisition ignores creative mode");
                player.setGameMode(GameType.SURVIVAL);
                h.assertTrue(!player.isCreative() && !player.isSpectator() && !player.getAbilities().invulnerable,
                        "A real survival mode change clears creative invulnerability");
                h.assertTrue(MonsterHunting.mayAttackPrey(mob, player), "The blood-night prey gate permits real players");
                if (requireInitiallyVisibleTarget) h.assertTrue(TargetingConditions.forCombat().range(mob.getAttributeValue(Attributes.FOLLOW_RANGE)).test(mob, player),
                        "The native combat predicate accepts the visible survival player: " + diagnostics());
                survivalHealth = player.getHealth();
            });
        }

        void combat(boolean rearmWithCrossbow) {
            GameTestSequence sequence = begin(true).thenWaitUntil(() -> h.assertTrue(mob.getTarget() == player,
                    "The existing native target selector must proactively discover the survival player: " + diagnostics()))
                    .thenWaitUntil(() -> h.assertTrue(player.getHealth() < survivalHealth,
                            "The naturally acquired player must receive actual attack damage, not just random wandering: " + diagnostics()))
                    .thenExecute(() -> {
                        if (pillager) {
                            h.assertTrue(mob.getMainHandItem().isEmpty() && mob.getOffhandItem().isEmpty(),
                                    "Real fallback melee damage is possible after explicitly losing the original crossbow");
                            assertOneFallback();
                        }
                        if (platform) h.assertTrue(mob.getY() >= origin.getY() + 1.8D,
                                "The pillager physically climbs both one-block steps onto the two-block platform before hitting");
                    });
            if (rearmWithCrossbow) {
                sequence.thenExecute(() -> {
                    player.setHealth(player.getMaxHealth());
                    mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
                    h.assertTrue(mob.getMainHandItem().getItem() instanceof CrossbowItem,
                            "Rearming uses a genuine crossbow rather than a guessed item identifier");
                    var fallback = mob.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal())
                            .filter(NativeIllagerCombat.UnarmedPillagerMeleeGoal.class::isInstance)
                            .map(NativeIllagerCombat.UnarmedPillagerMeleeGoal.class::cast).findFirst().orElseThrow();
                    h.assertFalse(fallback.canUse() || fallback.canContinueToUse(), "A held crossbow immediately disables the empty-hand fallback");
                }).thenWaitUntil(() -> h.assertTrue(mob.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.isRunning()
                                && w.getGoal() instanceof RangedCrossbowAttackGoal<?>),
                        "Natural goal arbitration returns to the original ranged crossbow goal: " + diagnostics()));
            }
            sequence.thenExecute(this::close).thenSucceed();
        }

        void crossbowCombat() {
            begin(true).thenWaitUntil(() -> h.assertTrue(mob.getTarget() == player,
                    "The naturally armed pillager proactively discovers the survival player: " + diagnostics()))
                    .thenWaitUntil(() -> h.assertTrue(player.getHealth() < survivalHealth,
                            "The retained natural crossbow causes real ranged damage: " + diagnostics()))
                    .thenExecute(() -> {
                        h.assertTrue(mob.isHolding(stack -> stack.getItem() instanceof CrossbowItem),
                                "Native or addon CrossbowItem weapons remain equipped on early births");
                        h.assertTrue(mob.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.isRunning()
                                        && w.getGoal() instanceof RangedCrossbowAttackGoal<?>),
                                "The real native ranged goal produces the attack");
                        h.assertFalse(mob.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.isRunning()
                                        && w.getGoal() instanceof NativeIllagerCombat.UnarmedPillagerMeleeGoal),
                                "A held crossbow never runs the melee fallback");
                        assertOneFallback(); close();
                    }).thenSucceed();
        }

        void assertOneFallback() {
            h.assertValueEqual(mob.goalSelector.getAvailableGoals().stream()
                    .filter(w -> w.getGoal() instanceof NativeIllagerCombat.UnarmedPillagerMeleeGoal).count(), 1L,
                    "Repeated join processing installs exactly one empty-hand melee fallback");
        }

        void ready() {
            for (int tasks = 0; tasks < 64 && h.getLevel().getChunkSource().pollTask(); tasks++) {}
            for (ChunkPos chunk : chunks) h.assertTrue(h.getLevel().isPositionEntityTicking(chunk.getWorldPosition()),
                    "The full natural AI fixture is entity-ticking: " + chunk);
            h.assertTrue(h.getLevel().getEntity(mob.getUUID()) == mob && h.getLevel().players().contains(player),
                    "The untouched native monster and registered player are present in the same server level");
        }

        void wall(boolean present) {
            for (int y = 0; y < 5; y++) for (int z = -3; z <= 3; z++)
                h.getLevel().setBlock(origin.offset(2, y, z), (present ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
        }

        String diagnostics() {
            return "mob=" + mob.getType() + ", tick=" + mob.tickCount + ", target=" + mob.getTarget()
                    + ", distance=" + mob.distanceTo(player) + ", follow=" + mob.getAttributeValue(Attributes.FOLLOW_RANGE)
                    + ", canAttack=" + mob.canAttack(player) + ", visible=" + mob.hasLineOfSight(player)
                    + ", visibility=" + player.getVisibilityPercent(mob) + ", allied=" + mob.isAlliedTo(player)
                    + ", invulnerable=" + player.getAbilities().invulnerable + ", weapon=" + mob.getMainHandItem()
                    + ", health=" + player.getHealth() + ", path=" + mob.getNavigation().getPath();
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            mob.discard(); player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
            for (ChunkPos chunk : chunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, key);
            h.getLevel().setDayTime(oldTime); BloodMoonInvasionData.get(h.getLevel()).restoreProgress(oldHighestDay);
        }
    }
}
