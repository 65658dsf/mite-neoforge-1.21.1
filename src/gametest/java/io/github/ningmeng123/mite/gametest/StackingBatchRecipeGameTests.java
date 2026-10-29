package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.crafting.StackBatchShapedRecipe;
import io.github.ningmeng123.mite.crafting.StackBatchShapelessRecipe;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipes;
import io.github.ningmeng123.mite.registry.StackingRecipeSerializers;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real recipe loading, network synchronization, native batch delivery and optional viewer pages. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class StackingBatchRecipeGameTests {
    private StackingBatchRecipeGameTests() {}

    @GameTest(template = "empty")
    public static void everyAdaptedNativeBatchLoadsWithoutTruncationAndRoundTripsOverTheNetwork(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonArray records;
        try (var stream = StackingBatchRecipeGameTests.class.getResourceAsStream("/mite/stacking-recipes-030.json")) {
            if (stream == null) throw new IllegalStateException("Missing independent native recipe evidence");
            records = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("entries");
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        helper.assertTrue(records.size() > 50, "The pinned native oversized-recipe inventory covers every affected recipe family");
        for (var element : records) {
            JsonObject record = element.getAsJsonObject();
            ResourceLocation id = ResourceLocation.parse(record.get("id").getAsString());
            CraftingRecipe loaded = (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(id).orElseThrow().value();
            JsonObject json = record.getAsJsonObject("original_json").deepCopy();
            helper.assertTrue(Recipe.CODEC.parse(ops, json).error().isPresent(), "Original strict recipe rejects its newly source-limited result: " + id);
            json.addProperty("type", record.get("adapted_type").getAsString());
            CraftingRecipe decoded = (CraftingRecipe) Recipe.CODEC.parse(ops, json).getOrThrow();
            int count = record.get("output_count").getAsInt();
            helper.assertValueEqual(loaded.getResultItem(helper.getLevel().registryAccess()).getCount(), count, "Loaded recipe retains complete batch: " + id);
            helper.assertValueEqual(decoded.getResultItem(helper.getLevel().registryAccess()).getCount(), count, "JSON decoding retains complete batch: " + id);
            ItemStack decodedOutput = decoded.getResultItem(helper.getLevel().registryAccess());
            boolean adjustedMineral = decodedOutput.is(Items.RAW_COPPER) || decodedOutput.is(Items.RAW_GOLD)
                    || decodedOutput.is(Items.RAW_IRON) || decodedOutput.is(Items.NETHERITE_INGOT);
            helper.assertValueEqual(decodedOutput.getMaxStackSize(), adjustedMineral ? 4 : record.get("maximum_stack").getAsInt(),
                    "Recipe outputs expose source limits with the current four-mineral adjustment: " + id);
            helper.assertValueEqual(decoded.getGroup(), json.has("group") ? json.get("group").getAsString() : "", "Native recipe group is unchanged: " + id);
            helper.assertValueEqual(decoded.category().getSerializedName(), json.has("category") ? json.get("category").getAsString() : "misc", "Native crafting category is unchanged: " + id);
            var roundTrip = (CraftingRecipe) Recipe.CODEC.parse(ops, Recipe.CODEC.encodeStart(ops, decoded).getOrThrow()).getOrThrow();
            helper.assertValueEqual(roundTrip.getResultItem(helper.getLevel().registryAccess()).getCount(), count, "JSON encode/decode retains complete batch: " + id);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                CraftingRecipe network;
                if (decoded instanceof StackBatchShapedRecipe shaped) {
                    StackingRecipeSerializers.SHAPED.get().streamCodec().encode(buffer, shaped);
                    StackBatchShapedRecipe received = StackingRecipeSerializers.SHAPED.get().streamCodec().decode(buffer);
                    helper.assertValueEqual(received.getWidth(), shaped.getWidth(), "Shaped network width stays native");
                    helper.assertValueEqual(received.getHeight(), shaped.getHeight(), "Shaped network height stays native");
                    helper.assertValueEqual(received.showNotification(), shaped.showNotification(), "Shaped network notification flag is preserved");
                    network = received;
                } else {
                    helper.assertTrue(decoded instanceof StackBatchShapelessRecipe, "Only the two known native batch adapters replace vanilla recipes");
                    StackBatchShapelessRecipe shapeless = (StackBatchShapelessRecipe) decoded;
                    StackingRecipeSerializers.SHAPELESS.get().streamCodec().encode(buffer, shapeless);
                    network = StackingRecipeSerializers.SHAPELESS.get().streamCodec().decode(buffer);
                }
                helper.assertValueEqual(network.getGroup(), decoded.getGroup(), "Network group is preserved");
                helper.assertValueEqual(network.category(), decoded.category(), "Network category is preserved");
                helper.assertValueEqual(network.getIngredients().size(), decoded.getIngredients().size(), "Network ingredient count is preserved");
                helper.assertTrue(ItemStack.matches(network.getResultItem(helper.getLevel().registryAccess()), decoded.getResultItem(helper.getLevel().registryAccess())), "Network components and complete result count are preserved");
                helper.assertValueEqual(buffer.readableBytes(), 0, "The codec consumes exactly its normal recipe network payload");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shapedBatchCodecKeepsNativePatternValidationAndNotificationFlag(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonObject valid = JsonParser.parseString("""
                {"type":"mite:stacking_shaped","group":"batch-notification","category":"redstone",
                 "key":{"#":{"item":"minecraft:birch_planks"}},"pattern":["##","##","##"],
                 "result":{"id":"minecraft:birch_door","count":3},"show_notification":false}
                """).getAsJsonObject();
        StackBatchShapedRecipe recipe = (StackBatchShapedRecipe) Recipe.CODEC.parse(ops, valid).getOrThrow();
        helper.assertFalse(recipe.showNotification(), "Native false show_notification survives JSON parsing");
        helper.assertValueEqual(recipe.getResultItem(helper.getLevel().registryAccess()).getCount(), 3, "Three single-stack doors remain a three-item recipe batch");
        JsonObject undefined = valid.deepCopy(); undefined.add("pattern", JsonParser.parseString("[\"#X\"]"));
        helper.assertTrue(Recipe.CODEC.parse(ops, undefined).error().isPresent(), "Undefined pattern symbols still fail native pattern validation");
        JsonObject unused = valid.deepCopy(); unused.getAsJsonObject("key").add("X", JsonParser.parseString("{\"item\":\"minecraft:stick\"}"));
        helper.assertTrue(Recipe.CODEC.parse(ops, unused).error().isPresent(), "Unused pattern keys still fail native pattern validation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shapelessBatchCodecKeepsNativeIngredientValidation(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonObject valid = JsonParser.parseString("""
                {"type":"mite:stacking_shapeless","group":"raw_copper","category":"misc",
                 "ingredients":[{"item":"minecraft:raw_copper_block"}],
                 "result":{"id":"minecraft:raw_copper","count":9}}
                """).getAsJsonObject();
        StackBatchShapelessRecipe recipe = (StackBatchShapelessRecipe) Recipe.CODEC.parse(ops, valid).getOrThrow();
        helper.assertValueEqual(recipe.getResultItem(helper.getLevel().registryAccess()).getCount(), 9, "Nine items remain intact while their inventory cap is four");
        JsonObject empty = valid.deepCopy(); empty.add("ingredients", new JsonArray());
        helper.assertTrue(Recipe.CODEC.parse(ops, empty).error().isPresent(), "Empty ingredient list still fails native validation");
        JsonObject oversized = valid.deepCopy(); JsonArray ingredients = new JsonArray();
        for (int i = 0; i < 10; i++) ingredients.add(JsonParser.parseString("{\"item\":\"minecraft:raw_copper_block\"}"));
        oversized.add("ingredients", ingredients);
        helper.assertTrue(Recipe.CODEC.parse(ops, oversized).error().isPresent(), "A default three-by-three grid still rejects ten ingredients");
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void shapelessRawMetalInstantClicksConserveTheirWholeBatchAsLegalStacks(GameTestHelper helper) {
        for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP))
                try (Fixture fixture = new Fixture(helper)) {
            fixture.load(false);
            helper.assertValueEqual(fixture.menu.getSlot(0).getItem().getCount(), 9, "The real result slot retains every item before production completes");
            helper.assertValueEqual(TimedCraftingController.period(fixture.player, fixture.menu), 0,
                    "Native raw copper has no R196 counterpart, so the batch adapter does not invent a timing rule");
            fixture.menu.clicked(0, 0, click, fixture.player);
            helper.assertFalse(TimedCraftingController.isActive(fixture.player), "The unchanged immediate recipe path creates no manufacturing session");
            helper.assertTrue(fixture.grid.isEmpty(), "One real " + click + " consumes exactly one native batch's ingredients");
            assertConservedAndBounded(helper, fixture, Items.RAW_COPPER, 9, "Immediate " + click);
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void immediateBatchOverflowWithFullInventoriesConservesEveryRawMetalInBothGameModes(GameTestHelper helper) {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE))
                try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setGameMode(mode); fixture.load(false);
            for (int slot = 0; slot < fixture.player.getInventory().items.size(); slot++)
                fixture.player.getInventory().setItem(slot, new ItemStack(Items.IRON_SWORD));
            fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
            helper.assertTrue(fixture.grid.isEmpty(), "A full backpack still consumes only one complete immediate batch");
            assertConservedAndBounded(helper, fixture, Items.RAW_COPPER, 9,
                    "Full-backpack " + mode + " ordinary click");
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void rightClickThrowAndOffhandSwapConsumeExactlyOneCompleteInstantRawMetalBatch(GameTestHelper helper) {
        record Action(ClickType type, int button) {}
        for (Action action : List.of(new Action(ClickType.PICKUP, 1),
                new Action(ClickType.THROW, 0), new Action(ClickType.THROW, 1), new Action(ClickType.SWAP, 40)))
                try (Fixture fixture = new Fixture(helper)) {
            fixture.load(false, 2);
            fixture.menu.clicked(0, action.button(), action.type(), fixture.player);
            helper.assertValueEqual(fixture.grid.getItem(0).getCount(), 1, "One " + action + " removes exactly one item from the first input stack");
            helper.assertTrue(fixture.menu.getSlot(0).getItem().is(Items.RAW_COPPER),
                    "The unconsumed second batch retains a matching result preview");
            helper.assertFalse(TimedCraftingController.isActive(fixture.player), "The result click does not schedule another instant batch");
            if (action.type() == ClickType.PICKUP) helper.assertValueEqual(fixture.menu.getCarried().getCount(), 1,
                    "The explicit right-click batch adaptation leaves one legal item on the cursor");
            if (action.type() == ClickType.THROW) helper.assertTrue(fixture.menu.getCarried().isEmpty(), "Throwing a recipe batch leaves the cursor empty");
            if (action.type() == ClickType.SWAP) helper.assertTrue(fixture.player.getInventory().getItem(40).is(Items.RAW_COPPER),
                    "The native offhand swap places a legal first stack in the actual offhand slot");
            assertConservedAndBounded(helper, fixture, Items.RAW_COPPER, 9, action.toString());
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void instantRawMetalQuickMoveWithOneFreeSlotStoresOneLegalStackAndDropsOnlyItsExactRemainder(GameTestHelper helper) {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE))
                try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setGameMode(mode); fixture.load(false, 2);
            for (int slot = 0; slot < fixture.player.getInventory().items.size(); slot++)
                fixture.player.getInventory().setItem(slot, slot == 35 ? ItemStack.EMPTY : new ItemStack(Items.IRON_SWORD));
            fixture.menu.clicked(0, 0, ClickType.QUICK_MOVE, fixture.player);
            Item output = Items.RAW_COPPER;
            helper.assertValueEqual(fixture.grid.getItem(0).getCount(), 1, "The successful partial Shift transfer consumes exactly one input batch, and cannot consume the blocked next batch");
            helper.assertValueEqual(fixture.player.getInventory().countItem(output), 4,
                    "The only free inventory slot receives precisely its source-limited capacity");
            int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, fixture.player.getBoundingBox().inflate(3),
                    entity -> entity.getItem().is(output)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertValueEqual(dropped, 5, "Only the undelivered batch remainder is dropped, with no second debit");
            assertConservedAndBounded(helper, fixture, output, 9, "One-free-slot Shift " + mode);
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void blockedInstantRawMetalQuickMoveAndOccupiedCursorLeaveAllIngredientsAndResultsUntouched(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.load(false, 2);
            for (int slot = 0; slot < fixture.player.getInventory().items.size(); slot++)
                fixture.player.getInventory().setItem(slot, new ItemStack(Items.IRON_SWORD));
            fixture.menu.clicked(0, 0, ClickType.QUICK_MOVE, fixture.player);
            helper.assertValueEqual(fixture.grid.getItem(0).getCount(), 2, "A fully blocked Shift transfer cannot consume either input batch");
            assertConservedAndBounded(helper, fixture, Items.RAW_COPPER, 0, "No-free-slot Shift");
            fixture.menu.setCarried(new ItemStack(Items.IRON_SWORD));
            for (ClickType type : List.of(ClickType.PICKUP, ClickType.THROW)) {
                fixture.menu.clicked(0, 0, type, fixture.player);
                helper.assertValueEqual(fixture.grid.getItem(0).getCount(), 2, "An incompatible occupied cursor prevents " + type + " from consuming a batch");
                helper.assertTrue(fixture.menu.getCarried().is(Items.IRON_SWORD), "Blocked batch output preserves the unrelated cursor item");
                assertConservedAndBounded(helper, fixture, Items.RAW_COPPER, 0, "Occupied cursor " + type);
            }
            Item output = Items.RAW_COPPER;
            int cap = 4;
            fixture.menu.setCarried(new ItemStack(output, cap));
            fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
            helper.assertValueEqual(fixture.grid.getItem(0).getCount(), 2, "An already full matching cursor cannot debit the input batch");
            assertConservedAndBounded(helper, fixture, output, cap, "Full matching cursor");
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void timedDoorBatchesWaitForTheirR196PeriodAndConserveLegalStacksWithAnyInventoryCapacity(GameTestHelper helper) {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE))
                for (int freeSlots : new int[]{0, 1, 36})
                for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE))
                for (ItemStack cursor : List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BIRCH_DOOR)))
                try (Fixture fixture = new Fixture(helper)) {
            fixture.player.setGameMode(mode);
            fixture.load(true, 2);
            for (int slot = freeSlots; slot < fixture.player.getInventory().items.size(); slot++)
                fixture.player.getInventory().setItem(slot, new ItemStack(Items.IRON_SWORD));
            fixture.menu.setCarried(cursor.copy());
            var rule = io.github.ningmeng123.mite.crafting.TimedCraftingRecipes.get(ResourceLocation.withDefaultNamespace("birch_door"));
            helper.assertTrue(rule != null, "The original door operation has an explicit manufacturing rule");
            helper.assertValueEqual(rule.difficulty(), 480F, "A complete three-door batch uses R196 difficulty 480");
            helper.assertValueEqual(TimedCraftingController.period(fixture.player, fixture.menu),
                    io.github.ningmeng123.mite.crafting.TimedCraftingRules.period(480F, 0, .2F, false),
                    "The physical flint workbench applies its real speed to the R196 door period");
            // This helper drives the production clock and checks every grid/inventory/cursor stack at N-1.
            TimedCraftingTestSupport.craftOne(helper, fixture.player, fixture.menu, click);
            for (int slot : new int[]{0, 1, 3, 4, 6, 7}) helper.assertValueEqual(fixture.grid.getItem(slot).getCount(), 1,
                    "Only the completed batch consumes one of each door ingredient");
            helper.assertTrue(ItemStack.matches(fixture.menu.getCarried(), cursor), "Timed manufacture preserves the preexisting cursor item");
            helper.assertValueEqual(fixture.player.getInventory().countItem(Items.BIRCH_DOOR), Math.min(freeSlots, 3),
                    "Completed doors occupy only the available legal one-item inventory stacks");
            assertConservedAndBounded(helper, fixture, Items.BIRCH_DOOR, 3 + (cursor.is(Items.BIRCH_DOOR) ? 1 : 0),
                    "Timed door " + mode + " " + click + " free slots=" + freeSlots);
        }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void alternateDoorResultClicksCannotExtractOrQueueAnUnmanufacturedBatch(GameTestHelper helper) {
        record Action(ClickType type, int button) {}
        for (Action action : List.of(new Action(ClickType.PICKUP, 1), new Action(ClickType.THROW, 0),
                new Action(ClickType.THROW, 1), new Action(ClickType.SWAP, 0), new Action(ClickType.SWAP, 40),
                new Action(ClickType.CLONE, 2), new Action(ClickType.PICKUP_ALL, 0)))
                try (Fixture fixture = new Fixture(helper)) {
            fixture.load(true, 2);
            fixture.menu.clicked(0, action.button(), action.type(), fixture.player);
            helper.assertFalse(TimedCraftingController.isActive(fixture.player), "Alternative door action " + action + " cannot queue manufacturing");
            for (int tick = 0; tick < TimedCraftingController.period(fixture.player, fixture.menu) * 2; tick++)
                TimedCraftingController.tick(fixture.player);
            for (int slot : new int[]{0, 1, 3, 4, 6, 7}) helper.assertValueEqual(fixture.grid.getItem(slot).getCount(), 2,
                    "Alternative door actions preserve every plank in both input batches");
            helper.assertTrue(fixture.menu.getSlot(0).getItem().is(Items.BIRCH_DOOR), "The unchanged door pattern retains its complete preview");
            helper.assertTrue(fixture.menu.getCarried().isEmpty(), "An unfinished door cannot reach the cursor");
            assertConservedAndBounded(helper, fixture, Items.BIRCH_DOOR, 0, "Unfinished door " + action);
            TimedCraftingTestSupport.craftOne(helper, fixture.player, fixture.menu, ClickType.PICKUP);
            assertConservedAndBounded(helper, fixture, Items.BIRCH_DOOR, 3, "Explicitly manufactured door after " + action);
        }
        helper.succeed();
    }

    private static void assertConservedAndBounded(GameTestHelper helper, Fixture fixture, Item output, int expected, String context) {
        int total = fixture.player.getInventory().countItem(output);
        for (int slot = 0; slot < fixture.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = fixture.player.getInventory().getItem(slot);
            if (stack.is(output)) helper.assertTrue(stack.getCount() <= stack.getMaxStackSize(), context + " never stores an over-limit inventory or offhand stack");
        }
        ItemStack carried = fixture.menu.getCarried();
        if (carried.is(output)) {
            total += carried.getCount();
            helper.assertTrue(carried.getCount() <= carried.getMaxStackSize(), context + " never creates an over-limit carried stack");
        }
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, fixture.player.getBoundingBox().inflate(3),
                candidate -> candidate.getItem().is(output))) {
            total += entity.getItem().getCount();
            helper.assertTrue(entity.getItem().getCount() <= entity.getItem().getMaxStackSize(), context + " never drops an over-limit output stack");
        }
        helper.assertValueEqual(total, expected, context + " conserves the complete native recipe batch across inventory, cursor and overflow drops");
    }

    @GameTest(template = "empty")
    public static void optionalWorkbenchViewerShowsFullNativeBatchWithItsActualStackLimit(GameTestHelper helper) {
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        for (String id : List.of("minecraft:birch_door", "minecraft:raw_copper")) {
            var page = pages.stream().filter(candidate -> candidate.workstationId().equals("mite:flint_crafting_table")
                    && candidate.source().id().equals(ResourceLocation.parse(id))).findFirst().orElseThrow();
            boolean doors = id.endsWith("birch_door");
            helper.assertValueEqual(page.output().getCount(), doors ? 3 : 9, "JEI displays the complete native batch rather than clipping it to an inventory stack");
            helper.assertValueEqual(page.output().getMaxStackSize(), doors ? 1 : 4, "The page uses the same actual stack cap as gameplay");
            helper.assertTrue(doors ? page.source().value() instanceof StackBatchShapedRecipe : page.source().value() instanceof StackBatchShapelessRecipe,
                    "The optional viewer recognizes both explicitly integrated batch adapters");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final BlockPos table;
        final BlockState original;
        final CraftingMenu menu;
        final CraftingContainer grid;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-batch-stack"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
            player.setNoGravity(true); player.setOnGround(true); player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0); player.getFoodData().setFoodLevel(6); player.removeAllEffects();
            table = helper.absolutePos(new BlockPos(2, 1, 2)); original = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, TimedCraftingTestSupport.flintTable().defaultBlockState());
            menu = new CraftingMenu(87, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu; grid = (CraftingContainer) menu.getSlot(1).container;
        }

        void load(boolean doors) {
            load(doors, 1);
        }

        void load(boolean doors, int batches) {
            grid.clearContent();
            if (doors) for (int slot : new int[]{0, 1, 3, 4, 6, 7}) grid.setItem(slot, new ItemStack(Items.BIRCH_PLANKS, batches));
            else grid.setItem(0, new ItemStack(Items.RAW_COPPER_BLOCK, batches));
            helper.assertTrue(menu.getSlot(0).getItem().is(doors ? Items.BIRCH_DOOR : Items.RAW_COPPER), "The actual recipe manager matches the unchanged native recipe pattern");
        }

        @Override public void close() {
            try {
                TimedCraftingController.cancel(player); grid.clearContent(); player.getInventory().clearContent();
                menu.setCarried(ItemStack.EMPTY);
                helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3), entity ->
                        entity.getItem().is(Items.BIRCH_DOOR) || entity.getItem().is(Items.RAW_COPPER)).forEach(ItemEntity::discard);
                player.containerMenu = player.inventoryMenu; player.server.getPlayerList().remove(player);
            } finally { helper.getLevel().setBlockAndUpdate(table, original); channel.finishAndReleaseAll(); }
        }
    }
}
