package io.github.ningmeng123.mite.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.ningmeng123.mite.crafting.AnvilExperienceAccess;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Book operations show their exact point fee; native pickup checks provide the affordability color. @author NingMeng123 */
@Mixin(AnvilScreen.class)
public abstract class AnvilExperienceScreenMixin extends AbstractContainerScreen<AnvilMenu> {
    protected AnvilExperienceScreenMixin(AnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @ModifyExpressionValue(method = "renderLabels", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent mite$showExperiencePoints(MutableComponent original) {
        var experience = (AnvilExperienceAccess)menu;
        return experience.mite$usesExperiencePoints()
                ? Component.translatable("gui.mite.anvil.experience_cost", experience.mite$experienceCost()) : original;
    }

    @ModifyExpressionValue(method = "renderLabels", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Abilities;instabuild:Z"))
    private boolean mite$pointFeesHaveNoLevelCap(boolean creative) {
        // This read only controls the native "40 levels" text branch. Book use
        // and repairs bypass it; the real pickup check still decides affordability.
        return creative || ((AnvilExperienceAccess)menu).mite$ignoresLevelLimit();
    }
}
