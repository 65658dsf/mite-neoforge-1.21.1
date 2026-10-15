package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Distinguish actual webs from slime, berry bushes and other native stuck vectors. @author NingMeng123 */
@Mixin(Entity.class)
public abstract class SourceEnchantmentMovementMixin {
    @Unique private boolean mite$enchantmentWebMotion;
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"))
    private void mite$rememberWeb(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        mite$enchantmentWebMotion = state.is(Blocks.COBWEB);
    }
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 mite$resistWeb(Vec3 movement) {
        if (!mite$enchantmentWebMotion) return movement;
        mite$enchantmentWebMotion = false;
        return SourceEnchantmentCombat.webMotion((Entity)(Object)this, movement);
    }
}
