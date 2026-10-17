package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.integration.jei.AnvilJeiRecipe;
import io.github.ningmeng123.mite.integration.jei.AnvilJeiRecipes;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Viewer rows are checked against independent tier expectations and physical anvil menus. @author NingMeng123 */
@EventBusSubscriber(modid = "mite", bus = EventBusSubscriber.Bus.MOD)
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class AnvilJeiRecipesGameTests {
    private static final BlockPos SITE = new BlockPos(1, 1, 1);
    private static final Set<String> ALL_ANVILS = Set.of("mite:copper_anvil", "mite:silver_anvil", "mite:golden_anvil",
            "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil", "mite:ancient_metal_anvil",
            "mite:mithril_anvil", "mite:adamantium_anvil");
    private static final Set<String> IRON_ANVILS = Set.of("minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil",
            "mite:ancient_metal_anvil", "mite:mithril_anvil", "mite:adamantium_anvil");
    private static volatile List<String> constructionCategoryIds;
    private static volatile boolean constructionAnvilsUnbound;
    private static volatile boolean constructionPluginInitialized;
    private AnvilJeiRecipesGameTests() {}

    @SubscribeEvent
    public static void verifyConstructionBeforeAnvilRegistration(FMLConstructModEvent event) {
        constructionAnvilsUnbound = !MiteFunctionalBlocks.ANVILS.isEmpty()
                && MiteFunctionalBlocks.ANVILS.values().stream().noneMatch(holder -> holder.isBound());
        if (!constructionAnvilsUnbound)
            throw new IllegalStateException("Early JEI regression must run before deferred material anvils are bound");
        constructionCategoryIds = List.copyOf(AnvilJeiRecipes.categoryIds());
        if (ModList.get().isLoaded("jei")) {
            try {
                Class.forName("io.github.ningmeng123.mite.integration.jei.MiteJeiPlugin", true,
                        AnvilJeiRecipesGameTests.class.getClassLoader());
                constructionPluginInitialized = true;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Optional JEI plugin must initialize during mod construction without dereferencing deferred anvils", failure);
            }
        }
        Mite.LOGGER.info("[MITE anvil JEI early-init audit] before-bound={}, categories={}, optional-JEI-reflection={}",
                constructionAnvilsUnbound, constructionCategoryIds,
                constructionPluginInitialized ? "executed" : "skipped (JEI absent)");
    }

    @GameTest(template = "empty")
    public static void categoryIdsAndOptionalPluginInitializeBeforeDeferredAnvilsAreBound(GameTestHelper helper) {
        helper.assertTrue(constructionAnvilsUnbound, "Construct callback checked truly unbound deferred anvils rather than post-registration state");
        List<String> expected = List.of("mite:copper_anvil", "minecraft:anvil", "mite:ancient_metal_anvil",
                "mite:mithril_anvil", "mite:adamantium_anvil");
        helper.assertTrue(expected.equals(constructionCategoryIds), "All five correct repair categories were available during mod construction");
        helper.assertTrue(new HashSet<>(constructionCategoryIds).equals(AnvilJeiRecipes.categoryProfiles().keySet()),
                "Early metadata and the later real registered block profiles describe the same categories");
        if (ModList.get().isLoaded("jei")) helper.assertTrue(constructionPluginInitialized,
                "Present optional JEI plugin was actually initialized during the unbound construction phase");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commonAnvilIndexLoadsWithoutJeiOrClientApiAndGroupsAllNineAnvils(GameTestHelper helper) {
        helper.assertFalse(ModList.get().isLoaded("jei"), "The dedicated verification runtime has no JEI mod");
        try {
            Class.forName("mezz.jei.api.IModPlugin", false, AnvilJeiRecipesGameTests.class.getClassLoader());
            helper.fail("Optional JEI API must not be present in the dedicated verification runtime");
        } catch (ClassNotFoundException expected) {
            helper.assertFalse(AnvilJeiRecipes.build().recipes().isEmpty(), "Repair rows build without client or optional JEI classes");
        }
        Map<String, Float> expectedCapacities = Map.of("mite:copper_anvil", 4F, "mite:silver_anvil", 4F,
                "mite:golden_anvil", 4F, "minecraft:anvil", 8F, "minecraft:chipped_anvil", 8F,
                "minecraft:damaged_anvil", 8F, "mite:ancient_metal_anvil", 16F, "mite:mithril_anvil", 64F,
                "mite:adamantium_anvil", 256F);
        var profiles = AnvilJeiRecipes.profiles();
        helper.assertTrue(profiles.keySet().equals(expectedCapacities.keySet()), "Exactly six source anvils and three native iron states are registered");
        expectedCapacities.forEach((id, capacity) -> {
            helper.assertValueEqual(profiles.get(id).capacity(), capacity, "Independent material capacity: " + id);
            helper.assertValueEqual(MaterialAnvilRules.capacity(profiles.get(id).state()), capacity, "Profile represents the real block: " + id);
        });
        var categories = AnvilJeiRecipes.categoryProfiles();
        helper.assertValueEqual(categories.size(), 5, "Equal-capacity metals and damaged iron states do not duplicate category tabs");
        helper.assertTrue(categories.values().stream().map(AnvilJeiRecipes.Profile::capacity).collect(Collectors.toSet())
                .equals(Set.of(4F, 8F, 16F, 64F, 256F)), "Categories retain all five independently specified capacity tiers");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void equipmentListsExactlyTheEqualOrStrongerRepairAnvils(GameTestHelper helper) {
        var index = AnvilJeiRecipes.build();
        assertAnvils(helper, index, "mite:copper_pickaxe", ALL_ANVILS);
        assertAnvils(helper, index, "mite:silver_pickaxe", ALL_ANVILS);
        assertAnvils(helper, index, "minecraft:golden_pickaxe", ALL_ANVILS);
        assertAnvils(helper, index, "minecraft:iron_pickaxe", IRON_ANVILS);
        assertAnvils(helper, index, "mite:mithril_pickaxe", Set.of("mite:mithril_anvil", "mite:adamantium_anvil"));
        assertAnvils(helper, index, "mite:adamantium_pickaxe", Set.of("mite:adamantium_anvil"));
        for (var page : pages(index, "mite:copper_pickaxe"))
            helper.assertValueEqual(page.requiredCapacity(), 4F, "Copper, silver and gold are the same repair tier");
        for (var page : pages(index, "minecraft:iron_pickaxe"))
            helper.assertValueEqual(page.requiredCapacity(), 8F, "Damaged native iron anvils retain full material capacity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyDisplayedRepairIsUniqueExecutableAndLeavesItsInputsUntouched(GameTestHelper helper) {
        var index = AnvilJeiRecipes.build();
        Player owner = player(helper, 203000);
        Set<String> rows = new HashSet<>();
        Set<ResourceLocation> represented = new HashSet<>();
        for (var page : index.recipes()) {
            helper.assertTrue(rows.add(page.itemId() + "/" + page.kind()), "Each item and operation appears once, with applicable anvils on the left");
            represented.add(page.itemId());
            helper.assertFalse(page.itemId().getPath().startsWith("diamond_"), "Removed diamond equipment is never resurrected by viewer rows");
            helper.assertValueEqual(page.left().getCount(), 1, "Repair example uses one damaged item");
            helper.assertValueEqual(page.right().getCount(), 1, "Example displays one consumed material or matching item");
            helper.assertValueEqual(page.output().getCount(), 1, "Repair output is one equipment item");
            helper.assertTrue(page.left().isDamaged(), "Displayed equipment is damaged rather than a pristine nonmatching ingredient");
            helper.assertTrue(page.output().getDamageValue() < page.left().getDamageValue(), "Every shown repair restores actual durability");
            helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(page.left().getItem()), page.itemId(), "Equipment focus indexes the real left item");
            var beforeLeft = page.left().copy();
            var beforeRight = page.right().copy();
            var detached = page.left();
            detached.setDamageValue(0);
            helper.assertTrue(ItemStack.matches(beforeLeft, page.left()), "Ingredient cycling cannot mutate the cached repair example");
            var state = AnvilJeiRecipes.profiles().get(page.anvilId()).state();
            var preview = MaterialAnvilRules.previewRepair(state, page.left(), page.right());
            var plan = MaterialAnvilRules.plan(owner, state, page.left(), page.right());
            helper.assertTrue(preview != null && preview.allowed(), "The player-independent real rules accept every shown example: " + page.itemId());
            helper.assertTrue(plan != null && plan.allowed(), "A qualified survival player can execute the displayed ingredients: " + page.itemId());
            helper.assertTrue(ItemStack.matches(preview.output(), page.output()) && ItemStack.matches(plan.output(), page.output()),
                    "Viewer output includes the real result damage, quality and components");
            helper.assertValueEqual(plan.consumed(), page.consumed(), "Viewer consumed count agrees with actual repair");
            helper.assertValueEqual(plan.anvilWear(), page.anvilWear(), "Viewer anvil wear agrees with actual repair");
            helper.assertValueEqual(page.consumed(), 1, "One-unit example never displays an unexplained material remainder");
            if (page.kind() == AnvilJeiRecipe.Kind.COMBINATION) {
                helper.assertTrue(page.right().is(page.left().getItem()), "Combination uses the exact same item");
                helper.assertValueEqual(page.anvilWear(), 0, "Source item combination does not wear the anvil");
            } else helper.assertFalse(page.right().is(page.left().getItem()), "Material repair has a real resource as its second input");
            helper.assertTrue(ItemStack.matches(beforeLeft, page.left()) && ItemStack.matches(beforeRight, page.right()),
                    "Pure viewer previews never consume or mutate the indexed source inputs");
        }
        helper.assertTrue(index.coveredItems().equals(represented), "Native replacement only covers items with real displayed operations");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void woodFlintObsidianAndLeatherRemainRepairableBySameItemOnly(GameTestHelper helper) {
        var index = AnvilJeiRecipes.build();
        for (String id : List.of("minecraft:wooden_sword", "mite:wooden_cudgel", "mite:flint_hatchet", "mite:flint_knife",
                "mite:obsidian_hatchet", "mite:obsidian_knife", "minecraft:leather_boots", "minecraft:leather_chestplate")) {
            var pages = pages(index, id);
            helper.assertValueEqual(pages.size(), 1, "Nonmetal source equipment has one useful combination view: " + id);
            helper.assertTrue(pages.getFirst().kind() == AnvilJeiRecipe.Kind.COMBINATION,
                    "Missing nugget resources must not exclude valid same-item repairs: " + id);
            assertAnvils(helper, index, id, ALL_ANVILS);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernMetalShieldsReinforcedBowsAndAllRodsAreIncludedButNativeBowIsNotHijacked(GameTestHelper helper) {
        var index = AnvilJeiRecipes.build();
        for (var shield : MiteShields.definitions()) assertKinds(helper, index, shield.id(), true);
        for (String id : List.of("mite:ancient_metal_bow", "mite:mithril_bow")) assertKinds(helper, index, id, true);
        for (var material : MiteRangedItems.rods()) {
            String id = MiteRangedItems.rodId(material).toString();
            boolean metal = !material.id.equals("flint") && !material.id.equals("obsidian");
            assertKinds(helper, index, id, metal);
        }
        helper.assertTrue(pages(index, "minecraft:bow").isEmpty(), "Unmanaged ordinary bow repairs stay with native JEI");
        helper.assertFalse(index.coveredItems().contains(ResourceLocation.withDefaultNamespace("bow")), "Native bow is not hidden as a covered repair");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realMenuMatchesOneNuggetRepairAndStillEnforcesQualityExperience(GameTestHelper helper) {
        var index = AnvilJeiRecipes.build();
        var page = pages(index, "mite:copper_pickaxe").stream()
                .filter(row -> row.kind() == AnvilJeiRecipe.Kind.MATERIAL).findFirst().orElseThrow();
        Player owner = player(helper, 0);
        AnvilMenu menu = menu(helper, owner, page.anvilId());
        menu.getSlot(0).set(page.left().copy());
        menu.getSlot(1).set(page.right().copy());
        helper.assertTrue(ItemStack.matches(menu.getSlot(2).getItem(), page.output()), "Physical menu displays the exact indexed repair output at zero XP");
        helper.assertValueEqual(menu.repairItemCountCost, 1, "Actual menu consumes the single displayed nugget");
        helper.assertValueEqual(menu.getCost(), 0, "Source repair itself has no XP fee");
        menu.clicked(2, 0, ClickType.PICKUP, owner);
        helper.assertTrue(ItemStack.matches(menu.getCarried(), page.output()), "Actual pickup accepts the indexed result");
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "One repair consumes exactly its displayed inputs");
        menu.setCarried(ItemStack.EMPTY);
        ItemStack independentCopper = stack("mite:copper_pickaxe");
        independentCopper.setDamageValue(3450);
        menu.getSlot(0).set(independentCopper);
        menu.getSlot(1).set(new ItemStack(MiteMaterials.COPPER_NUGGET.get()));
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 2650, "Independent source unit: copper pickaxe 4800/(3*2) restores 800 per nugget");
        AnvilMenu ironMenu = menu(helper, owner, "minecraft:anvil");
        ItemStack fine = new ItemStack(Items.IRON_PICKAXE);
        EquipmentDurability.setQuality(fine, Quality.FINE);
        fine.setDamageValue(10000);
        var preview = MaterialAnvilRules.previewRepair(Blocks.ANVIL.defaultBlockState(), fine, new ItemStack(Items.IRON_NUGGET));
        helper.assertTrue(preview != null && preview.allowed(), "Viewer preview does not disappear because the current player lacks quality ability");
        ironMenu.getSlot(0).set(fine);
        ironMenu.getSlot(1).set(new ItemStack(Items.IRON_NUGGET));
        helper.assertTrue(ironMenu.getSlot(2).getItem().isEmpty(), "Insufficient XP still rejects the actual high-quality repair");
        ironMenu.clicked(2, 0, ClickType.PICKUP, owner);
        helper.assertTrue(ironMenu.getCarried().isEmpty(), "The viewer cannot bypass real pickup eligibility");
        PlayerExperience.setTotal(owner, 203000);
        ironMenu.createResult();
        helper.assertTrue(ItemStack.matches(ironMenu.getSlot(2).getItem(), preview.output()), "The same high-quality repair becomes executable after acquiring enough XP");
        helper.assertTrue(index.coveredItems().equals(AnvilJeiRecipes.build().coveredItems()), "Index membership is independent of the player's current XP");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeReplacementOnlyHidesConfirmedGeneratedRepairsAndPreservesBooksAndOtherMods(GameTestHelper helper) {
        var covered = AnvilJeiRecipes.build().coveredItems();
        var iron = List.of(new ItemStack(Items.IRON_PICKAXE));
        var nugget = List.of(new ItemStack(Items.IRON_NUGGET));
        ResourceLocation materialUid = ResourceLocation.parse("minecraft:anvil.materials_repair.minecraft.iron_pickaxe");
        ResourceLocation combinationUid = ResourceLocation.parse("minecraft:anvil.self_repair.minecraft.iron_pickaxe");
        helper.assertTrue(AnvilJeiRecipes.isCoveredNativeRepair(materialUid, iron, nugget, covered),
                "Known JEI-generated material repair is replaced by the capacity-aware nugget page");
        helper.assertTrue(AnvilJeiRecipes.isCoveredNativeRepair(combinationUid, iron, iron, covered),
                "Known JEI-generated same-item repair is replaced by the source no-bonus page");
        var copper = List.of(stack("mite:copper_pickaxe"));
        helper.assertTrue(AnvilJeiRecipes.isCoveredNativeRepair(ResourceLocation.parse("mite:anvil.materials_repair.mite.copper_pickaxe"),
                copper, List.of(new ItemStack(MiteMaterials.COPPER_NUGGET.get())), covered), "Native generated IDs also sanitize modded item IDs");
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertFalse(AnvilJeiRecipes.isCoveredNativeRepair(materialUid, iron, List.of(book), covered),
                "Book application and enchantment merging remain in native JEI");
        helper.assertFalse(AnvilJeiRecipes.isCoveredNativeRepair(ResourceLocation.parse("other_mod:anvil.materials_repair.minecraft.iron_pickaxe"),
                iron, nugget, covered), "Another mod's operation is never hidden just because its ingredients overlap");
        helper.assertFalse(AnvilJeiRecipes.isCoveredNativeRepair(ResourceLocation.parse("minecraft:anvil.materials_repair.custom_iron_repair"),
                iron, nugget, covered), "Unconfirmed suffixes are not mistaken for JEI generated repairs");
        helper.assertFalse(AnvilJeiRecipes.isCoveredNativeRepair(ResourceLocation.parse("minecraft:anvil.custom_operation"),
                iron, nugget, covered), "Custom anvil operation IDs remain visible");
        var bow = List.of(new ItemStack(Items.BOW));
        helper.assertFalse(AnvilJeiRecipes.isCoveredNativeRepair(ResourceLocation.parse("minecraft:anvil.self_repair.minecraft.bow"),
                bow, bow, covered), "Ordinary bow has no replacement and retains native repair display");
        var netherite = List.of(new ItemStack(Items.NETHERITE_PICKAXE));
        helper.assertFalse(AnvilJeiRecipes.isCoveredNativeRepair(ResourceLocation.parse("minecraft:anvil.materials_repair.minecraft.netherite_pickaxe"),
                netherite, List.of(new ItemStack(Items.NETHERITE_INGOT)), covered), "Uncovered modern equipment does not lose its viewer operation");
        helper.assertFalse(AnvilJeiRecipes.isCoveredNativeRepair(materialUid,
                List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BOW)), nugget, covered), "A mixed focus cannot hide an uncovered alternative");
        helper.assertFalse(AnvilJeiRecipes.isCoveredNativeRepair(null, iron, nugget, covered), "Anonymous operations remain untouched");
        helper.assertFalse(AnvilJeiRecipes.isCoveredNativeRepair(materialUid, List.of(), nugget, covered), "Missing input evidence never hides a recipe");
        helper.succeed();
    }

    private static List<AnvilJeiRecipe> pages(AnvilJeiRecipes.Index index, String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        return index.recipes().stream().filter(page -> page.itemId().equals(key)).toList();
    }
    private static void assertKinds(GameTestHelper helper, AnvilJeiRecipes.Index index, String id, boolean material) {
        Set<AnvilJeiRecipe.Kind> kinds = pages(index, id).stream().map(AnvilJeiRecipe::kind).collect(Collectors.toSet());
        helper.assertTrue(kinds.equals(material ? Set.of(AnvilJeiRecipe.Kind.MATERIAL, AnvilJeiRecipe.Kind.COMBINATION)
                : Set.of(AnvilJeiRecipe.Kind.COMBINATION)), "Only supported source repair operations are displayed: " + id);
    }
    private static void assertAnvils(GameTestHelper helper, AnvilJeiRecipes.Index index, String id, Set<String> expected) {
        var pages = pages(index, id);
        helper.assertFalse(pages.isEmpty(), "Repair pages exist for " + id);
        for (var page : pages) helper.assertTrue(new HashSet<>(AnvilJeiRecipes.categoryAnvils(page.anvilId())).equals(expected),
                "Every listed catalyst repairs the item, without weaker false positives or repeated tabs: " + id);
    }
    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }
    private static Player player(GameTestHelper helper, int xp) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(SITE)));
        PlayerExperience.setTotal(player, xp);
        return player;
    }
    private static AnvilMenu menu(GameTestHelper helper, Player owner, String anvilId) {
        helper.setBlock(SITE.below(), Blocks.STONE);
        helper.setBlock(SITE, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(anvilId)));
        return new AnvilMenu(1, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(SITE)));
    }
}
