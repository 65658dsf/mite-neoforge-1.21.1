package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.tags.BlockTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** jw fells only the vertical log column, with every extra break using normal protection/wear/drops. @author NingMeng123 */
@Mixin(ServerPlayerGameMode.class)
public abstract class TreeFellingMixin {
    @Shadow protected ServerLevel level;
    @Shadow @org.spongepowered.asm.mixin.Final protected ServerPlayer player;
    @Shadow public abstract boolean destroyBlock(BlockPos pos);
    @Unique private boolean mite$felling;
    @WrapMethod(method = "destroyBlock")
    private boolean mite$verticalLogs(BlockPos pos, Operation<Boolean> original) {
        boolean log = level.getBlockState(pos).is(BlockTags.LOGS);
        boolean removed = original.call(pos);
        if (!removed || !log || mite$felling || player.isCreative() || player.isSpectator()
                || level.getBlockState(pos).is(BlockTags.LOGS)) return removed;
        int count = R196Enchantments.level(player.getMainHandItem(), level, "tree_felling");
        mite$felling = true;
        try {
            for (int dy = 1; dy <= count; dy++) {
                BlockPos above = pos.above(dy);
                if (!level.getBlockState(above).is(BlockTags.LOGS) || player.getMainHandItem().isEmpty()
                        || !level.mayInteract(player, above) || !destroyBlock(above)) break;
            }
        } finally { mite$felling = false; }
        return removed;
    }
}
