package io.github.ningmeng123.mite.rules;

import java.util.HashMap;
import java.util.Map;

/** Pure R196 block-hardness lookup, without Minecraft class loading. @author NingMeng123 */
public final class BlockHardnessRules {
    private static final Map<String, Float> REGISTERED = registered();
    private static final Map<String, Float> SLABS = slabs();

    private BlockHardnessRules() {}

    /** Pure table lookup for registration-oracle tests; no registry initialization is required. */
    public static float effectiveHardness(String namespace, String path, boolean doubleSlab, float fallback) {
        if (fallback < 0F || !"minecraft".equals(namespace)) return fallback;
        Float slab = SLABS.get(path);
        if (slab != null) return slab * (doubleSlab ? 2F : 1F);
        return REGISTERED.getOrDefault(path, fallback);
    }

    public static Map<String, Float> registeredHardness() { return REGISTERED; }
    public static Map<String, Float> slabHardness() { return SLABS; }

    /** R196 placement queries metadata zero of the legacy block, not its placed state. */
    public static float placementHardness(String namespace, String path, float fallback) {
        if (fallback < 0F || !"minecraft".equals(namespace)) return fallback;
        return switch (path) {
            case "snow" -> .05F;
            case "vine" -> .2F;
            case "oak_slab", "spruce_slab", "birch_slab", "jungle_slab" -> .4F;
            // All these modern IDs were subtypes of old block 44; metadata zero means stone.
            case "smooth_stone_slab", "sandstone_slab", "cobblestone_slab", "brick_slab",
                    "stone_brick_slab", "nether_brick_slab", "quartz_slab" -> 1.2F;
            default -> fallback;
        };
    }

