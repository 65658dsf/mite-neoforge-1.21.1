package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.campfire.CampfireController;
import io.github.ningmeng123.mite.campfire.CampfireFuelAccess;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent survival campfire vectors through native slots, ticking, interaction and persistence. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CampfireSurvivalGameTests {
    private CampfireSurvivalGameTests() {}

    @GameTest(template = "empty")
    public static void storedWoodFuelCapsAtThreeAndDoesNotIgniteAnExtinguishedCampfire(GameTestHelper helper) {
        for (Block block : fires()) try (Fixture f = new Fixture(helper, block)) {
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 0, "A fresh unlit block entity starts without fuel");
            helper.assertValueEqual(CampfireController.refuel(f.campfire, 1, false), 1, "One wood portion adds one fuel point");
            helper.assertFalse(f.lit(), "Adding wood alone does not ignite an extinguished campfire");
            helper.assertValueEqual(CampfireController.refuel(f.campfire, 20, false), 2, "Even an external bulk refill cannot exceed the remaining two-point capacity");
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 3, "Fuel capacity is exactly three points");
            helper.assertValueEqual(CampfireController.refuel(f.campfire, 1, false), 0, "Full storage refuses another fuel portion");
            f.tick(1000);
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 3, "Stored fuel does not burn while the campfire is unlit");
            helper.assertValueEqual(CampfireController.refuel(f.campfire, 3, true), 0, "Igniting already full storage does not fabricate another fuel point");
            helper.assertTrue(f.lit(), "A valid ignition still lights a full but extinguished campfire");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oneFuelPointBurnsForExactlyFortyFiveSecondsEvenWhenEmpty(GameTestHelper helper) {
        for (Block block : fires()) try (Fixture f = new Fixture(helper, block)) {
            CampfireController.refuel(f.campfire, 1, true);
            f.tick(899);
            helper.assertTrue(f.lit(), "A fuel point remains burning before tick 900");
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 1, "No fuel point expires before its complete 45-second lifetime");
            f.tick(1);
            helper.assertFalse(f.lit(), "Idle burning extinguishes exactly when the last fuel point reaches 900 ticks");
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 0, "Idle burning consumes the last stored point");
            CampfireController.refuel(f.campfire, 1, false);
            f.tick(1);
            helper.assertFalse(f.lit(), "Adding wood after fuel exhaustion does not restart the fire");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cookingAlwaysTakesFifteenSecondsAndTwoMeatsConsumeOneFuelPoint(GameTestHelper helper) {
        for (Block block : fires()) try (Fixture f = new Fixture(helper, block)) {
            CampfireController.refuel(f.campfire, 3, true);
            helper.assertTrue(f.campfire.placeFood(null, new ItemStack(Items.BEEF), 1), "The actual insertion accepts raw beef");
            helper.assertTrue(f.campfire.placeFood(null, new ItemStack(Items.PORKCHOP), 999999), "The actual insertion accepts a second meat independently of caller timing");
            f.tick(299);
            helper.assertValueEqual(f.dropped(Items.COOKED_BEEF) + f.dropped(Items.COOKED_PORKCHOP), 0,
                    "Neither one-tick nor excessive caller cooking times override the 300-tick survival rule");
            f.tick(1);
            helper.assertValueEqual(f.dropped(Items.COOKED_BEEF), 1, "The first meat cooks at exactly 15 seconds");
            helper.assertValueEqual(f.dropped(Items.COOKED_PORKCHOP), 1, "The second meat cooks at exactly 15 seconds");
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 2, "Two completed meat items consume exactly one fuel point before its idle deadline");
            helper.assertTrue(f.campfire.getItems().stream().allMatch(ItemStack::isEmpty), "A completed batch leaves no hidden duplicate raw input");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sequentialMealsShareTheSameTwoMeatFuelQuota(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Blocks.CAMPFIRE)) {
            CampfireController.refuel(f.campfire, 1, true);
            f.campfire.placeFood(null, new ItemStack(Items.CHICKEN), 1);
            f.tick(300);
            helper.assertValueEqual(f.dropped(Items.COOKED_CHICKEN), 1, "The first single-meat batch actually cooks");
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 1, "One cooked meat does not consume an entire two-meat fuel quota");
            helper.assertValueEqual(f.fuel().mite$cookedMeat(), 1, "The remaining quota is stored across separate insertion actions");
            f.campfire.placeFood(null, new ItemStack(Items.RABBIT), 1);
            f.tick(300);
            helper.assertValueEqual(f.dropped(Items.COOKED_RABBIT), 1, "The second single-meat batch actually cooks");
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 0, "The second sequential meat exhausts the original fuel point");
            helper.assertFalse(f.lit(), "The quota extinguishes the last point before its 45-second idle lifetime");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void simultaneousCompletionCarriesQuotaIntoTheNextPointOrReturnsTheUnfundedMeatRaw(GameTestHelper helper) {
        for (int availablePoints : new int[]{1, 2}) try (Fixture f = new Fixture(helper, Blocks.CAMPFIRE)) {
            CampfireController.refuel(f.campfire, availablePoints, true);
            f.campfire.placeFood(null, new ItemStack(Items.BEEF), 1); f.tick(300);
            helper.assertValueEqual(f.fuel().mite$cookedMeat(), 1, "The current fuel point already paid for one completed meat");
            helper.assertTrue(f.campfire.placeFood(null, new ItemStack(Items.CHICKEN), 1), "The first position accepts a second batch");
            helper.assertTrue(f.campfire.placeFood(null, new ItemStack(Items.PORKCHOP), 1), "The second position accepts the simultaneous batch");
            f.tick(300);
            helper.assertValueEqual(f.dropped(Items.COOKED_CHICKEN), 1, "The next completed meat consumes the original point's final quota");
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), availablePoints - 1, "Exhausting a meat quota consumes exactly one point");
            if (availablePoints == 1) {
                helper.assertValueEqual(f.dropped(Items.COOKED_PORKCHOP), 0, "The final point cannot grant a third cooked meat in the same tick");
                helper.assertValueEqual(f.dropped(Items.PORKCHOP), 1, "An unfunded simultaneous completion returns the original pork raw");
                helper.assertFalse(f.lit(), "The last quota extinguishes the fire before processing the second position");
            } else {
                helper.assertValueEqual(f.dropped(Items.COOKED_PORKCHOP), 1, "A remaining fuel point funds the simultaneous second position");
                helper.assertValueEqual(f.dropped(Items.PORKCHOP), 0, "Funded cooking never also drops its raw input");
                helper.assertValueEqual(f.fuel().mite$cookedMeat(), 1, "The simultaneous second position belongs to the next point's quota");
                helper.assertValueEqual(f.fuel().mite$fuelTicks(), 900, "Switching by quota grants the next point its complete idle lifetime");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void toppingUpReserveNeverResetsTheBurningPointsTimerOrMeatQuota(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Blocks.CAMPFIRE)) {
            CampfireController.refuel(f.campfire, 1, true);
            f.campfire.placeFood(null, new ItemStack(Items.BEEF), 1); f.tick(350);
            helper.assertValueEqual(f.fuel().mite$fuelTicks(), 550, "The original point has already burned for 350 ticks");
            helper.assertValueEqual(f.fuel().mite$cookedMeat(), 1, "One meat has already spent half the point's quota");
            helper.assertValueEqual(CampfireController.refuel(f.campfire, 2, false), 2, "Additional wood fuel only increases reserve");
            helper.assertValueEqual(f.fuel().mite$fuelTicks(), 550, "Wood refilling cannot extend the currently burning point");
            helper.assertValueEqual(f.fuel().mite$cookedMeat(), 1, "Wood refilling cannot erase previously cooked meat");
            CampfireController.refuel(f.campfire, 3, true);
            helper.assertValueEqual(f.fuel().mite$fuelTicks(), 550, "Repeated ignition cannot reset an active point's timer");
            helper.assertValueEqual(f.fuel().mite$cookedMeat(), 1, "Repeated ignition cannot reset an active point's meat quota");
            f.tick(550);
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 2, "Only the original point expires at its original deadline");
            helper.assertValueEqual(f.fuel().mite$fuelTicks(), 900, "The next reserved point starts a fresh 45-second lifetime");
            helper.assertValueEqual(f.fuel().mite$cookedMeat(), 0, "The next point starts with its own full two-meat quota");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualInsertionLimitsCookingToTwoSlotsWithoutConsumingAThirdMeat(GameTestHelper helper) {
        for (Block block : fires()) try (Fixture f = new Fixture(helper, block)) {
            CampfireController.refuel(f.campfire, 3, true);
            helper.assertTrue(f.campfire.placeFood(null, new ItemStack(Items.BEEF), 1), "First actual cooking position is available");
            helper.assertTrue(f.campfire.placeFood(null, new ItemStack(Items.MUTTON), 1), "Second actual cooking position is available");
            ItemStack third = new ItemStack(Items.CHICKEN, 3);
            helper.assertTrue(f.campfire.getCookableRecipe(third).isEmpty(), "The real recipe query reports the two-position limit");
            helper.assertFalse(f.campfire.placeFood(null, third, 1), "Direct callers cannot insert into the two unused vanilla positions");
            helper.assertValueEqual(third.getCount(), 3, "Rejecting a third meat never consumes it");
            helper.assertValueEqual(f.campfire.getItems().stream().filter(stack -> !stack.isEmpty()).count(), 2L, "Only two native positions contain input");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cookableAnimalsIncludeFishAndWormsButExcludeOtherNativeCookingRecipes(GameTestHelper helper) {
        Map<Item, Item> meals = new LinkedHashMap<>();
        meals.put(Items.BEEF, Items.COOKED_BEEF); meals.put(Items.PORKCHOP, Items.COOKED_PORKCHOP);
        meals.put(Items.CHICKEN, Items.COOKED_CHICKEN); meals.put(Items.MUTTON, Items.COOKED_MUTTON);
        meals.put(Items.RABBIT, Items.COOKED_RABBIT); meals.put(Items.COD, Items.COOKED_COD);
        meals.put(Items.SALMON, Items.COOKED_SALMON);
        meals.put(MiteFoods.RAW_LARGE_FISH.get(), MiteFoods.COOKED_LARGE_FISH.get());
        meals.put(MiteFoods.RAW_WORM.get(), MiteFoods.COOKED_WORM.get());
        for (var meal : meals.entrySet()) try (Fixture f = new Fixture(helper, Blocks.CAMPFIRE)) {
            CampfireController.refuel(f.campfire, 1, true);
            helper.assertTrue(f.campfire.getCookableRecipe(new ItemStack(meal.getKey())).isPresent(), "Real recipe lookup accepts the animal ingredient " + meal.getKey());
            helper.assertTrue(f.campfire.placeFood(null, new ItemStack(meal.getKey()), 1), "Real insertion accepts the animal ingredient " + meal.getKey());
            f.tick(300);
            helper.assertValueEqual(f.dropped(meal.getValue()), 1, "The actual recipe produces the expected cooked animal food " + meal.getKey());
        }
        for (Block block : fires()) try (Fixture f = new Fixture(helper, block)) {
            CampfireController.refuel(f.campfire, 3, true);
            for (Item input : new Item[]{Items.POTATO, Items.KELP, Items.ROTTEN_FLESH, Items.TROPICAL_FISH,
                    Items.PUFFERFISH, Items.COOKED_BEEF, Items.IRON_ORE}) {
                ItemStack rejected = new ItemStack(input);
                helper.assertTrue(f.campfire.getCookableRecipe(rejected).isEmpty(), "Only cookable raw animal ingredients pass the campfire query: " + input);
                helper.assertFalse(f.campfire.placeFood(null, rejected, 1), "Direct insertion cannot bypass the raw-meat gate: " + input);
                helper.assertValueEqual(rejected.getCount(), 1, "A rejected ingredient is preserved in the caller stack");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void injectedNonMeatAndExtraPositionsReturnOriginalStacksWithoutCooking(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Blocks.CAMPFIRE)) {
            CampfireController.refuel(f.campfire, 3, true);
            f.campfire.getItems().set(0, new ItemStack(Items.POTATO, 4));
            f.campfire.getItems().set(1, new ItemStack(Items.KELP, 2));
            f.campfire.getItems().set(2, new ItemStack(Items.BEEF));
            f.campfire.getItems().set(3, new ItemStack(Items.IRON_ORE));
            f.tick(1);
            helper.assertValueEqual(f.dropped(Items.POTATO), 4, "Forced non-meat positions safely return the entire original raw stack");
            helper.assertValueEqual(f.dropped(Items.KELP), 2, "A second forced non-meat position returns unchanged");
            helper.assertValueEqual(f.dropped(Items.BEEF), 1, "A raw meat forced into an unused extra position is returned raw");
            helper.assertValueEqual(f.dropped(Items.IRON_ORE), 1, "Forced ore cannot bypass the furnace progression");
            f.tick(300);
            helper.assertValueEqual(f.dropped(Items.BAKED_POTATO) + f.dropped(Items.DRIED_KELP) + f.dropped(Items.COOKED_BEEF), 0,
                    "No rejected or excess input turns into an unauthorized cooked result");
            helper.assertValueEqual(f.fuel().mite$cookedMeat(), 0, "Returning invalid inputs does not spend the meat quota");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extinguishingImmediatelyReturnsTheOriginalComponentBearingRawMeat(GameTestHelper helper) {
        for (Block block : fires()) try (Fixture f = new Fixture(helper, block)) {
            CampfireController.refuel(f.campfire, 3, true);
            ItemStack raw = new ItemStack(Items.BEEF);
            raw.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 campfire interruption"));
            f.campfire.placeFood(null, raw, 1); f.tick(150);
            BlockState lit = f.state();
            CampfireBlock.dowse(null, helper.getLevel(), f.pos, lit);
            helper.getLevel().setBlockAndUpdate(f.pos, lit.setValue(CampfireBlock.LIT, false));
            CampfireBlockEntity.cooldownTick(helper.getLevel(), f.pos, f.state(), f.campfire);
            helper.assertTrue(f.campfire.getItems().stream().allMatch(ItemStack::isEmpty), "Native extinguishing ejects all cooking items immediately");
            helper.assertValueEqual(f.dropped(Items.BEEF), 1, "Interrupted cooking drops exactly the original raw meat");
            helper.assertValueEqual(f.dropped(Items.COOKED_BEEF), 0, "No partial cooking is granted after extinguishing");
            var dropped = f.capture.live(ItemEntity.class).stream().filter(entity -> entity.getItem().is(Items.BEEF)).findFirst().orElseThrow().getItem();
            helper.assertValueEqual(dropped.getHoverName().getString(), "NingMeng123 campfire interruption", "Raw input components survive interruption");
            CompoundTag saved = f.campfire.saveWithFullMetadata(helper.getLevel().registryAccess());
            for (int progress : saved.getIntArray("CookingTimes")) helper.assertValueEqual(progress, 0, "Every native cooking progress slot resets on extinguishing");
            f.tick(300);
            helper.assertValueEqual(f.dropped(Items.BEEF), 1, "Further unlit ticks never duplicate returned meat");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeWaterloggingExtinguishesAndReturnsUnfinishedMeat(GameTestHelper helper) {
        for (Block block : fires()) try (Fixture f = new Fixture(helper, block)) {
            CampfireController.refuel(f.campfire, 1, true);
            f.campfire.placeFood(null, new ItemStack(Items.PORKCHOP), 1); f.tick(100);
            helper.assertTrue(((CampfireBlock) block).placeLiquid(helper.getLevel(), f.pos, f.state(), Fluids.WATER.getSource(false)), "The native waterlogging path accepts water");
            CampfireBlockEntity.cooldownTick(helper.getLevel(), f.pos, f.state(), f.campfire);
            helper.assertFalse(f.lit(), "Waterlogging puts out the campfire");
            helper.assertValueEqual(f.dropped(Items.PORKCHOP), 1, "Water interruption returns unfinished pork raw");
            CampfireController.refuel(f.campfire, 3, true);
            helper.assertFalse(f.lit(), "Refueling cannot ignite a waterlogged campfire");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exposedRainImmediatelyExtinguishesWhileARealRoofProtectsTheFire(GameTestHelper helper) {
        float rain = helper.getLevel().rainLevel, previousRain = helper.getLevel().oRainLevel;
        try {
            helper.getLevel().setRainLevel(0);
            BlockPos reference = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos exposed = new BlockPos(reference.getX(), 300, reference.getZ());
            for (Block block : fires()) try (Fixture f = new Fixture(helper, block, exposed)) {
                CampfireController.refuel(f.campfire, 1, true);
                f.campfire.placeFood(null, new ItemStack(Items.BEEF), 1); f.tick(100);
                helper.getLevel().setRainLevel(1);
                f.tick(1);
                helper.assertTrue(f.lit(), "A real solid roof protects a campfire from rain");
                helper.getLevel().setBlockAndUpdate(f.pos.above(2), Blocks.AIR.defaultBlockState());
                helper.assertTrue(helper.getLevel().isRainingAt(f.pos.above()), "The real world reports rain exposure after removing the roof");
                f.tick(1);
                helper.assertFalse(f.lit(), "An exposed campfire is put out on its first rainy server tick");
                helper.assertValueEqual(f.dropped(Items.BEEF), 1, "Rain returns an unfinished meat raw immediately");
                helper.assertValueEqual(f.dropped(Items.COOKED_BEEF), 0, "Rain does not grant a cooked result");
                CampfireController.refuel(f.campfire, 3, true);
                helper.assertFalse(f.lit(), "Exposed rain prevents another ignition");
                helper.getLevel().setRainLevel(0);
            }
        } finally {
            helper.getLevel().rainLevel = rain;
            helper.getLevel().oRainLevel = previousRain;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fuelProgressAndCookingQuotaSurviveNativeSaveLoadAndClientUpdateTags(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, Blocks.CAMPFIRE)) {
            CampfireController.refuel(f.campfire, 3, true);
            f.campfire.placeFood(null, new ItemStack(Items.BEEF), 1); f.tick(300);
            f.campfire.placeFood(null, new ItemStack(Items.CHICKEN), 1); f.tick(75);
            int points = f.fuel().mite$fuelPoints(), ticks = f.fuel().mite$fuelTicks(), cooked = f.fuel().mite$cookedMeat();
            CompoundTag saved = f.campfire.saveWithFullMetadata(helper.getLevel().registryAccess());
            f.campfire = (CampfireBlockEntity) BlockEntity.loadStatic(f.pos, f.state(), saved, helper.getLevel().registryAccess());
            helper.assertTrue(f.campfire != null, "Actual saved block entity resolves its registered campfire type");
            helper.getLevel().setBlockEntity(f.campfire);
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), points, "Native save/load preserves fuel points");
            helper.assertValueEqual(f.fuel().mite$fuelTicks(), ticks, "Native save/load preserves the current point's exact idle timer");
            helper.assertValueEqual(f.fuel().mite$cookedMeat(), cooked, "Native save/load preserves the quota after the first completed meat");
            var client = new CampfireBlockEntity(f.pos, f.state());
            client.loadWithComponents(f.campfire.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            var synced = (CampfireFuelAccess) client;
            helper.assertValueEqual(synced.mite$fuelPoints(), points, "Client update tags include the fuel points");
            helper.assertValueEqual(synced.mite$fuelTicks(), ticks, "Client update tags include the current point's timer");
            helper.assertValueEqual(synced.mite$cookedMeat(), cooked, "Client update tags include the cooking quota");
            f.tick(224);
            helper.assertValueEqual(f.dropped(Items.COOKED_CHICKEN), 0, "Loading does not prematurely finish the partially cooked second meat");
            f.tick(1);
            helper.assertValueEqual(f.dropped(Items.COOKED_CHICKEN), 1, "Loading resumes the native cooking progress at the exact remaining tick");
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 2, "Reloading cannot reset the previous meat and gain an extra free cooking quota");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyLitCampfiresMigrateOnceButSavedEmptyCampfiresNeverRegainFuel(GameTestHelper helper) {
        for (Block block : fires()) try (Fixture f = new Fixture(helper, block)) {
            CompoundTag legacy = f.campfire.saveWithFullMetadata(helper.getLevel().registryAccess());
            legacy.remove("MiteCampfire");
            helper.getLevel().setBlockAndUpdate(f.pos, f.state().setValue(CampfireBlock.LIT, true));
            f.reload(legacy);
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 3, "A legacy lit campfire without the new fields receives the migration reserve");
            helper.assertValueEqual(f.fuel().mite$fuelTicks(), 900, "Legacy migration starts one complete point lifetime");
            f.tick(2700);
            helper.assertFalse(f.lit(), "The migrated three-point reserve is finite");
            CompoundTag exhausted = f.campfire.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.assertValueEqual(exhausted.getCompound("MiteCampfire").getInt("Version"), 1, "Fuel persistence includes an explicit schema version");
            f.reload(exhausted);
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 0, "Reloading exhausted new-format data never grants another migration reserve");
            helper.assertValueEqual(f.fuel().mite$fuelTicks(), 0, "An exhausted reload has no phantom timer");
            f.tick(1);
            helper.assertFalse(f.lit(), "Reloading never relights exhausted new-format data");
            f.reload(legacy);
            helper.assertValueEqual(f.fuel().mite$fuelPoints(), 0, "An old unlit campfire also migrates without a free reserve");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualWoodFlintAndTorchInteractionsFollowTheirDistinctRefillRules(GameTestHelper helper) {
        try (Session session = player(helper)) {
            var player = session.player();
            for (Block block : fires()) try (Fixture f = new Fixture(helper, block)) {
                ItemStack wood = new ItemStack(Items.OAK_PLANKS, 5); player.setItemInHand(InteractionHand.MAIN_HAND, wood);
                f.use(player);
                helper.assertValueEqual(wood.getCount(), 4, "An actual wood interaction consumes one wooden fuel item");
                helper.assertValueEqual(f.fuel().mite$fuelPoints(), 1, "An actual wood interaction adds only one point");
                helper.assertFalse(f.lit(), "Wood cannot directly rekindle a fire");
                ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL); player.setItemInHand(InteractionHand.MAIN_HAND, flint);
                f.use(player);
                helper.assertTrue(f.lit(), "The actual flint-and-steel interaction ignites the campfire");
                helper.assertValueEqual(f.fuel().mite$fuelPoints(), 3, "Flint and steel restores fuel to the three-point cap");
                helper.assertValueEqual(flint.getDamageValue(), 1, "Igniting consumes exactly one point of flint-and-steel durability");
                f.tick(2700);
                helper.assertFalse(f.lit(), "The three flint-provided fuel points eventually expire");
                ItemStack torches = new ItemStack(Items.TORCH, 2); player.setItemInHand(InteractionHand.MAIN_HAND, torches);
                f.use(player);
                helper.assertTrue(f.lit(), "The actual torch interaction can relight an exhausted campfire");
                helper.assertValueEqual(torches.getCount(), 1, "A torch ignition consumes one torch");
                helper.assertValueEqual(f.fuel().mite$fuelPoints(), 1, "Torch ignition supplies one point rather than filling all three");
            }
        }
        helper.succeed();
    }

    private static Block[] fires() { return new Block[]{Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE}; }

    private static Session player(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-fire-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 2))));
            player.setNoGravity(true); player.setOnGround(true); player.getInventory().clearContent();
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll(); throw failure;
        }
    }

    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            } finally { channel.finishAndReleaseAll(); }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos pos;
        final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        final SpawnedEntityCapture capture;
        CampfireBlockEntity campfire;

        Fixture(GameTestHelper helper, Block block) {
            this(helper, block, helper.absolutePos(new BlockPos(2, 2, 2)));
        }
        Fixture(GameTestHelper helper, Block block, BlockPos position) {
            this.helper = helper; pos = position;
            AABB area = new AABB(pos).inflate(4);
            capture = new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ItemEntity && entity.getBoundingBox().intersects(area));
            remember(pos.below()); remember(pos); remember(pos.above(2));
            helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(pos.above(2), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState().setValue(CampfireBlock.LIT, false));
            campfire = (CampfireBlockEntity) helper.getLevel().getBlockEntity(pos);
        }

        void remember(BlockPos site) { originals.put(site.immutable(), helper.getLevel().getBlockState(site)); }
        BlockState state() { return helper.getLevel().getBlockState(pos); }
        boolean lit() { return state().getValue(CampfireBlock.LIT); }
        CampfireFuelAccess fuel() { return (CampfireFuelAccess) campfire; }
        void reload(CompoundTag saved) {
            campfire = (CampfireBlockEntity) BlockEntity.loadStatic(pos, state(), saved, helper.getLevel().registryAccess());
            helper.assertTrue(campfire != null, "Native metadata resolves the actual campfire block entity");
            helper.getLevel().setBlockEntity(campfire);
        }
        void tick(int count) {
            for (int tick = 0; tick < count; tick++) CampfireBlockEntity.cookTick(helper.getLevel(), pos, state(), campfire);
        }
        void use(ServerPlayer player) {
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        }
        int dropped(Item item) {
            return capture.live(ItemEntity.class).stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
        }
        @Override public void close() {
            campfire.clearContent();
            originals.forEach((site, state) -> helper.getLevel().setBlockAndUpdate(site, state));
            capture.close();
        }
    }
}
