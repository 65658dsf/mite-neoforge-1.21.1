package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.rules.FlintGravelRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Replaces only the ordinary player-mined gravel/flint result. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class FlintGravelDrops {
    private FlintGravelDrops() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.isCanceled() || !event.getState().is(Blocks.GRAVEL)
                || !(event.getBreaker() instanceof Player player) || player.isCreative() || player.isSpectator()
                || !event.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            return;
        }
        var enchantments = event.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        if (event.getTool().getEnchantmentLevel(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH)) > 0) {
            return;
        }
        // Preserve unrelated mod-added loot and respect datapacks which suppress the ordinary result.
        if (event.getDrops().stream().noneMatch(FlintGravelDrops::isOrdinaryDrop)) {
            return;
        }
        int fortune = event.getTool().getEnchantmentLevel(enchantments.getHolderOrThrow(Enchantments.FORTUNE));
        var result = FlintGravelRules.roll(fortune, event.getLevel().random::nextInt);
        ItemStack stack = switch (result) {
            case GRAVEL -> new ItemStack(Items.GRAVEL);
            case FLINT_CHIP -> new ItemStack(MiteItems.FLINT_CHIP.get());
            case FLINT -> new ItemStack(Items.FLINT);
        };
        event.getDrops().removeIf(FlintGravelDrops::isOrdinaryDrop);
        var pos = event.getPos();
        ItemEntity drop = new ItemEntity(event.getLevel(), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    private static boolean isOrdinaryDrop(ItemEntity entity) {
        return entity.getItem().is(Items.GRAVEL) || entity.getItem().is(Items.FLINT);
    }
}
