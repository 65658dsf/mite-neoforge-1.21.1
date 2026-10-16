package io.github.ningmeng123.mite.mixin;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Set the native cached light value before either campfire creates its block states. @author NingMeng123 */
@Mixin(Blocks.class)
public abstract class CampfireLightMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/CampfireBlock;<init>(ZILnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V"), index = 2)
    private static BlockBehaviour.Properties mite$tenLight(BlockBehaviour.Properties properties) {
        return properties.lightLevel(state -> state.getValue(CampfireBlock.LIT) ? 10 : 0);
    }
}
