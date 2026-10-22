package io.github.ningmeng123.mite.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings.Option;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.entity.R196Bat;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.rules.BloodMoonInvasionRules;
import io.github.ningmeng123.mite.rules.R196MobRules;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Source spawning loops replace vanilla scheduling; modern-only mobs retain explicit native predicates. @author NingMeng123 */
public final class R196MobSpawning {
    private static io.github.ningmeng123.mite.api.MiteExtensions.Policy extension() { return io.github.ningmeng123.mite.api.MiteExtensions.policy(); }
    private static io.github.ningmeng123.mite.api.MiteExtensions.SpawnContext context(ServerLevel level, Category category, Boolean deep) {
        return new io.github.ningmeng123.mite.api.MiteExtensions.SpawnContext(level, category, deep);
    }
    private static boolean isDeep(Mob mob, ServerLevel level) { return R196SpawnRules.sourceY(mob.getBlockY(), underworld(level)) < 60; }
    public static int monsterLimit(ServerLevel level, boolean deep, int chunks, boolean blood) {
        int configured = MiteConfig.monsterSpawningSettings().cap(chunks, blood, BloodMoonInvasion.highestDay(level));
        return Math.max(0, extension().monsterCap(level, deep, chunks, configured));
    }
    /** One transaction ledger per tick; passengers debit the height interval they actually occupy. */
    private static final class SpawnBudget {
        int deep, shallow;
        SpawnBudget(int deep, int shallow) { this.deep=Math.max(0,deep); this.shallow=Math.max(0,shallow); }
        int remaining(Boolean low) { return Boolean.TRUE.equals(low) ? deep : shallow; }
        int needed(List<Entity> tree, ServerLevel level, boolean low) {
            return (int)tree.stream().filter(entity -> !entity.isRemoved() && entity instanceof Mob mob && category(mob)==Category.MONSTER && isDeep(mob,level)==low).count();
        }
        boolean fits(List<Entity> tree, ServerLevel level) { return needed(tree,level,true)<=deep && needed(tree,level,false)<=shallow; }
        void consume(List<Entity> tree, ServerLevel level) { deep-=needed(tree,level,true); shallow-=needed(tree,level,false); }
    }
    public static final TagKey<EntityType<?>> DAYLIGHT_RESTRICTED_UNDEAD = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "daylight_restricted_undead"));
    public record Entry(EntityType<? extends Mob> type, String sourceClass, Category category,
                        int weight, int minimum, int maximum, int pack, boolean modern) {}
    private record Profile(int id, String name, Map<Category, List<Entry>> lists) {}
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();
    private static final Map<ServerLevel, RandomSource> INITIAL_WEIGHTED_RANDOM = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Mite.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> INITIAL_ANIMALS = ATTACHMENTS.register(
            "initial_animals_spawned", () -> AttachmentType.builder(() -> 0).serialize(Codec.intRange(0, 4)).build());
    private static final class State {
        final Random modulation = new Random();
        R196SpawnRules.RateState rate = new R196SpawnRules.RateState(0, 0, 0);
        long lastTick = Long.MIN_VALUE;
        long lastHostileAttempt = Long.MIN_VALUE;
        List<ChunkPos> strongholds;
    }
    private static final class Tables {
        static final Map<String, EntityType<? extends Mob>> TYPES = types();
        static final Map<EntityType<?>, String> CLASSES = classes();
        static final Map<String, Integer> PACKS = new HashMap<>();
        static final Map<Integer, Profile> BIOMES = load();
        static final List<Entry> FORTRESS = fortress();
        static final Map<String, List<String>> HIERARCHIES = hierarchies();
        private static Map<String, List<String>> hierarchies() {
            Map<String, List<String>> values = new HashMap<>();
            for (var element : R196MobRules.sourceData().getAsJsonArray("entities")) {
                JsonObject row = element.getAsJsonObject(); if (!row.has("hierarchy")) continue;
                List<String> ancestors = new ArrayList<>(); for (var ancestor : row.getAsJsonArray("hierarchy")) ancestors.add(ancestor.getAsString());
                values.put(row.get("source_class").getAsString(), List.copyOf(ancestors));
            }
            return Map.copyOf(values);
        }
        private static Map<String, EntityType<? extends Mob>> types() {
            Map<String, EntityType<? extends Mob>> values = new LinkedHashMap<>();
            values.put("tf", EntityType.CREEPER); values.put("tr", EntityType.SKELETON); values.put("tt", EntityType.SPIDER);
            values.put("tk", EntityType.GIANT); values.put("tw", EntityType.ZOMBIE); values.put("ts", EntityType.SLIME);
            values.put("tj", EntityType.GHAST); values.put("tn", EntityType.ZOMBIFIED_PIGLIN); values.put("tg", EntityType.ENDERMAN);
            values.put("te", EntityType.CAVE_SPIDER); values.put("tq", EntityType.SILVERFISH); values.put("td", EntityType.BLAZE);
            values.put("tl", EntityType.MAGMA_CUBE); values.put("sk", EntityType.ENDER_DRAGON); values.put("sm", EntityType.WITHER);
            values.put("ro", EntityType.BAT); values.put("tv", EntityType.WITCH); values.put("ry", EntityType.PIG);
            values.put("rz", EntityType.SHEEP); values.put("rr", EntityType.COW); values.put("rq", EntityType.CHICKEN);
            values.put("sc", EntityType.SQUID); values.put("sf", EntityType.WOLF); values.put("rw", EntityType.MOOSHROOM);
            values.put("sb", EntityType.SNOW_GOLEM); values.put("rx", EntityType.OCELOT); values.put("sd", EntityType.IRON_GOLEM);
            values.put("rs", EntityType.HORSE); values.put("ub", EntityType.VILLAGER);
            for (MobKind kind : MobKind.values()) values.put(kind.sourceClass(), MiteMobs.type(kind));
            return Map.copyOf(values);
        }
        private static Map<EntityType<?>, String> classes() {
            Map<EntityType<?>, String> values = new IdentityHashMap<>();
            TYPES.forEach((name, type) -> values.put(type, name));
            values.put(EntityType.WITHER_SKELETON, "tr");
            values.put(EntityType.ZOMBIE_VILLAGER, "tw"); values.put(EntityType.CAT, "rx");
            values.put(EntityType.DONKEY, "rs"); values.put(EntityType.MULE, "rs");
            values.put(EntityType.ZOMBIE_HORSE, "rs"); values.put(EntityType.SKELETON_HORSE, "rs");
            return values;
        }
        private static Map<Integer, Profile> load() {
            try (var reader = new InputStreamReader(Objects.requireNonNull(R196MobSpawning.class.getResourceAsStream("/mite/spawning-031.json")), StandardCharsets.UTF_8)) {
                JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
                for (var value : data.getAsJsonArray("living_entities")) {
                    JsonObject row = value.getAsJsonObject(); PACKS.put(row.get("class").getAsString(), row.get("maximum_pack").getAsInt());
                }
                Map<Integer, Profile> result = new HashMap<>();
                for (var value : data.getAsJsonArray("biomes")) {
                    JsonObject row = value.getAsJsonObject(); Map<Category, List<Entry>> lists = new EnumMap<>(Category.class);
                    for (var pool : row.getAsJsonArray("pools")) {
                        JsonObject list = pool.getAsJsonObject(); Category category = Category.valueOf(list.get("category").getAsString().toUpperCase(java.util.Locale.ROOT));
                        List<Entry> entries = new ArrayList<>();
                        for (var element : list.getAsJsonArray("entries")) {
                            JsonObject entry = element.getAsJsonObject(); String name = entry.get("entity_class").getAsString();
                            entries.add(new Entry(Objects.requireNonNull(TYPES.get(name), name), name, category, entry.get("weight").getAsInt(),
                                    entry.get("minimum_group").getAsInt(), entry.get("maximum_group").getAsInt(), PACKS.get(name), false));
                        }
                        lists.put(category, List.copyOf(entries));
                    }
                    int id = row.get("legacy_id").getAsInt(); result.put(id, new Profile(id, row.get("name").getAsString(), lists));
                }
                if (result.size() != 25) throw new IllegalStateException("Incomplete source biome table");
                return Map.copyOf(result);
            } catch (Exception failure) { throw new IllegalStateException("Cannot load R196 spawning tables", failure); }
        }
        private static List<Entry> fortress() {
            return List.of(new Entry(EntityType.BLAZE, "td", Category.MONSTER, 100, 2, 3, PACKS.get("td"), false),
                    new Entry(EntityType.ZOMBIFIED_PIGLIN, "tn", Category.MONSTER, 50, 4, 4, PACKS.get("tn"), false),
                    new Entry(EntityType.SKELETON, "tr", Category.MONSTER, 100, 4, 4, PACKS.get("tr"), false),
                    new Entry(EntityType.MAGMA_CUBE, "tl", Category.MONSTER, 30, 4, 4, PACKS.get("tl"), false));
        }
    }
    private R196MobSpawning() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ConfiguredMonsterSpawns.register();
        modBus.addListener(R196MobSpawning::registerPlacements);
        NeoForge.EVENT_BUS.addListener(R196MobSpawning::spawnerPosition);
        NeoForge.EVENT_BUS.addListener(R196MobSpawning::onServerTick);
    }
    private static void onServerTick(ServerTickEvent.Post event) {
        BloodMoonInvasion.unlocked(event.getServer().overworld());
        for (ServerLevel level : event.getServer().getAllLevels()) tick(level);
    }
    public static boolean manages(ServerLevel level) {
        return level.dimension().equals(Level.OVERWORLD) || level.dimension().equals(Level.NETHER)
                || level.dimension().equals(Level.END) || level.dimension().equals(MiteWorldDimensions.UNDERWORLD);
    }
    public static boolean underworld(ServerLevel level) { return level.dimension().equals(MiteWorldDimensions.UNDERWORLD); }
    public static String sourceClass(Mob mob) { return mob instanceof R196Mob r196 ? r196.miteKind().sourceClass() : Tables.CLASSES.get(mob.getType()); }
    public static String sourceClass(EntityType<?> type) { return Tables.CLASSES.get(type); }
    public static List<Mob> spawnerEntities(ServerLevel level, EntityType<?> expected, Class<?> javaClass, AABB bounds) {
        String required = sourceClass(expected);
        return level.getEntitiesOfClass(Mob.class, bounds, mob -> {
            String actual = sourceClass(mob);
            return actual == null ? javaClass.isInstance(mob) : Tables.HIERARCHIES.getOrDefault(actual, List.of(actual)).contains(required);
        });
    }
    public static boolean sourceType(EntityType<?> type) { return Tables.CLASSES.containsKey(type); }
    public static List<Entry> sourceProfile(int legacyBiomeId, Category category) { return Tables.BIOMES.get(legacyBiomeId).lists().get(category); }
    /** Existing authored pool metadata, including structure overrides; no parallel configuration entity list. */
    public static List<Entry> sourceEntries() {
        List<Entry> result = new ArrayList<>();
        for (var profile : Tables.BIOMES.values())
            for (var entries : profile.lists().values()) result.addAll(entries);
        result.addAll(Tables.FORTRESS);
        return List.copyOf(result);
    }
    public static Category category(Mob mob) {
        if (mob instanceof R196Mob r196) return category(r196.miteKind().category());
        if (mob.getType() == EntityType.IRON_GOLEM || mob.getType() == EntityType.SNOW_GOLEM || mob.getType() == EntityType.VILLAGER) return null;
        return category(mob.getType().getCategory());
    }
    private static Category category(MobCategory category) {
        return category == MobCategory.MONSTER ? Category.MONSTER : category == MobCategory.AMBIENT ? Category.AMBIENT
                : category == MobCategory.CREATURE ? Category.ANIMAL : category == MobCategory.WATER_CREATURE
                || category == MobCategory.WATER_AMBIENT || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS ? Category.AQUATIC : null;
    }
    public static int biomeId(Holder<Biome> holder, ServerLevel level) {
        if (underworld(level)) return 26;
        if (level.dimension().equals(Level.NETHER)) return 8;
        if (level.dimension().equals(Level.END)) return 9;
        String name = holder.unwrapKey().map(key -> key.location().getPath()).orElse("plains");
        if (holder.is(BiomeTags.IS_OCEAN)) return holder.value().getBaseTemperature() <= .15F ? 10 : 0;
        if (holder.is(BiomeTags.IS_RIVER)) return holder.value().getBaseTemperature() <= .15F ? 11 : 7;
        if (name.contains("swamp")) return 6;
        if (name.contains("jungle")) return 21;
        if (name.contains("taiga")) return 5;
        if (name.contains("desert") || holder.is(BiomeTags.IS_BADLANDS)) return 2;
        if (name.contains("beach")) return 16;
        if (holder.value().getBaseTemperature() <= .15F) return name.contains("peak") || name.contains("slope") ? 13 : 12;
        if (holder.is(BiomeTags.IS_FOREST)) return 4;
        if (holder.is(BiomeTags.IS_MOUNTAIN) || name.startsWith("windswept")) return 3;
        return 1;
    }
    private static Holder<Biome> sourceBiome(ServerLevel level, BlockPos pos) {
        return level.getBiome(new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1, pos.getZ()));
    }
    public static List<Entry> sourcePool(ServerLevel level, BlockPos pos, Category category) {
        if (category == Category.MONSTER && level.dimension().equals(Level.NETHER)) {
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(BuiltinStructures.FORTRESS);
            if (structure != null && (level.structureManager().getStructureWithPieceAt(pos, structure).isValid()
                    || level.getBlockState(pos.below()).is(Blocks.NETHER_BRICKS) && level.structureManager().getStructureAt(pos, structure).isValid())) return Tables.FORTRESS;
        }
        if (category == Category.MONSTER && level.dimension().equals(Level.OVERWORLD)) {
            var hut = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(BuiltinStructures.SWAMP_HUT);
            if (hut != null && level.structureManager().getStructureWithPieceAt(pos, hut).isValid()) return List.of();
        }
        return OverworldSlimeSpawns.pool(level, category,
                BloodMoonInvasion.pool(level, category, Tables.BIOMES.get(biomeId(sourceBiome(level, pos), level)).lists().get(category)));
    }
    private static List<Entry> pool(ServerLevel level, BlockPos pos, Category category, boolean modernOnly) {
        List<Entry> entries = new ArrayList<>();
        if (!modernOnly) entries.addAll(sourcePool(level, pos, category));
        var actual = level.getBiome(pos).value().getMobSettings();
        for (MobCategory vanilla : MobCategory.values()) if (category(vanilla) == category)
            for (var value : actual.getMobs(vanilla).unwrap()) if (!sourceType(value.type)) {
                @SuppressWarnings("unchecked") EntityType<? extends Mob> type = (EntityType<? extends Mob>) value.type;
                entries.add(new Entry(type, null, category, value.getWeight().asInt(), value.minCount, value.maxCount, 4, true));
            }
        return entries;
    }
    public static Map<Long, Boolean> eligibleChunks(ServerLevel level, boolean hostile) {
        Map<Long, Boolean> result = new HashMap<>();
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        var moon = R196SpawnRules.moon(level.getDayTime(), level.dimension().equals(Level.OVERWORLD));
        boolean day = R196SpawnRules.daytime((int)Math.floorMod(level.getDayTime(), 24000));
        for (Player player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            if (hostile) {
                int density = R196SpawnRules.density(R196SpawnRules.sourceY(player.getBlockY(), underworld(level)), level.dimension().equals(Level.OVERWORLD),
                        day, moon, strongholdProximity(level, player.blockPosition(), state));
                // Preserve source crowd protection while allowing the live one-player budget to exceed 105/140.
                density = Math.max(density, MiteConfig.monsterSpawningSettings().cap(
                        R196SpawnRules.VANILLA_ELIGIBLE_CHUNKS, moon.blood(), BloodMoonInvasion.highestDay(level)));
                density = Math.max(0, extension().localDensity(level, player, density));
                if (level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32, 8, 32), mob -> category(mob) == Category.MONSTER).size() > density) continue;
            }
            ChunkPos center = player.chunkPosition();
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
                long key = ChunkPos.asLong(center.x + dx, center.z + dz);
                boolean edge = Math.abs(dx) == 8 || Math.abs(dz) == 8;
                if (!edge) result.put(key, false); else result.putIfAbsent(key, true);
            }
        }
        return result;
    }
    private static float strongholdProximity(ServerLevel level, BlockPos pos, State state) {
        if (!level.dimension().equals(Level.OVERWORLD)) return 0F;
        if (state.strongholds == null) {
            var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolder(BuiltinStructureSets.STRONGHOLDS);
            state.strongholds = holder.filter(value -> value.value().placement() instanceof ConcentricRingsStructurePlacement)
                    .map(value -> level.getChunkSource().getGeneratorState().getRingPositionsFor((ConcentricRingsStructurePlacement)value.value().placement())).orElse(null);
            if (state.strongholds == null) state.strongholds = List.of();
        }
        ChunkPos nearest = null; double best = Double.MAX_VALUE;
        for (ChunkPos location : state.strongholds) {
            double distance = Math.hypot(location.getMiddleBlockX() - pos.getX(), location.getMiddleBlockZ() - pos.getZ());
            if (distance < best) { best = distance; nearest = location; }
        }
        if (nearest == null) return 0F;
        BlockPos spawn = level.getSharedSpawnPos();
        double originDistance = Math.hypot(nearest.getMiddleBlockX() - spawn.getX(), nearest.getMiddleBlockZ() - spawn.getZ());
        return originDistance < 2000 ? 0F : (float)Math.max(0, 1 - best / originDistance);
    }
    public static int count(ServerLevel level, Category category, Boolean deep) {
        int total = 0;
        for (Entity entity : level.getAllEntities()) if (entity instanceof Mob mob && category(mob) == category
                && (category == Category.MONSTER || !mob.isPersistenceRequired())
                && (deep == null || (R196SpawnRules.sourceY(mob.getBlockY(), underworld(level)) < 60) == deep)) total++;
        return total;
    }
    public static void tick(ServerLevel level) {
        if (!manages(level) || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || level.players().stream().noneMatch(player -> !player.isSpectator() && player.isAlive())) return;
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        if (state.lastTick == level.getGameTime()) return;
        state.lastTick = level.getGameTime();
        boolean overworld = level.dimension().equals(Level.OVERWORLD);
        var moon = R196SpawnRules.moon(level.getDayTime(), overworld);
        var step = R196SpawnRules.advanceRate(state.rate, overworld, moon.blood() || level.isThundering(), state.modulation::nextInt); state.rate = step.state();
        var settings = MiteConfig.monsterSpawningSettings();
        boolean hostile = level.getDifficulty() != Difficulty.PEACEFUL && level.getServer().isSpawningMonsters()
                && settings.ready(level.getGameTime(), state.lastHostileAttempt);
        if (hostile) state.lastHostileAttempt = level.getGameTime();
        float modifier = overworld ? step.modifier() : (float)settings.value(Option.DIMENSION_CHANCE_MULTIPLIER);
        boolean low = hostile && Math.random() < Math.clamp(extension().hostilePassProbability(level, true, settings.probability(true, modifier, moon.blood())),0F,1F);
        boolean high = hostile && Math.random() < Math.clamp(extension().hostilePassProbability(level, false, settings.probability(false, modifier, moon.blood())),0F,1F);
        boolean periodic = Math.floorMod(level.getGameTime(), 400) == 0;
        if (low || high) {
            Map<Long, Boolean> chunks = eligibleChunks(level, true);
            // Both height passes spend one dimension-wide allowance. Carry successful
            // additions explicitly because newly added hidden sections may not yet be
            // visible through getAllEntities during this same native server tick.
            if (extension().independentHostileBudgets(level, false)) {
                SpawnBudget budget = new SpawnBudget(monsterLimit(level,true,chunks.size(),moon.blood())-count(level,Category.MONSTER,true),
                        monsterLimit(level,false,chunks.size(),moon.blood())-count(level,Category.MONSTER,false));
                if (low && budget.deep>0) spawnPass(level,Category.MONSTER,true,chunks,false,Integer.MAX_VALUE,budget);
                if (high && budget.shallow>0) spawnPass(level,Category.MONSTER,false,chunks,false,Integer.MAX_VALUE,budget);
            } else {
                int remaining = Math.max(0, monsterLimit(level,false,chunks.size(),moon.blood()) - count(level, Category.MONSTER, null));
                if (low) remaining -= spawnPass(level, Category.MONSTER, true, chunks, false, remaining);
                if (high && remaining > 0) spawnPass(level, Category.MONSTER, false, chunks, false, remaining);
            }
        }
        if (periodic) {
            Map<Long, Boolean> chunks = eligibleChunks(level, false);
            if (level.getServer().isSpawningAnimals()) {
                spawnPass(level, Category.AMBIENT, null, chunks, false);
                spawnPass(level, Category.AQUATIC, null, chunks, false);
            }
        }
        if (R196SpawnRules.animalSpawnTick(level.getGameTime())) {
            Map<Long, Boolean> chunks = eligibleChunks(level, false);
            boolean blueAnimals = moon.blue() && !R196SpawnRules.daytime((int)Math.floorMod(level.getDayTime(), 24000));
            if (blueAnimals || level.getServer().isSpawningAnimals()) spawnPass(level, Category.ANIMAL, null, chunks, !blueAnimals);
        }
    }
    public static int spawnPass(ServerLevel level, Category category, Boolean deep, Map<Long, Boolean> chunks, boolean modernOnly) {
        return spawnPass(level, category, deep, chunks, modernOnly, Integer.MAX_VALUE);
    }
    private static int spawnPass(ServerLevel level, Category category, Boolean deep, Map<Long, Boolean> chunks, boolean modernOnly, int allowance) {
        SpawnBudget budget = null;
        if (category==Category.MONSTER && extension().independentHostileBudgets(level,false)) {
            boolean blood=R196SpawnRules.moon(level.getDayTime(),level.dimension().equals(Level.OVERWORLD)).blood();
            budget=new SpawnBudget(monsterLimit(level,true,chunks.size(),blood)-count(level,category,true),monsterLimit(level,false,chunks.size(),blood)-count(level,category,false));
        }
        return spawnPass(level,category,deep,chunks,modernOnly,allowance,budget);
    }
    private static int spawnPass(ServerLevel level, Category category, Boolean deep, Map<Long, Boolean> chunks, boolean modernOnly, int allowance, SpawnBudget budget) {
        var spawnContext=context(level,category,deep);
        boolean blood = R196SpawnRules.moon(level.getDayTime(), level.dimension().equals(Level.OVERWORLD)).blood();
        int limit = category==Category.MONSTER ? monsterLimit(level,Boolean.TRUE.equals(deep),chunks.size(),blood) : R196SpawnRules.cap(category, chunks.size(), Boolean.TRUE.equals(deep), false, blood);
        int present = count(level, category, category == Category.MONSTER && budget==null ? null : deep);
        if (present >= limit || allowance <= 0) return 0;
        int available = Math.min(limit - present, allowance);
        int spawned = 0; RandomSource random = level.getRandom(); boolean hangBats = random.nextBoolean();
        boolean overworld = level.dimension().equals(Level.OVERWORLD), day = R196SpawnRules.daytime((int)Math.floorMod(level.getDayTime(), 24000));
        var moon = R196SpawnRules.moon(level.getDayTime(), overworld);
        chunkLoop: for (var coordinate : chunks.entrySet()) {
            if (coordinate.getValue() || present >= limit) continue;
            ChunkPos chunkPos = new ChunkPos(coordinate.getKey()); LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) continue;
            int upper = Math.min(level.getMaxBuildHeight(), chunk.getHighestSectionPosition() + 16);
            if (upper <= level.getMinBuildHeight()) continue;
            int x = chunkPos.getMinBlockX() + random.nextInt(16), z = chunkPos.getMinBlockZ() + random.nextInt(16);
            int y = level.getMinBuildHeight() + random.nextInt(upper - level.getMinBuildHeight());
            if (Boolean.TRUE.equals(deep) && R196SpawnRules.sourceY(y, underworld(level)) >= 60) {
                x = chunkPos.getMinBlockX() + random.nextInt(16); z = chunkPos.getMinBlockZ() + random.nextInt(16);
                y = level.getMinBuildHeight() + random.nextInt(upper - level.getMinBuildHeight());
            }
            if (deep != null && (R196SpawnRules.sourceY(y, underworld(level)) < 60) != deep) continue;
            if (category == Category.MONSTER && (overworld && y == 63 && level.getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.ICE) && random.nextInt(4) > 0
                    || nearestPlayer(level, new Vec3(x, y, z), true) >= 2304 && random.nextInt(Math.max(1, extension().distantHostileSkipBound(spawnContext, 2))) == 0)) continue;
            int successes = 0;
            int groupCount = Math.max(0, extension().spawnGroups(spawnContext, 3));
            for (int group = 0; group < groupCount; group++) {
                int trialX = x, trialZ = z, sourceAttempts=4, maximumAttempts = Math.max(0,extension().spawnAttempts(spawnContext,null,4)); Entry selected = null; SpawnGroupData groupData = null;
                for (int attempt = 0; attempt < maximumAttempts; attempt++) {
                    if (category == Category.MONSTER && (budget==null ? spawned>=available : budget.remaining(deep)<=0)) return spawned;
                    int jitter=Math.max(1,extension().spawnJitter(spawnContext,6));
                    trialX += random.nextInt(jitter) - random.nextInt(jitter); trialZ += random.nextInt(jitter) - random.nextInt(jitter);
                    BlockPos position = new BlockPos(trialX, y, trialZ);
                    Double placementY = positionY(level, position, category);
                    if (placementY == null && selected != null && !selected.modern()) continue;
                    double actualY = placementY == null ? y : placementY;
                    Vec3 point = new Vec3(trialX + .5, actualY, trialZ + .5);
                    boolean close = category == Category.MONSTER && (overworld || underworld(level)) && nearestPlayer(level, point, true) < 576
                            && level.getMaxLocalRawBrightness(BlockPos.containing(point)) == 0 && level.getMaxLocalRawBrightness(BlockPos.containing(point).above()) == 0;
                    double nearest=nearestPlayer(level,point,false), spawnDistance=point.distanceToSqr(Vec3.atLowerCornerOf(level.getSharedSpawnPos()));
                    if (!extension().spawnDistance(spawnContext,position,nearest,spawnDistance,close,R196SpawnRules.distanceAllows(nearest,spawnDistance,close))) continue;
                    if (selected == null) {
                        selected = select(level, position, category, random, modernOnly || placementY == null, true);
                        if (selected == null) { if (placementY == null) continue; else break; }
                        if (category != Category.MONSTER && "EntityVampireBat".equals(selected.sourceClass()) && random.nextInt(4) == 0)
                            sourceAttempts = 4 + random.nextInt(5);
                    }
                    if (selected.type() == EntityType.GHAST && nearestPlayer(level, point, false) < 2304) continue;
                    Entry actual = substituteNatural(level, selected, random);
                    maximumAttempts = Math.max(0,extension().spawnAttempts(spawnContext,actual.type(),overworld && "EntityPhaseSpider".equals(actual.sourceClass()) ? 64 : sourceAttempts));
                    Mob mob = actual.type().create(level); if (mob == null) continue;
                    mob.moveTo(point.x, point.y, point.z, random.nextFloat() * 360F, 0F);
                    if (mob instanceof Bat bat && hangBats) hang(level, bat);
                    if (overworld && moon.blue() && !day && level.getBrightness(LightLayer.SKY, BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ())) > 0) continue;
                    if (!qualifiesWithEvent(mob, level, MobSpawnType.NATURAL, category != Category.ANIMAL || !moon.blue() || day, actual.modern())) {
                        if (mob.getBbWidth() < 1F) continue;
                        boolean valid = false;
                        for (double[] offset : new double[][]{{-.5, 0}, {.5, 0}, {0, -.5}, {0, .5}}) {
                            mob.moveTo(point.x + offset[0], point.y, point.z + offset[1], random.nextFloat() * 360F, 0F);
                            if (qualifiesWithEvent(mob, level, MobSpawnType.NATURAL, category != Category.ANIMAL || !moon.blue() || day, actual.modern())) { valid = true; break; }
                        }
                        if (!valid) continue;
                    }
                    if (category == Category.MONSTER && !day && R196OutdoorChecks.outdoors(level, mob.blockPosition())) {
                        if (!extension().outdoorAllows(spawnContext,mob,R196SpawnRules.outdoorAllows(mob.getType().is(DAYLIGHT_RESTRICTED_UNDEAD), day, moon, random::nextInt))) continue chunkLoop;
                    }
                    SourceFinalization prepared = prepareSourceFinalization(mob, level, groupData, MobSpawnType.NATURAL);
                    mob = prepared.mob();
                    if (category == Category.MONSTER) {
                        var result = finalizeNaturalMonster(level, mob, prepared.group(), budget==null ? available-spawned : Integer.MAX_VALUE,budget);
                        groupData = result.group();
                        if (result.monsters() == 0) continue;
                        spawned += result.monsters(); present = budget==null ? present+result.monsters() : limit-budget.remaining(deep);
                    } else {
                        groupData = EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.NATURAL, prepared.group());
                        if (mob.isRemoved() || mob.isSpawnCancelled()) continue;
                        level.addFreshEntityWithPassengers(mob);
                        if (!mob.isAddedToLevel()) continue;
                        spawned++; present++;
                    }
                    if (++successes >= actual.pack()) continue chunkLoop;
                }
            }
        }
        return spawned;
    }
    public record NaturalMonsterSpawn(SpawnGroupData group, int monsters) {}

    /** Commit a fresh natural-spawn tree atomically, including tt's already-added skeleton rider. */
    public static NaturalMonsterSpawn finalizeNaturalMonster(ServerLevel level, Mob root, SpawnGroupData group, int allowance) {
        return finalizeNaturalMonster(level,root,group,allowance,null);
    }
    private static NaturalMonsterSpawn finalizeNaturalMonster(ServerLevel level, Mob root, SpawnGroupData group, int allowance, SpawnBudget budget) {
        List<Entity> owned = new ArrayList<>(); owned.add(root);
        try {
            SpawnGroupData finalized = EventHooks.finalizeMobSpawn(root, level, level.getCurrentDifficultyAt(root.blockPosition()), MobSpawnType.NATURAL, group);
            // Snapshot immediately, before any parent add callback can cancel/remove it
            // and consequently detach its pre-added rider from getSelfAndPassengers.
            List<Entity> tree = root.getSelfAndPassengers().toList();
            for (Entity entity : tree) if (!owned.contains(entity)) owned.add(entity);
            int needed = (int)owned.stream().filter(entity -> !entity.isRemoved() && entity instanceof Mob mob
                    && category(mob) == Category.MONSTER).count();
            boolean daylightBlocked = owned.stream().anyMatch(entity -> !entity.isRemoved() && entity instanceof Mob mob
                    && !naturalDaylightAllows(mob, level));
            if (root.isRemoved() || root.isSpawnCancelled() || needed > allowance || allowance <= 0 || daylightBlocked || budget!=null && !budget.fits(owned,level)) {
                discardNaturalTree(owned); return new NaturalMonsterSpawn(finalized, 0);
            }
            for (Entity entity : tree) {
                if (entity.isRemoved()) continue;
                // Source tt's skeleton is already registered; adding it again would replay
                // join callbacks and attempt a duplicate world UUID insertion.
                if (!entity.isAddedToLevel() && !level.addFreshEntity(entity)) {
                    discardNaturalTree(owned); return new NaturalMonsterSpawn(finalized, 0);
                }
            }
            if (root.isRemoved() || !root.isAddedToLevel()) {
                discardNaturalTree(owned); return new NaturalMonsterSpawn(finalized, 0);
            }
            int added = (int)owned.stream().filter(entity -> !entity.isRemoved() && entity.isAddedToLevel()
                    && entity instanceof Mob mob && category(mob) == Category.MONSTER).count();
            if (budget!=null) budget.consume(owned,level);
            return new NaturalMonsterSpawn(finalized, added);
        } catch (RuntimeException | Error failure) {
            discardNaturalTree(owned); throw failure;
        }
    }

    private static void discardNaturalTree(List<Entity> owned) {
        for (int index = owned.size() - 1; index >= 0; index--) owned.get(index).discard();
    }
    public static Entry select(ServerLevel level, BlockPos pos, Category category, RandomSource random, boolean modernOnly) {
        return select(level, pos, category, random, modernOnly, false);
    }
    /** Ordinary source passes also perform the later pack and finalization identity lotteries. */
    public static Entry select(ServerLevel level, BlockPos pos, Category category, RandomSource random, boolean modernOnly,
            boolean finalizationTransforms) {
        List<Entry> entries = extension().spawnPool(context(level,category,null),pos,List.copyOf(pool(level, pos, category, modernOnly)));
        if (entries.isEmpty()) return null;
        Holder<Biome> surface = sourceBiome(level, pos);
        int sourceY = R196SpawnRules.sourceY(pos.getY(), underworld(level));
        boolean overworld = level.dimension().equals(Level.OVERWORLD), day = R196SpawnRules.daytime((int)Math.floorMod(level.getDayTime(), 24000));
        var moon = R196SpawnRules.moon(level.getDayTime(), overworld);
        boolean invasion = BloodMoonInvasion.unlocked(level);
        for (int attempt = 0; attempt < 16; attempt++) {
            Entry entry = ConfiguredMonsterSpawns.choose(level, pos, category, entries, random, finalizationTransforms);
            if (entry == null) return null;
            if (entry.modern()) return entry;
            String name = entry.sourceClass();
            if (OverworldSlimeSpawns.applies(level, name)) return entry;
            if (!BloodMoonInvasionRules.depthAllows(name, sourceY, overworld, moon.blood(), surface.value().getBaseTemperature() <= .15F,
                    biomeId(surface, level) == 2, invasion)) continue;
            boolean stoneAbove = firstAbove(level, pos).is(Blocks.STONE) || firstAbove(level, pos).is(Blocks.DEEPSLATE);
            if (name.equals("tf")) {
                if (level.dimensionType().hasSkyLight() && !day && random.nextInt(4) != 0 && R196OutdoorChecks.outdoors(level, pos)) continue;
                if (ConfiguredMonsterSpawns.replacement(random,
                        io.github.ningmeng123.mite.rules.MonsterSpawnRules.infernalCreeperProbability(sourceY),
                        entry.type(), MiteMobs.type(MobKind.INFERNAL_CREEPER),
                        () -> R196SpawnRules.infernalCreeper(sourceY, random.nextInt(40), random.nextFloat())))
                    return custom(MobKind.INFERNAL_CREEPER, entry);
            } else if (name.equals("ts") && stoneAbove || name.equals("EntityJelly") && !stoneAbove
                    || name.equals("EntityBlob") && !stoneAbove
                    || (name.equals("EntityOoze") || name.equals("EntityPudding")) && (!stoneAbove || !stone(level.getBlockState(pos.below())))) continue;
            else if (name.equals("tt") && level.dimensionType().hasSkyLight() && random.nextInt(4) == 0 && R196OutdoorChecks.outdoors(level, pos)) continue;
            else if (name.equals("EntityWoodSpider") && !woodCover(level, pos)) continue;
            else if (name.equals("EntityBlackWidowSpider") && random.nextFloat() < .5F) continue;
            else if (name.equals("tj")) {
                boolean reject = false;
                for (Entity entity : level.getAllEntities()) if (entity.getType() == EntityType.GHAST && entity.blockPosition().distSqr(pos) < 2304 && random.nextFloat() < .8F) reject = true;
                if (reject) return null;
            }
            return entry;
        }
        return null;
    }
    private static Entry weighted(List<Entry> entries, RandomSource random) {
        int draw = random.nextInt(entries.stream().mapToInt(Entry::weight).sum());
        for (Entry entry : entries) { draw -= entry.weight(); if (draw < 0) return entry; }
        throw new IllegalStateException("Invalid source spawn weights");
    }
    private static Entry custom(MobKind kind, Entry from) {
        return new Entry(MiteMobs.type(kind), kind.sourceClass(), from.category(), from.weight(), from.minimum(), from.maximum(), Tables.PACKS.get(kind.sourceClass()), false);
    }
    /** The source natural pass chooses each member's rare identity before native placement checks. */
    public static Entry substituteNatural(ServerLevel level, Entry selected, RandomSource random) {
        if ("EntityVampireBat".equals(selected.sourceClass()) && (underworld(level) || BloodMoonInvasion.unlocked(level))
                && ConfiguredMonsterSpawns.replacement(random, 1D / 6D, selected.type(), MiteMobs.type(MobKind.GIANT_VAMPIRE_BAT),
                        () -> random.nextInt(6) == 0)) return custom(MobKind.GIANT_VAMPIRE_BAT, selected);
        if ("EntityLongdead".equals(selected.sourceClass())
                && ConfiguredMonsterSpawns.replacement(random, 1D / 6D, selected.type(), MiteMobs.type(MobKind.LONGDEAD_GUARDIAN),
                        () -> random.nextInt(6) == 0)) return custom(MobKind.LONGDEAD_GUARDIAN, selected);
        return selected;
    }
    private static double nearestPlayer(ServerLevel level, Vec3 position, boolean aliveOnly) {
        double nearest = Double.MAX_VALUE;
        for (Player player : level.players()) if (!player.isSpectator() && (!aliveOnly || player.isAlive())) nearest = Math.min(nearest, player.position().distanceToSqr(position));
        return nearest;
    }
    public static BlockState firstAbove(LevelAccessor level, BlockPos position) {
        for (BlockPos.MutableBlockPos current = position.mutable().move(Direction.UP); current.getY() < level.getMaxBuildHeight(); current.move(Direction.UP)) {
            BlockState block = level.getBlockState(current); if (!block.isAir()) return block;
        }
        return Blocks.AIR.defaultBlockState();
    }
    private static boolean stone(BlockState state) { return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE); }
    private static boolean woodCover(ServerLevel level, BlockPos pos) {
        BlockState top = firstAbove(level, pos);
        return (R196OutdoorChecks.openSky(level, pos) || top.is(BlockTags.LEAVES) || top.is(BlockTags.LOGS)) && near(level, pos, BlockTags.LOGS, 5, 2)
                && near(level, pos.above(5), BlockTags.LEAVES, 5, 5);
    }
    private static boolean near(ServerLevel level, BlockPos pos, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag, int horizontal, int vertical) {
        for (BlockPos point : BlockPos.betweenClosed(pos.offset(-horizontal, -vertical, -horizontal), pos.offset(horizontal, vertical, horizontal)))
            if (level.getBlockState(point).is(tag)) return true;
        return false;
    }
    public static boolean groundAllows(LevelAccessor level, BlockPos pos, Category category) {
        if (category == Category.AMBIENT) return true;
        BlockState block = level.getBlockState(pos);
        if (category == Category.AQUATIC) return block.getFluidState().is(FluidTags.WATER);
        if (block.isAir() || block.is(Blocks.BEDROCK) || block.is(MiteWorldBlocks.MANTLE.get()) || block.is(Blocks.MYCELIUM) || block.is(BlockTags.FENCES)) return false;
        if (category == Category.ANIMAL) return block.is(Blocks.GRASS_BLOCK);
        return block.is(Blocks.SNOW) || block.is(Blocks.LILY_PAD) || block.getBlock() instanceof SlabBlock || block.getBlock() instanceof StairBlock
                || block.isFaceSturdy(level, pos, Direction.UP);
    }
    public static Double positionY(LevelAccessor level, BlockPos pos, Category category) {
        if (pos.getY() < level.getMinBuildHeight() + 1) return null;
        BlockState feet = level.getBlockState(pos);
        if (feet.isCollisionShapeFullBlock(level, pos) || feet.getFluidState().is(FluidTags.LAVA)) return null;
        if (category == Category.AQUATIC) return feet.getFluidState().is(FluidTags.WATER) && level.getFluidState(pos.below()).is(FluidTags.WATER) ? (double)pos.getY() : null;
        if (category == Category.AMBIENT) return feet.isAir() ? (double)pos.getY() : null;
        VoxelShape feetShape = feet.getCollisionShape(level, pos);
        if (feet.is(Blocks.SNOW)) feetShape = feet.getShape(level, pos); // R196 uses state bounds; modern one-layer collision is empty.
        boolean onFeet = !feetShape.isEmpty() || feet.is(Blocks.SNOW) || feet.is(Blocks.LILY_PAD);
        BlockPos ground = onFeet && !feet.is(Blocks.SNOW) ? pos : pos.below();
        if (!groundAllows(level, ground, category)) return null;
        VoxelShape shape = onFeet ? feetShape : level.getBlockState(ground).getCollisionShape(level, ground);
        if (shape.isEmpty()) return null;
        return (onFeet ? pos.getY() : ground.getY()) + shape.max(Direction.Axis.Y) + .005D;
    }
    private static void hang(ServerLevel level, Bat bat) {
        BlockPos start = bat.blockPosition();
        for (int dy = 1; dy < 16; dy++) {
            BlockPos ceiling = start.above(dy); BlockState state = level.getBlockState(ceiling);
            if (state.isAir()) continue;
            if (state.isFaceSturdy(level, ceiling, Direction.DOWN)) {
                if (bat instanceof R196Bat sourceBat) sourceBat.initializeHangingAt(ceiling);
                else { bat.setResting(true); bat.setPos(bat.getX(), ceiling.getY() - bat.getBbHeight() - .01, bat.getZ()); }
            }
            break;
        }
    }
    public static boolean qualifiesWithEvent(Mob mob, ServerLevelAccessor accessor, MobSpawnType reason, boolean light, boolean modern) {
        if (reason == MobSpawnType.NATURAL && !naturalDaylightAllows(mob, accessor.getLevel())) return false;
        // Vanilla's natural-spawn caller checks geometry separately from the mob's light/difficulty predicate.
        if (modern) return accessor.noCollision(mob)
                && SpawnPlacements.isSpawnPositionOk(mob.getType(), accessor, mob.blockPosition())
                && SpawnPlacements.checkSpawnRules(mob.getType(), accessor, reason, mob.blockPosition(), mob.getRandom())
                && EventHooks.checkSpawnPosition(mob, accessor, reason);
        var event = new MobSpawnEvent.PositionCheck(mob, accessor, reason, null); NeoForge.EVENT_BUS.post(event);
        return event.getResult() == MobSpawnEvent.PositionCheck.Result.SUCCEED || event.getResult() == MobSpawnEvent.PositionCheck.Result.DEFAULT
                && R196SpawnQualification.qualifies(mob, accessor, light, reason == MobSpawnType.SPAWNER);
    }
    /** Only the current blood date releases surface undead; underground habitats keep their own spawn checks. */
    public static boolean naturalDaylightAllows(Mob mob, ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD) || !mob.getType().is(DAYLIGHT_RESTRICTED_UNDEAD)) return true;
        return !R196SpawnRules.daytime((int)Math.floorMod(level.getDayTime(), 24000L))
                || R196SpawnRules.moon(level.getDayTime(), true).blood()
                || !R196OutdoorChecks.surface(level, mob.blockPosition());
    }
    private static boolean undead(String name) {
        return name != null && (name.equals("tw") || name.equals("tr") || name.equals("tn") || name.equals("EntityGhoul") || name.equals("EntityWight")
                || name.equals("EntityInvisibleStalker") || name.equals("EntityShadow") || name.equals("EntityRevenant") || name.equals("EntityLongdead")
                || name.equals("EntityLongdeadGuardian") || name.equals("EntityBoneLord") || name.equals("EntityAncientBoneLord") || name.equals("EntityNightwing"));
    }
    public static boolean isUndeadSource(String name) { return undead(name); }
    /** Source tr changes to type1 only after ordinary-size qualification; modern Minecraft represents it as another identity. */
    public static Mob sourceFinalizationIdentity(Mob mob, ServerLevel level) {
        if (mob.getType() != EntityType.SKELETON || !level.dimension().equals(Level.NETHER)) return mob;
        Mob result = EntityType.WITHER_SKELETON.create(level);
        if (result == null) throw new IllegalStateException("Registered Nether skeleton cannot create");
        CompoundTag state = new CompoundTag(); mob.saveWithoutId(state); result.load(state);
        result.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        return result;
    }
    public record SourceFinalization(Mob mob, SpawnGroupData group) {}
    /** ty carries both rolls through a pack; the original baby draw survives even though tw forces adulthood. */
    public static final class SourceZombieGroupData extends Zombie.ZombieGroupData {
        public final boolean sourceBabyDraw;
        public final boolean sourceVillager;
        public SourceZombieGroupData(boolean babyDraw, boolean villager) {
            super(false, false); sourceBabyDraw = babyDraw; sourceVillager = villager;
        }
    }
    /** tw:263..273 represents a villager as state; modern Minecraft requires a separate identity. */
    public static SourceFinalization prepareSourceFinalization(Mob candidate, ServerLevel level, SpawnGroupData group) {
        return prepareSourceFinalization(candidate, level, group, MobSpawnType.SPAWNER);
    }
    /** Natural spawn rates never alter dungeon/spawner source identity lotteries. */
    public static SourceFinalization prepareSourceFinalization(Mob candidate, ServerLevel level, SpawnGroupData group, MobSpawnType reason) {
        Mob mob = sourceFinalizationIdentity(candidate, level);
        if (mob.getType() != EntityType.ZOMBIE && mob.getType() != EntityType.ZOMBIE_VILLAGER && mob.getType() != EntityType.ZOMBIFIED_PIGLIN)
            return new SourceFinalization(mob, group);
        if (group == null) {
            boolean baby = level.getRandom().nextFloat() < .05F;
            boolean villager = mob.getType() == EntityType.ZOMBIE && reason == MobSpawnType.NATURAL
                    ? ConfiguredMonsterSpawns.replacement(level.getRandom(), .05D, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER,
                            () -> level.getRandom().nextFloat() < .05F)
                    : level.getRandom().nextFloat() < .05F;
            group = new SourceZombieGroupData(baby, villager);
        }
        if (group instanceof SourceZombieGroupData source && source.sourceVillager && mob.getType() == EntityType.ZOMBIE) {
            ZombieVillager villager = EntityType.ZOMBIE_VILLAGER.create(level);
            if (villager == null) throw new IllegalStateException("Registered source villager zombie cannot create");
            CompoundTag state = new CompoundTag(); mob.saveWithoutId(state); villager.load(state);
            villager.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
            mob = villager;
        }
        return new SourceFinalization(mob, group);
    }
    public static void initialPopulation(ServerLevelAccessor accessor, Holder<Biome> biome, ChunkPos chunkPos, RandomSource random) {
        ServerLevel level = accessor.getLevel(); if (!level.dimension().equals(Level.OVERWORLD)) return;
        // Modern population runs on generation workers; ServerLevel.random has a strict single-thread detector.
        // Keep the source's separate world-wide weighted stream without sharing that thread-confined vanilla RNG.
        RandomSource selectionRandom = INITIAL_WEIGHTED_RANDOM.computeIfAbsent(level, ignored -> RandomSource.createThreadSafe());
        Profile profile = Tables.BIOMES.get(biomeId(biome, level));
        for (Category category : List.of(Category.ANIMAL, Category.AQUATIC)) {
            List<Entry> entries = new ArrayList<>(profile.lists().get(category));
            for (MobCategory vanilla : MobCategory.values()) if (category(vanilla) == category)
                for (var extra : biome.value().getMobSettings().getMobs(vanilla).unwrap()) if (!sourceType(extra.type)) {
                    @SuppressWarnings("unchecked") EntityType<? extends Mob> type = (EntityType<? extends Mob>) extra.type;
                    entries.add(new Entry(type, null, category, extra.getWeight().asInt(), extra.minCount, extra.maxCount, 4, true));
                }
            if (entries.isEmpty()) continue;
            while (random.nextFloat() < R196SpawnRules.initialProbability(biome.value().getBaseTemperature() <= .15F, category)) {
                Entry selected = weighted(entries, selectionRandom);
                if (selected.type() == EntityType.HORSE && random.nextFloat() < .8F || selected.type() == EntityType.WOLF && random.nextFloat() < .5F) continue;
                int size = selected.minimum() + random.nextInt(1 + selected.maximum() - selected.minimum());
                int x = chunkPos.getMinBlockX() + random.nextInt(16), z = chunkPos.getMinBlockZ() + random.nextInt(16), initialX = x, initialZ = z;
                SpawnGroupData group = null;
                for (int member = 0; member < size; member++) {
                    for (int attempt = 0; attempt < 4; attempt++) {
                        boolean success = false;
                        int y = accessor.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                        if (category == Category.AQUATIC) { int top = accessor.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z); if (top - y > 3) y += 2 + random.nextInt(top - y - 3); }
                        BlockPos point = new BlockPos(x, y, z); Double actualY = positionY(accessor, point, category);
                        if (actualY != null || selected.modern()) {
                            ChunkAccess chunk = accessor.getChunk(x >> 4, z >> 4);
                            if (category != Category.ANIMAL || chunk.getData(INITIAL_ANIMALS.get()) < 4) {
                                if (category == Category.ANIMAL) { chunk.setData(INITIAL_ANIMALS.get(), chunk.getData(INITIAL_ANIMALS.get()) + 1); chunk.setUnsaved(true); }
                                Mob mob = selected.type().create(level);
                                if (mob != null) {
                                    mob.moveTo(x + .5D, actualY == null ? y : actualY, z + .5D, random.nextFloat() * 360F, 0F);
                                    boolean allowed = initialHeadroom(accessor, point, mob);
                                    if (selected.modern()) allowed = accessor.noCollision(mob) && qualifiesWithEvent(mob, accessor, MobSpawnType.CHUNK_GENERATION, true, true);
                                    if (allowed) {
                                        group = EventHooks.finalizeMobSpawn(mob, accessor, accessor.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.CHUNK_GENERATION, group);
                                        if (!mob.isRemoved() && !mob.isSpawnCancelled()) {
                                            accessor.addFreshEntityWithPassengers(mob); success = true;
                                            if (selected.type() == EntityType.SQUID) mob.setNoActionTime(-9600);
                                        }
                                    }
                                }
                            }
                        }
                        x += random.nextInt(5) - random.nextInt(5); z += random.nextInt(5) - random.nextInt(5);
                        while (x < chunkPos.getMinBlockX() || x > chunkPos.getMaxBlockX() || z < chunkPos.getMinBlockZ() || z > chunkPos.getMaxBlockZ()) {
                            x = initialX + random.nextInt(5) - random.nextInt(5); z = initialZ + random.nextInt(5) - random.nextInt(5);
                        }
                        if (success) break;
                    }
                }
            }
        }
    }
    public static boolean initialHeadroom(LevelAccessor level, BlockPos feet, Mob mob) {
        for (int dy = 1; dy < (int)mob.getBbHeight() + 1; dy++) {
            BlockPos pos = feet.above(dy);
            if (level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) return false;
        }
        return true;
    }
    private static void registerPlacements(RegisterSpawnPlacementsEvent event) {
        for (EntityType<?> rawType : Tables.CLASSES.keySet()) {
            @SuppressWarnings("unchecked") EntityType<? extends Mob> type = (EntityType<? extends Mob>)rawType;
            event.register(type, SpawnPlacements.getPlacementType(type), SpawnPlacements.getHeightmapType(type),
                    R196MobSpawning::placement, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
    private static boolean placement(EntityType<? extends Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos position, RandomSource random) {
        // Modern trials intentionally ignore natural light, floor and player-distance restrictions.
        // Their own collision, line-of-sight, obstruction and custom SpawnData checks still run.
        if (reason == MobSpawnType.TRIAL_SPAWNER && underworld(level.getLevel()))
            return level.getDifficulty() != Difficulty.PEACEFUL;
        if (reason == MobSpawnType.SPAWNER && manages(level.getLevel())) return true; // Actual positioned mob is checked by PositionCheck, before finalize.
        Mob mob = type.create(level.getLevel()); if (mob == null) return false;
        mob.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5, 0, 0);
        return R196SpawnQualification.qualifies(mob, level, true, false);
    }
    private static void spawnerPosition(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.SPAWNER || !manages(event.getLevel().getLevel()) || sourceClass(event.getEntity()) == null) return;
        if (event.getResult() != MobSpawnEvent.PositionCheck.Result.DEFAULT
                || event.getSpawner() instanceof R196SpawnerAccess access && !access.miteUsesSourceRules()) return;
        event.setResult(R196SpawnQualification.qualifies(event.getEntity(), event.getLevel(), true, true)
                ? MobSpawnEvent.PositionCheck.Result.SUCCEED : MobSpawnEvent.PositionCheck.Result.FAIL);
    }
}
