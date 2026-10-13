package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.rules.OreBlockRules;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** TNT's default no-decay drops still need apr's explosion-specific output identity. @author NingMeng123 */
@Mixin(BlockBehaviour.class)
public abstract class OreExplosionContextMixin {
    @Redirect(method = "onExplosionHit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;getDrops(Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;"))
    private List<ItemStack> mite$oreExplosionDrops(BlockState target, LootParams.Builder builder,
            BlockState state, Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> drops) {
        if (!OreBlockRules.isManaged(target)) return target.getDrops(builder);
        builder.withParameter(LootContextParams.EXPLOSION_RADIUS, explosion.radius());
        return target.getDrops(builder).stream().filter(stack -> !stack.isEmpty()).toList();
    }
}
