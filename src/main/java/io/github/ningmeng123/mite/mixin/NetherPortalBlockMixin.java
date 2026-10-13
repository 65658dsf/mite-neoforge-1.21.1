package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.portal.MitePortalProperties;
import io.github.ningmeng123.mite.portal.MitePortals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep vanilla rendering/portal processor, replace only R196 routing and disabled spawns. @author NingMeng123 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin extends Block {
    protected NetherPortalBlockMixin(BlockBehaviour.Properties properties) { super(properties); }

    @Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
    private void mite$saveDestination(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(MitePortalProperties.DESTINATION, MitePortalProperties.RUNEGATE);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mite$defaultRoute(BlockBehaviour.Properties properties, CallbackInfo ci) {
        registerDefaultState(defaultBlockState().setValue(MitePortalProperties.DESTINATION, 0)
                .setValue(MitePortalProperties.RUNEGATE, false));
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void mite$disablePortalSpawns(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        ci.cancel(); // R196 aps.updateTick clears its candidate unconditionally, confirmed in bytecode.
    }

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void mite$eligibleTraveler(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (!MitePortals.canEnter(level, pos, entity)) ci.cancel();
    }

    @Inject(method = "getPortalTransitionTime", at = @At("HEAD"), cancellable = true)
    private void mite$sourceTransitionTime(ServerLevel level, Entity entity, CallbackInfoReturnable<Integer> cir) {
        BlockPos entry = entity.portalProcess == null ? entity.blockPosition() : entity.portalProcess.getEntryPosition();
        cir.setReturnValue(entity instanceof Player player && !player.getAbilities().invulnerable
                && !MitePortals.isInstantGate(level, entry) ? 80 : 0);
    }

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void mite$route(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<DimensionTransition> cir) {
        cir.setReturnValue(MitePortals.destination(level, entity, pos));
    }
}
