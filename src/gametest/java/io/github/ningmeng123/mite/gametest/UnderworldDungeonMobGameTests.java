package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.UnderworldDungeonMobRules;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.UnderworldDungeonSpawns;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real dungeon pool upgrades, persistent cage identities and initialized spider riders. @author NingMeng123 */
@GameTestHolder("mite_underworld_dungeon_mobs")
@PrefixGameTestTemplate(false)
public final class UnderworldDungeonMobGameTests {
    @GameTest(template = "empty", templateNamespace = "mite_underworld_dungeon_mobs", timeoutTicks = 2400)
    public static void naturallyGeneratedDungeonsUseTheNewRoster(GameTestHelper helper) {
        UnderworldDungeonGenerationGameTests.naturalUnderworldChunksContainAnOrdinaryDungeonWithSpawnerAndLootChest(helper);
    }

    @GameTest(template = "empty", templateNamespace = "mite_underworld_dungeon_mobs", timeoutTicks = 400)
    public static void explicitOrdinaryCagesStillFinalizeTheirEstablishedReplacement(GameTestHelper helper) {
        UnderworldSpawnerGameTests.ordinarySpawnerActuallyCreatesAndFinalizesTheReplacement(helper);
    }

    @GameTest(template = "empty", templateNamespace = "mite_underworld_dungeon_mobs", timeoutTicks = 400)
    public static void trialCagesRetainBothModesEquipmentRulesAndWaveState(GameTestHelper helper) {
        UnderworldSpawnerGameTests.trialDataKeepsBothModesEquipmentRulesRewardsAndCountersAcrossReload(helper);
    }

    @GameTest(template = "empty", templateNamespace = "mite_underworld_dungeon_mobs", timeoutTicks = 400)
    public static void authoredWeightedCagesStillPreserveTheirNamesRulesAndWeights(GameTestHelper helper) {
        UnderworldSpawnerGameTests.ordinarySpawnerRewritesBothWeightedAndCurrentDataWithoutLosingAuthoredRules(helper);
    }

