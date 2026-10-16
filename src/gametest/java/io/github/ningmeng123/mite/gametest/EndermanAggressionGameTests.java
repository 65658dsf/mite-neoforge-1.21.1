package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.entity.SourceEndermanAggression;
import io.github.ningmeng123.mite.entity.SourceEndermanAggression.Decision;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent source decision boundaries and real native Goal lifecycle checks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class EndermanAggressionGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private EndermanAggressionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sourceDecisionOrderStopsAfterCurseHeldInventoryOrNativeReason(GameTestHelper h) {
        run(h, f -> {
            var nativeCalls = new AtomicInteger();
            f.curse();
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL));
            f.random.arm(3, 0);
            Decision curse = SourceEndermanAggression.decide(f.enderman, f.player, entity -> { nativeCalls.incrementAndGet(); return true; });
            h.assertTrue(curse == Decision.CURSE && curse.accepted() && !curse.immediate(), "Curse success precedes held-item immediate behavior");
            f.random.done(h);
            PlayerCurses.cure(f.player);
            f.random.arm(3, 0);
            h.assertValueEqual(SourceEndermanAggression.decide(f.enderman, f.player, entity -> { nativeCalls.incrementAndGet(); return true; }),
                    Decision.HELD_ITEM, "An uncursed player still consumes the first source draw, then triggers held branch");
            f.random.done(h);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            f.player.getInventory().items.set(9, new ItemStack(Items.ENDER_EYE, 5));
            f.random.arm(3, 2, 2000, 4);
            h.assertValueEqual(SourceEndermanAggression.decide(f.enderman, f.player, entity -> { nativeCalls.incrementAndGet(); return true; }),
                    Decision.INVENTORY_ITEMS, "Count five accepts roll four before native gaze");
            f.random.done(h);
            h.assertValueEqual(nativeCalls.get(), 0, "Earlier source branches never call native fallback");
            f.random.arm(3, 1, 2000, 5);
            h.assertValueEqual(SourceEndermanAggression.decide(f.enderman, f.player, entity -> { nativeCalls.incrementAndGet(); return true; }),
                    Decision.NATIVE, "At the inventory boundary native aggression is evaluated once");
            f.random.done(h);
            h.assertValueEqual(nativeCalls.get(), 1, "One native fallback evaluation");
            f.random.disarm();
            f.enderman.setTarget(f.player);
            f.random.arm();
            h.assertValueEqual(SourceEndermanAggression.decide(f.enderman, f.player, entity -> { nativeCalls.incrementAndGet(); return true; }),
                    Decision.NONE, "Acquired targets never run acquisition rules again");
            f.random.done(h);
            h.assertValueEqual(nativeCalls.get(), 1, "Acquired-target guard does not invoke native predicate");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void inventoryProvocationUsesStrictCountOverTwoThousandBoundariesAndSourceDrawCounts(GameTestHelper h) {
        run(h, f -> {
            for (int count : new int[]{0, 1, 2, 16, 1999, 2000, 2001}) {
                for (int roll : new int[]{0, 1, 15, 16, 1998, 1999}) {
                    f.player.getInventory().items.set(9, count == 0 ? ItemStack.EMPTY : new ItemStack(Items.ENDER_EYE, count));
                    if (count == 0) f.random.arm(3, 1); else f.random.arm(3, 1, 2000, roll);
                    Decision decision = SourceEndermanAggression.decide(f.enderman, f.player, entity -> false);
                    boolean expected = count > 0 && roll < count;
                    h.assertTrue(decision == (expected ? Decision.INVENTORY_ITEMS : Decision.NONE),
                            "Strict source roll<count at count=" + count + ", roll=" + roll);
                    h.assertTrue(decision.immediate() == expected, "Successful inventory branch is immediate");
                    f.random.done(h);
                }
            }
            // Clear the intentionally oversized boundary fixture before any inventory synchronization/save.
            f.player.getInventory().items.set(9, ItemStack.EMPTY);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void directInventoryCountExcludesCursorCraftingOtherContainersAndNestedItems(GameTestHelper h) {
        run(h, f -> {
            f.player.getInventory().items.set(0, new ItemStack(Items.ENDER_PEARL, 2));
            f.player.getInventory().items.set(9, new ItemStack(Items.ENDER_EYE, 3));
            f.player.getInventory().armor.set(0, new ItemStack(Items.ENDER_PEARL, 4));
            f.player.getInventory().offhand.set(0, new ItemStack(Items.ENDER_EYE, 5));
            h.assertValueEqual(SourceEndermanAggression.countItems(f.player), 14, "Hotbar, backpack, armor and offhand each counted once");
            f.player.inventoryMenu.getSlot(1).set(new ItemStack(Items.ENDER_PEARL, 7));
            f.player.getEnderChestInventory().setItem(0, new ItemStack(Items.ENDER_EYE, 8));
            var otherChest = new SimpleContainer(27);
            otherChest.setItem(0, new ItemStack(Items.ENDER_PEARL, 9));
            f.player.containerMenu = ChestMenu.threeRows(91, f.player.getInventory(), otherChest);
            f.player.containerMenu.setCarried(new ItemStack(Items.ENDER_EYE, 10));
            ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
            shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.ENDER_PEARL, 11))));
            f.player.getInventory().items.set(20, shulker);
            ItemStack bundle = new ItemStack(Items.BUNDLE);
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.ENDER_EYE, 12))));
            f.player.getInventory().items.set(21, bundle);
            h.assertValueEqual(SourceEndermanAggression.countItems(f.player), 14,
                    "Cursor, own crafting grid, open chest, ender chest, shulker and bundle contents do not count");
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            h.assertTrue(SourceEndermanAggression.holdsEnderItem(f.player), "Modern offhand eye counts as actually held");
            f.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            h.assertTrue(!SourceEndermanAggression.holdsEnderItem(f.player), "Backpacked items do not count as held");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void eachHeldPearlAndEyeInEitherHandAcquiresImmediatelyWithoutGazeOrSecondRoll(GameTestHelper h) {
        run(h, f -> {
            for (InteractionHand hand : InteractionHand.values()) for (Item item : List.of(Items.ENDER_PEARL, Items.ENDER_EYE)) {
                f.reset();
                f.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
                f.faceAway();
                ItemStack held = new ItemStack(item);
                f.player.setItemInHand(hand, held);
                f.random.arm(3, 1);
                h.assertTrue(f.goal.canUse(), "Visible held item provokes with pumpkin while facing away: " + hand + "/" + item);
                f.goal.start();
                h.assertTrue(f.enderman.getTarget() == f.player, "Same start call acquires target without native warning delay");
                h.assertTrue(f.player.getItemInHand(hand) == held && held.getCount() == 1, "Provocation does not consume or move held item");
                f.random.done(h);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void successfulBackpackRollAcquiresImmediatelyAndFailedBoundaryDoesNotAcquire(GameTestHelper h) {
        run(h, f -> {
            f.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            f.faceAway();
            f.player.getInventory().items.set(9, new ItemStack(Items.ENDER_EYE, 5));
            f.random.arm(3, 1, 2000, 5);
            h.assertTrue(!f.goal.canUse(), "Roll equal to count does not find a provoked candidate");
            f.random.done(h);
            f.random.arm(3, 1, 2000, 4);
            h.assertTrue(f.goal.canUse(), "Roll one below count provokes the visible backpack carrier");
            f.goal.start();
            h.assertTrue(f.enderman.getTarget() == f.player, "Backpack success enters native target state on start");
            f.random.done(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nearestUnprovokedVisiblePlayerBlocksFartherHeldItemCarrier(GameTestHelper h) {
        run(h, f -> {
            f.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            f.faceAway();
            ServerPlayer farther = f.addPlayer(f.pos.getX() + 6.5D, f.pos.getY(), f.pos.getZ() + .5D);
            farther.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL));
            f.random.arm(3, 1);
            h.assertTrue(!f.goal.canUse() && f.enderman.getTarget() == null,
                    "Source checks only nearest legal visible player, even if a farther player carries a pearl");
            f.random.done(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void wallsRejectHeldItemAndCurseCandidatesBeforeAnyProvocationDraw(GameTestHelper h) {
        run(h, f -> {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE));
            f.wall();
            f.random.arm();
            h.assertTrue(!f.goal.canUse(), "Stone wall excludes a normal held-item carrier before RNG");
            f.random.done(h);
            f.curse();
            f.random.arm();
            h.assertTrue(!f.goal.canUse(), "R196 upstream visible-candidate search also blocks cursed players behind walls");
            f.random.done(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void creativeSpectatorAndSourceSixtyFourBoundaryRejectBeforeDrawing(GameTestHelper h) {
        run(h, f -> {
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL));
            for (GameType type : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
                f.player.setGameMode(type);
                f.random.arm();
                h.assertTrue(!f.goal.canUse(), "Combat targeting excludes " + type + " before source draws");
                f.random.done(h);
            }
            f.player.setGameMode(GameType.SURVIVAL);
            for (double distance : new double[]{64D, 64.01D, 80D}) {
                f.player.moveTo(f.enderman.getX() + distance, f.enderman.getY(), f.enderman.getZ());
                f.enderman.getSensing().tick();
                f.random.arm();
                h.assertTrue(!f.goal.canUse(), "Source range is strictly below sixty-four, distance=" + distance);
                f.random.done(h);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pendingCurseWarningRechecksItemsAndStopsAfterTheyAreRemoved(GameTestHelper h) {
        run(h, f -> {
            f.curse();
            f.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            f.faceAway();
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL));
            f.random.arm(3, 0);
            h.assertTrue(f.goal.canUse(), "Successful curse takes priority over held immediate branch");
            f.goal.start();
            h.assertTrue(f.enderman.getTarget() == null, "Curse retains warning phase even when an item is held");
            f.random.done(h);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            f.random.arm(3, 1);
            h.assertTrue(!f.goal.canContinueToUse(), "Removing the item and failing the next curse check cancels pending warning");
            f.random.done(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pendingTargetRecognizesPearlMovedIntoModernOffhandWithoutDuplicateRoll(GameTestHelper h) {
        run(h, f -> {
            f.curse();
            f.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            f.faceAway();
            ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, pearl);
            f.random.arm(3, 0);
            h.assertTrue(f.goal.canUse(), "Curse opens the warning phase first");
            f.goal.start();
            f.random.done(h);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            f.player.setItemInHand(InteractionHand.OFF_HAND, pearl);
            f.random.arm(3, 1);
            h.assertTrue(f.goal.canContinueToUse(), "Current offhand holding is re-evaluated during pending state");
            f.goal.tick();
            h.assertTrue(f.enderman.getTarget() == f.player && f.player.getOffhandItem() == pearl,
                    "Offhand provocation promotes the existing pending target without consuming the pearl");
            f.random.done(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void removingEnderItemsDoesNotClearAnAlreadyAcquiredTargetOrFakeFreezeGaze(GameTestHelper h) {
        run(h, f -> {
            f.curse();
            f.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            f.faceAway();
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE));
            f.random.arm(3, 1);
            h.assertTrue(f.goal.canUse(), "Held eye provokes after the curse roll misses");
            f.goal.start();
            f.random.done(h);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            f.random.arm();
            h.assertTrue(f.goal.canContinueToUse() && f.enderman.getTarget() == f.player,
                    "Targeted pursuit continues natively after item removal and never re-enters acquisition RNG");
            Goal freeze = f.enderman.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(goal -> goal.getClass().getSimpleName().equals("EndermanFreezeWhenLookedAt"))
                    .findFirst().orElseThrow();
            h.assertTrue(!freeze.canUse() && !nativeGaze(f.enderman, f.player),
                    "Pumpkin/back-turned player never becomes a fake gaze in the native freeze path");
            f.goal.tick();
            h.assertTrue(f.enderman.getTarget() == f.player, "Native pursuit tick keeps the acquired target");
            f.random.done(h);
        });
    }

    private static boolean nativeGaze(EnderMan mob, ServerPlayer player) {
        try {
            Method method = EnderMan.class.getDeclaredMethod("isLookingAtMe", net.minecraft.world.entity.player.Player.class);
            method.setAccessible(true);
            return (boolean)method.invoke(mob, player);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static void run(GameTestHelper h, Consumer<Fixture> test) {
        BlockPos pos = new BlockPos(-25_000_000 + IDS.incrementAndGet() * 64 + 8, 160, -25_000_000 + 8);
        ChunkPos chunk = new ChunkPos(pos);
        boolean forcedBefore = h.getLevel().getForcedChunks().contains(chunk.toLong());
        h.getLevel().setChunkForced(chunk.x, chunk.z, true);
        h.getLevel().getChunkAt(pos);
        h.runAfterDelay(10, () -> {
            try (Fixture fixture = new Fixture(h, pos)) {
                test.accept(fixture);
                h.succeed();
            } finally {
                if (!forcedBefore) h.getLevel().setChunkForced(chunk.x, chunk.z, false);
            }
        });
    }

    private record Roll(int bound, int value) {}
    private static final class ScriptedRandom extends LegacyRandomSource {
        private final ArrayDeque<Roll> expected = new ArrayDeque<>();
        private boolean armed;
        ScriptedRandom() { super(123456789L); }
        void arm(int... pairs) {
            if (pairs.length % 2 != 0) throw new IllegalArgumentException("Bound/value pairs required");
            expected.clear();
            for (int i = 0; i < pairs.length; i += 2) expected.addLast(new Roll(pairs[i], pairs[i + 1]));
            armed = true;
        }
        void disarm() { armed = false; expected.clear(); }
        @Override public int nextInt(int bound) {
            if (!armed) return super.nextInt(bound);
            Roll next = expected.pollFirst();
            if (next == null || next.bound != bound || next.value < 0 || next.value >= bound)
                throw new AssertionError("Unexpected acquisition RNG nextInt(" + bound + "), expected " + next);
            return next.value;
        }
        void done(GameTestHelper h) {
            h.assertTrue(expected.isEmpty(), "Every independent source random draw was consumed once: remaining=" + expected);
            disarm();
        }
    }
    private static final class ScriptedEnderman extends EnderMan {
        ScriptedRandom scripted;
        ScriptedEnderman(Level level) { super(EntityType.ENDERMAN, level); scripted = new ScriptedRandom(); }
        @Override public RandomSource getRandom() { return scripted == null ? super.getRandom() : scripted; }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final BlockPos pos;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        final ScriptedEnderman enderman;
        final ScriptedRandom random;
        final ServerPlayer player;
        final Witch witch;
        final Goal goal;
        Fixture(GameTestHelper helper, BlockPos position) {
            h = helper; pos = position;
            player = addPlayer(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
            enderman = new ScriptedEnderman(h.getLevel());
            enderman.setNoAi(true); enderman.setNoGravity(true);
            enderman.moveTo(pos.getX() - 4.5D, pos.getY(), pos.getZ() + .5D);
            h.assertTrue(h.getLevel().addFreshEntity(enderman), "Actual source-tagged native Enderman registered");
            random = enderman.scripted;
            goal = enderman.targetSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(candidate -> candidate.getClass().getSimpleName().equals("EndermanLookForPlayerGoal"))
                    .findFirst().orElseThrow();
            witch = EntityType.WITCH.create(h.getLevel()); witch.setNoAi(true);
            faceAway();
        }
        ServerPlayer addPlayer(double x, double y, double z) {
            var profile = new GameProfile(UUID.randomUUID(), "ender-" + UUID.randomUUID().toString().substring(0, 8));
            var cookie = CommonListenerCookie.createInitial(profile, false);
            ServerPlayer created = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, created, cookie);
            created.setGameMode(GameType.SURVIVAL); created.setNoGravity(true); created.moveTo(x, y, z);
            players.add(created); channels.add(channel);
            return created;
        }
        void faceAway() { player.setYRot(-90F); player.setXRot(0F); } // Enderman is to the west, so face east.
        void curse() {
            h.assertTrue(PlayerCurses.schedule(player, witch, R196Curse.ENDERMEN_ENEMY, 0), "Isolated Enderman curse scheduled");
            PlayerCurses.reconcile(player);
        }
        void reset() {
            random.disarm(); goal.stop(); enderman.setTarget(null);
            enderman.setPersistentAngerTarget(null); enderman.setRemainingPersistentAngerTime(0);
            PlayerCurses.cure(player); player.getInventory().clearContent();
            enderman.getSensing().tick();
        }
        void wall() {
            for (int y = 0; y < 4; y++) for (int z = -1; z <= 1; z++) {
                BlockPos at = pos.offset(-2, y, z);
                before.putIfAbsent(at, h.getLevel().getBlockState(at));
                h.getLevel().setBlock(at, Blocks.STONE.defaultBlockState(), 2);
            }
            enderman.getSensing().tick();
            h.assertTrue(!enderman.getSensing().hasLineOfSight(player), "A real stone wall blocks candidate sight");
        }
        @Override public void close() {
            random.disarm();
            enderman.discard(); witch.discard();
            for (ServerPlayer p : players) {
                PlayerCurses.cure(p);
                p.containerMenu.setCarried(ItemStack.EMPTY);
                for (int slot = 1; slot <= 4; slot++) p.inventoryMenu.getSlot(slot).set(ItemStack.EMPTY);
                p.getInventory().clearContent(); p.getEnderChestInventory().clearContent();
                p.closeContainer();
                p.server.getPlayerList().remove(p);
            }
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
            before.forEach((at, state) -> h.getLevel().setBlock(at, state, 2));
        }
    }
}
