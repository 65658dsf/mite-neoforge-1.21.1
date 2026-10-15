package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.SpecialPlantRules;
import io.github.ningmeng123.mite.survival.SpecialPlantGrowth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StemBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Original dead-stem ochre tint works with modern stem and attached-stem models. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID,value=Dist.CLIENT)
public final class StemColors {
    private StemColors() {}
    @SubscribeEvent public static void blocks(RegisterColorHandlersEvent.Block event) {
        event.register((state,level,pos,index)->SpecialPlantRules.stemColor(state.hasProperty(StemBlock.AGE)?state.getValue(StemBlock.AGE):7,
                SpecialPlantGrowth.dead(state)),Blocks.MELON_STEM,Blocks.PUMPKIN_STEM,Blocks.ATTACHED_MELON_STEM,Blocks.ATTACHED_PUMPKIN_STEM);
    }
}
