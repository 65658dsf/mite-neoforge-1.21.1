package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual source pools, final identities and natural-only admission for saved rates. @author NingMeng123 */
@GameTestHolder("mite_config")
@PrefixGameTestTemplate(false)
public final class MonsterSpawnRuntimeGameTests {
    private MonsterSpawnRuntimeGameTests() {}

    @GameTest(template = "empty")
    public static void absoluteWeightsReplaceEachPoolsNativeWeightsAndStillMultiplyRates(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            settings.rates(List.of());
            settings.weights(List.of("minecraft:silverfish=0.25", "minecraft:spider=0.75"));
            var firstPool = List.of(new R196MobSpawning.Entry(EntityType.SILVERFISH, "tq", Category.MONSTER, 1000, 1, 1, 1, false),
                    new R196MobSpawning.Entry(EntityType.SPIDER, "tt", Category.MONSTER, 1, 1, 1, 1, false));
            var secondPool = List.of(new R196MobSpawning.Entry(EntityType.SILVERFISH, "tq", Category.MONSTER, 3, 1, 1, 1, false),
                    new R196MobSpawning.Entry(EntityType.SPIDER, "tt", Category.MONSTER, 400, 1, 1, 1, false));
            var firstRandom = RandomSource.create(538297L); var secondRandom = RandomSource.create(538297L);
            int silverfish = 0;
            for (int draw = 0; draw < 4000; draw++) {
                var first = ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER, firstPool, firstRandom, true);
                var second = ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER, secondPool, secondRandom, true);
                helper.assertTrue(first != null && second != null && first.type() == second.type(),
                        "The same absolute overrides replace both pools' different native weights");
                if (first.type() == EntityType.SILVERFISH) silverfish++;
            }
            helper.assertTrue(silverfish > 850 && silverfish < 1150, "Sub-unit weights0.25:0.75 produce a quarter pool share: " + silverfish);
            helper.assertValueEqual(firstPool.getFirst().weight(), 1000, "Selecting with overrides preserves original source metadata");
            helper.assertValueEqual(ConfiguredMonsterSpawns.weight(EntityType.SILVERFISH, 1000D), .25D, "Runtime resolution retains a positive sub-unit direct override exactly");
            helper.assertValueEqual(ConfiguredMonsterSpawns.weight(EntityType.SPIDER, 1D), .75D, "Neither fractional candidate is rounded into zero or its native fallback");
            for (double draw : new double[]{0D, Math.nextDown(.25D)})
                helper.assertTrue(ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER, firstPool,
                        fixedWeightDraw(draw), true) == firstPool.getFirst(), "Every exact draw below the quarter boundary selects the fractional first candidate: "+draw);
            for (double draw : new double[]{.25D, Math.nextDown(1D)})
                helper.assertTrue(ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER, firstPool,
                        fixedWeightDraw(draw), true) == firstPool.getLast(), "The exact quarter boundary and final interval select the other fractional candidate: "+draw);
            settings.rates(List.of("minecraft:silverfish=3"));
            silverfish = 0;
            var multiplied = RandomSource.create(934264L);
            for (int draw = 0; draw < 4000; draw++)
                if (ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER, firstPool, multiplied, true).type()
                        == EntityType.SILVERFISH) silverfish++;
            helper.assertTrue(silverfish > 1850 && silverfish < 2150, "Saved rate3 multiplies direct weight0.25 into the expected0.75:0.75 share: " + silverfish);
            helper.assertTrue(ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER, firstPool,
                    fixedWeightDraw(Math.nextDown(.5D)), true) == firstPool.getFirst(), "A draw just below the multiplied half boundary still selects the first candidate");
            helper.assertTrue(ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER, firstPool,
                    fixedWeightDraw(.5D), true) == firstPool.getLast(), "The exact multiplied half boundary selects the second candidate without integer quantization");
        }
        helper.succeed();
    }

    private static RandomSource fixedWeightDraw(double draw) {
        return new LegacyRandomSource(0L) {
            @Override public double nextDouble() { return draw; }
            @Override public int nextInt(int bound) { throw new AssertionError("Fractional configured weights must use the double lottery, not nextInt"); }
        };
    }

    @GameTest(template = "empty")
    public static void overworldBruteFollowsTheIteLordsEffectiveNaturalWeight(GameTestHelper helper) {
        var lordId = ResourceLocation.fromNamespaceAndPath("modern_ite", "zombie_lord");
        var lordType = BuiltInRegistries.ENTITY_TYPE.getOptional(lordId);
        if (lordType.isEmpty()) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);
        var brute = new R196MobSpawning.Entry(EntityType.PIGLIN_BRUTE, null, Category.MONSTER, 2, 1, 1, 1, true);
        var control = new R196MobSpawning.Entry(EntityType.PILLAGER, null, Category.MONSTER, 5, 1, 1, 1, true);
        try (var settings = new Settings(helper)) {
            level.setDayTime(223L * 24_000L + 18_000L);
            level.updateSkyBrightness();
            BloodMoonInvasionData.get(level).restoreProgress(224L);
            settings.rates(List.of());
            settings.weights(List.of("minecraft:pillager=5"));
            settings.ite.snapshot(Map.of(lordId.toString(), 3D), Map.of(lordId.toString(), 2.5D));
            var context = new MiteExtensions.SpawnContext(level, Category.MONSTER, null);
            var lord = MiteExtensions.policy().spawnPool(context, pos, List.of(brute)).stream()
                    .filter(entry -> entry.type() == lordType.get()).findFirst().orElseThrow();
            var entries = List.of(brute, lord, control);
            helper.assertTrue(ConfiguredMonsterSpawns.choose(level, pos, Category.MONSTER, entries,
                    fixedWeightDraw(Math.nextDown(.375D)), true) == brute, "The brute receives the same effective 7.5 weight as the ITE lord");
            helper.assertTrue(ConfiguredMonsterSpawns.choose(level, pos, Category.MONSTER, entries,
                    fixedWeightDraw(.375D), true) == lord, "The ITE lord begins exactly at its equal selection boundary");
            helper.assertTrue(ConfiguredMonsterSpawns.choose(level, pos, Category.MONSTER, entries,
                    fixedWeightDraw(.75D), true) == control, "The two equal weights together occupy 15 of 20 selection units");
            helper.assertTrue(ConfiguredMonsterSpawns.choose(level, pos, Category.MONSTER, List.of(brute),
                    fixedWeightDraw(0D), true) == null, "When the ITE lord is unavailable at a position, its brute counterpart is absent");

            settings.ite.snapshot(Map.of(lordId.toString(), 3D), Map.of(lordId.toString(), 0D));
            helper.assertTrue(ConfiguredMonsterSpawns.choose(level, pos, Category.MONSTER, entries,
                    fixedWeightDraw(0D), true) == control, "An ITE zero-weight override disables the brute counterpart too");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroCandidateWeightsCloseFamiliesWithoutCreatingMissingNaturalEntries(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            settings.rates(List.of("minecraft:creeper=0", "mite:infernal_creeper=1"));
            var level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            var entry = new R196MobSpawning.Entry(EntityType.CREEPER, "tf", Category.MONSTER, 100, 1, 1, 1, false);
            var random = RandomSource.create(93254L);
            helper.assertTrue(ConfiguredMonsterSpawns.choose(level, new BlockPos(0, -32, 0), Category.MONSTER,
                    List.of(entry), random, true) == entry, "An enabled variant keeps its parent candidate while only the parent rate is zero");
            settings.weights(List.of("minecraft:creeper=0", "minecraft:zombie=1000000"));
            var emptyRandom = RandomSource.create(81235L); var unchanged = RandomSource.create(81235L);
            helper.assertTrue(ConfiguredMonsterSpawns.choose(level, new BlockPos(0, -32, 0), Category.MONSTER,
                    List.of(entry), emptyRandom, true) == null, "Zero candidate weight closes that entire family despite an enabled replacement rate");
            helper.assertTrue(ConfiguredMonsterSpawns.choose(level, BlockPos.ZERO, Category.MONSTER,
                    List.of(), emptyRandom, true) == null, "A positive saved weight cannot invent an absent natural pool entry");
            helper.assertValueEqual(emptyRandom.nextLong(), unchanged.nextLong(), "Zero and empty pools do not consume invalid lottery draws");
            helper.assertValueEqual(ConfiguredMonsterSpawns.multiplier(MiteMobs.type(MobKind.INFERNAL_CREEPER)), 1D,
                    "Candidate overrides preserve independent final-species rate semantics");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitNativeWeightsRetainTheExistingIntegerRandomStream(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            settings.rates(List.of());
            settings.weights(List.of("minecraft:silverfish=40", "minecraft:spider=20"));
            var entries = List.of(new R196MobSpawning.Entry(EntityType.SILVERFISH, "tq", Category.MONSTER, 40, 1, 1, 1, false),
                    new R196MobSpawning.Entry(EntityType.SPIDER, "tt", Category.MONSTER, 20, 1, 1, 1, false));
            var configured = RandomSource.create(78345L); var original = RandomSource.create(78345L);
            for (int draw = 0; draw < 256; draw++) {
                var expected = original.nextInt(60) < 40 ? entries.getFirst() : entries.getLast();
                helper.assertTrue(ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER,
                        entries, configured, true) == expected, "An override equal to the original weight retains the native integer lottery");
            }
            helper.assertValueEqual(configured.nextLong(), original.nextLong(), "Unchanged explicit weights introduce no additional random draws");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void defaultPoolUsesExactlyTheOriginalIntegerLottery(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            settings.rates(List.of());
            var entries = R196MobSpawning.sourceProfile(1, Category.MONSTER);
            var configured = RandomSource.create(106789L); var original = RandomSource.create(106789L);
            int total = entries.stream().mapToInt(R196MobSpawning.Entry::weight).sum();
            for (int trial = 0; trial < 256; trial++) {
                int draw = original.nextInt(total); R196MobSpawning.Entry expected = null;
                for (var entry : entries) { draw -= entry.weight(); if (draw < 0) { expected = entry; break; } }
                helper.assertTrue(ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER,
                        entries, configured, true) == expected, "Default interval and random stream match the original integer lottery");
            }
            helper.assertValueEqual(configured.nextLong(), original.nextLong(), "No hidden default random draws");
            helper.assertTrue(R196MobSpawning.sourceEntries().stream().anyMatch(entry -> entry.type() == EntityType.BLAZE
                    && entry.weight() == 100), "Metadata includes the authored fortress override");
            helper.assertTrue(R196MobSpawning.sourceEntries().stream().anyMatch(entry -> entry.type() == MiteMobs.type(MobKind.VAMPIRE_BAT)
                    && entry.category() == Category.AMBIENT), "Metadata retains hostile ambient identities");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void increasedSavedRateChangesActualSelectionAndEmptyPoolsAreSafe(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            var entries = List.of(new R196MobSpawning.Entry(EntityType.SILVERFISH, "tq", Category.MONSTER, 1, 1, 1, 1, false),
                    new R196MobSpawning.Entry(EntityType.SPIDER, "tt", Category.MONSTER, 1, 1, 1, 1, false));
            settings.rates(List.of("minecraft:silverfish=3", "minecraft:spider=1"));
            int silverfish = 0; var random = RandomSource.create(762456L);
            for (int draw = 0; draw < 4000; draw++)
                if (ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER, entries, random, true).type()
                        == EntityType.SILVERFISH) silverfish++;
            helper.assertTrue(silverfish > 2800 && silverfish < 3200, "Threefold rate produces the expected 3:1 pool share: " + silverfish);
            settings.rates(List.of("minecraft:silverfish=0", "minecraft:spider=0"));
            helper.assertTrue(ConfiguredMonsterSpawns.choose(helper.getLevel(), BlockPos.ZERO, Category.MONSTER,
                    entries, random, true) == null, "All-zero pool does not draw nextInt(0) or select disabled entries");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fractionalInfernalRateSurvivesDisabledCreeperAndSourceSelection(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            List<String> rates = allDisabled(); rates.add("mite:infernal_creeper=0.00000001"); settings.rates(rates);
            var level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(level != null, "Underworld is registered");
            var selected = R196MobSpawning.select(level, new BlockPos(0, -32, 0), Category.MONSTER,
                    RandomSource.create(9345L), false, true);
            helper.assertTrue(selected != null && selected.type() == MiteMobs.type(MobKind.INFERNAL_CREEPER),
                    "Tiny positive variant weight remains selectable while the parent rate is zero");
            settings.rates(allDisabled());
            helper.assertTrue(R196MobSpawning.select(level, new BlockPos(0, -32, 0), Category.MONSTER,
                    RandomSource.create(9345L), false, true) == null, "Actual select safely handles a disabled source pool");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void enabledNetherWitherIdentitySurvivesDisabledOrdinarySkeleton(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            List<String> rates = allDisabled(); rates.add("minecraft:wither_skeleton=1"); settings.rates(rates);
            ServerLevel level = helper.getLevel().getServer().getLevel(Level.NETHER);
            // Ordinary Nether biomes deliberately omit tr; its natural entry is a fortress override.
            var fortressSkeleton = R196MobSpawning.sourceEntries().stream().filter(entry -> entry.type() == EntityType.SKELETON
                    && entry.weight() == 100 && entry.minimum() == 4 && entry.maximum() == 4).findFirst().orElseThrow();
            var selected = ConfiguredMonsterSpawns.choose(level, new BlockPos(0, 40, 0), Category.MONSTER,
                    List.of(fortressSkeleton), RandomSource.create(234L), true);
            helper.assertTrue(selected != null && selected.type() == EntityType.SKELETON,
                    "Final wither rate retains the disabled parent family");
            Mob original = selected.type().create(level);
            var result = R196MobSpawning.prepareSourceFinalization(original, level, null, MobSpawnType.NATURAL);
            helper.assertTrue(result.mob().getType() == EntityType.WITHER_SKELETON, "Actual source finalization reaches the configured terminal identity");
            result.mob().discard(); original.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void rareAmbientBatAndLongdeadReplacementsHaveIndependentSavedRates(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            var position = new BlockPos(0, -32, 0); var random = RandomSource.create(3491L);
            List<String> rates = allDisabled(); rates.add("mite:giant_vampire_bat=0.000001"); settings.rates(rates);
            var selected = R196MobSpawning.select(level, position, Category.AMBIENT, random, false, true);
            helper.assertTrue(selected != null && selected.type() == MiteMobs.type(MobKind.VAMPIRE_BAT),
                    "Tiny positive giant rate retains its disabled ambient parent family");
            helper.assertTrue(ConfiguredMonsterSpawns.multiplier(selected.type()) == 0D,
                    "The selected ambient parent still reads its zero rate, actual=" + ConfiguredMonsterSpawns.multiplier(selected.type()));
            helper.assertTrue(ConfiguredMonsterSpawns.multiplier(MiteMobs.type(MobKind.GIANT_VAMPIRE_BAT)) == .000001D,
                    "The enabled giant still reads its configured tiny rate");
            var giant = R196MobSpawning.substituteNatural(level, selected, random);
            helper.assertTrue(giant.type() == MiteMobs.type(MobKind.GIANT_VAMPIRE_BAT),
                    "Actual per-member source lottery produces enabled giant: actual=" + BuiltInRegistries.ENTITY_TYPE.getKey(giant.type())
                            + ", parentSource=" + selected.sourceClass() + ", underworld=" + R196MobSpawning.underworld(level));
            rates = allDisabled(); rates.add("mite:longdead_guardian=2"); settings.rates(rates);
            selected = R196MobSpawning.select(level, position, Category.MONSTER, random, false, true);
            helper.assertTrue(selected != null && selected.type() == MiteMobs.type(MobKind.LONGDEAD),
                    "Guardian controls its actual source-family selection independently");
            helper.assertTrue(R196MobSpawning.substituteNatural(level, selected, random).type() == MiteMobs.type(MobKind.LONGDEAD_GUARDIAN),
                    "Actual member replacement honors the configured guardian identity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zombieVillagerLotteryHonorsBothRatesAndSharesItsPackIdentity(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            settings.rates(List.of("minecraft:zombie=0", "minecraft:zombie_villager=1"));
            Mob first = EntityType.ZOMBIE.create(helper.getLevel());
            var result = R196MobSpawning.prepareSourceFinalization(first, helper.getLevel(), null, MobSpawnType.NATURAL);
            helper.assertTrue(result.mob().getType() == EntityType.ZOMBIE_VILLAGER, "Enabled child is not lost to disabled parent");
            Mob second = EntityType.ZOMBIE.create(helper.getLevel());
            var pack = R196MobSpawning.prepareSourceFinalization(second, helper.getLevel(), result.group(), MobSpawnType.NATURAL);
            helper.assertTrue(pack.mob().getType() == EntityType.ZOMBIE_VILLAGER, "The pack shares the first villager identity");
            result.mob().discard(); pack.mob().discard(); first.discard(); second.discard();
            settings.rates(List.of("minecraft:zombie=1", "minecraft:zombie_villager=0"));
            Mob ordinary = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(R196MobSpawning.prepareSourceFinalization(ordinary, helper.getLevel(), null, MobSpawnType.NATURAL).mob().getType()
                    == EntityType.ZOMBIE, "Disabled child has zero natural conversion chance");
            ordinary.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finalZeroRateBlocksNaturalIdentityAndKeepsExplicitSpawns(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            settings.rates(List.of("minecraft:zombie=0"));
            Mob natural = finalizedZombie(helper, MobSpawnType.NATURAL);
            helper.assertFalse(helper.getLevel().addFreshEntity(natural), "Final natural identity is blocked before world admission");
            natural.discard();
            for (MobSpawnType reason : List.of(MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND, MobSpawnType.SPAWNER)) {
                Mob explicit = finalizedZombie(helper, reason);
                try { helper.assertTrue(helper.getLevel().addFreshEntity(explicit), "Natural rate does not affect " + reason); }
                finally { explicit.discard(); }
            }
        }
        helper.succeed();
    }

    private static Mob finalizedZombie(GameTestHelper helper, MobSpawnType reason) {
        Mob mob = EntityType.ZOMBIE.create(helper.getLevel());
        mob.moveTo(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter()); mob.setNoAi(true);
        if (reason == MobSpawnType.COMMAND)
            mob.getPersistentData().putBoolean("modern_ite:explicit_command_spawn", true);
        EventHooks.finalizeMobSpawn(mob, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(mob.blockPosition()), reason, null);
        return mob;
    }

    @GameTest(template = "empty")
    public static void existingNaturalMobsKeepAdmissionAcrossReloadAndDimensionCopies(GameTestHelper helper) {
        try (var settings = new Settings(helper)) {
            settings.rates(List.of());
            Mob original = finalizedZombie(helper, MobSpawnType.NATURAL);
            try {
                helper.assertTrue(helper.getLevel().addFreshEntity(original), "A fresh default-rate natural spawn is admitted");
                helper.assertTrue(original.getPersistentData().getBoolean(ConfiguredMonsterSpawns.ADMITTED),
                        "Admission is recorded even at the default rate");
                settings.rates(List.of("minecraft:zombie=0"));
                CompoundTag state = new CompoundTag(); original.saveWithoutId(state);
                ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
                Mob transferred = EntityType.ZOMBIE.create(nether); transferred.load(state);
                try {
                    helper.assertTrue(transferred.getSpawnType() == MobSpawnType.NATURAL,
                            "NeoForge retains the original natural reason through a dimension copy");
                    var transfer = new EntityJoinLevelEvent(transferred, nether, false);
                    NeoForge.EVENT_BUS.post(transfer);
                    helper.assertFalse(transfer.isCanceled(), "A copied existing inhabitant survives a later zero natural rate");
                    transferred.getPersistentData().remove(ConfiguredMonsterSpawns.ADMITTED);
                    var loaded = new EntityJoinLevelEvent(transferred, nether, true);
                    NeoForge.EVENT_BUS.post(loaded);
                    helper.assertFalse(loaded.isCanceled(), "Older saved inhabitants remain loadable at a zero rate");
                    helper.assertTrue(transferred.getPersistentData().getBoolean(ConfiguredMonsterSpawns.ADMITTED),
                            "Disk load stamps the admission marker for its next dimension transfer");
                } finally { transferred.discard(); }
                Mob fresh = finalizedZombie(helper, MobSpawnType.NATURAL);
                try { helper.assertFalse(helper.getLevel().addFreshEntity(fresh), "A new natural zero-rate spawn is still blocked"); }
                finally { fresh.discard(); }
            } finally { original.discard(); }
        }
        helper.succeed();
    }

    private static List<String> allDisabled() {
        var result = new ArrayList<String>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) result.add(BuiltInRegistries.ENTITY_TYPE.getKey(type) + "=0");
        return result;
    }

    private static final class Settings implements AutoCloseable {
        private final List<? extends String> original = List.copyOf(MiteConfig.NATURAL_SPAWN_RATES.get());
        private final List<? extends String> originalWeights = List.copyOf(MiteConfig.NATURAL_SPAWN_WEIGHTS.get());
        private final MiteConfig.Snapshot remote = MiteConfig.effectiveSnapshot() == MiteConfig.localSnapshot()
                ? null : MiteConfig.effectiveSnapshot();
        private final ServerLevel level;
        private final long time;
        private final OptionalIte ite;
        private Settings(GameTestHelper helper) {
            level = helper.getLevel();
            time = level.getDayTime();
            MiteConfig.clearServerSnapshot();
            // Synchronous cross-dimension chunk creation can pump queued server reload work.
            // Finish the real source-pool column before publishing this test's rate revision.
            var underworld = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            if (underworld != null) underworld.getChunk(0, 0);
            ite = OptionalIte.create(helper);
            // Rate and admission tests need a legal natural-spawn control, independent of daylight.
            long fixtureTime = level.getDayTime();
            level.setDayTime(fixtureTime - Math.floorMod(fixtureTime, 24000L) + 18000L);
            level.updateSkyBrightness();
            MiteConfig.NATURAL_SPAWN_WEIGHTS.set(List.of()); MiteConfig.refreshEnchantmentRules();
        }
        private void rates(List<String> rates) {
            if (ite != null) ite.rates(rates);
            MiteConfig.NATURAL_SPAWN_RATES.set(List.copyOf(rates)); MiteConfig.refreshEnchantmentRules();
            // Runtime assertions consume one actual published revision. Other tests exercise
            // local saving/reloading independently, including queued native file-watcher work.
            MiteConfig.receiveServerSnapshot(MiteConfig.localSnapshot());
        }
        private void weights(List<String> weights) {
            MiteConfig.NATURAL_SPAWN_WEIGHTS.set(List.copyOf(weights)); MiteConfig.refreshEnchantmentRules();
            MiteConfig.receiveServerSnapshot(MiteConfig.localSnapshot());
        }
        @Override public void close() {
            MiteConfig.NATURAL_SPAWN_RATES.set(original); MiteConfig.NATURAL_SPAWN_WEIGHTS.set(originalWeights);
            MiteConfig.refreshEnchantmentRules();
            MiteConfig.clearServerSnapshot();
            if (remote != null) MiteConfig.receiveServerSnapshot(remote);
            if (ite != null) ite.close();
            level.setDayTime(time);
            level.updateSkyBrightness();
        }
    }

    /** Use the installed addon's real configuration and admission gates without a compile-time dependency. */
    private static final class OptionalIte implements AutoCloseable {
        private final ServerLevel level;
        private final Class<?> config, world, population;
        private final ModConfigSpec.ConfigValue<List<? extends String>> setting;
        private final List<? extends String> original;
        private final Map<?, ?> remoteRates, remoteWeights;
        private final long time, highest;
        private final boolean dirty, grace;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;

        private static OptionalIte create(GameTestHelper helper) {
            try { return new OptionalIte(helper, Class.forName("io.github.ningmeng123.modernite.config.IteSpawnConfig")); }
            catch (ClassNotFoundException absent) { return null; }
        }

        @SuppressWarnings("unchecked")
        private OptionalIte(GameTestHelper helper, Class<?> config) {
            this.level = helper.getLevel(); this.config = config;
            try {
                world = Class.forName("io.github.ningmeng123.modernite.world.ExtremeWorldState");
                population = Class.forName("io.github.ningmeng123.modernite.entity.SpawnPopulation");
                setting = (ModConfigSpec.ConfigValue<List<? extends String>>) config.getField("NATURAL_SPAWN_RATES").get(null);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Installed ITE spawn API unavailable", failure); }
            original = List.copyOf(setting.get());
            Object effectiveRates = call(config, "effectiveSnapshot", new Class<?>[0]);
            Object effectiveWeights = call(config, "effectiveWeights", new Class<?>[0]);
            boolean local = effectiveRates == call(config, "localSnapshot", new Class<?>[0])
                    && effectiveWeights == call(config, "localWeights", new Class<?>[0]);
            remoteRates = local ? null : (Map<?, ?>)effectiveRates;
            remoteWeights = local ? null : (Map<?, ?>)effectiveWeights;
            time = level.getServer().overworld().getDayTime();
            var history = BloodMoonInvasionData.get(level); highest = history.highestDay(); dirty = history.isDirty();
            grace = (boolean) call(world, "graceEnded", new Class<?>[]{ServerLevel.class}, level);
            level.getServer().overworld().setDayTime(32L * 24000L + 18000L);
            level.getServer().overworld().updateSkyBrightness();
            history.restoreProgress(33L);
            call(world, "restoreGraceEnded", new Class<?>[]{ServerLevel.class, boolean.class}, level, true);
            call(config, "clearServerSnapshot", new Class<?>[0]);
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-spawn-test"), false);
            player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, PacketSendListener listener) {}
            };
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(29, 161, 1)).getCenter()); player.setNoGravity(true);
            level.addNewPlayer(player);
            call(population, "clearReservations", new Class<?>[]{ServerLevel.class}, level);
        }

        private void rates(List<String> rates) {
            setting.set(rates.stream().filter(value -> value.startsWith("modern_ite:")).toList());
            call(config, "clearServerSnapshot", new Class<?>[0]);
            call(config, "refresh", new Class<?>[0]);
            call(config, "receiveServerSnapshot", new Class<?>[]{Map.class}, call(config, "localSnapshot", new Class<?>[0]));
            call(population, "clearReservations", new Class<?>[]{ServerLevel.class}, level);
        }

        private void snapshot(Map<?, ?> rates, Map<?, ?> weights) {
            call(config, "receiveServerSnapshot", new Class<?>[]{Map.class, Map.class}, rates, weights);
        }

        @Override public void close() {
            player.discard(); channel.finishAndReleaseAll();
            call(population, "clearReservations", new Class<?>[]{ServerLevel.class}, level);
            setting.set(original); call(config, "refresh", new Class<?>[0]);
            call(config, "clearServerSnapshot", new Class<?>[0]);
            if (remoteRates != null) snapshot(remoteRates, remoteWeights);
            level.getServer().overworld().setDayTime(time);
            level.getServer().overworld().updateSkyBrightness();
            var history = BloodMoonInvasionData.get(level); history.restoreProgress(highest); history.setDirty(dirty);
            call(world, "restoreGraceEnded", new Class<?>[]{ServerLevel.class, boolean.class}, level, grace);
        }

        private static Object call(Class<?> owner, String name, Class<?>[] parameters, Object... arguments) {
            try {
                var method = owner.getDeclaredMethod(name, parameters); method.setAccessible(true);
                return method.invoke(null, arguments);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Installed ITE spawn fixture: " + name, failure); }
        }
    }
}
