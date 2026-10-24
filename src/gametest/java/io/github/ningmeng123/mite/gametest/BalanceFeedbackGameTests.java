package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.github.ningmeng123.mite.entity.SourceVanillaMobLoot;
import io.github.ningmeng123.mite.loot.ArmorStructureLootModifier;
import io.github.ningmeng123.mite.loot.UnderworldDungeonLoot;
import io.github.ningmeng123.mite.rules.SpawnerBalanceRules;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real spawn insertion and death transactions cover the requested survival rebalance. @author NingMeng123 */
@GameTestHolder("mite_feedback")
@PrefixGameTestTemplate(false)
public final class BalanceFeedbackGameTests {
    private record Animal(EntityType<? extends Mob> type, Item meat) {}
    private static final List<Animal> ANIMALS = List.of(
            new Animal(EntityType.COW, Items.BEEF), new Animal(EntityType.MOOSHROOM, Items.BEEF),
            new Animal(EntityType.PIG, Items.PORKCHOP), new Animal(EntityType.SHEEP, Items.MUTTON),
            new Animal(EntityType.CHICKEN, Items.CHICKEN), new Animal(EntityType.HORSE, Items.BEEF),
            new Animal(EntityType.DONKEY, Items.BEEF), new Animal(EntityType.MULE, Items.BEEF),
            new Animal(EntityType.RABBIT, Items.RABBIT));

