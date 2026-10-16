package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.block.StrongboxBlock;
import io.github.ningmeng123.mite.block.entity.StrongboxBlockEntity;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantments;
import io.github.ningmeng123.mite.rules.PortableBlockRules;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Source portable blocks have a minimum strength and spare the held tool. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class FunctionalBlockMining {
    private FunctionalBlockMining() {}
    public static boolean portable(Player player, BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getBlock() instanceof StrongboxBlock) return level.getBlockEntity(pos) instanceof StrongboxBlockEntity entity && entity.isOwner(player);
        return portableWithoutOwner(state);
    }
    /** Explicit source inheritance families; having a BlockEntity alone never makes a block portable. */
    public static boolean portableWithoutOwner(BlockState state) {
        var block = state.getBlock();
        if (block instanceof StrongboxBlock) return false;
        return block instanceof AnvilBlock || block instanceof BedBlock || block instanceof LadderBlock
                || block instanceof AbstractCauldronBlock || block instanceof CraftingTableBlock
                || block instanceof AbstractFurnaceBlock || block instanceof ChestBlock || block instanceof EnderChestBlock
                || block instanceof BrewingStandBlock || block instanceof EnchantingTableBlock
                || block instanceof SignBlock || block instanceof AbstractSkullBlock
                || state.is(Blocks.TNT) || state.is(Blocks.DRAGON_EGG) || state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER)
                || state.is(Blocks.NOTE_BLOCK) || state.is(Blocks.JUKEBOX) || state.is(Blocks.BEACON)
                || state.is(Blocks.DAYLIGHT_DETECTOR) || state.is(Blocks.HOPPER);
    }
    public static float hardness(BlockState state, float raw) {
        return state.getBlock() instanceof AnvilBlock && !(state.getBlock() instanceof io.github.ningmeng123.mite.block.MaterialAnvilBlock)
                ? 9.6F : raw;
    }
    public static float portableStrength(Player player, BlockState state, BlockGetter level, BlockPos pos, float strength) {
        if (!portable(player, state, level, pos) || !(strength > 0F)) return strength;
        int haste = player.hasEffect(MobEffects.DIG_SPEED) ? player.getEffect(MobEffects.DIG_SPEED).getAmplifier() : -1;
        int fatigue = player.hasEffect(MobEffects.DIG_SLOWDOWN) ? player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier() : -1;
        var affinity = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.AQUA_AFFINITY);
        boolean aqua = java.util.stream.StreamSupport.stream(player.getArmorSlots().spliterator(), false)
                .anyMatch(stack -> stack.getEnchantmentLevel(affinity) > 0);
        return PortableBlockRules.strength(hardness(state, state.getDestroySpeed(level, pos)), player.experienceLevel, haste, fatigue,
                player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) && !aqua, player.onGround(), io.github.ningmeng123.mite.survival.FoodConsumption.hasEnergy(player));
    }
    public static int requiredHarvest(StrongboxBlock block) {
        return switch (block.material()) {
            case "copper", "silver", "gold" -> 3;
            case "iron", "ancient_metal" -> 4;
            case "mithril" -> 5;
            case "adamantium" -> 6;
            default -> throw new IllegalArgumentException(block.material());
        };
    }
    public static boolean blocked(Player player, BlockState state, BlockPos pos) {
        if (player.isCreative() || player.isSpectator() || !(state.getBlock() instanceof StrongboxBlock strongbox)
                || portable(player, state, player.level(), pos)) return false;
        var definition = MiteTools.definition(player.getMainHandItem());
        if (definition != null) return definition.harvestLevel() < requiredHarvest(strongbox)
                || definition.family() != ToolFamily.PICKAXE && definition.family() != ToolFamily.WAR_HAMMER;
        // Modern compatibility uses tier tags supplied by the main block-tag provider.
        return !player.getMainHandItem().isCorrectToolForDrops(state) || player.getMainHandItem().getDestroySpeed(state) <= 1F;
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void mining(PlayerEvent.BreakSpeed event) {
        event.getPosition().ifPresent(pos -> {
            if (!event.isCanceled() && blocked(event.getEntity(), event.getState(), pos)) event.setCanceled(true);
        });
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void harvest(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && blocked(event.getPlayer(), event.getState(), event.getPos())) event.setCanceled(true);
    }
}
