package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.R196Cube;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Magma keeps source mineral immunity, natural defense, water cooling, fire contact and exact damage. @author NingMeng123 */
@Mixin(MagmaCube.class)
public abstract class SourceMagmaCubeMixin extends Slime {
    @Unique private int mite$fizzDelay;
    protected SourceMagmaCubeMixin(EntityType<? extends Slime> type,Level level) { super(type,level); }
    @Inject(method="setSize",at=@At("TAIL"))
    private void mite$sourceNaturalDefense(int size,boolean heal,CallbackInfo ci) {
        ((MagmaCube)(Object)this).getAttribute(Attributes.ARMOR).setBaseValue(0);
    }
    @Inject(method="getAttackDamage",at=@At("HEAD"),cancellable=true)
    private void mite$sourceDamage(CallbackInfoReturnable<Float> cir) { cir.setReturnValue(((MagmaCube)(Object)this).getSize()*2F); }
    @Inject(method="getJumpDelay",at=@At("HEAD"),cancellable=true)
    private void mite$sourceJump(CallbackInfoReturnable<Integer> cir) { MagmaCube cube=(MagmaCube)(Object)this; cir.setReturnValue(cube.getTarget()==null ? 40+cube.getRandom().nextInt(81) : 20); }
    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (isInWaterOrRain() && --mite$fizzDelay<=0) {
            playSound(SoundEvents.FIRE_EXTINGUISH,.7F,1.6F+(getRandom().nextFloat()-getRandom().nextFloat())*.4F);
            mite$fizzDelay=getRandom().nextInt(7)+2;
            if (getRandom().nextInt(isInWater() ? 4 : 16)==0) hurt(MobCombat.source(this,"water"),1F);
        }
        R196Cube.magmaContactBlocks((MagmaCube)(Object)this);
    }
}
