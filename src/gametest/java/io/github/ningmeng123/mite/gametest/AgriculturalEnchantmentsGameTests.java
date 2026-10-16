package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.AgriculturalEnchantments;
import io.github.ningmeng123.mite.survival.FoodAgriculture;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BlockToolModificationEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real loot, canceled events and native/MITE tool-use paths for agricultural enchantments. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AgriculturalEnchantmentsGameTests {
    @GameTest(template = "empty")
    public static void sourceEnchantmentDataAndEffectiveToolBoundariesAreActive(GameTestHelper h) {
        var level = h.getLevel(); var registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (var key : java.util.List.of(AgriculturalEnchantments.HARVESTING, AgriculturalEnchantments.FERTILITY)) {
            var enchantment = registry.getHolderOrThrow(key).value();
            h.assertValueEqual(enchantment.getMaxLevel(), 5, "The source agricultural enchantment supports level V");
            h.assertValueEqual(enchantment.getMinCost(5), 41, "Level V starts at source effective budget 41");
            h.assertValueEqual(enchantment.getMaxCost(5), Integer.MAX_VALUE, "Source effective budget has no upper cutoff");
        }
        var hoe = enchanted(h, new ItemStack(Items.NETHERITE_HOE), true, 5);
        var scythe = enchanted(h, new ItemStack(MiteTools.get("mite:iron_scythe")), true, 5);
        var shovel = enchanted(h, new ItemStack(Items.IRON_SHOVEL), true, 5);
        h.assertValueEqual(AgriculturalEnchantments.harvesting(hoe, level, mature(Blocks.CARROTS.defaultBlockState())), .5F,
                "Modern hoes accept source half-strength harvesting bonus");
        h.assertValueEqual(AgriculturalEnchantments.harvesting(scythe, level, mature(Blocks.WHEAT.defaultBlockState())), .5F,
                "Scythes are effective against source wheat");
        h.assertValueEqual(AgriculturalEnchantments.harvesting(scythe, level, mature(Blocks.CARROTS.defaultBlockState())), 0F,
                "Scythes do not extend the exact source wheat condition to root vegetables");
        h.assertValueEqual(AgriculturalEnchantments.harvesting(shovel, level, mature(Blocks.WHEAT.defaultBlockState())), 0F,
                "Forced enchanted shovels remain outside agricultural tools");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualWheatLootGetsIndependentHarvestingExtraButNoFortuneBonus(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            var harvesting = enchanted(h, new ItemStack(Items.NETHERITE_HOE), true, 5);
            var fortune = new ItemStack(Items.NETHERITE_HOE);
            fortune.enchant(f.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE), 3);
            var wheat = mature(Blocks.WHEAT.defaultBlockState());
            int extra = 0;
            for (int i = 0; i < 64; i++) {
                Block.dropResources(wheat, f.level, f.pos, null, player, harvesting);
                int count = f.collect(Items.WHEAT); h.assertTrue(count == 1 || count == 2, "One source unit gets at most one extra at level V");
                extra += count - 1;
                Block.dropResources(wheat, f.level, f.pos, null, player, fortune);
                h.assertValueEqual(f.collect(Items.WHEAT), 1, "Fortune does not enter the source wheat formula");
            }
            h.assertTrue(extra > 0 && extra < 64, "Real source harvesting includes both bonus and nonbonus outcomes");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fertilityUsesRealMatureHarvestsIncludingLivingBlightedCrops(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            var hoe = enchanted(h, new ItemStack(Items.IRON_HOE), false, 5);
            h.assertTrue(!hoe.isEmpty() && AgriculturalEnchantments.effective(hoe, mature(Blocks.CARROTS.defaultBlockState())),
                    "The source iron hoe retains minecraft:iron_hoe and is a real effective nonempty harvest tool");
            for (boolean blighted : new boolean[]{false, true}) {
                f.set(f.pos.below(), soil());
                var state = mature(Blocks.CARROTS.defaultBlockState()).setValue(CubeCropBlight.BLIGHTED, blighted);
                Block.dropResources(state, f.level, f.pos, null, player, hoe);
                h.assertTrue(FoodAgriculture.fertilized(f.level.getBlockState(f.pos.below())), "Living mature crops trigger fertility even when blighted");
                f.clearDrops();
            }
            for (var state : new BlockState[]{Blocks.CARROTS.defaultBlockState(),
                    mature(Blocks.CARROTS.defaultBlockState()).setValue(CubeCropBlight.DEAD, true)}) {
                f.set(f.pos.below(), soil()); Block.dropResources(state, f.level, f.pos, null, player, hoe);
                h.assertFalse(FoodAgriculture.fertilized(f.level.getBlockState(f.pos.below())), "Immature and dead crops do not refertilize farmland");
                f.clearDrops();
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledHarvestDoesNotApplyFertility(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            var hoe = enchanted(h, new ItemStack(Items.NETHERITE_HOE), false, 5);
            Consumer<BlockDropsEvent> cancel = event -> { if (event.getPos().equals(f.pos)) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BlockDropsEvent.class, cancel);
            try { Block.dropResources(mature(Blocks.WHEAT.defaultBlockState()), f.level, f.pos, null, player, hoe); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertFalse(FoodAgriculture.fertilized(f.level.getBlockState(f.pos.below())), "Canceled drop processing cannot mutate fertility");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualModernHoeAndSourceMattockTillingReturnFertilizedFarmland(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            for (var item : java.util.List.of(Items.NETHERITE_HOE, MiteTools.get("mite:iron_mattock"))) {
                f.set(f.pos, Blocks.DIRT.defaultBlockState()); f.set(f.pos.above(), Blocks.AIR.defaultBlockState());
                var tool = enchanted(h, new ItemStack(item), false, 5); player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                var context = context(player, f.pos);
                var simulated = f.level.getBlockState(f.pos).getToolModifiedState(context, ItemAbilities.HOE_TILL, true);
                h.assertTrue(simulated != null && !FoodAgriculture.fertilized(simulated), "A simulated till does not consume fertility RNG or alter output");
                tool.getItem().useOn(context);
                h.assertTrue(f.level.getBlockState(f.pos).is(Blocks.FARMLAND), "The real tool-use path creates farmland");
                h.assertTrue(FoodAgriculture.fertilized(f.level.getBlockState(f.pos)), "Fertility V amends the state actually placed by the tool");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledToolTransformationLeavesDirtUnchanged(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var player = h.makeMockPlayer(GameType.SURVIVAL); var tool = enchanted(h, new ItemStack(Items.NETHERITE_HOE), false, 5);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool); f.set(f.pos, Blocks.DIRT.defaultBlockState());
            Consumer<BlockToolModificationEvent> cancel = event -> { if (event.getPos().equals(f.pos)) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BlockToolModificationEvent.class, cancel);
            try { tool.getItem().useOn(context(player, f.pos)); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(f.level.getBlockState(f.pos).is(Blocks.DIRT), "An external canceled till remains dirt and receives no fertilizer");
        }
        h.succeed();
    }

    private static ItemStack enchanted(GameTestHelper h, ItemStack tool, boolean harvesting, int level) {
        tool.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(harvesting ? AgriculturalEnchantments.HARVESTING : AgriculturalEnchantments.FERTILITY), level);
        return tool;
    }
    private static UseOnContext context(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
    private static BlockState mature(BlockState state) {
        return ((CropBlock) state.getBlock()).getStateForAge(((CropBlock) state.getBlock()).getMaxAge());
    }
    private static BlockState soil() { return Blocks.FARMLAND.defaultBlockState().setValue(FoodAgriculture.FERTILIZED, false); }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level; final BlockPos pos; final Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        final long reseed; final boolean oldDrops;
        Fixture(GameTestHelper h) {
            level = h.getLevel(); pos = h.absolutePos(new BlockPos(2, 3, 2)); reseed = level.random.nextLong();
            level.random.setSeed(196L);
            oldDrops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, level.getServer());
            set(pos.below(), soil()); set(pos, Blocks.AIR.defaultBlockState()); set(pos.above(), Blocks.AIR.defaultBlockState());
        }
        void set(BlockPos p, BlockState state) { saved.putIfAbsent(p, level.getBlockState(p)); level.setBlock(p, state, 2); }
        int collect(net.minecraft.world.item.Item item) {
            int count = 0;
            for (var entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1))) {
                if (entity.getItem().is(item)) count += entity.getItem().getCount(); entity.discard();
            }
            return count;
        }
        void clearDrops() { level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).forEach(ItemEntity::discard); }
        @Override public void close() {
            clearDrops(); saved.forEach((p, state) -> level.setBlock(p, state, 2));
            level.random.setSeed(reseed); level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(oldDrops, level.getServer());
        }
    }
}
