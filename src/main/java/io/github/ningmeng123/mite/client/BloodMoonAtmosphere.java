package io.github.ningmeng123.mite.client;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Match the daytime sky, clouds and air fog while retaining native fluids and night effects. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT)
public final class BloodMoonAtmosphere {
    private BloodMoonAtmosphere() {}

    public static LunarVisuals.Color rainColor(ClientLevel level) {
        return LunarVisuals.bloodRainTint(LunarCalendar.state(level.getDayTime(), level.dimension().equals(Level.OVERWORLD)));
    }

    public static Vec3 color(ClientLevel level, float partialTick, Vec3 original) {
        return color(level, partialTick, original, false);
    }

    private static Vec3 color(ClientLevel level, float partialTick, Vec3 original, boolean fog) {
        if (!level.dimension().equals(Level.OVERWORLD)) return original;
        var state = LunarCalendar.state(level.getDayTime(), true);
        float rain = level.getRainLevel(partialTick);
        if (!state.bloodPeriod() || state.night() || rain <= 0F) return original;
        var source = new LunarVisuals.Color((float)original.x, (float)original.y, (float)original.z);
        var result = fog ? LunarVisuals.bloodRainFog(state, rain, source) : LunarVisuals.bloodRainAtmosphere(state, rain, source);
        return new Vec3(result.red(), result.green(), result.blue());
    }

    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE
                || !(event.getCamera().getEntity().level() instanceof ClientLevel level)) return;
        Vec3 color = color(level, (float)event.getPartialTick(), new Vec3(event.getRed(), event.getGreen(), event.getBlue()), true);
        event.setRed((float)color.x); event.setGreen((float)color.y); event.setBlue((float)color.z);
    }
}