    @GameTest(template = "empty")
    public static void underworldGhastUnlockBeginsOnlyAfterNinetySixCompletedDays(GameTestHelper h) {
        h.assertFalse(io.github.ningmeng123.mite.worldgen.R196UnderworldSpawns.ghastUnlocked(96L), "The whole96th day remains locked");
        h.assertTrue(io.github.ningmeng123.mite.worldgen.R196UnderworldSpawns.ghastUnlocked(97L), "The97th day unlocks underworld natural ghasts");
        var level = h.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        Mob ghast = EntityType.GHAST.create(level);
        long day = io.github.ningmeng123.mite.worldgen.BloodMoonInvasion.highestDay(level);
        h.assertValueEqual(R196MobSpawning.naturalDaylightAllows(ghast, level), day > 96L,
                "The live natural spawn qualification reads the primary world's shared highest calendar day");
        h.assertTrue(R196MobSpawning.naturalDaylightAllows(EntityType.GHAST.create(h.getLevel().getServer().getLevel(Level.NETHER)),
                h.getLevel().getServer().getLevel(Level.NETHER)), "The underworld date gate does not suppress Nether ghasts");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyAdultMeatAnimalDropsOneTwoOrThreeEvenWhenIllOrAfterMassiveFall(GameTestHelper h) {
        for (Animal animal : ANIMALS) {
            boolean[] found = new boolean[4];
            for (int draw = 0; draw < 48; draw++) {
                Mob mob = animal.type().create(h.getLevel());
                mob.setPos(h.absolutePos(new BlockPos(1, 2, 1)).getCenter());
                mob.setNoAi(true);
                ((AgeableMob)mob).setAge(0);
                h.getLevel().addFreshEntity(mob);
                SourceVanillaMobLoot.setLivestockNeeds(mob, 0F, 0F, 0F);
                mob.getPersistentData().putBoolean("mite_massive_fall_031", true);
                mob.getRandom().setSeed(draw * 104729L);
                List<ItemStack> drops = new ArrayList<>();
                Consumer<LivingDropsEvent> listener = event -> {
                    if (event.getEntity() == mob) {
                        event.getDrops().forEach(drop -> drops.add(drop.getItem().copy()));
                        event.setCanceled(true);
                    }
                };
                NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, listener);
                try { mob.setHealth(0F); mob.die(mob.damageSources().generic()); }
                finally { NeoForge.EVENT_BUS.unregister(listener); mob.discard(); }
                int meat = drops.stream().filter(stack -> stack.is(animal.meat())).mapToInt(ItemStack::getCount).sum();
                h.assertTrue(meat >= 1 && meat <= 3, animal.type() + " adult meat is always within the three equal outcomes");
                found[meat] = true;
            }
            h.assertTrue(found[1] && found[2] && found[3], animal.type() + " actually reaches every requested meat quantity");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void babiesAndDisabledMobLootRemainExcludedFromGuaranteedMeat(GameTestHelper h) {
        var rule = h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean previous = rule.get();
        try {
            for (Animal animal : ANIMALS) for (boolean baby : new boolean[]{true, false}) {
                rule.set(baby, h.getLevel().getServer());
                Mob mob = animal.type().create(h.getLevel());
                mob.setPos(h.absolutePos(new BlockPos(1, 2, 1)).getCenter());
                ((AgeableMob)mob).setAge(baby ? -24000 : 0);
                h.getLevel().addFreshEntity(mob);
                int[] meat = {0};
                Consumer<LivingDropsEvent> listener = event -> {
                    if (event.getEntity() == mob) {
                        meat[0] = event.getDrops().stream().filter(drop -> drop.getItem().is(animal.meat()))
                                .mapToInt(drop -> drop.getItem().getCount()).sum();
                        event.setCanceled(true);
                    }
                };
                NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, listener);
                try { mob.setHealth(0F); mob.die(mob.damageSources().generic()); }
                finally { NeoForge.EVENT_BUS.unregister(listener); mob.discard(); }
                h.assertValueEqual(meat[0], 0, "Baby exclusion and doMobLoot apply to " + animal.type());
            }
        } finally { rule.set(previous, h.getLevel().getServer()); }
        h.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void ordinaryCageCountsSuccessfulMobsAcrossReloadAndDestroysExactlyAtFifteen(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        BlockPos spawn = h.absolutePos(new BlockPos(1, 2, 2));
        level.setBlock(spawn.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(spawn, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(spawn.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 3);
        BaseSpawner spawner = ((SpawnerBlockEntity)level.getBlockEntity(pos)).getSpawner();
        SpawnerSession session = new SpawnerSession(h);
        ServerPlayer player = session.player;
        player.setPos(pos.getX(), pos.getY() + 2, pos.getZ() + 2);
        int total = 0;
        int[] inserted = {0};
        String marker = "mite_quota_" + java.util.UUID.randomUUID();
        Consumer<EntityJoinLevelEvent> clearSpawnVolume = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof Mob mob && mob.getTags().contains(marker)) {
                mob.setNoAi(true); mob.setNoGravity(true);
                // Real inserted mobs move out of the next candidate's collision volume, like a working cage trap.
                mob.setPos(pos.getX() + 100 + inserted[0]++ * 2, pos.getY(), pos.getZ());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EntityJoinLevelEvent.class, clearSpawnVolume);
        try (var capture = new SpawnedEntityCapture(level, entity -> entity instanceof Mob && entity.getTags().contains(marker))) {
            CompoundTag tag = pigTag(spawn);
            ListTag markers = new ListTag(); markers.add(StringTag.valueOf(marker));
            tag.getCompound("SpawnData").getCompound("entity").put("Tags", markers);
            for (int cycle = 0; cycle < 4; cycle++) {
                // A fresh cage object simulates a real chunk reload with the persisted counter.
                spawner.load(level, pos, tag);
                spawner.serverTick(level, pos);
                var added = capture.live(Mob.class);
                total += added.size();
                added.forEach(Mob::discard);
                tag = spawner.save(new CompoundTag());
                h.assertValueEqual(tag.getInt(SpawnerBalanceRules.SPAWNED_TAG), total,
                        "Only actually inserted mobs debit the persistent lifetime quota");
                if (cycle < 3) {
                    h.assertValueEqual((int)tag.getShort("Delay"), 180, "Each completed wave resets exactly nine seconds");
                    h.assertTrue(level.getBlockState(pos).is(Blocks.SPAWNER), "The cage survives until its fifteenth successful spawn");
                    spawner = new BaseSpawner() { @Override public void broadcastEvent(Level ignored, BlockPos position, int event) {} };
                }
                tag.putShort("Delay", (short)0);
            }
            h.assertValueEqual(total, 15, "The final batch stops at three, never spawning a sixteenth mob");
            h.assertTrue(level.getBlockState(pos).isAir(), "The exhausted ordinary spawner block self-destructs");
            BaseSpawner restored = new BaseSpawner() { @Override public void broadcastEvent(Level ignored, BlockPos position, int event) {} };
            restored.load(level, pos, tag);
            restored.serverTick(level, pos);
            h.assertTrue(capture.live(Mob.class).isEmpty(), "Reloading an exhausted spawner cannot create more mobs");
        } finally {
            NeoForge.EVENT_BUS.unregister(clearSpawnVolume);
            session.close();
            level.removeBlock(pos, false);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void newOrdinarySpawnerStartsWithARealNineSecondCountdown(GameTestHelper h) {
        BaseSpawner spawner = new BaseSpawner() { @Override public void broadcastEvent(Level ignored, BlockPos pos, int event) {} };
        var tag = spawner.save(new CompoundTag());
        h.assertValueEqual((int)tag.getShort("Delay"), 180, "First spawn waits nine seconds");
        h.assertValueEqual((int)tag.getShort("MinSpawnDelay"), 180, "Minimum interval is fixed");
        h.assertValueEqual((int)tag.getShort("MaxSpawnDelay"), 180, "Maximum interval is fixed");
        h.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void cancelledEntityJoinsDoNotConsumeTheSuccessfulSpawnQuota(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        BlockPos spawn = h.absolutePos(new BlockPos(1, 2, 2));
        level.setBlock(spawn.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(spawn, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(spawn.above(), Blocks.AIR.defaultBlockState(), 3);
        SpawnerSession session = new SpawnerSession(h);
        var player = session.player; player.setPos(pos.getX(), pos.getY()+2, pos.getZ()+2);
        String marker = "mite_rejected_cage_" + java.util.UUID.randomUUID();
        Consumer<EntityJoinLevelEvent> reject = event -> {
            if (event.getLevel() == level && event.getEntity().getTags().contains(marker)) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EntityJoinLevelEvent.class, reject);
        try {
            CompoundTag tag = pigTag(spawn);
            ListTag markers = new ListTag(); markers.add(StringTag.valueOf(marker));
            tag.getCompound("SpawnData").getCompound("entity").put("Tags", markers);
            BaseSpawner spawner = new BaseSpawner() { @Override public void broadcastEvent(Level ignored, BlockPos position, int event) {} };
            spawner.load(level, pos, tag); spawner.serverTick(level, pos);
            h.assertValueEqual(spawner.save(new CompoundTag()).getInt(SpawnerBalanceRules.SPAWNED_TAG), 0,
                    "NeoForge's void passenger insertion may report true after cancellation; only actually added roots spend the quota");
        } finally { NeoForge.EVENT_BUS.unregister(reject); session.close(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void underworldDungeonRestoresTheMissingR196MaterialAndEquipmentOutcomes(GameTestHelper h) {
        var pool = ArmorStructureLootModifier.pools().get("dungeon_underworld");
        h.assertValueEqual(pool.minimumRolls(), 8, "Reference dungeon makes eight weighted rolls");
        for (String metal : List.of("mite:ancient_metal_ingot", "mite:ancient_metal_nugget")) {
            int outcomes = 0;
            for (int draw = 0; draw < pool.sourceTotalWeight(); draw++) {
                var entry = pool.select(draw);
                if (entry != null && entry.item().equals(metal)) {
                    outcomes++;
                    h.assertTrue(entry.minimum() == 1 && entry.maximum() == 4, "Reference ancient metal stacks contain one to four");
                }
            }
            h.assertValueEqual(outcomes, 10, "Each restored ancient metal occupies ten of the 74 reference outcomes");
        }
        for (var entry : pool.entries()) h.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(entry.item())),
                "Every dungeon outcome resolves to an actually registered item: " + entry.item());
        h.assertValueEqual(pool.entries().stream().mapToInt(ArmorStructureLootModifier.Entry::weight).sum(), 74,
                "All 73 ordinary outcomes and the enchanted book exactly fill the reference denominator");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredUnderworldDungeonHasOnlyTheReferenceLootAndMatchesIndependentSlotOracle(GameTestHelper h) {
        ServerLevel underworld = h.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        var pool = ArmorStructureLootModifier.pools().get("dungeon_underworld");
        var claims = io.github.ningmeng123.mite.utility.UniqueMusicSavedData.get(underworld);
        var records = List.of("underworld", "descent", "wanderer", "legends");
        var previous = new HashSet<String>();
        for (String record : records) { if (claims.has(record)) previous.add(record); claims.mark(record); }
        var params = new LootParams.Builder(underworld).withParameter(LootContextParams.ORIGIN, new Vec3(0, 8, 0))
                .create(LootContextParamSets.CHEST);
        var table = underworld.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("chests/simple_dungeon")));
        try {
            for (int seed = 1; seed <= 256; seed++) {
                var context = new LootContext.Builder(params).withOptionalRandomSource(RandomSource.create(seed)).create(Optional.empty());
                var actual = UnderworldDungeonLoot.generate(context, pool);
                Map<String, Integer> counts = new HashMap<>();
                for (var stack : actual) counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                h.assertTrue(counts.equals(referenceCounts(seed, 48)), "Seed " + seed + " matches independent R196 weight/quantity/27-slot replacement arithmetic");
                var real = table.getRandomItems(params, seed);
                h.assertTrue(real.size() <= 8, "A registered dungeon chest has only the reference eight rounds, without extra vanilla pools");
                for (var stack : real) h.assertTrue(pool.entries().stream().anyMatch(entry -> entry.item().equals(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())), "Modern dungeon-only items cannot leak into the complete R196 replacement");
            }
        } finally { for (String record : records) { claims.release(record); if (previous.contains(record)) claims.mark(record); } }
        h.succeed();
    }

    /** Independent transcription of afp.java, xo.java and mk.java; all record signatures are already claimed. */
    private static Map<String, Integer> referenceCounts(long seed, int sourceY) {
        String[] ids = {"mite:ancient_metal_nugget", "mite:ancient_metal_ingot", "minecraft:gunpowder", "minecraft:string",
                "minecraft:bowl", "mite:ancient_metal_bucket", "mite:ancient_metal_coin", "mite:music_disc_underworld", "mite:music_disc_descent", "mite:music_disc_wanderer", "mite:music_disc_legends",
                "minecraft:name_tag", "mite:ancient_metal_horse_armor", "mite:fishing_rod_ancient_metal_hook",
                "mite:ancient_metal_pickaxe", "mite:ancient_metal_shovel", "mite:ancient_metal_dagger", "mite:ancient_metal_sword",
                "mite:ancient_metal_bow", "mite:ancient_metal_chainmail_boots", "mite:ancient_metal_chainmail_leggings",
                "mite:ancient_metal_chainmail_chestplate", "mite:ancient_metal_chainmail_helmet", "minecraft:enchanted_book"};
        int[] weights = {10,10,10,10,3,2,1,1,1,1,1,2,1,2,2,2,2,2,2,2,2,2,2,1};
        Random random = new Random(seed);
        // Source aau.c contains38 enchantments; aau/aar/aas give both sharpness and unbreaking five levels.
        int enchantment = random.nextInt(38);
        int[] levels = {4,4,4,4,4,3,1,3,5,5,5,2,2,3,5,1,5,3,5,2,1,5,5,5,5,5,5,5,5,4,5,5,5,5,5,5,3,4};
        random.nextInt(levels[enchantment]);
        String[] slots = new String[27];
        int[] quantities = new int[27];
        for (int roll = 0; roll < 8; roll++) {
            int selected = -1;
            for (int attempt = 0; attempt < 4; attempt++) {
                int draw = random.nextInt(74), index = 0;
                while ((draw -= weights[index]) >= 0) index++;
                selected = index;
                if (index >= 7 && index <= 10) continue;
                if (index == 13 && sourceY < 48) { selected = -1; continue; }
                break;
            }
            if (selected < 0) continue;
            int quantity = 1 + random.nextInt(selected < 4 ? 4 : 1);
            int slot = random.nextInt(27);
            slots[slot] = ids[selected]; quantities[slot] = quantity;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (int slot = 0; slot < 27; slot++) if (slots[slot] != null) counts.merge(slots[slot], quantities[slot], Integer::sum);
        return counts;
    }

    @GameTest(template = "empty")
    public static void sourceHeightFortySevenExcludesFishingRodsButFortyEightAllowsThem(GameTestHelper h) {
        ServerLevel underworld = h.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        var pool = ArmorStructureLootModifier.pools().get("dungeon_underworld");
        for (int modernY : new int[]{7, 8}) {
            var params = new LootParams.Builder(underworld).withParameter(LootContextParams.ORIGIN, new Vec3(0, modernY, 0))
                    .create(LootContextParamSets.CHEST);
            var random = new FixedCandidateRandom(53);
            var context = new LootContext.Builder(params).withOptionalRandomSource(random).create(Optional.empty());
            var actual = UnderworldDungeonLoot.generate(context, pool);
            h.assertValueEqual(actual.size(), modernY == 7 ? 0 : 8,
                    "Literal mk rod rejection changes at mapped sourceY47/48, or modernY7/8");
            h.assertValueEqual(random.weightedDraws, modernY == 7 ? 32 : 8,
                    "Each below-threshold round spends exactly four failed source attempts");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void literalFourthRetryCanEmitClaimedRecordsAndTheReservedSourceSignature(GameTestHelper h) {
        ServerLevel underworld = h.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        var claims = io.github.ningmeng123.mite.utility.UniqueMusicSavedData.get(underworld);
        boolean previous = claims.has("underworld");
        try {
            for (boolean claimed : new boolean[]{true, false}) {
                claims.release("underworld"); if (claimed) claims.mark("underworld");
                var params = new LootParams.Builder(underworld).withParameter(LootContextParams.ORIGIN, new Vec3(0, 8, 0))
                        .create(LootContextParamSets.CHEST);
                var random = new FixedCandidateRandom(46);
                var context = new LootContext.Builder(params).withOptionalRandomSource(random).create(Optional.empty());
                var actual = UnderworldDungeonLoot.generate(context, ArmorStructureLootModifier.pools().get("dungeon_underworld"));
                h.assertValueEqual(actual.size(), claimed ? 8 : 9,
                        "mk retains a claimed fourth candidate; a newly reserved record also gets its final extra slot");
                h.assertValueEqual(random.weightedDraws, 32, "Every source round really exhausts all four record attempts");
                h.assertTrue(actual.stream().allMatch(stack -> stack.is(io.github.ningmeng123.mite.registry.MiteUtilityContent.get("music_disc_underworld"))),
                        "Only the scripted source record is produced by the literal retry boundary");
                h.assertTrue(claims.has("underworld"), "The saved world signature remains claimed");
            }
        } finally { claims.release("underworld"); if (previous) claims.mark("underworld"); }
        h.succeed();
    }

    private static final class FixedCandidateRandom extends LegacyRandomSource {
        private final int candidate;
        private int slot;
        int weightedDraws;
        FixedCandidateRandom(int candidate) { super(196L); this.candidate = candidate; }
        @Override public int nextInt(int bound) {
            if (bound == 74) { weightedDraws++; return candidate; }
            if (bound == 27) return slot++;
            if (bound == 38 || bound == 4 || bound == 1) return 0;
            throw new AssertionError("Unexpected source bound " + bound);
        }
    }

    /** Optional mods may send their real login/observer payloads while a cage test inserts mobs. */
    private static final class SpawnerSession implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        SpawnerSession(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(java.util.UUID.randomUUID(), "mite-cage-player"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.CREATIVE); player.setNoGravity(true); player.getInventory().clearContent();
        }
        @Override public void close() { player.server.getPlayerList().remove(player); channel.finishAndReleaseAll(); }
    }

    private static CompoundTag pigTag(BlockPos spawn) {
        CompoundTag entity = new CompoundTag(); entity.putString("id", "minecraft:pig");
        ListTag position = new ListTag(); position.add(DoubleTag.valueOf(spawn.getX() + .5));
        position.add(DoubleTag.valueOf(spawn.getY())); position.add(DoubleTag.valueOf(spawn.getZ() + .5));
        entity.put("Pos", position);
        CompoundTag limit = new CompoundTag(); limit.putInt("min_inclusive", 0); limit.putInt("max_inclusive", 15);
        CompoundTag rules = new CompoundTag(); rules.put("block_light_limit", limit); rules.put("sky_light_limit", limit.copy());
        CompoundTag data = new CompoundTag(); data.put("entity", entity); data.put("custom_spawn_rules", rules);
        CompoundTag tag = new CompoundTag(); tag.put("SpawnData", data); tag.putShort("Delay", (short)0);
        tag.putShort("MinSpawnDelay", (short)180); tag.putShort("MaxSpawnDelay", (short)180); tag.putShort("SpawnCount", (short)4);
        tag.putShort("MaxNearbyEntities", (short)1000); tag.putShort("RequiredPlayerRange", (short)16);
        return tag;
    }
}
