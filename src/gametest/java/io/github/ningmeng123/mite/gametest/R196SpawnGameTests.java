package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196OutdoorChecks;
import io.github.ningmeng123.mite.worldgen.R196SpawnQualification;
import io.github.ningmeng123.mite.worldgen.R196SpawnerAccess;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real source tables, placement, category caps, activation and failed spawner cycles; constants independently extracted from the old jar. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class R196SpawnGameTests {
    private R196SpawnGameTests() {}
    private static BlockPos position(GameTestHelper helper) { return helper.absolutePos(new BlockPos(4, 2, 4)); }
    private static R196MobSpawning.Entry entry(int biome, Category category, String name) {
        return R196MobSpawning.sourceProfile(biome, category).stream().filter(row -> name.equals(row.sourceClass())).findFirst().orElseThrow();
    }
    private static Mob create(GameTestHelper helper, MobKind kind) {
        Mob mob = MiteMobs.type(kind).create(helper.getLevel()); helper.assertTrue(mob != null, "Source entity creates " + kind);
        mob.moveTo(position(helper).getCenter()); mob.setNoAi(true); return mob;
    }
    private static void close(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .00001, message + " actual=" + actual + " expected=" + expected);
    }
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level; final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        Fixture(ServerLevel level) { this.level = level; }
        void set(BlockPos pos, BlockState block) { previous.putIfAbsent(pos.immutable(), level.getBlockState(pos)); level.setBlock(pos, block, 2); }
        void box(BlockPos low, BlockPos high, BlockState state) { for (BlockPos pos : BlockPos.betweenClosed(low, high)) set(pos, state); }
        @Override public void close() { previous.forEach((pos, state) -> level.setBlock(pos, state, 2)); }
    }
    @GameTest(template = "ranged_027")
    public static void allTwentyNineSourceTypesHaveTheirRealCategoryAndIdentity(GameTestHelper helper) {
        helper.assertValueEqual(MobKind.values().length, 29, "No unregistered archival species");
        for (MobKind kind : MobKind.values()) {
            Mob mob = create(helper, kind);
            helper.assertTrue(R196MobSpawning.sourceClass(mob).equals(kind.sourceClass()), "Runtime identity " + kind);
            Category expected = kind == MobKind.DIRE_WOLF ? Category.ANIMAL : kind.family() == MobKind.Family.BAT ? Category.AMBIENT : Category.MONSTER;
            helper.assertTrue(R196MobSpawning.category(mob) == expected, "Source classification " + kind);
        }
        helper.assertTrue(R196MobSpawning.category(EntityType.VILLAGER.create(helper.getLevel())) == null, "Villagers do not consume animal cap");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void twentyFiveLoadedProfilesRetainSourceWeightsGroupsAndIndependentPackLimits(GameTestHelper helper) {
        int total = 0;
        for (int id = 0; id <= 26; id++) {
            if (id == 14 || id == 15) continue; // The two mushroom island instances are actually unregistered in R196.
            for (Category category : Category.values()) total += R196MobSpawning.sourceProfile(id, category).size();
        }
        helper.assertValueEqual(total, 672, "All final initialized biome entries");
        var spider = entry(1, Category.MONSTER, "tt");
        helper.assertValueEqual(spider.weight(), 80, "Plains spider source weight");
        helper.assertValueEqual(spider.minimum(), 1, "Source min group");
        helper.assertValueEqual(spider.maximum(), 2, "Source max group");
        var bat = entry(1, Category.AMBIENT, "ro");
        helper.assertValueEqual(bat.minimum(), 8, "Biome bat group min");
        helper.assertValueEqual(bat.pack(), 4, "Natural pack cap differs from biome group");
        helper.assertTrue(R196MobSpawning.sourceProfile(2, Category.ANIMAL).isEmpty(), "Desert source animal pool is empty");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void dimensionPoolsHaveTheActualUnderworldNetherAndEndSpecies(GameTestHelper helper) {
        helper.assertValueEqual(R196MobSpawning.sourceProfile(26, Category.MONSTER).size(), 20, "Underworld has twenty monster entries");
        helper.assertTrue(entry(26, Category.MONSTER, "te").type() == EntityType.CAVE_SPIDER, "te is cave spider, not giant");
        helper.assertValueEqual(entry(26, Category.MONSTER, "EntityLongdead").weight(), 40, "Underworld longdead source weight");
        helper.assertTrue(R196MobSpawning.sourceProfile(26, Category.MONSTER).stream().noneMatch(row -> row.type() == EntityType.ZOMBIE), "Underworld source removes regular zombies");
        helper.assertValueEqual(R196MobSpawning.sourceProfile(8, Category.MONSTER).size(), 4, "Four Nether base entries");
        helper.assertValueEqual(entry(8, Category.MONSTER, "tj").pack(), 1, "Ghast max natural pack");
        helper.assertValueEqual(entry(9, Category.MONSTER, "EntityEarthElemental").weight(), 20, "End source earth elemental weight");
        for (var key : List.of(Level.OVERWORLD, Level.NETHER, Level.END, MiteWorldDimensions.UNDERWORLD)) {
            var level = helper.getLevel().getServer().getLevel(key);
            helper.assertTrue(level != null && R196MobSpawning.manages(level), "Owned source dimension " + key);
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void ordinarySizedNetherSkeletonQualificationTurnsIntoItsSourceWitherIdentityOnlyAtFinalization(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER); Mob skeleton = EntityType.SKELETON.create(nether);
        helper.assertTrue(skeleton != null, "Native source skeleton creates in Nether"); skeleton.setPos(10.5, 40.005, 10.5);
        close(helper, skeleton.getBbWidth(), .6, "Before source qualification skeleton has ordinary width");
        close(helper, skeleton.getBbHeight(), 1.8, "Before source qualification skeleton has ordinary height");
        Mob finalized = R196MobSpawning.sourceFinalizationIdentity(skeleton, nether);
        helper.assertTrue(finalized.getType() == EntityType.WITHER_SKELETON, "Source Nether tr always becomes type1");
        close(helper, finalized.getY(), skeleton.getY(), "Qualified exact source Y carries across split identity");
        close(helper, finalized.getMaxHealth(), 6, "Nether type1 retains tr source HP");
        Mob overworld = EntityType.SKELETON.create(helper.getLevel());
        helper.assertTrue(R196MobSpawning.sourceFinalizationIdentity(overworld, helper.getLevel()) == overworld, "Other dimensions keep source ordinary identity");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void modernBiomeFamiliesBridgeTheOldTwoDimensionalPools(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        helper.assertValueEqual(R196MobSpawning.biomeId(registry.getHolderOrThrow(Biomes.DESERT), helper.getLevel()), 2, "Modern desert bridge");
        helper.assertValueEqual(R196MobSpawning.biomeId(registry.getHolderOrThrow(Biomes.SNOWY_PLAINS), helper.getLevel()), 12, "Frozen plains bridge");
        helper.assertValueEqual(R196MobSpawning.biomeId(registry.getHolderOrThrow(Biomes.DARK_FOREST), helper.getLevel()), 4, "Modern forest bridge");
        helper.assertValueEqual(R196MobSpawning.biomeId(registry.getHolderOrThrow(Biomes.SWAMP), helper.getLevel()), 6, "Swamp source profile");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceGroundRejectsMantleBedrockMyceliumAndFences(GameTestHelper helper) {
        BlockPos pos = position(helper);
        try (Fixture blocks = new Fixture(helper.getLevel())) {
            for (var block : List.of(Blocks.BEDROCK, MiteWorldBlocks.MANTLE.get(), Blocks.MYCELIUM, Blocks.OAK_FENCE)) {
                blocks.set(pos, block.defaultBlockState());
                helper.assertTrue(!R196MobSpawning.groundAllows(helper.getLevel(), pos, Category.MONSTER), "Source hostile ground rejects " + block);
                helper.assertTrue(!R196MobSpawning.groundAllows(helper.getLevel(), pos, Category.ANIMAL), "Source animal ground rejects " + block);
            }
            blocks.set(pos, Blocks.GRASS_BLOCK.defaultBlockState());
            helper.assertTrue(R196MobSpawning.groundAllows(helper.getLevel(), pos, Category.ANIMAL), "Animal grass floor allowed");
            blocks.set(pos, Blocks.STONE.defaultBlockState());
            helper.assertTrue(!R196MobSpawning.groundAllows(helper.getLevel(), pos, Category.ANIMAL), "Animal stone floor rejected");
            helper.assertTrue(R196MobSpawning.groundAllows(helper.getLevel(), pos, Category.MONSTER), "Monster stone floor allowed");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void slabStairSnowAndLilyPadPositionsUsePhysicalSourceTop(GameTestHelper helper) {
        BlockPos pos = position(helper);
        try (Fixture blocks = new Fixture(helper.getLevel())) {
            blocks.set(pos.below(), Blocks.STONE.defaultBlockState());
            blocks.set(pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            close(helper, R196MobSpawning.positionY(helper.getLevel(), pos, Category.MONSTER), pos.getY() + .505D, "Half slab actual Y");
            helper.assertTrue(R196MobSpawning.positionY(helper.getLevel(), pos, Category.ANIMAL) == null, "Slab is not an animal grass surface");
            blocks.set(pos, Blocks.OAK_STAIRS.defaultBlockState());
            close(helper, R196MobSpawning.positionY(helper.getLevel(), pos, Category.MONSTER), pos.getY() + 1.005D, "Stair actual Y");
            blocks.set(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState()); blocks.set(pos, Blocks.SNOW.defaultBlockState());
            helper.assertTrue(R196MobSpawning.positionY(helper.getLevel(), pos, Category.ANIMAL) != null, "Snow layer still checks grass below");
            blocks.set(pos.below(), Blocks.WATER.defaultBlockState()); blocks.set(pos, Blocks.LILY_PAD.defaultBlockState());
            helper.assertTrue(R196MobSpawning.positionY(helper.getLevel(), pos, Category.MONSTER) != null, "Lily-pad source hostile placement");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void ambientAndAquaticPositionsRetainDifferentMaterialChecks(GameTestHelper helper) {
        BlockPos pos = position(helper);
        try (Fixture blocks = new Fixture(helper.getLevel())) {
            blocks.set(pos, Blocks.AIR.defaultBlockState()); blocks.set(pos.below(), Blocks.AIR.defaultBlockState());
            helper.assertTrue(R196MobSpawning.positionY(helper.getLevel(), pos, Category.AMBIENT) != null, "Bat needs no floor");
            helper.assertTrue(R196MobSpawning.positionY(helper.getLevel(), pos, Category.MONSTER) == null, "Hostile needs solid floor");
            blocks.set(pos, Blocks.WATER.defaultBlockState());
            helper.assertTrue(R196MobSpawning.positionY(helper.getLevel(), pos, Category.AMBIENT) == null, "Bat rejects water feet");
            helper.assertTrue(R196MobSpawning.positionY(helper.getLevel(), pos, Category.AQUATIC) == null, "Aquatic requires water below as well");
            blocks.set(pos.below(), Blocks.WATER.defaultBlockState());
            helper.assertTrue(R196MobSpawning.positionY(helper.getLevel(), pos, Category.AQUATIC) != null, "Two water cells permit aquatic position");
            blocks.set(pos, Blocks.LAVA.defaultBlockState());
            for (Category category : Category.values()) helper.assertTrue(R196MobSpawning.positionY(helper.getLevel(), pos, category) == null, "Lava feet reject " + category);
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void firstNonAirRoofStopsAtOtherBlocksInsteadOfLookingForAnyStoneAbove(GameTestHelper helper) {
        BlockPos pos = position(helper);
        try (Fixture blocks = new Fixture(helper.getLevel())) {
            blocks.box(pos.above(), pos.above(4), Blocks.AIR.defaultBlockState());
            blocks.set(pos.above(4), Blocks.STONE.defaultBlockState());
            helper.assertTrue(R196MobSpawning.firstAbove(helper.getLevel(), pos).is(Blocks.STONE), "Skip only air");
            blocks.set(pos.above(2), Blocks.OAK_LEAVES.defaultBlockState());
            helper.assertTrue(R196MobSpawning.firstAbove(helper.getLevel(), pos).is(Blocks.OAK_LEAVES), "Leaves stop first-above search");
            blocks.set(pos.above(), Blocks.DIRT.defaultBlockState());
            helper.assertTrue(R196MobSpawning.firstAbove(helper.getLevel(), pos).is(Blocks.DIRT), "Dirt obscures all later stone");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceOutdoorsIncludesShortPassableExitButNotClosedRoom(GameTestHelper helper) {
        BlockPos pos = position(helper);
        try (Fixture blocks = new Fixture(helper.getLevel())) {
            blocks.box(pos.offset(-3, 0, -3), pos.offset(3, 4, 3), Blocks.STONE.defaultBlockState());
            blocks.box(pos, pos.above(2), Blocks.AIR.defaultBlockState());
            helper.assertTrue(!R196OutdoorChecks.outdoors(helper.getLevel(), pos), "Sealed source room is indoors");
            blocks.box(pos.east(), pos.east(3).above(1), Blocks.AIR.defaultBlockState());
            blocks.box(pos.east(3).above(2), new BlockPos(pos.getX() + 3, helper.getLevel().getMaxBuildHeight() - 1, pos.getZ()), Blocks.AIR.defaultBlockState());
            helper.assertTrue(!R196OutdoorChecks.openSky(helper.getLevel(), pos), "Center still has a roof");
            helper.assertTrue(R196OutdoorChecks.outdoors(helper.getLevel(), pos), "Three-block path reaches an open column");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void initialPopulationChecksFullHeadCubesRatherThanNaturalLightOrGeneralCollision(GameTestHelper helper) {
        BlockPos pos = position(helper); Mob mob = create(helper, MobKind.REVENANT);
        try (Fixture blocks = new Fixture(helper.getLevel())) {
            blocks.set(pos.above(), Blocks.STONE.defaultBlockState());
            helper.assertTrue(!R196MobSpawning.initialHeadroom(helper.getLevel(), pos, mob), "Full head cube rejects initial placement");
            blocks.set(pos.above(), Blocks.OAK_SLAB.defaultBlockState());
            helper.assertTrue(R196MobSpawning.initialHeadroom(helper.getLevel(), pos, mob), "Source initial head check ignores non-full slabs");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void persistentMonstersStillCountWhilePersistentAnimalsDoNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); int monsters = R196MobSpawning.count(level, Category.MONSTER, null), animals = R196MobSpawning.count(level, Category.ANIMAL, null);
        Mob monster = create(helper, MobKind.REVENANT), animal = create(helper, MobKind.DIRE_WOLF);
        monster.setPersistenceRequired(); animal.setPersistenceRequired();
        try {
            helper.assertTrue(level.addFreshEntity(monster) && level.addFreshEntity(animal), "Real entities enter source count");
            helper.assertValueEqual(R196MobSpawning.count(level, Category.MONSTER, null), monsters + 1, "Named monster still consumes cap");
            helper.assertValueEqual(R196MobSpawning.count(level, Category.ANIMAL, null), animals, "Persistent source animal is exempt");
        } finally { monster.discard(); animal.discard(); }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void spawnerClassCapsUseOldSubclassRelationshipsRatherThanSharedModernImplementationClasses(GameTestHelper helper) {
        Mob vampire = create(helper, MobKind.VAMPIRE_BAT), giant = create(helper, MobKind.GIANT_VAMPIRE_BAT), nightwing = create(helper, MobKind.NIGHTWING);
        List<Mob> mobs = List.of(vampire, giant, nightwing);
        try {
            for (Mob mob : mobs) helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Shared implementation source bats enter world");
            var bounds = vampire.getBoundingBox().inflate(2);
            helper.assertValueEqual(R196MobSpawning.spawnerEntities(helper.getLevel(), MiteMobs.type(MobKind.VAMPIRE_BAT), vampire.getClass(), bounds).size(), 2,
                    "Vampire cap includes giant subclass, but excludes unrelated nightwing");
            helper.assertValueEqual(R196MobSpawning.spawnerEntities(helper.getLevel(), MiteMobs.type(MobKind.GIANT_VAMPIRE_BAT), giant.getClass(), bounds).size(), 1,
                    "Giant cap excludes its normal-size parent");
        } finally { mobs.forEach(Mob::discard); }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void belowAndAboveSixtyCountsAreIndependentAndNegativeYExtendsDeepTier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); int deep = R196MobSpawning.count(level, Category.MONSTER, true), high = R196MobSpawning.count(level, Category.MONSTER, false);
        Mob low = create(helper, MobKind.GHOUL), above = create(helper, MobKind.GHOUL); low.setPos(low.getX(), -12, low.getZ()); above.setPos(above.getX(), 60, above.getZ());
        try {
            helper.assertTrue(level.addFreshEntity(low) && level.addFreshEntity(above), "Both height bins add real entities");
            helper.assertValueEqual(R196MobSpawning.count(level, Category.MONSTER, true), deep + 1, "Negative modern Y is deep source tier");
            helper.assertValueEqual(R196MobSpawning.count(level, Category.MONSTER, false), high + 1, "Exactly sixty is upper tier");
        } finally { low.discard(); above.discard(); }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    @SuppressWarnings("removal")
    public static void livingCreativePlayerProvidesRadiusEightWithBoundaryOnlyChunks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setPos(320000, 70, -320000); var chunks = R196MobSpawning.eligibleChunks(helper.getLevel(), false); ChunkPos center = player.chunkPosition();
            int found = 0, active = 0;
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                Boolean edge = chunks.get(ChunkPos.asLong(center.x + x, center.z + z));
                helper.assertTrue(edge != null, "Creative source player contributes chunk"); found++; if (!edge) active++;
            }
            helper.assertValueEqual(found, 289, "Seventeen by seventeen chunk union"); helper.assertValueEqual(active, 225, "Only fifteen by fifteen interior participates");
        } finally { helper.getLevel().getServer().getPlayerList().remove(player); }
        helper.succeed();
    }
    private static CompoundTag spawnerTag(BlockPos position, int delay, boolean custom) {
        CompoundTag entity = new CompoundTag(); entity.putString("id", "minecraft:zombie");
        ListTag coordinates = new ListTag(); coordinates.add(DoubleTag.valueOf(position.getX() + .5)); coordinates.add(DoubleTag.valueOf(position.getY())); coordinates.add(DoubleTag.valueOf(position.getZ() + .5)); entity.put("Pos", coordinates);
        var rules = custom ? Optional.of(new SpawnData.CustomSpawnRules(new InclusiveRange<>(0, 15), new InclusiveRange<>(0, 15))) : Optional.<SpawnData.CustomSpawnRules>empty();
        CompoundTag tag = new CompoundTag(); tag.putShort("Delay", (short)delay); tag.put("SpawnData", SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, new SpawnData(entity, rules, Optional.empty())).getOrThrow()); return tag;
    }
    private static BaseSpawner spawner() { return new BaseSpawner() { @Override public void broadcastEvent(Level level, BlockPos pos, int event) {} }; }
    @GameTest(template = "ranged_027")
    @SuppressWarnings("removal")
    public static void failedSourceSpawnerCycleBacksOffTenTicksAndIncludesCreativeActivation(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel(); BlockPos pos = position(helper); player.setPos(pos.getX() + 3, pos.getY(), pos.getZ());
        try (Fixture blocks = new Fixture(helper.getLevel())) {
            blocks.box(pos.offset(-1, -1, -1), pos.offset(1, 3, 1), Blocks.STONE.defaultBlockState());
            BaseSpawner spawner = spawner(); spawner.load(helper.getLevel(), pos, spawnerTag(pos, 1, false));
            spawner.serverTick(helper.getLevel(), pos); helper.assertValueEqual((int)spawner.save(new CompoundTag()).getShort("Delay"), 0, "Creative player activates source countdown");
            spawner.serverTick(helper.getLevel(), pos); helper.assertValueEqual((int)spawner.save(new CompoundTag()).getShort("Delay"), 10, "Four failed source attempts add ten ticks");
            helper.assertTrue(((R196SpawnerAccess)spawner).miteUsesSourceRules(), "Default SpawnData follows source qualification");
        } finally { helper.getLevel().getServer().getPlayerList().remove(player); }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    @SuppressWarnings("removal")
    public static void successfulSourceSpawnerFinalizesRealMobAndResetsOriginalDelay(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel(); BlockPos pos = position(helper); player.setPos(pos.getX() + 3, pos.getY(), pos.getZ());
        var bounds = new net.minecraft.world.phys.AABB(pos).inflate(2, 4, 2);
        var before = helper.getLevel().getEntitiesOfClass(Mob.class, bounds);
        List<Mob> added = new ArrayList<>();
        try (Fixture blocks = new Fixture(helper.getLevel())) {
            blocks.box(pos.offset(-1, 0, -1), pos.offset(1, 3, 1), Blocks.AIR.defaultBlockState());
            blocks.box(pos.offset(-1, -1, -1), pos.offset(1, -1, 1), Blocks.STONE.defaultBlockState());
            CompoundTag tag = spawnerTag(pos, 0, false); tag.getCompound("SpawnData").getCompound("entity").putString("id", "minecraft:zombified_piglin");
            BaseSpawner spawner = spawner(); spawner.load(helper.getLevel(), pos, tag); spawner.serverTick(helper.getLevel(), pos);
            for (Mob mob : helper.getLevel().getEntitiesOfClass(Mob.class, bounds)) if (!before.contains(mob)) added.add(mob);
            helper.assertTrue(!added.isEmpty() && added.stream().allMatch(mob -> mob.getType() == EntityType.ZOMBIFIED_PIGLIN), "Real source spawner adds the qualified identity");
            int delay = spawner.save(new CompoundTag()).getShort("Delay");
            helper.assertTrue(delay >= 200 && delay < 800, "Successful cycle uses source random delay200..799");
        } finally { added.forEach(Mob::discard); helper.getLevel().getServer().getPlayerList().remove(player); }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    @SuppressWarnings("removal")
    public static void disabledSourceSpawnerPersistsAndNeverConsumesDelay(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel(); BlockPos pos = position(helper); player.moveTo(pos.getCenter());
        try {
            CompoundTag tag = spawnerTag(pos, 20, false); tag.putBoolean("mite:r196_disabled", true);
            BaseSpawner first = spawner(); first.load(helper.getLevel(), pos, tag); first.serverTick(helper.getLevel(), pos);
            CompoundTag saved = first.save(new CompoundTag()); helper.assertTrue(saved.getBoolean("mite:r196_disabled"), "Source metadata-fifteen representation saves");
            helper.assertValueEqual((int)saved.getShort("Delay"), 20, "Disabled generator does not activate");
            BaseSpawner restored = spawner(); restored.load(helper.getLevel(), pos, saved); restored.serverTick(helper.getLevel(), pos);
            helper.assertValueEqual((int)restored.save(new CompoundTag()).getShort("Delay"), 20, "Disabled reload remains inert");
        } finally { helper.getLevel().getServer().getPlayerList().remove(player); }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void explicitModernSpawnerRulesAreNotClaimedByDefaultSourceOverrides(GameTestHelper helper) {
        BlockPos pos = position(helper); BaseSpawner spawner = spawner(); spawner.load(helper.getLevel(), pos, spawnerTag(pos, 0, true));
        spawner.serverTick(helper.getLevel(), pos);
        helper.assertTrue(!((R196SpawnerAccess)spawner).miteUsesSourceRules(), "Configured modern SpawnData custom rules retain native handling");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void releaseSiegeIsDormantWithoutDisablingModernRaidInfrastructure(GameTestHelper helper) {
        long previous = helper.getLevel().getDayTime();
        try {
            helper.getLevel().setDayTime(18000); helper.assertValueEqual(new VillageSiege().tick(helper.getLevel(), true, true), 0, "R196 null spawn-position finder keeps siege dormant");
            helper.assertTrue(helper.getLevel().getRaids() != null, "Modern raid infrastructure stays available");
        } finally { helper.getLevel().setDayTime(previous); }
        helper.succeed();
    }
}
