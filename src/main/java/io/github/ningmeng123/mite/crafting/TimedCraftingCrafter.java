package io.github.ningmeng123.mite.crafting;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One redstone request processes one timed batch; pending work is deliberately not persisted. @author NingMeng123 */
public final class TimedCraftingCrafter {
    private static final Map<CrafterBlockEntity, Job> JOBS = new WeakHashMap<>();
    private static final class Job {
        final ResourceLocation recipe;
        final List<ItemStack> inputs;
        final int disabled;
        long lastTick;
        int progress;
        Job(ResourceLocation recipe, CrafterBlockEntity crafter, long time) {
            this.recipe = recipe;
            inputs = TimedCraftingController.snapshot(crafter);
            disabled = disabledMask(crafter);
            lastTick = time;
        }
    }
    private TimedCraftingCrafter() {}

    public static boolean delay(BlockState state, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)) return false;
        Job old = JOBS.get(crafter);
        var recipe = CrafterBlock.getPotentialResults(level, crafter.asCraftInput());
        if (old != null && (recipe.isEmpty() || !old.recipe.equals(recipe.get().id())
                || !TimedCraftingController.matches(old.inputs, crafter) || old.disabled != disabledMask(crafter))) {
            JOBS.remove(crafter);
            return true;
        }
        if (recipe.isEmpty()) return false;
        ItemStack result = recipe.get().value().assemble(crafter.asCraftInput(), level.registryAccess());
        if (!CoinCrafting.automatedAllowed(crafter.asCraftInput(), result)) {
            JOBS.remove(crafter);
            return true;
        }
        TimedCraftingRecipes.Rule rule = CraftingQuality.rule(recipe.get(), result, crafter.asCraftInput());
        if (rule == null) return false;
        // No operator/XP account: automation only manufactures default quality, never selects a paid quality.
        if (!(recipe.get().value() instanceof net.minecraft.world.item.crafting.ArmorDyeRecipe)
                && !CraftingQuality.shieldDecoration(recipe.get(), result))
            io.github.ningmeng123.mite.item.EquipmentDurability.setQuality(result,
                    io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality.AVERAGE);
        // The Crafter itself is a basic flint-speed device for recipes with no bench requirement.
        float capacity = rule.requiredCapacity() <= 0F ? 1F : 0F;
        for (Direction direction : Direction.values()) capacity = Math.max(capacity,
                WorkbenchRules.capacity(level.getBlockState(pos.relative(direction))));
        if (result.isEmpty() || capacity <= 0F || capacity < rule.requiredCapacity()
                || !WorkbenchRules.allowsCrafter(level, pos, result)) {
            JOBS.remove(crafter);
            return true;
        }
        for (int i = 0; i < crafter.getContainerSize(); i++) {
            if (crafter.getItem(i).isDamaged() && !CraftingQuality.shieldDecoration(recipe.get(), result)) {
                JOBS.remove(crafter); return true;
            }
        }
        if (old == null) {
            old = new Job(recipe.get().id(), crafter, level.getGameTime());
            JOBS.put(crafter, old);
        } else if (old.lastTick != level.getGameTime()) {
            old.lastTick = level.getGameTime();
            old.progress++;
        }
        int period = TimedCraftingRules.period(io.github.ningmeng123.mite.rules.EquipmentQualityRules.adjustedDifficulty(
                rule.difficulty(), io.github.ningmeng123.mite.item.EquipmentDurability.isManaged(result)
                        ? io.github.ningmeng123.mite.item.EquipmentDurability.quality(result) : null), 0,
                TimedCraftingRules.benchModifier(capacity, rule.requiredBench() != null), false);
        if (old.progress >= period) {
            JOBS.remove(crafter);
            return false; // Exactly one vanilla dispense consumes this batch, including all recipe remainders.
        }
        level.scheduleTick(pos, state.getBlock(), 1);
        return true;
    }

    private static int disabledMask(CrafterBlockEntity crafter) {
        int mask = 0;
        for (int i = 0; i < 9; i++) if (crafter.isSlotDisabled(i)) mask |= 1 << i;
        return mask;
    }
}
