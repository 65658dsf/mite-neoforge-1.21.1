package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.combat.SourcePlayerReach;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MonsterDayScaling;
import io.github.ningmeng123.mite.network.GameplayConfigPayload;
import io.github.ningmeng123.mite.rules.MonsterDayScalingRules;
import io.github.ningmeng123.mite.rules.MonsterGrowthRules;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.netty.buffer.Unpooled;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Immutable publication, the real packet codec, and the runtime consumers of common settings. @author NingMeng123 */
@GameTestHolder("mite_config")
@PrefixGameTestTemplate(false)
public final class MiteConfigGameTests {
    private MiteConfigGameTests() {}

    @GameTest(template = "empty")
    public static void bothDimensionsPublishIndependentMultipliersWithoutRewritingAdvancedRates(GameTestHelper helper) {
        try (var ignored = new Settings()) {
            var values = new EnumMap<R196OreRules.Dimension, Map<R196OreRules.Kind, Double>>(R196OreRules.Dimension.class);
            for (var dimension : List.of(R196OreRules.Dimension.OVERWORLD, R196OreRules.Dimension.UNDERWORLD)) {
                var multipliers = new EnumMap<R196OreRules.Kind, Double>(R196OreRules.Kind.class);
                for (var kind : R196OreRules.configurableKinds(dimension)) multipliers.put(kind, 1D);
                values.put(dimension, multipliers);
            }
            double surfaceCount = MiteConfig.OVERWORLD_ORE_CANDIDATES.get(R196OreRules.Kind.COPPER).get();
            double deepCount = MiteConfig.UNDERWORLD_ORE_CANDIDATES.get(R196OreRules.Kind.COPPER).get();
            double surfaceChance = MiteConfig.OVERWORLD_ORE_CHANCES.get(R196OreRules.Kind.COPPER).get();
            double deepChance = MiteConfig.UNDERWORLD_ORE_CHANCES.get(R196OreRules.Kind.COPPER).get();
            for (double multiplier : new double[]{0D, .5D, 1D, 2D}) {
                values.get(R196OreRules.Dimension.OVERWORLD).put(R196OreRules.Kind.COPPER, multiplier);
                values.get(R196OreRules.Dimension.UNDERWORLD).put(R196OreRules.Kind.COPPER, 2D - multiplier);
                helper.assertTrue(MiteConfig.saveOreMultipliers(values), "The two dimension drafts can be saved together");
                close(helper, MiteConfig.oreCandidates(R196OreRules.Kind.COPPER, R196OreRules.Dimension.OVERWORLD), surfaceCount * multiplier,
                        "The surface multiplier scales its own candidate count exactly once");
                close(helper, MiteConfig.underworldOreCandidates(R196OreRules.Kind.COPPER), deepCount * (2D - multiplier),
                        "The deep multiplier remains independent");
                close(helper, MiteConfig.OVERWORLD_ORE_CANDIDATES.get(R196OreRules.Kind.COPPER).get(), surfaceCount, "Simple editing preserves advanced surface candidates");
                close(helper, MiteConfig.UNDERWORLD_ORE_CANDIDATES.get(R196OreRules.Kind.COPPER).get(), deepCount, "Simple editing preserves advanced deep candidates");
                close(helper, MiteConfig.orePassChance(R196OreRules.Kind.COPPER, R196OreRules.Dimension.OVERWORLD), surfaceChance / 100D, "Surface probability remains unchanged");
                close(helper, MiteConfig.underworldOrePassChance(R196OreRules.Kind.COPPER), deepChance / 100D, "Deep probability remains unchanged");
            }
            for (var dimension : List.of(R196OreRules.Dimension.OVERWORLD, R196OreRules.Dimension.UNDERWORLD)) {
                var counts = dimension == R196OreRules.Dimension.OVERWORLD ? MiteConfig.OVERWORLD_ORE_CANDIDATES : MiteConfig.UNDERWORLD_ORE_CANDIDATES;
                var chances = dimension == R196OreRules.Dimension.OVERWORLD ? MiteConfig.OVERWORLD_ORE_CHANCES : MiteConfig.UNDERWORLD_ORE_CHANCES;
                int mineral = 0;
                for (var kind : R196OreRules.configurableKinds(dimension)) {
                    counts.get(kind).set(17.5D + mineral);
                    chances.get(kind).set(23D + mineral++);
                }
            }
            MiteConfig.refreshEnchantmentRules();
            for (var dimension : List.of(R196OreRules.Dimension.OVERWORLD, R196OreRules.Dimension.UNDERWORLD)) {
                int mineral = 0;
                for (var kind : R196OreRules.configurableKinds(dimension)) {
                    close(helper, MiteConfig.oreBaseCandidates(kind, dimension), 17.5D + mineral, "Each legacy frequency maps to a real candidate base: " + dimension + "/" + kind);
                    close(helper, MiteConfig.orePassChance(kind, dimension), (23D + mineral++) / 100D, "Each legacy chance maps to the only runtime pass filter: " + dimension + "/" + kind);
                }
            }
            var published = MiteConfig.localSnapshot();
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(bytes, new GameplayConfigPayload(published));
                var decoded = GameplayConfigPayload.CODEC.decode(bytes).snapshot();
                helper.assertValueEqual(decoded, published, "The real codec preserves separate multipliers and advanced rates for both dimensions");
                MiteConfig.receiveServerSnapshot(decoded);
                close(helper, MiteConfig.oreMultiplier(R196OreRules.Kind.COPPER, R196OreRules.Dimension.OVERWORLD), 2D, "Remote surface multiplier");
                close(helper, MiteConfig.underworldOreMultiplier(R196OreRules.Kind.COPPER), 0D, "Remote deep multiplier");
            } finally { bytes.release(); MiteConfig.clearServerSnapshot(); }
            values.get(R196OreRules.Dimension.UNDERWORLD).put(R196OreRules.Kind.COPPER, Double.NaN);
            values.get(R196OreRules.Dimension.OVERWORLD).put(R196OreRules.Kind.COPPER, 99D);
            helper.assertFalse(MiteConfig.saveOreMultipliers(values), "An invalid hidden dimension prevents saving either draft");
            helper.assertTrue(MiteConfig.localSnapshot() == published, "Rejected drafts cannot publish a partial revision");
            close(helper, MiteConfig.OVERWORLD_ORES.get(R196OreRules.Kind.COPPER).get(), 2D, "Rejected drafts do not mutate valid dimension fields");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void multipliersAndAdvancedChanceReachTheRealSurfaceAndDeepFeatureBatches(GameTestHelper helper) {
        try (var ignored = new Settings()) {
            int index = 0;
            for (var dimension : List.of(R196OreRules.Dimension.OVERWORLD, R196OreRules.Dimension.UNDERWORLD)) {
                var level = dimension == R196OreRules.Dimension.OVERWORLD ? helper.getLevel().getServer().overworld()
                        : helper.getLevel().getServer().getLevel(io.github.ningmeng123.mite.worldgen.MiteWorldDimensions.UNDERWORLD);
                helper.assertTrue(level != null, "The actual dimension is registered");
                var candidates = dimension == R196OreRules.Dimension.OVERWORLD ? MiteConfig.OVERWORLD_ORE_CANDIDATES : MiteConfig.UNDERWORLD_ORE_CANDIDATES;
                var chances = dimension == R196OreRules.Dimension.OVERWORLD ? MiteConfig.OVERWORLD_ORE_CHANCES : MiteConfig.UNDERWORLD_ORE_CHANCES;
                var multipliers = dimension == R196OreRules.Dimension.OVERWORLD ? MiteConfig.OVERWORLD_ORES : MiteConfig.UNDERWORLD_ORES;
                R196OreRules.Kind kind = dimension == R196OreRules.Dimension.OVERWORLD ? R196OreRules.Kind.ADAMANTIUM : R196OreRules.Kind.COPPER;
                candidates.values().forEach(value -> value.set(0D));
                chances.values().forEach(value -> value.set(100D));
                multipliers.values().forEach(value -> value.set(1D));
                candidates.get(kind).set(300D);
                var fixture = new OreGenerationGameTests.GenerationFixture(level, new net.minecraft.world.level.ChunkPos(6450 + index++, -6450), false);
                String feature = "ores_" + dimension.name().toLowerCase(java.util.Locale.ROOT);
                for (double multiplier : new double[]{0D, .5D, 2D}) {
                    multipliers.get(kind).set(multiplier);
                    // Long generation fixtures can overlap a FileWatcher reload from earlier tests' saves.
                    // Keep the disk revision consistent with the intended fixture instead of publishing temporary unsaved rates.
                    MiteConfig.SPEC.save();
                    MiteConfig.refreshEnchantmentRules();
                    fixture.fill(-64, 129);
                    fixture.place(feature, 196L);
                    int found = countMineral(fixture, kind);
                    helper.assertTrue(multiplier == 0D ? found == 0 : found > 0,
                            "Actual " + dimension + "/" + kind + " generation obeys multiplier " + multiplier + "; found=" + found);
                }
                chances.get(kind).set(0D);
                MiteConfig.SPEC.save();
                MiteConfig.refreshEnchantmentRules();
                close(helper, MiteConfig.orePassChance(kind, dimension), 0D, "The zero pass chance reaches the published runtime revision: " + dimension);
                fixture.fill(-64, 129);
                helper.assertValueEqual(countMineral(fixture, kind), 0, "Refilling the fixture clears every earlier ore before the zero-chance check: " + dimension);
                fixture.place(feature, 196L);
                close(helper, MiteConfig.orePassChance(kind, dimension), 0D, "The intended zero pass chance remains published throughout generation: " + dimension);
                helper.assertValueEqual(countMineral(fixture, kind), 0,
                        "An explicitly configured zero chance still disables the real batch without a second rarity path");
            }
        }
        helper.succeed();
    }

    private static int countMineral(OreGenerationGameTests.GenerationFixture fixture, R196OreRules.Kind kind) {
        int found = 0;
        String suffix = kind.name().toLowerCase(java.util.Locale.ROOT) + "_ore";
        BlockPos origin = fixture.center.getPos().getWorldPosition();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < 129; y++) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(fixture.region.getBlockState(origin.offset(x, y, z)).getBlock());
            if (id.getPath().equals(suffix) || id.getPath().equals("deepslate_" + suffix)) found++;
        }
        return found;
    }

    @GameTest(template = "empty")
    public static void commonSettingsPublishOneSnapshotAndSurviveTheActualNetworkCodec(GameTestHelper helper) {
        try (var ignored = new Settings()) {
            var before = MiteConfig.localSnapshot();
            MiteConfig.UNDERWORLD_ORES.get(R196OreRules.Kind.DIAMOND).set(2.25D);
            MiteConfig.MONSTER_GROWTH.set(1.75D);
            MiteConfig.HEIGHT_REACH_PENALTY.set(false);
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(List.of("minecraft:sharpness=2", "missing_mod:preserved=7"));
            MiteConfig.DISABLED_ENCHANTMENTS.set(List.of("minecraft:sharpness", "missing_mod:preserved"));
            helper.assertTrue(MiteConfig.localSnapshot() == before,
                    "Mutable editor values cannot leak a partially changed revision to generation workers");
            MiteConfig.refreshEnchantmentRules();
            var published = MiteConfig.localSnapshot();
            close(helper, published.ores().get("DIAMOND"), 2.25D, "Published underworld diamond rate");
            close(helper, MiteConfig.monsterGrowthMultiplier(), 1.75D, "Published daily monster growth");
            helper.assertFalse(published.heightPenalty(), "The same revision includes its combat flag");
            helper.assertValueEqual(published.caps().get("missing_mod:preserved"), 7,
                    "Temporarily absent registry entries retain their overrides");

            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(bytes, new GameplayConfigPayload(published));
                var decoded = GameplayConfigPayload.CODEC.decode(bytes);
                helper.assertValueEqual(decoded.snapshot(), published,
                        "The registered stream codec preserves every gameplay field and registry override");
                helper.assertValueEqual(bytes.readableBytes(), 0, "One snapshot consumes its entire packet");
            } finally {
                bytes.release();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remoteSnapshotsOverrideRuntimeRulesAndDisconnectRestoresLocalSettings(GameTestHelper helper) {
        var local = MiteConfig.localSnapshot();
        var remote = new MiteConfig.Snapshot(Map.of("DIAMOND", 0D), 3D, 2D, 0.5D,
                2D, 0.25D, false, false, true, Map.of("minecraft:sharpness", 2),
                Set.of("minecraft:sharpness", "missing_mod:preserved"));
        try {
            MiteConfig.receiveServerSnapshot(remote);
            helper.assertTrue(MiteConfig.effectiveSnapshot() == remote,
                    "The remote server publishes one authoritative client view");
            helper.assertTrue(MiteConfig.localSnapshot() == local,
                    "Receiving a remote view never replaces local common settings");
            close(helper, MiteConfig.underworldOreMultiplier(R196OreRules.Kind.DIAMOND), 0D, "Remote ore disabling");
            close(helper, MiteConfig.monsterGrowthMultiplier(), 3D, "Remote overall monster growth");
            close(helper, MiteConfig.monsterHealthGrowthMultiplier(), 2D, "Remote health growth");
            close(helper, MiteConfig.monsterDamageGrowthMultiplier(), 0.5D, "Remote attack growth");
            close(helper, MiteConfig.weaponReachMultiplier(), 2D, "Remote melee reach multiplier");
            close(helper, MiteConfig.weaponReachReduction(), 0.25D, "Remote melee reach reduction");
            helper.assertFalse(MiteConfig.heightReachPenaltyEnabled(), "Remote height adjustment flag");
            helper.assertFalse(MiteConfig.underworldTrialsEnabled(), "Remote underworld trial switch");
            helper.assertTrue(MiteConfig.overworldTrialsEnabled(), "Remote overworld trial switch");
            helper.assertValueEqual(MiteConfig.enchantmentMaxLevel("minecraft:sharpness", 5), 2, "Remote enchantment cap");
            helper.assertFalse(MiteConfig.enchantmentEnabled("minecraft:sharpness"), "Remote disabled enchantment");
            MiteConfig.clearServerSnapshot();
            helper.assertTrue(MiteConfig.effectiveSnapshot() == local,
                    "Disconnect immediately restores the pre-existing local revision");
            close(helper, MiteConfig.weaponReachMultiplier(), local.reachMultiplier(), "Restored local reach");
            helper.assertTrue(MiteConfig.enchantmentEnabled("minecraft:sharpness")
                            == !local.disabled().contains("minecraft:sharpness"),
                    "Remote disablement cannot leak into the next local game");
        } finally {
            MiteConfig.clearServerSnapshot();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualOreSettingsMigrateLegacyMultipliersAndSurviveTheNetworkCodec(GameTestHelper helper) {
        try (var ignored = new Settings()) {
            for (var kind : R196OreRules.UNDERWORLD) {
                MiteConfig.UNDERWORLD_ORES.get(kind).set(1D);
                MiteConfig.UNDERWORLD_ORE_CANDIDATES.get(kind).set((double)R196OreRules.candidateCount(kind, R196OreRules.Dimension.UNDERWORLD));
                MiteConfig.UNDERWORLD_ORE_CHANCES.get(kind).set(100D * R196OreRules.candidateChance(R196OreRules.Dimension.UNDERWORLD));
            }
            MiteConfig.refreshEnchantmentRules();
            close(helper, MiteConfig.underworldOreCandidates(R196OreRules.Kind.COPPER), 320D, "Copper exposes actual candidates rather than a multiplier");
            close(helper, MiteConfig.underworldOreCandidates(R196OreRules.Kind.IRON), 480D, "Iron exposes its distinct actual candidates");
            close(helper, MiteConfig.underworldOrePassChance(R196OreRules.Kind.COPPER), .1D, "The source probability is a ten-percent pass filter");
            close(helper, MiteConfig.underworldOreCandidates(R196OreRules.Kind.COPPER) * MiteConfig.underworldOrePassChance(R196OreRules.Kind.COPPER),
                    32D, "Default copper has 32 expected vein attempts per chunk");
            close(helper, MiteConfig.underworldOreCandidates(R196OreRules.Kind.IRON) * MiteConfig.underworldOrePassChance(R196OreRules.Kind.IRON),
                    48D, "Default iron has 48 expected vein attempts per chunk");

            MiteConfig.UNDERWORLD_ORES.get(R196OreRules.Kind.COPPER).set(2.25D);
            MiteConfig.refreshEnchantmentRules();
            close(helper, MiteConfig.underworldOreCandidates(R196OreRules.Kind.COPPER), 720D, "An existing multiplier is reflected in actual candidates");
            Map<R196OreRules.Kind, Double> candidates = new EnumMap<>(R196OreRules.Kind.class);
            Map<R196OreRules.Kind, Double> chancePercent = new EnumMap<>(R196OreRules.Kind.class);
            for (var kind : R196OreRules.configurableKinds(R196OreRules.Dimension.UNDERWORLD)) {
                candidates.put(kind, MiteConfig.underworldOreCandidates(kind));
                chancePercent.put(kind, 100D * MiteConfig.underworldOrePassChance(kind));
            }
            candidates.put(R196OreRules.Kind.IRON, 123.5D);
            chancePercent.put(R196OreRules.Kind.COPPER, 37.5D);
            chancePercent.put(R196OreRules.Kind.IRON, 25D);
            String customFormula = "base + reference * rate * days + 0";
            String healthBloodMoonFormula = "base + reference * rate * days + 1";
            String damageBloodMoonFormula = "base + reference * rate * days + 2";
            var growth = new MonsterGrowthRules.Settings(31D, 5D, 27D, .0075D, .005D, customFormula, customFormula,
                    healthBloodMoonFormula, damageBloodMoonFormula);
            helper.assertTrue(MiteConfig.saveMonsterGrowth(growth).valid(), "An executable custom formula and its actual bases and rates can be saved");
            helper.assertTrue(MiteConfig.saveUnderworldOreGeneration(candidates, chancePercent), "Valid actual generation settings can be saved together");
            close(helper, MiteConfig.underworldOreCandidates(R196OreRules.Kind.COPPER), 720D, "Migrating the old copper multiplier preserves its actual candidate count");
            close(helper, MiteConfig.underworldOreMultiplier(R196OreRules.Kind.COPPER), 1D, "Saving actual candidates normalizes the legacy multiplier");
            close(helper, MiteConfig.underworldOreCandidates(R196OreRules.Kind.IRON), 123.5D, "Fractional actual candidates remain available to generation");
            close(helper, MiteConfig.underworldOrePassChance(R196OreRules.Kind.IRON), .25D, "The saved percent becomes a runtime fraction");
            var published = MiteConfig.localSnapshot();
            close(helper, published.oreCandidates().get("COPPER"), 720D, "The immutable snapshot carries the migrated actual count");
            close(helper, published.oreCandidates().get("IRON"), 123.5D, "The immutable snapshot carries custom fractional counts");
            close(helper, published.oreChances().get("COPPER"), .375D, "The immutable snapshot carries custom probabilities");
            close(helper, published.monsterSettings().healthBase(), 31D, "The published monster revision carries the configured health base");
            close(helper, published.monsterSettings().damageRate(), .005D, "The published monster revision converts daily percentages to formula fractions");
            helper.assertValueEqual(published.monsterSettings().healthFormula(), customFormula, "The published monster revision retains the custom formula");
            helper.assertValueEqual(published.monsterSettings().healthBloodMoonFormula(), healthBloodMoonFormula,
                    "Health baseline settlement is published independently of the period expression");
            helper.assertValueEqual(published.monsterSettings().damageBloodMoonFormula(), damageBloodMoonFormula,
                    "Attack baseline settlement is published independently of the period expression");
            helper.assertValueEqual(MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.get(), healthBloodMoonFormula,
                    "Saving writes the explicit health settlement expression to the common config");
            helper.assertValueEqual(MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.get(), damageBloodMoonFormula,
                    "Saving writes the explicit attack settlement expression to the common config");
            var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                GameplayConfigPayload.CODEC.encode(bytes, new GameplayConfigPayload(published));
                var decoded = GameplayConfigPayload.CODEC.decode(bytes).snapshot();
                helper.assertValueEqual(decoded, published, "The real packet codec preserves the added generation maps and monster settings");
                helper.assertValueEqual(bytes.readableBytes(), 0, "One expanded snapshot consumes its entire packet");
                MiteConfig.receiveServerSnapshot(decoded);
                close(helper, MiteConfig.underworldOreCandidates(R196OreRules.Kind.COPPER), 720D, "Remote runtime sees actual copper candidates without multiplying twice");
                close(helper, MiteConfig.underworldOreCandidates(R196OreRules.Kind.IRON), 123.5D, "Remote runtime sees fractional iron candidates");
                close(helper, MiteConfig.underworldOrePassChance(R196OreRules.Kind.COPPER), .375D, "Remote runtime sees custom copper probability");
                helper.assertValueEqual(MiteConfig.monsterGrowthSettings().healthBloodMoonFormula(), healthBloodMoonFormula,
                        "Remote formula previews use the server's independent health settlement expression");
                helper.assertValueEqual(MiteConfig.monsterGrowthSettings().damageBloodMoonFormula(), damageBloodMoonFormula,
                        "Remote formula previews use the server's independent attack settlement expression");
            } finally {
                bytes.release();
                MiteConfig.clearServerSnapshot();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void omittedBloodMoonFormulasInheritExistingCustomPeriodExpressions(GameTestHelper helper) {
        try (var ignored = new Settings()) {
            String healthFormula = "base + reference * rate * days * 2";
            String damageFormula = "base + reference * rate * days * 3";
            MiteConfig.MONSTER_HEALTH_BASE.set(0D);
            MiteConfig.MONSTER_DAMAGE_BASE.set(0D);
            MiteConfig.MONSTER_SKELETON_REFERENCE.set(20D);
            MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.set(.5D);
            MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT.set(.25D);
            MiteConfig.MONSTER_HEALTH_FORMULA.set(healthFormula);
            MiteConfig.MONSTER_DAMAGE_FORMULA.set(damageFormula);
            MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.set("");
            MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set("  ");
            MiteConfig.refreshEnchantmentRules();
            var published = MiteConfig.localSnapshot().monsterSettings();
            helper.assertValueEqual(published.healthBloodMoonFormula(), healthFormula,
                    "An old custom health formula remains the blood-moon calculation when the new config entry is empty");
            helper.assertValueEqual(published.damageBloodMoonFormula(), damageFormula,
                    "A blank attack settlement field inherits the existing custom attack expression");
            var legacy = new MonsterGrowthRules.Settings(0D, 0D, 20D, .005D, .0025D, healthFormula, damageFormula);
            close(helper, MonsterGrowthRules.health(65L, 20D, false, published, 1D),
                    MonsterGrowthRules.health(65L, 20D, false, legacy, 1D),
                    "Loading an old file preserves health through multiple blood-moon settlements");
            close(helper, MonsterGrowthRules.damage(65L, 3D, published, 1D),
                    MonsterGrowthRules.damage(65L, 3D, legacy, 1D),
                    "Loading an old file preserves attack through multiple blood-moon settlements");
            helper.assertValueEqual(MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.get(), "",
                    "Publishing the inherited formula does not overwrite the file's inheritance setting");
            helper.assertValueEqual(MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.get(), "  ",
                    "Whitespace inheritance is interpreted without rewriting the source value");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidGrowthCannotSaveOrReplaceTheLastValidPublishedSettings(GameTestHelper helper) {
        try (var ignored = new Settings()) {
            var before = MiteConfig.localSnapshot();
            var beforeValues = monsterEditorValues();
            var invalid = new MonsterGrowthRules.Settings(19D, 4D, 30D, .02D, .01D,
                    "base / (days - days)", MonsterGrowthRules.DEFAULT_FORMULA);
            var rejected = MiteConfig.saveMonsterGrowth(invalid);
            helper.assertFalse(rejected.valid(), "A syntactically valid division-by-zero formula fails actual execution validation");
            helper.assertTrue(MiteConfig.localSnapshot() == before, "Rejected save cannot publish even a partial snapshot");
            helper.assertValueEqual(monsterEditorValues(), beforeValues, "Rejected save cannot mutate any numeric or formula editor field");
            var invalidSettlement = new MonsterGrowthRules.Settings(19D, 4D, 30D, .02D, .01D,
                    MonsterGrowthRules.DEFAULT_FORMULA, MonsterGrowthRules.DEFAULT_FORMULA,
                    "base / (days - days)", MonsterGrowthRules.DEFAULT_FORMULA);
            helper.assertFalse(MiteConfig.saveMonsterGrowth(invalidSettlement).valid(),
                    "A blood-moon expression must execute successfully before any settings are saved");
            helper.assertTrue(MiteConfig.localSnapshot() == before, "Rejected settlement cannot publish a partial snapshot");
            helper.assertValueEqual(monsterEditorValues(), beforeValues, "Rejected settlement cannot mutate any editor field");

            MiteConfig.MONSTER_HEALTH_BASE.set(99D);
            MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.set(1.5D);
            MiteConfig.MONSTER_HEALTH_FORMULA.set("base / (days - days)");
            MiteConfig.refreshEnchantmentRules();
            helper.assertValueEqual(MiteConfig.localSnapshot().monsterSettings(), before.monsterSettings(),
                    "Reloading an executable-but-invalid formula retains the entire last valid growth revision");
            helper.assertValueEqual(MiteConfig.monsterGrowthSettings(), before.monsterSettings(),
                    "Runtime consumers keep the last successful bases, rates and formulas after a bad file reload");
            MiteConfig.MONSTER_HEALTH_FORMULA.set(MonsterGrowthRules.DEFAULT_FORMULA);
            MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.set("base / (days - days)");
            MiteConfig.refreshEnchantmentRules();
            helper.assertValueEqual(MiteConfig.localSnapshot().monsterSettings(), before.monsterSettings(),
                    "A failing blood-moon settlement from a file reload also retains the entire valid revision");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commonSettingsReachActualMonsterScalingAndPlayerMeleeCalculations(GameTestHelper helper) {
        try (var ignored = new Settings()) {
            var monster = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(monster != null, "The native zombie fixture can be constructed");
            monster.setNoAi(true);
            var progress = monster.getPersistentData().getCompound(MonsterDayScaling.DATA_KEY);
            progress.putLong("day", 33L);
            monster.getPersistentData().put(MonsterDayScaling.DATA_KEY, progress);
            MiteConfig.MONSTER_GROWTH.set(0D);
            MiteConfig.MONSTER_HEALTH_GROWTH.set(3D);
            MiteConfig.MONSTER_DAMAGE_GROWTH.set(4D);
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(2D);
            MiteConfig.WEAPON_REACH_REDUCTION.set(0.25D);
            MiteConfig.HEIGHT_REACH_PENALTY.set(true);
            MiteConfig.refreshEnchantmentRules();
            MonsterDayScaling.refresh(monster);
            close(helper, monster.getMaxHealth(), 20D, "Zero overall growth disables health growth in a real monster");
            close(helper, MonsterDayScaling.attackMultiplier(monster), 1D, "Zero overall growth disables attributed attack growth");
            MiteConfig.MONSTER_GROWTH.set(2D);
            MiteConfig.refreshEnchantmentRules();
            MonsterDayScaling.refresh(monster);
            long day = monster.getPersistentData().getCompound(MonsterDayScaling.DATA_KEY).getLong("day");
            close(helper, monster.getMaxHealth(), Math.min(1024D, 20D * MonsterDayScalingRules.healthMultiplier(day, 6D)),
                    "Overall and health growth multiply in the runtime attribute update");
            close(helper, MonsterDayScaling.attackMultiplier(monster), 1D,
                    "Legacy damage growth multipliers cannot restore calendar damage growth");

            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var position = helper.absolutePos(new BlockPos(2, 3, 2));
            player.setPos(position.getX(), position.getY(), position.getZ());
            monster.setPos(player.getX() + 1D, player.getY(), player.getZ());
            close(helper, SourcePlayerReach.meleeReach(player, monster), 2.75D,
                    "The real survival reach applies its configured multiplier and subtraction");
            player.setPos(player.getX(), player.getY() + 2.5D, player.getZ());
            close(helper, SourcePlayerReach.meleeReach(player, monster), 4.75D, "Configured reach includes the enabled height adjustment");
            MiteConfig.HEIGHT_REACH_PENALTY.set(false);
            MiteConfig.refreshEnchantmentRules();
            close(helper, SourcePlayerReach.meleeReach(player, monster), 2.75D, "Disabling height adjustment reaches the actual player calculation");
            player.getAbilities().instabuild = true;
            close(helper, SourcePlayerReach.meleeReach(player, monster), 5D, "Creative reach retains its vanilla-style fixed range");
            monster.discard();
        }
        helper.succeed();
    }

    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < 1E-4D, label + ": " + actual + " vs " + expected);
    }

    private static List<Object> monsterEditorValues() {
        return List.of(MiteConfig.MONSTER_HEALTH_BASE.get(), MiteConfig.MONSTER_DAMAGE_BASE.get(), MiteConfig.MONSTER_SKELETON_REFERENCE.get(),
                MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.get(), MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT.get(),
                MiteConfig.MONSTER_HEALTH_FORMULA.get(), MiteConfig.MONSTER_DAMAGE_FORMULA.get(),
                MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.get(), MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.get());
    }

    private static final class Settings implements AutoCloseable {
        private final MiteConfig.Snapshot original = MiteConfig.localSnapshot();
        private final List<? extends String> caps = MiteConfig.ENCHANTMENT_MAX_LEVELS.get();
        private final List<? extends String> disabled = MiteConfig.DISABLED_ENCHANTMENTS.get();
        private final Map<R196OreRules.Kind, Double> candidates = new EnumMap<>(R196OreRules.Kind.class);
        private final Map<R196OreRules.Kind, Double> chances = new EnumMap<>(R196OreRules.Kind.class);
        private final Map<R196OreRules.Kind, Double> surfaceCandidates = new EnumMap<>(R196OreRules.Kind.class);
        private final Map<R196OreRules.Kind, Double> surfaceChances = new EnumMap<>(R196OreRules.Kind.class);
        private final Map<R196OreRules.Kind, Double> surfaceMultipliers = new EnumMap<>(R196OreRules.Kind.class);
        private final double healthBase = MiteConfig.MONSTER_HEALTH_BASE.get(), damageBase = MiteConfig.MONSTER_DAMAGE_BASE.get();
        private final double skeletonReference = MiteConfig.MONSTER_SKELETON_REFERENCE.get();
        private final double healthPercent = MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.get(), damagePercent = MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT.get();
        private final String healthFormula = MiteConfig.MONSTER_HEALTH_FORMULA.get(), damageFormula = MiteConfig.MONSTER_DAMAGE_FORMULA.get();
        private final String healthBloodMoonFormula = MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.get();
        private final String damageBloodMoonFormula = MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.get();

        private Settings() {
            // These legacy multiplier tests explicitly select inherited deep rates; the new preset has independent rates.
            MiteConfig.OVERWORLD_DEEPSLATE_ORES.values().forEach(setting -> setting.set(-1D));
            MiteConfig.UNDERWORLD_DEEPSLATE_ORES.values().forEach(setting -> setting.set(-1D));
            MiteConfig.OVERWORLD_ORES.forEach((kind, setting) -> surfaceMultipliers.put(kind, setting.get()));
            MiteConfig.OVERWORLD_ORE_CANDIDATES.forEach((kind, setting) -> surfaceCandidates.put(kind, setting.get()));
            MiteConfig.OVERWORLD_ORE_CHANCES.forEach((kind, setting) -> surfaceChances.put(kind, setting.get()));
            MiteConfig.UNDERWORLD_ORE_CANDIDATES.forEach((kind, setting) -> candidates.put(kind, setting.get()));
            MiteConfig.UNDERWORLD_ORE_CHANCES.forEach((kind, setting) -> chances.put(kind, setting.get()));
        }

        @Override public void close() {
            MiteConfig.OVERWORLD_DEEPSLATE_ORES.forEach((kind, setting) -> setting.set(original.overworldOres().get(R196OreRules.deepslateMultiplierKey(kind))));
            MiteConfig.UNDERWORLD_DEEPSLATE_ORES.forEach((kind, setting) -> setting.set(original.ores().get(R196OreRules.deepslateMultiplierKey(kind))));
            MiteConfig.OVERWORLD_ORES.forEach((kind, setting) -> setting.set(surfaceMultipliers.get(kind)));
            MiteConfig.OVERWORLD_ORE_CANDIDATES.forEach((kind, setting) -> setting.set(surfaceCandidates.get(kind)));
            MiteConfig.OVERWORLD_ORE_CHANCES.forEach((kind, setting) -> setting.set(surfaceChances.get(kind)));
            MiteConfig.UNDERWORLD_ORES.forEach((kind, setting) -> setting.set(original.ores().get(kind.name())));
            MiteConfig.UNDERWORLD_ORE_CANDIDATES.forEach((kind, setting) -> setting.set(candidates.get(kind)));
            MiteConfig.UNDERWORLD_ORE_CHANCES.forEach((kind, setting) -> setting.set(chances.get(kind)));
            MiteConfig.MONSTER_GROWTH.set(original.growth());
            MiteConfig.MONSTER_HEALTH_GROWTH.set(original.healthGrowth());
            MiteConfig.MONSTER_DAMAGE_GROWTH.set(original.damageGrowth());
            MiteConfig.MONSTER_HEALTH_BASE.set(healthBase);
            MiteConfig.MONSTER_DAMAGE_BASE.set(damageBase);
            MiteConfig.MONSTER_SKELETON_REFERENCE.set(skeletonReference);
            MiteConfig.MONSTER_HEALTH_DAILY_PERCENT.set(healthPercent);
            MiteConfig.MONSTER_DAMAGE_DAILY_PERCENT.set(damagePercent);
            MiteConfig.MONSTER_HEALTH_FORMULA.set(healthFormula);
            MiteConfig.MONSTER_DAMAGE_FORMULA.set(damageFormula);
            MiteConfig.MONSTER_HEALTH_BLOOD_MOON_FORMULA.set(healthBloodMoonFormula);
            MiteConfig.MONSTER_DAMAGE_BLOOD_MOON_FORMULA.set(damageBloodMoonFormula);
            MiteConfig.WEAPON_REACH_MULTIPLIER.set(original.reachMultiplier());
            MiteConfig.WEAPON_REACH_REDUCTION.set(original.reachReduction());
            MiteConfig.HEIGHT_REACH_PENALTY.set(original.heightPenalty());
            MiteConfig.UNDERWORLD_TRIALS.set(original.underworldTrials());
            MiteConfig.OVERWORLD_TRIALS.set(original.overworldTrials());
            MiteConfig.ENCHANTMENT_MAX_LEVELS.set(caps);
            MiteConfig.DISABLED_ENCHANTMENTS.set(disabled);
            MiteConfig.clearServerSnapshot();
            MiteConfig.refreshEnchantmentRules();
            MiteConfig.SPEC.save();
        }
    }
}
