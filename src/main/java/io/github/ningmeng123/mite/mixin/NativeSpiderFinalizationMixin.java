package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.NativeArachnidEvents;
import io.github.ningmeng123.mite.entity.R196Spider;
import io.github.ningmeng123.mite.entity.SourceSpiderFinalization;
import javax.annotation.Nullable;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only tt adds jockey/potion callbacks; native te and the four non-tt source spiders get the og base callback. @author NingMeng123 */
@Mixin({Spider.class,CaveSpider.class})
public abstract class NativeSpiderFinalizationMixin extends Monster {
    protected NativeSpiderFinalizationMixin(EntityType<? extends Monster> type,Level level) { super(type,level); }
    @Inject(method="finalizeSpawn",at=@At("HEAD"),cancellable=true)
    private void mite$sourceCallback(ServerLevelAccessor accessor,DifficultyInstance difficulty,MobSpawnType reason,
            @Nullable SpawnGroupData group,CallbackInfoReturnable<SpawnGroupData> cir) {
        Spider spider=(Spider)(Object)this;
        if(spider instanceof R196Spider || NativeArachnidEvents.managed(spider))
            cir.setReturnValue(SourceSpiderFinalization.finish(spider,accessor,difficulty,reason,group));
    }
}
