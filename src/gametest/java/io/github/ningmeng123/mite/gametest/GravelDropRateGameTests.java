package io.github.ningmeng123.mite.gametest;

import com.google.gson.Gson;
import io.github.ningmeng123.mite.config.GameplayConfigEdits;
import io.github.ningmeng123.mite.config.GravelDropSettings;
import io.github.ningmeng123.mite.config.GravelDropSettings.Rates;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.network.GameplayConfigPayload;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Saved drop percentages on the real harvest path, with and without Modern ITE. @author NingMeng123 */
@GameTestHolder("mite_gravel_rates")
@PrefixGameTestTemplate(false)
public final class GravelDropRateGameTests {
    @GameTest(template = "empty")
    public static void savedRatesControlBothGravelsAndMatchIteFlint(GameTestHelper helper)
            throws ReflectiveOperationException {
        var lock = GameplayConfigEditGameTests.lockCommonConfig();
        try {
            var original = GravelDropSettings.localRates();
            ModConfigSpec.ConfigValue<List<? extends String>> config = MiteConfig.SPEC.getValues().get("gravel_drops.rates");
            var originalEntries = List.copyOf(config.get());
            var level = helper.getLevel();
            var tileDrops = level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS);
            boolean oldTileDrops = tileDrops.get();
            BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
            var area = new AABB(pos).inflate(1.0D);
            try (var capture = new SpawnedEntityCapture(level,
                    entity -> entity instanceof ItemEntity && area.contains(entity.position()))) {
                tileDrops.set(true, level.getServer());
                verifyRates(new Harvest(helper, pos, helper.makeMockPlayer(GameType.SURVIVAL), capture));
            } finally {
                tileDrops.set(oldTileDrops, level.getServer());
                config.set(originalEntries);
                MiteConfig.SPEC.save();
                helper.assertValueEqual(GravelDropSettings.localRates(), original, "The original gravel rates are restored");
            }
        } finally {
            lock.unlock();
        }
        helper.succeed();
    }

    private static void verifyRates(Harvest harvest) {
        var helper = harvest.helper();
        boolean iteLoaded = ModList.get().isLoaded("modern_ite");
        helper.assertValueEqual(GravelDropSettings.wholeFlint(), iteLoaded, "Flint controls follow the loaded expansion");
        Rates defaults = GravelDropSettings.defaults(iteLoaded);
        var ordinary = Blocks.GRAVEL.defaultBlockState();
        var nether = MiteResourceBlocks.NETHER_GRAVEL.get().defaultBlockState();
        Item netherItem = MiteResourceBlocks.NETHER_GRAVEL.asItem();
        var enchantments = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var fortuneTool = new ItemStack(Items.IRON_SHOVEL);
        fortuneTool.enchant(enchantments.getHolderOrThrow(Enchantments.FORTUNE), 3);
        var silkTool = new ItemStack(Items.IRON_SHOVEL);
        silkTool.enchant(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);

        var minerals = new Rates(only(defaults.ordinary(), "mite:copper_nugget"), only(defaults.nether(), "mite:quartz_shard"));
        helper.assertTrue(GravelDropSettings.save(minerals), "Each complete 100 percent group is accepted");
        harvest.expect(ordinary, ItemStack.EMPTY, MiteMaterials.COPPER_NUGGET.get());
        harvest.expect(nether, fortuneTool, MiteShards.QUARTZ_SHARD.get());
        harvest.expect(ordinary, silkTool, Items.GRAVEL);
        harvest.expect(nether, silkTool, netherItem);

        String flintKey = iteLoaded ? "minecraft:flint" : "mite:flint_chip";
        Item flintItem = iteLoaded ? Items.FLINT : MiteItems.FLINT_CHIP.get();
        if (iteLoaded) {
            assertMergedFlint(helper, defaults.ordinary());
            assertMergedFlint(helper, defaults.nether());
        }
        var flint = new Rates(only(defaults.ordinary(), flintKey), only(defaults.nether(), flintKey));
        helper.assertTrue(GameplayConfigEdits.save("gravel_drops", new Gson().toJson(flint)),
                "The server editor can set the active flint product to 100 percent");
        harvest.expect(ordinary, ItemStack.EMPTY, flintItem);
        harvest.expect(nether, fortuneTool, flintItem);
        assertConfigurationSync(helper, flint, iteLoaded);

        var invalidNether = new HashMap<>(flint.nether());
        invalidNether.put(flintKey, 99D);
        helper.assertFalse(GravelDropSettings.save(new Rates(minerals.ordinary(), invalidNether)),
                "A group whose percentages do not total 100 is rejected");
        helper.assertValueEqual(GravelDropSettings.localRates(), flint, "A rejected save leaves both groups intact");

        var gravel = new Rates(only(defaults.ordinary(), "minecraft:gravel"), only(defaults.nether(), "mite:nether_gravel"));
        helper.assertTrue(GravelDropSettings.save(gravel), "Every specialty product can be set to zero");
        harvest.expect(ordinary, fortuneTool, Items.GRAVEL);
        harvest.expect(nether, fortuneTool, netherItem);
    }

    private static void assertConfigurationSync(GameTestHelper helper, Rates rates, boolean wholeFlint) {
        var config = new GameplayConfigPayload(MiteConfig.localSnapshot(), Map.of(), Map.of(), GravelDropSettings.localSnapshot());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            GameplayConfigPayload.CODEC.encode(buffer, config);
            var received = GameplayConfigPayload.CODEC.decode(buffer);
            helper.assertValueEqual(received.gravelDrops(), new GravelDropSettings.Snapshot(wholeFlint, rates),
                    "The updated probabilities and active flint product reach the client together");
            helper.assertValueEqual(buffer.readableBytes(), 0, "The synchronization packet is consumed completely");
        } finally {
            buffer.release();
        }
    }

    private static void assertMergedFlint(GameTestHelper helper, Map<String, Double> rates) {
        helper.assertFalse(rates.containsKey("mite:flint_chip"), "Installed ITE exposes no flint chip outcome");
        helper.assertTrue(Math.abs(rates.get("minecraft:flint") - 100D / 6D) < 1E-10D,
                "ITE's default whole-flint probability includes the former chip probability");
    }

    private static Map<String, Double> only(Map<String, Double> defaults, String selected) {
        var rates = new HashMap<String, Double>();
        defaults.keySet().forEach(id -> rates.put(id, id.equals(selected) ? 100D : 0D));
        return rates;
    }

    private record Harvest(GameTestHelper helper, BlockPos pos, Player player, SpawnedEntityCapture capture) {
        private void expect(BlockState state, ItemStack tool, Item expected) {
            Block.dropResources(state, helper.getLevel(), pos, null, player, tool);
            var drops = capture.live(ItemEntity.class);
            helper.assertValueEqual(drops.size(), 1, "One entity is spawned for " + state);
            var stack = drops.getFirst().getItem();
            helper.assertTrue(stack.is(expected), "Expected " + expected + " from " + state + " but received " + stack);
            helper.assertValueEqual(stack.getCount(), 1, "The selected product is dropped once");
            drops.getFirst().discard();
        }
    }
}
