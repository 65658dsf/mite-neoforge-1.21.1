package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteFoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Source manure routes mature fungi independently from farmland fertilization. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class SpecialPlantFertilization {
    private SpecialPlantFertilization() {}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void useManure(PlayerInteractEvent.RightClickBlock event) {
        if(!event.getItemStack().is(MiteFoods.get("manure"))||event.isCanceled()||event.getUseItem().isFalse()
                ||event.getEntity().isSpectator()||event.getEntity().getCooldowns().isOnCooldown(event.getItemStack().getItem())) return;
        Level level=event.getLevel(); BlockPos pos=event.getPos(); BlockState state=level.getBlockState(pos);
        if((state.is(Blocks.MYCELIUM)||state.is(Blocks.GRASS_BLOCK))&&event.getFace()==Direction.UP) { pos=pos.above(); state=level.getBlockState(pos); }
        if(!(state.getBlock() instanceof MushroomBlock)||!FungalGrowth.canGiant(level,pos,state)) return;
        if(!level.mayInteract(event.getEntity(),pos)||!event.getEntity().mayUseItemAt(pos,Direction.UP,event.getItemStack())) return;
        if(level instanceof ServerLevel server) {
            // Clicking mycelium itself directly invokes apk.fertilize, which has no extra 50% gate.
            boolean directMycelium=level.getBlockState(event.getPos()).is(Blocks.MYCELIUM);
            if((directMycelium||server.random.nextFloat()<.5F) && ((MushroomBlock)state.getBlock()).growMushroom(server,pos,state,server.random))
                io.github.ningmeng123.mite.advancement.MiteAchievements.award(event.getEntity(),"supersizeMe");
            event.getItemStack().consume(1,event.getEntity()); level.levelEvent(2005,pos,0);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide)); event.setCanceled(true);
    }
}
