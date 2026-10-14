package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.block.R196CopperspineBlock;
import io.github.ningmeng123.mite.entity.R196Infestation;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Source ore-sensitive infestation does not seize vanilla's global stone host mapping. @author NingMeng123 */
@Mixin(InfestedBlock.class)
public abstract class SourceInfestedBlockMixin extends Block {
    protected SourceInfestedBlockMixin(BlockBehaviour.Properties properties) { super(properties); }
    @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object mite$preserveHostMap(Map<Object,Object> map,Object host,Object block,Operation<Object> original) {
        return (Object)this instanceof R196CopperspineBlock ? map.get(host) : original.call(map,host,block);
    }
    @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;destroyTime(F)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;"))
    private static BlockBehaviour.Properties mite$sourceHardnessAndUpdates(BlockBehaviour.Properties properties,float time,Operation<BlockBehaviour.Properties> original) {
        return original.call(properties,.75F).randomTicks();
    }
    @Inject(method="spawnInfestation",at=@At("HEAD"),cancellable=true)
    private void mite$sourceInhabitant(ServerLevel level,BlockPos pos,CallbackInfo ci) { R196Infestation.spawn(level,pos,defaultBlockState(),null); ci.cancel(); }
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) { R196Infestation.randomTick(level,pos,state); }
}
