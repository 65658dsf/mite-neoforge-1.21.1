package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.survival.AutoRestock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Observe completed authoritative actions, never manual drops or container transfers. @author NingMeng123 */
@Mixin(ServerPlayerGameMode.class)
public abstract class AutoRestockGameModeMixin {
    @Shadow @Final protected ServerPlayer player;
    @WrapMethod(method = "useItem")
    private InteractionResult mite$used(ServerPlayer actor, Level level, ItemStack item, InteractionHand hand, Operation<InteractionResult> original) {
        var before = AutoRestock.before(actor, hand);
        InteractionResult result = original.call(actor, level, item, hand);
        AutoRestock.after(before); return result;
    }
    @WrapMethod(method = "useItemOn")
    private InteractionResult mite$placed(ServerPlayer actor, Level level, ItemStack item, InteractionHand hand,
            BlockHitResult hit, Operation<InteractionResult> original) {
        var before = AutoRestock.before(actor, hand);
        InteractionResult result = original.call(actor, level, item, hand, hit);
        AutoRestock.after(before); return result;
    }
    @WrapMethod(method = "destroyBlock")
    private boolean mite$brokenTool(BlockPos pos, Operation<Boolean> original) {
        var before = AutoRestock.before(player, InteractionHand.MAIN_HAND);
        boolean result = original.call(pos);
        AutoRestock.after(before); return result;
    }
}
