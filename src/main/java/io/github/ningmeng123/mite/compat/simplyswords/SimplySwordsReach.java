package io.github.ningmeng123.mite.compat.simplyswords;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.Mite;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Bundled weapon lengths remain available without optional Better Combat classes. @author NingMeng123 */
public final class SimplySwordsReach {
    private record Range(double absolute, double bonus) {
        double combine(double itemRange) { return absolute != 0D ? absolute : itemRange + bonus; }
    }
    private static final Map<ResourceLocation, Range> RANGES = new ConcurrentHashMap<>();
    private static final ResourceLocation LEGACY_REACH = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "tool_reach");

    private SimplySwordsReach() {}

    public static Double baseReach(ItemStack stack, double itemRange) {
        var definition = SimplySwordsCompat.definition(stack);
        if (definition == null) return null;
        // Explicit main-hand range components replace metadata; obsolete MITE bonuses do not.
        boolean explicit = stack.getAttributeModifiers().modifiers().stream().anyMatch(entry ->
                entry.attribute().is(Attributes.ENTITY_INTERACTION_RANGE) && entry.slot().test(EquipmentSlot.MAINHAND)
                        && !entry.modifier().id().equals(LEGACY_REACH));
        Range metadata = explicit ? null : RANGES.computeIfAbsent(BuiltInRegistries.ITEM.getKey(stack.getItem()),
                id -> read(id, new HashSet<>()));
        // Native material scythes have registry entries but no JSON; Better Combat assigns its scythe preset.
        if (!explicit && metadata == null && definition.family().equals("scythe")) metadata = new Range(0D, .5D);
        if (!explicit && metadata == null) return null;
        double reach = explicit ? itemRange : metadata.combine(itemRange);
        return Double.isFinite(reach) && reach > 0D ? reach : 0D;
    }

    private static Range read(ResourceLocation id, Set<ResourceLocation> visited) {
        if (!visited.add(id)) return new Range(0D, 0D);
        try (var stream = SimplySwordsReach.class.getClassLoader().getResourceAsStream(
                "data/" + id.getNamespace() + "/weapon_attributes/" + id.getPath() + ".json")) {
            if (stream == null) return missingParent(id);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Range parent = json.has("parent") ? read(ResourceLocation.parse(json.get("parent").getAsString()), visited)
                    : new Range(0D, 0D);
            if (parent == null) parent = new Range(0D, 0D);
            var attributes = json.has("attributes") ? json.getAsJsonObject("attributes") : null;
            double absolute = attributes != null && attributes.has("attack_range")
                    ? attributes.get("attack_range").getAsDouble() : 0D;
            double bonus = attributes != null && attributes.has("range_bonus")
                    ? attributes.get("range_bonus").getAsDouble() : 0D;
            // Better Combat 2.4 inherits zero-valued fields, including Simply Swords' cutlass bonus.
            return new Range(absolute == 0D ? parent.absolute() : absolute, bonus == 0D ? parent.bonus() : bonus);
        } catch (java.io.IOException | RuntimeException exception) {
            Mite.LOGGER.warn("Could not inspect bundled weapon length {}", id, exception);
            return new Range(0D, 0D);
        }
    }

    /** Only the Better Combat 2.4 parents referenced by Simply Swords 1.70 need absent-mod defaults. */
    private static Range missingParent(ResourceLocation id) {
        if (!id.getNamespace().equals("bettercombat")) return null;
        return new Range(0D, switch (id.getPath()) {
            case "claymore", "trident" -> .25D;
            case "glaive", "scythe" -> .5D;
            case "sickle" -> -.25D;
            case "spear" -> 1D;
            default -> 0D;
        });
    }
}
