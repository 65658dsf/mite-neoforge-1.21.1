package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.ningmeng123.mite.entity.R196Infestation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Respect final cancellable drops and the actual player/silverfish cause; explosions never release inhabitants. @author NingMeng123 */
@Mixin(CommonHooks.class)
public abstract class SourceInfestedDropsMixin {
    @WrapOperation(method="handleBlockDrops",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;spawnAfterBreak(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;Z)V"))
    private static void mite$sourceInfestationCause(BlockState state,ServerLevel level,BlockPos pos,ItemStack tool,boolean xp,Operation<Void> original,
            @Local(argsOnly=true) Entity breaker) {
        if (state.getBlock() instanceof InfestedBlock) {
            if (level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) && (breaker instanceof Player || breaker instanceof Silverfish))
                R196Infestation.spawn(level,pos,state,breaker instanceof Player player ? player : ((Silverfish)breaker).getTarget());
        } else original.call(state,level,pos,tool,xp);
    }
}
