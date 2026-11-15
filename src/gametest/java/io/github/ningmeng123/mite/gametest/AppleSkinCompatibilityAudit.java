package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Opt-in, test-source-only transformed-class audit; never creates or initializes Minecraft. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class AppleSkinCompatibilityAudit {
    private static final String SATURATION = "squeek.appleskin.client.HUDOverlayHandler$SaturationOverlay";
    private static final String DRAW = "squeek.appleskin.client.HUDOverlayHandler";
    private static final String HEALTH = "squeek.appleskin.client.HUDOverlayHandler$HealthOverlay";
    private static final String QUERY = "squeek.appleskin.helpers.FoodHelper$QueriedFoodResult";
    private static final String RENDER = "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;IIII)V";
    private AppleSkinCompatibilityAudit() {}

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!Boolean.getBoolean("mite.appleSkinAudit")) return;
        Path workspace = event.getGenerator().getPackOutput().getOutputFolder().toAbsolutePath().resolve("../../..").normalize();
        Path report = Path.of(System.getProperty("mite.appleSkinAuditOutput",
                workspace.resolve("build/verification/appleskin-compatibility-audit.json").toString()));
        event.getGenerator().addProvider(true, new AuditProvider(report));
    }

    private record AuditProvider(Path report) implements DataProvider {
        @Override public String getName() { return "MITE optional AppleSkin bytecode compatibility audit"; }
        @Override public CompletableFuture<?> run(CachedOutput output) {
            JsonObject result = new JsonObject();
            var loader = AppleSkinCompatibilityAudit.class.getClassLoader();
            try {
                Class.forName("net.minecraft.client.gui.Gui", false, loader);
                ClassNode gui = readExport("net.minecraft.client.gui.Gui");
                var food = method(gui, "renderFood", "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V");
                calls(food, "mite$foodContainerCount", 1);
                calls(food, "mite$foodIconPosition", 3);
                calls(method(gui, "renderFoodLevel", "(Lnet/minecraft/client/gui/GuiGraphics;)V"), "mite$foodReservedHeight", 1);
                result.addProperty("vanilla_food_icons_wrap_into_rows", true);
                result.addProperty("vanilla_food_reserves_additional_air_bar_height", true);
            } catch (ReflectiveOperationException | IOException error) {
                throw new IllegalStateException("Headless food HUD compatibility audit failed", error);
            }
            if (loader.getResource(SATURATION.replace('.', '/') + ".class") == null) {
                result.addProperty("status", "skipped: AppleSkin is absent");
                return DataProvider.saveStable(output, result, report);
            }
            try {
                // false is essential: loading forces Mixin transformation, but must not run
                // AppleSkin/Minecraft static initialization in this headless data generator.
                Class.forName(SATURATION, false, loader);
                Class.forName(DRAW, false, loader);
                Class.forName(HEALTH, false, loader);
                var queried = Class.forName(QUERY, false, loader);
                queried.getDeclaredMethod("mite$queriedStack");
                queried.getDeclaredMethod("mite$queriedProperties");
                var render = method(readExport(SATURATION), "render", RENDER);
                calls(render, "mite$nutritionCurrent", 3);
                calls(render, "mite$independentCapacity", 1);
                calls(render, "mite$noSatiationInNutritionCap", 1);
                calls(render, "mite$qualifiedNutritionPreview", 1);
                absent(render, "net/minecraft/world/food/FoodData", "getSaturationLevel");
                absent(render, "net/minecraft/world/food/FoodData", "getFoodLevel");
                absent(render, "net/minecraft/world/food/FoodProperties", "nutrition");
                for (var instruction : render.instructions) if (instruction instanceof FieldInsnNode field
                        && field.owner.equals(QUERY.replace('.', '/')) && field.name.equals("modifiedFoodProperties"))
                    throw new IllegalStateException("The original forecast properties read survived its redirect");
                var draw = method(readExport(DRAW), "drawSaturationOverlay", "(FFLnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;IIFI)V");
                calls(draw, "mite$boundedNutritionRow", 1);
                calls(draw, "mite$foodOffsetCount", 1);
                calls(draw, "mite$foodColumnOffset", 1);
                calls(draw, "mite$nutritionIconRow", 1);
                var hunger = method(readExport(DRAW), "drawHungerOverlay", "(IILnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;IIFZI)V");
                calls(hunger, "mite$boundedFoodPreview", 1);
                calls(hunger, "mite$foodOffsetCount", 1);
                calls(hunger, "mite$foodColumnOffset", 1);
                calls(hunger, "mite$foodPreviewIconRow", 2);
                var health = method(readExport(HEALTH), "shouldRenderOverlay",
                        "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/gui/GuiGraphics;I)Z");
                calls(health, "mite$disableModernHealingPreview", 1);
                result.addProperty("status", "passed: transformed bytecode, no target initialization");
                result.addProperty("nutrition_current_reads_replaced", 3);
                result.addProperty("independent_capacity_reads_replaced", 1);
                result.addProperty("satiation_cap_addends_replaced", 1);
                result.addProperty("qualified_forecast_reads_replaced", 1);
                result.addProperty("drawing_capacity_limits_replaced", 1);
                result.addProperty("food_preview_capacity_limits_replaced", 1);
                result.addProperty("nutrition_and_food_previews_share_multirow_offsets", true);
                result.addProperty("modern_healing_preview_disabled", 1);
                result.addProperty("queried_food_bridge_verified", true);
                return DataProvider.saveStable(output, result, report);
            } catch (ReflectiveOperationException | IOException error) {
                throw new IllegalStateException("Headless AppleSkin compatibility audit failed", error);
            }
        }
    }

    private static ClassNode readExport(String target) throws IOException {
        Path exported = Path.of(".mixin.out", "class", target.replace('.', '/') + ".class");
        if (!Files.isRegularFile(exported)) throw new IllegalStateException(
                "Missing transformed " + target + "; enable -Dmixin.debug.export=true with -Dmite.appleSkinAudit=true");
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(exported)).accept(node, 0);
        return node;
    }
    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing pinned method " + type.name + "." + name + descriptor));
    }
    private static void calls(MethodNode method, String hook, int expected) {
        int count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.contains(hook)) count++;
        if (count != expected) throw new IllegalStateException(hook + " expected " + expected + " transformed calls, found " + count);
    }
    private static void absent(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name))
            throw new IllegalStateException("Original misleading AppleSkin read remains: " + owner + "." + name);
    }
}
