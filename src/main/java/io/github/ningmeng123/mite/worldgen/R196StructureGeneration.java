package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.advancement.MiteAchievements;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.item.R196ReinforcedBowItem;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import io.github.ningmeng123.mite.rules.StructureUnlockRules;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Dynamic gates use immutable world snapshots; generation workers never open save files. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class R196StructureGeneration {
    public static final long RUINED_PORTAL_DAY = StructureUnlockRules.MINIMUM_DAY, PILLAGER_OUTPOST_DAY = 45L, VILLAGE_DAY = 60L;
    private record State(long time, int spawnX, int spawnZ, int conditions, Map<Metric, Long> exploration) {}
    private static final ConcurrentMap<MinecraftServer, State> STATES = new ConcurrentHashMap<>();
    private static final TagKey<Biome> MINESHAFT_HILLS = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "has_structure/r196_mineshaft_hills"));
    private static final ResourceLocation UNDERWORLD_TRIAL = ResourceLocation.fromNamespaceAndPath(
            Mite.MOD_ID, "underworld_trial_chambers");
    private static final ResourceLocation OVERWORLD_TRIAL = ResourceLocation.withDefaultNamespace("trial_chambers");
    private R196StructureGeneration() {}

    /** Publish only on the main thread, before spawn generation and after live progress/time changes. */
    public static void refresh(ServerLevel level) {
        if (!level.getServer().isSameThread()) return;
        ServerLevel overworld = level.getServer().overworld();
        if (overworld == null) return;
        BlockPos spawn = overworld.getSharedSpawnPos();
        STATES.put(level.getServer(), new State(overworld.getDayTime(), spawn.getX(), spawn.getZ(),
                StructureProgressData.get(overworld).conditions(), StructureExplorationData.get(overworld).snapshot()));
    }

    private static String vanillaId(Holder<Structure> structure) {
        return structure.unwrapKey().map(key -> key.location()).filter(id -> id.getNamespace().equals("minecraft"))
                .map(ResourceLocation::getPath).orElse("");
    }

    public static long displayDay(long time) {
        long safe = Math.max(time, 0L);
        return safe / 24000L + (safe % 24000L + 6000L) / 24000L + 1L;
    }

    public static boolean villageUnlocked(long time, int conditions) {
        return displayDay(time) >= VILLAGE_DAY && (conditions & StructureProgressData.OBSIDIAN_TOOL) != 0;
    }

    public static boolean ruinedPortalUnlocked(long time, int conditions) {
        return displayDay(time) >= RUINED_PORTAL_DAY && (conditions & StructureProgressData.UNDERWORLD_VISITED) != 0;
    }

    public static boolean pillagerOutpostUnlocked(long time, int conditions) {
        int required = StructureProgressData.BOW_CRAFTED | StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED;
        return displayDay(time) >= PILLAGER_OUTPOST_DAY && (conditions & required) == required;
    }

    public static boolean unlocked(ServerLevel level, Holder<Structure> structure) {
        // Generation and locate share this live gate, outside StructureCheck's permanent cache.
        ResourceLocation location = structure.unwrapKey().map(key -> key.location()).orElse(null);
        if (UNDERWORLD_TRIAL.equals(location) && !MiteConfig.underworldTrialsEnabled()) return false;
        if (OVERWORLD_TRIAL.equals(location) && !MiteConfig.overworldTrialsEnabled()) return false;
        if (location != null && StructureUnlockRules.exempt(location.getNamespace(), location.getPath())) return true;
        State state = STATES.get(level.getServer());
        if (state == null) return false;
        String namespace = location == null ? "" : location.getNamespace();
        String path = location == null ? "" : location.getPath();
        if (!StructureUnlockRules.dayUnlocked(namespace, path, displayDay(state.time()))) return false;
        var definition = StructureUnlockRules.forStructure(namespace, path);
        return definition == null || StructureUnlockRules.requirementsMet(definition, state.conditions(), state.exploration());
    }

    /** Fossils are features rather than structures; monster rooms deliberately retain their original generation. */
    public static boolean fossilUnlocked(ServerLevel level) {
        State state = STATES.get(level.getServer());
        var definition = StructureUnlockRules.find("overworld_fossil");
        return state != null && level.getServer().getWorldData().worldGenOptions().generateStructures()
                && displayDay(state.time()) >= definition.minimumDay()
                && StructureUnlockRules.requirementsMet(definition, state.conditions(), state.exploration());
    }

    public static boolean canGenerate(ServerLevel level, Holder<Structure> structure, ChunkPos coordinate) {
        if (!unlocked(level, structure)) return false;
        if (!level.dimension().equals(Level.OVERWORLD)) return true;
        String id = vanillaId(structure);
        if (id.equals("desert_pyramid") || id.equals("jungle_pyramid")) {
            State state = STATES.get(level.getServer());
            if (state == null) return false;
            double dx = (double)coordinate.getMinBlockX() - state.spawnX();
            double dz = (double)coordinate.getMinBlockZ() - state.spawnZ();
            return dx * dx + dz * dz >= 2000D * 2000D;
        }
        if (id.equals("mineshaft") || id.equals("mineshaft_mesa")) {
            if (!io.github.ningmeng123.mite.rules.StructureDistributionRules.mineshaftCandidate(
                    level.getSeed(), coordinate.x, coordinate.z, true)) return false;
            // No loaded chunks or heightmaps are requested from the generation worker.
            var source = level.getChunkSource();
            var generator = source.getGenerator();
            int x = coordinate.getMinBlockX(), z = coordinate.getMinBlockZ();
            int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, source.randomState());
            Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(x >> 2, y >> 2, z >> 2,
                    source.randomState().sampler());
            return biome.is(MINESHAFT_HILLS);
        }
        return true;
    }

    public static boolean qualifies(ItemStack output) {
        if (output.isEmpty()) return false;
        var definition = MiteTools.definition(output);
        boolean tool = definition == null ? output.getItem() instanceof DiggerItem
                : definition.family() == ToolFamily.PICKAXE || definition.family() == ToolFamily.WAR_HAMMER;
        return tool && output.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState());
    }

    private static int craftingConditions(ItemStack output) {
        if (output.isEmpty()) return 0;
        int bits = qualifies(output) ? StructureProgressData.OBSIDIAN_TOOL : 0;
        if (output.getItem() instanceof BowItem) bits |= StructureProgressData.BOW_CRAFTED;
        var tool = MiteTools.definition(output);
        var armor = MiteArmorContent.definition(output.getItem());
        if (tool != null && tool.material().equals("mithril")
                || armor != null && !armor.horse() && armor.material().equals("mithril")
                || output.getItem() instanceof R196ReinforcedBowItem bow && bow.material().equals("mithril"))
            bits |= StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED;
        return bits;
    }

    /** Called after an actual crafting transaction; merely owning, previewing or automating equipment does not qualify. */
    public static void crafted(ServerPlayer player, ItemStack output) {
        StructureExplorationProgress.crafted(player, output);
        int bits = craftingConditions(output);
        if (bits != 0) StructureProgressData.get(player.serverLevel()).fulfill(bits);
    }

    @SubscribeEvent public static void load(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) refresh(level);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) refresh(level);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { STATES.remove(event.getServer()); }

    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getOriginalStack();
        int bit = stack.is(Items.WHEAT) ? StructureProgressData.WHEAT : stack.is(Items.CARROT) ? StructureProgressData.CARROT
                : stack.is(Items.POTATO) ? StructureProgressData.POTATO
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "onion"))
                ? StructureProgressData.ONION : 0;
        if (bit != 0) StructureProgressData.get(player.serverLevel()).fulfill(bit);
    }

    @SubscribeEvent public static void craft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getInventory() instanceof net.minecraft.world.inventory.CraftingContainer grid
                && io.github.ningmeng123.mite.crafting.TimedCraftingController.isCompletingGrid(grid)) return;
        crafted(player, event.getCrafting());
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTo().equals(MiteWorldDimensions.UNDERWORLD))
            StructureProgressData.get(player.serverLevel()).fulfill(StructureProgressData.UNDERWORLD_VISITED);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Existing saves have authoritative completed-crafting statistics, unlike a player's current inventory.
        int bits = 0;
        for (var item : BuiltInRegistries.ITEM) {
            int crafted = craftingConditions(new ItemStack(item));
            if (crafted != 0 && player.getStats().getValue(Stats.ITEM_CRAFTED.get(item)) > 0) bits |= crafted;
        }
        var portal = player.server.getAdvancements().get(MiteAchievements.catalog().get("portal").id());
        if (player.level().dimension().equals(MiteWorldDimensions.UNDERWORLD)
                || portal != null && player.getAdvancements().getOrStartProgress(portal).isDone())
            bits |= StructureProgressData.UNDERWORLD_VISITED;
        if (bits != 0) StructureProgressData.get(player.serverLevel()).fulfill(bits);
    }
}
