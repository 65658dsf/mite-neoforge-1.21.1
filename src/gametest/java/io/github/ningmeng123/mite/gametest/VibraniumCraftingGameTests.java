package io.github.ningmeng123.mite.gametest;

import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.crafting.BulkMaterialRecipe;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.crafting.VibraniumUpgradeRecipe;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded alloy, manufacture, repair, storage and portable workstation integration. @author NingMeng123 */
@GameTestHolder("mite_vibranium")
@PrefixGameTestTemplate(false)
public final class VibraniumCraftingGameTests {
    private static final List<String> GEAR = List.of("hatchet", "axe", "pickaxe", "shovel", "hoe", "mattock",
            "scythe", "shears", "dagger", "knife", "sword", "battle_axe", "war_hammer",
            "helmet", "chestplate", "leggings", "boots", "shield", "bow");
    private VibraniumCraftingGameTests() {}

    @GameTest(template = "empty")
    public static void diamondAlloyUsesTheReferenceLayoutAndBootstrapsAtAdamantium(GameTestHelper h) {
        Item nugget = MiteMaterials.MITHRIL_NUGGET.get(), adamantium = MiteMaterials.ADAMANTIUM_INGOT.get();
        CraftingInput input = CraftingInput.of(3, 3, List.of(new ItemStack(nugget), new ItemStack(adamantium), new ItemStack(nugget),
                new ItemStack(adamantium), new ItemStack(Items.DIAMOND), new ItemStack(adamantium),
                new ItemStack(nugget), new ItemStack(adamantium), new ItemStack(nugget)));
        var alloy = recipe(h, "vibranium_ingot");
        h.assertTrue(alloy.value().matches(input, h.getLevel()), "NIN / IDI / NIN uses four mithril nuggets, four adamantium ingots and one diamond");
        ItemStack output = alloy.value().assemble(input, h.getLevel().registryAccess());
        h.assertTrue(output.is(MiteMaterials.VIBRANIUM_INGOT.get()) && output.getCount() == 1, "One complete alloy batch yields one vibranium ingot");
        h.assertValueEqual(WorkbenchRules.required(output), 256F, "The first vibranium ingot requires the preceding adamantium tier");
        var rule = CraftingQuality.rule(alloy, output, input);
        h.assertTrue(rule != null && rule.difficulty() > 100000F, "The loaded alloy participates in timed manufacturing");
        h.assertValueEqual(rule.requiredBench(), "adamantium", "Alloy timing uses the same bootstrap bench");
        assertBench(h, output, "mithril", false);
        assertBench(h, output, "adamantium", true);
        List<ItemStack> wrong = new ArrayList<>(input.items());
        wrong.set(4, new ItemStack(Items.EMERALD));
        h.assertFalse(alloy.value().matches(CraftingInput.of(3, 3, wrong), h.getLevel()), "An emerald cannot replace the diamond in this reference version");

        var bench = recipe(h, "vibranium_crafting_table");
        CraftingInput benchInput = sample(bench.value());
        h.assertTrue(bench.value().matches(benchInput, h.getLevel()), "The registered vibranium workbench recipe is craftable");
        ItemStack table = bench.value().assemble(benchInput, h.getLevel().registryAccess());
        h.assertValueEqual(WorkbenchRules.required(table), 256F, "The vibranium bench can be manufactured at adamantium");
        h.assertValueEqual(WorkbenchRules.capacity(MiteWorkbenches.TABLES.get("vibranium").get().defaultBlockState()), 512F,
                "The completed workbench supplies full vibranium capacity");
        assertBench(h, table, "adamantium", true);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyNativeVibraniumEquipmentRecipeUsesTimedQualityAndItsOwnBench(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.experienceLevel = 200;
        CraftingMenu menu = new CraftingMenu(81, player.getInventory());
        CraftingContainer grid = (CraftingContainer)menu.getSlot(1).container;
        for (String family : GEAR) {
            String path = "vibranium_" + family;
            var holder = recipe(h, path);
            CraftingInput input = sample(holder.value());
            h.assertTrue(holder.value().matches(input, h.getLevel()), "Real inputs match the loaded native recipe: " + path);
            ItemStack raw = holder.value().assemble(input, h.getLevel().registryAccess());
            h.assertTrue(raw.is(item(path)), "The loaded recipe creates the registered equipment: " + path);
            h.assertTrue(EquipmentDurability.isManaged(raw), "The result is managed quality equipment: " + path);
            h.assertTrue(CraftingQuality.lowestDifficulty(raw) > 0F, "Quality has a positive manufacturing difficulty: " + path);
            h.assertValueEqual(WorkbenchRules.required(raw), 512F, "Native vibranium equipment requires its own bench: " + path);
            assertBench(h, raw, "adamantium", false);
            assertBench(h, raw, "vibranium", true);
            grid.clearContent();
            for (int slot = 0; slot < input.size(); slot++) grid.setItem(slot, input.getItem(slot).copy());
            var resolved = CraftingQuality.resolve(player, menu, grid, holder, raw);
            h.assertTrue(resolved != null && resolved.rule() != null && resolved.quality() != null && resolved.range() != null,
                    "Actual menu preview resolves timing and selectable quality: " + path);
            boolean upgrade = holder.value() instanceof VibraniumUpgradeRecipe;
            h.assertValueEqual(resolved.fixed(), upgrade, "Upgrades preserve their source quality while new manufacture offers selection: " + path);
            h.assertFalse(resolved.repair(), "A material upgrade does not enter the repair recipe path: " + path);
            h.assertValueEqual(resolved.rule().requiredCapacity(), 512F, "Timed work cannot bypass the physical material gate: " + path);
        }
        CraftingQuality.clear(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allTwelveNativeUpgradesPreservePersonalComponentsQualityAndWearFraction(GameTestHelper h) {
        int count = 0;
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        CraftingMenu menu = new CraftingMenu(82, player.getInventory());
        CraftingContainer grid = (CraftingContainer)menu.getSlot(1).container;
        for (String family : GEAR) {
            var holder = recipe(h, "vibranium_" + family);
            if (!(holder.value() instanceof VibraniumUpgradeRecipe)) continue;
            count++;
            CraftingInput input = sample(holder.value());
            ItemStack source = VibraniumUpgradeRecipe.source(input);
            h.assertFalse(source.isEmpty(), "The source upgrade has exactly one real equipment ingredient: " + family);
            EquipmentDurability.setQuality(source, Quality.FINE);
            source.setDamageValue(source.getMaxDamage() / 3);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("Existing personal " + family));
            source.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 2);
            ItemStack output = holder.value().assemble(input, h.getLevel().registryAccess());
            h.assertValueEqual(EquipmentDurability.quality(output), Quality.FINE, "The upgraded material preserves the old quality: " + family);
            h.assertValueEqual(output.get(DataComponents.CUSTOM_NAME), source.get(DataComponents.CUSTOM_NAME), "The upgrade retains its personal name: " + family);
            h.assertValueEqual(output.get(DataComponents.ENCHANTMENTS), source.get(DataComponents.ENCHANTMENTS), "The upgrade retains its real enchantment component: " + family);
            double oldWear = (double)source.getDamageValue() / source.getMaxDamage();
            double newWear = (double)output.getDamageValue() / output.getMaxDamage();
            h.assertTrue(Math.abs(oldWear - newWear) <= 1D / output.getMaxDamage(), "The upgrade preserves the wear fraction within one point: " + family);
            h.assertValueEqual(output.getEnchantmentValue(), 120, "Copied personal data retain the new material's enchantability: " + family);
            h.assertFalse(output.canBeHurtBy(h.getLevel().damageSources().lava()), "Copied source components retain vibranium lava immunity: " + family);
            grid.clearContent();
            for (int slot = 0; slot < input.size(); slot++) grid.setItem(slot, input.getItem(slot).copy());
            var resolved = CraftingQuality.resolve(player, menu, grid, holder, output);
            h.assertTrue(resolved != null && resolved.fixed() && resolved.exempt() && !resolved.repair(), "Actual upgrade preview locks source quality and charges no new quality fee: " + family);
            h.assertValueEqual(resolved.quality(), Quality.FINE, "A zero-XP upgrade preview cannot downgrade the source quality: " + family);
            h.assertValueEqual(resolved.cost(), 0, "Material upgrades do not charge the source quality twice: " + family);
        }
        h.assertValueEqual(count, 12, "All eleven source metal equipment upgrades and the reinforced bow use component-preserving recipes");
        CraftingQuality.clear(player);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vibraniumNuggetAndStorageConversionsKeepAllNineComponentsAndSerialize(GameTestHelper h) {
        Item ingot = MiteMaterials.VIBRANIUM_INGOT.get(), nugget = MiteMaterials.VIBRANIUM_NUGGET.get();
        assertBatch(h, "vibranium_ingot_from_nuggets", filled(nugget, 9), ingot, 1);
        assertBatch(h, "vibranium_nuggets_from_ingot", CraftingInput.of(1, 1, List.of(new ItemStack(ingot))), nugget, 9);
        assertBatch(h, "vibranium_block", filled(ingot, 9), item("vibranium_block"), 1);
        CraftingInput block = CraftingInput.of(1, 1, List.of(new ItemStack(item("vibranium_block"))));
        assertBatch(h, "vibranium_ingots_from_block", block, ingot, 9);
        var reverse = recipe(h, "vibranium_ingots_from_block");
        h.assertTrue(reverse.value() instanceof BulkMaterialRecipe, "Nine ingots use the existing legal bulk-output recipe");
        ItemStack nine = reverse.value().assemble(block, h.getLevel().registryAccess());
        h.assertTrue(BulkMaterialRecipe.isValidResult(nine), "The nine-ingot exception explicitly recognizes vibranium");
        var ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var encoded = Recipe.CODEC.encodeStart(ops, reverse.value()).getOrThrow();
        var decoded = Recipe.CODEC.parse(ops, encoded).getOrThrow();
        h.assertTrue(decoded.getResultItem(h.getLevel().registryAccess()).is(ingot), "The storage recipe survives actual registry-aware JSON encoding");
        h.assertValueEqual(new ItemStack(ingot).getMaxStackSize(), 8, "Bulk output does not raise ordinary eight-ingot stack limits");
        h.assertValueEqual(new ItemStack(nugget).getMaxStackSize(), 64, "Vibranium nuggets retain ordinary nugget stack limits");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theVibraniumAnvilCostsThirtyOneIngotsAndRetainsItsExactPortableWear(GameTestHelper h) {
        var holder = recipe(h, "vibranium_anvil");
        CraftingInput input = sample(holder.value());
        int blocks = 0, ingots = 0;
        for (ItemStack ingredient : input.items()) {
            if (ingredient.is(item("vibranium_block"))) blocks++;
            if (ingredient.is(MiteMaterials.VIBRANIUM_INGOT.get())) ingots++;
        }
        h.assertTrue(blocks == 3 && ingots == 4 && blocks * 9 + ingots == 31, "The anvil consumes three nine-ingot blocks plus four ingots");
        h.assertTrue(holder.value().matches(input, h.getLevel()), "The loaded anvil recipe accepts its complete thirty-one-ingot cost");
        ItemStack anvil = holder.value().assemble(input, h.getLevel().registryAccess());
        h.assertValueEqual(WorkbenchRules.required(anvil), 512F, "Making the anvil requires a vibranium workbench");
        var rule = CraftingQuality.rule(holder, anvil, input);
        h.assertTrue(rule != null && rule.difficulty() == 31F * 51200F, "Anvil manufacture times the complete material batch");
        BlockPos position = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(position, MiteFunctionalBlocks.ANVILS.get("vibranium").get().defaultBlockState());
        h.assertTrue(h.getLevel().getBlockEntity(position) instanceof MaterialAnvilBlockEntity, "The vibranium anvil has its registered persistent block entity");
        var entity = (MaterialAnvilBlockEntity)h.getLevel().getBlockEntity(position);
        entity.addDamage(12345);
        var drops = Block.getDrops(h.getLevel().getBlockState(position), h.getLevel(), position, entity,
                h.makeMockPlayer(GameType.SURVIVAL), new ItemStack(MiteTools.get("vibranium_pickaxe")));
        h.assertValueEqual(drops.size(), 1, "A harvested vibranium anvil produces one portable stack");
        ItemStack dropped = drops.getFirst();
        h.assertTrue(dropped.is(item("vibranium_anvil")), "Harvest keeps the vibranium anvil identity");
        h.assertValueEqual(dropped.getOrDefault(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), 0), 12345, "Loot copies the exact anvil wear component");
        ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), dropped.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(ItemStack.matches(restored, dropped), "Portable anvil identity and wear survive item serialization");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeVibraniumEquipmentRepairsOnlyWithItsNuggetsOnASufficientAnvil(GameTestHelper h) {
        var sufficient = MiteFunctionalBlocks.ANVILS.get("vibranium").get().defaultBlockState();
        var inferior = MiteFunctionalBlocks.ANVILS.get("adamantium").get().defaultBlockState();
        for (String family : GEAR) {
            ItemStack original = new ItemStack(item("vibranium_" + family));
            EquipmentDurability.setQuality(original, Quality.POOR);
            original.setDamageValue(original.getMaxDamage() / 2);
            ItemStack nuggets = new ItemStack(MiteMaterials.VIBRANIUM_NUGGET.get(), 64);
            var repair = MaterialAnvilRules.previewRepair(sufficient, original, nuggets);
            h.assertTrue(repair != null && repair.allowed() && repair.repair(), "Vibranium nuggets repair actual native equipment: " + family);
            h.assertTrue(repair.output().getDamageValue() < original.getDamageValue(), "Repair restores actual durability: " + family);
            h.assertValueEqual(EquipmentDurability.quality(repair.output()), Quality.POOR, "Repair retains the original quality: " + family);
            h.assertValueEqual(repair.output().getMaxDamage(), original.getMaxDamage(), "Repair keeps one quality multiplier: " + family);
            var weak = MaterialAnvilRules.previewRepair(inferior, original, nuggets);
            h.assertTrue(weak != null && !weak.allowed(), "An adamantium anvil cannot repair higher-tier vibranium: " + family);
            var wrong = MaterialAnvilRules.previewRepair(sufficient, original, new ItemStack(MiteMaterials.ADAMANTIUM_NUGGET.get(), 64));
            h.assertTrue(wrong != null && !wrong.allowed(), "A preceding-tier nugget cannot repair vibranium: " + family);
            var ingot = MaterialAnvilRules.previewRepair(sufficient, original, new ItemStack(MiteMaterials.VIBRANIUM_INGOT.get()));
            h.assertTrue(ingot != null && !ingot.allowed(), "The source repair system consumes nuggets rather than ingots: " + family);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allNewNativeVibraniumIdentitiesRetainFireAndLavaImmunity(GameTestHelper h) {
        List<String> paths = new ArrayList<>(GEAR.stream().map(family -> "vibranium_" + family).toList());
        paths.addAll(List.of("vibranium_ingot", "vibranium_nugget", "vibranium_block", "vibranium_crafting_table", "vibranium_anvil"));
        for (String path : paths) {
            ItemStack stack = new ItemStack(item(path));
            h.assertFalse(stack.isEmpty(), "The requested native vibranium identity is actually registered: " + path);
            h.assertFalse(stack.canBeHurtBy(h.getLevel().damageSources().lava()), "The source material resists lava after being dropped: " + path);
            h.assertFalse(stack.canBeHurtBy(h.getLevel().damageSources().inFire()), "The source material resists fire after being dropped: " + path);
        }
        h.succeed();
    }

    private static Item item(String path) { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mite", path)); }
    private static RecipeHolder<CraftingRecipe> recipe(GameTestHelper h, String path) {
        return h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(holder -> holder.id().equals(ResourceLocation.fromNamespaceAndPath("mite", path))).findFirst().orElseThrow();
    }
    private static CraftingInput sample(CraftingRecipe recipe) {
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : recipe.getIngredients().size();
        int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 1;
        List<ItemStack> ingredients = recipe.getIngredients().stream().map(ingredient -> ingredient.isEmpty()
                ? ItemStack.EMPTY : ingredient.getItems()[0].copy()).toList();
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            items.set(y * 3 + x, ingredients.get(y * width + x));
        return CraftingInput.of(3, 3, items);
    }
    private static CraftingInput filled(Item item, int amount) {
        return CraftingInput.of(3, 3, Collections.nCopies(amount, item).stream().map(ItemStack::new).toList());
    }
    private static void assertBatch(GameTestHelper h, String path, CraftingInput input, Item output, int count) {
        var holder = recipe(h, path);
        h.assertTrue(holder.value().matches(input, h.getLevel()), "The complete registered conversion accepts its actual inputs: " + path);
        ItemStack result = holder.value().assemble(input, h.getLevel().registryAccess());
        h.assertTrue(result.is(output) && result.getCount() == count, "The conversion preserves its whole material batch: " + path);
        h.assertTrue(CraftingQuality.rule(holder, result, input) != null, "Material conversion has a manufacturing-time rule: " + path);
    }
    private static void assertBench(GameTestHelper h, ItemStack result, String material, boolean allowed) {
        BlockPos bench = h.absolutePos(new BlockPos(2, 3, 2));
        h.getLevel().setBlockAndUpdate(bench, MiteWorkbenches.TABLES.get(material).get().defaultBlockState());
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var menu = new CraftingMenu(82, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), bench));
        h.assertValueEqual(WorkbenchRules.allows(menu, result), allowed, "The physical " + material + " player workbench correctly gates " + result);
    }
}
