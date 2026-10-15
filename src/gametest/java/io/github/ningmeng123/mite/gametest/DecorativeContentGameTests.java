package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.mining.ToolHarvestRules;
import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.world.DecorativeDoorFrames;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual placement, redstone, loot and climate tests of extracted source rules. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DecorativeContentGameTests {
    private static final BlockPos SITE = new BlockPos(4, 2, 4);
    private DecorativeContentGameTests() {}
    @GameTest(template = "ranged_027")
    public static void decorativeIdentitiesAndPhysicalValuesMatchSource(GameTestHelper helper) throws Exception {
        try (var reader = new InputStreamReader(DecorativeContentGameTests.class.getResourceAsStream("/mite/decorative-028.json"), StandardCharsets.UTF_8)) {
            var entries = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries");
            helper.assertValueEqual(entries.size(), 18, "Eighteen requested decorative/redstone leaf identities");
            for (var element : entries) {
                var row = element.getAsJsonObject(); var id = ResourceLocation.parse(row.get("modern_id").getAsString());
                helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "Source item identity: " + id);
                var block = BuiltInRegistries.BLOCK.get(id);
                helper.assertValueEqual(new ItemStack(block).getMaxStackSize(), row.get("max_stack").getAsInt(), "Source stack: " + id);
                float nativeHardness = block.defaultBlockState().getDestroySpeed(helper.getLevel(), helper.absolutePos(SITE));
                helper.assertValueEqual(MiningProgress.effectiveHardness(block.defaultBlockState(), nativeHardness), row.get("hardness").getAsFloat(), "Source mining hardness: " + id);
                if (MiteDecorativeContent.material(block) != null) helper.assertValueEqual(MiteDecorativeContent.requiredHarvest(block), row.get("source_harvest").getAsInt(), "Source harvest tier: " + id);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void allDoorBarsAndDyeShapesResolveWithSourceOutputAndManufacturingRules(GameTestHelper helper) throws Exception {
        try (var reader = new InputStreamReader(DecorativeContentGameTests.class.getResourceAsStream("/mite/decorative-028.json"), StandardCharsets.UTF_8)) {
            for (var element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("recipes")) {
                var row = element.getAsJsonObject(); var id = ResourceLocation.parse(row.get("id").getAsString());
                int width = row.has("pattern") ? row.getAsJsonArray("pattern").get(0).getAsString().length() : 1;
                int height = row.has("pattern") ? row.getAsJsonArray("pattern").size() : 1;
                String input = row.get(row.has("pattern") ? "ingredient" : "input").getAsString();
                var inputItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(input));
                List<ItemStack> stacks = new ArrayList<>(); for (int slot = 0; slot < width * height; slot++) stacks.add(new ItemStack(inputItem));
                var crafting = CraftingInput.of(width, height, stacks);
                var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, helper.getLevel()).orElseThrow();
                helper.assertValueEqual(recipe.id(), id, "Source recipe identity");
                ItemStack output = recipe.value().assemble(crafting, helper.getLevel().registryAccess());
                helper.assertValueEqual(output.getCount(), row.get("count").getAsInt(), "Source output count");
                helper.assertTrue(output.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.get("output").getAsString()))), "Source output item");
                var rule = CraftingQuality.rule(recipe, output, crafting);
                helper.assertValueEqual(rule.difficulty(), row.get("difficulty").getAsFloat(), "Extracted timed difficulty");
                float required = row.get("required_bench").isJsonNull() ? 0 : WorkbenchRules.materialCapacity(row.get("required_bench").getAsString());
                helper.assertValueEqual(WorkbenchRules.required(output), required, "Physical output workbench gate");
            }
        }
        helper.succeed();
    }
    private static BlockPos framedDoor(GameTestHelper helper, Block block) {
        BlockPos absolute = helper.absolutePos(SITE);
        helper.setBlock(SITE.below(), Blocks.STONE);
        helper.setBlock(SITE.west(), Blocks.STONE); helper.setBlock(SITE.west().above(), Blocks.STONE);
        BlockState state = block.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        helper.getLevel().setBlock(absolute, state, 2);
        helper.getLevel().setBlock(absolute.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        return absolute;
    }
    @GameTest(template = "ranged_027")
    public static void copperOpensByHandWhileNewMaterialDoorsRequireRedstone(GameTestHelper helper) {
        List<Block> doors = new ArrayList<>(); doors.add(Blocks.COPPER_DOOR); MiteDecorativeContent.DOORS.values().forEach(value -> doors.add(value.get()));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Block block : doors) {
            BlockPos pos = framedDoor(helper, block);
            var state = helper.getLevel().getBlockState(pos);
            state.useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            helper.assertValueEqual(helper.getLevel().getBlockState(pos).getValue(DoorBlock.OPEN),
                    MiteDecorativeContent.isCopperDoor(block), "Copper opens by hand while the other source metals require redstone");
            helper.getLevel().setBlock(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(DoorBlock.OPEN), "Redstone opens lower half");
            helper.assertTrue(helper.getLevel().getBlockState(pos.above()).getValue(DoorBlock.OPEN), "Redstone synchronizes upper half");
            helper.getLevel().removeBlock(pos.east(), false);
            helper.assertTrue(!helper.getLevel().getBlockState(pos).getValue(DoorBlock.OPEN), "Power loss closes door");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void allCopperStagesKeepNativeManualTypeWithoutChangingVillagerDoorTags(GameTestHelper helper) {
        for (var block : List.of(Blocks.COPPER_DOOR, Blocks.EXPOSED_COPPER_DOOR, Blocks.WEATHERED_COPPER_DOOR,
                Blocks.OXIDIZED_COPPER_DOOR, Blocks.WAXED_COPPER_DOOR, Blocks.WAXED_EXPOSED_COPPER_DOOR,
                Blocks.WAXED_WEATHERED_COPPER_DOOR, Blocks.WAXED_OXIDIZED_COPPER_DOOR)) {
            var state = block.defaultBlockState();
            helper.assertTrue(DoorBlock.isWoodenDoor(state), "Native copper type retains its manual door interaction identity");
            helper.assertTrue(!state.is(BlockTags.MOB_INTERACTABLE_DOORS), "Manual opening does not alter the existing villager interaction tags");
        }
        for (var block : MiteDecorativeContent.DOORS.values())
            helper.assertTrue(!DoorBlock.isWoodenDoor(block.get().defaultBlockState()), "Other source metal types remain redstone-only");
        helper.assertTrue(DoorBlock.isWoodenDoor(Blocks.OAK_DOOR.defaultBlockState()), "Modern wooden door AI retains its native path");
        helper.assertTrue(Blocks.OAK_DOOR.defaultBlockState().is(BlockTags.MOB_INTERACTABLE_DOORS), "Villagers still recognize wooden doors");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void missingDoorFrameRejectsPlacementAndRemovingFrameDropsExactlyOneDoor(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL); player.setYRot(180);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_DOOR));
        BlockPos pos = helper.absolutePos(SITE); helper.setBlock(SITE.below(), Blocks.STONE);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false);
        var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(Blocks.COPPER_DOOR.getStateForPlacement(context) == null, "Free-standing door placement is rejected");
        pos = framedDoor(helper, Blocks.COPPER_DOOR);
        helper.assertTrue(DecorativeDoorFrames.supported(helper.getLevel().getBlockState(pos), helper.getLevel(), pos), "Solid two-level frame supports door");
        helper.getLevel().removeBlock(pos.west().above(), false);
        helper.assertTrue(helper.getLevel().getBlockState(pos).isAir() && helper.getLevel().getBlockState(pos.above()).isAir(), "Lost upper frame removes both halves");
        var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        helper.assertValueEqual(dropped.stream().filter(entity -> entity.getItem().is(Items.COPPER_DOOR)).mapToInt(entity -> entity.getItem().getCount()).sum(), 1, "Exactly one carried door drops");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void pairedMetalDoorsNeedOuterFramesAndLooseSoilCannotServeAsHinges(GameTestHelper helper) {
        BlockPos pos = framedDoor(helper, Blocks.COPPER_DOOR);
        BlockPos other = pos.east(); var first = helper.getLevel().getBlockState(pos);
        var second = first.setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
        helper.getLevel().setBlock(other.below(), Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(other.east(), Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(other.east().above(), Blocks.STONE.defaultBlockState(), 2);
        helper.getLevel().setBlock(other, second, 2);
        helper.getLevel().setBlock(other.above(), second.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        helper.assertTrue(first.canSurvive(helper.getLevel(), pos) && second.canSurvive(helper.getLevel(), other), "Both halves of a framed pair survive");
        helper.getLevel().setBlock(pos.west(), Blocks.DIRT.defaultBlockState(), 2);
        helper.getLevel().setBlock(pos.west().above(), Blocks.DIRT.defaultBlockState(), 2);
        helper.assertTrue(!first.canSurvive(helper.getLevel(), pos), "Source hinges reject full dirt block frames");
        helper.assertTrue(second.canSurvive(helper.getLevel(), other), "Separate outer frame still supports the paired neighbor");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceBarsConnectAndEnforceMaterialHarvestTier(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(SITE);
        player.moveTo(Vec3.atCenterOf(pos).add(0, 0, 2));
        for (var row : MiteDecorativeContent.BARS.entrySet()) {
            var block = row.getValue().get(); helper.setBlock(SITE, Blocks.AIR);
            helper.setBlock(SITE.below(), Blocks.STONE); helper.setBlock(SITE.north(), Blocks.STONE);
            var held = new ItemStack(block); player.setItemInHand(InteractionHand.MAIN_HAND, held);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false);
            var placement = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(((BlockItem) held.getItem()).place(placement).consumesAction(), "Actual bars item can be placed next to existing wall");
            helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(IronBarsBlock.NORTH), "Bars connect to solid neighbor");
            helper.getLevel().removeBlock(pos.north(), false);
            helper.assertTrue(!helper.getLevel().getBlockState(pos).getValue(IronBarsBlock.NORTH), "Connection disappears after actual neighbor removal");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mite:copper_pickaxe")));
            helper.assertValueEqual(ToolHarvestRules.isBlocked(player, block.defaultBlockState()), !row.getKey().equals("copper") && !row.getKey().equals("gold"), "Source tier gates stronger bars");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mite:adamantium_pickaxe")));
            helper.assertTrue(!ToolHarvestRules.isBlocked(player, block.defaultBlockState()), "Highest source pick harvests every bars material");
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceCopperBarsAndModernCopperTrapdoorsHaveIndependentSixIngotRecipes(GameTestHelper helper) {
        var barsInput = CraftingInput.of(3, 2, java.util.Collections.nCopies(6, new ItemStack(Items.COPPER_INGOT)));
        var bars = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, barsInput, helper.getLevel()).orElseThrow();
        helper.assertValueEqual(bars.id(), ResourceLocation.parse("mite:copper_bars"), "Original R196 3x2 shape unambiguously chooses bars");
        var barsOutput = bars.value().assemble(barsInput, helper.getLevel().registryAccess());
        helper.assertTrue(barsOutput.is(MiteDecorativeContent.BARS.get("copper").get().asItem()) && barsOutput.getCount() == 16,
                "Six ingots retain source batch of sixteen copper bars");
        var trapInput = CraftingInput.of(3, 3, List.of(new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT),
                ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY));
        var trap = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, trapInput, helper.getLevel()).orElseThrow();
        helper.assertValueEqual(trap.id(), ResourceLocation.parse("minecraft:copper_trapdoor"), "Modern trapdoor has its own shape and native identity");
        var trapOutput = trap.value().assemble(trapInput, helper.getLevel().registryAccess());
        helper.assertTrue(trapOutput.is(Items.COPPER_TRAPDOOR) && trapOutput.getCount() == 2, "Modern trapdoor keeps its six-ingot/two-result cost");
        helper.assertValueEqual(CraftingQuality.rule(trap, trapOutput, trapInput).difficulty(), 2400F, "Explicit modern copper whole-batch manufacturing cost");
        helper.assertValueEqual(WorkbenchRules.required(trapOutput), 4F, "Modern copper trapdoor uses the copper manufacturing stage");
        BlockPos pos = helper.absolutePos(SITE); helper.setBlock(SITE, Blocks.COPPER_TRAPDOOR);
        helper.getLevel().setBlock(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN), "Retained modern trapdoor still responds to redstone");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void sourceFlowerClimateAppliesToNativeCounterpartsAndNewDahlia(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(SITE); helper.setBlock(SITE.below(), Blocks.GRASS_BLOCK);
        helper.setBiome(Biomes.PLAINS);
        helper.assertTrue(MiteDecorativeContent.DAHLIA.get().defaultBlockState().canSurvive(helper.getLevel(), pos), "Dahlia grows in warm plains");
        helper.assertTrue(!Blocks.BLUE_ORCHID.defaultBlockState().canSurvive(helper.getLevel(), pos), "Orchid requires warmer than plains");
        helper.assertTrue(!Blocks.ALLIUM.defaultBlockState().canSurvive(helper.getLevel(), pos), "Allium outside swamp is illegal");
        helper.setBiome(Biomes.SWAMP);
        helper.assertTrue(Blocks.ALLIUM.defaultBlockState().canSurvive(helper.getLevel(), pos), "Source allium grows in swamp");
        helper.assertTrue(!MiteDecorativeContent.DAHLIA.get().defaultBlockState().canSurvive(helper.getLevel(), pos), "Swamp excludes other source flower subtypes");
        helper.setBiome(Biomes.JUNGLE);
        helper.assertTrue(Blocks.BLUE_ORCHID.defaultBlockState().canSurvive(helper.getLevel(), pos), "Orchid grows in hot jungle");
        helper.assertTrue(!Blocks.OXEYE_DAISY.defaultBlockState().canSurvive(helper.getLevel(), pos), "Daisy is excluded in jungle");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void witherwoodRequiresNetherGravelAndAppliesSourceContactDuration(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(SITE); var block = MiteDecorativeContent.WITHERWOOD_BUSH.get(); var state = block.defaultBlockState();
        helper.setBlock(SITE.below(), Blocks.GRAVEL);
        helper.assertTrue(!state.canSurvive(helper.getLevel(), pos), "Regular gravel cannot support witherwood");
        helper.setBlock(SITE.below(), MiteResourceBlocks.NETHER_GRAVEL.get());
        helper.assertTrue(state.canSurvive(helper.getLevel(), pos), "Persistent Nether gravel supports witherwood");
        var mob = helper.spawnWithNoFreeWill(EntityType.PIG, SITE);
        state.entityInside(helper.getLevel(), pos, mob);
        helper.assertValueEqual(mob.getEffect(MobEffects.WITHER).getDuration(), 200, "First contact gives 200 ticks");
        mob.removeEffect(MobEffects.WITHER); mob.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 0));
        state.entityInside(helper.getLevel(), pos, mob);
        helper.assertValueEqual(mob.getEffect(MobEffects.WITHER).getDuration(), 160, "Repeated contact adds source interval");
        mob.removeEffect(MobEffects.WITHER); mob.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
        state.entityInside(helper.getLevel(), pos, mob);
        helper.assertValueEqual(mob.getEffect(MobEffects.WITHER).getDuration(), 100, "Higher-amplifier existing effect remains intact");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void witherwoodShearsRecoverPlantAndSupportLossReturnsItself(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(SITE); var block = MiteDecorativeContent.WITHERWOOD_BUSH.get(); var state = block.defaultBlockState();
        var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, new ItemStack(MiteTools.get("mite:copper_shears")));
        var drops = state.getDrops(params);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()), "All material shears recover source bush");
        var silk = new ItemStack(MiteTools.get("mite:copper_pickaxe"));
        silk.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        var silkDrops = state.getDrops(new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, silk));
        helper.assertTrue(silkDrops.size() == 1 && silkDrops.getFirst().is(block.asItem()), "Source general silk-harvest path recovers bush");
        helper.setBlock(SITE.below(), MiteResourceBlocks.NETHER_GRAVEL.get()); helper.setBlock(SITE, block);
        helper.getLevel().removeBlock(pos.below(), false);
        helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "Unsupported bush disappears");
        var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        helper.assertValueEqual(entities.stream().filter(entity -> entity.getItem().is(block.asItem())).mapToInt(entity -> entity.getItem().getCount()).sum(), 1, "Illegal bush returns exactly itself");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void dahliaUsesTheModernFlowerPotWithoutCreatingAnotherItem(GameTestHelper helper) {
        var empty = (FlowerPotBlock) Blocks.FLOWER_POT;
        helper.assertTrue(empty.getFullPotsView().get(ResourceLocation.parse("mite:dahlia")).get() == MiteDecorativeContent.POTTED_DAHLIA.get(), "Native pot recognizes dahlia");
        helper.assertTrue(MiteDecorativeContent.POTTED_DAHLIA.get().getPotted() == MiteDecorativeContent.DAHLIA.get(), "Pot retains actual plant identity");
        helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("mite:potted_dahlia")), "Potted plant has no duplicate carried item");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void actualWitherwoodFallKeepsSourceFractionAndOrdinaryGroundKeepsNativeBehavior(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(SITE);
        helper.setBlock(SITE.below(), MiteResourceBlocks.NETHER_GRAVEL.get()); helper.setBlock(SITE, MiteDecorativeContent.WITHERWOOD_BUSH.get());
        var cushioned = helper.spawnWithNoFreeWill(EntityType.COW, SITE);
        cushioned.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        cushioned.causeFallDamage(8F, .5F, cushioned.damageSources().fall());
        helper.assertTrue(Math.abs(cushioned.getHealth() - 16.4F) < .0001F, "Source twenty-health cow loses precisely 3.6 instead of rounded damage");
        helper.getLevel().removeBlock(pos, false);
        var ordinary = helper.spawnWithNoFreeWill(EntityType.COW, SITE);
        ordinary.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        ordinary.causeFallDamage(8F, .5F, ordinary.damageSources().fall());
        helper.assertValueEqual(ordinary.getHealth(), 17F, "Source twenty-health cow retains native ceil((8-3)*.5)=3");
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void lowWitherwoodLandingDoesNotApplyNegativeDamageAndFallEventCancellationIsRespected(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(SITE);
        helper.setBlock(SITE.below(), MiteResourceBlocks.NETHER_GRAVEL.get()); helper.setBlock(SITE, MiteDecorativeContent.WITHERWOOD_BUSH.get());
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, SITE); cow.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        cow.causeFallDamage(3.7F, 1F, cow.damageSources().fall());
        helper.assertValueEqual(cow.getHealth(), 20F, "Source sub-one-point damage does not invoke hurt or healing");
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingFallEvent> cancel = event -> { if (event.getEntity() == cow) event.setCanceled(true); };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingFallEvent.class, cancel);
        try {
            cow.causeFallDamage(8F, 1F, cow.damageSources().fall());
            helper.assertValueEqual(cow.getHealth(), 20F, "Canceled fall event prevents source cushion path too");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel); }
        helper.succeed();
    }
}
