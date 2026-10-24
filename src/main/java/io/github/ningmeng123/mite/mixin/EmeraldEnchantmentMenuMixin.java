package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentMenuAccess;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import io.github.ningmeng123.mite.enchantment.VanillaTableEnchanting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.RegistryAccess;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Atomic source-point payment, material-aware selection and the source golden-apple conversion. @author NingMeng123 */
@Mixin(EnchantmentMenu.class)
public abstract class EmeraldEnchantmentMenuMixin extends AbstractContainerMenu implements R196EnchantmentMenuAccess {
    @Shadow @Final private ContainerLevelAccess access;
    @Shadow @Final private Container enchantSlots;
    @Shadow @Final private RandomSource random;
    @Shadow @Final private DataSlot enchantmentSeed;
    @Shadow @Final public int[] costs;
    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;
    @Shadow private List<EnchantmentInstance> getEnchantmentList(RegistryAccess registry, ItemStack stack, int slot, int level) {
        throw new AssertionError();
    }
    @Unique private final DataSlot mite$source = DataSlot.standalone();
    @Unique private final int[] mite$pointCosts = new int[3];

    protected EmeraldEnchantmentMenuMixin(MenuType<?> type, int id) { super(type, id); }
    @Override public boolean mite$usesSourceEnchanting() { return mite$source.get() != 0; }
    @Override public int mite$experienceCost(int slot) { return slot >= 0 && slot < 3 ? mite$pointCosts[slot] : 0; }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void mite$syncPointPrices(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        addDataSlot(mite$source);
        for (int slot = 0; slot < 3; slot++) addDataSlot(DataSlot.shared(mite$pointCosts, slot));
        if (access != ContainerLevelAccess.NULL) mite$source.set(1);
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void mite$serverOffersOnly(Container changed, CallbackInfo ci) {
        if (access == ContainerLevelAccess.NULL && mite$usesSourceEnchanting()) { ci.cancel(); return; }
        if (changed != enchantSlots || access == ContainerLevelAccess.NULL || !mite$goldenApple(enchantSlots.getItem(0))) return;
        // Source vm: special energy two is independent of shelves, table tier and ordinary candidate pools.
        Arrays.fill(costs, 2);
        Arrays.fill(enchantClue, -1);
        Arrays.fill(levelClue, -1);
        Arrays.fill(mite$pointCosts, 200);
        broadcastChanges();
        ci.cancel();
    }

    @Unique private static boolean mite$goldenApple(ItemStack stack) {
        return stack.is(Items.GOLDEN_APPLE) && stack.getCount() == 1 && !stack.isEnchanted();
    }

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void mite$quotePointPrices(Container changed, CallbackInfo ci) {
        if (changed != enchantSlots || access == ContainerLevelAccess.NULL) return;
        access.execute((level, pos) -> {
            ItemStack stack = enchantSlots.getItem(0);
            int shelves = R196EnchantmentTable.accessibleBookshelves(level, pos);
            boolean emerald = R196EnchantmentTable.emerald(level, pos);
            // Retained prices are independent of native bookshelf power and selection RNG.
            RandomSource priceRandom = RandomSource.create(enchantmentSeed.get());
            for (int slot = 0; slot < 3; slot++) {
                float noise = slot < 2 ? priceRandom.nextFloat() : .5F;
                int energy = costs[slot] > 0 && stack.isEnchantable()
                        ? R196EnchantmentTable.tableEnergy(stack, shelves, emerald, slot, noise) : 0;
                mite$pointCosts[slot] = R196EnchantmentTable.experienceCost(energy);
            }
            broadcastChanges();
        });
    }

    @Inject(method = "getEnchantmentList", at = @At("HEAD"), cancellable = true)
    private void mite$nativeTableSelection(RegistryAccess registry, ItemStack stack, int slot, int level,
            CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        // Preserve native seed addition (including overflow), while using the identical source payment budget.
        random.setSeed((long)(enchantmentSeed.get() + slot));
        int paidEnergy = access.evaluate((world, pos) -> {
            RandomSource priceRandom = RandomSource.create(enchantmentSeed.get());
            float noise = .5F;
            for (int current = 0; current <= slot && current < 2; current++) noise = priceRandom.nextFloat();
            if (slot == 2) noise = .5F;
            return R196EnchantmentTable.tableEnergy(stack, R196EnchantmentTable.accessibleBookshelves(world, pos),
                    R196EnchantmentTable.emerald(world, pos), slot, noise);
        }, Integer.MAX_VALUE);
        List<EnchantmentInstance> selected = new ArrayList<>(VanillaTableEnchanting.select(registry, stack, level, random, paidEnergy));
        if (stack.is(Items.BOOK) && selected.size() > 1) selected = List.of(selected.get(random.nextInt(selected.size())));
        cir.setReturnValue(selected);
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void mite$payPoints(Player owner, int slot, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
        if (slot < 0 || slot >= 3 || costs[slot] < 1 || mite$pointCosts[slot] < 1) return;
        ItemStack input = enchantSlots.getItem(0);
        boolean conversion = mite$goldenApple(input);
        if (!conversion && (!input.isEnchantable() || enchantClue[slot] < 0)) return;
        int offerLevel = costs[slot], payment = mite$pointCosts[slot], seed = enchantmentSeed.get();
        if (!owner.getAbilities().instabuild && (owner.experienceLevel < offerLevel || owner.totalExperience < payment
                || !R196EnchantmentTable.hasLapis(enchantSlots.getItem(1), slot))) return;
        if (access == ContainerLevelAccess.NULL) { cir.setReturnValue(true); return; }
        access.execute((level, pos) -> {
            if (level.isClientSide || !R196EnchantmentTable.isTable(level, pos) || !stillValid(owner)) return;
            int[] offers = costs.clone(), clues = enchantClue.clone(), ranks = levelClue.clone(), prices = mite$pointCosts.clone();
            // Revalidate changed shelves/configuration before charging for the displayed offer.
            ((EnchantmentMenu)(Object)this).slotsChanged(enchantSlots);
            if (!Arrays.equals(offers, costs) || !Arrays.equals(clues, enchantClue)
                    || !Arrays.equals(ranks, levelClue) || !Arrays.equals(prices, mite$pointCosts)) return;
            ItemStack before = input.copy(), lapisBefore = enchantSlots.getItem(1).copy();
            List<EnchantmentInstance> selected = conversion ? List.of() : getEnchantmentList(level.registryAccess(), before, slot, offerLevel);
            if (!conversion && selected.isEmpty()) return;
            ItemStack output = conversion ? new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)
                    : before.getItem().applyEnchantments(before.copy(), selected);
            if (output.isEmpty()) return;
            if (!owner.getAbilities().instabuild) {
                if (!(owner instanceof ServerPlayer server)) return;
                var approved = CraftingQuality.approvePayment(server, payment);
                if (approved == null || costs[slot] != offerLevel || mite$pointCosts[slot] != payment
                        || enchantmentSeed.get() != seed || owner.experienceLevel < offerLevel
                        || !ItemStack.matches(before, enchantSlots.getItem(0))
                        || !ItemStack.matches(lapisBefore, enchantSlots.getItem(1))
                        || !R196EnchantmentTable.hasLapis(enchantSlots.getItem(1), slot)
                        || !CraftingQuality.pay(server, approved, true)) return;
                enchantSlots.getItem(1).shrink(slot + 1);
            }
            // Refresh the seed without charging native levels a second time.
            owner.onEnchantmentPerformed(before, 0);
            enchantmentSeed.set(owner.getEnchantmentSeed());
            enchantSlots.setItem(0, output);
            net.neoforged.neoforge.common.CommonHooks.onPlayerEnchantItem(owner, output, selected);
            owner.awardStat(Stats.ENCHANT_ITEM);
            if (owner instanceof ServerPlayer server) CriteriaTriggers.ENCHANTED_ITEM.trigger(server, output, slot + 1);
            enchantSlots.setChanged();
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1F, .9F + random.nextFloat() * .1F);
            cir.setReturnValue(true);
        });
    }

    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void mite$validTable(Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(access.evaluate((level, pos) -> R196EnchantmentTable.isTable(level, pos)
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64D, true));
    }
}
