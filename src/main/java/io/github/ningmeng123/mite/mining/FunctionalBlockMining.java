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
        return state.getBlock() instanceof AnvilBlock || state.getBlock() instanceof StrongboxBlock
                && level.getBlockEntity(pos) instanceof StrongboxBlockEntity entity && entity.isOwner(player);
    }
    public static float hardness(BlockState state, float raw) {
        return state.getBlock() instanceof AnvilBlock && !(state.getBlock() instanceof io.github.ningmeng123.mite.block.MaterialAnvilBlock)
                ? 9.6F : raw;
    }
    public static float portableStrength(Player player, BlockState state, BlockGetter level, BlockPos pos, float strength) {
        return portable(player, state, level, pos) ? Math.max(strength, 4F * hardness(state, state.getDestroySpeed(level, pos))) : strength;
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
