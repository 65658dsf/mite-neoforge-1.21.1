package io.github.ningmeng123.mite.gametest;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.ningmeng123.mite.config.GameplayConfigExport;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.config.MonsterSpawnCatalog;
import io.github.ningmeng123.mite.network.GameplayConfigPayload;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings.Option;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Natural spawn configuration validation, export and real packet publication. @author NingMeng123 */
@GameTestHolder("mite_config")
@PrefixGameTestTemplate(false)
public final class MonsterSpawnConfigGameTests {
    private MonsterSpawnConfigGameTests() {}

    @GameTest(template = "empty")
    public static void directWeightsValidateAndSaveAtomicallyWithRates(GameTestHelper helper) throws Exception {
        for (String value : List.of("minecraft:zombie=0", "minecraft:zombie=1", "future_pack:missing=1000000"))
            helper.assertTrue(MiteConfig.validMonsterSpawnWeightOverride(value), "Whole candidate weights are accepted: " + value);
        for (String value : List.of("minecraft:zombie=-1", "minecraft:zombie=1.5", "minecraft:zombie=1000001",
                "minecraft:zombie=NaN", "minecraft:zombie=Infinity", "zombie=2", "bad namespace:zombie=2", "minecraft:zombie="))
            helper.assertFalse(MiteConfig.validMonsterSpawnWeightOverride(value), "Invalid candidate weights are rejected: " + value);
        helper.assertFalse(MiteConfig.validMonsterSpawnWeightOverride(null), "Null weight entries are rejected");
        try (var ignored = new Settings()) {
            var weights = Map.of("minecraft:zombie", 30D, "future_pack:missing", 1000000D, "mite:ghoul", 0D);
            helper.assertTrue(MiteConfig.saveMonsterSpawnSettings(Map.of("minecraft:zombie", .5D), weights),
                    "A complete rate and direct weight revision saves together");
            helper.assertValueEqual(MiteConfig.localSnapshot().monsterSpawnWeights(), weights, "Unknown entity weights remain saved");
            helper.assertValueEqual(MiteConfig.monsterSpawnWeight("minecraft:zombie", 100D), 30D, "Direct weight replaces the current pool's native value immediately");
            helper.assertValueEqual(MiteConfig.monsterSpawnWeight("minecraft:spider", 25D), 25D, "Omitted weights preserve each original pool value");
            var published = MiteConfig.localSnapshot();
            var storedRates = List.copyOf(MiteConfig.NATURAL_SPAWN_RATES.get());
            var storedWeights = List.copyOf(MiteConfig.NATURAL_SPAWN_WEIGHTS.get());
            for (double rejected : new double[]{-1D, .5D, 1000001D, Double.NaN, Double.POSITIVE_INFINITY}) {
                helper.assertFalse(MiteConfig.saveMonsterSpawnSettings(Map.of("minecraft:zombie", 2D), Map.of("minecraft:zombie", rejected)),
                        "Invalid direct weights cannot partially save a changed rate");
                helper.assertTrue(MiteConfig.localSnapshot() == published, "Rejected weights do not publish a partial revision");
                helper.assertValueEqual(MiteConfig.NATURAL_SPAWN_RATES.get(), storedRates, "Rejected weights preserve stored rates");
                helper.assertValueEqual(MiteConfig.NATURAL_SPAWN_WEIGHTS.get(), storedWeights, "Rejected weights preserve all stored weights");
            }
            helper.assertFalse(MiteConfig.saveMonsterSpawnSettings(Map.of("minecraft:zombie", Double.NaN), Map.of("minecraft:zombie", 200D)),
                    "Invalid rates cannot partially save changed direct weights");
            helper.assertFalse(MiteConfig.saveMonsterSpawnSettings(Map.of(), null), "A null weight draft is rejected");
            helper.assertTrue(MiteConfig.saveMonsterSpawnRates(Map.of("minecraft:zombie", .75D)), "The existing rate-only API still saves");
            helper.assertValueEqual(MiteConfig.localSnapshot().monsterSpawnWeights(), weights, "Rate-only saves preserve all direct weights");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void directWeightsUseTheRealPacketCodecAndRestoreLocalPreferences(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            var weights = Map.of("minecraft:zombie", 0D, "future_pack:missing", 12345D);
            helper.assertTrue(MiteConfig.saveMonsterSpawnSettings(Map.of("minecraft:zombie", 2D), weights), "Server candidate weights save");
            var server = MiteConfig.localSnapshot();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(buffer, new GameplayConfigPayload(server));
                var decoded = GameplayConfigPayload.CODEC.decode(buffer).snapshot();
                helper.assertValueEqual(decoded, server, "The actual packet codec preserves direct weights and every existing component");
                helper.assertValueEqual(buffer.readableBytes(), 0, "The complete expanded packet is consumed");
                helper.assertTrue(MiteConfig.saveMonsterSpawnSettings(Map.of(), Map.of("minecraft:zombie", 17D)), "Separate local candidate preferences save");
                var local = MiteConfig.localSnapshot();
                MiteConfig.receiveServerSnapshot(decoded);
                helper.assertValueEqual(MiteConfig.monsterSpawnWeight("minecraft:zombie", 100D), 0D, "Connected gameplay reads the authoritative server weight");
                helper.assertValueEqual(MiteConfig.monsterSpawnWeight("future_pack:missing", 1D), 12345D, "Authoritative unknown entity weights survive serialization");
                helper.assertValueEqual(MiteConfig.localSnapshot(), local, "Receiving weights never overwrites local preferences");
                MiteConfig.clearServerSnapshot();
                helper.assertValueEqual(MiteConfig.monsterSpawnWeight("minecraft:zombie", 100D), 17D, "Disconnect restores the separate local weight");
                helper.assertValueEqual(MiteConfig.monsterSpawnWeight("future_pack:missing", 7D), 7D, "Remote-only weights disappear after disconnect");
            } finally { buffer.release(); MiteConfig.clearServerSnapshot(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void directWeightsExportWithTheirRateRevisionWithoutTouchingActiveFiles(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            var weights = Map.of("minecraft:zombie", 25D, "mite:ghoul", 0D, "future_pack:missing", 1000000D);
            helper.assertTrue(MiteConfig.saveMonsterSpawnSettings(Map.of("minecraft:zombie", .5D), weights), "Exportable direct weights save");
            var published = MiteConfig.localSnapshot();
            var bundle = GameplayConfigExport.exportBundle(Files.createTempDirectory("mite-direct-weight-export-"));
            var parsed = new TomlParser().parse(Files.readString(bundle.primaryFile()));
            helper.assertTrue(MiteConfig.SPEC.isCorrect(parsed), "The complete weight export passes the registered configuration specification");
            helper.assertValueEqual(parsed.get(MiteConfig.NATURAL_SPAWN_WEIGHTS.getPath()), MiteConfig.NATURAL_SPAWN_WEIGHTS.get(),
                    "The exact sorted direct weight list is included in the server export");
            helper.assertValueEqual(parsed.get(MiteConfig.NATURAL_SPAWN_RATES.getPath()), MiteConfig.NATURAL_SPAWN_RATES.get(),
                    "The matching rate revision is included beside direct weights");
            helper.assertValueEqual(MiteConfig.localSnapshot(), published, "Exporting preserves the active weight revision");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void populationAndSchedulingEditsPublishImmediatelyAndRejectPartialInvalidDrafts(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            var values = new HashMap<>(MonsterSpawningSettings.DEFAULTS.values());
            values.put(Option.BASE_CAP.key(), 12D);
            values.put(Option.BLOOD_CAP.key(), 24D);
            values.put(Option.HOSTILE_INTERVAL_TICKS.key(), 100D);
            values.put(Option.SURFACE_CHANCE_PERCENT.key(), 75D);
            helper.assertTrue(MiteConfig.saveMonsterSpawningSettings(values), "Valid population and scheduling controls save together");
            helper.assertValueEqual(MiteConfig.monsterSpawningSettings().cap(289, false, 1), 12,
                    "The runtime snapshot observes the saved base cap immediately");
            helper.assertValueEqual(MiteConfig.monsterSpawningSettings().probability(false, 1F, false), .75F,
                    "The next pass uses the new percentage without a config reload");
            helper.assertFalse(MiteConfig.monsterSpawningSettings().ready(105, 100), "An active long interval is still waiting");
            values.put(Option.HOSTILE_INTERVAL_TICKS.key(), 5D);
            helper.assertTrue(MiteConfig.saveMonsterSpawningSettings(values), "The interval can change during an existing wait");
            helper.assertTrue(MiteConfig.monsterSpawningSettings().ready(105, 100), "Shortening the interval releases the existing wait immediately");
            if (!ModList.get().isLoaded("modern_ite")) {
                helper.assertValueEqual(R196MobSpawning.monsterLimit(helper.getLevel(), false, 289, false), 12,
                        "The real natural spawning runtime reads the edited cap");
                helper.assertValueEqual(R196MobSpawning.monsterLimit(helper.getLevel(), true, 289, true), 24,
                        "Both height passes read the edited blood population budget");
            }
            var before = MiteConfig.localSnapshot();
            var persisted = new LinkedHashMap<Option, Object>();
            MiteConfig.SPAWNING_CONTROLS.forEach((option, setting) -> persisted.put(option, setting.get()));
            for (double rejected : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1D, 1.5D, 100001D}) {
                var invalid = new HashMap<>(values);
                invalid.put(Option.BASE_CAP.key(), rejected);
                invalid.put(Option.BLOOD_CAP.key(), 99D);
                helper.assertFalse(MiteConfig.saveMonsterSpawningSettings(invalid), "An invalid control cannot partially save: " + rejected);
                helper.assertTrue(MiteConfig.localSnapshot() == before, "Rejected controls do not publish a new revision");
                MiteConfig.SPAWNING_CONTROLS.forEach((option, setting) -> helper.assertValueEqual(setting.get(), persisted.get(option),
                        "Rejected controls preserve every stored value: " + option.key()));
            }
            helper.assertFalse(MiteConfig.saveMonsterSpawningSettings(null), "A null control draft is rejected");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void populationAndSchedulingUseTheRealPacketCodecAndServerAuthority(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            var controls = new HashMap<>(MonsterSpawningSettings.DEFAULTS.values());
            controls.put(Option.BASE_CAP.key(), 30D);
            controls.put(Option.DAILY_CAP_INCREASE.key(), .5D);
            controls.put(Option.MAXIMUM_CAP.key(), 50D);
            controls.put(Option.HOSTILE_INTERVAL_TICKS.key(), 20D);
            helper.assertTrue(MiteConfig.saveMonsterSpawningSettings(controls), "Server spawning controls save");
            var authoritative = MiteConfig.localSnapshot();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(buffer, new GameplayConfigPayload(authoritative));
                var received = GameplayConfigPayload.CODEC.decode(buffer).snapshot();
                helper.assertValueEqual(received, authoritative, "All population and scheduling controls survive the actual packet codec");
                helper.assertValueEqual(buffer.readableBytes(), 0, "The packet decoder consumes the complete spawning snapshot");
                helper.assertTrue(MiteConfig.saveMonsterSpawningSettings(MonsterSpawningSettings.DEFAULTS.values()), "Separate local defaults save");
                var local = MiteConfig.localSnapshot();
                MiteConfig.receiveServerSnapshot(received);
                helper.assertValueEqual(MiteConfig.monsterSpawningSettings(), authoritative.spawningSettings(),
                        "The connected settings browser reads the server's population and scheduling controls");
                helper.assertValueEqual(MiteConfig.monsterSpawningSettings().cap(289, false, 101), 50,
                        "Authoritative daily growth and its ceiling apply together");
                helper.assertValueEqual(MiteConfig.localSnapshot(), local, "Server authority preserves the client's local preferences");
                MiteConfig.clearServerSnapshot();
                helper.assertValueEqual(MiteConfig.monsterSpawningSettings(), MonsterSpawningSettings.DEFAULTS,
                        "Disconnect restores local population and scheduling preferences");
            } finally { buffer.release(); MiteConfig.clearServerSnapshot(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exportedPopulationAndSchedulingControlsRetainExactValues(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            var controls = new HashMap<>(MonsterSpawningSettings.DEFAULTS.values());
            controls.put(Option.BASE_CAP.key(), 180D);
            controls.put(Option.DAILY_CAP_INCREASE.key(), 1.25D);
            controls.put(Option.MAXIMUM_CAP.key(), 300D);
            controls.put(Option.HOSTILE_INTERVAL_TICKS.key(), 40D);
            controls.put(Option.DIMENSION_CHANCE_MULTIPLIER.key(), .375D);
            helper.assertTrue(MiteConfig.saveMonsterSpawningSettings(controls), "Non-default population controls save");
            var published = MiteConfig.localSnapshot();
            Path directory = Files.createTempDirectory("mite-population-export-");
            var bundle = GameplayConfigExport.exportBundle(directory);
            var parsed = new TomlParser().parse(Files.readString(bundle.primaryFile()));
            helper.assertTrue(MiteConfig.SPEC.isCorrect(parsed), "The export with population controls passes the actual configuration specification");
            for (var option : Option.values()) {
                Number exported = parsed.get(MiteConfig.SPAWNING_CONTROLS.get(option).getPath());
                helper.assertValueEqual(exported.doubleValue(), controls.get(option.key()),
                        "The exported control preserves its exact editor value: " + option.key());
            }
            helper.assertValueEqual(MiteConfig.localSnapshot(), published, "Exporting preserves the effective spawning revision");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnRatesValidateBoundariesAndPreserveUnregisteredEntityIds(GameTestHelper helper) throws Exception {
        for (String value : List.of("minecraft:zombie=0", "minecraft:zombie=0.125", "mite:unregistered=1", "future_pack:new_mob=100"))
            helper.assertTrue(MiteConfig.validMonsterSpawnOverride(value), "A valid namespaced rate is accepted: " + value);
        for (String value : List.of("minecraft:zombie=NaN", "minecraft:zombie=Infinity", "minecraft:zombie=-Infinity",
                "minecraft:zombie=-0.01", "minecraft:zombie=101", "minecraft:zombie=100.0001", "minecraft:zombie=",
                "zombie=1", "bad namespace:zombie=1", "minecraft:zombie=1=2", "minecraft:zombie"))
            helper.assertFalse(MiteConfig.validMonsterSpawnOverride(value), "An invalid spawn rate is rejected: " + value);
        helper.assertFalse(MiteConfig.validMonsterSpawnOverride(null), "Null overrides are invalid");
        helper.assertFalse(MiteConfig.validMonsterSpawnOverride(1), "Overrides must use the ID=multiplier syntax");

        try (var ignored = new Settings()) {
            var rates = Map.of("minecraft:zombie", 0D, "future_pack:temporarily_absent", .125D,
                    "minecraft:skeleton", 100D, "mite:unchanged", 1D);
            helper.assertTrue(MiteConfig.saveMonsterSpawnRates(rates), "A complete valid rate draft saves");
            helper.assertValueEqual(MiteConfig.NATURAL_SPAWN_RATES.get(), List.of("future_pack:temporarily_absent=0.125", "minecraft:skeleton=100.0", "minecraft:zombie=0.0"),
                    "Overrides retain unknown IDs and use stable sorting without redundant default entries");
            helper.assertValueEqual(MiteConfig.monsterSpawnMultiplier("future_pack:temporarily_absent"), .125D,
                    "Missing registry entries retain their chosen rate for future installs");
            helper.assertValueEqual(MiteConfig.monsterSpawnMultiplier("minecraft:spider"), 1D, "Unconfigured entities inherit their native spawn rate");
            var before = MiteConfig.localSnapshot();
            var stored = List.copyOf(MiteConfig.NATURAL_SPAWN_RATES.get());
            for (double rejected : new double[]{ Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -.01D, 101D }) {
                helper.assertFalse(MiteConfig.saveMonsterSpawnRates(Map.of("minecraft:zombie", rejected, "minecraft:skeleton", 2D)),
                        "Invalid drafts cannot partially save: " + rejected);
                helper.assertValueEqual(MiteConfig.NATURAL_SPAWN_RATES.get(), stored, "A rejected draft leaves all saved entries untouched");
                helper.assertTrue(MiteConfig.localSnapshot() == before, "A rejected draft cannot publish a partial settings revision");
            }
            helper.assertFalse(MiteConfig.saveMonsterSpawnRates(Map.of("bad namespace:zombie", 2D)), "Invalid entity IDs cannot be saved");
            helper.assertFalse(MiteConfig.saveMonsterSpawnRates(null), "A null draft is rejected");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedSpawnRatesJoinTheServerReadyExportWithoutWritingTheActiveFile(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            var rates = Map.of("minecraft:zombie", .375D, "mite:ghoul", 0D, "future_pack:new_monster", 2.125D);
            helper.assertTrue(MiteConfig.saveMonsterSpawnRates(rates), "Spawn-rate editor values save successfully");
            var published = MiteConfig.localSnapshot();
            Path directory = Files.createTempDirectory("mite-spawn-rate-export-");
            var bundle = GameplayConfigExport.exportBundle(directory);
            var parsed = new TomlParser().parse(Files.readString(bundle.primaryFile()));
            helper.assertTrue(MiteConfig.SPEC.isCorrect(parsed), "The complete export passes the real MITE configuration specification");
            helper.assertValueEqual(parsed.get(MiteConfig.NATURAL_SPAWN_RATES.getPath()), MiteConfig.NATURAL_SPAWN_RATES.get(),
                    "The registered spawn-rate field is included with all exact saved ID overrides");
            helper.assertValueEqual(MiteConfig.localSnapshot(), published, "Exporting preserves the effective local spawn settings");
            helper.assertValueEqual(MiteConfig.monsterSpawnMultiplier("minecraft:zombie"), .375D, "Spawn configuration remains active after export");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realPacketCodecAndServerAuthorityCarrySpawnRateOverrides(GameTestHelper helper) throws Exception {
        try (var ignored = new Settings()) {
            var serverRates = Map.of("minecraft:zombie", 0D, "future_pack:missing_mob", 2.75D);
            helper.assertTrue(MiteConfig.saveMonsterSpawnRates(serverRates), "Server spawn settings save");
            var server = MiteConfig.localSnapshot();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(buffer, new GameplayConfigPayload(server));
                var decoded = GameplayConfigPayload.CODEC.decode(buffer).snapshot();
                helper.assertValueEqual(decoded, server, "The actual expanded packet codec preserves every settings component");
                helper.assertValueEqual(decoded.monsterSpawnRates(), serverRates, "Spawn rates survive actual buffer serialization");
                helper.assertValueEqual(buffer.readableBytes(), 0, "The packet decoder consumes the complete spawn settings payload");
                helper.assertTrue(MiteConfig.saveMonsterSpawnRates(Map.of("minecraft:zombie", .75D)), "The client's separate local preferences save");
                var local = MiteConfig.localSnapshot();
                MiteConfig.receiveServerSnapshot(decoded);
                helper.assertValueEqual(MiteConfig.monsterSpawnMultiplier("minecraft:zombie"), 0D, "Connected gameplay reads the server's authoritative spawn rate");
                helper.assertValueEqual(MiteConfig.monsterSpawnMultiplier("future_pack:missing_mob"), 2.75D, "Unknown IDs survive the server's immutable snapshot");
                helper.assertValueEqual(MiteConfig.localSnapshot(), local, "Server authority never overwrites local spawn preferences");
                helper.assertValueEqual(MiteConfig.NATURAL_SPAWN_RATES.get(), List.of("minecraft:zombie=0.75"), "The local editor values stay independent from the server view");
                MiteConfig.clearServerSnapshot();
                helper.assertValueEqual(MiteConfig.monsterSpawnMultiplier("minecraft:zombie"), .75D, "Disconnect restores the local spawn rate");
                helper.assertValueEqual(MiteConfig.monsterSpawnMultiplier("future_pack:missing_mob"), 1D, "Remote-only overrides disappear after disconnect");
            } finally { buffer.release(); MiteConfig.clearServerSnapshot(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnSnapshotsDefensivelyCopyValidEntriesAndRejectInvalidWireValues(GameTestHelper helper) {
        var rates = new HashMap<String, Double>();
        rates.put("minecraft:zombie", 0D);
        rates.put("future_pack:missing", 100D);
        rates.put("minecraft:skeleton", Double.NaN);
        rates.put("minecraft:spider", Double.POSITIVE_INFINITY);
        rates.put("minecraft:creeper", -1D);
        rates.put("minecraft:enderman", 101D);
        rates.put("bad namespace:entity", 2D);
        rates.put("minecraft:witch", null);
        var source = MiteConfig.localSnapshot();
        var snapshot = withRates(source, rates);
        helper.assertValueEqual(snapshot.monsterSpawnRates(), Map.of("minecraft:zombie", 0D, "future_pack:missing", 100D),
                "Malformed or nonfinite packet values cannot enter the immutable runtime snapshot");
        rates.put("minecraft:zombie", 2D);
        helper.assertValueEqual(snapshot.monsterSpawnRates().get("minecraft:zombie"), 0D, "A later source-map edit cannot mutate the published snapshot");
        boolean immutable = false;
        try { snapshot.monsterSpawnRates().put("minecraft:zombie", 3D); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        helper.assertTrue(immutable, "Published spawn settings are immutable");
        helper.assertTrue(withRates(source, null).monsterSpawnRates().isEmpty(), "An omitted legacy spawn map retains native default rates");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnCatalogReadsCurrentEntityAndBiomeRegistriesWithOriginalSourceWeights(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        for (String owner : List.of("mite", "modern_ite")) {
            var entries = MonsterSpawnCatalog.entries(owner, access);
            var byId = new HashMap<String, MonsterSpawnCatalog.Entry>();
            for (var entry : entries) {
                helper.assertTrue(byId.put(entry.id(), entry) == null, "Current entity IDs appear only once in their owner catalog");
                helper.assertTrue(MonsterSpawnCatalog.ownedBy(owner, entry.id()), "Every listed entity belongs in this mod's settings");
                helper.assertTrue(entry.type() == BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse(entry.id())),
                        "Catalog metadata points at the actual registered entity type");
                helper.assertValueEqual(entry.name(), entry.type().getDescription(), "Display names use the current entity registry translation");
            }
            for (var type : BuiltInRegistries.ENTITY_TYPE) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                if (type.getCategory() == MobCategory.MONSTER && MonsterSpawnCatalog.ownedBy(owner, id))
                    helper.assertTrue(byId.containsKey(id), "Every current registered monster is discovered automatically: " + id);
            }
            for (var nativeEntry : R196MobSpawning.sourceEntries()) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(nativeEntry.type()).toString();
                if (!MonsterSpawnCatalog.ownedBy(owner, id)) continue;
                var found = byId.get(id);
                // Ambient/creature source enemies are intentionally present too; unrelated source
                // animals do not become configurable just because they share the same scheduler.
                if (found != null) helper.assertTrue(found.baseWeights().contains(nativeEntry.weight()),
                        "Source monster metadata includes its actual configured pool weight: " + id);
            }
            access.registryOrThrow(Registries.BIOME).forEach(biome -> {
                for (var data : biome.getMobSettings().getMobs(MobCategory.MONSTER).unwrap()) {
                    if (R196MobSpawning.sourceType(data.type)) continue;
                    String id = BuiltInRegistries.ENTITY_TYPE.getKey(data.type).toString();
                    var found = byId.get(id);
                    if (found != null) helper.assertTrue(found.baseWeights().contains(data.getWeight().asInt()),
                            "Modern monster metadata includes the current biome's actual native weight: " + id);
                }
            });
        }
        helper.succeed();
    }

    private static MiteConfig.Snapshot withRates(MiteConfig.Snapshot source, Map<String, Double> rates) {
        return new MiteConfig.Snapshot(source.ores(), source.growth(), source.healthGrowth(), source.damageGrowth(),
                source.reachMultiplier(), source.reachReduction(), source.heightPenalty(), source.underworldTrials(), source.overworldTrials(),
                source.caps(), source.disabled(), source.monsterSettings(), source.oreCandidates(), source.oreChances(),
                source.overworldOreCandidates(), source.overworldOreChances(), source.overworldOres(), source.enchantmentChances(), rates);
    }

    /** Runs the real SPEC.save() against ephemeral backing; all active values and files are restored. */
    private static final class Settings implements AutoCloseable {
        private final Map<ModConfigSpec.ConfigValue<?>, Object> saved = new LinkedHashMap<>();
        private final IConfigSpec.ILoadedConfig original;
        private final MiteConfig.Snapshot remote;
        private final Path sourcePath;
        private final byte[] sourceBytes;

        private Settings() throws Exception {
            var config = ModConfigs.getModConfigs("mite").stream().filter(value -> value.getSpec() == MiteConfig.SPEC).findFirst().orElseThrow();
            var loadedField = ModConfigSpec.class.getDeclaredField("loadedConfig");
            loadedField.setAccessible(true);
            original = (IConfigSpec.ILoadedConfig)loadedField.get(MiteConfig.SPEC);
            remote = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot() ? null : MiteConfig.effectiveSnapshot();
            Path activeFile;
            try { activeFile = config.getFullPath(); }
            catch (IllegalStateException inMemory) { activeFile = null; }
            sourcePath = activeFile;
            sourceBytes = sourcePath == null ? null : Files.readAllBytes(sourcePath);
            collect(MiteConfig.SPEC.getValues(), saved);
            var implementation = Class.forName("net.neoforged.fml.config.LoadedConfig");
            var constructor = implementation.getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
            constructor.setAccessible(true);
            // CommentedConfig.copy is shallow: build every nested table independently so editing
            // a temporary spawn field cannot mutate the active file-backed configuration object.
            CommentedConfig contents = CommentedConfig.inMemory();
            MiteConfig.SPEC.correct(contents);
            saved.forEach((setting, value) -> contents.set(setting.getPath(), copy(value)));
            var temporary = (IConfigSpec.ILoadedConfig)constructor.newInstance(contents, null, config);
            try {
                MiteConfig.SPEC.acceptConfig(temporary);
                MiteConfig.clearServerSnapshot();
                MiteConfig.refreshEnchantmentRules();
            } catch (RuntimeException | Error failure) {
                MiteConfig.SPEC.acceptConfig(original);
                if (remote != null) MiteConfig.receiveServerSnapshot(remote);
                throw failure;
            }
        }

        @Override public void close() throws Exception {
            try { saved.forEach(MonsterSpawnConfigGameTests::set); }
            finally {
                MiteConfig.SPEC.acceptConfig(original);
                MiteConfig.clearServerSnapshot();
                MiteConfig.refreshEnchantmentRules();
                if (remote != null) MiteConfig.receiveServerSnapshot(remote);
            }
            if (sourceBytes != null && !Arrays.equals(sourceBytes, Files.readAllBytes(sourcePath)))
                throw new IllegalStateException("A spawn-config test unexpectedly changed the existing configuration file");
        }
    }

    private static void collect(UnmodifiableConfig definitions, Map<ModConfigSpec.ConfigValue<?>, Object> output) {
        for (var definition : definitions.entrySet()) {
            if (definition.getValue() instanceof UnmodifiableConfig child) collect(child, output);
            else if (definition.getValue() instanceof ModConfigSpec.ConfigValue<?> value) {
                Object raw = value.getRaw();
                output.put(value, copy(raw));
            }
        }
    }

    private static Object copy(Object value) {
        return value instanceof List<?> list ? list.stream().map(MonsterSpawnConfigGameTests::copy).toList() : value;
    }

    @SuppressWarnings("unchecked")
    private static void set(ModConfigSpec.ConfigValue<?> setting, Object value) {
        ((ModConfigSpec.ConfigValue<Object>)setting).set(value);
    }
}
