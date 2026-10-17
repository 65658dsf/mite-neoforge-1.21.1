package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.FoodManufacturingRecipe;
import io.github.ningmeng123.mite.crafting.FoodShapedRecipe;
import io.github.ningmeng123.mite.crafting.FoodShapelessRecipe;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.registry.FoodRecipeSerializers;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real matched recipes, timed result-slot consumption and native cooking. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FoodRecipeGameTests {
    private FoodRecipeGameTests() {}

    @GameTest(template = "empty")
    public static void sourceFoodsAndModernWaterBowlSoupsLoadWithoutConflictingCrafts(GameTestHelper helper) {
        var foods = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(holder -> holder.value() instanceof FoodManufacturingRecipe).toList();
        helper.assertValueEqual(foods.size(), 259, "254 source recipes, four modern soups and the timed water-bowl salad load");
        helper.assertValueEqual(foods.stream().filter(holder -> holder.value() instanceof FoodShapedRecipe).count(), 15L,
                "Eight food/material patterns plus seven empty-bucket patterns retain their shaped form");
        helper.assertValueEqual(foods.stream().filter(holder -> holder.value().getResultItem(helper.getLevel().registryAccess())
                .is(MiteFoods.get("cheese"))).count(), 65L, "Every milk vessel and cheese batch size is registered");
        helper.assertValueEqual(foods.stream().filter(holder -> holder.value().getResultItem(helper.getLevel().registryAccess())
                .is(Items.COOKIE)).count(), 50L, "All cookie source recipes are registered");
        for (String id : new String[]{"bread", "cookie", "cake", "pumpkin_pie", "mushroom_stew", "golden_apple", "golden_carrot",
                "melon_seeds", "pumpkin_seeds", "sugar", "bucket"})
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace(id)).isEmpty(),
                    "The modern default recipe cannot bypass the source ingredients or timing: " + id);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flourRequiresThreeWheatAndCompletesAfter62FlintBenchTicks(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.load(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT));
            helper.assertTrue(f.menu.getSlot(0).getItem().is(MiteFoods.get("flour")), "Three horizontal wheat produce flour, not bread");
            f.craft(62);
            helper.assertValueEqual(f.count(MiteFoods.get("flour")), 1, "One completed source batch produces one flour");
            helper.assertValueEqual(f.count(Items.WHEAT), 0, "All three separate wheat ingredients are consumed once");
            helper.assertValueEqual(f.count(Items.BREAD), 0, "Bread requires the separate dough cooking stage");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fourAndEightMilkBowlsUseTheSameCheesePeriodAndReturnEveryBowl(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            for (int milkCount : new int[]{4, 8}) {
                ItemStack[] milk = new ItemStack[milkCount];
                for (int i = 0; i < milkCount; i++) milk[i] = new ItemStack(MiteFoods.get("milk_bowl"));
                f.load(milk);
                helper.assertTrue(f.menu.getSlot(0).getItem().is(MiteFoods.get("cheese")), "The actual source milk-bowl cheese recipe matches");
                f.craft(995); // D6400 => base1195; level0 flint bench divides by1.2, then truncates.
                helper.assertValueEqual(f.count(MiteFoods.get("cheese")), milkCount == 4 ? 1 : 2,
                        "Four or eight milk bowls produce their source one or two cheeses");
                helper.assertValueEqual(f.count(Items.BOWL), milkCount, "Cheese contains no bowl, so every input milk bowl returns one empty bowl");
                helper.assertValueEqual(f.count(MiteFoods.get("milk_bowl")), 0, "The milk contents are consumed rather than duplicated");
                helper.assertValueEqual(f.player.totalExperience, 0, "Food manufacturing has no equipment quality XP fee");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foodBowlOutputsSuppressTheirEmptyBowlButCakeReturnsItsMilkVessel(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.load(new ItemStack(Items.BROWN_MUSHROOM), new ItemStack(Items.BROWN_MUSHROOM), new ItemStack(MiteFoods.get("milk_bowl")));
            helper.assertTrue(f.menu.getSlot(0).getItem().is(MiteFoods.get("cream_of_mushroom_soup")), "The source creamy mushroom recipe matches");
            f.craft(25);
            helper.assertValueEqual(f.count(MiteFoods.get("cream_of_mushroom_soup")), 1, "One new soup bowl contains the original vessel");
            helper.assertValueEqual(f.count(Items.BOWL), 0, "Soup manufacture must not duplicate its contained bowl as a remainder");
            f.load(new ItemStack(MiteFoods.get("flour")), new ItemStack(Items.SUGAR), new ItemStack(Items.EGG), new ItemStack(MiteFoods.get("milk_bowl")));
            helper.assertTrue(f.menu.getSlot(0).getItem().is(Items.CAKE), "Flour, sugar, egg and one milk bowl produce cake");
            f.craft(83);
            helper.assertValueEqual(f.count(Items.CAKE), 1, "The source cake batch produces one cake");
            helper.assertValueEqual(f.count(Items.BOWL), 1, "Cake has no bowl, so its consumed milk bowl returns one empty bowl");
            f.load(new ItemStack(MiteFoods.get("flour")), new ItemStack(MiteFoods.get("water_bowl")));
            f.craft(41);
            helper.assertValueEqual(f.count(MiteFoods.get("dough")), 1, "Water bowl plus flour produces one dough");
            helper.assertValueEqual(f.count(Items.BOWL), 1, "Solid dough also returns its water bowl's vessel");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void metalBucketsReturnTheirMatchingEmptyVesselsAndMilkCanBeReassembled(GameTestHelper helper) {
        Item[] milkBuckets = {MiteFoods.get("copper_milk_bucket"), MiteFoods.get("silver_milk_bucket"), MiteFoods.get("gold_milk_bucket"),
                Items.MILK_BUCKET, MiteFoods.get("mithril_milk_bucket"), MiteFoods.get("adamantium_milk_bucket"), MiteFoods.get("ancient_metal_milk_bucket")};
        Item[] emptyBuckets = {MiteFoods.get("copper_bucket"), MiteFoods.get("silver_bucket"), MiteFoods.get("gold_bucket"),
                Items.BUCKET, MiteFoods.get("mithril_bucket"), MiteFoods.get("adamantium_bucket"), MiteFoods.get("ancient_metal_bucket")};
        for (int index = 0; index < milkBuckets.length; index++) {
            CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(milkBuckets[index]), new ItemStack(Items.BOWL)));
            var holder = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            helper.assertTrue(holder.value() instanceof FoodManufacturingRecipe, "Every source metal uses the timed food recipe type");
            helper.assertTrue(holder.value().assemble(input, helper.getLevel().registryAccess()).is(MiteFoods.get("milk_bowl")), "One bowl receives milk");
            var remaining = holder.value().getRemainingItems(input);
            helper.assertTrue(remaining.get(0).is(emptyBuckets[index]), "The actual matched recipe returns the exact original metal's empty bucket");
            helper.assertTrue(remaining.get(1).isEmpty(), "The empty bowl is consumed into the result rather than returned again");
        }
        try (Fixture f = new Fixture(helper)) {
            f.load(new ItemStack(MiteFoods.get("copper_milk_bucket")), new ItemStack(Items.BOWL), new ItemStack(Items.BOWL), new ItemStack(Items.BOWL), new ItemStack(Items.BOWL));
            f.craft(25);
            helper.assertValueEqual(f.count(MiteFoods.get("milk_bowl")), 4, "One full bucket fills four separately supplied bowls");
            helper.assertValueEqual(f.count(MiteFoods.get("copper_bucket")), 1, "The bulk operation returns exactly one matching empty bucket");
            helper.assertValueEqual(f.count(Items.BOWL), 0, "All four empty input bowls are now contained in the outputs");
            f.load(new ItemStack(Items.BUCKET), new ItemStack(MiteFoods.get("milk_bowl")), new ItemStack(MiteFoods.get("milk_bowl")),
                    new ItemStack(MiteFoods.get("milk_bowl")), new ItemStack(MiteFoods.get("milk_bowl")));
            helper.assertFalse(TimedCraftingController.start(f.player, f.menu),
                    "R196 source596 requires an iron bench to refill a yi milk bucket, so flint cannot manufacture it");
            helper.assertValueEqual(f.count(MiteFoods.get("milk_bowl")), 4,
                    "A refused milk-bucket refill consumes none of the four milk bowls");
            helper.getLevel().setBlockAndUpdate(f.table, MiteWorkbenches.TABLES.get("iron").get().defaultBlockState());
            f.craft(25);
            helper.assertValueEqual(f.count(Items.MILK_BUCKET), 1, "Four milk bowls refill an iron bucket");
            helper.assertValueEqual(f.count(Items.BOWL), 4, "Refilling a bucket returns all four bowl vessels");
            helper.getLevel().setBlockAndUpdate(f.table, MiteWorkbenches.TABLES.get("flint").get().defaultBlockState());
            f.load(new ItemStack(MiteFoods.get("copper_milk_bucket")), new ItemStack(MiteFoods.get("mithril_milk_bucket")));
            f.craft(995);
            helper.assertValueEqual(f.count(MiteFoods.get("cheese")), 2, "R196's milk-only special matcher accepts mixed bucket metals");
            helper.assertValueEqual(f.count(MiteFoods.get("copper_bucket")), 1, "Mixed milk input returns its actual copper vessel");
            helper.assertValueEqual(f.count(MiteFoods.get("mithril_bucket")), 1, "Mixed milk input also returns its actual mithril vessel");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyCopperBucketCannotBeManufacturedOnAFlintBench(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.load(new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT),
                    ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT));
            helper.assertTrue(f.menu.getSlot(0).getItem().is(MiteFoods.get("copper_bucket")), "The source bucket pattern is recognized");
            helper.assertFalse(TimedCraftingController.start(f.player, f.menu), "Flint capacity one is below the copper manufacture capacity four");
            f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
            f.tick(400);
            helper.assertValueEqual(f.count(Items.COPPER_INGOT), 3, "Failed manufacturing never consumes the three ingots");
            helper.assertValueEqual(f.count(MiteFoods.get("copper_bucket")), 0, "Shift transfer cannot bypass the material requirement");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allNineFoodsKeepSourceRewardsWithTheExplicitCampfireMeatAdaptation(GameTestHelper helper) {
        Item[] inputs = {Items.PORKCHOP, Items.COD, Items.BEEF, Items.CHICKEN, Items.POTATO, Items.MUTTON,
                MiteFoods.get("dough"), MiteFoods.get("raw_large_fish"), MiteFoods.get("raw_worm")};
        Item[] outputs = {Items.COOKED_PORKCHOP, Items.COOKED_COD, Items.COOKED_BEEF, Items.COOKED_CHICKEN, Items.BAKED_POTATO,
                Items.COOKED_MUTTON, Items.BREAD, MiteFoods.get("cooked_large_fish"), MiteFoods.get("cooked_worm")};
        int[] rewards = {3, 3, 4, 3, 0, 2, 0, 4, 0};
        for (int index = 0; index < inputs.length; index++) {
            Item input = inputs[index];
            for (RecipeType<?> type : new RecipeType<?>[]{RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING}) {
                var matching = helper.getLevel().getRecipeManager().getRecipes().stream().map(holder -> holder.value())
                        .filter(recipe -> recipe instanceof AbstractCookingRecipe cooking && cooking.getType() == type
                                && cooking.matches(new SingleRecipeInput(new ItemStack(input)), helper.getLevel())).toList();
                if (type == RecipeType.CAMPFIRE_COOKING && (input == Items.POTATO || input == MiteFoods.get("dough"))) {
                    helper.assertTrue(matching.isEmpty(), "Non-meat source foods retain furnace and smoker recipes without a campfire or JEI recipe");
                    continue;
                }
                helper.assertValueEqual(matching.size(), 1, "Each food has exactly one recipe for this retained cooking device");
                AbstractCookingRecipe cooking = (AbstractCookingRecipe) matching.getFirst();
                helper.assertTrue(cooking.getResultItem(helper.getLevel().registryAccess()).is(outputs[index]), "The cooked output matches the source mapping");
                helper.assertValueEqual(cooking.getCookingTime(), type == RecipeType.CAMPFIRE_COOKING ? 300 : type == RecipeType.SMOKING ? 100 : 200,
                        "Declared recipe-book durations retain furnace200 and smoker100 while the user's modern campfire adaptation takes300 ticks");
                helper.assertValueEqual(cooking.getExperience(), (float) rewards[index], "Recipe rewards retain the literal R196 output experience");
            }
        }
        for (Item[] modern : new Item[][]{{Items.RABBIT, Items.COOKED_RABBIT}, {Items.SALMON, Items.COOKED_SALMON}}) {
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING,
                    new SingleRecipeInput(new ItemStack(modern[0])), helper.getLevel()).orElseThrow().value();
            helper.assertTrue(recipe.getResultItem(helper.getLevel().registryAccess()).is(modern[1]), "Retained modern meats keep their native cooked output");
            helper.assertValueEqual(recipe.getCookingTime(), 300, "Native rabbit and salmon recipe-book/JEI pages also declare fifteen seconds");
            helper.assertValueEqual(recipe.getExperience(), .35F, "Modern meats outside the source mappings retain vanilla cooking rewards");
        }
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING,
                new SingleRecipeInput(new ItemStack(Items.KELP)), helper.getLevel()).isEmpty(), "Native kelp is removed from campfire recipe lookup and JEI");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualFurnaceAndSmokerProduceBreadOnlyAtTheirExplicitOperatingDuration(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos front = position.north();
        BlockState previous = helper.getLevel().getBlockState(position);
        BlockState previousFront = helper.getLevel().getBlockState(front);
        try {
            helper.getLevel().setBlockAndUpdate(front, Blocks.AIR.defaultBlockState());
            for (BlockState device : new BlockState[]{Blocks.FURNACE.defaultBlockState(), Blocks.SMOKER.defaultBlockState()}) {
                if (helper.getLevel().getBlockEntity(position) instanceof AbstractFurnaceBlockEntity old) old.clearContent();
                helper.getLevel().setBlockAndUpdate(position, device);
                AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(position);
                furnace.setItem(0, new ItemStack(MiteFoods.get("dough")));
                furnace.setItem(1, new ItemStack(Items.CHARCOAL));
                int duration = device.is(Blocks.SMOKER) ? 100 : 200;
                for (int tick = 0; tick < duration - 1; tick++)
                    AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), position, helper.getLevel().getBlockState(position), furnace);
                helper.assertTrue(furnace.getItem(2).isEmpty(), "The actual device has no output one tick before its explicit operating duration");
                AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), position, helper.getLevel().getBlockState(position), furnace);
                helper.assertTrue(furnace.getItem(2).is(Items.BREAD), "The actual cooking logic produces bread at exactly200 ordinary or100 adapted smoker ticks");
                helper.assertValueEqual(furnace.getItem(2).getCount(), 1, "One dough produces exactly one bread");
                helper.assertTrue(furnace.getItem(0).isEmpty(), "The completed recipe consumes exactly its one dough");
            }
        } finally {
            if (helper.getLevel().getBlockEntity(position) instanceof AbstractFurnaceBlockEntity furnace) furnace.clearContent();
            helper.getLevel().setBlockAndUpdate(position, previous);
            helper.getLevel().setBlockAndUpdate(front, previousFront);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shapedAndShapelessRecipePacketsPreserveManufacturingAndVesselPolicies(GameTestHelper helper) {
        for (int order : new int[]{400, 509}) {
            CraftingRecipe original = (CraftingRecipe) helper.getLevel().getRecipeManager()
                    .byKey(ResourceLocation.fromNamespaceAndPath("mite", "food_019_" + order)).orElseThrow().value();
            CraftingRecipe decoded = original instanceof FoodShapedRecipe shaped
                    ? roundTrip(helper, FoodRecipeSerializers.SHAPED.get(), shaped)
                    : roundTrip(helper, FoodRecipeSerializers.SHAPELESS.get(), (FoodShapelessRecipe) original);
            helper.assertValueEqual(((FoodManufacturingRecipe) decoded).manufacturing(), ((FoodManufacturingRecipe) original).manufacturing(),
                    "Client recipe synchronization retains exact source difficulty, source order, material and vessel policy");
            helper.assertTrue(ItemStack.matches(decoded.getResultItem(helper.getLevel().registryAccess()), original.getResultItem(helper.getLevel().registryAccess())),
                    "Recipe synchronization retains the complete source output batch");
            helper.assertValueEqual(decoded.getIngredients().size(), original.getIngredients().size(), "Ingredient multiplicities remain explicit across the wire");
        }
        helper.succeed();
    }

    private static <T extends CraftingRecipe> T roundTrip(GameTestHelper helper, RecipeSerializer<T> serializer, T recipe) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            serializer.streamCodec().encode(buffer, recipe);
            T decoded = serializer.streamCodec().decode(buffer);
            helper.assertValueEqual(buffer.readableBytes(), 0, "The serializer consumes exactly one complete recipe");
            return decoded;
        } finally { buffer.release(); }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos table;
        private final BlockState previous;
        private final CraftingMenu menu;
        private final CraftingContainer grid;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-food-craft"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            previous = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, MiteWorkbenches.TABLES.get("flint").get().defaultBlockState());
            menu = new CraftingMenu(61, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            grid = (CraftingContainer) menu.getSlot(1).container;
        }
        private void load(ItemStack... input) {
            TimedCraftingController.cancel(player);
            grid.clearContent();
            player.getInventory().clearContent();
            for (int slot = 0; slot < input.length; slot++) grid.setItem(slot, input[slot]);
            helper.assertFalse(menu.getSlot(0).getItem().isEmpty(), "The real recipe manager populates this food preview");
        }
        private void craft(int expectedPeriod) {
            helper.assertValueEqual(TimedCraftingController.period(player, menu), expectedPeriod, "Source literal difficulty determines the complete batch duration");
            Item output = menu.getSlot(0).getItem().getItem();
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(TimedCraftingController.isActive(player), "The real click begins a timed manufacturing session for "
                    + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output));
            tick(expectedPeriod - 1);
            helper.assertValueEqual(player.getInventory().countItem(output), 0, "No output is supplied before the final production tick");
            tick(1);
            helper.assertFalse(TimedCraftingController.isActive(player), "Ordinary click completes only its one food batch");
        }
        private void tick(int count) { for (int i = 0; i < count; i++) TimedCraftingController.tick(player); }
        private int count(Item item) {
            int result = player.getInventory().countItem(item);
            for (int slot = 0; slot < grid.getContainerSize(); slot++) if (grid.getItem(slot).is(item)) result += grid.getItem(slot).getCount();
            return result;
        }
        @Override public void close() {
            try {
                TimedCraftingController.forget(player);
                grid.clearContent();
                player.getInventory().clearContent();
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally {
                helper.getLevel().setBlockAndUpdate(table, previous);
                channel.finishAndReleaseAll();
            }
        }
    }
}
