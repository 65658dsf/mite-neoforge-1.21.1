package io.github.ningmeng123.mite.rules;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** R196 aqz/apr mineral tiers and placed-ore bit; modern variants keep their source tier. @author NingMeng123 */
public final class OreBlockRules {
    public static final BooleanProperty PLACED = BooleanProperty.create("mite_placed");
    private static final Set<String> VANILLA = Set.of("coal_ore", "copper_ore", "iron_ore", "gold_ore", "nether_gold_ore",
            "lapis_ore", "redstone_ore", "diamond_ore", "emerald_ore", "nether_quartz_ore");
    private static final Set<String> ADDED = Set.of("silver_ore", "mithril_ore", "adamantium_ore");
    private OreBlockRules() {}

    public static int minimumHarvestLevel(BlockState state) {
        return switch (kind(state)) {
            case "adamantium_ore", "diamond_ore" -> 4;
            case "mithril_ore", "emerald_ore" -> 3;
            case "" -> 0;
            default -> 2;
        };
    }

    public static boolean isManaged(BlockState state) {
        return !kind(state).isEmpty();
    }

    public static float hardness(BlockState state, float fallback) {
        return switch (kind(state)) {
            case "coal_ore" -> 1.2F;
            case "gold_ore", "nether_gold_ore" -> 2.4F;
            case "copper_ore", "silver_ore" -> 2.5F;
            case "mithril_ore" -> 3.5F;
            case "adamantium_ore" -> 4F;
            case "" -> fallback;
            default -> 3F;
        };
    }
    private static String kind(BlockState state) {
        // Resistance is queried while blocks are constructed; never dereference unbound deferred blocks here.
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return "";
        String name = id.getPath();
        if (name.startsWith("deepslate_")) name = name.substring("deepslate_".length());
        return (id.getNamespace().equals("minecraft") && VANILLA.contains(name))
                || (id.getNamespace().equals("mite") && ADDED.contains(name)) ? name : "";
    }
}
