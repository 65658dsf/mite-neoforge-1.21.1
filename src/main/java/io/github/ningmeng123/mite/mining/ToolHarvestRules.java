package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.OreBlockRules;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.ToolFamily;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Enforce the declared R196 tool tiers at progress and final server removal. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class ToolHarvestRules {
    private ToolHarvestRules() {}
    public static boolean isBlocked(Player player, BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.AnvilBlock) return false;
        int decorative = io.github.ningmeng123.mite.registry.MiteDecorativeContent.requiredHarvest(state.getBlock());
        if (decorative > 0 && !player.isCreative() && !player.isSpectator()) {
            var tool = MiteTools.definition(player.getMainHandItem());
            return tool == null ? !player.getMainHandItem().isCorrectToolForDrops(state)
                    : tool.harvestLevel() < decorative || tool.family() != ToolFamily.PICKAXE && tool.family() != ToolFamily.WAR_HAMMER;
        }
        String storage = io.github.ningmeng123.mite.registry.MiteMetalBlocks.material(state);
        if (storage != null && !player.isCreative() && !player.isSpectator()) {
            var definition = MiteTools.definition(player.getMainHandItem());
            if (definition != null) return definition.harvestLevel() < io.github.ningmeng123.mite.registry.MiteMetalBlocks.requiredHarvest(storage)
                    || definition.family() != ToolFamily.PICKAXE && definition.family() != ToolFamily.WAR_HAMMER;
            return !player.getMainHandItem().isCorrectToolForDrops(state);
        }
        if (!player.isCreative() && !player.isSpectator() && OreBlockRules.isManaged(state)) {
            var definition = MiteTools.definition(player.getMainHandItem());
            // Managed tools use source numerical levels. Modern/modded tools retain their explicit Tool rules.
            if (definition != null) return definition.harvestLevel() < OreBlockRules.minimumHarvestLevel(state)
                    || !(definition.family() == ToolFamily.PICKAXE || definition.family() == ToolFamily.WAR_HAMMER);
            return !player.getMainHandItem().isCorrectToolForDrops(state);
        }
        if (player.isCreative() || player.isSpectator() || !SourceMiningRequirements.requiresTool(state)) return false;
        var held = player.getMainHandItem();
        var tool = MiteTools.definition(held);
        // An ineffective source item returns zero, then uf retries bare hands; a modern baseline one is not a tool.
        if (held.isEmpty() || !(held.getDestroySpeed(state) > 1F)) return true;
        if (tool != null && tool.harvestLevel() < SourceMiningRequirements.minimumHarvestLevel(state)) return true;
        return state.requiresCorrectToolForDrops() && !held.isCorrectToolForDrops(state);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void harvest(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && !FunctionalBlockMining.portable(event.getPlayer(), event.getState(), event.getLevel(), event.getPos())
                && isBlocked(event.getPlayer(), event.getState())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hint(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.isCanceled() && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && event.getEntity() instanceof ServerPlayer player && player.canInteractWithBlock(event.getPos(), 1.0)
                && player.serverLevel().hasChunkAt(event.getPos())
                && !FunctionalBlockMining.portable(player, player.serverLevel().getBlockState(event.getPos()), player.serverLevel(), event.getPos())
                && isBlocked(player, player.serverLevel().getBlockState(event.getPos()))) {
            player.displayClientMessage(Component.translatable("message.mite.tool_tier_too_low"), true);
        }
    }
}
