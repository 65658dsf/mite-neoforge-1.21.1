package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BlockToolModificationEvent;

/** Source per-unit harvesting bonus and post-harvest/tilling soil fertility. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class AgriculturalEnchantments {
    public static final ResourceKey<Enchantment> HARVESTING = key("harvesting");
    public static final ResourceKey<Enchantment> FERTILITY = key("fertility");
    private static final Set<net.minecraft.world.item.Item> MODERN_HOES = Set.of(Items.WOODEN_HOE, Items.STONE_HOE,
            Items.IRON_HOE, Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);
    private AgriculturalEnchantments() {}
    private static ResourceKey<Enchantment> key(String id) {
        return ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, id));
    }

    private static boolean tillingTool(ItemStack tool) {
        var definition = MiteTools.definition(tool);
        if (definition != null) return definition.family().name().equals("HOE") || definition.family().name().equals("MATTOCK");
        return MODERN_HOES.contains(tool.getItem());
    }

    public static boolean effective(ItemStack tool, BlockState state) {
        if (tool.isEmpty() || !CropLifecycle.managed(state)) return false;
        if (tillingTool(tool)) return true;
        var definition = MiteTools.definition(tool);
        return definition != null && definition.family().name().equals("SCYTHE") && state.is(Blocks.WHEAT);
    }

    private static float fraction(ItemStack tool, ServerLevel level, ResourceKey<Enchantment> key) {
        var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(key);
        return holder.map(enchantment -> (float)EnchantmentSources.number(
                EnchantmentSources.levels(tool, enchantment), value -> value / 5F)).orElse(0F);
    }

    public static float harvesting(ItemStack tool, ServerLevel level, BlockState state) {
        return effective(tool, state) ? fraction(tool, level, HARVESTING) * .5F : 0F;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fertilizeAfterHarvest(BlockDropsEvent event) {
        if (event.isCanceled() || !(event.getBreaker() instanceof Player player) || player.isCreative() || player.isSpectator()
                || !CropHarvest.isNormalHarvest(event.getLevel(), event.getPos())) return;
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof CropBlock crop) || !effective(event.getTool(), state)
                || !crop.isMaxAge(state) || CubeCropBlight.dead(state)) return;
        float chance = Math.min(1F, fraction(event.getTool(), event.getLevel(), FERTILITY));
        if (chance > 0F && event.getLevel().random.nextFloat() < chance) {
            BlockPos soil = event.getPos().below(); BlockState below = event.getLevel().getBlockState(soil);
            if (below.hasProperty(FoodAgriculture.FERTILIZED))
                event.getLevel().setBlock(soil, below.setValue(FoodAgriculture.FERTILIZED, true), 2);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fertilizeTilledSoil(BlockToolModificationEvent event) {
        if (event.isCanceled() || event.isSimulated() || !(event.getLevel() instanceof ServerLevel level)
                || event.getItemAbility() != ItemAbilities.HOE_TILL || !tillingTool(event.getHeldItemStack())) return;
        BlockState result = event.getFinalState(); Player player = event.getPlayer();
        // NeoForge posts this event before computing its native fallback. An unchanged dirt state
        // therefore needs the direct block extension, which does not recursively post this event.
        if (result == event.getState() && (result.is(Blocks.DIRT) || result.is(Blocks.GRASS_BLOCK) || result.is(Blocks.DIRT_PATH)))
            result = result.getBlock().getToolModifiedState(result, event.getContext(), ItemAbilities.HOE_TILL, false);
        if (result == null || !result.is(Blocks.FARMLAND) || !result.hasProperty(FoodAgriculture.FERTILIZED)
                || player == null || player.isSpectator() || !level.mayInteract(player, event.getPos())
                || !player.mayUseItemAt(event.getPos(), event.getContext().getClickedFace(), event.getHeldItemStack())) return;
        float chance = Math.min(1F, fraction(event.getHeldItemStack(), level, FERTILITY));
        if (chance > 0F && level.random.nextFloat() < chance)
            event.setFinalState(result.setValue(FoodAgriculture.FERTILIZED, true));
    }
}