    private static Map<String, Float> registered() {
        Map<String, Float> values = new HashMap<>();
        put(values, 2.4F, "stone");
        put(values, 0.5F, "grass_block");
        put(values, 0.5F, "dirt");
        put(values, 2.0F, "cobblestone");
        put(values, 0.8F, "oak_planks", "spruce_planks", "birch_planks", "jungle_planks");
        put(values, 0.02F, "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling");
        put(values, -1.0F, "bedrock");
        put(values, 0.4F, "sand");
        put(values, 0.6F, "gravel");
        put(values, 2.4F, "gold_ore");
        put(values, 3.0F, "iron_ore");
        put(values, 1.2F, "coal_ore");
        put(values, 1.2F, "oak_log", "spruce_log", "birch_log", "jungle_log");
        put(values, 0.2F, "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves");
        put(values, 0.6F, "sponge");
        put(values, 2.0F, "glass");
        put(values, 3.0F, "lapis_ore");
        put(values, 3.0F, "lapis_block");
        put(values, 3.5F, "dispenser");
        put(values, 0.8F, "sandstone", "chiseled_sandstone", "cut_sandstone");
        put(values, 0.8F, "note_block");
        put(values, 0.2F, "red_bed");
        put(values, 0.7F, "powered_rail");
        put(values, 0.7F, "detector_rail");
        put(values, 0.5F, "sticky_piston");
        put(values, 0.1F, "cobweb");
        put(values, 0.02F, "short_grass", "fern");
        put(values, 0.02F, "dead_bush");
        put(values, 0.5F, "piston");
        put(values, 0.5F, "piston_head");
        put(values, 0.8F, "white_wool", "orange_wool", "magenta_wool", "light_blue_wool", "yellow_wool", "lime_wool", "pink_wool", "gray_wool", "light_gray_wool", "cyan_wool", "purple_wool", "blue_wool", "brown_wool", "green_wool", "red_wool", "black_wool");
        put(values, -1.0F, "moving_piston");
        put(values, 0.0F, "dandelion");
        put(values, 0.0F, "poppy", "blue_orchid", "allium", "orange_tulip", "oxeye_daisy");
        put(values, 0.0F, "brown_mushroom");
        put(values, 0.0F, "red_mushroom");
        put(values, 4.8F, "gold_block");
        put(values, 9.6F, "iron_block");
        put(values, 2.0F, "bricks");
        put(values, 1.0F, "tnt");
        put(values, 1.5F, "bookshelf");
        put(values, 2.0F, "mossy_cobblestone");
        put(values, 2.4F, "obsidian");
        put(values, 0.0F, "torch", "wall_torch");
        put(values, 0.0F, "fire");
        put(values, 3.0F, "spawner");
        put(values, 0.8F, "oak_stairs");
        put(values, 0.2F, "chest");
        put(values, 0.0F, "redstone_wire");
        put(values, 3.0F, "diamond_ore");
        put(values, 19.2F, "diamond_block");
        put(values, 1.6F, "crafting_table");
        put(values, 0.6F, "farmland");
        put(values, 2.0F, "furnace");
        put(values, 0.1F, "oak_sign");
        put(values, 0.25F, "oak_door");
        put(values, 0.5F, "ladder");
        put(values, 0.7F, "rail");
        put(values, 2.0F, "cobblestone_stairs");
        put(values, 0.1F, "oak_wall_sign");
        put(values, 0.5F, "lever");
        put(values, 0.5F, "stone_pressure_plate");
        put(values, 4.0F, "iron_door");
        put(values, 0.5F, "oak_pressure_plate");
        put(values, 3.0F, "redstone_ore");
        put(values, 0.0F, "redstone_torch", "redstone_wall_torch");
        put(values, 0.5F, "stone_button");
        put(values, 1.0F, "ice");
        put(values, 0.4F, "snow_block");
        put(values, 0.4F, "cactus");
        put(values, 0.8F, "clay");
        put(values, 0.08F, "sugar_cane");
        put(values, 2.0F, "jukebox");
        put(values, 0.4F, "oak_fence");
        put(values, 0.6F, "pumpkin", "carved_pumpkin");
        put(values, 1.6F, "netherrack");
        put(values, 0.5F, "soul_sand");
        put(values, 0.3F, "glowstone");
        put(values, -1.0F, "nether_portal");
        put(values, 1.0F, "jack_o_lantern");
        put(values, 0.5F, "cake");
        put(values, 0.0F, "repeater");
        put(values, 0.8F, "oak_trapdoor");
        put(values, 0.75F, "infested_stone", "infested_cobblestone", "infested_stone_bricks");
        put(values, 1.5F, "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks");
        put(values, 0.2F, "brown_mushroom_block");
        put(values, 0.2F, "red_mushroom_block");
        put(values, 5.0F, "iron_bars");
        put(values, 0.1F, "glass_pane");
        put(values, 0.6F, "melon");
        put(values, 0.02F, "pumpkin_stem", "attached_pumpkin_stem");
        put(values, 0.02F, "melon_stem", "attached_melon_stem");
        put(values, 2.0F, "oak_fence_gate");
        put(values, 2.0F, "brick_stairs");
        put(values, 1.5F, "stone_brick_stairs");
        put(values, 0.6F, "mycelium");
        put(values, 0.02F, "lily_pad");
        put(values, 2.0F, "nether_bricks");
        put(values, 2.0F, "nether_brick_fence");
        put(values, 2.0F, "nether_brick_stairs");
        put(values, 0.0F, "nether_wart");
        put(values, 2.4F, "enchanting_table");
        put(values, 0.5F, "brewing_stand");
        put(values, 2.0F, "cauldron", "water_cauldron");
        put(values, -1.0F, "end_portal");
        put(values, -1.0F, "end_portal_frame");
        put(values, 3.0F, "end_stone");
        put(values, 3.0F, "dragon_egg");
        put(values, 0.3F, "redstone_lamp");
        put(values, 0.2F, "cocoa");
        put(values, 0.8F, "sandstone_stairs");
        put(values, 3.0F, "emerald_ore");
        put(values, 22.5F, "ender_chest");
        put(values, 0.0F, "tripwire_hook");
        put(values, 0.0F, "tripwire");
        put(values, 9.6F, "emerald_block");
        put(values, 0.8F, "spruce_stairs");
        put(values, 0.8F, "birch_stairs");
        put(values, 0.8F, "jungle_stairs");
        put(values, -1.0F, "command_block");
        put(values, 3.0F, "beacon");
        put(values, 1.5F, "cobblestone_wall", "mossy_cobblestone_wall");
        put(values, 0.0F, "flower_pot", "potted_oak_sapling", "potted_spruce_sapling", "potted_birch_sapling", "potted_jungle_sapling", "potted_fern", "potted_dead_bush", "potted_dandelion", "potted_poppy", "potted_brown_mushroom", "potted_red_mushroom", "potted_cactus");
        put(values, 0.5F, "oak_button");
        put(values, 1.0F, "skeleton_skull", "skeleton_wall_skull", "wither_skeleton_skull", "wither_skeleton_wall_skull", "zombie_head", "zombie_wall_head", "player_head", "player_wall_head", "creeper_head", "creeper_wall_head");
        put(values, 9.6F, "anvil", "chipped_anvil", "damaged_anvil");
        put(values, 0.2F, "trapped_chest");
        put(values, 0.5F, "light_weighted_pressure_plate");
        put(values, 0.5F, "heavy_weighted_pressure_plate");
        put(values, 0.0F, "comparator");
        put(values, 0.2F, "daylight_detector");
        put(values, 5.0F, "redstone_block");
        put(values, 3.0F, "nether_quartz_ore");
        put(values, 3.0F, "hopper");
        put(values, 0.8F, "quartz_block", "chiseled_quartz_block", "quartz_pillar");
        put(values, 0.8F, "quartz_stairs");
        put(values, 0.7F, "activator_rail");
        put(values, 3.5F, "dropper");
        put(values, 1.8F, "white_terracotta", "orange_terracotta", "magenta_terracotta", "light_blue_terracotta", "yellow_terracotta", "lime_terracotta", "pink_terracotta", "gray_terracotta", "light_gray_terracotta", "cyan_terracotta", "purple_terracotta", "blue_terracotta", "brown_terracotta", "green_terracotta", "red_terracotta", "black_terracotta");
        put(values, 0.5F, "hay_block");
        put(values, 0.1F, "white_carpet", "orange_carpet", "magenta_carpet", "light_blue_carpet", "yellow_carpet", "lime_carpet", "pink_carpet", "gray_carpet", "light_gray_carpet", "cyan_carpet", "purple_carpet", "blue_carpet", "brown_carpet", "green_carpet", "red_carpet", "black_carpet");
        put(values, 1.8F, "terracotta");
        put(values, 1.2F, "coal_block");
        put(values, 4.8F, "copper_block");
        put(values, 0.0F, "potted_blue_orchid", "potted_allium", "potted_orange_tulip", "potted_oxeye_daisy");
        return Map.copyOf(values);
    }

    private static Map<String, Float> slabs() {
        Map<String, Float> values = new HashMap<>();
        // Explicit old overrides, not full-block hardness divided by two.
        put(values, 1.2F, "smooth_stone_slab");
        put(values, 0.5F, "sandstone_slab");
        put(values, 1.0F, "cobblestone_slab");
        put(values, 1.0F, "brick_slab");
        put(values, 1.0F, "stone_brick_slab");
        put(values, 1.0F, "nether_brick_slab");
        put(values, 0.4F, "quartz_slab");
        put(values, 0.4F, "oak_slab");
        put(values, 0.4F, "spruce_slab");
        put(values, 0.4F, "birch_slab");
        put(values, 0.4F, "jungle_slab");
        return Map.copyOf(values);
    }

    private static void put(Map<String, Float> values, float hardness, String... paths) {
        for (String path : paths)
            if (values.putIfAbsent(path, hardness) != null)
                throw new IllegalStateException("Duplicate source hardness mapping: " + path);
    }
}
