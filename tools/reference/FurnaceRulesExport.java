/* Guarded R196 furnace bytecode oracle. Developer: NingMeng123. */
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes registry and in-memory furnace recipes only; no client, server or world is created. */
public final class FurnaceRulesExport {
    private static Object row(Object... values) { return RegistryExport.map(values); }
    private static Object stack(ye value) throws Exception { return RegistryExport.stack(value); }

    /** Original canSmelt/smeltItem execute against a reference block and three in-memory slots. */
    private static final class ProbeFurnace extends asg {
        final aok block;
        ProbeFurnace(aok block) { this.block = block; }
        @Override public aok getFurnaceBlock() { return block; }
    }

    @SuppressWarnings("removal")
    public static void main(String[] arguments) throws Exception {
        Path root = Path.of(arguments[0]).toAbsolutePath().normalize();
        RegistryExport.ReadOnlyGuard guard = new RegistryExport.ReadOnlyGuard(
                List.of(root, Path.of(System.getProperty("java.home"))));
        guard.checkPermission(new java.io.FilePermission(root.resolve("tools/reference/FurnaceRulesExport.java").toString(), "read"));
        System.setSecurityManager(guard);
        Class.forName("aqz"); Class.forName("yc"); aaf.a();
        List<Object> items = new ArrayList<>();
        List<Object> fuels = new ArrayList<>();
        for (yc item : yc.g) {
            if (item == null) continue;
            for (Object object : item.getSubItems()) {
                ye value = (ye) object;
                int ticks = item.getBurnTime(value);
                int heat = item.getHeatLevel(value);
                Object record = row("source_item_id", item.cv, "subtype", value.getItemSubtype(),
                        "name", item.d(value), "class", item.getClass().getName(),
                        "ticks", ticks, "heat", heat, "large", we.isLargeItem(item),
                        "container_item_id", item.t() == null ? null : item.t().cv,
                        "xp_reward", item.getExperienceReward(value.getItemSubtype()),
                        "stack_limit", item.getItemStackLimit(value.getItemSubtype(), 0));
                items.add(record);
                if (ticks > 0 || heat > 0) fuels.add(record);
            }
        }
        int[] furnaceIds = {218, 250, 220, 61, 222, 224};
        List<Object> profiles = new ArrayList<>();
        List<Object> batches = new ArrayList<>();
        for (int furnaceId : furnaceIds) {
            aok block = (aok) aqz.s[furnaceId];
            profiles.add(row("source_block_id", furnaceId,
                    "max_heat", block.getMaxHeatLevel(), "accepts_large", block.acceptsLargeItems(),
                    "is_oven", block.isOven(), "hardness", block.getBlockHardness(2),
                    "explosion_resistance", block.getExplosionResistance(null),
                    "lit_light", aqz.w[block.getActiveBlockID()],
                    "min_harvest_level", block.getMinHarvestLevel(2),
                    "stack_limit", yc.g[furnaceId].getItemStackLimit(0, 0),
                    "component_difficulty", block.getCraftingDifficultyAsComponent(0)));
            for (int inputId : new int[]{12, 24, 337, 82, 200, 202, 203, 363}) {
                for (int count : new int[]{1, 3, 4, 5, 16}) {
                    for (int heat = 0; heat <= 4; heat++) {
                        ProbeFurnace furnace = new ProbeFurnace(block);
                        furnace.a(0, new ye(inputId, count, 0));
                        furnace.smeltItem(heat);
                        batches.add(row("furnace_source_id", furnaceId, "input_source_id", inputId,
                                "input_count", count, "heat", heat,
                                "remaining_input", stack(furnace.a(0)), "output", stack(furnace.a(2))));
                    }
                }
            }
        }
        List<Object> slots = new ArrayList<>();
        for (int furnaceId : furnaceIds) for (int fuelId : new int[]{5, 17, 280, 263, 325, 327, 369}) {
            int variants = fuelId == 263 ? 2 : 1;
            for (int subtype = 0; subtype < variants; subtype++) {
                ProbeFurnace furnace = new ProbeFurnace((aok) aqz.s[furnaceId]);
                ye fuel = new ye(fuelId, 1, subtype);
                slots.add(row("furnace_source_id", furnaceId, "fuel_source_id", fuelId,
                        "subtype", subtype, "slot_accepts", new SlotFuel(furnace, 1, 0, 0, furnace).a(fuel),
                        "automation_accepts", furnace.b(1, fuel), "heat", furnace.getItemHeatLevel(fuel)));
            }
        }
        List<Object> melts = new ArrayList<>();
        for (wr bucket : new wr[]{yc.bucketCopperEmpty, yc.bucketSilverEmpty, yc.bucketGoldEmpty,
                yc.bucketIronEmpty, yc.bucketAncientMetalEmpty, yc.bucketMithrilEmpty, yc.bucketAdamantiumEmpty}) {
            melts.add(row("source_item_id", bucket.cv, "material", bucket.getVesselMaterial().name,
                    "chance", bucket.getChanceOfMeltingWhenFilledWithLava()));
        }
        List<Object> recipes = new ArrayList<>();
        for (Object object : RegistryExport.recipes(aaf.a())) {
            Map<?, ?> recipe = (Map<?, ?>) object;
            Map<?, ?> output = (Map<?, ?>) recipe.get("output");
            if (output == null) continue;
            int id = (Integer) output.get("item_id");
            for (int furnaceId : furnaceIds) if (id == furnaceId) recipes.add(object);
        }
        List<Object> capacity = new ArrayList<>();
        for (int inputId : new int[]{12, 337, 73, 21}) {
            ye input = new ye(inputId, 16, 0);
            ye result = aab.a().getSmeltingResult(input, 2);
            int limit = result.e();
            for (int count : new int[]{limit - 4, limit - 3, limit - 1, limit}) {
                ProbeFurnace furnace = new ProbeFurnace((aok) aqz.s[61]);
                furnace.a(0, input.m());
                ye previous = result.m(); previous.b = count;
                furnace.a(2, previous);
                furnace.smeltItem(2);
                capacity.add(row("input_source_id", inputId, "input_count", 16, "heat", 2,
                        "output_count_before", count, "output_limit", limit,
                        "remaining_input", stack(furnace.a(0)), "output", stack(furnace.a(2))));
            }
        }
        Object result = row("author", "NingMeng123", "mode", "guarded_reference_bytecode_furnace",
                "game_main_invoked", false, "world_instantiated", false,
                "professions_suppressed", atv.professions_suppressed,
                "items", items, "fuels", fuels, "profiles", profiles,
                "smelting", RegistryExport.smelting(aab.a()),
                "crafting_recipes", recipes,
                "batch_cases", batches, "fuel_slot_cases", slots, "capacity_cases", capacity,
                "bucket_melting", melts);
        System.out.println("FURNACE_FIXTURE_JSON_BEGIN\n" + RegistryExport.json(result)
                + "\nFURNACE_FIXTURE_JSON_END");
    }
}