    @GameTest(template = "empty", templateNamespace = "mite_underworld_dungeon_mobs", timeoutTicks = 400)
    public static void legacyDungeonsCoverTheRosterAndKeepTheirChoiceAcrossReload(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        Set<String> expected = new HashSet<>();
        UnderworldDungeonMobRules.mobs().forEach(mob -> expected.add(role(mob.entityId(), mob.spiderJockey())));
        Set<String> observed = new HashSet<>();
        long randomState = randomState(level);
        String[] legacyIds = {"minecraft:skeleton", "minecraft:zombie", "minecraft:spider",
                "mite:longdead", "mite:revenant", "mite:demon_spider"};
        for (int index = 0; index < 256; index++) {
            BlockPos pos = site(index);
            CompoundTag input = baseTag(legacyIds[index % legacyIds.length]);
            // Vanilla saves both a current candidate and an identical weight-one candidate.
            if ((index & 1) == 0) input.put("SpawnPotentials", singlePotential(legacyIds[index % legacyIds.length], 1));
            BaseSpawner spawner = base();
            spawner.load(level, pos, input);
            CompoundTag saved = spawner.save(new CompoundTag());
            SpawnData selected = spawnData(saved);
            helper.assertTrue(saved.getBoolean(UnderworldDungeonSpawns.CONFIGURED_TAG), "Legacy dungeon gains its persisted migration marker");
            observed.add(role(selected.getEntityToSpawn().getString("id"), UnderworldDungeonSpawns.isJockey(selected)));
            var pool = saved.getList("SpawnPotentials", 10);
            helper.assertValueEqual(pool.size(), 1, "A dungeon retains one fixed mob role");
            helper.assertTrue(pool.getCompound(0).getCompound("data").equals(saved.getCompound("SpawnData")),
                    "Delay reset uses the same mob role as the current cycle");
            BaseSpawner reloaded = base();
            reloaded.load(level, pos.offset(4096, 0, -4096), saved);
            helper.assertTrue(saved.equals(reloaded.save(new CompoundTag())), "Reloading even at another position cannot reroll an already configured cage");
        }
        helper.assertTrue(observed.equals(expected), "Legacy skeleton, zombie and spider cages collectively reach every advertised dungeon role: " + observed);
        helper.assertTrue(randomState(level) == randomState, "Loading and saving dungeon cages does not advance the world's shared random source");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_underworld_dungeon_mobs", timeoutTicks = 400)
    public static void overworldAndAuthoredUnderworldCagesKeepTheirConfiguration(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        BlockPos overworldPos = helper.absolutePos(new BlockPos(1, 1, 1));
        BaseSpawner ordinary = base();
        ordinary.load(overworld, overworldPos, baseTag("minecraft:skeleton"));
        CompoundTag saved = ordinary.save(new CompoundTag());
        helper.assertValueEqual(id(saved.getCompound("SpawnData")), "minecraft:skeleton", "Overworld dungeon identity stays vanilla");
        helper.assertTrue(!saved.getBoolean(UnderworldDungeonSpawns.CONFIGURED_TAG), "Overworld cage never enters the underground roster");

        ServerLevel level = underworld(helper);
        CompoundTag named = baseTag("minecraft:skeleton");
        named.getCompound("SpawnData").getCompound("entity").putString("CustomName", "\"Authored cage\"");
        BaseSpawner custom = base();
        custom.load(level, site(300), named);
        saved = custom.save(new CompoundTag());
        helper.assertTrue(!saved.getBoolean(UnderworldDungeonSpawns.CONFIGURED_TAG), "Custom entity NBT is not mistaken for a legacy default");
        helper.assertValueEqual(id(saved.getCompound("SpawnData")), "mite:longdead", "Authored skeleton retains its established underworld adaptation");
        helper.assertValueEqual(saved.getCompound("SpawnData").getCompound("entity").getString("CustomName"), "\"Authored cage\"", "Authored name survives");

        for (String authoredField : new String[]{"custom_spawn_rules", "equipment"}) {
            CompoundTag configured = baseTag("minecraft:skeleton");
            CompoundTag configuration = new CompoundTag();
            if (authoredField.equals("custom_spawn_rules")) {
                CompoundTag light = new CompoundTag();
                light.putInt("min_inclusive", 0);
                light.putInt("max_inclusive", 14);
                configuration.put("block_light_limit", light);
            } else {
                configuration.putString("loot_table", "minecraft:equipment/trial_chamber");
                configuration.putFloat("slot_drop_chances", .25F);
            }
            configured.getCompound("SpawnData").put(authoredField, configuration);
            BaseSpawner authoredConfiguration = base();
            authoredConfiguration.load(level, site(302), configured);
            CompoundTag actualConfiguration = authoredConfiguration.save(new CompoundTag());
            helper.assertTrue(!actualConfiguration.getBoolean(UnderworldDungeonSpawns.CONFIGURED_TAG),
                    "Explicit " + authoredField + " prevents legacy upgrade");
            helper.assertTrue(actualConfiguration.getCompound("SpawnData").contains(authoredField),
                    "Explicit " + authoredField + " survives the established mob adaptation");
        }

        CompoundTag weighted = baseTag("minecraft:zombie");
        ListTag pool = singlePotential("minecraft:zombie", 7);
        pool.add(weighted("minecraft:skeleton", 3));
        weighted.put("SpawnPotentials", pool);
        BaseSpawner authored = base();
        authored.load(level, site(301), weighted);
        saved = authored.save(new CompoundTag());
        helper.assertTrue(!saved.getBoolean(UnderworldDungeonSpawns.CONFIGURED_TAG), "An authored weighted pool is never replaced by the dungeon roster");
        var actual = saved.getList("SpawnPotentials", 10);
        helper.assertValueEqual(actual.size(), 2, "Both configured choices remain");
        helper.assertValueEqual(actual.getCompound(0).getInt("weight"), 7, "First relative weight survives");
        helper.assertValueEqual(actual.getCompound(1).getInt("weight"), 3, "Second relative weight survives");
        helper.assertValueEqual(id(actual.getCompound(0).getCompound("data")), "mite:revenant", "First authored role retains its old adaptation");
        helper.assertValueEqual(id(actual.getCompound(1).getCompound("data")), "mite:longdead", "Second authored role retains its old adaptation");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_underworld_dungeon_mobs", timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void spiderKnightCycleAddsOneVehicleAndOneFullyEquippedSpawnerRider(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        BlockPos pos = jockeyPosition(level, 400);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try (Room room = new Room(level, pos); SpawnedEntityCapture capture = new SpawnedEntityCapture(level, e -> e instanceof Mob && e.blockPosition().distSqr(pos) < 100)) {
            player.teleportTo(level, pos.getX() + 3, pos.getY(), pos.getZ(), 0, 0);
            for (int attempt = 0; attempt < 20 && capture.live(Mob.class).isEmpty(); attempt++) {
                BaseSpawner spawner = base();
                spawner.load(level, pos, baseTag("minecraft:skeleton"));
                helper.assertTrue(UnderworldDungeonSpawns.isJockey(spawnData(spawner.save(new CompoundTag()))), "Fixture selected a real spider-knight dungeon");
                spawner.serverTick(level, pos);
            }
            var spawned = capture.live(Mob.class);
            helper.assertValueEqual(spawned.size(), 2, "Exactly one spider and its actual rider join the level, without an orphaned preview rider");
            Mob spider = spawned.stream().filter(mob -> mob.getType() == MiteMobs.type(MobKind.DEMON_SPIDER)).findFirst().orElseThrow();
            Mob rider = spawned.stream().filter(mob -> mob.getType() == MiteMobs.type(MobKind.LONGDEAD)).findFirst().orElseThrow();
            helper.assertTrue(spider.getSpawnType() == MobSpawnType.SPAWNER && rider.getSpawnType() == MobSpawnType.SPAWNER,
                    "Both mount and rider receive genuine SPAWNER finalization");
            helper.assertTrue(spider.getPassengers().size() == 1 && spider.getFirstPassenger() == rider && rider.getVehicle() == spider,
                    "Both joined entities form the same mounted spider knight");
            helper.assertTrue(rider.getMainHandItem().getItem() instanceof BowItem, "The longdead rider receives a usable generated bow");
            helper.assertTrue(!rider.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Normal rider armor initialization also ran");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "mite_underworld_dungeon_mobs", timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void spawnEggOverrideRemovesSpiderKnightPassengersAndSurvivesReload(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        BlockPos pos = jockeyPosition(level, 600);
        BaseSpawner spawner = base();
        spawner.load(level, pos, baseTag("minecraft:skeleton"));
        helper.assertTrue(UnderworldDungeonSpawns.isJockey(spawnData(spawner.save(new CompoundTag()))), "Fixture starts with a spider-knight cage");
        spawner.setEntityId(MiteMobs.type(MobKind.REVENANT), level, RandomSource.create(196L), pos);
        CompoundTag saved = spawner.save(new CompoundTag());
        CompoundTag entity = saved.getCompound("SpawnData").getCompound("entity");
        helper.assertValueEqual(entity.getString("id"), "mite:revenant", "Spawn egg chooses the requested entity");
        helper.assertTrue(!entity.contains(UnderworldDungeonSpawns.JOCKEY_TAG) && !entity.contains("Passengers"),
                "A spawn egg removes both the private knight flag and its saved preview passenger");
        var pool = saved.getList("SpawnPotentials", 10);
        helper.assertTrue(pool.size() <= 1, "The following cycle cannot retain an authored pool of stale spider knights");
        if (!pool.isEmpty()) helper.assertTrue(pool.getCompound(0).getCompound("data").equals(saved.getCompound("SpawnData")),
                "A retained future candidate agrees with the egg-selected role");
        BaseSpawner reloaded = base();
        reloaded.load(level, pos, saved);
        helper.assertTrue(saved.equals(reloaded.save(new CompoundTag())), "Reload never replaces the explicit spawn-egg selection");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try (Room room = new Room(level, pos); SpawnedEntityCapture capture = new SpawnedEntityCapture(level, e -> e instanceof Mob && e.blockPosition().distSqr(pos) < 100)) {
            player.teleportTo(level, pos.getX() + 3, pos.getY(), pos.getZ(), 0, 0);
            for (int attempt = 0; attempt < 20 && capture.live(Mob.class).isEmpty(); attempt++) {
                BaseSpawner restored = base();
                restored.load(level, pos, saved);
                restored.serverTick(level, pos);
            }
            var spawned = capture.live(Mob.class);
            helper.assertValueEqual(spawned.size(), 1, "Only the egg-selected creature is added, with no residual rider");
            helper.assertTrue(spawned.getFirst().getType() == MiteMobs.type(MobKind.REVENANT) && spawned.getFirst().getPassengers().isEmpty(),
                    "The actual next cage cycle respects the spawn egg and has no passengers");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static BaseSpawner base() {
        return new BaseSpawner() { @Override public void broadcastEvent(Level level, BlockPos pos, int event) {} };
    }
    private static CompoundTag data(String id) {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", id);
        return (CompoundTag) SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, new SpawnData(entity, Optional.empty(), Optional.empty())).getOrThrow();
    }
    private static CompoundTag weighted(String id, int weight) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("weight", weight);
        entry.put("data", data(id));
        return entry;
    }
    private static ListTag singlePotential(String id, int weight) {
        ListTag pool = new ListTag();
        pool.add(weighted(id, weight));
        return pool;
    }
    private static CompoundTag baseTag(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putShort("Delay", (short) 0);
        tag.putShort("MinSpawnDelay", (short) 200);
        tag.putShort("MaxSpawnDelay", (short) 800);
        tag.putShort("SpawnCount", (short) 1);
        tag.putShort("SpawnRange", (short) 1);
        tag.put("SpawnData", data(id));
        return tag;
    }
    private static SpawnData spawnData(CompoundTag saved) {
        return SpawnData.CODEC.parse(NbtOps.INSTANCE, saved.getCompound("SpawnData")).getOrThrow();
    }
    private static String id(CompoundTag data) { return data.getCompound("entity").getString("id"); }
    private static String role(String id, boolean jockey) { return id + (jockey ? "/jockey" : "/single"); }
    private static BlockPos site(int index) { return new BlockPos(-18000 - index * 32, 180, -18000); }
    private static BlockPos jockeyPosition(ServerLevel level, int start) {
        for (int index = start; index < start + 256; index++) {
            BlockPos pos = site(index);
            if (UnderworldDungeonSpawns.isJockey(UnderworldDungeonSpawns.select(level, pos))) return pos;
        }
        throw new IllegalStateException("No spider-knight dungeon position found in the fixture search");
    }
    private static ServerLevel underworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "Underworld is loaded");
        return level;
    }
    private static long randomState(ServerLevel level) {
        try {
            Field seed = LegacyRandomSource.class.getDeclaredField("seed");
            seed.setAccessible(true);
            return ((AtomicLong) seed.get(level.getRandom())).get();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect shared server random state", failure);
        }
    }

