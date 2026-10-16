package io.github.ningmeng123.mite.integration.jade;

import io.github.ningmeng123.mite.block.BlueberryBushBlock;
import java.util.List;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade discovers this optional integration; normal MITE initialization never loads it. @author NingMeng123 */
@WailaPlugin
public final class MiteJadePlugin implements IWailaPlugin {
    // These classes do not inherit from each other, so each target resolves the singleton exactly once.
    private static final List<Class<? extends Block>> TARGETS = List.of(
            CropBlock.class, FarmBlock.class, StemBlock.class, AttachedStemBlock.class,
            BlueberryBushBlock.class, CocoaBlock.class, NetherWartBlock.class,
            MushroomBlock.class, SugarCaneBlock.class, CactusBlock.class);

    @Override
    public void register(IWailaCommonRegistration registration) {
        TARGETS.forEach(target -> registration.registerBlockDataProvider(CropJadeProvider.INSTANCE, target));
        registration.registerBlockDataProvider(CampfireJadeProvider.INSTANCE, CampfireBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        TARGETS.forEach(target -> registration.registerBlockComponent(CropJadeProvider.INSTANCE, target));
        registration.registerBlockComponent(CampfireJadeProvider.INSTANCE, CampfireBlock.class);
        // Health and age are useful even when the server does not supply the optional Jade payload.
        registration.markAsClientFeature(CropJadeProvider.UID);
        registration.addTooltipCollectedCallback(CropJadeProvider::removeVanillaProgress);
    }
}
