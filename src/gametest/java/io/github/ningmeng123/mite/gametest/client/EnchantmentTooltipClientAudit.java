package io.github.ningmeng123.mite.gametest.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.client.GearProgressionTooltips;
import io.github.ningmeng123.mite.progression.GearModifier;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Shift-gated growth descriptions on the real client tooltip event bus. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class EnchantmentTooltipClientAudit implements DataProvider {
    private static final String HINT = "tooltip.mite.enchantment_descriptions_shift";
    private final Path output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    private EnchantmentTooltipClientAudit(Path output, CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @SubscribeEvent public static void gather(GatherDataEvent event) {
        if (!event.includeClient() || !Boolean.getBoolean("mite.enchantmentClientAudit")) return;
        Path enchantmentOutput = Path.of(System.getProperty("mite.enchantmentClientAuditOutput",
                "build/verification/enchantment-client-audit.json"));
        event.getGenerator().addProvider(true, new EnchantmentTooltipClientAudit(
                enchantmentOutput.resolveSibling("enchantment-tooltip-client-audit.json"), event.getLookupProvider()));
    }

    @Override public String getName() { return "Shift-gated enchantment and growth descriptions without rendering"; }

    @Override public CompletableFuture<?> run(CachedOutput unused) {
        var report = new JsonObject();
        report.addProperty("author", "NingMeng123");
        report.addProperty("world_created", false);
        report.addProperty("opengl_or_render_invoked", false);
        report.addProperty("visual_appearance_tested", false);
        var cases = new JsonArray();
        report.add("cases", cases);
        Language originalLanguage = Language.getInstance();
        try {
            HolderLookup.Provider lookup = registries.join();
            for (String locale : List.of("zh_cn", "en_us")) {
                Map<String, String> translations = translations(locale);
                Language.inject(language(translations, originalLanguage));
                for (GearModifier modifier : GearModifier.values()) {
                    String nameKey = ((TranslatableContents)modifier.displayName().getContents()).getKey();
                    check(translations.containsKey(nameKey + ".desc"), "Modifier description exists in " + locale + ": " + nameKey);
                    verify(stack(Map.of(modifier.id(), 2)), lookup, List.of(nameKey + ".desc"), locale, cases);
                }
                var screenshotAffixes = Map.of(GearModifier.ARMOR_BLESSED.id(), 1,
                        GearModifier.ARMOR_DURABILITY.id(), 1, GearModifier.ARMOR_PROJECTILE_PROTECTION.id(), 1);
                ItemStack screenshot = stack(screenshotAffixes);
                List<String> screenshotDescriptions = List.of("modifier.mite.armor.blessed.desc",
                        "modifier.mite.armor.durability.desc", "modifier.mite.armor.projectile_protection.desc");
                verify(screenshot, lookup, screenshotDescriptions, locale, cases);
                verifyLegacy(lookup, translations, originalLanguage, locale, cases);
                verifyExistingHint(lookup, translations, originalLanguage);
                verifyUnknown(lookup);
                verifyEventBus(screenshot, lookup, screenshotDescriptions, report);
            }
            auditOptionalKeybindMixin(report);
            report.addProperty("modifier_count_per_locale", GearModifier.values().length);
            report.addProperty("tooltip_flags_control_shift_state", true);
            report.addProperty("existing_enchantment_description_hint_reused", true);
            report.addProperty("partial_registry_context_supported", true);
            report.addProperty("held_item_components_preserved", true);
            report.addProperty("status", "passed_shift_gated_client_tooltips_without_rendering");
            write(report);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            report.addProperty("status", "failed");
            report.addProperty("failure", failure.toString());
            try { write(report); } catch (Exception secondary) { failure.addSuppressed(secondary); }
            return CompletableFuture.failedFuture(failure);
        } finally {
            Language.inject(originalLanguage);
        }
    }

    private static void verifyLegacy(HolderLookup.Provider lookup, Map<String, String> translations,
            Language fallback, String locale, JsonArray cases) {
        verify(stack(Map.of(ResourceLocation.parse("mite:stun"), 2)), lookup,
                List.of("enchantment.mite.stun.desc"), locale, cases);
        for (String suffix : List.of("desc", "description", "info")) {
            var fixture = new LinkedHashMap<>(translations);
            String key = "enchantment.minecraft.fire_protection." + suffix + ".2";
            fixture.put(key, "Localized level-two fire protection fixture");
            Language.inject(language(fixture, fallback));
            verify(stack(Map.of(ResourceLocation.parse("minecraft:fire_protection"), 2)), lookup,
                    List.of(key), locale, cases);
        }
        Language.inject(language(translations, fallback));
    }

    private static void verifyExistingHint(HolderLookup.Provider lookup, Map<String, String> translations,
            Language fallback) {
        var fixture = new LinkedHashMap<>(translations);
        fixture.put("enchdesc.activate.message", "Existing localized Shift hint");
        Language.inject(language(fixture, fallback));
        try {
            ItemStack stack = stack(Map.of(GearModifier.ARMOR_BLESSED.id(), 1));
            var lines = new ArrayList<Component>();
            lines.add(Component.translatable("enchdesc.activate.message"));
            GearProgressionTooltips.append(new ItemTooltipEvent(stack, null, lines, flags(false), Item.TooltipContext.of(lookup)));
            check(hints(lines) == 1, "An existing Enchantment Descriptions hint is retained exactly once");
            check(count(lines, "modifier.mite.armor.blessed.desc") == 0, "Reusing the existing hint keeps descriptions collapsed");
            List<Component> expanded = tooltip(stack, lookup, true, false);
            check(hints(expanded) == 0 && count(expanded, "modifier.mite.armor.blessed.desc") == 1,
                    "Shift expands descriptions while an Enchantment Descriptions hint translation is available");
        } finally {
            Language.inject(language(translations, fallback));
        }
    }

    private static void verify(ItemStack stack, HolderLookup.Provider lookup, List<String> descriptions,
            String locale, JsonArray cases) {
        ItemStack before = stack.copy();
        List<Component> collapsed = tooltip(stack, lookup, false, false);
        List<Component> expanded = tooltip(stack, lookup, true, false);
        check(count(collapsed, "tooltip.mite.gear_affix") == GearProgression.state(stack).affixes().size(),
                "Collapsed tooltip keeps every affix name and rank");
        check(count(expanded, "tooltip.mite.gear_affix") == GearProgression.state(stack).affixes().size(),
                "Expanded tooltip keeps every affix name and rank");
        for (String key : descriptions) {
            check(count(collapsed, key) == 0, "Description is hidden before Shift: " + key);
            check(count(expanded, key) == 1, "Shift reveals each description exactly once: " + key);
        }
        check(hints(collapsed) == 1, "Collapsed affixes advertise Shift exactly once");
        check(hints(expanded) == 0, "Expanded affixes have their effect descriptions");
        check(ItemStack.matches(before, stack), "Tooltip generation preserves the entire hovered stack");
        var fact = new JsonObject();
        fact.addProperty("locale", locale);
        fact.addProperty("affix_count", GearProgression.state(stack).affixes().size());
        fact.addProperty("expanded_description_count", descriptions.size());
        var keys = new JsonArray();
        descriptions.forEach(keys::add);
        fact.add("description_keys", keys);
        cases.add(fact);
    }

    private static void verifyUnknown(HolderLookup.Provider lookup) {
        String unknown = "unknown:missing_enchantment";
        ItemStack unknownStack = stack(Map.of(ResourceLocation.parse(unknown), 1));
        List<Component> expanded = tooltip(unknownStack, lookup, true, false);
        check(expanded.stream().anyMatch(line -> line.getString().contains(unknown)), "Unknown affix retains its ID fallback");
        var noContext = new ArrayList<Component>();
        GearProgressionTooltips.append(new ItemTooltipEvent(unknownStack, null, noContext, flags(true), Item.TooltipContext.EMPTY));
        check(noContext.stream().anyMatch(line -> line.getString().contains(unknown)), "Missing registry context retains its ID fallback");
        HolderLookup.Provider partialLookup = HolderLookup.Provider.create(Stream.empty());
        List<Component> partial = tooltip(unknownStack, partialLookup, true, false);
        check(partial.stream().anyMatch(line -> line.getString().contains(unknown)), "A registry context without enchantments retains its ID fallback");
    }

    private static void verifyEventBus(ItemStack stack, HolderLookup.Provider lookup, List<String> descriptions,
            JsonObject report) throws ReflectiveOperationException {
        Field shutdown = NeoForge.EVENT_BUS.getClass().getDeclaredField("shutdown");
        shutdown.setAccessible(true);
        boolean originalShutdown = shutdown.getBoolean(NeoForge.EVENT_BUS);
        try {
            if (originalShutdown) NeoForge.EVENT_BUS.start();
            List<Component> collapsed = tooltip(stack, lookup, false, true);
            List<Component> expanded = tooltip(stack, lookup, true, true);
            check(count(expanded, "tooltip.mite.gear_affixes") == 1, "Client event bus automatically discovers the growth tooltip subscriber");
            for (String key : descriptions) {
                check(count(collapsed, key) == 0 && count(expanded, key) == 1, "Real event dispatch respects Shift for " + key);
            }
            report.addProperty("actual_client_event_bus_dispatch", true);
        } finally {
            shutdown.setBoolean(NeoForge.EVENT_BUS, originalShutdown);
        }
    }

    private static ItemStack stack(Map<ResourceLocation, Integer> affixes) {
        ItemStack stack = new ItemStack(Items.IRON_CHESTPLATE);
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(GearProgress.UNASSIGNED, 3, 74000, affixes, 1));
        return stack;
    }

    private static List<Component> tooltip(ItemStack stack, HolderLookup.Provider lookup, boolean shift, boolean eventBus) {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal("Existing tooltip content"));
        var event = new ItemTooltipEvent(stack, null, lines, flags(shift), Item.TooltipContext.of(lookup));
        if (eventBus) NeoForge.EVENT_BUS.post(event);
        else GearProgressionTooltips.append(event);
        check(lines.getFirst().getString().equals("Existing tooltip content"), "Existing tooltip lines are preserved");
        return lines;
    }

    private static TooltipFlag flags(boolean shift) {
        return new TooltipFlag() {
            @Override public boolean isAdvanced() { return false; }
            @Override public boolean isCreative() { return false; }
            @Override public boolean hasControlDown() { return false; }
            @Override public boolean hasShiftDown() { return shift; }
            @Override public boolean hasAltDown() { return false; }
        };
    }

    private static long hints(List<Component> lines) {
        return lines.stream().filter(line -> contains(line, HINT) || contains(line, "enchdesc.activate.message")).count();
    }

    private static long count(List<Component> lines, String key) {
        return lines.stream().filter(line -> contains(line, key)).count();
    }

    private static boolean contains(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents text) {
            if (text.getKey().equals(key)) return true;
            for (Object argument : text.getArgs()) if (argument instanceof Component nested && contains(nested, key)) return true;
        }
        return component.getSiblings().stream().anyMatch(sibling -> contains(sibling, key));
    }

    private static Map<String, String> translations(String locale) throws Exception {
        var result = new LinkedHashMap<String, String>();
        try (var stream = EnchantmentTooltipClientAudit.class.getClassLoader().getResourceAsStream("assets/mite/lang/" + locale + ".json")) {
            check(stream != null, "MITE language resource exists for " + locale);
            Language.loadFromJson(stream, result::put);
        }
        return result;
    }

    private static Language language(Map<String, String> translations, Language fallback) {
        return new Language() {
            @Override public String getOrDefault(String key, String defaultValue) {
                return translations.getOrDefault(key, fallback.getOrDefault(key, defaultValue));
            }
            @Override public boolean has(String key) { return translations.containsKey(key) || fallback.has(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return fallback.getVisualOrder(text); }
        };
    }

    private static void auditOptionalKeybindMixin(JsonObject report) throws Exception {
        String name = "net.darkhax.enchdesc.common.impl.EnchdescMod";
        try { Class.forName(name, false, EnchantmentTooltipClientAudit.class.getClassLoader()); }
        catch (ClassNotFoundException absent) {
            report.addProperty("optional_enchantment_descriptions_loaded", false);
            return;
        }
        report.addProperty("optional_enchantment_descriptions_loaded", true);
        Path exported = Path.of(".mixin.out", "class", name.replace('.', '/') + ".class");
        check(Files.isRegularFile(exported), "Optional description class has an actual transformed export");
        var type = new ClassNode();
        new ClassReader(Files.readAllBytes(exported)).accept(type, 0);
        MethodNode gate = type.methods.stream().filter(method -> merged(method, "io.github.ningmeng123.mite.mixin.client.EnchantmentDescriptionKeyMixin"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Optional Shift-key Mixin was applied"));
        boolean checksShift = false, returnsValue = false;
        for (var instruction : gate.instructions) if (instruction instanceof MethodInsnNode call) {
            if (call.owner.equals("net/minecraft/client/gui/screens/Screen") && call.name.equals("hasShiftDown")) checksShift = true;
            if (call.name.equals("setReturnValue")) returnsValue = true;
        }
        check(checksShift && returnsValue, "Merged optional description gate returns the actual Shift-key state");
        report.addProperty("optional_description_gate_uses_actual_shift_state", true);
    }

    private static boolean merged(MethodNode method, String mixin) {
        return merged(method.visibleAnnotations, mixin) || merged(method.invisibleAnnotations, mixin);
    }

    private static boolean merged(List<AnnotationNode> annotations, String mixin) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null)
            for (int index = 0; index + 1 < annotation.values.size(); index += 2)
                if (annotation.values.get(index).equals("mixin") && annotation.values.get(index + 1).equals(mixin)) return true;
        return false;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void write(JsonObject report) throws Exception {
        Path target = output.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
    }
}
