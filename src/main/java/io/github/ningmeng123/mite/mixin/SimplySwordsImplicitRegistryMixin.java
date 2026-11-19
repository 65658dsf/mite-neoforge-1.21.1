package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.rules.SimplySwordsImplicitRules;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Balance built-in implicits while using Simply Swords' native handlers. @author NingMeng123 */
@Pseudo
@Mixin(targets = "net.sweenus.simplyswords.api.WeaponImplicitRegistry", remap = false)
public abstract class SimplySwordsImplicitRegistryMixin {
    @Shadow @Final private static Map<ResourceLocation, ?> DEFINITIONS_BY_ID;

    @ModifyArgs(method = "chanceDefinition", at = @At(value = "INVOKE", target =
            "Lnet/sweenus/simplyswords/api/WeaponImplicitDefinition;<init>(Lnet/minecraft/resources/ResourceLocation;"
                    + "Lnet/minecraft/resources/ResourceLocation;IILnet/sweenus/simplyswords/api/WeaponImplicitDefinition$DamageHandler;"
                    + "Lnet/sweenus/simplyswords/api/WeaponImplicitDefinition$HitHandler;Lnet/sweenus/simplyswords/api/WeaponImplicitDefinition$IncomingDamageHandler;"
                    + "Lnet/sweenus/simplyswords/api/WeaponImplicitDefinition$TooltipFormatter;)V"), allow = 1)
    private static void mite$balanceImplicitRange(Args args) {
        var range = SimplySwordsImplicitRules.range(args.get(0));
        if (range == null) return;
        args.set(2, range.min());
        args.set(3, range.max());
    }

    @WrapOperation(method = "applySunder", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), allow = 1)
    private static int mite$capGreathammerSunder(int limit, int total, Operation<Integer> original,
            ItemStack stack, @Coerce Object component, LivingEntity target, LivingEntity attacker, float damage) {
        var implicit = (SimplySwordsImplicitRules.ComponentAccess) component;
        if (SimplySwordsImplicitRules.GREATHAMMER_SUNDER.equals(implicit.implicitId())) {
            limit = SimplySwordsImplicitRules.GREATHAMMER_SUNDER_CAP;
        }
        return original.call(limit, total);
    }

    @WrapOperation(method = "formatTooltip(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/network/chat/Component;",
            at = @At(value = "INVOKE", target = "Lnet/sweenus/simplyswords/api/WeaponImplicitDefinition;formatTooltip("
                    + "Lnet/sweenus/simplyswords/item/component/WeaponImplicitComponent;)Lnet/minecraft/network/chat/Component;"), allow = 1)
    private static Component mite$showGreathammerCap(@Coerce Object definition, @Coerce Object component,
            Operation<Component> original) {
        return definition == DEFINITIONS_BY_ID.get(SimplySwordsImplicitRules.GREATHAMMER_SUNDER)
                ? mite$greathammerTooltip() : original.call(definition, component);
    }

    @Inject(method = "formatRangePreview", at = @At("HEAD"), cancellable = true)
    private static void mite$previewFixedValue(@Coerce Object definition, CallbackInfoReturnable<Component> cir) {
        if (definition == DEFINITIONS_BY_ID.get(SimplySwordsImplicitRules.GREATHAMMER_SUNDER)) {
            cir.setReturnValue(mite$greathammerTooltip());
        } else if (definition == DEFINITIONS_BY_ID.get(SimplySwordsImplicitRules.SCYTHE_EXECUTE)) {
            int chance = SimplySwordsImplicitRules.range(SimplySwordsImplicitRules.SCYTHE_EXECUTE).min();
            cir.setReturnValue(Component.translatable("tooltip.simplyswords.implicit.execute", chance));
        }
    }

    @Inject(method = "appendRange", at = @At("HEAD"), cancellable = true)
    private static void mite$skipFixedRange(Component tooltip, @Coerce Object definition,
            CallbackInfoReturnable<Component> cir) {
        if (definition == DEFINITIONS_BY_ID.get(SimplySwordsImplicitRules.GREATHAMMER_SUNDER)
                || definition == DEFINITIONS_BY_ID.get(SimplySwordsImplicitRules.SCYTHE_EXECUTE)) {
            cir.setReturnValue(tooltip);
        }
    }

    @Unique
    private static Component mite$greathammerTooltip() {
        int perHit = SimplySwordsImplicitRules.range(SimplySwordsImplicitRules.GREATHAMMER_SUNDER).min();
        return Component.translatable("tooltip.mite.simplyswords.greathammer_sunder", perHit,
                SimplySwordsImplicitRules.GREATHAMMER_SUNDER_CAP);
    }
}
