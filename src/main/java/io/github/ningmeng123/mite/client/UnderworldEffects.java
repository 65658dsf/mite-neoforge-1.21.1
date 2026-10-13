package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/** Sunless source fog, using the actual progressing local dimension clock. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class UnderworldEffects extends DimensionSpecialEffects {
    public UnderworldEffects() { super(Float.NaN, true, SkyType.NONE, false, false); }
    @SubscribeEvent public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "underworld"), new UnderworldEffects());
    }
    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        var level = Minecraft.getInstance().level;
        long day = level == null ? 1 : Math.floorDiv(level.getDayTime() + 6000, 24000) + 1;
        double gray = Math.abs(Math.floorMod(day, 32) - 16) * .004;
        return new Vec3(gray, gray, gray);
    }
    @Override public boolean isFoggyAt(int x, int z) { return false; }
    @Override public float[] getSunriseColor(float time, float partialTick) { return null; }
}
