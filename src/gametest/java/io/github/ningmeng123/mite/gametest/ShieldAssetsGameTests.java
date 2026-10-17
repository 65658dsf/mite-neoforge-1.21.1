package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShieldDecorationRecipe;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native crafting and banner decoration retain metal identities and source durability. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ShieldAssetsGameTests {
    @GameTest(template = "empty")
    public static void everyMetalAcceptsNativeBannerDecorationWithoutLosingComponents(GameTestHelper h) {
        var recipe = new ShieldDecorationRecipe(CraftingBookCategory.MISC);
        var patterns = new BannerPatternLayers.Builder()
                .add(h.getLevel().registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.CROSS), DyeColor.BLUE)
                .build();
        for (var definition : MiteShields.definitions()) {
            ItemStack shield = new ItemStack(MiteShields.get(definition.material()));
            EquipmentDurability.setQuality(shield, Quality.POOR);
            shield.setDamageValue(7);
            shield.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123"));
            ItemStack before = shield.copy(), banner = new ItemStack(Items.RED_BANNER);
            banner.set(DataComponents.BANNER_PATTERNS, patterns);
            CraftingInput input = CraftingInput.of(2, 1, List.of(shield, banner));
            h.assertTrue(recipe.matches(input, h.getLevel()), "Native decoration accepts " + definition.id());
            ItemStack actual = recipe.assemble(input, h.getLevel().registryAccess());
            ItemStack expected = before.copy();
            expected.set(DataComponents.BASE_COLOR, DyeColor.RED);
            expected.set(DataComponents.BANNER_PATTERNS, patterns);
            h.assertTrue(ItemStack.matches(actual, expected), "Decoration preserves metal, damage, quality and custom name: " + definition.id());
            h.assertTrue(ItemStack.matches(shield, before), "Preview never mutates the input shield");
            h.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(actual, banner)), h.getLevel()), "Existing patterns cannot be overwritten");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bannerDecorationRejectsMultipleShieldsAndUnrelatedEquipment(GameTestHelper h) {
        var recipe = new ShieldDecorationRecipe(CraftingBookCategory.MISC);
        ItemStack banner = new ItemStack(Items.BLUE_BANNER);
        h.assertFalse(recipe.matches(CraftingInput.of(3, 1,
                List.of(new ItemStack(MiteShields.get("copper")), new ItemStack(MiteShields.get("mithril")), banner)), h.getLevel()),
                "Two shields cannot be merged or decorated by one banner");
        h.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(new ItemStack(Items.IRON_CHESTPLATE), banner)), h.getLevel()),
                "An armor item is not treated as a shield");
        h.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(new ItemStack(MiteShields.get("silver")), new ItemStack(Items.STICK))), h.getLevel()),
                "A banner remains mandatory");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void availableMetalIngotsCraftTheirMatchingShield(GameTestHelper h) {
        int checked = 0;
        for (var definition : MiteShields.definitions()) {
            if (!definition.isNew() || definition.material().equals("rusted_iron")) continue;
            String ingotId = switch (definition.material()) {
                case "copper", "gold" -> "minecraft:" + definition.material() + "_ingot";
                default -> "mite:" + definition.material() + "_ingot";
            };
            var metal = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingotId));
            CraftingInput input = CraftingInput.of(3, 3, List.of(
                    new ItemStack(Items.OAK_PLANKS), new ItemStack(metal), new ItemStack(Items.SPRUCE_PLANKS),
                    new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS), new ItemStack(Items.OAK_PLANKS),
                    ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY));
            var holder = h.getLevel().getRecipeManager().byKey(ResourceLocation.parse(definition.id())).orElseThrow();
            h.assertTrue(holder.value() instanceof CraftingRecipe, "Shield recipe is ordinary shaped crafting");
            var recipe = (CraftingRecipe)holder.value();
            h.assertTrue(recipe.matches(input, h.getLevel()), "Mixed plank species and matching metal form " + definition.id());
            var output = recipe.assemble(input, h.getLevel().registryAccess());
            h.assertTrue(output.is(MiteShields.get(definition.material())) && output.getCount() == 1, "One matching shield is produced");
            var invalid = CraftingInput.of(3, 3, List.of(
                    new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.STICK), new ItemStack(Items.OAK_PLANKS),
                    new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                    ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY));
            h.assertFalse(recipe.matches(invalid, h.getLevel()), "Wood alone cannot create a metal shield");
            checked++;
        }
        h.assertValueEqual(checked, 7, "Seven ingot materials have new recipes; rusted equipment remains scavenged");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allShieldsKeepNativeDurabilityEnchantmentAndCommonToolTags(GameTestHelper h) {
        for (var definition : MiteShields.definitions()) {
            var stack = new ItemStack(MiteShields.get(definition.material()));
            h.assertTrue(stack.is(ItemTags.DURABILITY_ENCHANTABLE), "Unbreaking and Mending recognize " + definition.id());
            h.assertFalse(stack.is(ItemTags.EQUIPPABLE_ENCHANTABLE), "Shields follow native behavior and cannot receive Binding");
            h.assertTrue(stack.is(ItemTags.VANISHING_ENCHANTABLE), "Vanishing recognizes metal shields");
            h.assertTrue(stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHIELD), "Common shield integrations recognize the identity");
        }
        h.succeed();
    }
}
