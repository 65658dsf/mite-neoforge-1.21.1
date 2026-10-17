package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.block.MaterialAnvilBlock;
import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.crafting.AnvilDurability;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Carried durability uses source wear without changing equipment damage or the item being inspected. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AnvilDurabilityItemGameTests {
    private static final BlockPos SITE = new BlockPos(1, 1, 1);
    private record MaterialCase(String name, int maximum, int wornMinimum) {
        Block block() { return name.equals("iron") ? Blocks.ANVIL : MiteFunctionalBlocks.ANVILS.get(name).get(); }
    }
    private static final List<MaterialCase> MATERIALS = List.of(
            new MaterialCase("copper", 198400, 158720), new MaterialCase("silver", 198400, 158720), new MaterialCase("gold", 198400, 158720),
            new MaterialCase("iron", 396800, 317440), new MaterialCase("ancient_metal", 793600, 634880),
            new MaterialCase("mithril", 3174400, 2539520), new MaterialCase("adamantium", 12697600, 10158080),
            // Source float division reaches 0.8F at this first integer, one before exact integer 80% wear.
            new MaterialCase("vibranium", 25395200, 20316159));

    private AnvilDurabilityItemGameTests() {}

    @GameTest(template = "empty")
    public static void eightFreshMaterialsDisplayTheirActualMaximumWithoutEquipmentDamage(GameTestHelper helper) {
        for (MaterialCase material : MATERIALS) {
            ItemStack stack = new ItemStack(material.block());
            assertView(helper, AnvilDurability.of(stack), material.maximum, material.maximum, material.name);
            helper.assertFalse(stack.has(MiteFunctionalBlocks.ANVIL_DAMAGE.get()), "Reading a fresh anvil cannot create source wear");
            helper.assertFalse(stack.has(DataComponents.MAX_DAMAGE) || stack.has(DataComponents.DAMAGE),
                    "A durability label cannot turn " + material.name + " into damageable equipment");
            helper.assertValueEqual(stack.getMaxStackSize(), 1, "Displaying source wear retains the carried anvil limit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeChippedAndDamagedItemsUseTheirStageLowerBoundWithoutAnExactComponent(GameTestHelper helper) {
        ItemStack chipped = new ItemStack(Items.CHIPPED_ANVIL);
        ItemStack damaged = new ItemStack(Items.DAMAGED_ANVIL);
        assertView(helper, AnvilDurability.of(chipped), 198400, 396800, "Native chipped anvil starts at fifty percent wear");
        assertView(helper, AnvilDurability.of(damaged), 79360, 396800, "Native damaged anvil starts at eighty percent wear");
        helper.assertFalse(chipped.has(MiteFunctionalBlocks.ANVIL_DAMAGE.get()) || damaged.has(MiteFunctionalBlocks.ANVIL_DAMAGE.get()),
                "Legacy stage fallback is a read-only estimate and cannot manufacture exact stored wear");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void customBlockStateComponentSuppliesTheStageWhenExactWearIsAbsent(GameTestHelper helper) {
        for (MaterialCase material : MATERIALS) if (!material.name.equals("iron")) {
            for (int stage = 0; stage <= 2; stage++) {
                ItemStack stack = staged(material, stage);
                int remaining = stage == 2 ? material.maximum - material.wornMinimum
                        : stage == 1 ? material.maximum / 2 : material.maximum;
                assertView(helper, AnvilDurability.of(stack), remaining, material.maximum, material.name + " stage " + stage);
                helper.assertTrue(stack.get(DataComponents.BLOCK_STATE).equals(
                                BlockItemStateProperties.EMPTY.with(MaterialAnvilBlock.DAMAGE_STAGE, stage)),
                        "Inferring stage cannot normalize or erase the original BLOCK_STATE component");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactSourceWearOverridesBothCustomAndNativeStageEvenWhenTheyDisagree(GameTestHelper helper) {
        for (MaterialCase material : MATERIALS) {
            ItemStack stack = material.name.equals("iron") ? new ItemStack(Items.DAMAGED_ANVIL) : staged(material, 2);
            for (int exact : new int[]{0, 17, material.maximum / 2 + 37, material.maximum, Integer.MAX_VALUE}) {
                stack.set(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), exact);
                assertView(helper, AnvilDurability.of(stack), Math.max(0, material.maximum - exact), material.maximum,
                        material.name + " exact component " + exact);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedInspectionPreservesComponentsCountAndIndependentEquipmentDurability(GameTestHelper helper) {
        for (MaterialCase material : MATERIALS) {
            ItemStack stack = material.name.equals("iron") ? new ItemStack(Items.CHIPPED_ANVIL, 3) : staged(material, 2);
            stack.setCount(3); // Legacy overfull stacks must remain untouched by a display query.
            stack.set(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), 31);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Source anvil " + material.name));
            stack.set(DataComponents.MAX_DAMAGE, 97);
            stack.set(DataComponents.DAMAGE, 23);
            ItemStack before = stack.copy();
            var saved = stack.save(helper.getLevel().registryAccess());
            for (int query = 0; query < 3; query++) {
                var view = AnvilDurability.of(stack);
                assertView(helper, view, material.maximum - 31, material.maximum, material.name);
                helper.assertTrue(view.text().getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals("tooltip.mite.durability")
                                && java.util.Arrays.equals(contents.getArgs(), new Object[]{material.maximum - 31, material.maximum}),
                        "The shared translated line receives exact remaining and maximum values");
            }
            helper.assertTrue(ItemStack.matches(stack, before), "Repeated inspection retains the whole stack including all components and count");
            helper.assertTrue(saved.equals(stack.save(helper.getLevel().registryAccess())), "Inspection cannot change serialized item data");
            helper.assertValueEqual(stack.getCount(), 3, "Display cannot repair an overfull legacy stack by deleting items");
            helper.assertValueEqual(stack.getMaxDamage(), 97, "Source wear display cannot overwrite a supplied equipment durability capacity");
            helper.assertValueEqual(stack.getDamageValue(), 23, "Source wear display cannot overwrite an independent equipment wear value");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryItemsAndEmptyStacksHaveNoAnvilDisplayEvenWithForeignWearComponents(GameTestHelper helper) {
        for (ItemStack stack : List.of(ItemStack.EMPTY, new ItemStack(Items.STICK), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.STONE), new ItemStack(Items.IRON_BLOCK))) {
            if (!stack.isEmpty()) {
                stack.set(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), 17);
                stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(MaterialAnvilBlock.DAMAGE_STAGE, 2));
            }
            ItemStack before = stack.copy();
            helper.assertTrue(AnvilDurability.of(stack) == null, "Foreign components cannot make an ordinary item display anvil durability");
            helper.assertTrue(ItemStack.matches(stack, before), "Ignoring ordinary items cannot alter their data");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remainingCalculationClampsCorruptValuesWithoutIntegerOverflow(GameTestHelper helper) {
        helper.assertValueEqual(AnvilDurability.remaining(0, Integer.MAX_VALUE), Integer.MAX_VALUE, "The largest capacity remains representable");
        helper.assertValueEqual(AnvilDurability.remaining(-1, 198400), 198400, "Negative wear cannot increase the displayed maximum");
        helper.assertValueEqual(AnvilDurability.remaining(Integer.MIN_VALUE, Integer.MAX_VALUE), Integer.MAX_VALUE, "Clamping occurs before subtraction");
        helper.assertValueEqual(AnvilDurability.remaining(Integer.MAX_VALUE, 198400), 0, "Excess wear never displays a negative remainder");
        helper.assertValueEqual(AnvilDurability.remaining(Integer.MIN_VALUE, Integer.MIN_VALUE), 0, "Both corrupt negative values safely clamp to zero");
        helper.assertValueEqual(AnvilDurability.remaining(7, -1), 0, "Invalid capacity cannot produce durability");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedItemsRealPlacementBlockReloadAndSpawnedLootKeepTheSameDisplayedWear(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(SITE);
        helper.setBlock(SITE.below(), Blocks.STONE);
        for (MaterialCase material : MATERIALS) {
            helper.setBlock(SITE, material.block());
            MaterialAnvilBlockEntity original = entity(helper, pos);
            int wear = material.maximum / 2 + 37;
            original.addDamage(wear);
            original = entity(helper, pos); // Native iron stages recreate the entity.
            assertView(helper, AnvilDurability.of(original), material.maximum - wear, material.maximum, "Original " + material.name);

            var saved = original.saveWithFullMetadata(helper.getLevel().registryAccess());
            var state = helper.getLevel().getBlockState(pos);
            var restored = (MaterialAnvilBlockEntity)BlockEntity.loadStatic(pos, state, saved, helper.getLevel().registryAccess());
            helper.assertTrue(restored != null, "The registered anvil block entity loads through native NBT dispatch");
            helper.getLevel().setBlockEntity(restored);
            assertView(helper, AnvilDurability.of(restored), material.maximum - wear, material.maximum, "Block reload " + material.name);
            helper.assertTrue(saved.equals(restored.saveWithFullMetadata(helper.getLevel().registryAccess())),
                    "Reading a loaded block entity cannot change its saved wear");

            try (SpawnedEntityCapture capture = new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ItemEntity
                    && entity.position().distanceToSqr(Vec3.atCenterOf(pos)) < 9D)) {
                helper.assertTrue(helper.getLevel().destroyBlock(pos, true), "Actual native destruction removes the worn " + material.name + " anvil");
                var drops = capture.live(ItemEntity.class);
                helper.assertValueEqual(drops.size(), 1, "Real destruction spawns one portable anvil item entity");
                ItemStack drop = drops.getFirst().getItem();
                helper.assertValueEqual(drop.getCount(), 1, "The real dropped stack contains one anvil");
                helper.assertValueEqual(drop.getOrDefault(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), -1), wear,
                        "Native copy_components loot retains the complete exact wear");
                assertView(helper, AnvilDurability.of(drop), material.maximum - wear, material.maximum, "Real dropped " + material.name);
                ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), drop.save(helper.getLevel().registryAccess())).orElseThrow();
                helper.assertTrue(ItemStack.matches(loaded, drop), "Native item serialization preserves the actual harvested stack");
                assertView(helper, AnvilDurability.of(loaded), material.maximum - wear, material.maximum, "Saved portable " + material.name);
                placeItem(helper, pos, loaded, "Saved dropped " + material.name);
                assertView(helper, AnvilDurability.of(entity(helper, pos)), material.maximum - wear, material.maximum,
                        "Replaced " + material.name + " agrees with its saved item tooltip");
            }
            helper.getLevel().removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualPlacementOfLegacyStageOnlyItemsAgreesWithTheCarriedEstimate(GameTestHelper helper) {
        helper.setBlock(SITE.below(), Blocks.STONE);
        BlockPos pos = helper.absolutePos(SITE);
        for (MaterialCase material : MATERIALS) for (int stage = 0; stage <= 2; stage++) {
            // empty.nbt is one air block; SITE is outside that cleared volume.
            // Initialize the destination instead of assuming the surrounding world is air.
            helper.setBlock(SITE, Blocks.AIR);
            ItemStack stack = material.name.equals("iron") ? new ItemStack(stage == 2 ? Items.DAMAGED_ANVIL
                    : stage == 1 ? Items.CHIPPED_ANVIL : Items.ANVIL) : staged(material, stage);
            var carried = AnvilDurability.of(stack);
            placeItem(helper, pos, stack, "Legacy " + material.name + " stage " + stage);
            helper.assertTrue(AnvilDurability.of(entity(helper, pos)).equals(carried),
                    "Carried fallback agrees with the actual placed block entity for " + material.name + " stage " + stage);
            helper.getLevel().removeBlock(pos, false);
        }
        helper.succeed();
    }

    private static void placeItem(GameTestHelper helper, BlockPos pos, ItemStack stack, String label) {
        var level = helper.getLevel();
        var before = level.getBlockState(pos);
        int beforeCount = stack.getCount();
        var context = new DirectionalPlaceContext(level, pos, Direction.NORTH, stack, Direction.UP);
        BlockItem item = (BlockItem)stack.getItem();
        boolean replaceable = context.canPlace();
        var candidate = item.getBlock().getStateForPlacement(context);
        boolean survives = candidate != null && candidate.canSurvive(level, pos);
        boolean unobstructed = candidate != null && level.isUnobstructed(candidate, pos, CollisionContext.empty());
        helper.assertTrue(before.isAir() && replaceable,
                label + " fixture must provide an empty replaceable destination: pos=" + pos + ", state=" + before + ", canPlace=" + replaceable);
        var result = item.place(context);
        helper.assertTrue(result.consumesAction(), label + " actual BlockItem.place must succeed: result=" + result + ", pos=" + pos
                + ", before=" + before + ", after=" + level.getBlockState(pos) + ", candidate=" + candidate
                + ", count=" + beforeCount + "->" + stack.getCount() + ", canPlace=" + replaceable
                + ", canSurvive=" + survives + ", unobstructed=" + unobstructed + ", player=" + context.getPlayer());
        helper.assertValueEqual(stack.getCount(), beforeCount - 1, label + " actual placement consumes exactly one carried anvil");
    }

    private static ItemStack staged(MaterialCase material, int stage) {
        ItemStack stack = new ItemStack(material.block());
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(MaterialAnvilBlock.DAMAGE_STAGE, stage));
        return stack;
    }

    private static MaterialAnvilBlockEntity entity(GameTestHelper helper, BlockPos pos) {
        var entity = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(entity instanceof MaterialAnvilBlockEntity, "Actual native/custom anvil has its registered source wear entity");
        return (MaterialAnvilBlockEntity)entity;
    }

    private static void assertView(GameTestHelper helper, AnvilDurability.View view, int remaining, int maximum, String context) {
        helper.assertTrue(view != null, "An anvil exposes a durability view: " + context);
        helper.assertValueEqual(view.remaining(), remaining, "Exact remaining source durability: " + context);
        helper.assertValueEqual(view.maximum(), maximum, "Actual source material maximum: " + context);
    }
}
