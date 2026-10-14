package io.github.ningmeng123.mite.ranged;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A one-shot impact removes only source period-zero blocks; period 400 only emits steam. @author NingMeng123 */
public final class SphereCorrosion {
    private static final Map<Integer, int[]> SOURCE = load();
    private static final Map<String, Integer> DIRECT = Map.ofEntries(
            Map.entry("cobweb",30), Map.entry("dead_bush",32), Map.entry("dandelion",37), Map.entry("poppy",38),
            Map.entry("brown_mushroom",39), Map.entry("red_mushroom",40), Map.entry("torch",50), Map.entry("wall_torch",50),
            Map.entry("wheat",59), Map.entry("redstone_torch",76), Map.entry("redstone_wall_torch",76),
            Map.entry("sugar_cane",83), Map.entry("cake",92), Map.entry("pumpkin_stem",104), Map.entry("attached_pumpkin_stem",104),
            Map.entry("melon_stem",105), Map.entry("attached_melon_stem",105), Map.entry("vine",106), Map.entry("lily_pad",111),
            Map.entry("nether_wart",115), Map.entry("tripwire",132), Map.entry("carrots",141), Map.entry("potatoes",142),
            Map.entry("short_grass",31), Map.entry("fern",31), Map.entry("tall_grass",31), Map.entry("large_fern",31),
            Map.entry("powered_rail",27), Map.entry("detector_rail",28), Map.entry("sticky_piston",29), Map.entry("piston",33),
            Map.entry("piston_head",34), Map.entry("iron_block",42), Map.entry("chest",54), Map.entry("ladder",65),
            Map.entry("rail",66), Map.entry("lever",69), Map.entry("iron_door",71), Map.entry("cactus",81),
            Map.entry("pumpkin",86), Map.entry("carved_pumpkin",86), Map.entry("jack_o_lantern",91), Map.entry("repeater",93),
            Map.entry("iron_bars",101), Map.entry("melon",103), Map.entry("enchanting_table",116), Map.entry("cauldron",118),
            Map.entry("water_cauldron",118), Map.entry("lava_cauldron",118), Map.entry("cocoa",127), Map.entry("tripwire_hook",131),
            Map.entry("anvil",145), Map.entry("chipped_anvil",145), Map.entry("damaged_anvil",145), Map.entry("trapped_chest",146),
            Map.entry("heavy_weighted_pressure_plate",148), Map.entry("comparator",149), Map.entry("daylight_detector",151),
            Map.entry("hopper",154), Map.entry("activator_rail",157), Map.entry("hay_block",170), Map.entry("copper_block",204));
    private SphereCorrosion() {}
    public static int legacyBlock(BlockState state) {
        String metal = MiteDecorativeContent.material(state.getBlock());
        if (metal != null) {
            boolean door = state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
            return switch (metal) {
                case "copper" -> door ? 208 : 213;
                case "silver" -> door ? 209 : 214;
                case "gold" -> door ? 210 : 215;
                case "ancient_metal" -> door ? 253 : 199;
                case "mithril" -> door ? 211 : 216;
                case "adamantium" -> door ? 212 : 217;
                default -> -1;
            };
        }
        if (state.is(BlockTags.WOOL)) return 35;
        if (state.is(BlockTags.WOOL_CARPETS)) return 171;
        if (state.is(BlockTags.BEDS)) return 26;
        if (state.is(BlockTags.LEAVES)) return 18;
        if (state.is(BlockTags.SAPLINGS)) return 6;
        if (state.is(BlockTags.FLOWERS)) return 38;
        if (state.is(BlockTags.WOODEN_DOORS)) return 64;
        if (state.is(BlockTags.WOODEN_TRAPDOORS)) return 96;
        if (state.is(BlockTags.WOODEN_FENCES)) return 85;
        if (state.is(BlockTags.FENCE_GATES)) return 107;
        if (state.is(BlockTags.WOODEN_PRESSURE_PLATES)) return 72;
        if (state.is(BlockTags.WOODEN_BUTTONS)) return 143;
        if (state.is(BlockTags.SIGNS) || state.is(BlockTags.ALL_HANGING_SIGNS)) return 63;
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id.getNamespace().equals("minecraft")) return DIRECT.getOrDefault(id.getPath(), -1);
        if (!id.getNamespace().equals("mite")) return -1;
        return switch (id.getPath()) {
            case "onions" -> 234; case "blueberry_bush" -> 249; case "witherwood_bush" -> 32;
            case "silver_block" -> 205; case "ancient_metal_block" -> 252;
            case "copper_anvil" -> 229; case "silver_anvil" -> 230; case "ancient_metal_anvil" -> 254;
            case "copper_strongbox" -> 239; case "silver_strongbox" -> 240; case "iron_strongbox" -> 242; case "ancient_metal_strongbox" -> 255;
            case "emerald_enchanting_table" -> 245;
            default -> -1;
        };
    }
    public static int period(BlockState state, boolean acid) {
        int[] period = SOURCE.get(legacyBlock(state)); return period == null ? -1 : period[acid ? 1 : 0];
    }
    public static boolean contact(Level level, BlockPos pos, Direction face, boolean acid) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockState state = level.getBlockState(pos);
        if (acid && face == Direction.UP && (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM))) {
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState()); steam(server, pos); return true;
        }
        int period = period(state, acid);
        if (period < 0) return false;
        steam(server, pos);
        // aqz.onContactWith* uses setBlockToAir, deliberately without block/item drops.
        return period == 0 && level.removeBlock(pos, false);
    }
    private static void steam(ServerLevel server, BlockPos pos) { server.sendParticles(ParticleTypes.CLOUD, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, 8, .2, .2, .2, .02); }
    private static Map<Integer, int[]> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(SphereCorrosion.class.getResourceAsStream("/mite/sphere-corrosion-028.json")), StandardCharsets.UTF_8)) {
            Map<Integer, int[]> values = new HashMap<>();
            JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("rows").forEach(e -> {
                var row = e.getAsJsonObject(); values.put(row.get("legacy_id").getAsInt(), new int[]{row.get("pepsin").getAsInt(), row.get("acid").getAsInt()});
            });
            return Map.copyOf(values);
        } catch (Exception error) { throw new IllegalStateException("Cannot load guarded source corrosion periods", error); }
    }
}
