package io.github.ningmeng123.mite.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import io.github.ningmeng123.mite.crafting.TimedCraftingRules;
import io.github.ningmeng123.mite.integration.jei.WorkbenchJeiRecipes;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live stone-family coverage, independently selected from real block shapes and base materials. @author NingMeng123 */
@GameTestHolder("mite_crafting_audit")
@PrefixGameTestTemplate(false)
public final class StoneCraftingCoverageGameTests {
    private static final Set<String> BASES = Set.of(
            "stone", "smooth_stone", "cobblestone", "mossy_cobblestone", "stone_bricks", "mossy_stone_bricks", "chiseled_stone_bricks",
            "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite",
            "deepslate", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "deepslate_tiles", "chiseled_deepslate",
            "tuff", "polished_tuff", "tuff_bricks", "chiseled_tuff", "chiseled_tuff_bricks",
            "sandstone", "cut_sandstone", "smooth_sandstone", "chiseled_sandstone",
            "red_sandstone", "cut_red_sandstone", "smooth_red_sandstone", "chiseled_red_sandstone",
            "bricks", "packed_mud", "mud_bricks", "prismarine", "dark_prismarine", "prismarine_bricks", "sea_lantern",
            "nether_bricks", "red_nether_bricks", "chiseled_nether_bricks", "blackstone", "polished_blackstone",
            "polished_blackstone_bricks", "chiseled_polished_blackstone", "end_stone", "end_stone_bricks",
            "purpur_block", "purpur_pillar", "quartz_block", "quartz_bricks", "quartz_pillar", "chiseled_quartz_block", "smooth_quartz",
            "basalt", "polished_basalt", "smooth_basalt", "dripstone_block", "stone_button", "stone_pressure_plate",
            "polished_blackstone_button", "polished_blackstone_pressure_plate");

    private StoneCraftingCoverageGameTests() {}

