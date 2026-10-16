package io.github.ningmeng123.mite.mixin;
import io.github.ningmeng123.mite.advancement.MiteAchievements;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** xl awards Plant Doctor only after an actual successful crop treatment. @author NingMeng123 */
@Mixin(BoneMealItem.class)
public abstract class AchievementBonemealMixin {
    @Inject(method="applyBonemeal",at=@At("RETURN"))
    private static void mite$treated(ItemStack stack,Level level,BlockPos pos,Player player,CallbackInfoReturnable<Boolean> cir){
        if(Boolean.TRUE.equals(cir.getReturnValue())&&player!=null&&level.getBlockState(pos).getBlock() instanceof CropBlock)
            MiteAchievements.award(player,"plantDoctor");
    }
}
