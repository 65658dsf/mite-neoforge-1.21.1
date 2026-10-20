package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.enchantment.ConfiguredEnchantments;
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

/** Keep source reward rules; each route applies candidate probabilities exactly once. @author NingMeng123 */
@Mixin(EnchantmentHelper.class)
public abstract class R196RandomEnchantmentsMixin {
    @WrapMethod(method = "selectEnchantment")
    private static List<EnchantmentInstance> mite$sourceRandomSelection(RandomSource random, ItemStack stack, int energy,
            Stream<Holder<Enchantment>> stream, Operation<List<EnchantmentInstance>> original) {
        if (!stack.is(Items.BOOK) && !R196EnchantmentTable.isSourceEquipment(stack))
            return original.call(random, stack, energy, stream);
        List<Holder<Enchantment>> allowed = stream.filter(ConfiguredEnchantments::enabled).toList();
        boolean modernBook = stack.is(Items.BOOK) && allowed.stream().noneMatch(holder -> holder.unwrapKey()
                .map(key -> R196EnchantmentCatalog.definition(key.location().toString()) != null).orElse(false));
        return modernBook ? original.call(random, stack, energy, allowed.stream())
                : R196EnchantmentTable.randomEnchantments(stack, energy, random, allowed);
    }
}
