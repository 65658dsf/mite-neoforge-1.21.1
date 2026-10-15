package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentTable;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers level-based mob/loot generation without enabling unregistered R196 infinity. @author NingMeng123 */
@Mixin(EnchantmentHelper.class)
public abstract class R196RandomEnchantmentsMixin {
    @Inject(method = "selectEnchantment", at = @At("HEAD"), cancellable = true)
    private static void mite$sourceRandomSelection(RandomSource random, ItemStack stack, int energy,
            Stream<Holder<Enchantment>> stream, CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        if (Boolean.TRUE.equals(MITE_NATIVE_BOOK.get())) return;
        if (!stack.is(Items.BOOK) && !R196EnchantmentTable.isSourceEquipment(stack)) return;
        List<Holder<Enchantment>> allowed = stream.toList();
        // Explicit modern-only book pools retain their intended modern rewards. Mixed/general pools use R196.
        boolean modernBook = stack.is(Items.BOOK) && allowed.stream().noneMatch(holder -> holder.unwrapKey()
                .map(key -> R196EnchantmentCatalog.definition(key.location().toString()) != null).orElse(false));
        if (modernBook) {
            // Re-enter the vanilla method with a fresh stream; suppress this hook only for this nested call.
            try { MITE_NATIVE_BOOK.set(true); cir.setReturnValue(EnchantmentHelper.selectEnchantment(random, stack, energy, allowed.stream())); }
            finally { MITE_NATIVE_BOOK.remove(); }
        } else cir.setReturnValue(R196EnchantmentTable.randomEnchantments(stack, energy, random, allowed));
    }
    @org.spongepowered.asm.mixin.Unique private static final ThreadLocal<Boolean> MITE_NATIVE_BOOK = new ThreadLocal<>();
}