    static JsonObject supplemental() throws Exception {
        try (var reader = new InputStreamReader(StoneCraftingCoverageGameTests.class.getResourceAsStream(
                "/mite/timed-crafting-stone-family.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("recipes");
        }
    }

    private static boolean stone(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals("minecraft") || !(stack.getItem() instanceof BlockItem item)) return false;
        if (BASES.contains(id.getPath())) return true;
        if (item.getBlock() instanceof WallBlock) return true;
        // Vanilla 1.21.1 omits bamboo mosaic shapes from WOODEN_* tags;
        // they are still wooden building products, not mineral masonry.
        if (stack.is(Items.BAMBOO_MOSAIC_SLAB) || stack.is(Items.BAMBOO_MOSAIC_STAIRS)) return false;
        // All remaining vanilla nonwood/noncopper building slabs and stairs are masonry.
        if (id.getPath().contains("copper")) return false;
        return item.getBlock() instanceof StairBlock && !stack.is(ItemTags.WOODEN_STAIRS)
                || item.getBlock() instanceof SlabBlock && !stack.is(ItemTags.WOODEN_SLABS);
    }

    @GameTest(template = "empty")
    public static void everyLoadedStoneCraftHasATimedRuleAndMatchingJeiPage(GameTestHelper helper) throws Exception {
        for (var item : new net.minecraft.world.item.Item[]{Items.BAMBOO_MOSAIC_SLAB, Items.BAMBOO_MOSAIC_STAIRS,
                Items.BAMBOO_SLAB, Items.BAMBOO_STAIRS, Items.OAK_SLAB, Items.CHERRY_STAIRS, Items.CUT_COPPER_STAIRS}) {
            helper.assertFalse(stone(new ItemStack(item)), "The independent stone scope excludes wooden mosaics, other wood and copper: "
                    + BuiltInRegistries.ITEM.getKey(item));
        }
        for (var item : new net.minecraft.world.item.Item[]{Items.STONE_SLAB, Items.GRANITE_STAIRS, Items.MUD_BRICK_SLAB,
                Items.TUFF_BRICK_STAIRS, Items.POLISHED_BLACKSTONE_BRICK_SLAB, Items.PRISMARINE_STAIRS,
                Items.PURPUR_SLAB, Items.QUARTZ_STAIRS, Items.RED_SANDSTONE_SLAB, Items.END_STONE_BRICK_WALL}) {
            helper.assertTrue(stone(new ItemStack(item)), "Excluding wooden mosaics preserves each real stone family in the audit: "
                    + BuiltInRegistries.ITEM.getKey(item));
        }
        var pages = WorkbenchJeiRecipes.build(helper.getLevel());
        var manager = helper.getLevel().getRecipeManager();
        var rows = new JsonArray();
        int walls = 0;
        for (var holder : manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .sorted(Comparator.comparing(recipe -> recipe.id().toString())).toList()) {
            var output = holder.value().getResultItem(helper.getLevel().registryAccess());
            if (!stone(output)) continue;
            var rule = CraftingQuality.rule(holder, output, CraftingInput.EMPTY);
            helper.assertTrue(rule != null && rule.difficulty() > 0, "No loaded stone-family craft remains instantaneous: " + holder.id());
            var page = pages.stream().filter(candidate -> candidate.source().id().equals(holder.id())
                    && candidate.workstationId().equals("mite:flint_crafting_table")).findFirst().orElseThrow();
            int ticks = TimedCraftingRules.period(rule.difficulty(), 0, .2F, false);
            helper.assertValueEqual(page.baselineTicks(), ticks, "JEI uses the real whole-batch manufacturing clock: " + holder.id());
            helper.assertValueEqual(page.output().getCount(), output.getCount(), "JEI preserves the actual recipe batch: " + holder.id());
            helper.assertTrue(page.output().is(output.getItem()), "The timed page retains the actual stone output: " + holder.id());
            if (((BlockItem) output.getItem()).getBlock() instanceof WallBlock) walls++;
            var row = new JsonObject();
            row.addProperty("recipe", holder.id().toString());
            row.addProperty("output", BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
            row.addProperty("count", output.getCount());
            row.addProperty("difficulty", rule.difficulty());
            row.addProperty("flint_table_ticks_level_zero", ticks);
            rows.add(row);
        }
        helper.assertTrue(rows.size() >= 150, "Coverage selects all loaded vanilla stone building crafts, including base processing");
        helper.assertTrue(walls >= 25, "Coverage includes every vanilla stone wall family");
        for (var row : supplemental().entrySet()) {
            var id = ResourceLocation.parse(row.getKey());
            helper.assertTrue(manager.byKey(id).isPresent(), "Every new timing entry corresponds to a real loaded recipe: " + id);
            var data = row.getValue().getAsJsonObject();
            helper.assertValueEqual(TimedCraftingRecipes.get(id).difficulty(), data.get("difficulty").getAsFloat(),
                    "Every supplemental stone rule reaches the shared server lookup: " + id);
            var benchmark = TimedCraftingRecipes.get(ResourceLocation.parse(data.get("benchmark").getAsString()));
            helper.assertTrue(benchmark != null, "The disclosed operation benchmark is a real existing rule: " + id);
            float expected = (float) (benchmark.difficulty() * data.get("factor").getAsDouble());
            helper.assertValueEqual(TimedCraftingRecipes.get(id).difficulty(), expected,
                    "Whole-batch difficulty follows the disclosed operation and input-count factor: " + id);
        }
        var report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("crafting_count", rows.size());
        report.addProperty("wall_count", walls);
        report.addProperty("new_supplemental_rules", supplemental().size());
        report.addProperty("loaded_stonecutting_count", manager.getAllRecipesFor(RecipeType.STONECUTTING).stream()
                .filter(holder -> stone(holder.value().getResultItem(helper.getLevel().registryAccess()))).count());
        report.addProperty("stonecutting_policy", "Separate native instant operation; no crafting controller or JEI time is fabricated for it");
        report.add("crafting", rows);
        var path = Path.of(System.getProperty("mite.stoneCraftingAuditOutput", "build/verification/stone-crafting-coverage.json"));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        helper.succeed();
    }
}
