package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.survival.GrindstoneExperienceAccess;
import io.github.ningmeng123.mite.survival.GrindstoneResultExperienceAccess;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

/** All ordinary, hotbar, drop and collection paths must afford the result's quoted fee. @author NingMeng123 */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneResultSlotMixin extends Slot implements GrindstoneResultExperienceAccess {
    @Shadow @Final GrindstoneMenu this$0;

    protected GrindstoneResultSlotMixin(Container container, int slot, int x, int y) { super(container, slot, x, y); }

    @Invoker("getExperienceAmount")
    @Override public abstract int mite$rollExperience(Level level);

    @Override public boolean mayPickup(Player player) {
        return ((GrindstoneExperienceAccess)this$0).mite$canTake(player) && super.mayPickup(player);
    }
}
