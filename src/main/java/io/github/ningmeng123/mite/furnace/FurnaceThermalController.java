package io.github.ningmeng123.mite.furnace;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.rules.R196FurnaceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

/** asg's state machine, adapted to modern recipes and validated on every insertion and ignition. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class FurnaceThermalController {
    private FurnaceThermalController() {}
    public record CookingBatch(ItemStack result, int consumed, RecipeHolder<? extends AbstractCookingRecipe> recipe) {}
    public static R196FurnaceRules.Profile profile(BlockState state) {
        return R196FurnaceRules.profile(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }
    public static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    public static R196FurnaceRules.Fuel fuel(ItemStack stack) {
        if (stack.isEmpty()) return new R196FurnaceRules.Fuel(0, 0);
        var source = mappedFuel(stack);
        if (source != null) return source;
        int ticks = stack.getBurnTime(null);
        return new R196FurnaceRules.Fuel(ticks, ticks > 0 ? 1 : 0);
    }
    private static R196FurnaceRules.Fuel mappedFuel(ItemStack stack) {
        // User adaptation: a wooden handle does not make non-wood equipment combustible.
        if (EquipmentRecycling.isNonWoodEquipment(stack)) return new R196FurnaceRules.Fuel(0, 0);
        var source = R196FurnaceRules.fuel(id(stack));
        if (source != null) return source;
        // Modern wood species inherit the corresponding source shape. Nonflammable Nether wood stays nonflammable.
        if (stack.is(ItemTags.NON_FLAMMABLE_WOOD)) return new R196FurnaceRules.Fuel(0, 0);
        int ticks = stack.is(ItemTags.LOGS) ? 1600 : stack.is(ItemTags.PLANKS) || stack.is(ItemTags.WOODEN_BUTTONS)
                || stack.is(ItemTags.WOODEN_DOORS) ? 400 : stack.is(ItemTags.WOODEN_SLABS) || stack.is(ItemTags.SAPLINGS) ? 200
                : stack.is(ItemTags.BOATS) ? 1200 : stack.is(ItemTags.WOODEN_STAIRS) || stack.is(ItemTags.WOODEN_FENCES)
                || stack.is(ItemTags.WOODEN_TRAPDOORS) ? 400 : 0;
        return ticks > 0 ? new R196FurnaceRules.Fuel(ticks, 1) : null;
    }
    @SubscribeEvent public static void fuelTime(FurnaceFuelBurnTimeEvent event) {
        var fuel = mappedFuel(event.getItemStack());
        if (fuel != null) event.setBurnTime(fuel.ticks());
    }
    public static boolean isLarge(ItemStack stack) {
        Boolean source = R196FurnaceRules.large(id(stack));
        if (source != null) return source;
        Item item = stack.getItem();
        if (item instanceof BoatItem || item instanceof BedItem) return true;
        if (!(item instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        return !(block instanceof TorchBlock || block instanceof SaplingBlock || block instanceof FlowerBlock
                || block instanceof TallGrassBlock || block instanceof CropBlock || block instanceof BaseRailBlock
                || block instanceof WaterlilyBlock || block instanceof VineBlock || block instanceof DeadBushBlock
                || block instanceof ButtonBlock);
    }
    public static int requiredHeat(ItemStack input) {
        // Equipment recycling uses the same heat gate as vanilla iron/gold recycling.
        if (EquipmentRecycling.material(input) != null) return 2;
        return R196FurnaceRules.requiredHeat(id(input));
    }
    public static boolean canFuel(R196FurnaceRules.Profile profile, ItemStack stack) {
        if (profile == null) return stack.getBurnTime(null) > 0;
        return R196FurnaceRules.fuelAccepted(fuel(stack).heat(), profile.maxHeat(), isLarge(stack), profile.acceptsLarge());
    }
    public static boolean canInsertFuel(R196FurnaceRules.Profile profile, ItemStack stack, ItemStack input) {
        // Wet sponges are modern content: their empty container is not combustible fuel.
        return canFuel(profile, stack) || input.is(Items.WET_SPONGE) && emptyBucket(stack);
    }
    public static boolean canInput(Level level, RecipeType<? extends AbstractCookingRecipe> type,
            R196FurnaceRules.Profile profile, ItemStack input) {
        if (input.isEmpty() || profile == null || !profile.acceptsLarge() && isLarge(input)) return false;
        if (type == RecipeType.SMELTING && (input.is(Items.SAND) || input.is(Items.RED_SAND)
                || input.is(Items.SANDSTONE) || input.is(Items.RED_SANDSTONE))) return true;
        return level.getRecipeManager().getRecipeFor((RecipeType<AbstractCookingRecipe>) type, new SingleRecipeInput(input), level).isPresent();
    }
    public static boolean emptyBucket(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.getItem() instanceof MiteBucketItem bucket && bucket.contents() == MiteBucketItem.Contents.EMPTY;
    }
    public static boolean frontBlocked(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(AbstractFurnaceBlock.FACING);
        BlockPos front = pos.relative(facing);
        BlockState neighbor = level.getBlockState(front);
        return neighbor.getFluidState().is(FluidTags.WATER) || neighbor.isFaceSturdy(level, front, facing.getOpposite());
    }
    public static boolean frontAccessible(Level level, BlockPos pos, BlockState state) {
        // abw.isAirOrPassableBlock(include_liquid=true): water can be opened through, but extinguishes the fire.
        return !level.getBlockState(pos.relative(state.getValue(AbstractFurnaceBlock.FACING))).isSolid();
    }
    public static boolean tick(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace) {
        var profile = profile(state);
        if (profile == null) return false;
        FurnaceThermalAccess access = (FurnaceThermalAccess) furnace;
        if (access.mite$getBurn() != 1 && frontBlocked(level, pos, state)) {
            if (access.mite$getBurn() > 0 && level instanceof net.minecraft.server.level.ServerLevel server
                    && level.getFluidState(pos.relative(state.getValue(AbstractFurnaceBlock.FACING))).is(FluidTags.WATER))
                server.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 4, .2, .2, .2, .02);
            access.mite$setBurn(0); access.mite$setProgress(0); access.mite$setTotal(profile.cycleTicks());
            lit(level, pos, state, false); furnace.setChanged(); return true;
        }
        if (access.mite$getBurn() > 0) access.mite$setBurn(access.mite$getBurn() - 1);
        else access.mite$setHeat(0);
        ItemStack input = furnace.getItem(0), fuel = furnace.getItem(1);
        var recipe = input.isEmpty() ? null : access.mite$recipe(level, input);
        if (access.mite$getBurn() == 0 && canFuel(profile, fuel)
                && batch(level, furnace, profile, fuel(fuel).heat(), recipe) != null) {
            var rule = fuel(fuel);
            access.mite$setBurn(rule.ticks()); access.mite$setDuration(rule.ticks()); access.mite$setHeat(rule.heat());
            ItemStack remainder = fuel.hasCraftingRemainingItem() ? fuel.getCraftingRemainingItem() : ItemStack.EMPTY;
            fuel.shrink(1);
            if (fuel.isEmpty()) furnace.setItem(1, remainder);
            else if (!remainder.isEmpty()) { // Defensive support for external stacked container fuels.
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, remainder);
            }
        }
        CookingBatch batch = access.mite$getBurn() > 0 ? batch(level, furnace, profile, access.mite$getHeat(), recipe) : null;
        access.mite$setTotal(profile.cycleTicks());
        if (batch == null) access.mite$setProgress(0);
        else {
            access.mite$setProgress(access.mite$getProgress() + 1);
            if (access.mite$getProgress() >= profile.cycleTicks()) {
                access.mite$setProgress(0);
                ItemStack output = furnace.getItem(2);
                if (output.isEmpty()) furnace.setItem(2, batch.result().copy()); else output.grow(batch.result().getCount());
                boolean wetSponge = input.is(Items.WET_SPONGE);
                input.shrink(batch.consumed());
                if (batch.recipe() != null) furnace.setRecipeUsed(batch.recipe());
                // Retain the useful modern wet-sponge interaction with both vanilla and metal buckets.
                if (wetSponge && emptyBucket(furnace.getItem(1))) {
                    ItemStack empty = furnace.getItem(1);
                    ItemStack filled = new ItemStack(empty.getItem() instanceof MiteBucketItem bucket
                            ? io.github.ningmeng123.mite.registry.MiteFoods.get(bucket.metal() + "_water_bucket") : Items.WATER_BUCKET);
                    empty.shrink(1);
                    if (empty.isEmpty()) furnace.setItem(1, filled);
                    else net.minecraft.world.Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, filled);
                }
            }
        }
        lit(level, pos, state, access.mite$getBurn() > 0); furnace.setChanged(); return true;
    }
    private static CookingBatch batch(Level level, AbstractFurnaceBlockEntity furnace, R196FurnaceRules.Profile profile,
            int heat, RecipeHolder<? extends AbstractCookingRecipe> recipe) {
        return preview(level, ((FurnaceThermalAccess) furnace).mite$recipeType(), profile,
                furnace.getItem(0), furnace.getItem(2), furnace.getMaxStackSize(), heat, recipe);
    }
    /** Read-only processing plan shared with recipe viewers; the server still owns inventory changes. */
    public static CookingBatch preview(Level level, RecipeType<? extends AbstractCookingRecipe> type,
            R196FurnaceRules.Profile profile, ItemStack input, ItemStack existing, int maxStackSize,
            int heat, RecipeHolder<? extends AbstractCookingRecipe> recipe) {
        if (input.isEmpty() || heat <= 0 || heat > profile.maxHeat() || heat < requiredHeat(input)
                || !profile.acceptsLarge() && isLarge(input)) return null;
        boolean normal = type == RecipeType.SMELTING;
        int consumed = 1;
        ItemStack result;
        if (normal && (input.is(Items.SAND) || input.is(Items.RED_SAND))) {
            if (input.getCount() < 4) return null;
            consumed = 4;
            result = new ItemStack(heat == 1 ? input.is(Items.RED_SAND) ? Items.RED_SANDSTONE : Items.SANDSTONE : Items.GLASS);
        } else if (normal && (input.is(Items.SANDSTONE) || input.is(Items.RED_SANDSTONE))) result = new ItemStack(Items.GLASS);
        else {
            if (recipe == null) return null;
            result = recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess());
        }
        if (result.isEmpty() || !existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, result)) return null;
        int room = Math.min(maxStackSize, result.getMaxStackSize()) - existing.getCount();
        if (normal && input.is(Items.CLAY_BALL) && result.is(Items.BRICK)) {
            consumed = Math.min(4, Math.min(input.getCount(), room));
            if (consumed <= 0) return null;
            result.setCount(consumed);
        }
        if (room < result.getCount()) return null;
        return new CookingBatch(result, consumed, recipe);
    }
    private static void lit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.getValue(AbstractFurnaceBlock.LIT) != lit) level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, lit), 3);
    }
}
