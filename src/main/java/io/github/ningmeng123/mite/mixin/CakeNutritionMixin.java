package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteFoodCatalog;
import io.github.ningmeng123.mite.curse.CurseIngestion;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Six slices, instantaneous use, source food values and sugar/protein. @author NingMeng123 */
@Mixin(CakeBlock.class)
public abstract class CakeNutritionMixin {
    @Inject(method="eat",at=@At("HEAD"),cancellable=true)
    private static void mite$eatSlice(LevelAccessor level,BlockPos pos,BlockState state,Player player,CallbackInfoReturnable<InteractionResult> cir) {
        if(!FoodConsumption.canIngest(player,MiteFoodCatalog.CAKE_SLICE) || !CurseIngestion.cakeAllowed(player)) {
            cir.setReturnValue(InteractionResult.PASS); return;
        }
        if(!level.isClientSide()) {
            player.awardStat(Stats.EAT_CAKE_SLICE);
            ((DualFoodData)player.getFoodData()).mite$ingest(MiteFoodCatalog.CAKE_SLICE);
            int bites=state.getValue(CakeBlock.BITES);
            level.gameEvent(player,GameEvent.EAT,pos);
            if(bites<5) level.setBlock(pos,state.setValue(CakeBlock.BITES,bites+1),3);
            else { level.removeBlock(pos,false); level.gameEvent(player,GameEvent.BLOCK_DESTROY,pos); }
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
