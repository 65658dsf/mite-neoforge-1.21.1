package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.furnace.*;
import io.github.ningmeng123.mite.rules.R196FurnaceRules;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** All native furnace menus synchronize heat and apply identical manual/shift insertion rules. @author NingMeng123 */
@Mixin(AbstractFurnaceMenu.class)
public abstract class FurnaceThermalMenuMixin extends AbstractContainerMenu implements FurnaceThermalMenuAccess {
    @Shadow @Final private Container container;
    @Shadow @Final private RecipeType<? extends AbstractCookingRecipe> recipeType;
    @Shadow @Final protected Level level;
    @Unique private int mite$heat, mite$required;
    @Unique private int mite$maximum = 2;
    @Unique private int mite$large = 1;
    @Unique private int mite$managed = 1;
    protected FurnaceThermalMenuMixin(MenuType<?> type, int id) { super(type, id); }
    public int mite$getHeat() { return container instanceof AbstractFurnaceBlockEntity f ? ((FurnaceThermalAccess)f).mite$getHeat() : mite$heat; }
    public int mite$getMaxHeat() { return container instanceof AbstractFurnaceBlockEntity f ? FurnaceThermalController.profile(f.getBlockState()) == null ? 0
            : FurnaceThermalController.profile(f.getBlockState()).maxHeat() : mite$maximum; }
    public int mite$getRequiredHeat() { return container instanceof AbstractFurnaceBlockEntity ? container.getItem(0).isEmpty() ? 0
            : FurnaceThermalController.requiredHeat(container.getItem(0)) : mite$required; }
    @Unique private R196FurnaceRules.Profile mite$profile() {
        if (container instanceof AbstractFurnaceBlockEntity f) return FurnaceThermalController.profile(f.getBlockState());
        return mite$managed == 0 ? null : new R196FurnaceRules.Profile(mite$maximum, mite$large != 0, 200);
    }
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/inventory/RecipeBookType;ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;Lnet/minecraft/world/inventory/ContainerData;)V", at = @At("TAIL"))
    private void mite$setup(MenuType<?> type, RecipeType<?> recipes, RecipeBookType book, int id, Inventory inventory,
            Container furnace, ContainerData data, CallbackInfo ci) {
        addDataSlot(new ThermalDataSlot(this::mite$getHeat, value -> mite$heat = value));
        addDataSlot(new ThermalDataSlot(this::mite$getMaxHeat, value -> mite$maximum = value));
        addDataSlot(new ThermalDataSlot(this::mite$getRequiredHeat, value -> mite$required = value));
        addDataSlot(new ThermalDataSlot(() -> container instanceof AbstractFurnaceBlockEntity ? mite$profile() != null && mite$profile().acceptsLarge() ? 1 : 0 : mite$large, value -> mite$large = value));
        addDataSlot(new ThermalDataSlot(() -> container instanceof AbstractFurnaceBlockEntity ? mite$profile() == null ? 0 : 1 : mite$managed, value -> mite$managed = value));
        ThermalSlot input = new ThermalSlot(container, 0, 56, 17, stack -> mite$profile() == null
                || FurnaceThermalController.canInput(level, recipeType, mite$profile(), stack));
        ThermalSlot fuel = new ThermalSlot(container, 1, 56, 53, stack -> mite$profile() == null
                ? stack.getBurnTime(recipeType) > 0 || stack.is(net.minecraft.world.item.Items.BUCKET)
                : FurnaceThermalController.canInsertFuel(mite$profile(), stack, container.getItem(0)));
        input.index = 0; fuel.index = 1; slots.set(0, input); slots.set(1, fuel);
    }
    @Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
    private void mite$fuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (mite$profile() != null) cir.setReturnValue(FurnaceThermalController.canInsertFuel(mite$profile(), stack, container.getItem(0)));
    }
    @Inject(method = "canSmelt", at = @At("HEAD"), cancellable = true)
    private void mite$input(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (mite$profile() != null) cir.setReturnValue(FurnaceThermalController.canInput(level, recipeType, mite$profile(), stack));
    }
}
