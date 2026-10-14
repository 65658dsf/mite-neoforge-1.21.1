package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteArmorContent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source metal armor/chains resist fire; adamantium also resists lava. @author NingMeng123 */
@Mixin(ItemStack.class)
public abstract class SourceArmorDamageMixin {
    @Inject(method = "canBeHurtBy", at = @At("HEAD"), cancellable = true)
    private void mite$sourceMetalImmunity(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!source.is(DamageTypeTags.IS_FIRE)) return;
        ItemStack stack = (ItemStack)(Object)this;
        if (stack.isEmpty()) return;
        var armor = MiteArmorContent.definition(stack.getItem());
        String material = armor == null ? null : armor.material();
        if (material == null) material = MiteArmorContent.chains().stream()
                .filter(chain -> stack.is(MiteArmorContent.get(chain.id()))).map(MiteArmorContent.ChainDefinition::material).findFirst().orElse(null);
        if (material == null && (stack.is(Items.IRON_HORSE_ARMOR) || stack.is(Items.GOLDEN_HORSE_ARMOR)
                || stack.is(Items.IRON_HELMET) || stack.is(Items.IRON_CHESTPLATE)
                || stack.is(Items.IRON_LEGGINGS) || stack.is(Items.IRON_BOOTS))) material = "iron";
        if (material != null && (!source.is(DamageTypes.LAVA) || material.equals("adamantium"))) cir.setReturnValue(false);
    }
}
