package io.github.ningmeng123.mite.progression;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Mob;
import io.github.ningmeng123.mite.mining.FunctionalBlockMining;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;

/** Source ITE experience belongs to effective tool actions, real primary melee and each accepted armor portion. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class GearProgressionEvents {
    private static final ThreadLocal<MiningAction> MINING = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SOURCE_ARMOR_ALLOCATION = new ThreadLocal<>();

    private static final class MiningAction {
        private final ServerPlayer player;
        private final BlockPos pos;
        private final ItemStack tool;
        private final BlockState state;
        private final float hardness;
        private final boolean portable;
        private boolean rewarded;
        private MiningAction(ServerPlayer player, BlockPos pos) {
            this.player = player;
            this.pos = pos.immutable();
            tool = player.getMainHandItem();
            state = player.serverLevel().getBlockState(pos);
            hardness = MiningProgress.effectiveHardness(state, state.getDestroySpeed(player.level(), pos));
            portable = FunctionalBlockMining.portable(player, state, player.level(), pos);
        }
    }

    private GearProgressionEvents() {}

    public static boolean mining(ServerPlayer player, BlockPos pos, Supplier<Boolean> action) {
        if (!GearProgression.canGain(player) || !player.serverLevel().hasChunkAt(pos)) return action.get();
        MiningAction previous = MINING.get();
        MINING.set(new MiningAction(player, pos));
        try { return action.get(); }
        finally { if (previous == null) MINING.remove(); else MINING.set(previous); }
    }

    public static void mined(ServerPlayer player, BlockPos pos, boolean canHarvest) {
        MiningAction action = MINING.get();
        if (action == null || action.rewarded || action.player != player || !action.pos.equals(pos)) return;
        action.rewarded = true;
        if (!canHarvest || !GearProgression.canGain(player) || action.portable
                || !(action.hardness > 0F) || !action.tool.isDamageableItem()
                || !GearProgression.canMine(action.tool, action.state, player.level(), pos)) return;
        GearProgression.award(action.tool, miningExperienceMillis(action.tool, action.state), player);
    }

    public static long miningExperienceMillis(ItemStack tool, BlockState state) {
        ToolFamily family = R196EnchantmentCatalog.family(tool);
        if (family == null) return 0L;
        int points = switch (family) {
            case SHOVEL -> 3;
            case MATTOCK -> 2;
            case SCYTHE -> 1;
            case AXE, HATCHET, BATTLE_AXE -> state.is(BlockTags.LOGS) ? 2
                    : Math.max(SourceBlockMaterialExperience.minimumHarvestLevel(state), 1);
            case HOE -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) ? 8 : 0;
            default -> SourceBlockMaterialExperience.minimumHarvestLevel(state);
        };
        return points * GearProgressionRules.SCALE;
    }

    /** Committed placement provenance remains useful to geology and source ore drops, not as a general XP ban. */
    public static void beforeCommittedUse(UseOnContext context, BlockPos pos, BlockState state, boolean blockItem) {
        if (context.getPlayer() != null && context.getLevel() instanceof ServerLevel
                && blockItem && !state.isAir()) PlayerPlacedBlocks.set(context.getLevel(), pos, true);
    }

    public static void afterCommittedUse(UseOnContext context, BlockPos pos, BlockState previous) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !GearProgression.canGain(player)
                || previous.is(Blocks.FARMLAND) || !context.getLevel().getBlockState(pos).is(Blocks.FARMLAND)
                || !GearProgression.canTill(context.getItemInHand())) return;
        GearProgression.award(context.getItemInHand(), GearProgressionRules.SCALE, player);
    }

    /** The old bow/shield/projectile paths do not train equipment; retain this transport hook for binary callers. */
    public static void projectile(Projectile projectile, ItemStack weapon) {}

    /** Called from the primary melee transaction with the original attacking item and actual lost health. */
    public static void melee(Player attacker, LivingEntity target, ItemStack weapon, float actualHealthLost) {
        if (!(attacker instanceof ServerPlayer player) || !GearProgression.canGain(player)
                || target instanceof Player || !(actualHealthLost > 0F) || !Float.isFinite(actualHealthLost)
                || !GearModifier.isWeapon(weapon)) return;
        ToolFamily family = R196EnchantmentCatalog.family(weapon);
        int multiplier = target instanceof R196Mob mob && mob.miteKind() == MobKind.EARTH_ELEMENTAL
                && (family == ToolFamily.AXE || family == ToolFamily.HATCHET || family == ToolFamily.BATTLE_AXE) ? 10 : 1;
        int points = (int)Math.min(target.getMaxHealth(), actualHealthLost * multiplier);
        GearProgression.award(weapon, points * GearProgressionRules.SCALE, player);
    }

    /** In the reference timing, armor's health-loss result is still zero and clamps to one for each chosen portion. */
    public static void armorPortion(Player wearer, DamageSource source, ItemStack armor) {
        if (!(wearer instanceof ServerPlayer player) || !GearProgression.canGain(player)
                || source == null || source.getEntity() == null || !GearProgression.isArmor(armor)) return;
        GearProgression.award(armor, 2L * GearProgressionRules.SCALE, player);
    }

    /** Mark only our random-portion event; native mixed armor must still train every applicable slot. */
    public static <T> T sourceArmorAllocation(Supplier<T> operation) {
        Boolean previous = SOURCE_ARMOR_ALLOCATION.get();
        SOURCE_ARMOR_ALLOCATION.set(true);
        try { return operation.get(); }
        finally {
            if (previous == null) SOURCE_ARMOR_ALLOCATION.remove(); else SOURCE_ARMOR_ALLOCATION.set(previous);
        }
    }

    /** Modern native armor follows once-per-slot allocation; our scoped source path reports exact portions itself. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void nativeArmor(ArmorHurtEvent event) {
        if (event.isCanceled() || Boolean.TRUE.equals(SOURCE_ARMOR_ALLOCATION.get())
                || !(event.getEntity() instanceof Player player)) return;
        event.getArmorMap().values().forEach(entry -> {
            if ((int)entry.newDamage > 0)
                armorPortion(player, event.getDamageSource(), entry.armorItemStack);
        });
    }
}
