package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.worldgen.TrialBlockDrops;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Filter block loot before explosion merging while retaining native destruction and effects. @author NingMeng123 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class TrialBlockStateDropsMixin {
    @WrapMethod(method = "onExplosionHit")
    private void mite$naturalTrialExplosion(Level level, BlockPos pos, Explosion explosion,
            BiConsumer<ItemStack, BlockPos> drops, Operation<Void> original) {
        boolean protectedBlock = TrialBlockDrops.suppresses(level, pos);
        try (var ignored = TrialBlockDrops.destruction(level, pos)) {
            BiConsumer<ItemStack, BlockPos> output = protectedBlock ? (stack, location) -> {} : drops;
            original.call(level, pos, explosion, output);
        }
    }

    @Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
    private void mite$naturalTrialLoot(LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
        var origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin != null && TrialBlockDrops.suppresses(params.getLevel(), BlockPos.containing(origin))) {
            cir.setReturnValue(List.of());
        }
    }

    @Inject(method = "spawnAfterBreak", at = @At("HEAD"), cancellable = true)
    private void mite$naturalTrialAfterBreak(ServerLevel level, BlockPos pos, ItemStack tool,
            boolean experience, CallbackInfo ci) {
        if (TrialBlockDrops.suppresses(level, pos)) ci.cancel();
    }
}
