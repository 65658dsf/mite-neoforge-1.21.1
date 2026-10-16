package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.OverworldSlimeSpawns;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning.Entry;
import io.github.ningmeng123.mite.worldgen.R196SpawnQualification;
import io.github.ningmeng123.mite.worldgen.UnderworldSpawnerMobs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Persistent user-requested invasion, with independent R196 pools and real selection/finalization paths. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class BloodMoonInvasionGameTests {
    private static final AtomicInteger SITES = new AtomicInteger();

    @GameTest(template = "empty")
    public static void exactDayNinetySevenBoundaryPersistsThroughRealNbtReloadAndTimeRollback(GameTestHelper helper) throws Exception {
        try (ProgressState state = new ProgressState(helper.getLevel())) {
            state.time(2_303_999L);
            helper.assertFalse(BloodMoonInvasion.unlocked(state.level), "The last tick of calendar day96 is still locked");
            helper.assertValueEqual(state.data.highestDay(), 96L, "The maximum reached day is observed before the unlock");
            state.data.setDirty(false);
            BloodMoonInvasion.highestDay(state.level);
            helper.assertFalse(state.data.isDirty(), "Repeated observations within one day do not dirty the save every tick");
            state.time(2_304_000L);
            helper.assertTrue(BloodMoonInvasion.unlocked(state.level), "The first tick of day97 unlocks without waiting for another moon");
            helper.assertValueEqual(state.data.highestDay(), 97L, "The progress clock uses raw calendar time without a6000tick offset");
            helper.assertTrue(state.data.isDirty(), "The changed day is marked for persistence");
            CompoundTag serialized = state.data.save(new CompoundTag(), state.level.registryAccess());
            helper.assertValueEqual(serialized.getInt("version"), 1, "The new world data has an explicit schema version");
            helper.assertTrue(serialized.getBoolean("unlocked"), "The permanent invasion bit is saved");
            helper.assertValueEqual(serialized.getLong("highest_day"), 97L, "Maximum reached calendar day is saved as a long");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(serialized, bytes);
            CompoundTag decoded = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
            BloodMoonInvasionData restored = BloodMoonInvasionData.load(decoded, state.level.registryAccess());
            state.level.getDataStorage().set(BloodMoonInvasionData.NAME, restored);
            state.time(0L);
            helper.assertTrue(BloodMoonInvasion.unlocked(state.level), "A fresh loaded SavedData remains unlocked after a backward time command");
            helper.assertValueEqual(BloodMoonInvasion.highestDay(state.level), 97L, "A time rollback never reduces equipment/invasion progress");
            ServerLevel underworld = state.level.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(BloodMoonInvasionData.get(underworld) == restored, "The persisted progress is world-shared rather than one copy per dimension");
            helper.assertValueEqual(BloodMoonInvasion.highestDay(underworld), 97L, "Other dimensions read the same maximum main-world day");
            helper.assertFalse(BloodMoonInvasion.unlocked(underworld), "The new invasion rule is only an overworld rule");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void alreadyLateWorldMigratesLazilyAndAppendOnlyPoolsKeepAllSourceEntries(GameTestHelper helper) {
        try (ProgressState state = new ProgressState(helper.getLevel()); Room room = new Room(helper)) {
            state.time(2_303_999L);
            List<Entry> original = R196MobSpawning.sourcePool(state.level, room.pos, Category.MONSTER);
            helper.assertTrue(original.equals(OverworldSlimeSpawns.pool(state.level, Category.MONSTER, R196MobSpawning.sourceProfile(1, Category.MONSTER))),
                    "The early pool only applies the four ordinary-overworld slime adaptations before day97");
            state.time(2_304_000L);
            List<Entry> expanded = R196MobSpawning.sourcePool(state.level, room.pos, Category.MONSTER);
            helper.assertTrue(state.data.unlocked(), "A real pool query migrates a late pre-feature save immediately");
            helper.assertTrue(expanded.subList(0, original.size()).equals(original), "Every old entry, weight, group and order remains intact");
            helper.assertValueEqual(expanded.size(), original.size() + 3, "Only three missing underworld types are appended to an ordinary biome");
            helper.assertValueEqual(expanded.stream().mapToInt(Entry::weight).sum(), 1135, "Adapted plains total weight1050 receives exactly85 extra tickets");
            helper.assertValueEqual(new HashSet<>(expanded.stream().map(Entry::type).toList()).size(), expanded.size(), "Shared species cannot appear twice");
            for (var extra : Map.of("te", 40, "EntityLongdead", 40, "EntityAncientBoneLord", 5).entrySet()) {
                Entry entry = named(expanded, extra.getKey());
                helper.assertValueEqual(entry.weight(), extra.getValue(), "The underworld source weight is preserved for " + extra.getKey());
            }
            helper.assertTrue(named(expanded, "EntityBoneLord") != null && named(expanded, "EntityAncientBoneLord") != null,
                    "The old and new natural lord entries remain available");
            helper.assertTrue(R196MobSpawning.sourcePool(state.level, room.pos, Category.ANIMAL).equals(R196MobSpawning.sourceProfile(1, Category.ANIMAL)),
                    "The invasion does not add animals or remove source horses");
            for (int id = 0; id <= 26; id++) if (id != 14 && id != 15) for (Category category : List.of(Category.MONSTER, Category.AMBIENT)) {
                List<Entry> base = R196MobSpawning.sourceProfile(id, category);
                List<Entry> underworld = R196MobSpawning.sourceProfile(26, category);
                List<Entry> merged = BloodMoonInvasion.merge(base, underworld);
                helper.assertTrue(merged.subList(0, base.size()).equals(base), "The merge preserves every initialized source profile " + id);
                var previousTypes = new HashSet<>(base.stream().map(Entry::type).toList());
                var appendedTypes = new HashSet<net.minecraft.world.entity.EntityType<?>>();
                for (Entry appended : merged.subList(base.size(), merged.size())) {
                    helper.assertFalse(previousTypes.contains(appended.type()), "Append does not repeat an existing species in profile " + id);
                    helper.assertTrue(appendedTypes.add(appended.type()), "New suffix contains each missing species only once in profile " + id);
                }
                var expectedMissing = new HashSet<>(underworld.stream().map(Entry::type).toList()); expectedMissing.removeAll(previousTypes);
                helper.assertTrue(appendedTypes.equals(expectedMissing), "The suffix adds every missing underworld type and no unrelated type in profile " + id);
                for (var type : previousTypes)
                    helper.assertValueEqual(merged.stream().filter(entry -> entry.type() == type).count(), base.stream().filter(entry -> entry.type() == type).count(),
                            "Existing source multiplicities, including the swamp's two different slime entries, remain unchanged in profile " + id);
                if (id == 6 && category == Category.MONSTER) {
                    var slimes = merged.stream().filter(entry -> entry.type() == EntityType.SLIME).toList();
                    helper.assertValueEqual(slimes.size(), 2, "R196 acq plus adi intentionally leaves two source slime entries in swamps");
                    helper.assertTrue(slimes.get(0).weight() == 100 && slimes.get(0).minimum() == 1 && slimes.get(0).maximum() == 4
                                    && slimes.get(1).weight() == 10 && slimes.get(1).minimum() == 1 && slimes.get(1).maximum() == 1,
                            "The independent source100(1-4) and extra swamp10(1) weights/groups are preserved rather than normalized away");
                }
            }
            state.time(0L);
            helper.assertTrue(R196MobSpawning.sourcePool(state.level, room.pos, Category.MONSTER).equals(expanded), "Persisted pools do not revert after time rollback");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firstThreeBloodMoonsKeepSourceExceptionsAndDayNinetySevenOpensLaterSurfaceSelection(GameTestHelper helper) throws Exception {
        try (ProgressState state = new ProgressState(helper.getLevel()); Room room = new Room(helper)) {
            for (int day : new int[]{32, 64, 96}) {
                state.time((day - 1L) * 24_000L + 13_000L);
                helper.assertTrue(select(room, "EntityGhoul") != null && select(room, "EntityBoneLord") != null,
                        "R196 already allows its blood-night exceptions on blood moon " + day);
                helper.assertTrue(select(room, "EntityDemonSpider") == null && select(room, "EntityPhaseSpider") == null,
                        "The source exceptions do not unlock every deep species on blood moon " + day);
            }
            state.time(2_303_999L);
            helper.assertTrue(select(room, "EntityDemonSpider") == null, "The third blood moon alone does not enable the new persistent rule early");
            state.time(2_304_000L);
            for (String source : List.of("te", "EntityLongdead", "EntityAncientBoneLord", "EntityInvisibleStalker", "EntityEarthElemental",
                    "EntityBlob", "EntityOoze", "EntityPudding", "EntityDemonSpider", "EntityPhaseSpider", "EntityHellhound", "EntityRevenant")) {
                Entry selected = select(room, source);
                helper.assertTrue(selected != null && selected.sourceClass().equals(source), "The actual selector can choose late surface species " + source);
            }
            state.time(1_000L); // Ordinary daylight after rolling the clock back: the unlock itself remains.
            helper.assertTrue(select(room, "EntityDemonSpider") != null, "The permanent selection gate no longer depends on a blood moon being active");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodStormDaylightOpensSourceSurfaceExceptionsWithoutRemovingTheirClimateRules(GameTestHelper helper) throws Exception {
        try (ProgressState state = new ProgressState(helper.getLevel()); Room room = new Room(helper)) {
            for (int day : new int[]{32, 64, 96}) for (int hour : new int[]{0, 6000, 12000, 13000, 18000, 23000}) {
                state.time((day - 1L) * 24_000L + hour);
                room.biome(Biomes.PLAINS);
                for (String source : List.of("EntityGhoul", "EntityBoneLord", "EntityRevenant"))
                    helper.assertTrue(select(room, source) != null, "Actual blood-date selector admits surface " + source + " at day/hour " + day + "/" + hour);
                helper.assertTrue(select(room, "EntityShadow") == null && select(room, "EntityWight") == null,
                        "A blood storm does not turn warm plains into desert or freezing habitat");
                helper.assertTrue(select(room, "EntityDemonSpider") == null,
                        "Before day97 a blood storm releases only the documented source surface exceptions");
                room.biome(Biomes.DESERT);
                helper.assertTrue(select(room, "EntityShadow") != null && select(room, "EntityWight") == null,
                        "Actual daytime/nighttime desert selection keeps the shadow niche");
                room.biome(Biomes.SNOWY_PLAINS);
                helper.assertTrue(select(room, "EntityWight") != null && select(room, "EntityShadow") == null,
                        "Actual daytime/nighttime freezing selection keeps the wight niche");
            }
            room.biome(Biomes.PLAINS); state.time(24_000L + 6000L);
            helper.assertTrue(select(room, "EntityBoneLord") == null && select(room, "EntityRevenant") == null,
                    "Ordinary daylight before the permanent invasion keeps the original deep-only selection gates");
            helper.assertTrue(select(room, "EntityLongdead") == null && select(room, "EntityAncientBoneLord") == null,
                    "Blood-date daylight does not prematurely add the day97-exclusive source entries");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void expandedSelectionKeepsClimateAndTreeCoverWhileOrdinaryOverworldSlimesUseGeneralGround(GameTestHelper helper) throws Exception {
        try (ProgressState state = new ProgressState(helper.getLevel()); Room room = new Room(helper)) {
            state.time(2_304_000L);
            helper.assertTrue(select(room, "EntityWight") == null && select(room, "EntityShadow") == null, "Warm plains do not acquire climate-specific surface monsters");
            room.biome(Biomes.SNOWY_PLAINS);
            helper.assertTrue(select(room, "EntityWight") != null && select(room, "EntityShadow") == null, "Freezing surface admits wights without admitting desert shadows");
            room.biome(Biomes.DESERT);
            helper.assertTrue(select(room, "EntityShadow") != null && select(room, "EntityWight") == null, "Desert surface admits shadows without admitting wights");
            room.biome(Biomes.PLAINS);
            room.set(room.pos.above(4), Blocks.DIRT.defaultBlockState());
            for (String source : List.of("EntityJelly", "EntityBlob", "EntityOoze", "EntityPudding"))
                helper.assertTrue(select(room, source) != null, "The user-requested ordinary slime habitat no longer requires a stone roof for " + source);
            room.set(room.pos.above(4), Blocks.STONE.defaultBlockState());
            room.set(room.pos.below(), Blocks.DIRT.defaultBlockState());
            helper.assertTrue(select(room, "EntityOoze") != null && select(room, "EntityPudding") != null, "All four ordinary-overworld slimes can use dirt ground");
            room.set(room.pos.below(), Blocks.STONE.defaultBlockState());
            helper.assertTrue(select(room, "EntityPudding") != null, "The complete original stone enclosure remains eligible");
            helper.assertTrue(select(room, "EntityWoodSpider") == null, "A stone room does not manufacture a wood-spider habitat");
            room.set(room.pos.above(4), Blocks.OAK_LEAVES.defaultBlockState());
            room.set(room.pos.east(2), Blocks.OAK_LOG.defaultBlockState());
            helper.assertTrue(select(room, "EntityWoodSpider") != null, "Real leaves and a nearby trunk satisfy the retained source habitat checks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyAppendedWeightedTicketReachesTheActualNaturalSelector(GameTestHelper helper) throws Exception {
        try (ProgressState state = new ProgressState(helper.getLevel()); Room room = new Room(helper)) {
            state.time(2_304_000L);
            List<Entry> pool = actualPool(room.level, room.pos, Category.MONSTER);
            int sum = pool.stream().mapToInt(Entry::weight).sum(), offset = 0;
            Map<String, Integer> observed = new LinkedHashMap<>();
            for (Entry entry : pool) {
                if (List.of("te", "EntityLongdead", "EntityAncientBoneLord").contains(entry.sourceClass()))
                    for (int ticket = offset; ticket < offset + entry.weight(); ticket++) {
                        Entry result = R196MobSpawning.select(room.level, room.pos, Category.MONSTER, new TicketRandom(sum, ticket), false);
                        helper.assertTrue(result != null && result.sourceClass().equals(entry.sourceClass()), "Every appended ticket belongs to exactly its source entry");
                        observed.merge(result.sourceClass(), 1, Integer::sum);
                    }
                offset += entry.weight();
            }
            helper.assertTrue(observed.equals(Map.of("te", 40, "EntityLongdead", 40, "EntityAncientBoneLord", 5)),
                    "Real selection retains40:40:5 relative weight without duplicating any pre-existing source entry");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realSubstitutionKeepsOneInSixGiantBatsAndGuardiansWithoutChangingOtherDimensions(GameTestHelper helper) throws Exception {
        try (ProgressState state = new ProgressState(helper.getLevel())) {
            Entry vampire = named(R196MobSpawning.sourceProfile(1, Category.AMBIENT), "EntityVampireBat");
            Entry longdead = named(R196MobSpawning.sourceProfile(26, Category.MONSTER), "EntityLongdead");
            state.time(2_303_999L);
            helper.assertTrue(substitute(state.level, vampire, new TicketRandom(6, 0)) == vampire, "Pre-unlock overworld bats do not acquire the underworld giant replacement");
            state.time(2_304_000L);
            int giants = 0, guardians = 0;
            for (int draw = 0; draw < 6; draw++) {
                if (substitute(state.level, vampire, new TicketRandom(6, draw)).sourceClass().equals("EntityGiantVampireBat")) giants++;
                if (substitute(state.level, longdead, new TicketRandom(6, draw)).sourceClass().equals("EntityLongdeadGuardian")) guardians++;
            }
            helper.assertValueEqual(giants, 1, "Only one of six independent draws promotes a vampire bat");
            helper.assertValueEqual(guardians, 1, "The existing longdead-guardian substitution remains one in six");
            state.time(0L);
            helper.assertTrue(substitute(state.level, vampire, new TicketRandom(6, 0)).sourceClass().equals("EntityGiantVampireBat"), "Giant bat access survives the saved unlock after time rollback");
            ServerLevel underworld = state.level.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(substitute(underworld, vampire, new TicketRandom(6, 0)).sourceClass().equals("EntityGiantVampireBat"), "Original underworld substitution is unchanged");
            for (var dimension : List.of(Level.NETHER, Level.END)) {
                ServerLevel other = state.level.getServer().getLevel(dimension);
                helper.assertFalse(BloodMoonInvasion.unlocked(other), "Overworld progression cannot enable invasion in " + dimension.location());
                helper.assertTrue(substitute(other, vampire, new TicketRandom(6, 0)) == vampire, "Other dimensions keep their own substitution rules");
            }
            CompoundTag lord = new CompoundTag(); lord.putString("id", "mite:ancient_bone_lord");
            SpawnData remapped = UnderworldSpawnerMobs.remap(new SpawnData(lord, java.util.Optional.empty(), java.util.Optional.empty()));
            helper.assertValueEqual(remapped.getEntityToSpawn().getString("id"), "mite:longdead_guardian", "Natural invasion does not relax the existing caged-lord ban");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void lateSurfaceBatsAndNaturalEntriesStillPassRealLightCollisionAndFinalizationRules(GameTestHelper helper) {
        Room room = new Room(helper);
        helper.runAtTickTime(239, room::close);
        helper.startSequence().thenWaitUntil(() -> helper.assertValueEqual(room.level.getMaxLocalRawBrightness(room.pos), 0,
                "The sealed above-ground room waits for real chunk lighting"))
                .thenExecute(() -> {
                    try (ProgressState state = new ProgressState(room.level)) {
                        for (MobKind kind : List.of(MobKind.VAMPIRE_BAT, MobKind.GIANT_VAMPIRE_BAT, MobKind.NIGHTWING)) {
                            Mob bat = mob(room, kind);
                            state.data.restoreProgress(0L);
                            state.time(2_303_999L);
                            helper.assertFalse(R196SpawnQualification.qualifies(bat, room.level, true, false), "The original high-Y bat restriction remains before the unlock");
                            state.time(2_304_000L); bat.getRandom().setSeed(falseBooleanSeed());
                            helper.assertTrue(R196SpawnQualification.qualifies(bat, room.level, true, false), "A real dark high-Y habitat admits late " + kind);
                            room.set(room.pos, Blocks.STONE.defaultBlockState()); bat.getRandom().setSeed(falseBooleanSeed());
                            helper.assertFalse(R196SpawnQualification.qualifies(bat, room.level, true, false), "The unlock cannot bypass actual entity/block collision");
                            room.set(room.pos, Blocks.AIR.defaultBlockState()); bat.discard();
                        }
                        Mob nativeBat = EntityType.BAT.create(room.level);
                        try {
                            nativeBat.moveTo(room.pos.getX() + .5D, room.pos.getY() + .005D, room.pos.getZ() + .5D, 0F, 0F);
                            nativeBat.getRandom().setSeed(falseBooleanSeed());
                            helper.assertFalse(R196SpawnQualification.qualifies(nativeBat, room.level, true, false),
                                    "Ordinary native bats keep their originalY<63 limit after monster invasion unlocks");
                        } finally { nativeBat.discard(); }
                        for (MobKind kind : List.of(MobKind.LONGDEAD, MobKind.LONGDEAD_GUARDIAN, MobKind.ANCIENT_BONE_LORD, MobKind.DEMON_SPIDER)) {
                            Mob candidate = mob(room, kind);
                            try {
                                helper.assertTrue(R196MobSpawning.qualifiesWithEvent(candidate, room.level, MobSpawnType.NATURAL, true, false), "Late natural qualification uses the actual collision/light/event path for " + kind);
                                EventHooks.finalizeMobSpawn(candidate, room.level, room.level.getCurrentDifficultyAt(room.pos), MobSpawnType.NATURAL, null);
                                helper.assertTrue(!candidate.isSpawnCancelled() && room.level.addFreshEntity(candidate), "The real natural transaction adds the finalized " + kind);
                                helper.assertValueEqual(candidate.getSpawnType(), MobSpawnType.NATURAL, "The new pool does not turn natural monsters into spawner mobs");
                                if (kind == MobKind.ANCIENT_BONE_LORD) helper.assertFalse(candidate.getMainHandItem().isEmpty(), "Natural lord equipment is finalized normally");
                            } finally { candidate.discard(); }
                        }
                    }
                    room.set(room.pos.above(2), Blocks.GLOWSTONE.defaultBlockState());
                })
                .thenWaitUntil(() -> helper.assertTrue(room.level.getMaxLocalRawBrightness(room.pos) >= 8, "The negative control waits for actual block light"))
                .thenExecute(() -> {
                    try (ProgressState state = new ProgressState(room.level); room) {
                        state.time(2_304_000L);
                        Mob bat = mob(room, MobKind.GIANT_VAMPIRE_BAT);
                        try {
                            bat.getRandom().setSeed(falseBooleanSeed());
                            helper.assertFalse(R196SpawnQualification.qualifies(bat, room.level, true, false), "Permanent unlock does not admit an actually lit bat habitat");
                        } finally { bat.discard(); }
                    }
                    helper.succeed();
                });
    }

    private static Mob mob(Room room, MobKind kind) {
        Mob mob = MiteMobs.type(kind).create(room.level);
        mob.moveTo(room.pos.getX() + .5D, room.pos.getY() + .005D, room.pos.getZ() + .5D, 0F, 0F);
        mob.setNoAi(true); mob.setNoGravity(true); return mob;
    }

    private static long falseBooleanSeed() {
        long seed = 0L;
        while (new Random(seed).nextBoolean()) seed++;
        return seed;
    }

    private static Entry named(List<Entry> pool, String sourceClass) {
        return pool.stream().filter(entry -> sourceClass.equals(entry.sourceClass())).findFirst().orElseThrow();
    }

    private static Entry select(Room room, String name) throws ReflectiveOperationException {
        List<Entry> pool = actualPool(room.level, room.pos, Category.MONSTER);
        int sum = pool.stream().mapToInt(Entry::weight).sum(), offset = 0;
        for (Entry entry : pool) {
            if (name.equals(entry.sourceClass())) return R196MobSpawning.select(room.level, room.pos, Category.MONSTER, new TicketRandom(sum, offset), false);
            offset += entry.weight();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Entry> actualPool(ServerLevel level, BlockPos pos, Category category) throws ReflectiveOperationException {
        Method method = R196MobSpawning.class.getDeclaredMethod("pool", ServerLevel.class, BlockPos.class, Category.class, boolean.class);
        method.setAccessible(true);
        return (List<Entry>)method.invoke(null, level, pos, category, false);
    }

    private static Entry substitute(ServerLevel level, Entry entry, RandomSource random) throws ReflectiveOperationException {
        Method method = R196MobSpawning.class.getDeclaredMethod("substitute", ServerLevel.class, Entry.class, RandomSource.class);
        method.setAccessible(true);
        return (Entry)method.invoke(null, level, entry, random);
    }

    private static final class TicketRandom extends LegacyRandomSource {
        private final int bound, ticket;
        TicketRandom(int bound, int ticket) { super(0L); this.bound = bound; this.ticket = ticket; }
        @Override public int nextInt(int requested) { return requested == bound ? ticket : requested - 1; }
        @Override public float nextFloat() { return .75F; }
    }

    private static final class ProgressState implements AutoCloseable {
        final ServerLevel level;
        final BloodMoonInvasionData data;
        private final long time, highestDay;
        private final boolean dirty;
        ProgressState(ServerLevel level) {
            this.level = level.getServer().overworld(); data = BloodMoonInvasionData.get(level);
            time = this.level.getDayTime(); highestDay = data.highestDay(); dirty = data.isDirty();
            data.restoreProgress(0L); this.level.setDayTime(0L);
        }
        void time(long value) { level.setDayTime(value); level.updateSkyBrightness(); }
        @Override public void close() {
            level.getDataStorage().set(BloodMoonInvasionData.NAME, data);
            data.restoreProgress(highestDay); data.setDirty(dirty); level.setDayTime(time); level.updateSkyBrightness();
            BloodMoonInvasionData.get(level); // Refresh the asynchronous read-only snapshot after replacing a loaded fixture.
        }
    }

    private static final class Room implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final LevelChunkSection section;
        private final PalettedContainerRO<Holder<Biome>> biomes;
        private final int quartX, quartY, quartZ;
        private boolean closed;
        Room(GameTestHelper helper) {
            level = helper.getLevel(); pos = new BlockPos(200_008 + SITES.getAndIncrement() * 32, 100, 200_008);
            var chunk = level.getChunkAt(pos); section = chunk.getSection(chunk.getSectionIndex(pos.getY())); biomes = section.getBiomes();
            quartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX()); quartY = QuartPos.fromBlock(pos.getY()) & ~3; quartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 4, 4)))
                set(cell, cell.getY() == pos.getY() - 1 || cell.getY() == pos.getY() + 4
                        || Math.abs(cell.getX() - pos.getX()) == 4 || Math.abs(cell.getZ() - pos.getZ()) == 4
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            biome(Biomes.PLAINS);
        }
        void set(BlockPos point, BlockState state) { original.putIfAbsent(point.immutable(), level.getBlockState(point)); level.setBlock(point, state, 2); }
        void biome(ResourceKey<Biome> key) {
            Holder<Biome> selected = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
            section.fillBiomesFromNoise((x, y, z, climate) -> selected, level.getChunkSource().randomState().sampler(), quartX, quartY, quartZ);
        }
        @Override public void close() {
            if (closed) return;
            closed = true; original.forEach((point, state) -> level.setBlock(point, state, 2));
            section.fillBiomesFromNoise((x, y, z, climate) -> biomes.get(x & 3, y & 3, z & 3), level.getChunkSource().randomState().sampler(), quartX, quartY, quartZ);
        }
    }
}
