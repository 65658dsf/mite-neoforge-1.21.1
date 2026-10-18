package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.loot.R196OreBonusFunction;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.rules.OreBlockRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent R196 formulas, scripted boundary draws and actual native drop transactions. @author NingMeng123 */
@GameTestHolder("mite_fortune")
@PrefixGameTestTemplate(false)
public final class FortuneParityGameTests {
    private FortuneParityGameTests() {}

    @GameTest(template = "empty")
    public static void ordinaryOreFortuneChecksEveryLevelAtTheExactSourceFloatBoundary(GameTestHelper h) {
        for (Block ore : List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.DIAMOND_ORE,
                Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.NETHER_QUARTZ_ORE)) {
            for (int level = 0; level <= 3; level++) {
                ItemStack tool = tool(h, level, 0);
                if (level == 0) {
                    ScriptedRandom random = new ScriptedRandom(new int[0], new float[0]);
                    h.assertValueEqual(ore(h, ore.defaultBlockState(), tool, false, random).getCount(), 1,
                            "Unenchanted ore retains one source unit without a bonus draw");
                    random.assertConsumed(h);
                    continue;
                }
                // Independent literal apr/aqz arithmetic, including the original float rounding.
                float boundary = (1F + level * .1F) - 1F;
                for (float draw : new float[]{Math.nextDown(boundary), boundary, Math.nextUp(boundary)}) {
                    ScriptedRandom random = new ScriptedRandom(new int[0], new float[]{draw});
                    ItemStack result = ore(h, ore.defaultBlockState(), tool, false, random);
                    h.assertValueEqual(result.getCount(), draw < boundary ? 2 : 1,
                            "Fortune " + level + " uses a strict per-unit source float boundary for " + ore);
                    h.assertTrue(result.is(output(ore)), "Fortune preserves the original mineral identity");
                    random.assertConsumed(h);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lapisAndRedstoneRollThreeToFiveBaseUnitsThenCopyEachUnitIndependently(GameTestHelper h) {
        for (Block ore : List.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE)) {
            for (int level = 0; level <= 3; level++) for (int base = 3; base <= 5; base++) {
                float boundary = (1F + level * .1F) - 1F;
                float[] draws = new float[level == 0 ? 0 : base];
                for (int unit = 0; unit < draws.length; unit++) draws[unit] = unit % 2 == 0 ? Math.nextDown(boundary) : boundary;
                ScriptedRandom random = new ScriptedRandom(new int[]{base - 3}, draws);
                ItemStack result = ore(h, ore.defaultBlockState(), tool(h, level, 0), false, random);
                h.assertValueEqual(result.getCount(), base + (level == 0 ? 0 : (base + 1) / 2),
                        "Each of " + base + " original units has exactly one independent Fortune " + level + " trial");
                h.assertTrue(result.is(output(ore)), "The multi-unit roll retains lapis/redstone identity");
                h.assertTrue(random.bounds.equals(List.of(3)), "Source base quantity uses exactly nextInt(3)");
                random.assertConsumed(h);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void naturalMetalAndPlacedMetalKeepDifferentFortuneEligibilityWhileSilkReturnsOre(GameTestHelper h) {
        for (String id : List.of("minecraft:copper_ore", "minecraft:deepslate_copper_ore", "minecraft:iron_ore",
                "minecraft:deepslate_iron_ore", "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
                "mite:silver_ore", "mite:deepslate_silver_ore", "mite:mithril_ore", "mite:deepslate_mithril_ore",
                "mite:adamantium_ore", "mite:deepslate_adamantium_ore")) {
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            for (int level = 0; level <= 3; level++) {
                ScriptedRandom natural = new ScriptedRandom(new int[0], level == 0 ? new float[0] : new float[]{0F});
                ItemStack result = ore(h, block.defaultBlockState(), tool(h, level, 0), true, natural);
                h.assertValueEqual(result.getCount(), level == 0 ? 1 : 2, "Natural metal enters the source per-unit fortune path: " + id);
                h.assertTrue(result.is(block.asItem()), "Natural metal fortune copies its whole ore block");
                natural.assertConsumed(h);
                ScriptedRandom placed = new ScriptedRandom(new int[0], new float[0]);
                result = ore(h, block.defaultBlockState().setValue(OreBlockRules.PLACED, true), tool(h, level, 0), true, placed);
                h.assertValueEqual(result.getCount(), 1, "Placed metal suppresses fortune at every ordinary level: " + id);
                h.assertTrue(result.is(block.asItem()), "Placed metal retains its block identity");
                placed.assertConsumed(h);
            }
        }
        for (Block block : List.of(Blocks.DIAMOND_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.NETHER_QUARTZ_ORE)) {
            ItemStack silk = tool(h, 3, 0);
            silk.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
            ScriptedRandom random = new ScriptedRandom(new int[0], new float[0]);
            ItemStack result = ore(h, block.defaultBlockState(), silk, false, random);
            h.assertTrue(result.is(block.asItem()) && result.getCount() == 1, "Silk touch exits before mineral or fortune draws");
            random.assertConsumed(h);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oreFortuneKeepsOrdinaryAndAffixContributionsWithoutMultiplyingBaseUnits(GameTestHelper h) {
        for (int normal = 0; normal <= 3; normal++) for (int affix = 1; affix <= 3; affix++) {
            float boundary = (float)((normal > 0 ? (double)((1F + normal * .1F) - 1F) : 0D)
                    + (double)((1F + affix * .1F) - 1F));
            for (float draw : new float[]{Math.nextDown(boundary), boundary}) {
                ScriptedRandom random = new ScriptedRandom(new int[0], new float[]{draw});
                ItemStack result = ore(h, Blocks.DIAMOND_ORE.defaultBlockState(), tool(h, normal, affix), false, random);
                h.assertValueEqual(result.getCount(), draw < boundary ? 2 : 1,
                        "Independent ordinary and affix improvements feed one shared per-unit copy trial");
                random.assertConsumed(h);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeNetherWartMaturityAndEveryFortuneLevelKeepTheSourceQuantityFormula(GameTestHelper h) {
        for (int level = 0; level <= 3; level++) {
            for (int age = 0; age < 3; age++) {
                ScriptedRandom random = new ScriptedRandom(new int[0], new float[0]);
                List<ItemStack> drops = loot(h, Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, age), tool(h, level, 0), random);
                assertOnly(h, drops, Items.NETHER_WART, 1, "Immature nether wart ignores fortune at age " + age);
                random.assertConsumed(h);
            }
            for (int base = 2; base <= 4; base++) for (int extra = 0; extra <= level; extra++) {
                ScriptedRandom random = new ScriptedRandom(level == 0 ? new int[]{base - 2} : new int[]{base - 2, extra}, new float[0]);
                var drops = loot(h, Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3), tool(h, level, 0), random);
                assertOnly(h, drops, Items.NETHER_WART, base + extra, "Mature nether wart follows 2+nextInt(3)+nextInt(F+1)");
                h.assertTrue(random.bounds.equals(level == 0 ? List.of(3) : List.of(3, level + 1)),
                        "Mature wart keeps the two independent nonconstant quantity draws");
                random.assertConsumed(h);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void explosionsCannotUseAProvidedFortuneToolAndNetherWartNeverDropsFromExplosions(GameTestHelper h) {
        for (int level = 0; level <= 3; level++) {
            ScriptedRandom random = new ScriptedRandom(new int[0], new float[0]);
            var state = Blocks.DIAMOND_ORE.defaultBlockState();
            var params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, h.absolutePos(new BlockPos(2, 2, 2)).getCenter())
                    .withParameter(LootContextParams.BLOCK_STATE, state).withParameter(LootContextParams.TOOL, tool(h, level, 0))
                    .withParameter(LootContextParams.EXPLOSION_RADIUS, 4F).create(LootContextParamSets.BLOCK);
            var context = new LootContext.Builder(params).withOptionalRandomSource(random).create(Optional.empty());
            ItemStack result = new R196OreBonusFunction(List.of(), false).apply(new ItemStack(Items.DIAMOND_ORE), context);
            h.assertTrue(result.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mite:diamond_shard"))) && result.getCount() == 1,
                    "An explosion still drops one source fragment when an external caller provides a fortune tool");
            random.assertConsumed(h);
        }
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var previous = h.getLevel().getBlockState(pos);
        try {
            for (int age = 0; age <= 3; age++) {
                BlockState wart = Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, age);
                h.getLevel().setBlock(pos, wart, 2);
                List<ItemStack> drops = new ArrayList<>();
                Explosion explosion = new Explosion(h.getLevel(), null, pos.getX(), pos.getY(), pos.getZ(), 4F, false, Explosion.BlockInteraction.DESTROY);
                wart.onExplosionHit(h.getLevel(), pos, explosion, (stack, at) -> drops.add(stack));
                h.assertTrue(h.getLevel().getBlockState(pos).isAir() && drops.isEmpty(), "The real explosion removes age " + age + " wart without producing loot");
            }
        } finally { h.getLevel().setBlock(pos, previous, 2); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realGrassLootUsesEveryDryAndRainyFortuneDenominatorAndReplacesExactlyOneDirt(GameTestHelper h) {
        var world = h.getLevel();
        BlockPos reference = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos pos = new BlockPos((reference.getX() & ~15) + 8, world.getMaxBuildHeight() - 40, (reference.getZ() & ~15) + 8);
        float rain = world.rainLevel, previousRain = world.oRainLevel;
        var chunk = world.getChunkAt(pos);
        var section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        var oldBiomes = section.getBiomes();
        int bx = QuartPos.fromBlock(chunk.getPos().getMinBlockX()), by = QuartPos.fromBlock(pos.getY()) & ~3;
        int bz = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
        var sampler = world.getChunkSource().randomState().sampler();
        var forest = world.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.FOREST);
        section.fillBiomesFromNoise((x, y, z, climate) -> forest, sampler, bx, by, bz);
        try {
            h.assertTrue(!world.getBiome(pos).value().coldEnoughToSnow(pos), "The controlled forest fixture permits source worm extraction");
            for (boolean raining : new boolean[]{false, true}) {
                world.setRainLevel(raining ? 1F : 0F);
                h.assertTrue(world.isRainingAt(pos.above()) == raining, "The real outdoor fixture has the requested rain exposure");
                for (int level = 0; level <= 3; level++) {
                    int denominator = raining ? new int[]{4, 3, 2, 2}[level] : new int[]{16, 15, 14, 13}[level];
                    for (int draw : new int[]{0, denominator - 1}) {
                        ScriptedRandom random = new ScriptedRandom(new int[]{draw}, new float[0]);
                        var state = Blocks.GRASS_BLOCK.defaultBlockState();
                        var params = new LootParams.Builder(world).withParameter(LootContextParams.ORIGIN, pos.getCenter())
                                .withParameter(LootContextParams.BLOCK_STATE, state).withParameter(LootContextParams.TOOL, tool(h, level, 0))
                                .withParameter(LootContextParams.THIS_ENTITY, h.makeMockPlayer(GameType.SURVIVAL)).create(LootContextParamSets.BLOCK);
                        var drops = world.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable()).getRandomItems(params, random);
                        assertOnly(h, drops, draw == 0 ? MiteFoods.get("raw_worm") : Items.DIRT, 1,
                                "Grass produces exactly one worm or one dirt at Fortune " + level + " in rain=" + raining);
                        h.assertTrue(random.bounds.equals(List.of(denominator)), "The actual grass modifier uses the source probability denominator");
                        random.assertConsumed(h);
                    }
                }
            }
        } finally {
            section.fillBiomesFromNoise((x, y, z, climate) -> oldBiomes.get(x & 3, y & 3, z & 3), sampler, bx, by, bz);
            world.rainLevel = rain;
            world.oRainLevel = previousRain;
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void glowstoneGrassAndFernDoNotReceiveModernFortuneQuantityBonuses(GameTestHelper h) {
        for (int level = 0; level <= 3; level++) {
            for (int base = 2; base <= 4; base++) {
                ScriptedRandom random = new ScriptedRandom(new int[]{base - 2}, new float[0]);
                assertOnly(h, loot(h, Blocks.GLOWSTONE.defaultBlockState(), tool(h, level, 0), random), Items.GLOWSTONE_DUST, base,
                        "Source glowstone dust retains its ordinary quantity with Fortune " + level);
                h.assertTrue(random.bounds.equals(List.of(3)), "Glowstone makes only its original quantity draw");
                random.assertConsumed(h);
            }
            for (Block plant : List.of(Blocks.SHORT_GRASS, Blocks.FERN)) {
                ScriptedRandom win = new ScriptedRandom(new int[0], new float[]{0F});
                assertOnly(h, loot(h, plant.defaultBlockState(), tool(h, level, 0), win), Items.WHEAT_SEEDS, 1,
                        "An existing seed success produces one seed at every fortune level");
                win.assertConsumed(h);
                ScriptedRandom miss = new ScriptedRandom(new int[0], new float[]{.99F});
                h.assertTrue(loot(h, plant.defaultBlockState(), tool(h, level, 0), miss).isEmpty(), "Fortune does not turn a seed failure into a success");
                miss.assertConsumed(h);
            }
        }
        ItemStack affixed = tool(h, 3, 5);
        ScriptedRandom glowstone = new ScriptedRandom(new int[]{0}, new float[0]);
        assertOnly(h, loot(h, Blocks.GLOWSTONE.defaultBlockState(), affixed, glowstone), Items.GLOWSTONE_DUST, 2,
                "Fortune affixes also require a source-supported quantity effect");
        glowstone.assertConsumed(h);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nonPlayerGravelUsesTheSourceBaseBlockInsteadOfTheModernFlintTable(GameTestHelper h) {
        for (int level = 0; level <= 3; level++) {
            ScriptedRandom random = new ScriptedRandom(new int[0], new float[0]);
            assertOnly(h, loot(h, Blocks.GRAVEL.defaultBlockState(), tool(h, level, 0), random), Items.GRAVEL, 1,
                    "The nonplayer loot path retains gravel before the player-only source screening event");
            random.assertConsumed(h);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realNativeDropEventsKeepFortuneCopiesOutOfHarvestExperience(GameTestHelper h) {
        var world = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        List<Observation> transactions = new ArrayList<>();
        Consumer<BlockDropsEvent> observe = event -> {
            if (event.getLevel() == world && event.getPos().equals(pos)) transactions.add(new Observation(
                    event.getDrops().stream().mapToInt(entity -> entity.getItem().getCount()).sum(), event.getDroppedExperience()));
        };
        boolean previousDrops = world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        long followingSeed = world.random.nextLong();
        world.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, world.getServer());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BlockDropsEvent.class, observe);
        try (var spawned = new SpawnedEntityCapture(world, entity -> entity instanceof ItemEntity && entity.getBoundingBox().intersects(new AABB(pos).inflate(2)))) {
            for (Reward reward : List.of(new Reward(Blocks.DIAMOND_ORE, 30), new Reward(Blocks.EMERALD_ORE, 20),
                    new Reward(Blocks.NETHER_QUARTZ_ORE, 10), new Reward(Blocks.LAPIS_ORE, 5),
                    new Reward(Blocks.REDSTONE_ORE, 0), new Reward(Blocks.IRON_ORE, 0))) {
                for (int level : new int[]{0, 1, 2, 3, 10}) {
                    long seed = 196L + level;
                    world.random.setSeed(seed);
                    transactions.clear();
                    Block.dropResources(reward.ore().defaultBlockState(), world, pos, null, player, tool(h, 0, 0));
                    h.assertValueEqual(transactions.size(), 1, "One actual native loot transaction supplies the unenchanted baseline");
                    int originalUnits = transactions.getFirst().count();
                    spawned.live(ItemEntity.class).forEach(ItemEntity::discard);
                    world.random.setSeed(seed);
                    transactions.clear();
                    Block.dropResources(reward.ore().defaultBlockState(), world, pos, null, player, tool(h, level, 0));
                    h.assertValueEqual(transactions.size(), 1, "One actual native loot transaction supplies the fortune result");
                    Observation result = transactions.getFirst();
                    h.assertValueEqual(result.experience(), originalUnits * reward.perUnit(), "Harvest XP counts only original units at Fortune " + level);
                    h.assertTrue(result.count() >= originalUnits && result.count() <= originalUnits * 2, "Each original unit creates at most one fortune copy");
                    if (level == 10) h.assertValueEqual(result.count(), originalUnits * 2, "Source unrestricted test level guarantees copies without adding harvest XP");
                    h.assertValueEqual(spawned.live(ItemEntity.class).stream().mapToInt(entity -> entity.getItem().getCount()).sum(), result.count(),
                            "The final native drop entities match the observed event transaction");
                    spawned.live(ItemEntity.class).forEach(ItemEntity::discard);
                }
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(observe);
            world.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(previousDrops, world.getServer());
            world.random.setSeed(followingSeed);
        }
        h.succeed();
    }

    private static ItemStack tool(GameTestHelper h, int normal, int affix) {
        ItemStack tool = new ItemStack(MiteTools.get("mithril_pickaxe"));
        if (normal > 0) tool.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE), normal);
        if (affix > 0) tool.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), affix, 0L,
                Map.of(Enchantments.FORTUNE.location(), affix), 1));
        return tool;
    }

    private static LootParams params(GameTestHelper h, BlockState state, ItemStack tool) {
        return new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, h.absolutePos(new BlockPos(2, 2, 2)).getCenter())
                .withParameter(LootContextParams.BLOCK_STATE, state).withParameter(LootContextParams.TOOL, tool).create(LootContextParamSets.BLOCK);
    }

    private static ItemStack ore(GameTestHelper h, BlockState state, ItemStack tool, boolean suppressPlaced, ScriptedRandom random) {
        LootContext context = new LootContext.Builder(params(h, state, tool)).withOptionalRandomSource(random).create(Optional.empty());
        return new R196OreBonusFunction(List.of(), suppressPlaced).apply(new ItemStack(state.getBlock().asItem()), context);
    }

    private static List<ItemStack> loot(GameTestHelper h, BlockState state, ItemStack tool, ScriptedRandom random) {
        return h.getLevel().getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable()).getRandomItems(params(h, state, tool), random);
    }

    private static Item output(Block ore) {
        if (ore == Blocks.COAL_ORE || ore == Blocks.DEEPSLATE_COAL_ORE) return Items.COAL;
        if (ore == Blocks.DIAMOND_ORE || ore == Blocks.DEEPSLATE_DIAMOND_ORE) return Items.DIAMOND;
        if (ore == Blocks.EMERALD_ORE || ore == Blocks.DEEPSLATE_EMERALD_ORE) return Items.EMERALD;
        if (ore == Blocks.NETHER_QUARTZ_ORE) return Items.QUARTZ;
        return ore == Blocks.LAPIS_ORE || ore == Blocks.DEEPSLATE_LAPIS_ORE ? Items.LAPIS_LAZULI : Items.REDSTONE;
    }

    private static void assertOnly(GameTestHelper h, List<ItemStack> drops, Item item, int quantity, String message) {
        h.assertTrue(drops.stream().allMatch(stack -> stack.is(item)), message + ": wrong item identity");
        h.assertValueEqual(drops.stream().mapToInt(ItemStack::getCount).sum(), quantity, message);
    }

    private record Reward(Block ore, int perUnit) {}
    private record Observation(int count, int experience) {}

    private static final class ScriptedRandom extends LegacyRandomSource {
        private final int[] integers;
        private final float[] floats;
        private int integerIndex;
        private int floatIndex;
        private final List<Integer> bounds = new ArrayList<>();
        ScriptedRandom(int[] integers, float[] floats) { super(196L); this.integers = integers; this.floats = floats; }
        @Override public int nextInt(int bound) {
            // Constant native ranges may call nextInt(1); they carry no random choice.
            if (bound == 1) return 0;
            bounds.add(bound);
            if (integerIndex >= integers.length) throw new AssertionError("Unexpected nextInt(" + bound + ")");
            int value = integers[integerIndex++];
            if (value < 0 || value >= bound) throw new AssertionError("Scripted integer is outside nextInt(" + bound + ")");
            return value;
        }
        @Override public float nextFloat() {
            if (floatIndex >= floats.length) throw new AssertionError("Unexpected nextFloat()");
            return floats[floatIndex++];
        }
        void assertConsumed(GameTestHelper h) {
            h.assertValueEqual(integerIndex, integers.length, "All nonconstant integer draws are accounted for");
            h.assertValueEqual(floatIndex, floats.length, "All source bonus/chance draws are accounted for");
        }
    }
}
