package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.item.RunestoneItem;
import io.github.ningmeng123.mite.registry.MiteCreativeTabs;
import io.github.ningmeng123.mite.registry.MiteCreativeTabs.Group;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.rules.FunctionalBlockRules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual creative contents rebuild, complete registry coverage and retained component variants. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CreativeTabs034GameTests {
    private CreativeTabs034GameTests() {}

    @GameTest(template="empty")
    public static void independentCategoriesCoverRegisteredItemsAndPreserveSearchAndVariants(GameTestHelper h) {
        List<Item> registered=BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Mite.MOD_ID))
                .filter(item -> item!=Items.AIR && item.isEnabled(h.getLevel().enabledFeatures())).toList();
        h.assertTrue(!registered.isEmpty(),"The actual registry contains enabled MITE items");
        List<Item> required=new ArrayList<>(registered); required.add(Items.WOODEN_SWORD);
        Set<ResourceKey<CreativeModeTab>> keys=new HashSet<>();
        for(Group group:Group.values()) keys.add(group.key());
        h.assertValueEqual(keys.size(),6,"Exactly six distinct MITE category identities after merging building content");
        h.assertValueEqual(BuiltInRegistries.CREATIVE_MODE_TAB.registryKeySet().stream()
                .filter(key -> key.location().getNamespace().equals(Mite.MOD_ID)).count(),6L,
                "No obsolete MITE food or building category remains");
        h.assertTrue(!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID,"building_blocks")),
                "The separate building and decoration page is removed");

        for(boolean permissions:new boolean[]{false,true}) {
            CreativeModeTabs.tryRebuildTabContents(h.getLevel().enabledFeatures(),permissions,h.getLevel().registryAccess());
            CreativeModeTab search=CreativeModeTabs.searchTab();
            Set<String> names=new HashSet<>();
            for(Group group:Group.values()) {
                CreativeModeTab tab=BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(group.key());
                h.assertTrue(tab.getType()==CreativeModeTab.Type.CATEGORY && tab.shouldDisplay(),"Visible category "+group);
                h.assertTrue(!tab.getIconItem().isEmpty(),"Registered category icon "+group);
                String name=tab.getDisplayName().getString();
                h.assertTrue(!name.isBlank()&&names.add(name),"Distinct nonempty category title "+group);
                for(ItemStack stack:tab.getDisplayItems()) {
                    h.assertValueEqual(stack.getCount(),1,"Creative variants are single-item entries");
                    if(required.contains(stack.getItem())) {
                        h.assertTrue(MiteCreativeTabs.group(stack.getItem())==group,"The item is shown in its assigned category: "+id(stack.getItem()));
                        assertVariant(h,search.getDisplayItems(),stack,"Global search retains the precise displayed stack");
                    }
                }
            }
            for(Item item:required) {
                h.assertTrue(MiteCreativeTabs.group(item)!=null,"Every registered MITE identity and the retained-ID club has a group: "+id(item));
                int categories=0;
                for(Group group:Group.values()) {
                    var tab=BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(group.key());
                    if(contains(tab.getDisplayItems(),item)) ++categories;
                }
                h.assertValueEqual(categories,1,"Item identity occurs in exactly one MITE parent page, allowing its component variants: "+id(item));
                h.assertTrue(contains(search.getDisplayItems(),item),"Global search includes every MITE identity: "+id(item));
            }
            h.assertTrue(MiteCreativeTabs.group(Items.WOODEN_SWORD)==Group.COMBAT,"The existing wooden sword ID is the combat club");
            h.assertTrue(MiteCreativeTabs.group(Items.DIRT)==null,"Ordinary vanilla identities are not reclassified as new MITE content");

            for(CreativeModeTab tab:CreativeModeTabs.allTabs()) {
                ResourceKey<CreativeModeTab> key=BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab).orElseThrow();
                for(Item forbidden:List.of(Items.WOODEN_AXE,Items.WOODEN_PICKAXE,Items.CRAFTING_TABLE,
                        Items.DIAMOND_SWORD,Items.DIAMOND_AXE,Items.DIAMOND_PICKAXE,Items.DIAMOND_SHOVEL,Items.DIAMOND_HOE,
                        Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS,Items.DIAMOND_HORSE_ARMOR,
                        Items.NETHERITE_SWORD,Items.NETHERITE_AXE,Items.NETHERITE_PICKAXE,Items.NETHERITE_SHOVEL,Items.NETHERITE_HOE,
                        Items.NETHERITE_HELMET,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_LEGGINGS,Items.NETHERITE_BOOTS)) {
                    h.assertTrue(!contains(tab.getDisplayItems(),forbidden)&&!contains(tab.getSearchTabDisplayItems(),forbidden),
                            "Retired item cannot return through parent or search contents: "+id(forbidden));
                }
                if(SimplySwordsCompat.enabled()) for(String family:SimplySwordsCompat.families()) {
                    Item forbidden=SimplySwordsCompat.weapon("netherite",family);
                    h.assertTrue(!contains(tab.getDisplayItems(),forbidden)&&!contains(tab.getSearchTabDisplayItems(),forbidden),
                            "Retired Simply Swords netherite weapon stays absent from parent and search contents: "+family);
                }
                if(key.location().getNamespace().equals("minecraft")&&tab.getType()==CreativeModeTab.Type.CATEGORY) {
                    h.assertTrue(tab.getDisplayItems().stream().noneMatch(stack -> required.contains(stack.getItem()))
                                    &&tab.getSearchTabDisplayItems().stream().noneMatch(stack -> required.contains(stack.getItem())),
                            "Vanilla category has no remaining MITE items or club: "+key.location());
                }
            }
            assertNative(h,CreativeModeTabs.NATURAL_BLOCKS,Items.DIRT);
            assertNative(h,CreativeModeTabs.COMBAT,Items.IRON_SWORD);
            assertNative(h,CreativeModeTabs.TOOLS_AND_UTILITIES,Items.WOODEN_SHOVEL);
            assertNative(h,CreativeModeTabs.TOOLS_AND_UTILITIES,Items.WOODEN_HOE);
            assertNative(h,CreativeModeTabs.FOOD_AND_DRINKS,Items.BREAD);
            assertNative(h,CreativeModeTabs.INGREDIENTS,Items.IRON_INGOT);
            assertNative(h,CreativeModeTabs.INGREDIENTS,Items.DIAMOND);
            assertNative(h,CreativeModeTabs.INGREDIENTS,Items.NETHERITE_INGOT);
            assertNative(h,CreativeModeTabs.INGREDIENTS,Items.NETHERITE_SCRAP);
            assertNative(h,CreativeModeTabs.NATURAL_BLOCKS,Items.DIAMOND_ORE);
            assertNative(h,CreativeModeTabs.NATURAL_BLOCKS,Items.DEEPSLATE_DIAMOND_ORE);
            assertFoodTabContainsOnlyMite(h);
            assertFunctionalVariants(h);
        }
        h.succeed();
    }

    private static void assertFoodTabContainsOnlyMite(GameTestHelper h) {
        var foods=BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(Group.FOODS.key());
        for(var entries:List.of(foods.getDisplayItems(),foods.getSearchTabDisplayItems()))
            h.assertTrue(entries.stream().allMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Mite.MOD_ID)),
                    "The MITE food category's display and search entries contain only added MITE food and vessels");
        for(Item item:List.of(Items.APPLE,Items.BREAD,Items.CAKE,Items.BOWL,Items.BUCKET,Items.WATER_BUCKET,Items.MILK_BUCKET)) {
            h.assertTrue(!contains(foods.getDisplayItems(),item),"Vanilla food or vessel is not duplicated in the MITE page: "+id(item));
            h.assertTrue(contains(CreativeModeTabs.searchTab().getDisplayItems(),item),"Vanilla food or vessel remains available in global search: "+id(item));
        }
    }
    private static void assertFunctionalVariants(GameTestHelper h) {
        var functional=BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(Group.FUNCTIONAL_BLOCKS.key()).getDisplayItems();
        var search=CreativeModeTabs.searchTab().getDisplayItems();
        for(Item item:List.of(MiteWorldBlocks.MITHRIL_RUNESTONE_ITEM.get(),MiteWorldBlocks.ADAMANTIUM_RUNESTONE_ITEM.get())) {
            h.assertTrue(MiteCreativeTabs.group(item)==Group.FUNCTIONAL_BLOCKS,"Rune gates are functional blocks");
            h.assertValueEqual(functional.stream().filter(stack -> stack.is(item)).count(),16L,"Every rune identity retains sixteen component variants");
            for(int rune=0;rune<16;rune++) {
                ItemStack expected=RunestoneItem.withRune(new ItemStack(item),rune);
                assertVariant(h,functional,expected,"Functional page retains exact rune components "+rune);
                assertVariant(h,search,expected,"Search retains exact rune components "+rune);
            }
        }
        h.assertValueEqual(MiteFunctionalBlocks.ANVILS.size(),7,"Seven additional material anvil identities");
        MiteFunctionalBlocks.ANVILS.forEach((material,holder) -> {
            Item item=holder.get().asItem();
            h.assertTrue(MiteCreativeTabs.group(item)==Group.FUNCTIONAL_BLOCKS,"Material anvils remain functional blocks");
            h.assertValueEqual(functional.stream().filter(stack -> stack.is(item)).count(),3L,"Pristine and both worn anvil variants remain available: "+material);
            int durability=FunctionalBlockRules.anvilDurability(WorkbenchRules.materialCapacity(material));
            for(int stage=0;stage<=2;stage++) {
                ItemStack expected=new ItemStack(item);
                if(stage>0) expected.set(MiteFunctionalBlocks.ANVIL_DAMAGE.get(),FunctionalBlockRules.minimumDamage(stage,durability));
                assertVariant(h,functional,expected,"Functional page retains precise anvil wear components: "+material+" stage "+stage);
                assertVariant(h,search,expected,"Search retains precise anvil wear components: "+material+" stage "+stage);
            }
        });
    }
    private static void assertNative(GameTestHelper h,ResourceKey<CreativeModeTab> key,Item item) {
        var tab=BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(key);
        h.assertTrue(contains(tab.getDisplayItems(),item),"Ordinary vanilla item remains in its native category: "+id(item));
        h.assertTrue(contains(CreativeModeTabs.searchTab().getDisplayItems(),item),"Ordinary vanilla item remains searchable: "+id(item));
    }
    private static void assertVariant(GameTestHelper h,Collection<ItemStack> entries,ItemStack expected,String message) {
        h.assertValueEqual(entries.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack,expected)).count(),1L,message+": "+id(expected.getItem()));
    }
    private static boolean contains(Collection<ItemStack> entries,Item item) { return entries.stream().anyMatch(stack -> stack.is(item)); }
    private static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
}
