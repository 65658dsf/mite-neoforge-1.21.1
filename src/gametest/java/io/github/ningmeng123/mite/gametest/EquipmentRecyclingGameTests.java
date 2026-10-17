package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import io.github.ningmeng123.mite.integration.jei.FurnaceJeiRecipes;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.R196FurnaceRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Effective fuel rules, loaded recipes and actual native recycling of damaged equipment. @author NingMeng123 */
@GameTestHolder("mite_equipment_recycling")
@PrefixGameTestTemplate(false)
public final class EquipmentRecyclingGameTests {
    private EquipmentRecyclingGameTests() {}

    @GameTest(template = "empty")
    public static void onlyWoodenToolsBurnThroughNativeFuelEventsAndAllFurnaceSlots(GameTestHelper helper) {
        for (var definition : MiteTools.definitions()) {
            boolean wooden = definition.material().equals("wood");
            for (boolean worn : new boolean[]{false, true}) {
                ItemStack stack = tool(definition, worn);
                for (RecipeType<?> type : new RecipeType<?>[]{null, RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING})
                    helper.assertValueEqual(stack.getBurnTime(type), wooden ? 200 : 0,
                            "Native fuel events accept only wooden equipment, including worn tools: " + definition.id() + " / " + type);
                helper.assertValueEqual(FurnaceThermalController.fuel(stack).ticks(), wooden ? 200 : 0,
                        "The thermal controller uses the same effective fuel decision: " + definition.id());
            }
        }
        for (String furnaceId : R196FurnaceRules.profiles().keySet()) try (Fixture f = new Fixture(helper, furnaceId)) {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = (AbstractFurnaceMenu) f.furnace.createMenu(91, player.getInventory(), player);
            for (var definition : MiteTools.definitions()) {
                ItemStack stack = tool(definition, true);
                boolean wooden = definition.material().equals("wood");
                helper.assertTrue(f.furnace.canPlaceItem(1, stack) == wooden,
                        "Native fuel inventories accept only wooden tools: " + furnaceId + " / " + definition.id());
                helper.assertTrue(f.furnace.canPlaceItemThroughFace(1, stack, Direction.WEST) == wooden,
                        "Sided fuel insertion cannot burn nonwooden equipment: " + furnaceId + " / " + definition.id());
                helper.assertTrue(menu.getSlot(1).mayPlace(stack) == wooden,
                        "The actual player fuel slot agrees with native insertion: " + furnaceId + " / " + definition.id());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyMetalToolHasOneSmeltingAndBlastingRecipeForOneMatchingNugget(GameTestHelper helper) {
        for (var definition : MiteTools.definitions()) for (boolean worn : new boolean[]{false, true}) {
            ItemStack stack = tool(definition, worn);
            Item expected = nugget(definition.material());
            for (RecipeType<?> type : new RecipeType<?>[]{RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING}) {
                var matching = matching(helper, type, stack);
                boolean recyclable = expected != null && type != RecipeType.SMOKING;
                helper.assertValueEqual(matching.size(), recyclable ? 1 : 0,
                        "Metal tools match exactly one recycling recipe; wood, flint, obsidian and smoking match none: "
                                + definition.id() + " / " + type + " / worn=" + worn);
                if (!recyclable) continue;
                var recipe = matching.getFirst();
                ItemStack result = recipe.assemble(new SingleRecipeInput(stack), helper.getLevel().registryAccess());
                helper.assertTrue(result.is(expected) && result.getCount() == 1,
                        "Each tool recycles into exactly one nugget of its metal, regardless of wear: " + definition.id());
                helper.assertValueEqual(recipe.getCookingTime(), type == RecipeType.SMELTING ? 200 : 100,
                        "Recycling retains vanilla smelting and blasting durations");
                helper.assertValueEqual(recipe.getExperience(), 0.1F, "Recycling retains vanilla equipment recipe experience");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeFurnaceAndBlastFurnaceRecycleEveryWornMetalToolExactlyOnce(GameTestHelper helper) {
        for (String furnaceId : List.of("minecraft:furnace", "minecraft:blast_furnace"))
            try (Fixture f = new Fixture(helper, furnaceId)) {
                int cycle = furnaceId.equals("minecraft:furnace") ? 200 : 100;
                for (var definition : MiteTools.definitions()) {
                    Item expected = nugget(definition.material());
                    if (expected == null) continue;
                    ItemStack stack = tool(definition, true);
                    helper.assertValueEqual(FurnaceThermalController.requiredHeat(stack), 2,
                            "Every metal tool uses the vanilla iron/gold recycling temperature: " + definition.id());
                    helper.assertTrue(f.furnace.canPlaceItem(0, stack), "The real device accepts worn metal tools as recipe inputs");
                    f.furnace.setItem(0, stack);
                    f.furnace.setItem(1, new ItemStack(Items.COAL));
                    f.tick(cycle - 1);
                    helper.assertTrue(f.furnace.getItem(2).isEmpty() && f.furnace.getItem(0).getCount() == 1,
                            "Recycling cannot complete or consume its tool before the final tick: " + furnaceId + " / " + definition.id());
                    f.tick(1);
                    ItemStack result = f.furnace.removeItem(2, 64);
                    helper.assertTrue(result.is(expected) && result.getCount() == 1 && f.furnace.getItem(0).isEmpty(),
                            "A native completed cycle consumes one damaged tool and produces one matching nugget: "
                                    + furnaceId + " / " + definition.id());
                }
            }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heatOneCannotRecycleMetalToolsOrSpendItsFuel(GameTestHelper helper) {
        for (String furnaceId : List.of("minecraft:furnace", "minecraft:blast_furnace"))
            try (Fixture f = new Fixture(helper, furnaceId)) {
                for (var definition : MiteTools.definitions()) {
                    if (nugget(definition.material()) == null) continue;
                    ItemStack stack = tool(definition, true);
                    int damage = stack.getDamageValue();
                    f.furnace.setItem(0, stack);
                    f.furnace.setItem(1, new ItemStack(Items.CHARCOAL));
                    f.tick(200);
                    helper.assertTrue(f.furnace.getItem(2).isEmpty(), "Insufficient heat cannot recycle " + definition.id());
                    helper.assertTrue(f.furnace.getItem(0).is(stack.getItem()) && f.furnace.getItem(0).getCount() == 1
                                    && f.furnace.getItem(0).getDamageValue() == damage,
                            "A rejected cold cycle preserves the actual worn tool");
                    helper.assertTrue(f.furnace.getItem(1).is(Items.CHARCOAL) && f.furnace.getItem(1).getCount() == 1,
                            "A rejected cold cycle preserves its fuel");
                }
            }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void externallyForcedNonWoodenToolFuelCannotIgnite(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, "minecraft:furnace")) {
            for (var definition : MiteTools.definitions()) {
                if (definition.material().equals("wood")) continue;
                ItemStack fuel = tool(definition, true);
                f.furnace.setItem(0, new ItemStack(Items.BEEF));
                f.furnace.setItem(1, fuel);
                f.tick(200);
                helper.assertTrue(f.furnace.getItem(2).isEmpty() && f.furnace.getItem(0).is(Items.BEEF),
                        "A forced inventory write cannot make nonwooden tools cook food: " + definition.id());
                helper.assertTrue(f.furnace.getItem(1).is(fuel.getItem()) && f.furnace.getItem(1).getCount() == 1,
                        "The rejected tool fuel remains intact: " + definition.id());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeViewerShowsMetalRecyclingWithoutOfferingNonWoodenEquipmentAsFuel(GameTestHelper helper) {
        var recipes = FurnaceJeiRecipes.build(helper.getLevel());
        helper.assertTrue(!recipes.isEmpty(), "The common furnace recipe viewer builds actual registered recipes");
        for (var shown : recipes) for (ItemStack fuel : shown.fuels()) {
            var definition = MiteTools.definition(fuel);
            helper.assertTrue(definition == null || definition.material().equals("wood"),
                    "No JEI processing alternative offers metal, flint or obsidian equipment as fuel: " + fuel);
        }
        helper.assertTrue(recipes.stream().anyMatch(shown -> shown.fuels().stream().anyMatch(fuel -> fuel.is(item("mite:wooden_cudgel")))),
                "The viewer still offers the wooden cudgel as fuel for a valid low-heat batch");
        for (var definition : MiteTools.definitions()) {
            Item expected = nugget(definition.material());
            if (expected == null) continue;
            for (String furnaceId : List.of("minecraft:furnace", "minecraft:blast_furnace"))
                helper.assertTrue(recipes.stream().anyMatch(shown -> shown.furnaceId().equals(furnaceId)
                                && shown.inputs().stream().anyMatch(input -> input.is(item(definition.id())))
                                && shown.output().is(expected) && shown.output().getCount() == 1),
                        "The viewer exposes one-nugget recycling in the real supported device: " + furnaceId + " / " + definition.id());
        }
        helper.succeed();
    }

    private static ItemStack tool(MiteTools.Definition definition, boolean worn) {
        ItemStack stack = new ItemStack(item(definition.id()));
        if (worn) stack.setDamageValue(stack.getMaxDamage() - 1);
        return stack;
    }

    private static List<AbstractCookingRecipe> matching(GameTestHelper helper, RecipeType<?> type, ItemStack stack) {
        return helper.getLevel().getRecipeManager().getRecipes().stream().map(holder -> holder.value())
                .filter(recipe -> recipe instanceof AbstractCookingRecipe cooking && cooking.getType() == type
                        && cooking.matches(new SingleRecipeInput(stack), helper.getLevel()))
                .map(recipe -> (AbstractCookingRecipe) recipe).toList();
    }

    private static Item nugget(String material) {
        return switch (material) {
            case "copper", "silver", "ancient_metal", "mithril", "adamantium" -> item("mite:" + material + "_nugget");
            case "gold" -> Items.GOLD_NUGGET;
            case "iron", "rusted_iron" -> Items.IRON_NUGGET;
            default -> null;
        };
    }

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) throw new IllegalArgumentException("Unregistered recycling test item: " + id);
        return item;
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos position, front;
        final BlockState old, oldFront;
        final AbstractFurnaceBlockEntity furnace;

        Fixture(GameTestHelper helper, String id) {
            this.helper = helper;
            position = helper.absolutePos(new BlockPos(2, 2, 2));
            front = position.north();
            old = helper.getLevel().getBlockState(position);
            oldFront = helper.getLevel().getBlockState(front);
            helper.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            helper.getLevel().setBlockAndUpdate(position, block.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
            furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(position);
        }

        void tick(int count) {
            for (int tick = 0; tick < count; tick++)
                AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), position, helper.getLevel().getBlockState(position), furnace);
        }

        @Override public void close() {
            furnace.clearContent();
            helper.getLevel().setBlockAndUpdate(position, old);
            helper.getLevel().setBlockAndUpdate(front, oldFront);
        }
    }
}
