package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real trial ticks, living UUID tracking, death, reward ejection, cooldown and subsequent rounds. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UnderworldTrialWaveGameTests {
    private static final int TIMEOUT = 800;
    private static final int COOLDOWN = 120;

    private UnderworldTrialWaveGameTests() {}

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void normalTrialTracksRealReplacementsThroughRewardsCooldownAndNextRound(GameTestHelper helper) {
        start(helper, false);
    }

    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void ominousTrialTracksRealReplacementsThroughRewardsCooldownAndNextRound(GameTestHelper helper) {
        start(helper, true);
    }

    private static void start(GameTestHelper helper, boolean ominous) {
        Wave fixture = new Wave(helper, ominous);
        // Actual entity loading/ticking is checked below; a fixed delay alone is not a readiness test.
        helper.onEachTick(() -> {
            if (helper.getTick() < 5) return;
            try {
                fixture.tick();
            } catch (RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        });
        helper.runAtTickTime(TIMEOUT - 1, () -> {
            String diagnostic = fixture.diagnostic();
            fixture.close();
            helper.fail("Trial wave did not complete its real reward/cooldown/reselection cycle; " + diagnostic);
        });
    }

    private static TrialSpawnerConfig config(String id) {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", id);
        SpawnData spawn = new SpawnData(entity, Optional.empty(), Optional.empty());
        ResourceKey<LootTable> emptyItems = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("empty"));
        return new TrialSpawnerConfig(2, 2F, 1F, 0F, 0F, 1,
                SimpleWeightedRandomList.single(spawn),
                SimpleWeightedRandomList.single(BuiltInLootTables.SPAWNER_TRIAL_CHAMBER_KEY), emptyItems);
    }

    /** Reads actual protected vanilla state, without replacing any state-machine calculation. */
    private static final class ObservedData extends TrialSpawnerData {
        ObservedData(UUID player) {
            super(Set.of(player), Set.of(), 0L, 0L, 0, Optional.empty(), Optional.empty());
        }
        int total() { return totalMobsSpawned; }
        Set<UUID> current() { return Set.copyOf(currentMobs); }
        long cooldownEnd() { return cooldownEndsAt; }
        boolean noPlayers() { return detectedPlayers.isEmpty(); }
        Optional<SpawnData> next() { return nextSpawnData; }
    }

    private static final class State implements TrialSpawner.StateAccessor {
        private TrialSpawnerState value = TrialSpawnerState.ACTIVE;
        @Override public void setState(Level level, TrialSpawnerState state) { value = state; }
        @Override public TrialSpawnerState getState() { return value; }
        @Override public void markUpdated() {}
    }

    private static final class Wave implements AutoCloseable {
        final GameTestHelper helper;
        final boolean ominous;
        final ServerLevel level;
        final ChunkPos chunk;
        final BlockPos position;
        final boolean forcedBefore;
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final List<Entity> spawned = new ArrayList<>();
        final List<Mob> mobs = new ArrayList<>();
        final Consumer<EntityJoinLevelEvent> listener;
        final ObservedData data;
        final State state = new State();
        final TrialSpawner spawner;
        boolean detect = true;
        boolean closed;
        int phase;
        long phaseAt;
        long finishedAt;
        Mob first;
        Mob second;
        int spawnTicks;
        String waiting = "initial";

        @SuppressWarnings("removal")
        Wave(GameTestHelper helper, boolean ominous) {
            this.helper = helper;
            this.ominous = ominous;
            level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(level != null, "The actual underworld level exists");
            // These two independent rooms must never share a chunk or a force-load ticket, even
            // when the randomized GameTest grid puts both tiny templates in the same source chunk.
            // Fixed nearby sites also avoid unrelated far-away region loading at turbo test speed.
            chunk = new ChunkPos(ominous ? 1002 : 1000, 1000);
            position = new BlockPos(chunk.getMinBlockX() + 8, 200, chunk.getMinBlockZ() + 8);
            forcedBefore = level.getForcedChunks().contains(chunk.toLong());
            level.setChunkForced(chunk.x, chunk.z, true);
            // ChunkMap.prepareEntityTickingChunk requires FULL chunks in a radius of TWO. Merely
            // writing the center room may leave a cached FULL center whose promotion still waits
            // for its outer neighbors, especially when another test already loaded that center.
            // Request the exact native promotion dependency region after installing the ticket.
            for (int x = chunk.x - 2; x <= chunk.x + 2; x++) {
                for (int z = chunk.z - 2; z <= chunk.z + 2; z++) {
                    level.getChunk(x, z, ChunkStatus.FULL, true);
                }
            }
            for (BlockPos pos : BlockPos.betweenClosed(position.offset(-5, -1, -5), position.offset(5, 5, 5))) {
                previous.put(pos.immutable(), level.getBlockState(pos));
                level.setBlock(pos, pos.getY() == position.getY() - 1
                        ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            level.setBlock(position, Blocks.TRIAL_SPAWNER.defaultBlockState().setValue(TrialSpawnerBlock.OMINOUS, ominous), 2);
            helper.assertTrue(level.getBlockState(position.below()).is(Blocks.GLOWSTONE)
                    && level.getBlockState(position).is(Blocks.TRIAL_SPAWNER),
                    "The lit trial room exists inside the buildable world boundary");
            AABB area = new AABB(position).inflate(7);
            listener = event -> {
                if (event.isCanceled() || event.getLevel() != level || !area.contains(event.getEntity().position())) return;
                Entity entity = event.getEntity();
                spawned.add(entity);
                if (entity instanceof Mob mob) {
                    mob.setNoAi(true);
                    mobs.add(mob);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, listener);
            UUID player = UUID.randomUUID();
            data = new ObservedData(player);
            PlayerDetector detector = (access, selector, at, range, sight) -> detect ? List.of(player) : List.of();
            spawner = new TrialSpawner(config("minecraft:skeleton"), config("minecraft:zombie"), data,
                    COOLDOWN, 14, state, detector, PlayerDetector.EntitySelector.SELECT_FROM_LEVEL);
            spawner.overridePeacefulAndMobSpawnRule();
        }

        void tick() {
            if (closed) return;
            // GameTestServer runs ticks without real-time pacing. Drain a bounded amount of the
            // chunk cache's own task queue so ready promotion callbacks are not starved by 800
            // immediate test callbacks. This processes native tasks; it never forces manager state.
            if (!level.areEntitiesLoaded(chunk.toLong()) || !level.isPositionEntityTicking(position)) {
                for (int tasks = 0; tasks < 64 && level.getChunkSource().pollTask(); tasks++) {}
            }
            if (!level.areEntitiesLoaded(chunk.toLong()) || !level.isPositionEntityTicking(position)) {
                waiting = "chunk_readiness";
                return;
            }
            // EntityJoinLevel is synchronous, but newly loaded entity sections can become visible
            // later. Do not tick the manual fixture spawner in that gap: native tracking correctly
            // interprets a missing world UUID as an absent mob and would drop its newly added UUID.
            for (UUID id : data.current()) {
                Mob pending = mobs.stream().filter(mob -> mob.getUUID().equals(id)).findFirst().orElse(null);
                helper.assertTrue(pending != null, "Every tracked trial UUID came from a real spawn");
                if (pending.isAlive() && !visible(pending)) {
                    waiting = "uuid_visibility:" + id;
                    return;
                }
            }
            long now = level.getGameTime();
            waiting = "native_trial_tick";
            spawnTicks++;
            spawner.tickServer(level, position, ominous);
            switch (phase) {
                case 0 -> {
                    if (data.total() == 0) return;
                    helper.assertValueEqual(mobs.size(), 1, "The first real trial tick adds exactly one replacement");
                    first = mobs.getFirst();
                    if (!visible(first)) return;
                    assertTracked(first, 1);
                    helper.assertTrue(level.getMaxLocalRawBrightness(first.blockPosition()) >= 8,
                            "The replacement succeeds in a lit trial room rather than relying on natural darkness");
                    phase = 1;
                    phaseAt = now;
                }
                case 1 -> {
                    assertTracked(first, 1);
                    helper.assertValueEqual(mobs.size(), 1, "A living tracked UUID prevents premature replacement waves");
                    if (now - phaseAt < 12) return;
                    first.kill();
                    helper.assertTrue(!first.isAlive(), "The first real replacement is killed");
                    phase = 2;
                }
                case 2 -> {
                    if (data.total() < 2) return;
                    helper.assertValueEqual(mobs.size(), 2, "The second real spawn follows the first tracked death");
                    second = mobs.getLast();
                    if (!visible(second)) return;
                    assertTracked(second, 2);
                    phase = 3;
                    phaseAt = now;
                }
                case 3 -> {
                    assertTracked(second, 2);
                    helper.assertTrue(state.value == TrialSpawnerState.ACTIVE,
                            "Finishing the spawn count cannot award rewards while the last replacement lives");
                    if (now - phaseAt < 8) return;
                    second.kill();
                    helper.assertTrue(!second.isAlive(), "The last real replacement is killed");
                    detect = false;
                    phase = 4;
                }
                case 4 -> {
                    helper.assertTrue(state.value == TrialSpawnerState.WAITING_FOR_REWARD_EJECTION,
                            "Death of the final tracked UUID enters the native reward delay");
                    helper.assertTrue(data.current().isEmpty(), "Killed replacement UUIDs are removed from native tracking");
                    helper.assertValueEqual(data.total(), 0, "The native state machine resets the completed wave count");
                    helper.assertTrue(data.cooldownEnd() == now + COOLDOWN, "The configured cooldown begins at completion");
                    finishedAt = now;
                    phase = 5;
                }
                case 5 -> {
                    helper.assertValueEqual(mobs.size(), 2, "No replacement is spawned during reward processing");
                    if (now - finishedAt < 40) {
                        helper.assertTrue(state.value == TrialSpawnerState.WAITING_FOR_REWARD_EJECTION,
                                "Real server ticks preserve the original forty-tick reward delay");
                    }
                    if (state.value != TrialSpawnerState.COOLDOWN) return;
                    long keys = spawned.stream().filter(ItemEntity.class::isInstance).map(ItemEntity.class::cast)
                            .filter(entity -> entity.getItem().is(Items.TRIAL_KEY)).mapToLong(entity -> entity.getItem().getCount()).sum();
                    helper.assertTrue(keys > 0, "The original reward loot table ejects real trial-key ItemEntities");
                    helper.assertTrue(data.noPlayers(), "Each registered participant receives its reward exactly once");
                    helper.assertTrue(now < data.cooldownEnd(), "The spawner enters cooldown before its expiry");
                    phase = 6;
                }
                case 6 -> {
                    helper.assertValueEqual(mobs.size(), 2, "No duplicate mob appears during the native cooldown");
                    if (state.value != TrialSpawnerState.WAITING_FOR_PLAYERS) return;
                    helper.assertTrue(now >= finishedAt + COOLDOWN, "The full configured cooldown has elapsed");
                    helper.assertTrue(data.next().isEmpty(), "Native cooldown reset clears the cached SpawnData");
                    if (ominous) spawner.applyOminous(level, position);
                    detect = true;
                    phase = 7;
                }
                case 7 -> {
                    if (data.total() == 0) return;
                    helper.assertValueEqual(mobs.size(), 3, "The next trial round spawns one fresh replacement");
                    if (!visible(mobs.getLast())) return;
                    assertTracked(mobs.getLast(), 1);
                    helper.assertTrue(data.next().isPresent(), "A new potential is selected after the old cache was cleared");
                    helper.assertTrue(data.next().orElseThrow().getEntityToSpawn().getString("id").equals(expected()),
                            "Reselection after a complete cooldown still uses the MITE identity");
                    close();
                    helper.succeed();
                }
                default -> throw new IllegalStateException("Unknown trial phase " + phase);
            }
        }

        private String expected() { return ominous ? "mite:revenant" : "mite:longdead"; }

        private boolean visible(Mob mob) { return level.getEntity(mob.getUUID()) == mob; }

        String diagnostic() {
            return "phase=" + phase + ", waiting=" + waiting + ", trialTicks=" + spawnTicks
                    + ", loaded=" + level.areEntitiesLoaded(chunk.toLong())
                    + ", entityTicking=" + level.isPositionEntityTicking(position)
                    + ", forced=" + level.getForcedChunks().contains(chunk.toLong())
                    + ", state=" + state.value + ", ominous=" + spawner.isOminous()
                    + ", total=" + data.total() + ", current=" + data.current()
                    + ", next=" + data.next().map(value -> value.getEntityToSpawn().toString()).orElse("empty")
                    + ", position=" + position + ", expectedType=" + expected()
                    + ", captured=" + mobs.stream().map(mob -> BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())
                            + "/alive=" + mob.isAlive() + "/removed=" + mob.isRemoved()
                            + "/visible=" + visible(mob) + "/pos=" + mob.position()).toList();
        }

        private void assertTracked(Mob mob, int total) {
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString().equals(expected()),
                    "The selected trial mode produces its exact MITE replacement");
            helper.assertTrue(mob.isAlive() && level.getEntity(mob.getUUID()) == mob,
                    "Native world UUID lookup resolves the same living replacement returned by spawnMob");
            helper.assertTrue(data.current().equals(Set.of(mob.getUUID())),
                    "The real trial spawner tracks the replacement UUID, not a discarded vanilla entity");
            helper.assertValueEqual(data.total(), total, "Native successful-spawn accounting remains exact");
            helper.assertTrue(mob.isPersistenceRequired(), "Trial-spawned replacements retain native persistence");
            helper.assertTrue(mob.getSpawnType() == MobSpawnType.TRIAL_SPAWNER,
                    "An id-only trial potential receives its native trial-spawn finalization");
            helper.assertTrue(!mob.getMainHandItem().isEmpty(),
                    "Native finalization gives the replacement its real R196 weapon");
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            NeoForge.EVENT_BUS.unregister(listener);
            spawned.forEach(Entity::discard);
            previous.forEach((pos, block) -> level.setBlock(pos, block, 2));
            if (!forcedBefore) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
}
