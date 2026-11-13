package io.github.ningmeng123.mite.api;

import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import io.github.ningmeng123.mite.rules.LunarCalendar;
import io.github.ningmeng123.mite.rules.MonsterSpawningSettings;
import io.github.ningmeng123.mite.rules.ProgressionRules;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.mixin.RangedAttributeAccess;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Optional, common-side extension contract. Defaults preserve standalone MITE behavior. @author NingMeng123 */
public final class MiteExtensions {
    public record SpawnContext(ServerLevel level, Category category, Boolean deep) {}
    /** Optional settings contributed to the host's natural-spawn editor. */
    public record SpawningControl(String key, String translationKey, Object defaultValue, double minimum, double maximum) {}
    public interface Policy {
        default LunarCalendar.State calendar(LunarCalendar.State original) { return original; }
        default long bloodMoonCount(long highestDay, long original) { return original; }
        /** Resolve the synchronized preset without a world, including configuration screens. */
        default boolean referenceSpawningSettings(boolean original) { return original; }
        /** Resolve the active natural-spawn preset. Addons may supply their own server-side preset. */
        default boolean referenceHostileSpawning(ServerLevel level, boolean original) { return referenceSpawningSettings(original); }
        /** Let an addon retain reference populations and habitats while exposing editable scheduling values. */
        default boolean configurableHostileSpawningSettings() { return false; }
        /** Supply authoritative scheduling values while retaining the host's saved settings independently. */
        default MonsterSpawningSettings.Settings hostileSpawningSettings(MonsterSpawningSettings.Settings original) { return original; }
        /** A nonempty list replaces the host editor's controls with the addon's complete spawning controls. */
        default List<SpawningControl> spawningControls() { return List.of(); }
        default Map<String, Object> spawningControlValues() { return Map.of(); }
        /** Validate and persist a complete editor draft, then synchronize its authoritative values. */
        default boolean saveSpawningControls(Map<String, Object> values) { return false; }
        default boolean independentHostileBudgets(ServerLevel level, boolean original) { return original; }
        default int monsterCap(ServerLevel level, boolean deep, int eligibleChunks, int original) { return original; }
        default int localDensity(ServerLevel level, Player player, int original) { return original; }
        default float hostilePassProbability(ServerLevel level, boolean deep, float original) { return original; }
        default int spawnGroups(SpawnContext context, int original) { return original; }
        default int spawnAttempts(SpawnContext context, EntityType<?> selected, int original) { return original; }
        default int spawnJitter(SpawnContext context, int original) { return original; }
        default int distantHostileSkipBound(SpawnContext context, int original) { return original; }
        default boolean spawnDistance(SpawnContext context, BlockPos pos, double nearestPlayerSq, double spawnSq, boolean close, boolean original) { return original; }
        default boolean outdoorAllows(SpawnContext context, Mob mob, boolean original) { return original; }
        default List<R196MobSpawning.Entry> spawnPool(SpawnContext context, BlockPos pos, List<R196MobSpawning.Entry> original) { return original; }
        /** Return the addon's own saved natural-spawn rate for a registered entity. */
        default double monsterSpawnMultiplier(EntityType<?> type, double original) { return original; }
        /** Absolute weight of an existing natural pool candidate, before per-species multipliers. */
        default double monsterSpawnWeight(EntityType<?> type, double original) { return original; }
        /** Configuration screens use this to keep addon-owned creatures in their own menu. */
        default boolean ownsMonsterSpawnSettings(String entityId) { return false; }
        /** Include an addon's finalization replacements in the parent family's effective weight. */
        default double monsterSpawnWeightMultiplier(SpawnContext context, BlockPos pos, EntityType<?> type, double original) { return original; }
        default double maximumHealth(Player player, double original) { return original; }
        default int foodCapacity(Player player, int original) { return original; }
        default int gearMaximumLevel(ItemStack stack, int original) { return original; }
        default io.github.ningmeng123.mite.progression.GearAffixRules.Definition affixDefinition(ItemStack stack, Holder<Enchantment> enchantment, io.github.ningmeng123.mite.progression.GearAffixRules.Definition original) { return original; }
        default float naturalRegeneration(Player player, float original) { return original; }
        default long gearExperience(ItemStack stack, long original) { return original; }
        default void preserve(ItemStack source, ItemStack result) {}
        default boolean isMaintenanceRecipe(net.minecraft.world.item.crafting.CraftingRecipe recipe) { return false; }
        default boolean enchantmentSupported(Holder<Enchantment> enchantment, boolean original) { return original; }
        default boolean enchantmentApplicable(String id, ItemStack stack, boolean original) { return original; }
        default boolean enchantmentsCompatible(String left, String right, boolean original) { return original; }
        default boolean tableEnchantmentsCompatible(ItemStack stack, Holder<Enchantment> left, Holder<Enchantment> right, boolean original) { return original; }
        default boolean anvilEnchantmentsCompatible(ItemStack stack, Holder<Enchantment> left, Holder<Enchantment> right, boolean original) { return original; }
        default float enchantmentPowerMultiplier(float roll, float original) { return original; }
        default float craftingDifficulty(ItemStack stack, float original) { return original; }
        default int materialUpgradeIngots(ItemStack stack, int original) { return original; }
        default double mobHealthLimit(Mob mob, double original) { return original; }
        default double maximumHealthAttributeRange(double original) { return original; }
        default io.github.ningmeng123.mite.rules.R196FurnaceRules.Profile furnaceProfile(net.minecraft.world.level.block.state.BlockState state, io.github.ningmeng123.mite.rules.R196FurnaceRules.Profile original) { return original; }
        default io.github.ningmeng123.mite.rules.R196FurnaceRules.Fuel fuel(ItemStack stack, io.github.ningmeng123.mite.rules.R196FurnaceRules.Fuel original) { return original; }
        default int requiredHeat(ItemStack stack, int original) { return original; }
        default void furnaceCooked(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace) {}
        default int gemSacrificeExperience(ItemStack stack, int original) { return original; }
        default boolean canSacrificeGem(Player player, ItemStack stack, boolean original) { return original; }
        default int foodUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity user, int original) { return original; }
        default void portalUsed(net.minecraft.server.level.ServerPlayer player, BlockPos entry) {}
        default int breedingExperience(net.minecraft.world.entity.animal.Animal parent, int original) { return original; }
        default float creeperExplosionRadius(net.minecraft.world.entity.monster.Creeper creeper, float original) { return original; }
        default void villagerOffers(net.minecraft.world.entity.npc.Villager villager, net.minecraft.world.item.trading.MerchantOffers offers) {}
        default boolean mobDefense(Mob mob, boolean original) { return original; }
        /** Identify the addon's actual archer roles without turning every skeleton subtype into an archer. */
        default boolean earlyEquipmentArcher(Mob mob, boolean original) { return original; }
        /** Legacy compatibility callback; the host no longer clears visible or hidden birth equipment. */
        default void clearEarlySpawnEquipment(Mob mob) {}
        default float playerInWallDamage(Player player, float original) { return original; }
        default int fishingExperience(Player player, int original) { return original; }
        default boolean canDisarmPlayer(Player attacker, Player target, boolean original) { return original; }
    }
    private static volatile Policy policy = new Policy() {};
    private static boolean registered;
    private static final Map<String,io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog.Definition> ENCHANTMENTS = new ConcurrentHashMap<>();
    private static final Map<String,MiteTools.Definition> TOOLS = new ConcurrentHashMap<>();
    private static final Map<String,MiteArmorContent.Definition> ARMOR = new ConcurrentHashMap<>();
    private MiteExtensions() {}
    /** Register once during common setup on both client and dedicated server. */
    public static synchronized void register(Policy extension) {
        if (registered) throw new IllegalStateException("A MITE extension policy is already registered");
        policy = Objects.requireNonNull(extension); registered = true;
    }
    public static Policy policy() { return policy; }
    public static int foodCapacity(Player player) {
        if (player == null) return 20;
        if (player.level().isClientSide) {
            var snapshot=io.github.ningmeng123.mite.network.NutritionPayload.latest();
            if (snapshot!=null && snapshot.entityId()==player.getId()) return snapshot.limit();
        }
        return Math.clamp(policy.foodCapacity(player, ProgressionRules.foodLimitForLevel(player.experienceLevel)),1,20);
    }
    public static double maximumHealth(Player player) {
        double value=policy.maximumHealth(player,ProgressionRules.maxHealthForLevel(player.experienceLevel));
        if (!Double.isFinite(value) || value < 1D) throw new IllegalStateException("Invalid extension player health");
        return value;
    }
    public static void preserve(ItemStack source, ItemStack result) { policy.preserve(source,result); }
    public static void registerExternalTool(MiteTools.Definition definition) {
        Objects.requireNonNull(definition);
        if (TOOLS.putIfAbsent(definition.id(),definition)!=null) throw new IllegalArgumentException("Duplicate external tool " + definition.id());
    }
    public static void registerExternalArmor(MiteArmorContent.Definition definition) {
        Objects.requireNonNull(definition);
        if (ARMOR.putIfAbsent(definition.id(),definition)!=null) throw new IllegalArgumentException("Duplicate external armor " + definition.id());
    }
    public static void registerExternalEnchantment(io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog.Definition definition) {
        Objects.requireNonNull(definition);
        if (ENCHANTMENTS.putIfAbsent(definition.resourceId(),definition)!=null) throw new IllegalArgumentException("Duplicate external enchantment " + definition.resourceId());
    }
    public static List<io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog.Definition> externalEnchantments() {
        return ENCHANTMENTS.values().stream().sorted(java.util.Comparator.comparing(io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog.Definition::resourceId)).toList();
    }
    public static MiteTools.Definition externalTool(String id) { return TOOLS.get(id); }
    public static MiteArmorContent.Definition externalArmor(Item item) { return ARMOR.get(BuiltInRegistries.ITEM.getKey(item).toString()); }
    /** Call once after policy registration; native attribute synchronization remains authoritative. */
    public static void applyAttributeRanges() {
        double maximum=policy.maximumHealthAttributeRange(1024D);
        if (!Double.isFinite(maximum)||maximum<1024D) throw new IllegalArgumentException("Invalid health attribute range");
        ((RangedAttributeAccess)(Object)Attributes.MAX_HEALTH.value()).mite$setMaxValue(maximum);
    }
}
