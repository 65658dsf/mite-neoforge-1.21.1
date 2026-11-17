package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.progression.PlayerPlacedBlocks;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureExplorationData;
import io.github.ningmeng123.mite.worldgen.StructureExplorationProgress;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native exploration statistics, transaction provenance and persistent shared maxima. @author NingMeng123 */
@GameTestHolder("mite_structure_exploration")
@PrefixGameTestTemplate(false)
public final class StructureExplorationGameTests {
    private StructureExplorationGameTests() {}

    @GameTest(template = "empty")
    public static void playerDistancesPersistButDifferentPlayersNeverPoolTheirTravel(GameTestHelper h) {
        var data = new StructureExplorationData();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        data.add(first, Metric.OCEAN_SAIL_CM, 30_000L);
        Map<Metric, Long> oldSnapshot = data.snapshot();
        data.add(second, Metric.OCEAN_SAIL_CM, 25_000L);
        h.assertValueEqual(data.snapshot().get(Metric.OCEAN_SAIL_CM), 30_000L,
                "Two sailors below five hundred blocks cannot pool fifty-five thousand centimeters");
        data.add(first, Metric.OCEAN_SAIL_CM, 20_001L);
        h.assertValueEqual(data.snapshot().get(Metric.OCEAN_SAIL_CM), 50_001L,
                "One player completing strictly over five hundred blocks unlocks the shared milestone");
        h.assertValueEqual(oldSnapshot.get(Metric.OCEAN_SAIL_CM), 30_000L, "Published generation snapshots never mutate");
        var saved = data.save(new CompoundTag(), h.getLevel().registryAccess());
        var restored = StructureExplorationData.load(saved, h.getLevel().registryAccess());
        h.assertValueEqual(restored.value(first, Metric.OCEAN_SAIL_CM), 50_001L, "First player's total survives NBT");
        h.assertValueEqual(restored.value(second, Metric.OCEAN_SAIL_CM), 25_000L, "Second player's total remains independent");
        restored.add(second, Metric.OCEAN_SAIL_CM, 10_000L);
        h.assertValueEqual(restored.snapshot().get(Metric.OCEAN_SAIL_CM), 50_001L, "Reloading never changes max into sum");
        restored.restore(new CompoundTag());
        h.assertTrue(restored.snapshot().isEmpty() && restored.value(first, Metric.OCEAN_SAIL_CM) == 0L,
                "Explicit restoration replaces every player and the shared view");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void savedCountersRejectNegativeValuesClampOverflowAndMergeDuplicatePlayersByMaximum(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        ListTag entries = new ListTag();
        for (int fish : new int[]{2, 4}) {
            CompoundTag entry = new CompoundTag(), totals = new CompoundTag();
            entry.putUUID("player", id);
            totals.putLong(Metric.OCEAN_SAIL_CM.key(), Long.MAX_VALUE);
            totals.putLong(Metric.DESERT_WALK_CM.key(), -20L);
            totals.putLong(Metric.FISH_CAUGHT.key(), fish);
            entry.put("totals", totals); entries.add(entry);
        }
        CompoundTag saved = new CompoundTag(); saved.put("players", entries);
        var data = StructureExplorationData.load(saved, h.getLevel().registryAccess());
        h.assertValueEqual(data.value(id, Metric.OCEAN_SAIL_CM), Metric.OCEAN_SAIL_CM.cap(), "Saved overflows are capped");
        h.assertValueEqual(data.value(id, Metric.DESERT_WALK_CM), 0L, "Negative persisted movement is rejected");
        h.assertValueEqual(data.value(id, Metric.FISH_CAUGHT), 4L, "Duplicate persisted player records merge by maximum");
        data.add(id, Metric.OCEAN_SAIL_CM, Long.MAX_VALUE);
        data.add(id, Metric.FISH_CAUGHT, -100L);
        data.observe(id, Metric.FISH_CAUGHT, 1L);
        h.assertValueEqual(data.value(id, Metric.OCEAN_SAIL_CM), Metric.OCEAN_SAIL_CM.cap(), "Adding huge deltas never wraps");
        h.assertValueEqual(data.value(id, Metric.FISH_CAUGHT), 4L, "Ordinary observations cannot roll progress back");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void continuousTravelKeepsSubCentimeterRemaindersAndRejectsFlightTeleportAndModeChanges(GameTestHelper h) {
        try (Scope scope = new Scope(h); PlayerFixture f = new PlayerFixture(h, scope)) {
            ServerPlayer player = f.player;
            Vec3 start = new Vec3(scope.pos.getX() + .5D, -40D, scope.pos.getZ() + .5D);
            player.setPos(start); player.setOnGround(true); player.setShiftKeyDown(true);
            StructureExplorationProgress.sample(player);
            for (int i = 1; i <= 4; i++) { player.setPos(start.add(i * .004D, 0D, 0D)); StructureExplorationProgress.sample(player); }
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.UNDERGROUND_WALK_CM), 1L,
                    "Four movements below a centimeter retain their fractional remainder");
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.DEEP_SNEAK_CM), 1L, "Deep sneaking uses the real player flag");
            player.setShiftKeyDown(false); StructureExplorationProgress.sample(player);
            player.setPos(player.position().add(1D, 0D, 0D)); StructureExplorationProgress.sample(player);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.DEEP_SNEAK_CM), 1L, "Ordinary deep walking does not count as sneaking");
            long before = scope.data.value(player.getUUID(), Metric.UNDERGROUND_WALK_CM);
            player.setPos(player.position().add(9D, 0D, 0D)); StructureExplorationProgress.sample(player);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.UNDERGROUND_WALK_CM), before, "A nine-block teleport is not exploration");
            player.getAbilities().flying = true; StructureExplorationProgress.sample(player);
            player.setPos(player.position().add(1D, 0D, 0D)); StructureExplorationProgress.sample(player);
            player.getAbilities().flying = false; StructureExplorationProgress.sample(player);
            player.setGameMode(GameType.CREATIVE); StructureExplorationProgress.sample(player);
            player.setPos(player.position().add(3D, 0D, 0D)); StructureExplorationProgress.sample(player);
            player.setGameMode(GameType.SURVIVAL); StructureExplorationProgress.sample(player);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.UNDERGROUND_WALK_CM), before,
                    "Flight and creative travel cannot become survival credit on the next sample");
            player.setPos(player.position().add(4D, 0D, 0D));
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player,
                    net.minecraft.world.level.Level.NETHER, net.minecraft.world.level.Level.OVERWORLD));
            StructureExplorationProgress.sample(player);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.UNDERGROUND_WALK_CM), before,
                    "A dimension event discards the previous position before a new baseline");
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityTeleportEvent.TeleportCommand(player,
                    player.getX() + 2D, player.getY(), player.getZ()));
            player.setPos(player.position().add(2D, 0D, 0D)); StructureExplorationProgress.sample(player);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.UNDERGROUND_WALK_CM), before,
                    "Even a short teleport command resets the baseline instead of counting as walking");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void groundedNetherSprintPacketsCountOnlyRestoredServerPositions(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "The actual Nether dimension is loaded");
        try (Scope scope = new Scope(h, nether, 72); PlayerFixture f = new PlayerFixture(h, scope)) {
            scope.fillBiome.accept(Biomes.SOUL_SAND_VALLEY);
            ServerPlayer player = f.player;
            Map<BlockPos, BlockState> oldBlocks = new HashMap<>();
            boolean[] sampledAirborne = {false};
            Consumer<PlayerTickEvent.Post> observePhysics = event -> {
                if (event.getEntity() == player && !player.onGround()) sampledAirborne[0] = true;
            };
            try {
                for (int x = -1; x <= 4; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 2; y++) {
                    BlockPos pos = scope.pos.offset(x, y, z);
                    oldBlocks.put(pos, scope.level.getBlockState(pos));
                    scope.level.setBlock(pos, y == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
                Vec3 start = Vec3.atBottomCenterOf(scope.pos);
                player.teleportTo(scope.level, start.x, start.y, start.z, 0F, 0F);
                f.acknowledgeTeleport(h);
                player.connection.resetPosition();
                StructureExplorationProgress.reset(player);
                player.setOnGround(true);
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.tick();
                NeoForge.EVENT_BUS.addListener(observePhysics);
                for (int step = 1; step <= 4; step++) {
                    double x = start.x + step * .25D;
                    player.setSprinting(true);
                    player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(x, start.y, start.z, true));
                    h.assertTrue(player.onGround() && player.isSprinting() && Math.abs(player.getX() - x) < 1E-9D,
                            "The native listener accepts each flat grounded sprint movement packet");
                    player.setDeltaMovement(.05D, 0D, 0D);
                    player.connection.tick();
                    h.assertTrue(Math.abs(player.getX() - x) < 1E-9D,
                            "The native connection restores the accepted client position after player physics");
                    h.assertValueEqual(scope.data.value(player.getUUID(), Metric.NETHER_WALK_CM), step * 25L,
                            "Final server positions credit the exact grounded sprint distance");
                }
                h.assertTrue(sampledAirborne[0], "Native player physics can clear the ground flag before the connection restores position");
                player.setDeltaMovement(.05D, 0D, 0D);
                player.connection.tick();
                h.assertValueEqual(scope.data.value(player.getUUID(), Metric.NETHER_WALK_CM), 100L,
                        "Temporary idle-tick physics cannot add exploration distance");
                h.assertValueEqual(scope.data.value(player.getUUID(), Metric.SOUL_VALLEY_WALK_CM), 100L,
                        "Soul valley progress uses the same final accepted sprint distance");
            } finally {
                NeoForge.EVENT_BUS.unregister(observePhysics);
                oldBlocks.forEach((pos, state) -> scope.level.setBlock(pos, state, 2));
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void netherSprintJumpsCountTakeoffAirborneAndLandingHorizontalTravel(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "The actual Nether dimension is loaded");
        try (Scope scope = new Scope(h, nether, 72); PlayerFixture f = new PlayerFixture(h, scope)) {
            scope.fillBiome.accept(Biomes.SOUL_SAND_VALLEY);
            ServerPlayer player = f.player;
            Vec3 start = player.position();
            player.setSprinting(true);
            player.setOnGround(true);
            StructureExplorationProgress.sample(player);
            player.setPos(start.add(1D, 0D, 0D));
            StructureExplorationProgress.sample(player);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.NETHER_WALK_CM), 100L,
                    "Grounded sprinting counts as travel on foot");
            player.setOnGround(false);
            player.setPos(start.add(1.5D, .4D, 0D));
            StructureExplorationProgress.sample(player);
            player.setPos(start.add(2D, .8D, 0D));
            StructureExplorationProgress.sample(player);
            player.setPos(start.add(2.5D, .4D, 0D));
            StructureExplorationProgress.sample(player);
            player.setOnGround(true);
            player.setPos(start.add(3D, 0D, 0D));
            StructureExplorationProgress.sample(player);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.NETHER_WALK_CM), 300L,
                    "A sprint jump includes takeoff, airborne and landing horizontal segments");
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.SOUL_VALLEY_WALK_CM), 300L,
                    "Soul valley exploration includes the same sprint jump");
            player.setOnGround(false);
            player.setPos(start.add(3D, .8D, 0D));
            StructureExplorationProgress.sample(player);
            player.setOnGround(true);
            player.setPos(start.add(3D, 0D, 0D));
            StructureExplorationProgress.sample(player);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.NETHER_WALK_CM), 300L,
                    "A vertical-only jump adds no horizontal distance");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void overworldSprintJumpsCountBiomeTravelButDeepSneakingStillRequiresGround(GameTestHelper h) {
        try (Scope scope = new Scope(h); PlayerFixture f = new PlayerFixture(h, scope)) {
            ServerPlayer player = f.player;
            Vec3 start = new Vec3(scope.pos.getX() + .5D, -41D, scope.pos.getZ() + .5D);
            player.setSprinting(true);
            player.setShiftKeyDown(true);
            for (var entry : Map.of(Biomes.DESERT, Metric.DESERT_WALK_CM, Biomes.JUNGLE, Metric.JUNGLE_WALK_CM,
                    Biomes.SNOWY_PLAINS, Metric.SNOW_WALK_CM, Biomes.DARK_FOREST, Metric.DARK_FOREST_WALK_CM,
                    Biomes.SWAMP, Metric.SWAMP_TRAVEL_CM).entrySet()) {
                scope.fillBiome.accept(entry.getKey());
                player.setPos(start);
                player.setOnGround(true);
                StructureExplorationProgress.reset(player);
                StructureExplorationProgress.sample(player);
                player.setOnGround(false);
                player.setPos(start.add(.5D, .4D, 0D));
                StructureExplorationProgress.sample(player);
                player.setOnGround(true);
                player.setPos(start.add(1D, 0D, 0D));
                StructureExplorationProgress.sample(player);
                h.assertValueEqual(scope.data.value(player.getUUID(), entry.getValue()), 100L,
                        "Each overworld foot-travel biome includes sprint jumps: " + entry.getValue());
            }
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.UNDERGROUND_WALK_CM), 500L,
                    "Underground travel includes all five horizontal jump paths");
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.DEEP_SNEAK_CM), 0L,
                    "Holding sneak during airborne travel cannot satisfy deep grounded sneaking");
            player.setPos(player.position().add(1D, 0D, 0D));
            StructureExplorationProgress.sample(player);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.DEEP_SNEAK_CM), 100L,
                    "A subsequent grounded sneak segment still advances deep sneaking");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingAndNativeStatisticsBackfillOnlyTheirOwnCompletedActions(GameTestHelper h) {
        try (Scope scope = new Scope(h); PlayerFixture f = new PlayerFixture(h, scope)) {
            ServerPlayer player = f.player;
            player.getInventory().setItem(0, new ItemStack(Items.BRUSH));
            StructureExplorationProgress.reconcileStatistics(player, true);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.BRUSH_CRAFTED), 0L, "Possessing a brush is not crafting");
            R196StructureGeneration.crafted(player, new ItemStack(Items.BRUSH));
            R196StructureGeneration.crafted(player, new ItemStack(Items.ENDER_PEARL));
            R196StructureGeneration.crafted(player, new ItemStack(Items.GOLDEN_HORSE_ARMOR));
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.BRUSH_CRAFTED), 1L, "Completed crafting reaches the exploration callback");
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.EYE_CRAFTED), 0L, "A pearl is not a crafted eye");
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.GOLD_ARMOR_CRAFTED), 0L, "Horse armor does not satisfy player armor");
            player.getStats().setValue(player, Stats.ITEM_CRAFTED.get(Items.ENDER_EYE), 1);
            player.getStats().setValue(player, Stats.ITEM_CRAFTED.get(Items.GOLDEN_HELMET), 1);
            player.getStats().setValue(player, Stats.CUSTOM.get(Stats.FISH_CAUGHT), 4);
            player.getStats().setValue(player, Stats.ENTITY_KILLED.get(EntityType.ENDER_DRAGON), 1);
            StructureExplorationProgress.reconcileStatistics(player, true);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.EYE_CRAFTED), 1L, "Authoritative crafted-eye stats migrate old saves");
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.GOLD_ARMOR_CRAFTED), 1L, "Real gold armor stats migrate old saves");
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.FISH_CAUGHT), 4L, "Only the final caught-fish statistic counts");
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.DRAGON_KILLED), 1L, "Native dragon kill credit supplies the shared prerequisite");
            StructureExplorationProgress.reconcileStatistics(player, true);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.FISH_CAUGHT), 4L, "Polling does not repeatedly add the same fish");
            player.getStats().setValue(player, Stats.CUSTOM.get(Stats.FISH_CAUGHT), 5);
            StructureExplorationProgress.reconcileStatistics(player, false);
            h.assertValueEqual(scope.data.value(player.getUUID(), Metric.FISH_CAUGHT), 5L, "The fifth real catch completes the prerequisite");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oceanTravelRequiresAnAfloatBoatOrActuallySubmergedEyes(GameTestHelper h) {
        try (Scope scope = new Scope(h); PlayerFixture f = new PlayerFixture(h, scope)) {
            scope.fillBiome.accept(Biomes.OCEAN);
            Map<BlockPos, BlockState> oldBlocks = new HashMap<>();
            var player = f.player;
            Boat vessel = new Boat(scope.level, scope.pos.getX() + .5D, scope.pos.getY() + .8D, scope.pos.getZ() + .5D);
            try {
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = -3; y <= 3; y++) {
                    BlockPos pos = scope.pos.offset(x, y, z);
                    oldBlocks.put(pos, scope.level.getBlockState(pos));
                    scope.level.setBlock(pos, y <= 0 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
                vessel.setNoGravity(true); scope.level.addFreshEntity(vessel);
                vessel.baseTick();
                h.assertTrue(vessel.isInWater() && !vessel.isUnderWater(), "The actual boat hull floats in the ocean fixture");
                h.assertTrue(player.startRiding(vessel, true), "The real player rides the actual boat");
                player.setPos(vessel.getX(), vessel.getY() + .5D, vessel.getZ());
                StructureExplorationProgress.reset(player); StructureExplorationProgress.sample(player);
                vessel.setPos(vessel.position().add(1D, 0D, 0D)); vessel.baseTick();
                player.setPos(vessel.getX(), vessel.getY() + .5D, vessel.getZ());
                StructureExplorationProgress.sample(player);
                h.assertValueEqual(scope.data.value(player.getUUID(), Metric.OCEAN_SAIL_CM), 100L,
                        "One real horizontal block in an ocean boat contributes one hundred centimeters");
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
                    scope.level.setBlock(scope.pos.offset(x, 0, z), Blocks.ICE.defaultBlockState(), 2);
                vessel.setPos(vessel.getX(), scope.pos.getY() + 1D, vessel.getZ()); vessel.baseTick();
                player.setPos(vessel.getX(), vessel.getY() + .5D, vessel.getZ());
                StructureExplorationProgress.sample(player);
                vessel.setPos(vessel.position().add(.5D, 0D, 0D)); vessel.baseTick();
                player.setPos(vessel.getX(), vessel.getY() + .5D, vessel.getZ()); StructureExplorationProgress.sample(player);
                h.assertValueEqual(scope.data.value(player.getUUID(), Metric.OCEAN_SAIL_CM), 100L,
                        "Ice-road boat travel cannot satisfy ocean sailing");
                player.stopRiding();
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
                    scope.level.setBlock(scope.pos.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 2);
                player.setPos(scope.pos.getX() + .5D, scope.pos.getY() - 1D, scope.pos.getZ() + .5D);
                player.setOnGround(false); player.baseTick();
                h.assertTrue(player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "The player's actual eye position is under water");
                StructureExplorationProgress.reset(player); StructureExplorationProgress.sample(player);
                player.setPos(player.position().add(1D, 0D, 0D)); player.baseTick(); StructureExplorationProgress.sample(player);
                h.assertValueEqual(scope.data.value(player.getUUID(), Metric.OCEAN_DIVE_CM), 100L,
                        "Underwater physical travel counts after the actual eye-fluid update");
                player.setPos(player.getX(), scope.pos.getY() + 1D, player.getZ()); player.baseTick();
                StructureExplorationProgress.sample(player);
                player.setPos(player.position().add(.5D, 0D, 0D)); player.baseTick(); StructureExplorationProgress.sample(player);
                h.assertValueEqual(scope.data.value(player.getUUID(), Metric.OCEAN_DIVE_CM), 100L,
                        "Surface travel with dry eyes cannot become diving progress");
            } finally {
                player.stopRiding(); vessel.discard();
                oldBlocks.forEach((pos, state) -> scope.level.setBlock(pos, state, 2));
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void completedNaturalMiningRejectsPlacedStoneCanceledBreaksAndUnrelatedBlocks(GameTestHelper h) {
        try (Scope scope = new Scope(h); PlayerFixture f = new PlayerFixture(h, scope)) {
            var player = f.player;
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            BlockPos target = scope.pos.offset(1, 0, 0);
            BlockState original = h.getLevel().getBlockState(target);
            try {
                h.getLevel().setBlock(target, Blocks.STONE.defaultBlockState(), 2);
                PlayerPlacedBlocks.set(h.getLevel(), target, false);
                h.assertTrue(player.gameMode.destroyBlock(target), "The native server removal actually succeeds");
                h.assertValueEqual(scope.data.value(player.getUUID(), Metric.NATURAL_STONE_MINED), 1L,
                        "Successful native mining advances the natural-stone count");
                h.getLevel().setBlock(target, Blocks.DEEPSLATE.defaultBlockState(), 2);
                PlayerPlacedBlocks.set(h.getLevel(), target, true);
                h.assertTrue(player.gameMode.destroyBlock(target), "A placed stone can still be mined normally");
                h.assertValueEqual(scope.data.value(player.getUUID(), Metric.NATURAL_STONE_MINED), 1L,
                        "Provenance captured before removal prevents a placed-stone farming loop");
                h.getLevel().setBlock(target, Blocks.STONE.defaultBlockState(), 2);
                java.util.function.Consumer<BlockEvent.BreakEvent> cancel = event -> {
                    if (event.getPlayer() == player && event.getPos().equals(target)) event.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(cancel);
                try { h.assertFalse(player.gameMode.destroyBlock(target), "The real break event was canceled"); }
                finally { NeoForge.EVENT_BUS.unregister(cancel); }
                h.assertValueEqual(scope.data.value(player.getUUID(), Metric.NATURAL_STONE_MINED), 1L,
                        "A canceled native mining transaction never counts");
                StructureExplorationProgress.mined(player, Blocks.COBBLESTONE.defaultBlockState(), false);
                h.assertValueEqual(scope.data.value(player.getUUID(), Metric.NATURAL_STONE_MINED), 1L,
                        "Processed cobblestone is outside the natural-rock roster");
            } finally {
                h.getLevel().setBlock(target, original, 2);
                PlayerPlacedBlocks.set(h.getLevel(), target, false);
            }
        }
        h.succeed();
    }

    private static final class Scope implements AutoCloseable {
        private final ServerLevel level;
        private final StructureExplorationData data;
        private final CompoundTag saved;
        private final boolean dirty;
        private final BlockPos pos;
        private final Runnable restoreBiome;
        private final Consumer<ResourceKey<Biome>> fillBiome;
        private Scope(GameTestHelper h) {
            this(h, h.getLevel(), -40);
        }
        private Scope(GameTestHelper h, ServerLevel level, int height) {
            this.level = level; data = StructureExplorationData.get(level);
            saved = data.save(new CompoundTag(), level.registryAccess()); dirty = data.isDirty();
            data.restore(new CompoundTag());
            BlockPos origin = h.absolutePos(new BlockPos(1, 2, 1));
            pos = new BlockPos((origin.getX() & ~15) + 8, height, (origin.getZ() & ~15) + 8);
            var chunk = level.getChunkAt(pos);
            var section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
            var old = section.getBiomes();
            List<Holder<Biome>> biomes = new ArrayList<>(64);
            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) biomes.add(old.get(x, y, z));
            int qx = QuartPos.fromBlock(chunk.getPos().getMinBlockX()), qy = QuartPos.fromBlock(pos.getY()) & ~3,
                    qz = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            var sampler = level.getChunkSource().randomState().sampler();
            fillBiome = key -> {
                var biome = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
                section.fillBiomesFromNoise((x, y, z, climate) -> biome, sampler, qx, qy, qz);
            };
            fillBiome.accept(Biomes.DESERT);
            restoreBiome = () -> section.fillBiomesFromNoise((x, y, z, climate) ->
                    biomes.get(((x & 3) << 4) | ((y & 3) << 2) | (z & 3)), sampler, qx, qy, qz);
        }
        @Override public void close() {
            restoreBiome.run(); data.restore(saved); data.setDirty(dirty);
        }
    }

    private static final class PlayerFixture implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private PlayerFixture(GameTestHelper h, Scope scope) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "struct-explore"), false);
            player = new ServerPlayer(scope.level.getServer(), scope.level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            if (player.serverLevel() != scope.level)
                player.teleportTo(scope.level, scope.pos.getX() + .5D, scope.pos.getY(), scope.pos.getZ() + .5D, 0F, 0F);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.setOnGround(true);
            player.moveTo(scope.pos.getCenter());
            player.getInventory().clearContent(); player.removeAllEffects();
            PlayerExperience.setTotal(player, 1000);
            player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(6);
            h.assertTrue(player.isAlive() && !player.isCreative() && !player.isSpectator(),
                    "The exploration fixture is a normal living survival player, not the always-creative GameTest mock subclass");
            StructureExplorationProgress.reset(player);
        }
        private void acknowledgeTeleport(GameTestHelper h) {
            channel.runPendingTasks();
            int teleportId = -1;
            for (Object packet : channel.outboundMessages())
                if (packet instanceof ClientboundPlayerPositionPacket position) teleportId = position.getId();
            h.assertTrue(teleportId >= 0, "Native login and positioning emit a teleport confirmation identifier");
            player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleportId));
        }
        @Override public void close() {
            try {
                StructureExplorationProgress.reset(player);
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}
