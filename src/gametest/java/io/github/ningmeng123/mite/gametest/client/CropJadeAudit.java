package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Opt-in real Jade API audit without a Minecraft instance, world, rendering or UI. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CropJadeAudit implements DataProvider {
    private final Path output;

    private CropJadeAudit(Path output) { this.output = output; }

    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.cropJadeAudit")) return;
        Path report = Path.of(System.getProperty("mite.cropJadeAuditOutput", "build/verification/crop-jade-audit.json"));
        event.getGenerator().addProvider(true, new CropJadeAudit(report));
    }

    @Override public String getName() { return "Optional crop and anvil inspection against the real Jade API without rendering"; }

    @Override
    public CompletableFuture<?> run(CachedOutput unused) {
        JsonObject report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("minecraft_instance_created", false);
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("game_ui_automated", false);
        report.addProperty("visual_appearance_passed", false);
        try {
            check(ModList.get().isLoaded("jade"), "This opt-in API audit requires an external Jade mod in its isolated mods directory");
            // Do not expose any Jade signatures on this automatically discovered event subscriber.
            // Ordinary no-Jade data/server runs must never resolve the nested optional API implementation.
            RuntimeAudit.audit(report, Path.of(System.getProperty("mite.anvilJadeAuditOutput",
                    output.resolveSibling("anvil-jade-audit.json").toString())));
            report.addProperty("status", "passed_real_jade_api_and_payload_audit_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed"); report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        }
    }

    private void write(JsonObject report) throws java.io.IOException {
        Path absolute = output.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** This class is resolved only after the explicit audit option and loaded-Jade guard. */
    private static final class RuntimeAudit {
        private static final net.minecraft.core.BlockPos POSITION = new net.minecraft.core.BlockPos(12, 80, -5);
        private static final net.minecraft.resources.ResourceLocation DIMENSION = net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld");
        private static final net.minecraft.resources.ResourceLocation VANILLA = snownee.jade.api.JadeIds.MC_CROP_PROGRESS;
        private static final net.minecraft.resources.ResourceLocation UID = io.github.ningmeng123.mite.integration.jade.CropJadeProvider.UID;
        private static final io.github.ningmeng123.mite.integration.jade.CropJadeProvider PROVIDER = io.github.ningmeng123.mite.integration.jade.CropJadeProvider.INSTANCE;
        private static final java.util.List<String> TARGETS = java.util.List.of("CropBlock", "FarmBlock", "StemBlock", "AttachedStemBlock",
                "BlueberryBushBlock", "CocoaBlock", "NetherWartBlock", "MushroomBlock", "SugarCaneBlock", "CactusBlock");

        private static void audit(JsonObject report, Path anvilOutput) throws java.io.IOException {
            var pluginType = io.github.ningmeng123.mite.integration.jade.MiteJadePlugin.class;
            check(pluginType.isAnnotationPresent(snownee.jade.api.WailaPlugin.class), "Missing runtime WailaPlugin entry annotation");
            long discovered = ModList.get().getAllScanData().stream().flatMap(data -> data.getAnnotations().stream())
                    .filter(annotation -> annotation.annotationType().getClassName().equals(snownee.jade.api.WailaPlugin.class.getName())
                            && annotation.clazz().getClassName().equals(pluginType.getName())).count();
            check(discovered == 1, "NeoForge annotation scan must discover one MITE Jade plugin, found " + discovered);
            report.addProperty("plugin_scan_entries", discovered);
            report.addProperty("jade_version", ModList.get().getModContainerById("jade").orElseThrow().getModInfo().getVersion().toString());

            Registration registration = new Registration();
            var plugin = new io.github.ningmeng123.mite.integration.jade.MiteJadePlugin();
            plugin.register(proxy(snownee.jade.api.IWailaCommonRegistration.class, registration));
            plugin.registerClient(proxy(snownee.jade.api.IWailaClientRegistration.class, registration));
            registration.verify(report);
            check(PROVIDER.getDefaultPriority() == 100, "Crop provider must run after Jade's ordinary crop progress");
            report.addProperty("provider_priority", PROVIDER.getDefaultPriority());

            var wheat = net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 3);
            var empty = new net.minecraft.nbt.CompoundTag();
            check(PROVIDER.shouldRequestData(accessor(wheat, empty, false)), "Wheat without a block entity must request server data");
            check(!PROVIDER.shouldRequestData(accessor(wheat, empty, true)), "Fake display blocks must not request server data");
            check(!PROVIDER.shouldRequestData(accessor(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), empty, false)), "Non-crops must not request server data");
            report.addProperty("non_block_entity_crop_supported", true);

            JsonArray cases = new JsonArray(); report.add("tooltip_cases", cases);
            tooltipCase("healthy_wheat", wheat, java.util.Set.of("tooltip.mite.crop.health", "tooltip.mite.crop.health.healthy", "tooltip.mite.crop.growth"),
                    java.util.Set.of("tooltip.mite.crop.soil", "tooltip.mite.crop.condition"), registration.callback, cases);
            tooltipCase("blighted_wheat", wheat.setValue(io.github.ningmeng123.mite.entity.CubeCropBlight.BLIGHTED, true),
                    java.util.Set.of("tooltip.mite.crop.health.blighted", "tooltip.mite.crop.cure"), java.util.Set.of("tooltip.mite.crop.no_cure"), registration.callback, cases);
            tooltipCase("dead_wheat", wheat.setValue(io.github.ningmeng123.mite.entity.CubeCropBlight.DEAD, true),
                    java.util.Set.of("tooltip.mite.crop.health.dead", "tooltip.mite.crop.no_cure"), java.util.Set.of("tooltip.mite.crop.growth", "tooltip.mite.crop.cure"), registration.callback, cases);
            var onion = io.github.ningmeng123.mite.registry.MiteFoodPlants.ONIONS.get().defaultBlockState()
                    .setValue(io.github.ningmeng123.mite.block.OnionCropBlock.BLIGHTED, true);
            tooltipCase("blighted_onion", onion, java.util.Set.of("tooltip.mite.crop.health.blighted", "tooltip.mite.crop.cure"), java.util.Set.of(), registration.callback, cases);
            tooltipCase("mature_beetroot", net.minecraft.world.level.block.Blocks.BEETROOTS.defaultBlockState().setValue(net.minecraft.world.level.block.BeetrootBlock.AGE, 3),
                    java.util.Set.of("tooltip.mite.crop.mature", "tooltip.mite.crop.health.healthy"), java.util.Set.of("tooltip.mite.crop.growth"), registration.callback, cases);
            tooltipCase("attached_stem", net.minecraft.world.level.block.Blocks.ATTACHED_MELON_STEM.defaultBlockState(),
                    java.util.Set.of("tooltip.mite.crop.health.healthy", "tooltip.mite.crop.mature"), java.util.Set.of("tooltip.mite.crop.soil", "tooltip.mite.crop.condition"), registration.callback, cases);
            tooltipCase("dead_attached_stem", net.minecraft.world.level.block.Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState().setValue(io.github.ningmeng123.mite.entity.CubeCropBlight.DEAD, true),
                    java.util.Set.of("tooltip.mite.crop.health.dead", "tooltip.mite.crop.no_cure"), java.util.Set.of("tooltip.mite.crop.mature"), registration.callback, cases);
            tooltipCase("fertilized_soil", net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7)
                            .setValue(io.github.ningmeng123.mite.survival.FoodAgriculture.FERTILIZED, true),
                    java.util.Set.of("tooltip.mite.crop.soil", "tooltip.mite.crop.fertilized"), java.util.Set.of("tooltip.mite.crop.health", "tooltip.mite.crop.irrigation"), registration.callback, cases);
            tooltipCase("unfertilized_soil", net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState(),
                    java.util.Set.of("tooltip.mite.crop.soil", "tooltip.mite.crop.unfertilized"), java.util.Set.of("tooltip.mite.crop.health"), registration.callback, cases);
            tooltipCase("mushroom", net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM.defaultBlockState(),
                    java.util.Set.of("tooltip.mite.crop.giant_progress"), java.util.Set.of("tooltip.mite.crop.growth", "tooltip.mite.crop.condition"), registration.callback, cases);

            Tooltip disabled = new Tooltip(); disabled.addVanilla();
            registration.callback.onTooltipCollected(box(disabled), accessor(wheat, empty, false));
            check(disabled.tagged.containsKey(VANILLA), "Disabled MITE display must retain vanilla Jade progress");
            Tooltip unknownCane = new Tooltip(); unknownCane.addVanilla();
            var caneAccessor = accessor(net.minecraft.world.level.block.Blocks.SUGAR_CANE.defaultBlockState(), empty, false);
            PROVIDER.appendTooltip(unknownCane.api(), caneAccessor, null);
            registration.callback.onTooltipCollected(box(unknownCane), caneAccessor);
            check(unknownCane.tagged.containsKey(VANILLA) && !unknownCane.tagged.containsKey(UID) && unknownCane.components.isEmpty(),
                    "Cane without server data has no known growth details and must retain native Jade lines");
            report.addProperty("empty_cane_fallback_retains_vanilla_progress", true);
            Tooltip unrelated = new Tooltip(); unrelated.addVanilla(); unrelated.tagged.put(UID, 1);
            registration.callback.onTooltipCollected(box(unrelated), accessor(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), empty, false));
            check(unrelated.tagged.containsKey(VANILLA), "Unrelated block progress must not be removed by MITE's callback");
            report.addProperty("disabled_and_unrelated_vanilla_progress_retained", true);
            auditPayload(wheat, report);
            AnvilJadeAudit.audit(report, anvilOutput);
        }

        private static void tooltipCase(String name, net.minecraft.world.level.block.state.BlockState state, java.util.Set<String> required,
                java.util.Set<String> forbidden, snownee.jade.api.callback.JadeTooltipCollectedCallback callback, JsonArray cases) {
            Tooltip recording = new Tooltip(); recording.addVanilla();
            var accessor = accessor(state, new net.minecraft.nbt.CompoundTag(), false);
            PROVIDER.appendTooltip(recording.api(), accessor, null);
            check(!recording.tagged.containsKey(VANILLA), name + ": native progress should be removed during collection");
            check(!recording.components.isEmpty(), name + ": no crop details");
            check(recording.tagged.getOrDefault(UID, 0) == recording.components.size(), name + ": each crop line needs its own MITE tag");
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(); recording.components.forEach(component -> collectKeys(component, keys));
            check(!keys.contains("tooltip.mite.crop.condition.temperature"), name + ": retired temperature warnings must never reappear");
            for (String key : required) check(keys.contains(key), name + ": missing " + key);
            for (String key : forbidden) check(!keys.contains(key), name + ": invented/incorrect detail " + key);
            // Model a later-priority native provider or a user-customized sort order.
            recording.addVanilla(); callback.onTooltipCollected(box(recording), accessor);
            check(!recording.tagged.containsKey(VANILLA), name + ": final callback must remove late native progress");
            JsonObject fact = new JsonObject(); fact.addProperty("case", name); fact.addProperty("line_count", recording.components.size());
            JsonArray keyList = new JsonArray(); keys.forEach(keyList::add); fact.add("translation_keys", keyList); fact.addProperty("duplicate_vanilla_progress", false); cases.add(fact);
        }

        private static void auditPayload(net.minecraft.world.level.block.state.BlockState state, JsonObject report) {
            var base = io.github.ningmeng123.mite.integration.CropInspection.state(state);
            var detailed = new io.github.ningmeng123.mite.integration.CropInspection.Snapshot(base.kind(), base.health(), base.age(), base.maxAge(),
                    7, true, io.github.ningmeng123.mite.integration.CropInspection.Condition.GROWING, 15, 1, -1, -1);
            net.minecraft.nbt.CompoundTag packet = new net.minecraft.nbt.CompoundTag(), payload = new net.minecraft.nbt.CompoundTag();
            payload.putLong("position", POSITION.asLong()); payload.putString("block", "minecraft:wheat");
            payload.putString("dimension", "minecraft:overworld"); payload.putString("state", state.toString());
            payload.put("data", io.github.ningmeng123.mite.integration.CropInspection.save(detailed));
            packet.put(io.github.ningmeng123.mite.integration.jade.CropJadeProvider.DATA_KEY, payload);
            check(decode(state, packet, false).equals(detailed), "Exact target payload must supply real soil and growth conditions");
            check(decode(state, packet, true).equals(base), "Fake target must ignore a matching server packet");
            var legacy = packet.copy();
            legacy.getCompound(io.github.ningmeng123.mite.integration.jade.CropJadeProvider.DATA_KEY).getCompound("data").putString("condition", "TEMPERATURE");
            var migrated = decode(state, legacy, false);
            check(migrated.condition() == io.github.ningmeng123.mite.integration.CropInspection.Condition.NONE
                            && migrated.health() == detailed.health() && migrated.age() == detailed.age() && migrated.moisture() == 7,
                    "Old temperature payloads preserve actual crop and soil facts without the retired growth restriction");
            java.util.Set<String> legacyKeys = new java.util.LinkedHashSet<>();
            io.github.ningmeng123.mite.integration.CropInspection.lines(migrated)
                    .forEach(component -> collectKeys(component, legacyKeys));
            check(legacyKeys.contains("tooltip.mite.crop.growth") && legacyKeys.contains("tooltip.mite.crop.soil")
                            && !legacyKeys.contains("tooltip.mite.crop.condition") && !legacyKeys.contains("tooltip.mite.crop.condition.temperature"),
                    "Decoded legacy Jade payload lines keep crop facts visible without showing the obsolete temperature warning");
            JsonArray rejected = new JsonArray();
            java.util.Map<String, java.util.function.Consumer<net.minecraft.nbt.CompoundTag>> invalid = new java.util.LinkedHashMap<>();
            invalid.put("stale_position", data -> data.putLong("position", POSITION.above().asLong()));
            invalid.put("different_block", data -> data.putString("block", "minecraft:carrots"));
            invalid.put("different_dimension", data -> data.putString("dimension", "minecraft:the_nether"));
            invalid.put("stale_block_state", data -> data.putString("state", state.setValue(net.minecraft.world.level.block.CropBlock.AGE, 4).toString()));
            invalid.put("missing_context", data -> data.remove("dimension"));
            invalid.put("unsupported_version", data -> data.getCompound("data").putInt("version", 2));
            invalid.put("health_disagrees", data -> data.getCompound("data").putString("health", "BLIGHTED"));
            invalid.put("age_disagrees", data -> data.getCompound("data").putInt("age", 4));
            invalid.put("kind_disagrees", data -> data.getCompound("data").putString("kind", "SOIL"));
            invalid.put("invalid_moisture", data -> data.getCompound("data").putInt("moisture", 8));
            invalid.forEach((name, mutation) -> {
                var corrupt = packet.copy(); mutation.accept(corrupt.getCompound(io.github.ningmeng123.mite.integration.jade.CropJadeProvider.DATA_KEY));
                check(decode(state, corrupt, false).equals(base), name + ": stale/malformed data must fall back to local block state"); rejected.add(name);
            });
            check(decode(state, new net.minecraft.nbt.CompoundTag(), false).equals(base), "Missing optional server payload must retain local health and age");
            JsonObject fact = new JsonObject(); fact.addProperty("matching_target_accepted", true); fact.addProperty("fake_target_falls_back", true);
            fact.addProperty("obsolete_temperature_payload_warning_removed", true);
            fact.add("rejected_payloads", rejected); fact.addProperty("fallback_moisture", base.moisture()); fact.addProperty("fallback_condition", base.condition().name());
            report.add("server_payload_context", fact);
        }

        private static io.github.ningmeng123.mite.integration.CropInspection.Snapshot decode(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.nbt.CompoundTag data, boolean fake) {
            return io.github.ningmeng123.mite.integration.jade.CropJadeProvider.snapshot(state, POSITION, DIMENSION, data, fake);
        }

        private static snownee.jade.api.BlockAccessor accessor(net.minecraft.world.level.block.state.BlockState state, net.minecraft.nbt.CompoundTag data, boolean fake) {
            return proxy(snownee.jade.api.BlockAccessor.class, (target, method, args) -> switch (method.getName()) {
                case "getBlockState" -> state;
                case "getBlock" -> state.getBlock();
                case "getServerData" -> data;
                case "getPosition" -> POSITION;
                case "getBlockEntity" -> null;
                case "isFakeBlock" -> fake;
                case "getLevel" -> throw new IllegalStateException("State-only tooltip audit must never create or query a world");
                default -> defaultResult(target, method, args);
            });
        }

        private static snownee.jade.api.ui.IBoxElement box(Tooltip tooltip) {
            return proxy(snownee.jade.api.ui.IBoxElement.class, (target, method, args) -> method.getName().equals("getTooltip")
                    ? tooltip.api() : defaultResult(target, method, args));
        }

        private static void collectKeys(net.minecraft.network.chat.Component component, java.util.Set<String> keys) {
            if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated) {
                keys.add(translated.getKey());
                for (Object argument : translated.getArgs()) if (argument instanceof net.minecraft.network.chat.Component nested) collectKeys(nested, keys);
            }
            component.getSiblings().forEach(sibling -> collectKeys(sibling, keys));
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> api, java.lang.reflect.InvocationHandler handler) {
            return (T) java.lang.reflect.Proxy.newProxyInstance(CropJadeAudit.class.getClassLoader(), new Class<?>[]{api}, handler);
        }

        private static Object defaultResult(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            if (method.getName().equals("toString") && method.getParameterCount() == 0) return "CropJadeAudit proxy";
            if (method.getName().equals("hashCode") && method.getParameterCount() == 0) return System.identityHashCode(target);
            if (method.getName().equals("equals") && method.getParameterCount() == 1) return target == args[0];
            if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(target, method, args);
            if (method.getReturnType() == void.class) return null;
            throw new UnsupportedOperationException("Unexpected Jade API call " + method);
        }

        private static final class Tooltip implements java.lang.reflect.InvocationHandler {
            private final java.util.List<net.minecraft.network.chat.Component> components = new java.util.ArrayList<>();
            private final java.util.Map<net.minecraft.resources.ResourceLocation, Integer> tagged = new java.util.LinkedHashMap<>();
            private void addVanilla() { tagged.put(VANILLA, 1); }
            private snownee.jade.api.ITooltip api() { return proxy(snownee.jade.api.ITooltip.class, this); }
            @Override public Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
                if (method.getName().equals("add") && args.length == 2 && args[0] instanceof net.minecraft.network.chat.Component component
                        && args[1] instanceof net.minecraft.resources.ResourceLocation tag) {
                    components.add(component); tagged.merge(tag, 1, Integer::sum); return null;
                }
                if (method.getName().equals("remove")) return tagged.remove((net.minecraft.resources.ResourceLocation) args[0]) != null;
                if (method.getName().equals("get") && args.length == 1 && args[0] instanceof net.minecraft.resources.ResourceLocation tag)
                    return java.util.Collections.nCopies(tagged.getOrDefault(tag, 0), null);
                return defaultResult(target, method, args);
            }
        }

        private static final class Registration implements java.lang.reflect.InvocationHandler {
            private final java.util.List<Class<?>> server = new java.util.ArrayList<>(), client = new java.util.ArrayList<>();
            private int clientFeatures;
            private int campfireServer, campfireClient, anvilServer, anvilClient;
            private snownee.jade.api.callback.JadeTooltipCollectedCallback callback;
            @Override public Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
                if (method.getName().equals("registerBlockDataProvider") || method.getName().equals("registerBlockComponent")) {
                    boolean common = method.getName().equals("registerBlockDataProvider");
                    if (args[0] == io.github.ningmeng123.mite.integration.jade.CampfireJadeProvider.INSTANCE) {
                        check(args[1] == net.minecraft.world.level.block.CampfireBlock.class, "Campfire data/components target exactly CampfireBlock");
                        if (common) campfireServer++; else campfireClient++;
                        return null;
                    }
                    if (args[0] == io.github.ningmeng123.mite.integration.jade.AnvilJadeProvider.INSTANCE) {
                        check(args[1] == net.minecraft.world.level.block.AnvilBlock.class, "Anvil data/components target exactly the one AnvilBlock parent");
                        if (common) anvilServer++; else anvilClient++;
                        return null;
                    }
                    check(args[0] == PROVIDER, "Every crop target must use the same stateless singleton provider");
                    (method.getName().equals("registerBlockDataProvider") ? server : client).add((Class<?>) args[1]); return null;
                }
                if (method.getName().equals("markAsClientFeature")) { check(args[0].equals(UID), "Unexpected Jade client feature ID"); clientFeatures++; return null; }
                if (method.getName().equals("addTooltipCollectedCallback") && args.length == 2) {
                    check(callback == null, "Duplicate final tooltip callback"); callback = (snownee.jade.api.callback.JadeTooltipCollectedCallback) args[1]; return null;
                }
                return defaultResult(target, method, args);
            }
            private void verify(JsonObject report) {
                check(server.size() == 10 && server.equals(client), "Common/client target registrations must match the ten supported disjoint block classes");
                check(new java.util.HashSet<>(server).size() == 10, "Duplicate target registration");
                check(server.stream().map(Class::getSimpleName).toList().equals(TARGETS), "Unexpected target types");
                for (int a = 0; a < server.size(); a++) for (int b = a + 1; b < server.size(); b++)
                    check(!server.get(a).isAssignableFrom(server.get(b)) && !server.get(b).isAssignableFrom(server.get(a)), "Inherited targets would duplicate Jade output");
                check(clientFeatures == 1 && callback != null, "Local-state feature and final progress-cleanup callback must each be registered once");
                check(campfireServer == 1 && campfireClient == 1, "Campfire common/client providers are each registered exactly once");
                check(anvilServer == 1 && anvilClient == 1, "Anvil common/client providers are each registered exactly once, without inherited duplicates");
                JsonArray names = new JsonArray(); server.forEach(type -> names.add(type.getName())); report.add("registered_block_classes", names);
                report.addProperty("server_registrations", server.size()); report.addProperty("client_registrations", client.size());
                report.addProperty("targets_have_no_inheritance_overlap", true); report.addProperty("client_feature_registrations", clientFeatures);
                report.addProperty("final_tooltip_callbacks", 1);
                report.addProperty("campfire_server_and_client_registrations", 1);
                report.addProperty("anvil_server_and_client_registrations", 1);
            }
        }
    }
}
