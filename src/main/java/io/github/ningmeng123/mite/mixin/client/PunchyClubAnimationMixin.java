package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import net.minecraft.core.Holder;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Clubs share the active Punchy sword animations without changing gameplay tags. @author NingMeng123 */
@Pseudo
@Mixin(targets = "punchy.client.render.ToolKindResolver", remap = false)
public abstract class PunchyClubAnimationMixin {
    // Punchy 3.2b delegates the tag checks to original$resolveItemKind after checking overrides.
    @WrapOperation(method = {
            "resolveItemKind(Lnet/minecraft/world/item/ItemStack;Lpunchy/client/renderer/layer/VanillaFirstPersonItemLayer$ToolKind;)Lpunchy/client/renderer/layer/VanillaFirstPersonItemLayer$ToolKind;",
            "original$resolveItemKind(Lnet/minecraft/world/item/ItemStack;Lpunchy/client/renderer/layer/VanillaFirstPersonItemLayer$ToolKind;)Lpunchy/client/renderer/layer/VanillaFirstPersonItemLayer$ToolKind;"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z"), require = 1)
    private static boolean mite$clubStackSwordAnimation(ItemStack stack, TagKey<Item> tag, Operation<Boolean> original) {
        return original.call(stack, tag) || tag.equals(ItemTags.SWORDS) && mite$club(stack);
    }

    @WrapOperation(method = "resolveItemKind(Lnet/minecraft/world/item/Item;Lpunchy/client/renderer/layer/VanillaFirstPersonItemLayer$ToolKind;)Lpunchy/client/renderer/layer/VanillaFirstPersonItemLayer$ToolKind;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Holder$Reference;is(Lnet/minecraft/tags/TagKey;)Z"), require = 1)
    private static boolean mite$clubItemSwordAnimation(Holder.Reference<Item> item, TagKey<Item> tag, Operation<Boolean> original) {
        return original.call(item, tag) || tag.equals(ItemTags.SWORDS) && mite$club(new ItemStack(item.value()));
    }

    @Unique
    private static boolean mite$club(ItemStack stack) {
        var profile = EquipmentDurability.toolProfile(stack);
        return profile != null && switch (profile.family()) {
            case CLUB, CUDGEL -> true;
            default -> false;
        };
    }
}
