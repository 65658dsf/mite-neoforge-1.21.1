package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** The real client event bus must discover the anvil tooltip subscriber without manual registration. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AnvilItemTooltipClientAudit implements DataProvider {
    private final Path output;
    private AnvilItemTooltipClientAudit(Path output) { this.output = output; }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.cropJadeAudit")) return;
        Path cropOutput = Path.of(System.getProperty("mite.cropJadeAuditOutput", "build/verification/crop-jade-audit.json"));
        event.getGenerator().addProvider(true, new AnvilItemTooltipClientAudit(cropOutput.resolveSibling("anvil-item-tooltip-audit.json")));
    }

    @Override public String getName() { return "Actual client item tooltip event bus registration without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("visual_appearance_passed", false);
        report.addProperty("manual_listener_registrations", 0);
        report.addProperty("environment_dist", FMLEnvironment.dist.name());
        report.addProperty("datagen_running", DatagenModLoader.isRunningDataGen());
        try {
            check(FMLEnvironment.dist == Dist.CLIENT && DatagenModLoader.isRunningDataGen(), "This fixture only activates the client datagen bus");
            // NeoForge creates the game bus with startShutdown(). Datagen only runs
            // CommonModLoader.begin; ClientModLoader normally starts it after loading.
            // Inspect the existing registrations, then reproduce only that public start.
            Field shutdown = field("shutdown");
            boolean originalShutdown = shutdown.getBoolean(NeoForge.EVENT_BUS);
            report.addProperty("event_bus_shutdown_before", originalShutdown);
            check(originalShutdown, "Datagen must leave the game bus shut down before this isolated fixture");
            Map<?, ?> registrations = (Map<?, ?>)field("listeners").get(NeoForge.EVENT_BUS);
            report.addProperty("existing_listener_targets", registrations.size());
            JsonArray targets = new JsonArray();
            for (String name : new String[]{"AnvilDurabilityDisplay", "CraftingTooltips", "MaterialDoorTooltips"}) {
                String expected = "io.github.ningmeng123.mite.client." + name;
                check(registrations.keySet().stream().anyMatch(target -> target instanceof Class<?> type && type.getName().equals(expected)),
                        "The actual game bus already contains the automatically discovered subscriber " + expected);
                targets.add(expected);
            }
            report.add("existing_automatic_tooltip_subscribers", targets);
            ItemStack anvil = new ItemStack(MiteFunctionalBlocks.ANVILS.get("copper").get());
            anvil.set(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), 12345);
            ItemStack before = anvil.copy();
            var dormantAnvil = post(anvil, TooltipFlag.NORMAL);
            var dormantEquipment = post(new ItemStack(Items.IRON_PICKAXE), TooltipFlag.NORMAL);
            var dormantDoor = post(new ItemStack(Items.COPPER_DOOR), TooltipFlag.NORMAL);
            report.addProperty("anvil_lines_before_start", dormantAnvil.size());
            report.addProperty("equipment_control_lines_before_start", dormantEquipment.size());
            report.addProperty("door_control_lines_before_start", dormantDoor.size());
            check(dormantAnvil.isEmpty() && dormantEquipment.isEmpty() && dormantDoor.isEmpty(),
                    "The shut-down datagen game bus must suppress both the new and existing tooltip subscribers");
            try {
                NeoForge.EVENT_BUS.start();
                report.addProperty("bus_only_activated_for_datagen_test", true);
                for (TooltipFlag flag : new TooltipFlag[]{TooltipFlag.NORMAL, TooltipFlag.ADVANCED}) {
                    var lines = post(anvil, flag);
                    var durability = lines.stream().filter(AnvilItemTooltipClientAudit::durabilityLine).toList();
                    report.addProperty(flag.isAdvanced() ? "advanced_anvil_durability_lines" : "normal_anvil_durability_lines", durability.size());
                    check(durability.size() == 1, "The automatically registered subscriber appends exactly one source durability line");
                    var text = (TranslatableContents)durability.getFirst().getContents();
                    check(Arrays.equals(text.getArgs(), new Object[]{186055, 198400}), "Actual event dispatch preserves exact remaining/maximum arguments");
                    check(ItemStack.matches(anvil, before), "The real tooltip event bus cannot mutate the carried anvil");
                }
                var equipment = post(new ItemStack(Items.IRON_PICKAXE), TooltipFlag.NORMAL);
                var door = post(new ItemStack(Items.COPPER_DOOR), TooltipFlag.NORMAL);
                report.add("equipment_control_translation_keys_after_start", keys(equipment));
                report.add("door_control_translation_keys_after_start", keys(door));
                check(equipment.stream().anyMatch(AnvilItemTooltipClientAudit::durabilityLine)
                                && equipment.stream().anyMatch(line -> translated(line, "tooltip.mite.quality")),
                        "The existing CraftingTooltips subscriber responds after the same lifecycle activation");
                check(door.stream().anyMatch(line -> translated(line, "tooltip.mite.metal_door.frame"))
                                && door.stream().anyMatch(line -> translated(line, "tooltip.mite.copper_door.manual")),
                        "The existing MaterialDoorTooltips subscriber responds after the same lifecycle activation");
                check(post(new ItemStack(Items.STONE), TooltipFlag.NORMAL).stream().noneMatch(AnvilItemTooltipClientAudit::durabilityLine),
                        "The event subscriber cannot add an anvil durability line to an ordinary stone item");
            } finally {
                // IEventBus deliberately exposes no shutdown API. Restore only the
                // original flag so other data providers retain the ordinary environment.
                shutdown.setBoolean(NeoForge.EVENT_BUS, originalShutdown);
                report.addProperty("event_bus_original_shutdown_restored", shutdown.getBoolean(NeoForge.EVENT_BUS) == originalShutdown);
            }
            report.addProperty("actual_neoforge_event_bus_dispatch", true);
            report.addProperty("automatic_subscriber_registration", true);
            report.addProperty("normal_and_advanced_append_once", true);
            report.addProperty("exact_arguments_and_item_components_preserved", true);
            report.addProperty("ordinary_item_ignored", true);
            report.addProperty("status", "passed_actual_client_tooltip_event_bus_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static ArrayList<Component> post(ItemStack stack, TooltipFlag flag) {
        var lines = new ArrayList<Component>();
        NeoForge.EVENT_BUS.post(new ItemTooltipEvent(stack, null, lines, flag, Item.TooltipContext.EMPTY));
        return lines;
    }

    private static boolean durabilityLine(Component line) {
        return translated(line, "tooltip.mite.durability");
    }

    private static boolean translated(Component line, String key) {
        return line.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
    }

    private static JsonArray keys(ArrayList<Component> lines) {
        JsonArray result = new JsonArray();
        for (Component line : lines) if (line.getContents() instanceof TranslatableContents text) result.add(text.getKey());
        return result;
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Field field = NeoForge.EVENT_BUS.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void write(JsonObject report) throws java.io.IOException {
        Path absolute = output.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