    private static final class Room implements AutoCloseable {
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        private final Set<Long> addedTickets = new HashSet<>();
        private Room(ServerLevel level, BlockPos center) {
            this.level = level;
            for (int x = Math.floorDiv(center.getX() - 6, 16); x <= Math.floorDiv(center.getX() + 6, 16); x++)
                for (int z = Math.floorDiv(center.getZ() - 6, 16); z <= Math.floorDiv(center.getZ() + 6, 16); z++) {
                    long chunk = ChunkPos.asLong(x, z);
                    if (!level.getForcedChunks().contains(chunk)) {
                        level.setChunkForced(x, z, true);
                        addedTickets.add(chunk);
                    }
                }
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -1, -6), center.offset(6, 5, 6))) {
                previous.put(pos.immutable(), level.getBlockState(pos));
                boolean shell = pos.getY() == center.getY() - 1 || pos.getY() == center.getY() + 5
                        || Math.abs(pos.getX() - center.getX()) == 6 || Math.abs(pos.getZ() - center.getZ()) == 6;
                level.setBlock(pos, (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
            }
        }
        @Override public void close() {
            previous.forEach((pos, state) -> level.setBlock(pos, state, 2));
            for (long ticket : addedTickets) {
                ChunkPos chunk = new ChunkPos(ticket);
                level.setChunkForced(chunk.x, chunk.z, false);
            }
        }
    }
}
