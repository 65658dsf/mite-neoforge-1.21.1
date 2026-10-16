package io.github.ningmeng123.mite.campfire;

import io.github.ningmeng123.mite.furnace.FurnaceThermalController;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** One server state machine accounts for both idle time and cooked-meat quotas. @author NingMeng123 */
public final class CampfireController {
    public static final TagKey<Item> MEATS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("mite", "campfire_meats"));
    private CampfireController() {}

    public static int fuelPoints(CampfireBlockEntity fire) { return ((CampfireFuelAccess) fire).mite$fuelPoints(); }

    public static boolean isMeat(ItemStack stack) {
        return !stack.isEmpty() && stack.is(MEATS) && FurnaceThermalController.requiredHeat(stack) <= 1;
    }

    public static boolean isWoodFuel(ItemStack stack) {
        if (stack.isEmpty() || stack.is(ItemTags.NON_FLAMMABLE_WOOD)) return false;
        if (stack.is(Items.STICK) || stack.is(Items.BOWL) || stack.is(ItemTags.SAPLINGS)
                || stack.is(ItemTags.BOATS) || stack.getItem() instanceof TieredItem tool && tool.getTier() == Tiers.WOOD)
            return stack.getBurnTime(null) > 0;
        return stack.getItem() instanceof BlockItem block && !(block.getBlock() instanceof CampfireBlock)
                && stack.getBurnTime(null) > 0 && (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)
                || stack.is(ItemTags.WOODEN_SLABS) || stack.is(ItemTags.WOODEN_STAIRS)
                || stack.is(ItemTags.WOODEN_FENCES) || stack.is(ItemTags.FENCE_GATES)
                || stack.is(ItemTags.WOODEN_DOORS) || stack.is(ItemTags.WOODEN_TRAPDOORS)
                || stack.is(ItemTags.WOODEN_BUTTONS) || stack.is(ItemTags.WOODEN_PRESSURE_PLATES)
                || block.getBlock().defaultBlockState().ignitedByLava());
    }

    public static boolean wet(Level level, BlockPos pos, BlockState state) {
        return state.getValue(CampfireBlock.WATERLOGGED) || level.isRainingAt(pos.above());
    }

    public static boolean isBurning(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)
                && !wet(level, pos, state) && level.getBlockEntity(pos) instanceof CampfireFuelAccess fuel
                && fuel.mite$fuelPoints() > 0;
    }

    /** Adding reserve never resets the lifetime or meat quota of the point already burning. */
    public static int refuel(CampfireBlockEntity fire, int points, boolean ignite) {
        Level level = fire.getLevel();
        if (level == null || level.isClientSide || points < 0) return 0;
        BlockPos pos = fire.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CampfireBlock) || ignite && wet(level, pos, state)) return 0;
        CampfireFuelAccess fuel = (CampfireFuelAccess) fire;
        int added = Math.min(points, CampfireRules.MAX_FUEL - fuel.mite$fuelPoints());
        if (added > 0) {
            if (fuel.mite$fuelPoints() == 0) {
                fuel.mite$setFuelTicks(CampfireRules.FUEL_TICKS);
                fuel.mite$setCookedMeat(0);
            }
            fuel.mite$setFuelPoints(fuel.mite$fuelPoints() + added);
        }
        if (ignite && fuel.mite$fuelPoints() > 0 && !state.getValue(CampfireBlock.LIT)) {
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1F, 1F);
            level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
        }
        if (added > 0 || ignite) updated(fire);
        return added;
    }

    public static Optional<RecipeHolder<CampfireCookingRecipe>> recipe(CampfireBlockEntity fire, ItemStack input) {
        Level level = fire.getLevel();
        if (level == null || !isMeat(input) || !isBurning(level, fire.getBlockPos())) return Optional.empty();
        if (fire.getItems().subList(0, CampfireRules.COOKING_SLOTS).stream().noneMatch(ItemStack::isEmpty))
            return Optional.empty();
        return cookingRecipe(level, input);
    }

    private static Optional<RecipeHolder<CampfireCookingRecipe>> cookingRecipe(Level level, ItemStack input) {
        return level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(input), level);
    }

    public static boolean placeFood(CampfireBlockEntity fire, LivingEntity entity, ItemStack input) {
        if (recipe(fire, input).isEmpty()) return false;
        CampfireFuelAccess fuel = (CampfireFuelAccess) fire;
        for (int slot = 0; slot < CampfireRules.COOKING_SLOTS; slot++) {
            if (!fire.getItems().get(slot).isEmpty()) continue;
            fire.getItems().set(slot, input.consumeAndReturn(1, entity));
            fuel.mite$cookingProgress()[slot] = 0;
            fuel.mite$cookingTime()[slot] = CampfireRules.COOKING_TICKS;
            fire.getLevel().gameEvent(GameEvent.BLOCK_CHANGE, fire.getBlockPos(), GameEvent.Context.of(entity, fire.getBlockState()));
            updated(fire);
            return true;
        }
        return false;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CampfireBlockEntity fire) {
        if (level.isClientSide) return;
        CampfireFuelAccess fuel = (CampfireFuelAccess) fire;
        if (!state.getValue(CampfireBlock.LIT) || wet(level, pos, state) || fuel.mite$fuelPoints() == 0) {
            extinguish(fire);
            return;
        }
        fuel.mite$setFuelTicks(fuel.mite$fuelTicks() - 1);
        for (int slot = 0; slot < fire.getItems().size(); slot++) {
            ItemStack raw = fire.getItems().get(slot);
            if (raw.isEmpty()) continue;
            var recipe = isMeat(raw) && slot < CampfireRules.COOKING_SLOTS ? cookingRecipe(level, raw) : Optional.<RecipeHolder<CampfireCookingRecipe>>empty();
            if (recipe.isEmpty() || raw.getCount() != 1) {
                ejectSlot(fire, slot);
                continue;
            }
            fuel.mite$cookingTime()[slot] = CampfireRules.COOKING_TICKS;
            if (++fuel.mite$cookingProgress()[slot] < CampfireRules.COOKING_TICKS) continue;
            ItemStack cooked = recipe.get().value().assemble(new SingleRecipeInput(raw), level.registryAccess());
            if (cooked.isEmpty() || !cooked.isItemEnabled(level.enabledFeatures())) continue;
            fire.getItems().set(slot, ItemStack.EMPTY);
            fuel.mite$cookingProgress()[slot] = 0;
            fuel.mite$cookingTime()[slot] = 0;
            drop(fire, cooked);
            fuel.mite$setCookedMeat(fuel.mite$cookedMeat() + 1);
            if (fuel.mite$cookedMeat() == CampfireRules.MEAT_PER_FUEL) {
                consumePoint(fire);
                if (fuel.mite$fuelPoints() == 0) return;
            }
        }
        if (fuel.mite$fuelTicks() <= 0) {
            consumePoint(fire);
            if (fuel.mite$fuelPoints() == 0) return;
        }
        fire.setChanged();
        if (fuel.mite$fuelTicks() % 20 == 0) updated(fire);
        if (level instanceof ServerLevel server) CampfireEnvironment.tick(server, pos);
    }

    private static void consumePoint(CampfireBlockEntity fire) {
        CampfireFuelAccess fuel = (CampfireFuelAccess) fire;
        fuel.mite$setFuelPoints(Math.max(0, fuel.mite$fuelPoints() - 1));
        fuel.mite$setFuelTicks(fuel.mite$fuelPoints() > 0 ? CampfireRules.FUEL_TICKS : 0);
        fuel.mite$setCookedMeat(0);
        if (fuel.mite$fuelPoints() == 0) extinguish(fire);
        else updated(fire);
    }

    public static void extinguish(CampfireBlockEntity fire) {
        Level level = fire.getLevel();
        if (level == null || level.isClientSide) return;
        BlockPos pos = fire.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean changed = false;
        for (int slot = 0; slot < fire.getItems().size(); slot++) {
            changed |= !fire.getItems().get(slot).isEmpty();
            ejectSlot(fire, slot);
        }
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1F, 1F);
            level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
            changed = true;
        }
        if (level instanceof ServerLevel server) CampfireEnvironment.forget(server, pos);
        if (changed) updated(fire);
    }

    private static void ejectSlot(CampfireBlockEntity fire, int slot) {
        CampfireFuelAccess fuel = (CampfireFuelAccess) fire;
        ItemStack raw = fire.getItems().get(slot);
        fire.getItems().set(slot, ItemStack.EMPTY);
        fuel.mite$cookingProgress()[slot] = 0;
        fuel.mite$cookingTime()[slot] = 0;
        if (!raw.isEmpty()) drop(fire, raw);
    }

    private static void drop(CampfireBlockEntity fire, ItemStack stack) {
        BlockPos pos = fire.getBlockPos();
        Containers.dropItemStack(fire.getLevel(), pos.getX() + .5, pos.getY() + .75, pos.getZ() + .5, stack);
        updated(fire);
    }

    public static void updated(CampfireBlockEntity fire) {
        fire.setChanged();
        if (fire.getLevel() != null)
            fire.getLevel().sendBlockUpdated(fire.getBlockPos(), fire.getBlockState(), fire.getBlockState(), Block.UPDATE_CLIENTS);
    }
}
