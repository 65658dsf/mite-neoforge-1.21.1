package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTabs;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Independent native acquisition and gameplay checks for retired diamond and unique weapons. @author NingMeng123 */
@GameTestHolder("mite_simplyswords")
@PrefixGameTestTemplate(false)
public final class SimplySwordsUniqueRemovalGameTests {
    private static final List<String> FAMILIES = List.of("longsword", "twinblade", "rapier", "katana", "sai",
            "spear", "glaive", "warglaive", "cutlass", "claymore", "greataxe", "greathammer", "chakram", "scythe", "halberd");

    private SimplySwordsUniqueRemovalGameTests() {}

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void everyOfficialUniqueIsRetiredAndCannotReturnThroughSavedStacks(GameTestHelper h) throws IOException {
        if (absent(h)) return;
        Set<Item> uniques = officialUniqueItems(h);
        h.assertTrue(uniques.size() >= 57, "The complete official unique roster is loaded, including transformed relics and lichblades");
        h.assertTrue(uniques.contains(item("stormbringer")), "The screenshot's Stormbringer is part of the independent fixture");
        for (Item item : uniques) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Retired NingMeng123 unique"));
            h.assertTrue(RemovedDiamondEquipment.isRemoved(item) && RemovedDiamondEquipment.isRemoved(stack),
                    "Both registered-item and stack boundaries reject the official unique: " + BuiltInRegistries.ITEM.getKey(item));
            h.assertTrue(load(h, stack).isEmpty(), "Native saved-stack load cannot restore unique equipment: " + item);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void ordinaryFamiliesRunicFamiliesAndCraftingMaterialsRemainAvailable(GameTestHelper h) {
        if (absent(h)) return;
        for (String material : List.of("iron", "gold", "runic")) for (String family : FAMILIES) {
            ItemStack stack = new ItemStack(item(material + "_" + family));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Allowed NingMeng123 weapon"));
            h.assertFalse(RemovedDiamondEquipment.isRemoved(stack), "Every ordinary/runic weapon type remains available: " + material + "/" + family);
            h.assertTrue(ItemStack.matches(stack, load(h, stack)), "Allowed native families preserve their components across saves");
        }
        for (String path : List.of("runic_tablet", "runefused_gem", "netherfused_gem", "contained_remnant", "sword_on_a_stick")) {
            ItemStack stack = new ItemStack(item(path));
            h.assertFalse(RemovedDiamondEquipment.isRemoved(stack), "Unrelated material and ordinary starter items are retained: " + path);
            h.assertTrue(ItemStack.matches(stack, load(h, stack)), "Allowed starter/material item survives native saved-stack load: " + path);
        }
        for (String material : List.of("diamond", "netherite")) for (String family : FAMILIES) {
            ItemStack retired = new ItemStack(item(material + "_" + family));
            h.assertTrue(RemovedDiamondEquipment.isRemoved(retired.getItem()) && RemovedDiamondEquipment.isRemoved(retired),
                    "Both item and stack boundaries retire every diamond/netherite family: " + material + "/" + family);
            h.assertTrue(load(h, retired).isEmpty(), "Saved retired ordinary weapons cannot return: " + material + "/" + family);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void everyCreativeCategoryAndSearchExcludeRetiredWeaponsButRetainRunicAndOrdinaryWeapons(GameTestHelper h) throws IOException {
        if (absent(h)) return;
        Set<Item> retired = retiredItems(h);
        for (boolean permissions : new boolean[]{false, true}) {
            CreativeModeTabs.tryRebuildTabContents(h.getLevel().enabledFeatures(), permissions, h.getLevel().registryAccess());
            for (var tab : CreativeModeTabs.allTabs()) {
                h.assertTrue(tab.getDisplayItems().stream().noneMatch(stack -> retired.contains(stack.getItem())),
                        "Every creative category excludes diamond and unique weapons with either permission level: " + tab.getDisplayName().getString());
                h.assertTrue(tab.getSearchTabDisplayItems().stream().noneMatch(stack -> retired.contains(stack.getItem())),
                        "Every creative category's search entries exclude diamond and unique weapons");
            }
            var search = CreativeModeTabs.searchTab().getDisplayItems();
            for (String family : FAMILIES) for (String material : List.of("iron", "gold", "runic")) {
                Item allowed = item(material + "_" + family);
                h.assertTrue(search.stream().anyMatch(stack -> stack.is(allowed)), "Allowed family remains visible in global creative search: " + material + "/" + family);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void nativeRecipeLoadAndDynamicReplacementCannotProduceRetiredWeapons(GameTestHelper h) throws IOException {
        if (absent(h)) return;
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("unique_melting")).isEmpty(),
                "Unique-only melting cannot expose disabled weapons in material recipe pages");
        Set<Item> retired = retiredItems(h);
        for (var recipe : h.getLevel().getRecipeManager().getRecipes()) h.assertTrue(
                !retired.contains(recipe.value().getResultItem(h.getLevel().registryAccess()).getItem()),
                "The actual loaded recipe manager exposes no diamond or unique output: " + recipe.id());
        RecipeManager manager = new RecipeManager(h.getLevel().registryAccess());
        List<RecipeHolder<?>> fixtures = new ArrayList<>();
        for (Item item : retired) fixtures.add(recipe(BuiltInRegistries.ITEM.getKey(item).getPath(), item));
        fixtures.add(recipe("allowed_runic", item("runic_longsword")));
        fixtures.add(recipe("allowed_ordinary", item("iron_longsword")));
        fixtures.add(recipe("allowed_material", item("runic_tablet")));
        manager.replaceRecipes(fixtures);
        h.assertValueEqual(manager.getRecipes().size(), 3, "Runtime recipe replacement filters every diamond and unique result");
        h.assertValueEqual(manager.getAllRecipesFor(RecipeType.CRAFTING).size(), 3, "Both the ID index and crafting-type index remain filtered");
        for (Item item : retired) h.assertTrue(manager.byKey(testId(BuiltInRegistries.ITEM.getKey(item).getPath())).isEmpty(),
                "No dynamically introduced retired recipe remains addressable by ID");
        for (String name : List.of("allowed_runic", "allowed_ordinary", "allowed_material")) h.assertTrue(manager.byKey(testId(name)).isPresent(),
                "Allowed recipes remain addressable after runtime replacement: " + name);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void finalNativeLootReturnRejectsEveryDiamondAndUniqueAndPreservesAllowedLoot(GameTestHelper h) throws IOException {
        if (absent(h)) return;
        Set<Item> retired = retiredItems(h);
        var builder = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST);
        for (Item item : retired) builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(item)));
        for (String path : List.of("runic_longsword", "iron_longsword", "runic_tablet"))
            builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(item(path))));
        var table = builder.build();
        table.setLootTableId(testId("loot"));
        var params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, location(h)).create(LootContextParamSets.CHEST);
        var actual = table.getRandomItems(params, 8132L);
        h.assertValueEqual(actual.size(), 3, "The final loot boundary removes every diamond and unique entry while retaining legal outputs");
        h.assertTrue(actual.stream().noneMatch(stack -> retired.contains(stack.getItem())), "No retired weapon reaches native loot consumers");
        for (String path : List.of("runic_longsword", "iron_longsword", "runic_tablet")) h.assertTrue(actual.stream().anyMatch(stack -> stack.is(item(path))),
                "Allowed native loot survives the same final boundary: " + path);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void diamondWeaponsCannotBeSoldOrRequiredByRestoredTrades(GameTestHelper h) {
        if (absent(h)) return;
        var villager = EntityType.VILLAGER.create(h.getLevel());
        h.assertTrue(villager != null, "The native merchant fixture exists");
        try {
            MerchantOffers offers = new MerchantOffers();
            for (String family : FAMILIES) {
                Item diamond = item("diamond_" + family);
                offers.add(offer(Items.EMERALD, null, diamond));
                offers.add(offer(diamond, null, Items.EMERALD));
                offers.add(offer(Items.EMERALD, diamond, item("iron_" + family)));
            }
            MerchantOffer gems = offer(Items.EMERALD, null, Items.DIAMOND);
            MerchantOffer runic = offer(Items.EMERALD, null, item("runic_longsword"));
            offers.add(gems); offers.add(runic);
            villager.setOffers(offers);
            var actual = villager.getOffers();
            h.assertTrue(actual.size() == 2 && actual.contains(gems) && actual.contains(runic),
                    "All fifteen diamond families are removed from either trade cost and result while gems and runic weapons remain");
            actual.add(offer(Items.EMERALD, null, item("diamond_halberd")));
            h.assertValueEqual(villager.getOffers().size(), 2, "A later-added diamond sale is also filtered on native access");
        } finally { villager.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void separateGeneratedContainerPityRollCannotInsertUniquesAndStillAwardsRunicTablets(GameTestHelper h) throws ReflectiveOperationException {
        if (absent(h)) return;
        Class<?> manager = Class.forName("net.sweenus.simplyswords.loot.PityLootManager");
        var chooseUnique = manager.getDeclaredMethod("chooseUnique", RandomSource.class);
        chooseUnique.setAccessible(true);
        for (int seed = 0; seed < 32; seed++) h.assertTrue(chooseUnique.invoke(null, RandomSource.create(seed)) == null,
                "The separate post-unpack unique chooser cannot bypass the final loot-table filter");
        Class<?> pityType = Class.forName("net.sweenus.simplyswords.loot.PlayerPityState");
        Object pity = pityType.getConstructor().newInstance();
        pityType.getMethod("setUniqueMisses", int.class).invoke(pity, 1_000_000);
        pityType.getMethod("setTabletMisses", int.class).invoke(pity, 1_000_000);
        Class<?> modeType = Class.forName("net.sweenus.simplyswords.loot.PityLootManager$SimulationMode");
        Object mode = modeType.getField("PITY_PROGRESSION").get(null);
        var simulate = manager.getMethod("simulateGeneratedContainerLoot", ResourceKey.class, pityType, RandomSource.class, modeType);
        var table = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/end_city_treasure"));
        var actual = (List<?>)simulate.invoke(null, table, pity, RandomSource.create(8132L), mode);
        h.assertTrue(actual.size() == 1 && actual.getFirst() instanceof ItemStack stack && stack.is(item("runic_tablet")),
                "The actual container-generation roll skips unique hard pity and retains the independent runic-tablet hard pity");
        h.assertValueEqual(pityType.getMethod("tabletMisses").invoke(pity), 0, "The allowed tablet award still resets its own pity counter");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void actualPlayerTickClearsInventoryCursorEnderChestAndDeepContainerRetiredWeapons(GameTestHelper h) {
        if (absent(h)) return;
        try (Session session = newPlayer(h)) {
            var player = session.player();
            player.getInventory().setItem(0, new ItemStack(item("diamond_katana")));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(item("soulrender")));
            player.getEnderChestInventory().setItem(0, new ItemStack(item("diamond_spear")));
            player.containerMenu.setCarried(new ItemStack(item("diamond_scythe")));
            ItemStack legal = new ItemStack(item("runic_longsword"));
            legal.set(DataComponents.CUSTOM_NAME, Component.literal("Allowed NingMeng123 runic"));
            ItemStack bundle = new ItemStack(Items.BUNDLE);
            List<ItemStack> bundleContents = new ArrayList<>();
            for (String family : FAMILIES) bundleContents.add(new ItemStack(item("diamond_" + family)));
            bundleContents.add(new ItemStack(item("magiscythe")));
            bundleContents.add(legal);
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(bundleContents));
            ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
            shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(item("dormant_relic")), bundle,
                    new ItemStack(item("runic_tablet"), 3))));
            player.getInventory().setItem(1, shulker);
            player.doTick();
            h.assertTrue(player.getInventory().getItem(0).isEmpty() && player.getOffhandItem().isEmpty(), "The native player tick removes held and inventory retired weapons");
            h.assertTrue(player.getEnderChestInventory().getItem(0).isEmpty() && player.containerMenu.getCarried().isEmpty(), "Ender chest and active cursor cannot retain diamond weapons");
            List<ItemStack> slots = player.getInventory().getItem(1).get(DataComponents.CONTAINER).stream().toList();
            h.assertTrue(slots.get(0).isEmpty(), "Nested container retirement retains the original empty slot");
            List<ItemStack> remaining = new ArrayList<>();
            slots.get(1).get(DataComponents.BUNDLE_CONTENTS).items().forEach(remaining::add);
            h.assertTrue(remaining.size() == 1 && ItemStack.matches(remaining.getFirst(), legal), "Deep bundle cleanup removes all fifteen diamond families and uniques while preserving the exact legal runic stack");
            h.assertTrue(slots.get(2).is(item("runic_tablet")) && slots.get(2).getCount() == 3, "Nested materials retain their original slots and counts");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void worldJoinAndNativeEntityTickRejectRetiredDropsAndLateMobEquipment(GameTestHelper h) {
        if (absent(h)) return;
        var level = h.getLevel();
        Vec3 pos = location(h);
        var forbidden = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(item("diamond_chakram")));
        var allowed = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(item("runic_longsword")));
        // Neutral mobs exercise the same equipment boundary without hostile spawn-loadout replacement.
        var mob = EntityType.PIG.create(level);
        h.assertTrue(mob != null, "The native neutral mob fixture exists");
        try {
            h.assertTrue(!level.addFreshEntity(forbidden) || forbidden.isRemoved() || forbidden.getItem().isEmpty(), "Directly spawned diamond drops cannot enter the world");
            h.assertTrue(level.addFreshEntity(allowed), "The legal runic drop enters the world normally");
            mob.moveTo(pos); mob.setNoAi(true); mob.setNoGravity(true);
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item("diamond_greathammer")));
            mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(item("iron_longsword")));
            h.assertTrue(level.addFreshEntity(mob), "An otherwise legal mob still enters the world");
            h.assertTrue(mob.getMainHandItem().isEmpty() && mob.getOffhandItem().is(item("iron_longsword")), "Entity join strips diamond equipment and keeps ordinary equipment");
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item("soulrender")));
            allowed.setItem(new ItemStack(item("diamond_halberd")));
            level.tickNonPassenger(mob); level.tickNonPassenger(allowed);
            h.assertTrue(mob.getMainHandItem().isEmpty() && mob.getOffhandItem().is(item("iron_longsword")), "Late unique equipment assignments are cleared on the native entity tick");
            h.assertTrue(allowed.isRemoved(), "Replacing an existing drop with a diamond weapon cannot bypass the entity tick");
        } finally { forbidden.discard(); allowed.discard(); mob.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "simplyswords_compat")
    public static void diamondAndUniqueWeaponsCannotAttackOrActivateBeforeTheNextCleanupTick(GameTestHelper h) {
        if (absent(h)) return;
        var target = EntityType.PIG.create(h.getLevel());
        h.assertTrue(target != null, "The native target fixture exists");
        try (Session session = newPlayer(h)) {
            var player = session.player();
            List<String> retiredPaths = new ArrayList<>(List.of("stormbringer", "magiscythe", "chompolotl"));
            for (String family : FAMILIES) retiredPaths.add("diamond_" + family);
            for (String path : retiredPaths) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item(path)));
                h.assertTrue(NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND)).isCanceled(),
                        "Retired weapon use is vetoed before the next player tick: " + path);
                h.assertTrue(NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, target)).isCanceled(), "A just-injected retired weapon cannot attack: " + path);
                h.assertTrue(player.getMainHandItem().isEmpty(), "The rejected attack immediately purges the retired weapon");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("runic_longsword")));
            h.assertFalse(NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, target)).isCanceled(), "The same registered attack boundary still accepts runic weapons");
            h.assertTrue(player.getMainHandItem().is(item("runic_longsword")), "Runic equipment survives the attack check");
        } finally { target.discard(); }
        h.succeed();
    }

    private static Set<Item> retiredItems(GameTestHelper h) throws IOException {
        Set<Item> result = officialUniqueItems(h);
        for (String family : FAMILIES) result.add(item("diamond_" + family));
        return result;
    }

    /** Read the mod's original declaration rather than the production classifier or filtered runtime tag. */
    private static Set<Item> officialUniqueItems(GameTestHelper h) throws IOException {
        var resource = h.getLevel().getServer().getResourceManager().getResource(id("tags/item/uniques.json")).orElseThrow();
        Set<Item> result = new LinkedHashSet<>();
        try (var reader = resource.openAsReader()) {
            for (var entry : GsonHelper.parse(reader).getAsJsonArray("values")) {
                var object = entry.isJsonObject() ? entry.getAsJsonObject() : null;
                ResourceLocation itemId = ResourceLocation.parse(object == null ? entry.getAsString() : object.get("id").getAsString());
                if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                    h.assertTrue(object != null && !GsonHelper.getAsBoolean(object, "required", true), "Required official unique fixture is registered: " + itemId);
                    continue;
                }
                result.add(BuiltInRegistries.ITEM.get(itemId));
            }
        }
        return result;
    }

    private static RecipeHolder<?> recipe(String path, Item result) {
        return new RecipeHolder<>(testId(path), new ShapelessRecipe("", CraftingBookCategory.MISC,
                new ItemStack(result), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.COBBLESTONE))));
    }

    private static MerchantOffer offer(Item first, Item second, Item result) {
        return new MerchantOffer(new ItemCost(first, 1), second == null ? Optional.empty() : Optional.of(new ItemCost(second, 1)),
                new ItemStack(result), 12, 1, .05F);
    }

    private static ItemStack load(GameTestHelper h, ItemStack stack) {
        return ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
    }

    private static Item item(String path) {
        Item item = BuiltInRegistries.ITEM.get(id(path));
        if (item == Items.AIR) throw new IllegalStateException("Missing native Simply Swords fixture: " + path);
        return item;
    }

    private static boolean absent(GameTestHelper h) {
        if (SimplySwordsCompat.enabled()) return false;
        h.succeed(); return true;
    }

    private static Vec3 location(GameTestHelper h) { return Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 12, 2))); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("simplyswords", path); }
    private static ResourceLocation testId(String path) { return ResourceLocation.fromNamespaceAndPath("mite", "unique_removal_test/" + path); }

    private static Session newPlayer(GameTestHelper h) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "unique-retire"), false);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.moveTo(location(h));
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
