package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.FoodManufacturingRecipe;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.integration.jei.SuspiciousStewJeiRecipes;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipes;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SuspiciousStewRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Matched modern soup recipes, native flower effects and actual vessel consumption. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WaterBowlSoupGameTests {
    private WaterBowlSoupGameTests() {}

    private record Soup(String id, Item output, Item[] ingredients) {}

    private static List<Soup> soups() {
        Item water = MiteFoods.get("water_bowl");
        return List.of(
                new Soup("minecraft:beetroot_soup", Items.BEETROOT_SOUP,
                        new Item[]{Items.BEETROOT, Items.BEETROOT, Items.BEETROOT, Items.BEETROOT,
                                Items.BEETROOT, Items.BEETROOT, water}),
                new Soup("mite:food_019_703", Items.MUSHROOM_STEW,
                        new Item[]{Items.BROWN_MUSHROOM, Items.RED_MUSHROOM, water}),
                new Soup("minecraft:rabbit_stew_from_red_mushroom", Items.RABBIT_STEW,
                        new Item[]{Items.BAKED_POTATO, Items.COOKED_RABBIT, water, Items.CARROT, Items.RED_MUSHROOM}),
                new Soup("minecraft:rabbit_stew_from_brown_mushroom", Items.RABBIT_STEW,
                        new Item[]{Items.BAKED_POTATO, Items.COOKED_RABBIT, water, Items.CARROT, Items.BROWN_MUSHROOM}),
                new Soup("minecraft:suspicious_stew", Items.SUSPICIOUS_STEW,
                        new Item[]{Items.RED_MUSHROOM, water, Items.DANDELION, Items.BROWN_MUSHROOM}));
    }

    @GameTest(template = "empty")
    public static void allFourSoupFamiliesRequireWaterAndCannotUseTheOldEmptyBowlRecipes(GameTestHelper helper) {
        for (Soup soup : soups()) {
            var input = grid(soup.ingredients());
            var holder = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            helper.assertValueEqual(holder.id(), ResourceLocation.parse(soup.id()), "The real recipe manager selects the water-bowl recipe");
            helper.assertTrue(holder.value() instanceof FoodManufacturingRecipe, "Every soup shares server manufacturing validation");
            var metadata = ((FoodManufacturingRecipe) holder.value()).manufacturing();
            helper.assertValueEqual(metadata.difficulty(), 25F, "The complete soup batch retains the bowl-soup difficulty");
            helper.assertTrue(metadata.requiredBench().isEmpty(), "These food combinations require no material workbench");
            helper.assertTrue(metadata.outputContainsBowl(), "The output contains the supplied vessel");
            helper.assertValueEqual(metadata.sourceOrder(), soup.output() == Items.MUSHROOM_STEW ? 703 : 0,
                    "Only the existing mushroom soup keeps a historical source recipe order");
            helper.assertTrue(holder.value().assemble(input, helper.getLevel().registryAccess()).is(soup.output()), "The matched recipe creates the original soup item");

            Item[] dry = soup.ingredients().clone();
            for (int i = 0; i < dry.length; i++) if (dry[i] == MiteFoods.get("water_bowl")) dry[i] = Items.BOWL;
            CraftingInput dryInput = grid(dry);
            helper.assertFalse(helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .anyMatch(recipe -> recipe.value().matches(dryInput, helper.getLevel())
                            && recipe.value().assemble(dryInput, helper.getLevel().registryAccess()).is(soup.output())),
                    "No loaded native or MITE recipe can still produce this soup from an empty bowl: " + soup.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothRabbitMushroomVariantsKeepNativeShapelessPlacementAndIngredientCounts(GameTestHelper helper) {
        for (Soup soup : soups().stream().filter(value -> value.output() == Items.RABBIT_STEW).toList()) {
            for (int shift = 0; shift < soup.ingredients().length; shift++) {
                List<Item> shuffled = new ArrayList<>(Arrays.asList(soup.ingredients()));
                Collections.rotate(shuffled, shift);
                ItemStack[] slots = new ItemStack[9];
                Arrays.fill(slots, ItemStack.EMPTY);
                int[] positions = {8, 1, 6, 3, 4};
                for (int index = 0; index < positions.length; index++) slots[positions[index]] = new ItemStack(shuffled.get(index));
                CraftingInput input = CraftingInput.of(3, 3, Arrays.asList(slots));
                var holder = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
                helper.assertValueEqual(holder.id(), ResourceLocation.parse(soup.id()), "Both mushroom colors match regardless of grid placement");
                helper.assertTrue(holder.value().assemble(input, helper.getLevel().registryAccess()).is(Items.RABBIT_STEW), "Rearrangement retains one rabbit stew");
            }
            Item[] missingRabbit = Arrays.stream(soup.ingredients()).filter(item -> item != Items.COOKED_RABBIT).toArray(Item[]::new);
            helper.assertFalse(recipe(helper, soup.id()).matches(grid(missingRabbit), helper.getLevel()), "Rabbit stew still requires its cooked rabbit");
            Item[] rawRabbit = soup.ingredients().clone();
            for (int index = 0; index < rawRabbit.length; index++) if (rawRabbit[index] == Items.COOKED_RABBIT) rawRabbit[index] = Items.RABBIT;
            helper.assertFalse(recipe(helper, soup.id()).matches(grid(rawRabbit), helper.getLevel()), "Changing only the vessel does not permit raw rabbit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void suspiciousStewPreservesEveryNativeFlowerEffectAndRejectsExtraOrInvalidIngredients(GameTestHelper helper) {
        CraftingRecipe recipe = recipe(helper, "minecraft:suspicious_stew");
        SuspiciousStewRecipe nativeRecipe = new SuspiciousStewRecipe(CraftingBookCategory.MISC);
        int flowers = 0;
        for (Item flower : BuiltInRegistries.ITEM.stream().filter(item -> new ItemStack(item).is(ItemTags.SMALL_FLOWERS)).toList()) {
            CraftingInput input = square(Items.RED_MUSHROOM, MiteFoods.get("water_bowl"), flower, Items.BROWN_MUSHROOM);
            CraftingInput nativeInput = square(Items.RED_MUSHROOM, Items.BOWL, flower, Items.BROWN_MUSHROOM);
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "The actual custom recipe accepts native small flower " + BuiltInRegistries.ITEM.getKey(flower));
            helper.assertTrue(nativeRecipe.matches(nativeInput, helper.getLevel()), "The comparison uses the native valid flower recipe");
            ItemStack output = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(output.is(Items.SUSPICIOUS_STEW), "Each flower retains the suspicious stew output");
            helper.assertValueEqual(output.get(DataComponents.SUSPICIOUS_STEW_EFFECTS),
                    nativeRecipe.assemble(nativeInput, helper.getLevel().registryAccess()).get(DataComponents.SUSPICIOUS_STEW_EFFECTS),
                    "The complete native effect identities and durations survive the water-bowl adaptation");
            var sourceFlower = SuspiciousEffectHolder.tryGet(flower);
            if (sourceFlower != null)
                helper.assertValueEqual(output.get(DataComponents.SUSPICIOUS_STEW_EFFECTS), sourceFlower.getSuspiciousEffects(),
                        "The flower's registered effect component is preserved exactly");
            flowers++;
        }
        helper.assertTrue(flowers >= 10, "The regression covers the native small-flower family, not only one chosen example");
        Item water = MiteFoods.get("water_bowl");
        for (Item[] invalid : new Item[][]{
                {Items.RED_MUSHROOM, Items.BROWN_MUSHROOM, Items.DANDELION, Items.BOWL},
                {Items.RED_MUSHROOM, Items.BROWN_MUSHROOM, Items.DANDELION, water, Items.CARROT},
                {Items.RED_MUSHROOM, Items.BROWN_MUSHROOM, Items.DANDELION, water, Items.POPPY},
                {Items.RED_MUSHROOM, Items.BROWN_MUSHROOM, Items.DANDELION, water, water},
                {Items.RED_MUSHROOM, Items.RED_MUSHROOM, Items.DANDELION, water},
                {Items.RED_MUSHROOM, Items.BROWN_MUSHROOM, Items.CARROT, water},
                {Items.RED_MUSHROOM, Items.BROWN_MUSHROOM, water},
                {Items.BROWN_MUSHROOM, Items.DANDELION, water}})
            helper.assertFalse(recipe.matches(grid(invalid), helper.getLevel()), "The special recipe refuses missing, duplicate, non-flower and extra ingredients");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipesUseTheSameVesselAccountingWithoutAnOperatorAndNeverReturnAnExtraEmptyBowl(GameTestHelper helper) {
        for (Soup soup : soups()) {
            CraftingInput input = grid(soup.ingredients());
            CraftingRecipe recipe = recipe(helper, soup.id());
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "A crafter's ordinary CraftingInput matches without a player");
            ItemStack output = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(output.is(soup.output()) && output.getCount() == 1, "Automation assembles exactly one soup bowl per ingredient set");
            var remaining = recipe.getRemainingItems(input);
            helper.assertValueEqual(remaining.size(), input.size(), "Remainders retain the device's original slot count");
            helper.assertTrue(remaining.stream().allMatch(ItemStack::isEmpty), "The water bowl is inside the soup and cannot also return an empty bowl");
            helper.assertTrue(input.items().stream().filter(stack -> stack.is(MiteFoods.get("water_bowl"))).allMatch(stack -> stack.getCount() == 1),
                    "Recipe evaluation and preview never consume or duplicate an input bowl");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualTimedResultClicksConsumeOneWaterBowlAndOneBatchOfIngredients(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            for (Soup soup : soups()) {
                fixture.load(soup);
                helper.assertValueEqual(TimedCraftingController.period(fixture.player, fixture.menu), 25, "Each water-bowl soup uses the shared timed manufacturing path");
                fixture.menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
                helper.assertTrue(TimedCraftingController.isActive(fixture.player), "An ordinary real result-slot click starts this soup's manufacture");
                fixture.tick(24);
                helper.assertValueEqual(fixture.count(soup.output()), 0, "The output cannot be taken before the final manufacturing tick");
                fixture.tick(1);
                helper.assertFalse(TimedCraftingController.isActive(fixture.player), "One ordinary click stops after one completed batch");
                helper.assertValueEqual(fixture.count(soup.output()), 1, "One normal click supplies exactly one completed soup");
                helper.assertValueEqual(fixture.count(MiteFoods.get("water_bowl")), 1, "Exactly one of the two available water bowls is consumed");
                helper.assertValueEqual(fixture.count(Items.BOWL), 0, "Actual result-slot consumption creates no extra empty bowl");
                for (Item ingredient : Arrays.stream(soup.ingredients()).distinct().toList()) {
                    int batchCount = (int) Arrays.stream(soup.ingredients()).filter(item -> item == ingredient).count();
                    helper.assertValueEqual(fixture.count(ingredient), batchCount, "Every ingredient is consumed once while the second batch remains: " + BuiltInRegistries.ITEM.getKey(ingredient));
                }
                if (soup.output() == Items.SUSPICIOUS_STEW)
                    helper.assertValueEqual(fixture.find(soup.output()).get(DataComponents.SUSPICIOUS_STEW_EFFECTS),
                            SuspiciousEffectHolder.tryGet(Items.DANDELION).getSuspiciousEffects(), "Taking the result retains its actual flower-effect component");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void synchronizedRecipesKeepWaterRequirementsManufacturingMetadataAndFlowerEffects(GameTestHelper helper) {
        for (Soup soup : soups()) {
            CraftingRecipe original = recipe(helper, soup.id());
            CraftingRecipe decoded = roundTrip(helper, original);
            CraftingInput input = grid(soup.ingredients());
            helper.assertTrue(decoded instanceof FoodManufacturingRecipe, "Recipe synchronization retains the shared food manufacturing interface");
            helper.assertValueEqual(((FoodManufacturingRecipe) decoded).manufacturing(), ((FoodManufacturingRecipe) original).manufacturing(),
                    "Wire synchronization preserves the whole timing and contained-vessel policy");
            helper.assertTrue(decoded.matches(input, helper.getLevel()), "The synchronized recipe still requires and accepts its water bowl");
            helper.assertTrue(ItemStack.matches(decoded.assemble(input, helper.getLevel().registryAccess()), original.assemble(input, helper.getLevel().registryAccess())),
                    "Synchronized assembly preserves the output and all native suspicious-stew effects");
            helper.assertTrue(decoded.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "The client recipe retains the no-duplicate-bowl policy");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void optionalJeiFlowerPagesUseWaterAndOnlyHideTheOldSyntheticEmptyBowlPages(GameTestHelper helper) {
        var source = new RecipeHolder<CraftingRecipe>(ResourceLocation.withDefaultNamespace("suspicious_stew"),
                recipe(helper, "minecraft:suspicious_stew"));
        var pages = SuspiciousStewJeiRecipes.variants(source, helper.getLevel());
        helper.assertValueEqual((long) pages.size(), BuiltInRegistries.ITEM.stream()
                .filter(item -> new ItemStack(item).is(ItemTags.SMALL_FLOWERS)).count(),
                "The common optional viewer exposes every actual flower without requiring client or JEI classes");
        for (var page : pages) {
            helper.assertTrue(SuspiciousStewJeiRecipes.isDisplayVariant(page.id()), "Each dynamic flower gets a namespaced display-only identity");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(page.id()).isEmpty(), "Display pages never add duplicate playable manufacturing recipes");
            List<ItemStack> ingredients = page.value().getIngredients().stream().map(ingredient -> ingredient.getItems()[0].copy()).toList();
            helper.assertValueEqual(ingredients.stream().filter(stack -> stack.is(MiteFoods.get("water_bowl"))).count(), 1L,
                    "The viewer shows exactly one filled water bowl");
            helper.assertFalse(ingredients.stream().anyMatch(stack -> stack.is(Items.BOWL)), "The replacement page cannot advertise a dry empty bowl");
            CraftingInput input = CraftingInput.of(2, 2, ingredients);
            helper.assertTrue(source.value().matches(input, helper.getLevel()) && page.value().matches(input, helper.getLevel()),
                    "Each viewer page represents a genuinely craftable flower combination");
            helper.assertTrue(ItemStack.matches(page.value().getResultItem(helper.getLevel().registryAccess()),
                    source.value().assemble(input, helper.getLevel().registryAccess())),
                    "The visible flower result retains the true server effect component and duration");
            helper.assertTrue(page.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
                    "The viewer's vessel accounting matches the actual contained-bowl output");
            helper.assertFalse(SuspiciousStewJeiRecipes.isObsoleteVanillaPage(page, helper.getLevel()), "New water-bowl pages remain visible");
        }
        var dryIngredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.BROWN_MUSHROOM),
                Ingredient.of(Items.RED_MUSHROOM), Ingredient.of(Items.BOWL), Ingredient.of(Items.DANDELION));
        CraftingRecipe dry = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.SUSPICIOUS_STEW), dryIngredients);
        ResourceLocation oldId = ResourceLocation.withDefaultNamespace("jei.suspicious.stew.minecraft.dandelion");
        helper.assertTrue(SuspiciousStewJeiRecipes.isObsoleteVanillaPage(new RecipeHolder<>(oldId, dry), helper.getLevel()),
                "Only the old JEI-generated vanilla empty-bowl page is hidden");
        helper.assertFalse(SuspiciousStewJeiRecipes.isObsoleteVanillaPage(
                new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("other", oldId.getPath()), dry), helper.getLevel()),
                "An unrelated mod's recipe with the same path is not removed");
        helper.assertFalse(SuspiciousStewJeiRecipes.isObsoleteVanillaPage(
                new RecipeHolder<>(ResourceLocation.withDefaultNamespace("other_suspicious_stew"), dry), helper.getLevel()),
                "An unrelated recipe identifier is not removed merely because it outputs stew");
        helper.assertFalse(SuspiciousStewJeiRecipes.isObsoleteVanillaPage(
                new RecipeHolder<>(oldId, pages.getFirst().value()), helper.getLevel()),
                "A water-bowl recipe remains visible even when given the former synthetic identifier");
        CraftingRecipe otherOutput = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.MUSHROOM_STEW), dryIngredients);
        helper.assertFalse(SuspiciousStewJeiRecipes.isObsoleteVanillaPage(new RecipeHolder<>(oldId, otherOutput), helper.getLevel()),
                "The precise obsolete-page check does not remove another output type");
        helper.assertFalse(SuspiciousStewJeiRecipes.isDisplayVariant(oldId), "Native generated pages cannot masquerade as MITE display variants");
        var workbenchPages = WorkbenchJeiRecipes.build(helper.getLevel()).stream()
                .filter(page -> SuspiciousStewJeiRecipes.isDisplayVariant(page.displayId())).toList();
        for (var profile : WorkbenchJeiRecipes.profiles().values()) {
            var stationPages = workbenchPages.stream().filter(page -> page.workstationId().equals(profile.id())).toList();
            helper.assertValueEqual(stationPages.size(), pages.size(), "Each workbench and the inventory expose all real flower variants");
            helper.assertValueEqual(stationPages.stream().map(page -> page.displayId()).distinct().count(), (long) pages.size(),
                    "Distinct flower effects have distinct viewer identities within each workstation");
        }
        for (var page : workbenchPages) {
            helper.assertValueEqual(page.source().id(), source.id(), "A dynamic display retains the real server recipe identity");
            helper.assertTrue(page.source().value() == source.value(), "The page references the actual loaded dynamic recipe");
            CraftingInput input = CraftingInput.of(page.width(), page.height(), page.inputs().stream()
                    .map(slot -> slot.isEmpty() ? ItemStack.EMPTY : slot.getFirst().copy()).toList());
            helper.assertTrue(page.source().value().matches(input, helper.getLevel()), "Displayed flower ingredients match the real server source");
            helper.assertTrue(ItemStack.matches(page.output(), page.source().value().assemble(input, helper.getLevel().registryAccess())),
                    "Each displayed output and effect is reproduced by the referenced server recipe");
            helper.assertTrue(page.remainders().isEmpty(), "The actual source recipe keeps viewer remainders free of duplicated bowls");
        }
        helper.succeed();
    }

    private static CraftingRecipe recipe(GameTestHelper helper, String id) {
        return (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow().value();
    }

    private static CraftingInput grid(Item... ingredients) {
        List<ItemStack> slots = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int index = 0; index < ingredients.length; index++) slots.set(index, new ItemStack(ingredients[index]));
        return CraftingInput.of(3, 3, slots);
    }

    private static CraftingInput square(Item... ingredients) {
        return CraftingInput.of(2, 2, Arrays.stream(ingredients).map(ItemStack::new).toList());
    }

    @SuppressWarnings("unchecked")
    private static CraftingRecipe roundTrip(GameTestHelper helper, CraftingRecipe original) {
        RecipeSerializer<CraftingRecipe> serializer = (RecipeSerializer<CraftingRecipe>) original.getSerializer();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            serializer.streamCodec().encode(buffer, original);
            CraftingRecipe decoded = serializer.streamCodec().decode(buffer);
            helper.assertValueEqual(buffer.readableBytes(), 0, "The registered serializer consumes exactly its recipe packet");
            return decoded;
        } finally {
            buffer.release();
        }
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
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-water-soup"), false);
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
            menu = new CraftingMenu(63, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
            grid = (CraftingContainer) menu.getSlot(1).container;
        }

        private void load(Soup soup) {
            TimedCraftingController.cancel(player);
            grid.clearContent();
            player.getInventory().clearContent();
            player.containerMenu.setCarried(ItemStack.EMPTY);
            for (int slot = 0; slot < soup.ingredients().length; slot++) grid.setItem(slot, new ItemStack(soup.ingredients()[slot], 2));
            helper.assertTrue(menu.getSlot(0).getItem().is(soup.output()), "The actual menu preview finds the matching modern soup");
        }

        private void tick(int count) {
            for (int index = 0; index < count; index++) TimedCraftingController.tick(player);
        }

        private int count(Item item) {
            int result = player.getInventory().countItem(item);
            if (menu.getCarried().is(item)) result += menu.getCarried().getCount();
            for (int slot = 0; slot < grid.getContainerSize(); slot++) if (grid.getItem(slot).is(item)) result += grid.getItem(slot).getCount();
            return result;
        }

        private ItemStack find(Item item) {
            if (menu.getCarried().is(item)) return menu.getCarried();
            for (ItemStack stack : player.getInventory().items) if (stack.is(item)) return stack;
            return ItemStack.EMPTY;
        }

        @Override public void close() {
            try {
                TimedCraftingController.forget(player);
                grid.clearContent();
                player.getInventory().clearContent();
                menu.setCarried(ItemStack.EMPTY);
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
            } finally {
                helper.getLevel().setBlockAndUpdate(table, previous);
                channel.finishAndReleaseAll();
            }
        }
    }
}
