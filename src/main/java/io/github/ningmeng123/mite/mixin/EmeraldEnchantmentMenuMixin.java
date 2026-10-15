package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** R196 table offers, point payment with native lapis costs and native modern-only equipment fallback. @author NingMeng123 */
@Mixin(EnchantmentMenu.class)
public abstract class EmeraldEnchantmentMenuMixin extends AbstractContainerMenu implements R196EnchantmentMenuAccess {
    @Shadow @Final private ContainerLevelAccess access;
    @Shadow @Final private Container enchantSlots;
    @Shadow @Final private RandomSource random;
    @Shadow @Final private DataSlot enchantmentSeed;
    @Shadow @Final public int[] costs;
    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;
    @Unique private final DataSlot mite$source = DataSlot.standalone();
    @Unique private int mite$shelves;
    @Unique private boolean mite$emerald;
    @Unique private boolean mite$unblocked;
    @Unique private boolean mite$offersReady;
    @Unique private ItemStack mite$offerInput = ItemStack.EMPTY;

    protected EmeraldEnchantmentMenuMixin(MenuType<?> type, int id) { super(type, id); }
    @Override public boolean mite$usesSourceEnchanting() { return mite$source.get() != 0; }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void mite$syncSourceMode(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        addDataSlot(mite$source);
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void mite$sourceOffers(Container changed, CallbackInfo ci) {
        if (changed != enchantSlots) return;
        if (access == ContainerLevelAccess.NULL) {
            if (mite$usesSourceEnchanting()) ci.cancel();
            return;
        }
        ItemStack stack = enchantSlots.getItem(0);
        boolean source = access.evaluate(R196EnchantmentTable::isTable, false) && R196EnchantmentTable.sourceRoute(stack);
        mite$source.set(source ? 1 : 0);
        if (!source) { mite$offersReady = false; return; }
        ci.cancel();
        access.execute((level, pos) -> {
            int shelves = R196EnchantmentTable.accessibleBookshelves(level, pos);
            boolean emerald = R196EnchantmentTable.emerald(level, pos);
            boolean unblocked = R196EnchantmentTable.passable(level, pos.above());
            // Changing the native lapis slot must not reroll the same input's offers.
            if (mite$offersReady && ItemStack.matches(stack, mite$offerInput) && shelves == mite$shelves
                    && emerald == mite$emerald && unblocked == mite$unblocked) {
                broadcastChanges();
                return;
            }
            mite$offersReady = true;
            mite$offerInput = stack.copy();
            mite$shelves = shelves;
            mite$emerald = emerald;
            mite$unblocked = unblocked;
            enchantmentSeed.set(random.nextInt());
            for (int slot = 0; slot < 3; slot++) {
                costs[slot] = unblocked
                        ? R196EnchantmentTable.energy(stack, mite$shelves, mite$emerald, slot, slot < 2 ? random.nextFloat() : .5F) : 0;
                enchantClue[slot] = -1;
                levelClue[slot] = -1;
            }
            broadcastChanges();
        });
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void mite$sourceEnchant(Player owner, int slot, CallbackInfoReturnable<Boolean> cir) {
        if (!mite$usesSourceEnchanting()) return;
        cir.setReturnValue(false);
        if (slot < 0 || slot >= 3 || costs[slot] < 1) return;
        ItemStack input = enchantSlots.getItem(0);
        if (!R196EnchantmentTable.enchantable(input)) return;
        int energy = costs[slot];
        int payment = R196EnchantmentTable.experienceCost(energy);
        int lapisCost = slot + 1;
        if (!owner.getAbilities().instabuild && (owner.totalExperience < payment
                || !R196EnchantmentTable.hasLapis(enchantSlots.getItem(1), slot))) return;
        if (access == ContainerLevelAccess.NULL) { cir.setReturnValue(true); return; }
        access.execute((level, pos) -> {
            if (level.isClientSide || !R196EnchantmentTable.isTable(level, pos)
                    || !R196EnchantmentTable.passable(level, pos.above())) return;
            if (mite$shelves != R196EnchantmentTable.accessibleBookshelves(level, pos)
                    || mite$emerald != R196EnchantmentTable.emerald(level, pos)) {
                ((EnchantmentMenu)(Object)this).slotsChanged(enchantSlots);
                return;
            }
            ItemStack before = input.copy();
            ItemStack lapisBefore = enchantSlots.getItem(1).copy();
            ItemStack output = R196EnchantmentTable.output(level.registryAccess(), before, energy, random);
            if (output.isEmpty()) return;
            if (!owner.getAbilities().instabuild) {
                if (!(owner instanceof ServerPlayer server)) return;
                var approved = CraftingQuality.approvePayment(server, payment);
                // uf.addExperience only increases score on positive XP gains; enchanting never subtracts score.
                if (approved == null || costs[slot] != energy || !ItemStack.matches(before, enchantSlots.getItem(0))
                        || !ItemStack.matches(lapisBefore, enchantSlots.getItem(1))
                        || !R196EnchantmentTable.hasLapis(enchantSlots.getItem(1), slot)
                        || !CraftingQuality.pay(server, approved, true)) return;
                enchantSlots.getItem(1).shrink(lapisCost);
            }
            // Modern creative keeps free enchanting, rather than the source's creative XP debt.
            enchantSlots.setItem(0, output);
            net.neoforged.neoforge.common.CommonHooks.onPlayerEnchantItem(owner, output,
                    EnchantmentHelper.getEnchantmentsForCrafting(output).entrySet().stream()
                            .map(entry -> new net.minecraft.world.item.enchantment.EnchantmentInstance(entry.getKey(), entry.getIntValue())).toList());
            owner.awardStat(Stats.ENCHANT_ITEM);
            if (owner instanceof ServerPlayer server) CriteriaTriggers.ENCHANTED_ITEM.trigger(server, output, energy);
            enchantSlots.setChanged();
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1F, .9F + random.nextFloat() * .1F);
            cir.setReturnValue(true);
        });
    }
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void mite$validSourceTable(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (access.evaluate(R196EnchantmentTable::isTable, false))
            cir.setReturnValue(access.evaluate((level, pos) -> R196EnchantmentTable.passable(level, pos.above())
                    && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64D, false));
    }
}
