package io.github.ningmeng123.mite.mining;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.github.ningmeng123.mite.registry.MiteResourceBlocks;
import io.github.ningmeng123.mite.rules.FlintGravelRules;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
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

/** Complete R196 player-mined gravel tree, with persistent Nether-gravel identity. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class FlintGravelDrops {
    private FlintGravelDrops() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        boolean netherGravel = event.getState().is(MiteResourceBlocks.NETHER_GRAVEL.get());
        if (event.isCanceled() || (!event.getState().is(Blocks.GRAVEL) && !netherGravel)
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
        var fortune = EnchantmentSources.levels(event.getTool(), enchantments.getHolderOrThrow(Enchantments.FORTUNE));
        var result = FlintGravelRules.roll(fortune.normal(), fortune.affix(), netherGravel, event.getLevel().random::nextInt);
        ItemStack stack = switch (result) {
            case GRAVEL -> new ItemStack(netherGravel ? MiteResourceBlocks.NETHER_GRAVEL.asItem() : Items.GRAVEL);
            case FLINT_CHIP -> new ItemStack(MiteItems.FLINT_CHIP.get());
            case FLINT -> new ItemStack(Items.FLINT);
            case COPPER_NUGGET -> new ItemStack(MiteMaterials.COPPER_NUGGET.get());
            case SILVER_NUGGET -> new ItemStack(MiteMaterials.SILVER_NUGGET.get());
            case GOLD_NUGGET -> new ItemStack(Items.GOLD_NUGGET);
            case OBSIDIAN_SHARD -> new ItemStack(MiteShards.OBSIDIAN_SHARD.get());
            case EMERALD_SHARD -> new ItemStack(MiteShards.EMERALD_SHARD.get());
            case DIAMOND_SHARD -> new ItemStack(MiteShards.DIAMOND_SHARD.get());
            case MITHRIL_NUGGET -> new ItemStack(MiteMaterials.MITHRIL_NUGGET.get());
            case ADAMANTIUM_NUGGET -> new ItemStack(MiteMaterials.ADAMANTIUM_NUGGET.get());
            case QUARTZ_SHARD -> new ItemStack(MiteShards.QUARTZ_SHARD.get());
        };
        event.getDrops().removeIf(FlintGravelDrops::isOrdinaryDrop);
        var pos = event.getPos();
        ItemEntity drop = new ItemEntity(event.getLevel(), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
        if(stack.is(Items.FLINT)||stack.is(MiteItems.FLINT_CHIP.get()))
            io.github.ningmeng123.mite.advancement.MiteAchievements.award(player,"flintFinder");
    }

    private static boolean isOrdinaryDrop(ItemEntity entity) {
        return entity.getItem().is(Items.GRAVEL) || entity.getItem().is(Items.FLINT)
                || entity.getItem().is(MiteResourceBlocks.NETHER_GRAVEL.asItem());
    }
}
