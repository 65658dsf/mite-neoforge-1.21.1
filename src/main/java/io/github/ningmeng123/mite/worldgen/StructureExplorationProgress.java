package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Only completed survival actions and continuous physical travel advance exploration. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class StructureExplorationProgress {
    private static final Map<ServerPlayer, Travel> TRAVEL = new WeakHashMap<>();
    private static final Set<Block> NATURAL_STONE = Set.of(Blocks.STONE, Blocks.DEEPSLATE,
            Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.CALCITE, Blocks.SMOOTH_BASALT);
    private static final class Travel {
        private ResourceKey<Level> dimension;
        private Vec3 position;
        private EnumSet<Metric> eligible;
        private final EnumMap<Metric, Double> remainder = new EnumMap<>(Metric.class);
        private Travel(ServerPlayer player, EnumSet<Metric> eligible) {
            dimension = player.level().dimension();
            position = player.position();
            this.eligible = eligible;
        }
    }

    private StructureExplorationProgress() {}

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    /** Public for deterministic native-player integration tests; the event calls this once per tick. */
    public static void sample(ServerPlayer player) {
        if (!eligible(player)) { reset(player); return; }
        EnumSet<Metric> eligible = travelMetrics(player);
        Travel previous = TRAVEL.putIfAbsent(player, new Travel(player, eligible));
        if (previous == null) return;
        Vec3 current = player.position();
        Vec3 delta = current.subtract(previous.position);
        double squared = delta.lengthSqr();
        if (previous.dimension.equals(player.level().dimension()) && squared <= 64D && squared > 0D) {
            double distanceCm = Math.hypot(delta.x, delta.z) * 100D;
            if (Double.isFinite(distanceCm) && distanceCm > 0D) for (Metric metric : eligible) {
                if (!previous.eligible.contains(metric)) continue;
                double total = previous.remainder.getOrDefault(metric, 0D) + distanceCm;
                long whole = (long)Math.floor(total + 1E-9D);
                previous.remainder.put(metric, Math.max(0D, total - whole));
                if (whole > 0L) StructureExplorationData.get(player.serverLevel()).add(player.getUUID(), metric, whole);
            }
        }
        previous.dimension = player.level().dimension();
        previous.position = current;
        previous.eligible = eligible;
    }

    public static void reset(ServerPlayer player) { TRAVEL.remove(player); }

    private static EnumSet<Metric> travelMetrics(ServerPlayer player) {
        var result = EnumSet.noneOf(Metric.class);
        if (player.getAbilities().flying || player.isFallFlying()) return result;
        var biome = player.level().getBiome(player.blockPosition());
        boolean onFoot = !player.isPassenger() && !player.isInWaterOrBubble();
        boolean boat = player.getVehicle() instanceof Boat vessel && vessel.isInWater() && !vessel.isUnderWater();
        if (player.level().dimension().equals(Level.OVERWORLD)) {
            if (biome.is(BiomeTags.IS_OCEAN)) {
                if (boat) result.add(Metric.OCEAN_SAIL_CM);
                if (!player.isPassenger() && player.isEyeInFluid(FluidTags.WATER)) result.add(Metric.OCEAN_DIVE_CM);
            }
            if ((biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) && (onFoot || boat))
                result.add(Metric.SWAMP_TRAVEL_CM);
            if (onFoot) {
                if (biome.is(Biomes.DESERT)) result.add(Metric.DESERT_WALK_CM);
                if (biome.is(BiomeTags.IS_JUNGLE)) result.add(Metric.JUNGLE_WALK_CM);
                if (snow(biome)) result.add(Metric.SNOW_WALK_CM);
                if (biome.is(Biomes.DARK_FOREST)) result.add(Metric.DARK_FOREST_WALK_CM);
                if (player.getY() < 0D) result.add(Metric.UNDERGROUND_WALK_CM);
                if (player.getY() <= -40D && player.onGround() && player.isShiftKeyDown()) result.add(Metric.DEEP_SNEAK_CM);
            }
        } else if (player.level().dimension().equals(Level.NETHER) && onFoot) {
            result.add(Metric.NETHER_WALK_CM);
            if (biome.is(Biomes.SOUL_SAND_VALLEY)) result.add(Metric.SOUL_VALLEY_WALK_CM);
        } else if (player.level().dimension().equals(Level.END) && onFoot
                && player.getX() * player.getX() + player.getZ() * player.getZ() > 1_000_000D) {
            result.add(Metric.END_ISLAND_WALK_CM);
        }
        return result;
    }

    private static boolean snow(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        return biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.ICE_SPIKES) || biome.is(Biomes.SNOWY_TAIGA)
                || biome.is(Biomes.SNOWY_BEACH) || biome.is(Biomes.SNOWY_SLOPES) || biome.is(Biomes.GROVE)
                || biome.is(Biomes.FROZEN_PEAKS) || biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.FROZEN_RIVER);
    }

    /** Called only by the existing completed crafting transaction, never a recipe preview. */
    public static void crafted(ServerPlayer player, ItemStack output) {
        if (!eligible(player) || output.isEmpty()) return;
        Metric metric = craftedMetric(output);
        if (metric != null) StructureExplorationData.get(player.serverLevel()).observe(player.getUUID(), metric, 1L);
    }

    private static Metric craftedMetric(ItemStack output) {
        if (output.is(Items.BRUSH)) return Metric.BRUSH_CRAFTED;
        if (output.is(Items.ENDER_EYE)) return Metric.EYE_CRAFTED;
        var armor = MiteArmorContent.definition(output.getItem());
        return armor != null && !armor.horse() && armor.material().equals("gold") ? Metric.GOLD_ARMOR_CRAFTED : null;
    }

    /** The caller captured provenance before a successful native block removal. */
    public static void mined(ServerPlayer player, BlockState original, boolean playerPlaced) {
        if (eligible(player) && player.level().dimension().equals(Level.OVERWORLD)
                && !playerPlaced && NATURAL_STONE.contains(original.getBlock()))
            StructureExplorationData.get(player.serverLevel()).add(player.getUUID(), Metric.NATURAL_STONE_MINED, 1L);
    }

    public static void reconcileStatistics(ServerPlayer player, boolean includeCrafted) {
        if (!eligible(player)) return;
        var data = StructureExplorationData.get(player.serverLevel());
        data.observe(player.getUUID(), Metric.FISH_CAUGHT, player.getStats().getValue(Stats.CUSTOM.get(Stats.FISH_CAUGHT)));
        data.observe(player.getUUID(), Metric.DRAGON_KILLED, player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.ENDER_DRAGON)));
        if (includeCrafted) for (var item : BuiltInRegistries.ITEM) {
            Metric metric = craftedMetric(new ItemStack(item));
            if (metric != null && player.getStats().getValue(Stats.ITEM_CRAFTED.get(item)) > 0)
                data.observe(player.getUUID(), metric, 1L);
        }
    }

    // Connection ticking restores the accepted position after player physics, so sample before that simulation.
    @SubscribeEvent public static void tick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sample(player);
        if (player.tickCount % 20 == 0) reconcileStatistics(player, false);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { reset(player); reconcileStatistics(player, true); }
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reset(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reset(player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void teleport(EntityTeleportEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof ServerPlayer player) reset(player);
        for (var passenger : event.getEntity().getPassengers())
            if (passenger instanceof ServerPlayer player) reset(player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) reset(player);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { TRAVEL.clear(); }
}
