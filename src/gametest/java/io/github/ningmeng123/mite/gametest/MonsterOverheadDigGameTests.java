package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual player perches, physical head clearance and protected staged overhead mining. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MonsterOverheadDigGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_overhead_dig_test", Integer::compareTo);

    @GameTest(template="empty")
    public static void ordinaryUnarmedZombieStartsOnTheBlockAboveItsHeadWithoutSight(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2, false, Blocks.DIRT.defaultBlockState())) {
            h.assertFalse(R196MonsterAi.diggingEnabled(f.mob), "Ordinary non-smart empty-hand zombie does not use the general source digging exception");
            h.assertFalse(f.mob.getSensing().hasLineOfSight(f.player), "The actual roof hides the perched player");
            h.assertFalse(SourceMeleeGoal.canStrike(f.mob, f.player), "The player cannot be struck from the supported position under the roof");
            h.assertTrue(f.goal.canDestroy(f.ceiling, true), "The actual head-blocking dirt is physically reachable");
            h.assertTrue(f.goal.canUse(), "A nearby player directly above activates overhead digging without the smart flag or line of sight");
            h.assertValueEqual(f.goal.targetBlock(), f.ceiling, "The selected target is the block over the monster's own head");
            f.goal.start();
            h.assertTrue(f.goal.canContinueToUse(), "The overhead task continues while the player remains on the roof");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void overheadDirtKeepsFortyTickDelayAndElevenRealActions(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2, false, Blocks.DIRT.defaultBlockState())) {
            h.assertTrue(f.goal.canUse(), "Actual overhead fixture starts");
            f.goal.start();
            h.assertValueEqual(f.goal.stageDelay(f.ceiling), 150, "Ordinary dirt keeps its existing mining speed");
            f.tick(39);
            h.assertValueEqual(f.goal.progress(), -1, "The first 39 ticks cannot start a crack or instantly destroy the roof");
            h.assertTrue(h.getLevel().getBlockState(f.ceiling).is(Blocks.DIRT), "The player support remains intact during the opening delay");
            var saved = f.mob.getPersistentData().getCompound("mite_digging_031");
            h.assertValueEqual(BlockPos.of(saved.getLong("position")), f.ceiling, "Overhead mining saves the actual roof target");
            h.assertValueEqual(saved.getInt("cooldown"), 1, "The normal saved countdown advances for an overhead target");
            f.tick(1);
            h.assertValueEqual(f.goal.progress(), 0, "Tick 40 performs the first ordinary mining action");
            f.tick(150 * 9);
            h.assertValueEqual(f.goal.progress(), 9, "Ten actions still leave the supported roof intact");
            f.tick(149);
            h.assertTrue(h.getLevel().getBlockState(f.ceiling).is(Blocks.DIRT), "The final action must wait its complete interval");
            f.tick(1);
            h.assertTrue(h.getLevel().isEmptyBlock(f.ceiling), "The eleventh real action removes the overhead block");
            h.assertFalse(f.mob.getPersistentData().contains("mite_digging_031"), "A completed overhead break clears the normal active save state");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void smallZombieUsesItsOwnActualHeadHeight(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1, true, Blocks.DIRT.defaultBlockState())) {
            h.assertTrue(f.mob.getBbHeight() < 1F, "The scaled small zombie fits beneath its one-block-high roof");
            h.assertFalse(f.mob.getSensing().hasLineOfSight(f.player), "The small zombie's player target is hidden above the roof");
            h.assertTrue(f.goal.canUse(), "A non-smart small zombie selects its physically blocking overhead dirt");
            h.assertValueEqual(f.goal.targetBlock(), f.ceiling, "The small body targets its own ceiling instead of the full-size height");
            f.goal.start();
            f.tick(40 + 10 * f.goal.stageDelay(f.ceiling));
            h.assertTrue(h.getLevel().isEmptyBlock(f.ceiling), "The small zombie completes the ordinary staged overhead break");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void partialTopSlabIsSelectedByItsActualCollisionShape(GameTestHelper h) {
        BlockState roof = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        try (Fixture f = new Fixture(h, 2, false, roof)) {
            f.raiseOnSlab();
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            h.assertFalse(f.mob.getSensing().hasLineOfSight(f.player), "The real upper half of the slab blocks the overhead player");
            h.assertTrue(f.goal.canUse(), "A partial ceiling supporting the player is found from its actual collision shape");
            h.assertValueEqual(f.goal.targetBlock(), f.ceiling, "The upper-half slab is the actual selected support");
            f.goal.start();
            f.tick(40 + 10 * f.goal.stageDelay(f.ceiling));
            h.assertTrue(h.getLevel().isEmptyBlock(f.ceiling), "The partial overhead slab completes normal staged mining");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void zombieStandingOnASlabUsesItsRaisedBodyAndEyePosition(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3, false, Blocks.DIRT.defaultBlockState())) {
            f.raiseOnSlab();
            h.assertTrue(f.mob.getBoundingBox().minY == f.origin.getY() + .5D
                    && f.mob.getBoundingBox().maxY < f.ceiling.getY(), "A real bottom slab supports the raised body with clear space below the ceiling");
            h.assertTrue(f.goal.canUse(), "The actual raised head and eye position find the reachable roof");
            h.assertValueEqual(f.goal.targetBlock(), f.ceiling, "The raised monster still targets the real overhead player support");
            f.goal.start();
            f.tick(40 + 10 * f.goal.stageDelay(f.ceiling));
            h.assertTrue(h.getLevel().isEmptyBlock(f.ceiling), "The raised monster completes the ordinary staged overhead break");
            h.assertTrue(h.getLevel().getBlockState(f.origin).is(Blocks.STONE_SLAB), "The supporting floor slab remains intact");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void overheadStoneNeedsAnEffectivePickaxeAndBedrockRemainsImmutable(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2, false, Blocks.STONE.defaultBlockState())) {
            for (ItemStack held : List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_SWORD))) {
                f.mob.setItemSlot(EquipmentSlot.MAINHAND, held);
                h.assertFalse(f.goal.canDestroy(f.ceiling, true), "An empty hand, wrong axe or sword cannot bypass stone tool requirements");
                h.assertFalse(f.goal.canUse(), "The overhead exception cannot start an impossible stone break");
            }
            f.mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            h.assertTrue(f.goal.canDestroy(f.ceiling, true), "The source-effective iron pickaxe unlocks overhead stone");
            h.assertTrue(f.goal.canUse(), "The real goal starts stone mining with the correct tool");
            f.goal.start();
            f.tick(40 + 10 * f.goal.stageDelay(f.ceiling));
            h.assertTrue(h.getLevel().isEmptyBlock(f.ceiling), "A correctly equipped monster eventually breaks its actual stone ceiling");
            h.assertTrue(f.mob.getMainHandItem().getDamageValue() > 0, "The overhead stone break uses ordinary tool durability");
            f.goal.stop();
            f.put(f.ceiling, Blocks.BEDROCK.defaultBlockState());
            h.assertFalse(f.goal.canDestroy(f.ceiling, true), "A correct pickaxe never unlocks bedrock overhead");
            h.assertFalse(f.goal.canUse(), "An immutable ceiling cannot become an overhead target");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void overheadExceptionDoesNotPermitOffBodyOrDistantCeilings(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2, false, Blocks.DIRT.defaultBlockState())) {
            BlockPos adjacent = f.ceiling.east();
            f.put(adjacent, Blocks.DIRT.defaultBlockState());
            h.assertFalse(f.goal.canDestroy(adjacent, true), "An adjacent roof block outside the body footprint is not an overhead exception");
            h.assertTrue(f.goal.canUse(), "The actual body-blocking ceiling is still selected");
            h.assertValueEqual(f.goal.targetBlock(), f.ceiling, "The exception chooses the real ceiling under the player");
            f.goal.stop();
            f.player.moveTo(f.origin.getX() + 4.5D, f.origin.getY(), f.origin.getZ() + .5D, 0F, 0F);
            h.assertFalse(f.goal.canDestroy(f.ceiling, true), "A same-level player cannot grant arbitrary upward digging");
            h.assertFalse(f.goal.canUse(), "A monster cannot keep mining a former perch after the player leaves");
        }
        try (Fixture f = new Fixture(h, 4, false, Blocks.DIRT.defaultBlockState())) {
            h.assertFalse(f.goal.canDestroy(f.ceiling, true), "A ceiling beyond normal physical eye reach stays forbidden");
            h.assertFalse(f.goal.canUse(), "A player too high above cannot make the monster dig out of reach");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void movingPlayerImmediatelyStopsTheOverheadTask(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2, false, Blocks.DIRT.defaultBlockState())) {
            h.assertTrue(f.goal.canUse(), "The player starts directly above the roof");
            f.goal.start();
            f.tick(40);
            h.assertValueEqual(f.goal.progress(), 0, "The task has begun actual crack progress");
            f.player.moveTo(f.origin.getX() + 4.5D, f.origin.getY() + 3D, f.origin.getZ() + .5D, 0F, 0F);
            h.assertFalse(f.goal.canContinueToUse(), "Moving away invalidates the overhead target before another action");
            f.tick(1);
            h.assertTrue(h.getLevel().getBlockState(f.ceiling).is(Blocks.DIRT), "A stale overhead target cannot be destroyed after the player moves away");
            h.assertFalse(f.mob.getPersistentData().contains("mite_digging_031"), "Stopping the stale task clears saved overhead progress");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void missingOrNonplayerTargetsCannotGrantTheOverheadException(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2, false, Blocks.DIRT.defaultBlockState())) {
            f.mob.setTarget(null);
            h.assertFalse(f.goal.canDestroy(f.ceiling, true), "No current player target cannot grant upward reach");
            h.assertFalse(f.goal.canUse(), "No current target cannot start overhead digging");
            var cow = EntityType.COW.create(h.getLevel());
            try {
                cow.moveTo(f.player.position());
                f.mob.setTarget(cow);
                h.assertFalse(f.goal.canDestroy(f.ceiling, true), "A nonplayer animal above the monster does not grant the player-perch exception");
                h.assertFalse(f.goal.canUse(), "An overhead animal cannot activate the player-perch behavior");
            } finally {
                cow.discard();
            }
            f.mob.setTarget(f.player);
            f.player.discard();
            h.assertFalse(f.goal.canDestroy(f.ceiling, true), "A removed player target cannot retain upward reach");
            h.assertFalse(f.goal.canUse(), "A removed player cannot start an overhead task");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void griefRuleAndCancelledDestroyEventProtectTheOverheadSupport(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2, false, Blocks.DIRT.defaultBlockState())) {
            var rule = h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
            boolean old = rule.get();
            try {
                rule.set(false, h.getLevel().getServer());
                h.assertFalse(f.goal.canDestroy(f.ceiling, true), "Mob griefing protects actual overhead supports");
                h.assertFalse(f.goal.canUse(), "Disabled mob griefing prevents the overhead task from starting");
            } finally {
                rule.set(old, h.getLevel().getServer());
            }
            int[] callbacks = {0};
            Consumer<LivingDestroyBlockEvent> cancel = event -> {
                if (event.getEntity() == f.mob && event.getPos().equals(f.ceiling)) {
                    callbacks[0]++;
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(LivingDestroyBlockEvent.class, cancel);
            try {
                h.assertTrue(f.goal.canUse(), "The restored grief rule permits normal staged overhead mining");
                f.goal.start();
                f.tick(40 + 10 * f.goal.stageDelay(f.ceiling));
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            h.assertValueEqual(callbacks[0], 1, "The actual final overhead action dispatches the ordinary cancellable destroy event");
            h.assertTrue(h.getLevel().getBlockState(f.ceiling).is(Blocks.DIRT), "A cancelled final action preserves the player's support");
            h.assertFalse(f.goal.canUse(), "A rejected overhead block is not retried immediately");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=300)
    public static void activeWatcherRetainsExclusiveOwnershipOfAnOverheadBlock(GameTestHelper h) {
        Fixture f = new Fixture(h, 2, false, Blocks.DIRT.defaultBlockState());
        f.requestTickingChunks();
        Zombie peer = f.zombie(false);
        R196DiggerGoal other = goal(peer);
        Runnable cleanup = () -> { other.stop(); peer.discard(); f.close(); };
        h.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
        });
        h.startSequence().thenWaitUntil(() -> {
            if (f.chunks.stream().anyMatch(chunk -> !h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())))
                for (int tasks = 0; tasks < 64 && h.getLevel().getChunkSource().pollTask(); tasks++) {}
            h.assertTrue(f.chunks.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                    "The real peer fixture reaches entity-ticking status before spatial ownership queries");
            h.assertTrue(h.getLevel().getEntity(f.mob.getUUID()) == f.mob && h.getLevel().getEntity(peer.getUUID()) == peer,
                    "Both actual monsters are indexed before testing ownership");
            var visible = h.getLevel().getEntitiesOfClass(Zombie.class, f.mob.getBoundingBox().inflate(4D));
            h.assertTrue(visible.contains(f.mob) && visible.contains(peer), "The native peer query can see both actual watchers");
        }).thenExecute(() -> {
            try {
                peer.setTarget(f.player);
                h.assertTrue(f.goal.canUse(), "The first actual watcher claims the ceiling");
                f.goal.start();
                h.assertFalse(other.canDestroy(f.ceiling, true), "A second watcher cannot mine another active watcher's overhead target");
                h.assertFalse(other.canUse(), "The overhead exception does not bypass peer ownership");
                f.goal.stop();
                h.assertTrue(other.canDestroy(f.ceiling, true), "The second watcher can take the unclaimed roof after the first releases it");
            } finally {
                cleanup.run();
            }
        }).thenSucceed();
    }

    private static R196DiggerGoal goal(Zombie mob) {
        R196DiggerGoal result = R196DiggerGoal.first(mob);
        if (result == null) {
            result = new R196DiggerGoal(mob);
            mob.goalSelector.addGoal(1, result);
        }
        return result;
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos origin;
        final BlockPos ceiling;
        final Zombie mob;
        final Player player;
        final R196DiggerGoal goal;
        final long previousTime;
        final List<ChunkPos> chunks = new ArrayList<>();
        boolean closed;

        Fixture(GameTestHelper h, int roofHeight, boolean small, BlockState roof) {
            helper = h;
            previousTime = h.getLevel().getDayTime();
            h.getLevel().setDayTime(18000L);
            origin = new BlockPos(460_000 + IDS.getAndIncrement() * 32, h.absolutePos(BlockPos.ZERO).getY() + 8, 460_000);
            ceiling = origin.above(roofHeight);
            // Real bedrock support, an enclosed lower chamber and a solid player platform exclude walking or attack routes.
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = -1; y <= roofHeight + 3; y++) {
                boolean solid = y == -1 || y == roofHeight || (Math.abs(x) == 3 || Math.abs(z) == 3) && y < roofHeight;
                put(origin.offset(x, y, z), (solid ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState());
            }
            put(ceiling, roof);
            mob = zombie(small);
            player = h.makeMockPlayer(GameType.SURVIVAL);
            player.moveTo(origin.getX() + .5D, ceiling.getY() + 1D, origin.getZ() + .5D, 0F, 0F);
            mob.setTarget(player);
            goal = goal(mob);
        }

        Zombie zombie(boolean small) {
            Zombie result = small ? (Zombie)MiteMobs.type(MobKind.REVENANT).create(helper.getLevel())
                    : EntityType.ZOMBIE.create(helper.getLevel());
            result.moveTo(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D, 0F, 0F);
            result.setNoAi(true);
            result.setNoGravity(true);
            result.setOnGround(true);
            result.setPersistenceRequired();
            result.setCanPickUpLoot(false);
            helper.getLevel().addFreshEntity(result);
            // Source zombies remain adults; scale exercises short-body geometry without changing their age policy.
            if (small) {
                result.getAttribute(Attributes.SCALE).setBaseValue(.5D);
                result.refreshDimensions();
                result.moveTo(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D, 0F, 0F);
                result.setOnGround(true);
            }
            result.getPersistentData().putBoolean(R196MonsterAi.SMART, false);
            result.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            return result;
        }

        void put(BlockPos pos, BlockState state) {
            helper.getLevel().setBlock(pos, state, 2);
        }

        void raiseOnSlab() {
            put(origin, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            mob.moveTo(origin.getX() + .5D, origin.getY() + .5D, origin.getZ() + .5D, 0F, 0F);
            mob.setOnGround(true);
        }

        void requestTickingChunks() {
            ChunkPos low = new ChunkPos(origin.offset(-4, 0, -4)), high = new ChunkPos(origin.offset(4, 0, 4));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                chunks.add(chunk);
                helper.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, mob.getId());
                for (int cx = x - 2; cx <= x + 2; cx++) for (int cz = z - 2; cz <= z + 2; cz++)
                    helper.getLevel().getChunk(cx, cz, ChunkStatus.FULL, true);
            }
        }

        void tick(int count) {
            for (int i = 0; i < count; i++) goal.tick();
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            goal.stop();
            mob.discard();
            player.discard();
            for (ChunkPos chunk : chunks) helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, mob.getId());
            helper.getLevel().setDayTime(previousTime);
        }
    }
}
