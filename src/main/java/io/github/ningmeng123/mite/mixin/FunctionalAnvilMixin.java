package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.crafting.AnvilExperienceAccess;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.enchantment.R196BookAnvil;
import io.github.ningmeng123.mite.furnace.ThermalDataSlot;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source material repairs use no XP fee and deterministically wear the anvil. @author NingMeng123 */
@Mixin(value = AnvilMenu.class, priority = 1100)
public abstract class FunctionalAnvilMixin extends ItemCombinerMenu implements AnvilExperienceAccess {
    @Shadow public int repairItemCountCost;
    @Shadow private String itemName;
    @Shadow @Final private DataSlot cost;
    @Unique private final DataSlot mite$sourceMode = DataSlot.standalone();
    @Unique private final DataSlot mite$pointMode = DataSlot.standalone();
    @Unique private final int[] mite$pointWords = new int[2];
    @Unique private int mite$nativeBookWear;
    protected FunctionalAnvilMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) { super(type, id, inventory, access); }
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void mite$addSourceModeSync(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        addDataSlot(mite$sourceMode);
    }
    // Append after the established cost, source-mode and four durability words.
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"), order = 1200)
    private void mite$addPointCostSync(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        addDataSlot(mite$pointMode);
        for (int word = 0; word < 2; word++) {
            final int index = word;
            addDataSlot(new ThermalDataSlot(
                    () -> (cost.get() >>> (index * 16)) & 0xFFFF,
                    value -> mite$pointWords[index] = value & 0xFFFF));
        }
    }
    @Override public boolean mite$usesExperiencePoints() { return mite$pointMode.get() != 0; }
    @Override public boolean mite$ignoresLevelLimit() {
        return mite$usesExperiencePoints() || MaterialAnvilRules.isEquipmentRepair(inputSlots.getItem(0), inputSlots.getItem(1));
    }
    @Override public int mite$experienceCost() {
        if (!mite$usesExperiencePoints()) return 0;
        return access == ContainerLevelAccess.NULL ? mite$pointWords[0] | (mite$pointWords[1] << 16) : cost.get();
    }
    @Inject(method = "getCost", at = @At("HEAD"), cancellable = true)
    private void mite$fullPointCost(CallbackInfoReturnable<Integer> cir) {
        if (mite$ignoresLevelLimit()) cir.setReturnValue(access == ContainerLevelAccess.NULL
                ? mite$pointWords[0] | (mite$pointWords[1] << 16) : cost.get());
    }
    @WrapMethod(method = "createResult")
    private void mite$scopedSourceAnvilUpdate(Operation<Void> original) {
        try (var scope = MaterialAnvilRules.beginUpdate()) { original.call(); }
    }
    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void mite$beginSourceAnvilPreview(CallbackInfo ci) {
        // A menu without physical access is the remote client view. Its source result is
        // authoritative server data; the pre-click refresh cannot replace it with
        // a local vanilla calculation that lacks source repair/book support.
        if (access == ContainerLevelAccess.NULL && (mite$sourceMode.get() != 0 || mite$usesExperiencePoints())) {
            ci.cancel();
            return;
        }
    }
    @Inject(method = "createResult", at = @At("RETURN"))
    private void mite$sourceRepairPreview(CallbackInfo ci) {
        if (access == ContainerLevelAccess.NULL) return;
        // Native shift-click can empty the result stack before onTake receives it.
        // Quote wear from the actual preview while its enchantments are still available.
        mite$nativeBookWear = R196BookAnvil.anvilWear(inputSlots.getItem(0), inputSlots.getItem(1), resultSlots.getItem(0));
        mite$sourceMode.set(0);
        mite$pointMode.set(0);
        if (MaterialAnvilRules.otherModControlsUpdate()) return;
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof MaterialAnvilBlockEntity)) return;
            var plan = MaterialAnvilRules.plan(player, level.getBlockState(pos), inputSlots.getItem(0), inputSlots.getItem(1));
            if (plan == null) {
                int points = R196BookAnvil.experiencePoints(inputSlots.getItem(0), inputSlots.getItem(1), resultSlots.getItem(0));
                if (points > 0) {
                    mite$pointMode.set(1);
                    cost.set(points);
                } else if (inputSlots.getItem(1).has(DataComponents.STORED_ENCHANTMENTS)) {
                    // Native-only books must also add or upgrade an enchantment to produce a result.
                    resultSlots.setItem(0, ItemStack.EMPTY);
                    cost.set(0);
                }
                broadcastChanges();
                return;
            }
            mite$nativeBookWear = 0;
            mite$sourceMode.set((plan.consumed() << 1) | 1);
            ItemStack result = mite$namedOutput(plan.output());
            int points = R196BookAnvil.experiencePoints(inputSlots.getItem(0), inputSlots.getItem(1), result);
            mite$pointMode.set(points > 0 ? 1 : 0);
            resultSlots.setItem(0, result);
            repairItemCountCost = plan.consumed();
            cost.set(points);
            broadcastChanges();
        });
    }
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void mite$sourceRepairPickup(Player owner, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
        if (mite$sourceMode.get() == 0) {
            if (mite$usesExperiencePoints()) cir.setReturnValue(hasItem && mite$canAfford(owner, mite$experienceCost()));
            else if (mite$ignoresLevelLimit()) {
                int levels = ((AnvilMenu)(Object)this).getCost();
                cir.setReturnValue(hasItem && levels > 0 && (owner.isCreative() || owner.experienceLevel >= levels));
            }
            return;
        }
        if (access == ContainerLevelAccess.NULL) {
            cir.setReturnValue(hasItem && mite$canAfford(owner, ((AnvilMenu)(Object)this).getCost()));
            return;
        }
        cir.setReturnValue(hasItem && access.evaluate((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof MaterialAnvilBlockEntity)) return false;
            var plan = MaterialAnvilRules.plan(owner, level.getBlockState(pos), inputSlots.getItem(0), inputSlots.getItem(1));
            if (plan == null || !plan.allowed()) return false;
            ItemStack expected = mite$namedOutput(plan.output());
            int cost = R196BookAnvil.experiencePoints(inputSlots.getItem(0), inputSlots.getItem(1), expected);
            return mite$canAfford(owner, cost) && ItemStack.matches(expected, resultSlots.getItem(0));
        }, false));
    }
    @Unique private ItemStack mite$namedOutput(ItemStack result) {
        if (result.isEmpty()) return result;
        if (itemName != null && !itemName.isBlank()) {
            if (!itemName.equals(inputSlots.getItem(0).getHoverName().getString()))
                result.set(DataComponents.CUSTOM_NAME, Component.literal(itemName));
        } else result.remove(DataComponents.CUSTOM_NAME);
        return result;
    }
    @Unique private static boolean mite$canAfford(Player owner, int cost) {
        return cost == 0 || owner.isCreative() || owner.totalExperience >= cost;
    }
    @Unique private static void mite$payBookPoints(Player owner, int points) {
        // Commit the fixed purchase price directly; XP-gain callbacks cannot turn a taken result into a free book operation.
        PlayerExperience.setTotal(owner, owner.totalExperience - points);
        owner.increaseScore(-points);
    }
    // This is the final native survival price limit, not the earlier creative target override.
    @ModifyExpressionValue(method = "createResult", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;instabuild:Z", ordinal = 1))
    private boolean mite$bookAndRepairFeesHaveNoLevelLimit(boolean creative) {
        return creative || !MaterialAnvilRules.otherModControlsUpdate() && inputSlots.getItem(0).getCount() == 1
                && (inputSlots.getItem(1).has(DataComponents.STORED_ENCHANTMENTS)
                        || MaterialAnvilRules.isEquipmentRepair(inputSlots.getItem(0), inputSlots.getItem(1)));
    }
    @Redirect(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;giveExperienceLevels(I)V"))
    private void mite$payNativeBookPoints(Player owner, int levels) {
        if (mite$usesExperiencePoints()) mite$payBookPoints(owner, mite$experienceCost());
        else owner.giveExperienceLevels(levels);
    }
    @Redirect(method = "lambda$onTake$2", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/AnvilBlock;damage(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState mite$noVanillaRandomAnvilBreak(BlockState state) {
        // Exact repair/book wear replaces the native random damage-stage roll.
        return state;
    }
    @WrapMethod(method = "onTake")
    private void mite$wearFromNativeBook(Player owner, ItemStack taken, Operation<Void> original) {
        int wear = mite$sourceMode.get() == 0 ? mite$nativeBookWear : 0;
        mite$nativeBookWear = 0;
        original.call(owner, taken);
        if (wear <= 0 || owner.isCreative()) return;
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof MaterialAnvilBlockEntity entity) {
                entity.addDamage(wear);
                broadcastChanges();
            }
        });
    }
    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void mite$completeSourceRepair(Player owner, ItemStack taken, CallbackInfo ci) {
        if (mite$sourceMode.get() == 0) return;
        ci.cancel();
        if (access == ContainerLevelAccess.NULL) {
            int cost = ((AnvilMenu)(Object)this).getCost();
            if (!mite$canAfford(owner, cost)) return;
            if (cost > 0 && !owner.isCreative()) mite$payBookPoints(owner, cost);
            int consumed = mite$sourceMode.get() >> 1;
            ItemStack remaining = inputSlots.getItem(1).copy(); remaining.shrink(consumed);
            inputSlots.setItem(0, ItemStack.EMPTY); inputSlots.setItem(1, remaining);
            ((AnvilMenu)(Object)this).setMaximumCost(0);
            return;
        }
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof MaterialAnvilBlockEntity entity)) return;
            var plan = MaterialAnvilRules.plan(owner, level.getBlockState(pos), inputSlots.getItem(0), inputSlots.getItem(1));
            if (plan == null || !plan.allowed()) return;
            int cost = R196BookAnvil.experiencePoints(inputSlots.getItem(0), inputSlots.getItem(1), mite$namedOutput(plan.output()));
            if (!mite$canAfford(owner, cost)) return;
            if (cost > 0 && !owner.isCreative()) mite$payBookPoints(owner, cost);
            net.neoforged.neoforge.common.CommonHooks.onAnvilRepair(owner, taken, inputSlots.getItem(0), inputSlots.getItem(1));
            ItemStack remaining = inputSlots.getItem(1).copy();
            remaining.shrink(plan.consumed());
            inputSlots.setItem(0, ItemStack.EMPTY);
            inputSlots.setItem(1, remaining);
            ((AnvilMenu)(Object)this).setMaximumCost(0);
            if (!owner.isCreative()) entity.addDamage(plan.anvilWear());
            if (level.getBlockEntity(pos) != null) level.levelEvent(1030, pos, 0);
            broadcastChanges();
        });
    }
}
