package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.combat.ShieldGuard;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.MaterialUpgradeRequirements;
import io.github.ningmeng123.mite.crafting.TimedSmithingController;
import io.github.ningmeng123.mite.data.MaterialUpgradeRecipes;
import io.github.ningmeng123.mite.data.MaterialUpgradeRecipes.Upgrade;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipes;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteUpgradeTemplates;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
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
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
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
        for (Upgrade upgrade : MaterialUpgradeRecipes.smithingUpgrades()) {
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
    public static void allSmithingRecipesRequireTheCorrectTemplateBaseAndNextMaterial(GameTestHelper h) {
        for (Upgrade upgrade : MaterialUpgradeRecipes.smithingUpgrades()) {
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
            Upgrade upgrade = upgrade("mite:silver_" + family, "iron");
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
    public static void fullIronToVibraniumSmithingRouteKeepsIdentityLevelsExperienceAndLegacyAffixes(GameTestHelper h) {
        ItemStack current = personalEquipment(h, item("minecraft:iron_sword"));
        GearProgress original = GearProgression.state(current);
        for (var step : List.of(Map.entry("ancient_metal", 25), Map.entry("mithril", 25),
                Map.entry("adamantium", 25), Map.entry("vibranium", 25))) {
            Upgrade upgrade = upgrade(BuiltInRegistries.ITEM.getKey(current.getItem()).toString(), step.getKey());
            ItemStack before = current.copy();
            current = recipe(h, upgrade).assemble(input(current, upgrade.addition()), h.getLevel().registryAccess());
            h.assertValueEqual(GearProgression.state(current), original, "Each material upgrade preserves the exact growth UUID, level, XP and every historical affix");
            h.assertValueEqual(GearProgression.maxLevel(current), step.getValue(), "Standalone material upgrades retain the uniform growth cap");
            assertPersonalComponents(h, before, current);
        }
        h.assertTrue(current.is(item("mite:vibranium_sword")), "Four consecutive smithing steps reach vibranium after the workbench upgrade to iron");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void proportionalDamageRoundsWithoutCreatingAnAlreadyBrokenOutput(GameTestHelper h) {
        Upgrade upgrade = upgrade("mite:silver_pickaxe", "iron");
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
    public static void actualSmithingMenuTimesBothTakePathsAndConsumesTheFullTargetCost(GameTestHelper h) {
        try (SmithingSession session = new SmithingSession(h)) {
            Upgrade upgrade = upgrade("mite:ancient_metal_helmet", "mithril");
            ItemStack source = personalEquipment(h, upgrade.base()), before = source.copy();
            ItemStack templates = template(); templates.setCount(3);
            session.menu.getSlot(0).set(templates);
            session.menu.getSlot(1).set(source);
            session.menu.getSlot(2).set(new ItemStack(upgrade.addition(), 6));
            h.assertTrue(session.menu.stillValid(session.player), "A physical smithing table backs the native menu");
            h.assertTrue(session.menu.getSlot(3).getItem().is(upgrade.result()), "The next material appears in the native result slot");
            h.assertTrue(ItemStack.matches(session.menu.getSlot(1).getItem(), before), "Preview consumes and changes no equipment");
            h.assertValueEqual(session.menu.getSlot(0).getItem().getCount(), 3, "Preview consumes no templates");
            h.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 6, "Preview consumes no ingots");
            session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
            int period = TimedSmithingController.period(session.player, session.menu);
            h.assertTrue(period > 25, "A fine mithril helmet uses its real quality-adjusted manufacturing difficulty");
            session.tick(period - 1);
            h.assertTrue(session.menu.getCarried().isEmpty(), "A normal click cannot take an upgrade before the complete period");
            h.assertTrue(ItemStack.matches(session.menu.getSlot(1).getItem(), before), "Waiting consumes no old equipment");
            h.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 6, "Waiting consumes no metal");
            session.tick(1);
            ItemStack taken = session.menu.getCarried();
            h.assertTrue(taken.is(upgrade.result()) && taken.getCount() == 1, "Normal click takes exactly one upgraded item");
            assertPersonalComponents(h, before, taken);
            h.assertTrue(session.menu.getSlot(1).getItem().isEmpty(), "Normal pickup consumes the old equipment");
            h.assertValueEqual(session.menu.getSlot(0).getItem().getCount(), 2, "Normal pickup consumes one template");
            h.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 1, "Normal pickup consumes all five mithril ingots and preserves the extra ingot");
            session.menu.setCarried(ItemStack.EMPTY);
            session.menu.getSlot(1).set(before.copy());
            session.menu.getSlot(2).set(new ItemStack(upgrade.addition(), 6));
            ItemStack moved = session.menu.quickMoveStack(session.player, 3);
            h.assertTrue(moved.isEmpty() && session.player.getInventory().countItem(upgrade.result()) == 0,
                    "Direct quick-move starts manufacturing rather than immediately transferring the preview");
            session.tick(period - 1);
            h.assertValueEqual(session.player.getInventory().countItem(upgrade.result()), 0, "Shift transfer also waits for the complete period");
            h.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 6, "Shift waiting consumes none of the replenished metal");
            session.tick(1);
            h.assertValueEqual(session.player.getInventory().countItem(upgrade.result()), 1, "Shift manufacturing delivers exactly one upgraded item");
            h.assertTrue(session.menu.getSlot(1).getItem().isEmpty(), "Shift-click consumes exactly one previous item");
            h.assertValueEqual(session.menu.getSlot(0).getItem().getCount(), 1, "Shift-click consumes one more template");
            h.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 1, "Shift manufacturing consumes five more ingots and preserves the extra ingot");
            ItemStack delivered = session.player.getInventory().items.stream().filter(stack -> stack.is(upgrade.result())).findFirst().orElseThrow();
            assertPersonalComponents(h, before, delivered);
            h.assertValueEqual(session.player.totalExperience, 0, "An inherited equipment quality requires no second manufacturing XP charge");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void smithingMenuRejectsMissingAndChangedIngredientsWithoutConsumingInputs(GameTestHelper h) {
        try (SmithingSession session = new SmithingSession(h)) {
            Upgrade upgrade = upgrade("mite:silver_sword", "iron");
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
    public static void mithrilHelmetRequiresFiveIngotsInBothNativeMatchingAndRealMenu(GameTestHelper helper) {
        Upgrade upgrade = upgrade("mite:ancient_metal_helmet", "mithril");
        helper.assertValueEqual(MaterialUpgradeRequirements.ingots(new ItemStack(upgrade.result())), 5, "A mithril helmet costs its complete five-ingot recipe");
        for (int available : new int[]{4, 5, 6}) {
            try (SmithingSession session = new SmithingSession(helper)) {
                ItemStack source = personalEquipment(helper, upgrade.base());
                session.load(upgrade, source, available);
                var nativeInput = new SmithingRecipeInput(template(), source, new ItemStack(upgrade.addition(), available));
                helper.assertTrue(recipe(helper, upgrade).matches(nativeInput, helper.getLevel()) == (available >= 5),
                        "Native recipe matching includes the full material quantity: " + available);
                var before = TimedCraftingController.snapshot(session.menu.getSlot(0).container);
                session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                if (available < 5) {
                    helper.assertTrue(session.menu.getSlot(3).getItem().isEmpty(), "Four ingots cannot advertise a valid helmet result");
                    helper.assertFalse(TimedSmithingController.isActive(session.player), "Insufficient metal cannot start an upgrade");
                    helper.assertTrue(session.menu.quickMoveStack(session.player, 3).isEmpty(), "Direct quick-move cannot bypass insufficient metal");
                    session.tick(200);
                    helper.assertTrue(TimedCraftingController.matches(before, session.menu.getSlot(0).container), "Insufficient metal leaves all three inputs unchanged");
                    helper.assertTrue(session.menu.getCarried().isEmpty(), "Insufficient metal produces nothing");
                } else {
                    int period = TimedSmithingController.period(session.player, session.menu);
                    session.tick(period - 1);
                    helper.assertTrue(TimedCraftingController.matches(before, session.menu.getSlot(0).container), "Inputs remain intact until the final tick");
                    session.tick(1);
                    helper.assertTrue(session.menu.getCarried().is(upgrade.result()), "The complete period produces the requested helmet");
                    helper.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), available - 5, "Exactly five ingots are consumed, preserving any extra ingot");
                    helper.assertTrue(session.menu.getSlot(0).getItem().isEmpty() && session.menu.getSlot(1).getItem().isEmpty(), "One template and one previous helmet are consumed");
                    session.tick(period * 2);
                    helper.assertValueEqual(session.menu.getCarried().getCount(), 1, "A completed request cannot manufacture a second item");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smithingAlternateClicksAndDirectSlotCallsCannotTakeTheUnfinishedPreview(GameTestHelper helper) {
        try (SmithingSession session = new SmithingSession(helper)) {
            Upgrade upgrade = upgrade("mite:ancient_metal_helmet", "mithril");
            session.load(upgrade, personalEquipment(helper, upgrade.base()), 6);
            var before = TimedCraftingController.snapshot(session.menu.getSlot(0).container);
            session.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            for (ClickType click : List.of(ClickType.SWAP, ClickType.THROW, ClickType.PICKUP_ALL, ClickType.QUICK_CRAFT))
                session.menu.clicked(3, 0, click, session.player);
            session.menu.clicked(3, 40, ClickType.SWAP, session.player);
            session.player.setGameMode(GameType.CREATIVE);
            session.menu.clicked(3, 2, ClickType.CLONE, session.player);
            session.player.setGameMode(GameType.SURVIVAL);
            helper.assertFalse(session.menu.getSlot(3).mayPickup(session.player), "The anonymous native smithing result slot refuses unfinished pickup");
            helper.assertTrue(session.menu.getSlot(3).safeTake(1, Integer.MAX_VALUE, session.player).isEmpty(), "Direct safeTake cannot bypass the native mayPickup gate");
            session.menu.getSlot(3).onTake(session.player, session.menu.getSlot(3).getItem().copy());
            helper.assertTrue(TimedCraftingController.matches(before, session.menu.getSlot(0).container), "Unscoped onTake cannot consume inputs or authorize an upgrade");
            helper.assertTrue(session.menu.getCarried().isEmpty(), "Swap, throw, collect, drag and creative clone cannot take the preview");
            helper.assertValueEqual(session.player.getInventory().countItem(upgrade.result()), 0, "No alternate click delivers an upgraded helmet");
            helper.assertFalse(TimedSmithingController.isActive(session.player), "Unsupported result clicks do not queue manufacturing");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedSmithingRequestsDoNotAccelerateOrChangeTheOriginalDeliveryMode(GameTestHelper helper) {
        try (SmithingSession session = new SmithingSession(helper)) {
            Upgrade upgrade = upgrade("mite:ancient_metal_helmet", "mithril");
            session.load(upgrade, personalEquipment(helper, upgrade.base()), 8);
            session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
            int period = TimedSmithingController.period(session.player, session.menu);
            session.tick(1);
            for (int duplicate = 0; duplicate < 10; duplicate++) {
                session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                session.menu.clicked(3, 0, ClickType.QUICK_MOVE, session.player);
                session.menu.quickMoveStack(session.player, 3);
            }
            helper.assertValueEqual(TimedSmithingController.progress(session.player), 1, "Repeated clicks neither queue, reset nor advance the existing period");
            session.tick(period - 1);
            helper.assertTrue(session.menu.getCarried().is(upgrade.result()), "The original ordinary click still delivers to the cursor");
            helper.assertValueEqual(session.player.getInventory().countItem(upgrade.result()), 0, "Later Shift clicks cannot change the active transaction's destination");
            helper.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 3, "Repeated requests consume only one complete five-ingot batch");
            helper.assertFalse(TimedSmithingController.isActive(session.player), "Smithing never silently queues the next equipment item");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void changedSmithingComponentsQuantitiesAndClosedOrBrokenTablesCancelWithoutPayment(GameTestHelper helper) {
        for (int mutation = 0; mutation < 4; mutation++) {
            try (SmithingSession session = new SmithingSession(helper)) {
                Upgrade upgrade = upgrade("mite:ancient_metal_helmet", "mithril");
                session.load(upgrade, personalEquipment(helper, upgrade.base()), 6);
                session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
                int period = TimedSmithingController.period(session.player, session.menu);
                session.tick(5);
                if (mutation == 0) {
                    ItemStack changed = session.menu.getSlot(1).getItem().copy();
                    changed.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 changed during manufacturing"));
                    session.menu.getSlot(1).set(changed);
                } else if (mutation == 1) session.menu.getSlot(2).set(new ItemStack(upgrade.addition(), 4));
                else if (mutation == 2) {
                    session.menu.removed(session.player);
                    session.player.containerMenu = session.player.inventoryMenu;
                } else helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(1, 1, 1)), Blocks.AIR.defaultBlockState());
                session.tick(period + 1);
                helper.assertFalse(TimedSmithingController.isActive(session.player), "Changed inputs, a closed menu or a removed physical table cancel the original session");
                helper.assertTrue(session.menu.getCarried().isEmpty(), "Canceled manufacture never delivers to the cursor");
                helper.assertValueEqual(session.player.getInventory().countItem(upgrade.result()), 0, "Canceled manufacture never delivers an inventory output");
                if (mutation != 2) {
                    helper.assertValueEqual(session.menu.getSlot(0).getItem().getCount(), 1, "Cancellation consumes no template");
                    helper.assertTrue(session.menu.getSlot(1).getItem().is(upgrade.base()), "Cancellation preserves the source equipment");
                    helper.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), mutation == 1 ? 4 : 6, "Cancellation consumes no additional ingot");
                } else {
                    helper.assertValueEqual(session.player.getInventory().countItem(upgrade.base()), 1, "Closing returns the original equipment through the native menu cleanup");
                    helper.assertValueEqual(session.player.getInventory().countItem(upgrade.addition()), 6, "Closing returns every unconsumed ingot");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rightClickCancelsAndHungerOrDamageCannotBypassSmithingTime(GameTestHelper helper) {
        try (SmithingSession session = new SmithingSession(helper)) {
            Upgrade upgrade = upgrade("mite:ancient_metal_helmet", "mithril");
            session.load(upgrade, personalEquipment(helper, upgrade.base()), 5);
            session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
            int period = TimedSmithingController.period(session.player, session.menu);
            session.tick(100);
            TimedSmithingController.damaged(session.player, 1F);
            helper.assertValueEqual(TimedSmithingController.progress(session.player), 50, "Damage applies the same fifty-tick setback as workbench manufacturing");
            session.player.getFoodData().setFoodLevel(0);
            ((DualFoodData)session.player.getFoodData()).mite$setNutrition(0);
            session.tick(period);
            helper.assertValueEqual(TimedSmithingController.progress(session.player), 50, "No food energy pauses rather than completing or consuming ingredients");
            session.menu.clicked(3, 1, ClickType.PICKUP, session.player);
            helper.assertFalse(TimedSmithingController.isActive(session.player), "Right-click cancels the paused upgrade");
            session.player.getFoodData().setFoodLevel(6);
            session.tick(period);
            helper.assertTrue(session.menu.getCarried().isEmpty(), "Food returning cannot replay a canceled request");
            session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
            helper.assertValueEqual(TimedSmithingController.progress(session.player), 0, "A new request starts its own complete period");
            session.tick(period);
            helper.assertTrue(session.menu.getCarried().is(upgrade.result()), "A fresh full period completes after food returns");
            helper.assertValueEqual(session.player.totalExperience, 0, "Preserved fine quality does not charge manufacturing XP a second time");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smithingCannotConsumeInputsWithoutAnAvailableDeliveryDestination(GameTestHelper helper) {
        try (SmithingSession session = new SmithingSession(helper)) {
            Upgrade upgrade = upgrade("mite:ancient_metal_helmet", "mithril");
            session.load(upgrade, personalEquipment(helper, upgrade.base()), 5);
            var before = TimedCraftingController.snapshot(session.menu.getSlot(0).container);
            session.menu.setCarried(new ItemStack(Items.DIAMOND));
            session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
            helper.assertFalse(TimedSmithingController.isActive(session.player), "An incompatible occupied cursor cannot start an upgrade");
            session.menu.setCarried(ItemStack.EMPTY);
            for (int slot = 0; slot < session.player.getInventory().items.size(); slot++)
                session.player.getInventory().setItem(slot, new ItemStack(Items.DIAMOND, 64));
            session.menu.clicked(3, 0, ClickType.QUICK_MOVE, session.player);
            helper.assertFalse(TimedSmithingController.isActive(session.player), "A full inventory cannot start an immediate overflow-producing shift transaction");
            helper.assertTrue(TimedCraftingController.matches(before, session.menu.getSlot(0).container), "Rejected delivery leaves every input untouched");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualContainerPacketsCannotDeclareAnUpgradeCompleteOrAccelerateIt(GameTestHelper helper) {
        try (SmithingSession session = new SmithingSession(helper)) {
            Upgrade upgrade = upgrade("mite:ancient_metal_helmet", "mithril");
            session.load(upgrade, personalEquipment(helper, upgrade.base()), 5);
            int period = TimedSmithingController.period(session.player, session.menu);
            session.player.connection.handleContainerClick(new ServerboundContainerClickPacket(session.menu.containerId,
                    session.menu.getStateId(), 3, 0, ClickType.PICKUP, new ItemStack(upgrade.result()), Int2ObjectMaps.emptyMap()));
            helper.assertTrue(TimedSmithingController.isActive(session.player), "The real server packet starts an authoritative timed transaction");
            helper.assertTrue(session.menu.getCarried().isEmpty(), "A client-reported carried result cannot manufacture an output");
            session.tick(period - 1);
            session.player.connection.handleContainerClick(new ServerboundContainerClickPacket(session.menu.containerId,
                    session.menu.getStateId(), 3, 0, ClickType.QUICK_MOVE, new ItemStack(upgrade.result()), Int2ObjectMaps.emptyMap()));
            helper.assertTrue(session.menu.getCarried().isEmpty(), "A second real packet cannot bypass the final required tick");
            session.tick(1);
            helper.assertTrue(session.menu.getCarried().is(upgrade.result()), "Only the authoritative final tick delivers the original single output");
            helper.assertTrue(session.menu.getSlot(0).getItem().isEmpty() && session.menu.getSlot(1).getItem().isEmpty()
                    && session.menu.getSlot(2).getItem().isEmpty(), "The packet transaction consumes one complete recipe exactly once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryArmorTrimKeepsItsNativeInstantSingleIngredientTransaction(GameTestHelper helper) {
        try (SmithingSession session = new SmithingSession(helper)) {
            session.menu.getSlot(0).set(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE));
            session.menu.getSlot(1).set(new ItemStack(Items.IRON_CHESTPLATE));
            session.menu.getSlot(2).set(new ItemStack(Items.QUARTZ, 2));
            helper.assertTrue(session.menu.getSlot(3).getItem().has(DataComponents.TRIM), "An unrelated native armor-trim recipe still creates its normal preview");
            session.menu.clicked(3, 0, ClickType.PICKUP, session.player);
            helper.assertTrue(session.menu.getCarried().is(Items.IRON_CHESTPLATE) && session.menu.getCarried().has(DataComponents.TRIM), "Other smithing templates retain their immediate native transaction");
            helper.assertValueEqual(session.menu.getSlot(2).getItem().getCount(), 1, "Native armor trim still consumes only its one matching material");
            helper.assertFalse(TimedSmithingController.isActive(session.player), "Armor trim never starts a MITE equipment-upgrade timer");
        }
        helper.succeed();
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
    public static void ordinaryNetheriteTemplateCannotRestoreRemovedEquipment(GameTestHelper h) {
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("minecraft:netherite_pickaxe_smithing")).isEmpty(),
                "The retired netherite upgrade recipe is not loaded");
        ItemStack source = personalEquipment(h, item("mite:ancient_metal_pickaxe"));
        SmithingRecipeInput input = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), source,
                new ItemStack(Items.NETHERITE_INGOT));
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, h.getLevel()).isEmpty(),
                "The old template, ancient metal and netherite ingot cannot restore retired equipment");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void templateHasOnlyItsLoadedSurvivalCreationRecipeAndNoDuplication(GameTestHelper h) {
        h.assertValueEqual(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get().getDescriptionId(), "item.mite.equipment_upgrade_smithing_template",
                "The template uses its own registered translated display name");
        CraftingRecipe first = craftingRecipe(h, "mite:equipment_upgrade_smithing_template");
        h.assertTrue(first.getSerializer() == RecipeSerializer.SHAPED_RECIPE, "The pictured layout uses a shaped recipe");
        for (int silverCorners = 0; silverCorners < 16; silverCorners++) {
            List<ItemStack> ingredients = templateIngredients(silverCorners, 1);
            CraftingInput firstInput = CraftingInput.of(3, 3, ingredients);
            h.assertTrue(first.matches(firstInput, h.getLevel()), "Every corner independently accepts copper or silver: " + silverCorners);
            ItemStack made = first.assemble(firstInput, h.getLevel().registryAccess());
            h.assertTrue(made.is(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()) && made.getCount() == 1, "Initial crafting creates one template");
            for (int slot = 0; slot < ingredients.size(); slot++) {
                List<ItemStack> missing = new java.util.ArrayList<>(ingredients);
                missing.set(slot, ItemStack.EMPTY);
                h.assertFalse(first.matches(CraftingInput.of(3, 3, missing), h.getLevel()), "All nine ingredients are required: " + slot);
            }
            List<ItemStack> diamondCenter = new java.util.ArrayList<>(ingredients);
            diamondCenter.set(4, new ItemStack(Items.DIAMOND));
            h.assertFalse(first.matches(CraftingInput.of(3, 3, diamondCenter), h.getLevel()), "A diamond cannot replace the required central lapis lazuli");
            java.util.Collections.swap(ingredients, 1, 3);
            h.assertFalse(first.matches(CraftingInput.of(3, 3, ingredients), h.getLevel()), "Iron and gold must occupy their pictured positions");
        }
        h.assertFalse(first.matches(CraftingInput.of(2, 2, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.STONE),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND))), h.getLevel()), "The previous shapeless recipe is retired");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("mite:equipment_upgrade_smithing_template_duplication")).isEmpty(),
                "The removed duplication recipe is absent from the loaded server recipe set");
        CraftingInput copyInput = CraftingInput.of(3, 3, List.of(new ItemStack(Items.IRON_INGOT), template(), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STONE), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT)));
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, copyInput, h.getLevel()).isEmpty(),
                "An old template, seven iron ingots and stone cannot manufacture more templates");
        h.assertValueEqual(h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(holder -> holder.value().getResultItem(h.getLevel().registryAccess()).is(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get())).count(),
                1L, "Only the copper/silver, iron, gold and lapis lazuli layout produces the template");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void templatePickupAndShiftClickWaitForOneCompleteManufacturingPeriod(GameTestHelper h) {
        for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE)) {
            try (CraftingSession session = new CraftingSession(h)) {
                session.load(click == ClickType.PICKUP ? 0 : 9, 2);
                h.assertValueEqual(TimedCraftingController.period(session.player, session.menu), 140,
                        "A level-zero iron workbench applies its normal speed bonus to template manufacture");
                TimedCraftingTestSupport.craftOne(h, session.player, session.menu, click);
                h.assertValueEqual(session.player.getInventory().countItem(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()), 1,
                        "A completed pickup or shift-click produces exactly one template");
                for (int slot = 0; slot < 9; slot++) h.assertValueEqual(session.grid.getItem(slot).getCount(), 1,
                        "Exactly one material in each of the nine input slots is consumed");
                h.assertTrue(session.menu.getCarried().isEmpty(), "The timed result cannot also appear on the cursor");
                h.assertValueEqual(session.player.totalExperience, 0, "A non-equipment template does not acquire equipment quality costs");
                PlayerExperience.setLevels(session.player, 10);
                h.assertTrue(TimedCraftingController.period(session.player, session.menu) < 140,
                        "The template inherits existing experience-level manufacturing speed bonuses");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void exchangingACopperCornerForSilverCancelsTheUnfinishedTemplateBatch(GameTestHelper h) {
        try (CraftingSession session = new CraftingSession(h)) {
            session.load(0, 2);
            session.menu.clicked(0, 0, ClickType.QUICK_MOVE, session.player);
            session.tick(70);
            h.assertTrue(TimedCraftingController.isActive(session.player) && TimedCraftingController.progress(session.player) > 0,
                    "The actual shift-click has an unfinished template batch");
            session.grid.setItem(0, new ItemStack(MiteMaterials.SILVER_INGOT.get(), 2));
            session.menu.slotsChanged(session.grid);
            session.tick(280);
            h.assertFalse(TimedCraftingController.isActive(session.player), "A changed copper/silver input snapshot cancels manufacture");
            h.assertValueEqual(session.player.getInventory().countItem(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()), 0,
                    "Changing valid ingredient alternatives cannot deliver the old preview");
            for (int slot = 0; slot < 9; slot++) h.assertValueEqual(session.grid.getItem(slot).getCount(), 2,
                    "Cancellation consumes none of the remaining ingredients");
            TimedCraftingTestSupport.craftOne(h, session.player, session.menu, ClickType.PICKUP);
            h.assertValueEqual(session.player.getInventory().countItem(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()), 1,
                    "The valid mixed-metal replacement recipe must wait a fresh complete period");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void jeiTemplatePagesUseTheSameManufacturingRuleAndOmitDuplication(GameTestHelper h) {
        var index = WorkbenchJeiRecipes.index(h.getLevel());
        var pages = index.recipes().stream().filter(page -> page.output().is(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get())).toList();
        Map<String, Integer> periods = templateWorkbenchPeriods();
        h.assertValueEqual(pages.stream().map(page -> page.workstationId()).collect(java.util.stream.Collectors.toSet()), periods.keySet(),
                "JEI lists the template only on iron and higher workbenches, with no sub-iron or inventory page");
        for (var page : pages) {
            h.assertValueEqual(page.source().id(), id("mite:equipment_upgrade_smithing_template"),
                    "Every template page comes from its single surviving creation recipe");
            h.assertValueEqual(page.inputs().size(), 9, "JEI retains the complete nine-slot template layout");
            h.assertTrue(page.inputs().get(4).size() == 1 && page.inputs().get(4).getFirst().is(Items.LAPIS_LAZULI)
                    && page.inputs().get(4).getFirst().getCount() == 1, "JEI displays one central lapis lazuli");
            h.assertTrue(page.inputs().stream().flatMap(List::stream).noneMatch(stack -> stack.is(Items.DIAMOND)),
                    "Template creation no longer advertises a diamond ingredient");
            h.assertTrue(page.rule() != null, "JEI receives manufacturing metadata instead of an instant-crafting page");
            h.assertValueEqual(page.rule().difficulty(), 400F, "JEI uses the template's actual manufacturing difficulty");
            h.assertValueEqual(page.rule().requiredBench(), "iron", "Template manufacturing has an iron workbench minimum");
            h.assertValueEqual(page.requiredCapacity(), 8F, "JEI enforces the iron material capacity");
            h.assertValueEqual(page.baselineTicks(), periods.get(page.workstationId()).intValue(),
                    "JEI reports each permitted workbench's actual material speed");
        }
        h.assertTrue(index.originals().stream().noneMatch(holder -> holder.id().equals(id("mite:equipment_upgrade_smithing_template_duplication"))),
                "The removed duplication recipe has no surviving native or custom JEI page");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void templateManufacturingRequiresIronAndAcceptsEveryHigherWorkbench(GameTestHelper h) {
        Map<String, Integer> periods = templateWorkbenchPeriods();
        for (String material : MiteWorkbenches.TABLES.keySet()) {
            try (CraftingSession session = new CraftingSession(h, material)) {
                session.load(6, 2);
                Integer expectedPeriod = periods.get("mite:" + material + "_crafting_table");
                if (expectedPeriod == null) {
                    h.assertTrue(session.menu.getSlot(0).getItem().isEmpty(),
                            "A sub-iron workbench cannot advertise a usable template preview");
                    h.assertFalse(TimedCraftingController.start(session.player, session.menu),
                            "A " + material + " workbench cannot start template manufacturing");
                    for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE)) {
                        session.menu.clicked(0, 0, click, session.player);
                        session.tick(280);
                        h.assertFalse(TimedCraftingController.isActive(session.player), "Clicks cannot bypass the iron workbench minimum");
                        h.assertValueEqual(session.player.getInventory().countItem(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()), 0,
                                "A sub-iron workbench never delivers a template");
                        h.assertTrue(session.menu.getCarried().isEmpty(), "The result cannot bypass the gate on the cursor");
                        for (int slot = 0; slot < 9; slot++) h.assertValueEqual(session.grid.getItem(slot).getCount(), 2,
                                "Rejected template manufacture consumes none of its input materials");
                    }
                } else {
                    h.assertTrue(session.menu.getSlot(0).getItem().is(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()),
                            "Every permitted workbench displays the actual template preview");
                    h.assertValueEqual(TimedCraftingController.period(session.player, session.menu), expectedPeriod.intValue(),
                            "A " + material + " workbench applies its ordinary material speed bonus");
                    TimedCraftingTestSupport.craftOne(h, session.player, session.menu, ClickType.PICKUP);
                    h.assertValueEqual(session.player.getInventory().countItem(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()), 1,
                            "Every iron-or-higher workbench completes one real template batch");
                    for (int slot = 0; slot < 9; slot++) h.assertValueEqual(session.grid.getItem(slot).getCount(), 1,
                            "A permitted workbench consumes precisely one complete material batch");
                }
            }
        }
        h.succeed();
    }

    private static Map<String, Integer> templateWorkbenchPeriods() {
        return Map.of("mite:iron_crafting_table", 140, "mite:ancient_metal_crafting_table", 130,
                "mite:mithril_crafting_table", 122, "mite:adamantium_crafting_table", 115, "mite:vibranium_crafting_table", 115);
    }

    private static List<ItemStack> templateIngredients(int silverCorners, int count) {
        List<ItemStack> result = new java.util.ArrayList<>(List.of(
                new ItemStack(Items.COPPER_INGOT, count), new ItemStack(Items.IRON_INGOT, count), new ItemStack(Items.COPPER_INGOT, count),
                new ItemStack(Items.GOLD_INGOT, count), new ItemStack(Items.LAPIS_LAZULI, count), new ItemStack(Items.GOLD_INGOT, count),
                new ItemStack(Items.COPPER_INGOT, count), new ItemStack(Items.IRON_INGOT, count), new ItemStack(Items.COPPER_INGOT, count)));
        int[] corners = {0, 2, 6, 8};
        for (int corner = 0; corner < corners.length; corner++) if ((silverCorners & (1 << corner)) != 0)
            result.set(corners[corner], new ItemStack(MiteMaterials.SILVER_INGOT.get(), count));
        return result;
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

    private static SmithingRecipeInput input(ItemStack source, Item ingot) {
        int count = MaterialUpgradeRecipes.smithingUpgrades().stream()
                .filter(upgrade -> upgrade.base() == source.getItem() && upgrade.addition() == ingot)
                .findFirst().map(upgrade -> MaterialUpgradeRequirements.ingots(new ItemStack(upgrade.result()))).orElse(1);
        return new SmithingRecipeInput(template(), source, new ItemStack(ingot, count));
    }
    private static ItemStack template() { return new ItemStack(MiteUpgradeTemplates.UPGRADE_TEMPLATE.get()); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static Item item(String value) { return BuiltInRegistries.ITEM.get(id(value)); }

    private static class MenuSession<T extends AbstractContainerMenu> implements AutoCloseable {
        private final GameTestHelper helper;
        final ServerPlayer player;
        private final EmbeddedChannel channel;
        final T menu;
        private final BlockPos table;
        private final BlockState previous;
        private final int inputSlots;

        private MenuSession(GameTestHelper helper, BlockState tableState, int inputSlots,
                BiFunction<ServerPlayer, ContainerLevelAccess, T> createMenu) {
            this.helper = helper;
            this.inputSlots = inputSlots;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-upgrade-ui"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getFoodData().setFoodLevel(6);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            previous = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, tableState);
            menu = createMenu.apply(player, ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
        }

        @Override public void close() {
            try {
                TimedCraftingController.forget(player);
                TimedSmithingController.cancel(player);
                for (int slot = 0; slot < inputSlots; slot++) menu.getSlot(slot).set(ItemStack.EMPTY);
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

    private static final class SmithingSession extends MenuSession<SmithingMenu> {
        private SmithingSession(GameTestHelper helper) {
            super(helper, Blocks.SMITHING_TABLE.defaultBlockState(), 4,
                    (player, access) -> new SmithingMenu(37, player.getInventory(), access));
        }
        void load(Upgrade upgrade, ItemStack source, int ingots) {
            menu.getSlot(0).set(template());
            menu.getSlot(1).set(source);
            menu.getSlot(2).set(new ItemStack(upgrade.addition(), ingots));
        }
        void tick(int ticks) { for (int tick = 0; tick < ticks; tick++) TimedSmithingController.tick(player); }
    }

    private static final class CraftingSession extends MenuSession<CraftingMenu> {
        private final GameTestHelper helper;
        final CraftingContainer grid;
        private CraftingSession(GameTestHelper helper) {
            this(helper, "iron");
        }
        private CraftingSession(GameTestHelper helper, String material) {
            super(helper, MiteWorkbenches.TABLES.get(material).get().defaultBlockState(), 10,
                    (player, access) -> new CraftingMenu(38, player.getInventory(), access));
            this.helper = helper;
            grid = (CraftingContainer) menu.getSlot(1).container;
        }
        void load(int silverCorners, int count) {
            List<ItemStack> ingredients = templateIngredients(silverCorners, count);
            for (int slot = 0; slot < ingredients.size(); slot++) grid.setItem(slot, ingredients.get(slot));
            menu.slotsChanged(grid);
            helper.assertTrue(menu.stillValid(player), "The template is made in a real nearby workbench");
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), helper.getLevel())
                    .map(holder -> holder.id().equals(id("mite:equipment_upgrade_smithing_template"))).orElse(false),
                    "The actual grid matches the sole loaded template creation recipe");
        }
        void tick(int ticks) { for (int tick = 0; tick < ticks; tick++) TimedCraftingController.tick(player); }
    }
}
