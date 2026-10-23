package io.github.ningmeng123.mite.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.TimedCraftingRecipes;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Audits the loaded recipe manager, including data-pack overrides and remaining instant recipes. @author NingMeng123 */
@GameTestHolder("mite_crafting_audit")
@PrefixGameTestTemplate(false)
public final class CommonCraftingCoverageGameTests {
    private CommonCraftingCoverageGameTests() {}

    @GameTest(template = "empty")
    public static void loadedRecipesUseSupplementalRulesAndDiscloseRemainingInstantRecipes(GameTestHelper helper) throws Exception {
        var report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        var timed = new JsonArray();
        var instant = new JsonArray();
        var dynamic = new JsonArray();
        var manager = helper.getLevel().getRecipeManager();
        for (var holder : manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .sorted(Comparator.comparing(recipe -> recipe.id().toString())).toList()) {
            var row = new JsonObject();
            row.addProperty("recipe", holder.id().toString());
            row.addProperty("serializer", BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer()).toString());
            var output = holder.value().getResultItem(helper.getLevel().registryAccess());
            if (output.isEmpty()) {
                // Special recipes need real inputs; separate integration tests exercise their operation-specific rules.
                dynamic.add(row);
                continue;
            }
            row.addProperty("output", BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
            row.addProperty("count", output.getCount());
            var rule = CraftingQuality.rule(holder, output, CraftingInput.EMPTY);
            if (rule == null) instant.add(row);
            else {
                row.addProperty("difficulty", rule.difficulty());
                row.addProperty("required_bench", rule.requiredBench());
                timed.add(row);
            }
        }
        var absent = new JsonArray();
        int loadedCommon = 0;
        try (var reader = new InputStreamReader(CommonCraftingCoverageGameTests.class.getResourceAsStream(
                "/mite/timed-crafting-r196-common.json"), StandardCharsets.UTF_8)) {
            for (var entry : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("recipes").entrySet()) {
                var id = ResourceLocation.parse(entry.getKey());
                helper.assertTrue(TimedCraftingRecipes.get(id) != null, "Every supplemental entry reaches the production lookup: " + id);
                if (manager.byKey(id).isPresent()) loadedCommon++;
                else absent.add(id.toString());
            }
        }
        helper.assertTrue(loadedCommon >= 19, "The live manager contains the four reported recipe families including all bed colors");
        report.addProperty("loaded_common_recipe_count", loadedCommon);
        report.addProperty("timed_static_count", timed.size());
        report.addProperty("instant_static_count", instant.size());
        report.addProperty("dynamic_recipe_count", dynamic.size());
        report.add("timed_static", timed);
        report.add("instant_static", instant);
        report.add("dynamic_requires_real_inputs", dynamic);
        report.add("common_ids_absent_from_active_recipe_manager", absent);
        var output = Path.of(System.getProperty("mite.craftingCoverageOutput", "build/verification/common-crafting-coverage.json"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        helper.succeed();
    }
}
