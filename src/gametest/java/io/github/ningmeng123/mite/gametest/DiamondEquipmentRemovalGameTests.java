package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native acquisition and saved-state boundaries retire gear without deleting mineral resources. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DiamondEquipmentRemovalGameTests {
    private static final List<Item> REMOVED = List.of(Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_PICKAXE,
            Items.STONE_SHOVEL, Items.STONE_HOE, Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE,
            Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_HORSE_ARMOR,
            Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL,
            Items.NETHERITE_HOE, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

    private DiamondEquipmentRemovalGameTests() {}

    @GameTest(template = "empty")
    public static void finalLootPipelineRemovesAllRetiredGearTypesAndPreservesResources(GameTestHelper helper) {
        LootTable.Builder builder = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST);
        for (Item item : removedItems()) builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(item)));
        builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.DIAMOND)));
        builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.DIAMOND_BLOCK)));
        builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.IRON_SWORD)));
        builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.NETHERITE_INGOT)));
        builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)));
        LootTable table = builder.build();
        table.setLootTableId(id("diamond_removal_fixture"));
        List<ItemStack> actual = table.getRandomItems(params(helper, LootContextParamSets.CHEST), 1234L);
        helper.assertValueEqual(actual.size(), 5, "The final native loot return removes every retired gear entry and only those entries");
        helper.assertTrue(actual.stream().noneMatch(DiamondEquipmentRemovalGameTests::removed), "No retired equipment reaches loot consumers");
        helper.assertTrue(actual.stream().anyMatch(stack -> stack.is(Items.DIAMOND)), "Diamond gems remain available");
        helper.assertTrue(actual.stream().anyMatch(stack -> stack.is(Items.DIAMOND_BLOCK)), "Diamond storage blocks remain available");
        helper.assertTrue(actual.stream().anyMatch(stack -> stack.is(Items.IRON_SWORD)), "Other equipment is retained");
        helper.assertTrue(actual.stream().anyMatch(stack -> stack.is(Items.NETHERITE_INGOT)), "Netherite ingots remain available");
        helper.assertTrue(actual.stream().anyMatch(stack -> stack.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)), "Netherite templates are not equipment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realChestVaultAndTrialEquipmentLootCannotReintroduceDiamondGear(GameTestHelper helper) {
        for (String path : List.of("chests/end_city_treasure", "chests/bastion_treasure", "chests/ancient_city",
                "chests/trial_chambers/reward_rare", "chests/trial_chambers/reward_ominous_rare",
                "equipment/trial_chamber", "equipment/trial_chamber_melee")) {
            LootTable table = helper.getLevel().getServer().reloadableRegistries()
                    .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace(path)));
            helper.assertTrue(table != LootTable.EMPTY, "The real pinned vanilla loot table exists: " + path);
            var context = path.startsWith("equipment/") ? LootContextParamSets.EQUIPMENT : LootContextParamSets.CHEST;
            LootParams params = params(helper, context);
            for (long seed = 1; seed <= 32; seed++)
                helper.assertTrue(table.getRandomItems(params, seed).stream()
                        .noneMatch(DiamondEquipmentRemovalGameTests::removed), "Every final chest/vault/equipment roll is filtered: " + path);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void villagerOffersRejectGearOnEitherSideAndKeepDiamondMaterialTrades(GameTestHelper helper) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(villager != null, "Native villager is registered");
        try {
            MerchantOffers offers = new MerchantOffers();
            offers.add(offer(Items.EMERALD, null, Items.DIAMOND_SWORD));
            offers.add(offer(Items.DIAMOND_CHESTPLATE, null, Items.EMERALD));
            offers.add(offer(Items.EMERALD, Items.DIAMOND_BOOTS, Items.IRON_BOOTS));
            offers.add(offer(Items.EMERALD, null, Items.NETHERITE_SWORD));
            offers.add(offer(Items.NETHERITE_CHESTPLATE, null, Items.EMERALD));
            offers.add(offer(Items.EMERALD, Items.NETHERITE_BOOTS, Items.IRON_BOOTS));
            MerchantOffer diamonds = offer(Items.DIAMOND, null, Items.EMERALD);
            MerchantOffer purchaseDiamonds = offer(Items.EMERALD, null, Items.DIAMOND);
            MerchantOffer iron = offer(Items.EMERALD, null, Items.IRON_SWORD);
            offers.add(diamonds); offers.add(purchaseDiamonds); offers.add(iron);
            // This is the same already-populated offers field used after restoring a saved villager.
            villager.setOffers(offers);
            MerchantOffers actual = villager.getOffers();
            helper.assertValueEqual(actual.size(), 3, "Both sold and required retired equipment are removed from existing offers");
            helper.assertTrue(actual.contains(diamonds) && actual.contains(purchaseDiamonds) && actual.contains(iron),
                    "Diamond resources and other equipment retain their original trade objects and prices");
            // A later career update/addon can append a new offer to the same native list.
            actual.add(offer(Items.EMERALD, null, Items.DIAMOND_PICKAXE));
            helper.assertValueEqual(villager.getOffers().size(), 3, "The next native offer access also filters newly added forbidden results");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newlyGeneratedHighLevelVillagerOffersDoNotSellRetiredEquipment(GameTestHelper helper) {
        for (var profession : List.of(VillagerProfession.ARMORER, VillagerProfession.TOOLSMITH, VillagerProfession.WEAPONSMITH)) {
            for (int level : new int[]{3, 4, 5}) {
                Villager villager = EntityType.VILLAGER.create(helper.getLevel());
                helper.assertTrue(villager != null, "Native villager is registered");
                try {
                    villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(level));
                    for (MerchantOffer offer : villager.getOffers()) helper.assertTrue(!removed(offer.getResult())
                                    && !removed(offer.getCostA()) && !removed(offer.getCostB()),
                            "Real native career generation never offers diamond equipment on either side");
                } finally { villager.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeStackNbtLoadClearsAllRetiredGearWithoutChangingAllowedStacks(GameTestHelper helper) {
        for (Item item : removedItems()) {
            ItemStack original = new ItemStack(item);
            original.set(DataComponents.CUSTOM_NAME, Component.literal("Retired NingMeng123 fixture"));
            ItemStack loaded = load(helper, original);
            helper.assertTrue(loaded.isEmpty(), "Native ItemStack.parse removes saved retired gear: " + item);
        }
        for (Item item : List.of(Items.DIAMOND, Items.DIAMOND_ORE, Items.DEEPSLATE_DIAMOND_ORE, Items.DIAMOND_BLOCK,
                Items.IRON_SWORD, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.NETHERITE_BLOCK,
                Items.ANCIENT_DEBRIS, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) {
            ItemStack original = new ItemStack(item);
            original.set(DataComponents.CUSTOM_NAME, Component.literal("Allowed NingMeng123 fixture"));
            helper.assertTrue(ItemStack.matches(original, load(helper, original)), "Allowed resource/equipment stack retains every component: " + item);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedNestedShulkersAndBundlesRemoveGearAndRetainLegalContents(GameTestHelper helper) {
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 3);
        diamonds.set(DataComponents.CUSTOM_NAME, Component.literal("Nested NingMeng123 diamonds"));
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.NETHERITE_AXE), diamonds)));
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(
                List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.STONE, 5), bundle)));
        ItemStack loaded = load(helper, shulker);
        helper.assertTrue(loaded.is(Items.SHULKER_BOX), "The outer allowed storage item is never removed");
        List<ItemStack> slots = loaded.get(DataComponents.CONTAINER).stream().toList();
        helper.assertTrue(slots.get(0).isEmpty(), "A removed container entry leaves its original slot empty");
        helper.assertTrue(slots.get(1).is(Items.STONE) && slots.get(1).getCount() == 5, "Other container slots and counts are unchanged");
        ItemStack loadedBundle = slots.get(2);
        helper.assertTrue(loadedBundle.is(Items.BUNDLE), "Nested bundles survive the containing shulker load");
        List<ItemStack> remaining = new ArrayList<>();
        loadedBundle.get(DataComponents.BUNDLE_CONTENTS).items().forEach(remaining::add);
        helper.assertValueEqual(remaining.size(), 1, "Removed bundle entries do not leave illegal empty weighted entries");
        helper.assertTrue(ItemStack.matches(diamonds, remaining.getFirst()), "Deep legal contents keep their names and counts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entityJoinClearsRetiredDropsAndMobEquipmentButPreservesAllowedEquipment(GameTestHelper helper) {
        Vec3 location = location(helper);
        ItemEntity forbiddenDrop = new ItemEntity(helper.getLevel(), location.x, location.y, location.z, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
        ItemEntity gemDrop = new ItemEntity(helper.getLevel(), location.x, location.y, location.z, new ItemStack(Items.DIAMOND, 3));
        var zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "Native zombie is registered");
        var world = helper.getLevel().getServer().overworld();
        long time = world.getDayTime();
        var progress = io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData.get(world);
        long highest = progress.highestDay(); boolean dirty = progress.isDirty();
        try {
            // This retirement test exercises equipment preservation after the independent day-35 unlock.
            world.setDayTime(35L * 24000L); progress.restoreProgress(36L); // Isolate retired-item filtering safely after the first16-day equipment restriction.
            boolean accepted = helper.getLevel().addFreshEntity(forbiddenDrop);
            helper.assertTrue(!accepted || forbiddenDrop.isRemoved() || forbiddenDrop.getItem().isEmpty(),
                    "A retired item entity never becomes a usable world drop");
            helper.assertTrue(helper.getLevel().addFreshEntity(gemDrop) && gemDrop.getItem().is(Items.DIAMOND)
                    && gemDrop.getItem().getCount() == 3, "Diamond gem drops remain normal world entities");
            zombie.moveTo(location); zombie.setNoAi(true); zombie.setNoGravity(true);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "The ordinary mob still joins the world");
            helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && zombie.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    && zombie.getMainHandItem().isEmpty(), "Equipment directly assigned before spawn/load cannot bypass the retirement rule");
            helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "Legal explicitly equipped armor is preserved");
        } finally {
            forbiddenDrop.discard(); gemDrop.discard(); zombie.discard();
            world.setDayTime(time); progress.restoreProgress(highest); progress.setDirty(dirty);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realPlayerTickPurgesInventoryEnderChestArmorCursorAndNestedGear(GameTestHelper helper) {
        try (Session session = newPlayer(helper)) {
            var player = session.player();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 4));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.NETHERITE_SWORD));
            player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
            player.getEnderChestInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 4));
            ItemStack bundle = new ItemStack(Items.BUNDLE);
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.NETHERITE_HOE), new ItemStack(Items.STICK, 2))));
            player.getInventory().setItem(2, bundle);
            player.containerMenu.setCarried(new ItemStack(Items.NETHERITE_AXE));
            // The real network listener calls doTick(), which enters Player.tick and its NeoForge pre event.
            player.doTick();
            helper.assertTrue(player.getInventory().getItem(0).isEmpty() && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    && player.getOffhandItem().isEmpty(), "The actual player tick clears inventory, armor and offhand gear");
            helper.assertTrue(player.getEnderChestInventory().getItem(0).isEmpty(), "Existing ender chest gear is retired too");
            helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "The active menu cursor cannot carry a forbidden stack");
            helper.assertValueEqual(player.getInventory().getItem(1).getCount(), 4, "Diamond resources retain their entire inventory count");
            helper.assertValueEqual(player.getEnderChestInventory().getItem(1).getCount(), 4, "Other ender chest contents survive");
            List<ItemStack> contents = new ArrayList<>();
            player.getInventory().getItem(2).get(DataComponents.BUNDLE_CONTENTS).items().forEach(contents::add);
            helper.assertTrue(contents.size() == 1 && contents.getFirst().is(Items.STICK) && contents.getFirst().getCount() == 2,
                    "Newly injected nested contents are cleaned during player ticks without deleting the allowed storage item");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeEntityTickRemovesForbiddenGearAssignedAfterWorldJoin(GameTestHelper helper) {
        var level = helper.getLevel();
        var zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(zombie != null, "Native zombie is registered");
        Vec3 location = location(helper);
        ItemEntity drop = new ItemEntity(level, location.x, location.y, location.z, new ItemStack(Items.STONE));
        try {
            zombie.moveTo(location); zombie.setNoAi(true); zombie.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(zombie) && level.addFreshEntity(drop), "Legal entities first join normally");
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            drop.setItem(new ItemStack(Items.DIAMOND_HORSE_ARMOR));
            // ServerLevel owns the entity pre event; calling Entity.tick directly would bypass this real boundary.
            level.tickNonPassenger(zombie);
            level.tickNonPassenger(drop);
            helper.assertTrue(zombie.getMainHandItem().isEmpty() && zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                    "Later equipment assignments cannot bypass the native server entity tick cleanup");
            helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "Legal late equipment is preserved");
            helper.assertTrue(drop.isRemoved(), "Replacing an already joined item entity with forbidden gear removes the drop on its native tick");
        } finally { zombie.discard(); drop.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeManagerReplacementRejectsForbiddenStaticResultsAcrossBothIndexes(GameTestHelper helper) {
        RecipeManager manager = new RecipeManager(helper.getLevel().registryAccess());
        List<RecipeHolder<?>> fixtures = new ArrayList<>();
        List<Item> retiredItems = removedItems();
        for (int index = 0; index < retiredItems.size(); index++) fixtures.add(recipe("retired_" + index, retiredItems.get(index)));
        fixtures.add(recipe("diamond_resource", Items.DIAMOND));
        fixtures.add(recipe("other_equipment", Items.IRON_SWORD));
        manager.replaceRecipes(fixtures);
        helper.assertValueEqual(manager.getRecipes().size(), 2, "The native recipe map contains only allowed static outputs");
        helper.assertValueEqual(manager.getAllRecipesFor(RecipeType.CRAFTING).size(), 2, "The recipe type lookup is filtered together with the ID lookup");
        for (int index = 0; index < retiredItems.size(); index++) helper.assertTrue(manager.byKey(id("retired_" + index)).isEmpty(),
                "A dynamically replaced forbidden recipe cannot be found by ID");
        helper.assertTrue(manager.byKey(id("diamond_resource")).isPresent() && manager.byKey(id("other_equipment")).isPresent(),
                "Valid recipes remain registered after runtime replacement");
        helper.succeed();
    }

    private static RecipeHolder<?> recipe(String name, Item result) {
        return new RecipeHolder<>(id(name), new ShapelessRecipe("", CraftingBookCategory.MISC,
                new ItemStack(result), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.COBBLESTONE))));
    }

    private static MerchantOffer offer(Item first, Item second, Item result) {
        return new MerchantOffer(new ItemCost(first, 1), second == null ? Optional.empty() : Optional.of(new ItemCost(second, 1)),
                new ItemStack(result), 12, 1, .05F);
    }

    private static ItemStack load(GameTestHelper helper, ItemStack stack) {
        return ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
    }

    private static LootParams params(GameTestHelper helper, net.minecraft.world.level.storage.loot.parameters.LootContextParamSet type) {
        var builder = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, location(helper));
        if (type == LootContextParamSets.EQUIPMENT) builder.withParameter(LootContextParams.THIS_ENTITY, EntityType.ZOMBIE.create(helper.getLevel()));
        return builder.create(type);
    }

    private static Vec3 location(GameTestHelper helper) { return Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 12, 2))); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("mite", "diamond_removal_test/" + path); }
    private static boolean removed(ItemStack stack) { return !stack.isEmpty() && removedItems().stream().anyMatch(stack::is); }

    private static List<Item> removedItems() {
        List<Item> items = new ArrayList<>(REMOVED);
        if (SimplySwordsCompat.enabled()) {
            for (String family : List.of("longsword", "twinblade", "rapier", "katana", "sai", "spear", "glaive", "warglaive",
                    "cutlass", "claymore", "greataxe", "greathammer", "chakram", "scythe", "halberd")) {
                for (String material : List.of("diamond", "netherite")) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("simplyswords", material + "_" + family));
                    if (item == Items.AIR) throw new IllegalStateException("Missing native retired fixture: " + material + "/" + family);
                    items.add(item);
                }
            }
        }
        return items;
    }

    private static Session newPlayer(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "diamond-retire"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.moveTo(location(helper));
            player.setNoGravity(true); player.setOnGround(true);
            PlayerExperience.setTotal(player, 0); player.getFoodData().setFoodLevel(6);
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll(); throw failure;
        }
    }

    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try { if (player.server.getPlayerList().getPlayer(player.getUUID()) != null) player.server.getPlayerList().remove(player); }
            finally { channel.finishAndReleaseAll(); }
        }
    }
}
