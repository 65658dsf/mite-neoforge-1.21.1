package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.combat.ShieldGuard;
import io.github.ningmeng123.mite.data.MaterialUpgradeRecipes;
import io.github.ningmeng123.mite.data.MaterialUpgradeRecipes.Upgrade;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Loaded smithing recipes, real menu transactions and complete item identity preservation. @author NingMeng123 */
@GameTestHolder("mite_upgrade")
@PrefixGameTestTemplate(false)
public final class MaterialEquipmentUpgradeGameTests {
    private static final Set<String> INITIAL_MATERIALS = Set.of("copper", "silver", "rusted_iron", "gold");

    private MaterialEquipmentUpgradeGameTests() {}

    @GameTest(template = "empty")
    public static void everyAvailableUpgradeLoadsAsNativeSmithingAndAdoptsItsNewMaterialDefaults(GameTestHelper h) {
        Set<String> recipeIds = new HashSet<>();
        Set<String> initialMaterials = new HashSet<>();
        for (Upgrade upgrade : MaterialUpgradeRecipes.upgrades()) {
            h.assertTrue(recipeIds.add(upgrade.id()), "Every material/family transition has a unique recipe ID");
            h.assertValueEqual(upgrade.toMaterial(), nextMaterial(upgrade.fromMaterial()), "No recipe skips or reverses a material stage: " + upgrade.id());
            h.assertValueEqual(family(upgrade.base(), upgrade.fromMaterial()), family(upgrade.result(), upgrade.toMaterial()),
                    "The upgrade preserves its equipment family: " + upgrade.id());
            if (INITIAL_MATERIALS.contains(upgrade.fromMaterial())) initialMaterials.add(upgrade.fromMaterial());
            SmithingTransformRecipe recipe = recipe(h, upgrade);
            h.assertTrue(recipe.getSerializer() == RecipeSerializer.SMITHING_TRANSFORM, "Native smithing JSON and networking remain in use");
            ItemStack source = new ItemStack(upgrade.base());
            source.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 " + upgrade.id()));
            ItemStack before = source.copy();
            SmithingRecipeInput input = input(source, upgrade.addition());
            h.assertTrue(recipe.matches(input, h.getLevel()), "The registered equipment and matching ingot form " + upgrade.id());
            ItemStack output = recipe.assemble(input, h.getLevel().registryAccess());
            h.assertTrue(output.is(upgrade.result()) && output.getCount() == 1, "Exactly one corresponding next-material item is produced");
            assertMaterialDefaults(h, output, new ItemStack(upgrade.result()), upgrade.id());
            h.assertValueEqual(output.get(DataComponents.CUSTOM_NAME), source.get(DataComponents.CUSTOM_NAME), "Custom names survive every family");
            h.assertTrue(ItemStack.matches(source, before), "Recipe preview never changes its input");
            h.assertFalse(output.has(GearProgression.PROGRESS.get()), "An untouched item does not acquire invented growth data");
        }
        h.assertTrue(recipeIds.size() >= 100, "The loaded route covers tools, armor, shields and available extensions");
        h.assertValueEqual(initialMaterials, INITIAL_MATERIALS, "Copper, silver, rusted iron and gold all enter at iron");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allRecipesRequireExactlyTheTemplateBaseAndNextMaterialIngot(GameTestHelper h) {
        for (Upgrade upgrade : MaterialUpgradeRecipes.upgrades()) {
            SmithingTransformRecipe recipe = recipe(h, upgrade);
            ItemStack template = template(), base = new ItemStack(upgrade.base()), ingot = new ItemStack(upgrade.addition());
            for (SmithingRecipeInput invalid : List.of(
                    new SmithingRecipeInput(ItemStack.EMPTY, base, ingot),
                    new SmithingRecipeInput(template, ItemStack.EMPTY, ingot),
                    new SmithingRecipeInput(template, base, ItemStack.EMPTY),
                    new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), base, ingot),
                    new SmithingRecipeInput(template, base, new ItemStack(Items.STICK)),
                    new SmithingRecipeInput(template, new ItemStack(upgrade.result()), ingot))) {
                h.assertFalse(recipe.matches(invalid, h.getLevel()), "A missing or wrong ingredient cannot use " + upgrade.id());
            }
            var ingots = MaterialUpgradeRecipes.upgrades().stream().map(Upgrade::addition).distinct().toList();
            for (Item otherIngot : ingots) if (otherIngot != upgrade.addition()) {
                h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING,
                        input(base, otherIngot), h.getLevel()).isEmpty(), "A wrong tier ingot cannot skip, repeat or reverse an upgrade");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void namedEnchantedToolsAndTrimmedArmorKeepPersonalComponentsButReplaceOldStats(GameTestHelper h) {
        for (String family : List.of("sword", "pickaxe", "chestplate")) {
            Upgrade upgrade = upgrade("mite:copper_" + family, "iron");
            ItemStack source = personalEquipment(h, upgrade.base());
            source.set(DataComponents.MAX_DAMAGE, 400);
            source.set(DataComponents.MAX_STACK_SIZE, 2);
            source.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            source.remove(DataComponents.TOOL);
            source.remove(DataComponents.FIRE_RESISTANT);
            source.setDamageValue(source.getMaxDamage() / 4);
            if (family.equals("chestplate")) source.set(DataComponents.TRIM, new ArmorTrim(
                    h.getLevel().registryAccess().registryOrThrow(Registries.TRIM_MATERIAL).getHolderOrThrow(TrimMaterials.REDSTONE),
                    h.getLevel().registryAccess().registryOrThrow(Registries.TRIM_PATTERN).getHolderOrThrow(TrimPatterns.SENTRY)));
            ItemStack before = source.copy();
            ItemStack output = recipe(h, upgrade).assemble(input(source, upgrade.addition()), h.getLevel().registryAccess());
            ItemStack target = new ItemStack(upgrade.result());
            EquipmentDurability.setQuality(target, Quality.FINE);
            assertMaterialDefaults(h, output, target, family);
            assertPersonalComponents(h, source, output);
            h.assertTrue(Objects.equals(output.get(DataComponents.TRIM), source.get(DataComponents.TRIM)), "Armor keeps the exact trim");
            h.assertValueEqual(output.getDamageValue(), output.getMaxDamage() / 4, "Wear remains one-quarter of the new quality-adjusted durability");
            h.assertTrue(ItemStack.matches(source, before), "Neither target stats nor converted damage leaks back into the source");
            ItemStack loaded = ItemStack.parse(h.getLevel().registryAccess(), output.save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(ItemStack.matches(output, loaded), "Personal components and new material stats survive save/load together");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullCopperToVibraniumRouteKeepsIdentityLevelsExperienceAndLegacyAffixes(GameTestHelper h) {
        ItemStack current = personalEquipment(h, item("mite:copper_sword"));
        GearProgress original = GearProgression.state(current);
        for (var step : List.of(Map.entry("iron", 25), Map.entry("ancient_metal", 25), Map.entry("mithril", 25),
                Map.entry("adamantium", 25), Map.entry("vibranium", 25))) {
            Upgrade upgrade = upgrade(BuiltInRegistries.ITEM.getKey(current.getItem()).toString(), step.getKey());
            ItemStack before = current.copy();
            current = recipe(h, upgrade).assemble(input(current, upgrade.addition()), h.getLevel().registryAccess());
            h.assertValueEqual(GearProgression.state(current), original, "Each material upgrade preserves the exact growth UUID, level, XP and every historical affix");
            h.assertValueEqual(GearProgression.maxLevel(current), step.getValue(), "Standalone material upgrades retain the uniform growth cap");
            assertPersonalComponents(h, before, current);
        }
        h.assertTrue(current.is(item("mite:vibranium_sword")), "Five consecutive smithing steps reach vibranium");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void proportionalDamageRoundsWithoutCreatingAnAlreadyBrokenOutput(GameTestHelper h) {
        Upgrade upgrade = upgrade("mite:copper_pickaxe", "iron");
        ItemStack source = new ItemStack(upgrade.base());
        source.set(DataComponents.MAX_DAMAGE, 1_000_000);
        source.setDamageValue(999_999);
        ItemStack output = recipe(h, upgrade).assemble(input(source, upgrade.addition()), h.getLevel().registryAccess());
        h.assertValueEqual(output.getDamageValue(), output.getMaxDamage() - 1, "Rounding nearly complete wear leaves exactly one durability rather than breaking the preview");
        source.set(DataComponents.MAX_DAMAGE, 7);
        source.setDamageValue(2);
        output = recipe(h, upgrade).assemble(input(source, upgrade.addition()), h.getLevel().registryAccess());
        h.assertValueEqual(output.getDamageValue(), 2743, "Two-sevenths of the iron pickaxe's 9600 durability rounds to the nearest point");
        source.setDamageValue(0);
        output = recipe(h, upgrade).assemble(input(source, upgrade.addition()), h.getLevel().registryAccess());
        h.assertValueEqual(output.getDamageValue(), 0, "A fresh equipment item remains fresh after upgrading");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldUpgradeKeepsBannerAndGuardHistoryAndUsesTheStrongerMaterialProfile(GameTestHelper h) {
        Upgrade upgrade = upgrade("mite:copper_shield", "iron");
        ItemStack source = personalEquipment(h, upgrade.base());
        source.set(DataComponents.BASE_COLOR, DyeColor.RED);
        source.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(h.getLevel().registryAccess()
                .registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.CROSS), DyeColor.BLUE).build());
        source.set(ShieldGuard.GUARD.get(), new ShieldGuard.State(24D, 2));
        source.setDamageValue(source.getMaxDamage() / 2);
        ItemStack before = source.copy();
        ItemStack output = recipe(h, upgrade).assemble(input(source, upgrade.addition()), h.getLevel().registryAccess());
        assertPersonalComponents(h, source, output);
        h.assertValueEqual(output.get(DataComponents.BASE_COLOR), DyeColor.RED, "The metal upgrade keeps its banner's base color");
        h.assertValueEqual(output.get(DataComponents.BANNER_PATTERNS), source.get(DataComponents.BANNER_PATTERNS), "The complete banner layer order survives");
        h.assertValueEqual(ShieldGuard.state(output), ShieldGuard.state(source), "Changing metal cannot reset the used shield's guard history");
        h.assertValueEqual(ShieldGuard.profile(output).blockLimit(), 5, "The upgraded iron shield now accepts five blocks");
        h.assertTrue(ShieldGuard.profile(output).damageLimit() > ShieldGuard.profile(source).damageLimit(), "The new damage threshold increases proportionally");
        h.assertValueEqual(output.getDamageValue(), output.getMaxDamage() / 2, "Shield wear keeps the same proportion");
        h.assertTrue(ItemStack.matches(source, before), "Shield decoration and guard remain untouched in the input");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualSmithingMenuPreviewAndBothTakePathsConsumeOneTemplateAndOneIngot(GameTestHelper h) {
        try (SmithingSession session = new SmithingSession(h)) {
            Upgrade upgrade = upgrade("mite:copper_pickaxe", "iron");
            ItemStack source = personalEquipment(h, upgrade.base()), before = source.copy();
            ItemStack templates = template(); templates.setCount(3);
            session.menu.getSlot(0).set(templates);
            session.menu.getSlot(1).set(source);
            session.menu.getSlot(2).set(new ItemStack(upgrade.addition(), 3));
            h.assertTrue(session.menu.stillValid(session.player), "A physical smithing table backs the native menu");
            h.assertTrue(session.menu.getSlot(3).getItem().is(upgrade.result()), "The next material appears in the native result slot");
            h.assertTrue(ItemStack.matches(session.menu.getSlot(1).getItem(), before), "Preview consumes and changes no equipment");
            h.assertValueEqual(session.menu.getSlot(0).getItem().getCount(), 3, "Preview consumes no templates");
            h.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 3, "Preview consumes no ingots");
            session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
            ItemStack taken = session.menu.getCarried();
            h.assertTrue(taken.is(upgrade.result()) && taken.getCount() == 1, "Normal click takes exactly one upgraded item");
            assertPersonalComponents(h, before, taken);
            h.assertTrue(session.menu.getSlot(1).getItem().isEmpty(), "Normal pickup consumes the old equipment");
            h.assertValueEqual(session.menu.getSlot(0).getItem().getCount(), 2, "Normal pickup consumes one template");
            h.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 2, "Normal pickup consumes one ingot");
            session.menu.setCarried(ItemStack.EMPTY);
            session.menu.getSlot(1).set(before.copy());
            ItemStack moved = session.menu.quickMoveStack(session.player, 3);
            h.assertTrue(moved.is(upgrade.result()) && session.player.getInventory().countItem(upgrade.result()) == 1,
                    "Shift-click transfers exactly one upgraded item to the inventory");
            h.assertTrue(session.menu.getSlot(1).getItem().isEmpty(), "Shift-click consumes exactly one previous item");
            h.assertValueEqual(session.menu.getSlot(0).getItem().getCount(), 1, "Shift-click consumes one more template");
            h.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 1, "Shift-click consumes one more ingot");
            ItemStack delivered = session.player.getInventory().items.stream().filter(stack -> stack.is(upgrade.result())).findFirst().orElseThrow();
            assertPersonalComponents(h, before, delivered);
            h.assertValueEqual(session.player.totalExperience, 0, "An inherited equipment quality requires no second manufacturing XP charge");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void smithingMenuRejectsMissingAndChangedIngredientsWithoutConsumingInputs(GameTestHelper h) {
        try (SmithingSession session = new SmithingSession(h)) {
            Upgrade upgrade = upgrade("mite:copper_sword", "iron");
            session.menu.getSlot(1).set(new ItemStack(upgrade.base()));
            session.menu.getSlot(2).set(new ItemStack(upgrade.addition(), 2));
            h.assertTrue(session.menu.getSlot(3).getItem().isEmpty(), "Equipment plus ingot cannot omit the template");
            session.menu.getSlot(0).set(template());
            h.assertTrue(session.menu.getSlot(3).getItem().is(upgrade.result()), "Adding the required template creates a preview");
            session.menu.getSlot(2).set(new ItemStack(Items.STICK, 2));
            h.assertTrue(session.menu.getSlot(3).getItem().isEmpty(), "Changing the last slot immediately invalidates the old preview");
            session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
            h.assertTrue(session.menu.getCarried().isEmpty(), "A stale result cannot be retrieved by a normal click");
            h.assertTrue(session.menu.quickMoveStack(session.player, 3).isEmpty(), "A stale result cannot be shift-clicked");
            h.assertValueEqual(session.menu.getSlot(0).getItem().getCount(), 1, "Invalid operations consume no template");
            h.assertTrue(session.menu.getSlot(1).getItem().is(upgrade.base()), "Invalid operations preserve the old equipment");
            h.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 2, "Invalid operations consume no extra material");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeSmithingSerializationRetainsMaterialUpgradePreservationOnTheReceivingSide(GameTestHelper h) {
        Upgrade upgrade = upgrade("mite:adamantium_pickaxe", "vibranium");
        SmithingTransformRecipe original = recipe(h, upgrade);
        var ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
        var json = Recipe.CODEC.encodeStart(ops, original).getOrThrow();
        h.assertValueEqual(json.getAsJsonObject().get("type").getAsString(), "minecraft:smithing_transform", "The upgrade uses the native data-pack recipe format");
        var decoded = (SmithingTransformRecipe) Recipe.CODEC.parse(ops, json).getOrThrow();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            SmithingTransformRecipe.Serializer.STREAM_CODEC.encode(buffer, decoded);
            SmithingTransformRecipe restored = SmithingTransformRecipe.Serializer.STREAM_CODEC.decode(buffer);
            ItemStack source = personalEquipment(h, upgrade.base());
            source.remove(DataComponents.FIRE_RESISTANT);
            source.setDamageValue(source.getMaxDamage() / 4);
            SmithingRecipeInput input = input(source, upgrade.addition());
            h.assertTrue(restored.matches(input, h.getLevel()), "Native networking retains all three ingredients");
            ItemStack output = restored.assemble(input, h.getLevel().registryAccess());
            assertPersonalComponents(h, source, output);
            h.assertTrue(output.has(DataComponents.FIRE_RESISTANT), "An explicit old removal cannot erase the new metal's fire resistance");
            h.assertValueEqual(output.getDamageValue(), output.getMaxDamage() / 4, "The received recipe also converts proportional wear");
        } finally { buffer.release(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryNetheriteTemplateRetainsItsPreviousAbsoluteDamageBehavior(GameTestHelper h) {
        var holder = h.getLevel().getRecipeManager().byKey(id("minecraft:netherite_pickaxe_smithing")).orElseThrow();
        var recipe = (SmithingTransformRecipe) holder.value();
        ItemStack source = personalEquipment(h, item("mite:ancient_metal_pickaxe"));
        source.set(DataComponents.MAX_DAMAGE, 400);
        source.setDamageValue(100);
        source.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        SmithingRecipeInput input = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), source,
                new ItemStack(Items.NETHERITE_INGOT));
        h.assertTrue(recipe.matches(input, h.getLevel()), "The existing ancient-metal-to-netherite recipe remains loaded");
        ItemStack output = recipe.assemble(input, h.getLevel().registryAccess());
        h.assertTrue(output.is(Items.NETHERITE_PICKAXE), "The ordinary template still produces netherite");
        assertPersonalComponents(h, source, output);
        h.assertValueEqual(output.getDamageValue(), 100, "The existing native upgrade keeps absolute damage rather than using the new proportional branch");
        h.assertValueEqual(output.get(DataComponents.ATTRIBUTE_MODIFIERS), ItemAttributeModifiers.EMPTY,
                "The native template retains its pre-existing component-copy semantics");
        h.assertValueEqual(output.getOrDefault(DataComponents.MAX_DAMAGE, 0), new ItemStack(Items.NETHERITE_PICKAXE).getOrDefault(DataComponents.MAX_DAMAGE, 0),
                "The existing quality-preservation hook still adopts the target durability base");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void templateHasLoadedSurvivalCreationAndNativeStyleDuplicationRecipes(GameTestHelper h) {
        h.assertValueEqual(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get().getDescriptionId(), "item.mite.equipment_upgrade_smithing_template",
                "The template uses its own registered translated display name");
        CraftingInput firstInput = CraftingInput.of(2, 2, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.STONE),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND)));
        CraftingRecipe first = craftingRecipe(h, "mite:equipment_upgrade_smithing_template");
        h.assertTrue(first.matches(firstInput, h.getLevel()), "Paper, stone, iron and a diamond allow the first template to be obtained in survival");
        CraftingInput noDiamond = CraftingInput.of(2, 2, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.STONE),
                new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY));
        h.assertFalse(first.matches(noDiamond, h.getLevel()), "The previous three-ingredient recipe cannot omit the required diamond");
        ItemStack made = first.assemble(firstInput, h.getLevel().registryAccess());
        h.assertTrue(made.is(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()) && made.getCount() == 1, "Initial crafting creates one template");
        CraftingRecipe copy = craftingRecipe(h, "mite:equipment_upgrade_smithing_template_duplication");
        CraftingInput copyInput = CraftingInput.of(3, 3, List.of(new ItemStack(Items.IRON_INGOT), template(), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STONE), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT)));
        h.assertTrue(copy.matches(copyInput, h.getLevel()), "Seven iron ingots, stone and an old template match the duplication recipe");
        ItemStack copied = copy.assemble(copyInput, h.getLevel().registryAccess());
        h.assertTrue(copied.is(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()) && copied.getCount() == 2, "Duplication consumes the old template and yields two");
        h.assertTrue(copy.getRemainingItems(copyInput).stream().allMatch(ItemStack::isEmpty), "The old template is not also returned as a third copy");
        h.succeed();
    }

    private static void assertMaterialDefaults(GameTestHelper h, ItemStack actual, ItemStack target, String context) {
        h.assertValueEqual(actual.getOrDefault(DataComponents.MAX_DAMAGE, 0), target.getOrDefault(DataComponents.MAX_DAMAGE, 0), "New material supplies base durability: " + context);
        h.assertValueEqual(actual.getMaxStackSize(), target.getMaxStackSize(), "New material supplies stack size: " + context);
        h.assertTrue(Objects.equals(actual.get(DataComponents.ATTRIBUTE_MODIFIERS), target.get(DataComponents.ATTRIBUTE_MODIFIERS)), "New material supplies attack/armor attributes: " + context);
        h.assertTrue(Objects.equals(actual.get(DataComponents.TOOL), target.get(DataComponents.TOOL)), "New material supplies mining rules: " + context);
        h.assertTrue(Objects.equals(actual.get(DataComponents.FIRE_RESISTANT), target.get(DataComponents.FIRE_RESISTANT)), "New material supplies fire resistance: " + context);
    }

    private static void assertPersonalComponents(GameTestHelper h, ItemStack source, ItemStack output) {
        h.assertValueEqual(EquipmentDurability.quality(output), EquipmentDurability.quality(source), "Quality is inherited exactly once");
        h.assertValueEqual(output.get(DataComponents.CUSTOM_NAME), source.get(DataComponents.CUSTOM_NAME), "Name is inherited");
        h.assertValueEqual(output.get(DataComponents.ENCHANTMENTS), source.get(DataComponents.ENCHANTMENTS), "Ordinary enchantment ranks and tooltip data are inherited");
        h.assertValueEqual(output.get(DataComponents.LORE), source.get(DataComponents.LORE), "Lore is inherited");
        h.assertValueEqual(output.get(DataComponents.CUSTOM_DATA), source.get(DataComponents.CUSTOM_DATA), "Third-party custom data is inherited");
        h.assertValueEqual(output.getOrDefault(DataComponents.REPAIR_COST, 0), source.getOrDefault(DataComponents.REPAIR_COST, 0), "Prior anvil use is inherited");
        h.assertValueEqual(GearProgression.state(output), GearProgression.state(source), "Growth UUID, level, XP and independent affix ranks are inherited without rerolling");
    }

    private static ItemStack personalEquipment(GameTestHelper h, Item item) {
        ItemStack stack = new ItemStack(item);
        EquipmentDurability.setQuality(stack, Quality.FINE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 preserved equipment"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("NingMeng123 upgrade history"))));
        stack.set(DataComponents.REPAIR_COST, 7);
        CompoundTag data = new CompoundTag(); data.putString("owner", "NingMeng123");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 7);
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 4, 123_456L,
                Map.of(Enchantments.UNBREAKING.location(), 3, Enchantments.MENDING.location(), 2,
                        id("missing:legacy_preserved_affix"), 231), 1));
        return stack;
    }

    private static SmithingTransformRecipe recipe(GameTestHelper h, Upgrade upgrade) {
        var loaded = h.getLevel().getRecipeManager().byKey(id(upgrade.id())).orElseThrow();
        h.assertTrue(loaded.value() instanceof SmithingTransformRecipe, "An actual native smithing recipe is loaded: " + upgrade.id());
        return (SmithingTransformRecipe) loaded.value();
    }

    private static CraftingRecipe craftingRecipe(GameTestHelper h, String id) {
        return (CraftingRecipe) h.getLevel().getRecipeManager().byKey(id(id)).orElseThrow().value();
    }

    private static Upgrade upgrade(String base, String targetMaterial) {
        return MaterialUpgradeRecipes.upgrades().stream().filter(value -> value.base() == item(base) && value.toMaterial().equals(targetMaterial))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing upgrade " + base + " -> " + targetMaterial));
    }

    private static String family(Item item, String material) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.startsWith("simplyswords_")) path = path.substring("simplyswords_".length());
        String hookSuffix = "_" + material + "_hook";
        if (path.endsWith(hookSuffix)) path = path.substring(0, path.length() - hookSuffix.length());
        String prefix = material + "_";
        if (material.equals("gold") && path.startsWith("golden_")) path = path.substring("golden_".length());
        else if (path.startsWith(prefix)) path = path.substring(prefix.length());
        // MITE names its chainmail family chain_<slot>; vanilla uses chainmail_<slot>.
        return path.startsWith("chain_") ? "chainmail_" + path.substring("chain_".length()) : path;
    }

    private static String nextMaterial(String material) {
        return switch (material) {
            case "copper", "silver", "rusted_iron", "gold" -> "iron";
            case "iron" -> "ancient_metal";
            case "ancient_metal" -> "mithril";
            case "mithril" -> "adamantium";
            case "adamantium" -> "vibranium";
            default -> throw new IllegalArgumentException("Unexpected upgrade source material " + material);
        };
    }

    private static SmithingRecipeInput input(ItemStack source, Item ingot) { return new SmithingRecipeInput(template(), source, new ItemStack(ingot)); }
    private static ItemStack template() { return new ItemStack(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static Item item(String value) { return BuiltInRegistries.ITEM.get(id(value)); }

    private static final class SmithingSession implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final SmithingMenu menu;
        private final BlockPos table;
        private final BlockState previous;

        private SmithingSession(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-upgrade-ui"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            previous = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, Blocks.SMITHING_TABLE.defaultBlockState());
            menu = new SmithingMenu(37, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
        }

        @Override public void close() {
            try {
                for (int slot = 0; slot < 4; slot++) menu.getSlot(slot).set(ItemStack.EMPTY);
                menu.setCarried(ItemStack.EMPTY);
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
