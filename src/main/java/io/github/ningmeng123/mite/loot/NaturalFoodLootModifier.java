package io.github.ningmeng123.mite.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Source worms, sequential leaf drops, zombie-villager onions and offshore large fish. @author NingMeng123 */
public final class NaturalFoodLootModifier extends LootModifier {
    public static final MapCodec<NaturalFoodLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, NaturalFoodLootModifier::new));
    public NaturalFoodLootModifier(LootItemCondition[] conditions) { super(conditions); }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        var level = context.getLevel();
        var state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        var entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        var tool = context.getParamOrNull(LootContextParams.TOOL);
        BlockPos pos = origin == null ? BlockPos.ZERO : BlockPos.containing(origin);
        if (state != null && (state.is(MiteFoodPlants.ONIONS.get()) || state.is(MiteFoodPlants.BLUEBERRY_BUSH.get()))
                && context.hasParam(LootContextParams.EXPLOSION_RADIUS)) { loot.clear(); return loot; }
        if (state != null && state.is(Blocks.GRASS_BLOCK) && entity instanceof Player
                && !level.getBiome(pos).value().coldEnoughToSnow(pos)
                && loot.stream().noneMatch(stack -> stack.is(Blocks.GRASS_BLOCK.asItem()))) {
            var sources = tool == null ? new EnchantmentSources.Levels(0, 0) : EnchantmentSources.levels(tool,
                    level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE));
            int fortune = Math.min(3, sources.normal());
            if (level.isRainingAt(pos.above())) fortune += 12;
            fortune = Math.min(14, fortune);
            boolean worm;
            if (sources.affix() == 0) worm = context.getRandom().nextInt(16 - fortune) == 0;
            else {
                int rain = level.isRainingAt(pos.above()) ? 12 : 0;
                double baseline = 1D / (16 - rain);
                double chance = Math.clamp(1D / (16 - fortune)
                        + 1D / Math.max(1D, 16D - rain - sources.affix()) - baseline, 0D, 1D);
                worm = context.getRandom().nextFloat() < chance;
            }
            if (worm) {
                loot.removeIf(stack -> stack.is(Items.DIRT));
                loot.add(new ItemStack(MiteFoods.get("raw_worm")));
            }
        }
        if (state != null && originalLeaf(state.getBlock())) {
            // Native silk/shears drops continue to carry the actual leaf block.
            if (loot.stream().anyMatch(stack -> stack.is(state.getBlock().asItem()))) return loot;
            Block sapling = state.is(Blocks.OAK_LEAVES) ? Blocks.OAK_SAPLING : state.is(Blocks.SPRUCE_LEAVES)
                    ? Blocks.SPRUCE_SAPLING : state.is(Blocks.BIRCH_LEAVES) ? Blocks.BIRCH_SAPLING : Blocks.JUNGLE_SAPLING;
            loot.removeIf(stack -> stack.is(sapling.asItem()) || stack.is(Items.STICK) || stack.is(Items.APPLE));
            if (state.getValue(LeavesBlock.PERSISTENT) || context.hasParam(LootContextParams.EXPLOSION_RADIUS)) return loot;
            if (swamp(context, pos) && context.getRandom().nextBoolean()) return loot;
            if (context.getRandom().nextFloat() < .01F) loot.add(new ItemStack(sapling));
            else if (context.getRandom().nextFloat() < .05F) loot.add(new ItemStack(Items.STICK));
            else if (context.getRandom().nextFloat() < .005F) {
                if (state.is(Blocks.JUNGLE_LEAVES)) loot.add(new ItemStack(MiteFoods.get("banana")));
                else if (state.is(Blocks.OAK_LEAVES)) loot.add(new ItemStack(level.getBiome(pos).is(BiomeTags.IS_JUNGLE)
                        ? MiteFoods.get("orange") : Items.APPLE));
            }
        }
        if (entity instanceof ZombieVillager && context.hasParam(LootContextParams.LAST_DAMAGE_PLAYER)
                && EntityType.ZOMBIE_VILLAGER.getDefaultLootTable().location().equals(context.getQueriedLootTableId())) {
            var damage = context.getParamOrNull(LootContextParams.DAMAGE_SOURCE);
            var attacker = context.getParamOrNull(LootContextParams.ATTACKING_ENTITY);
            int looting = attacker instanceof Player player ? enchantment(context, player.getMainHandItem(), Enchantments.LOOTING) : 0;
            if ((damage == null || !damage.is(DamageTypeTags.IS_FALL)) && context.getRandom().nextInt(50) < 5 + looting * 2
                    && context.getRandom().nextInt(6) == 0) loot.add(new ItemStack(MiteFoods.get("onion")));
        }
        if (entity instanceof FishingHook hook && BuiltInLootTables.FISHING.location().equals(context.getQueriedLootTableId())) {
            for (int index = 0; index < loot.size(); index++) {
                ItemStack stack = loot.get(index);
                if ((stack.is(Items.COD) || stack.is(Items.SALMON)) && context.getRandom().nextFloat() >= .8F
                        && isOffshoreOcean(hook)) loot.set(index, new ItemStack(MiteFoods.get("raw_large_fish"), stack.getCount()));
            }
        }
        return loot;
    }
    private static int enchantment(LootContext context, ItemStack tool, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        return tool == null ? 0 : EnchantmentSources.levels(tool, context.getLevel().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key)).totalLevel();
    }
    public static boolean originalLeaf(Block block) {
        return block == Blocks.OAK_LEAVES || block == Blocks.SPRUCE_LEAVES || block == Blocks.BIRCH_LEAVES || block == Blocks.JUNGLE_LEAVES;
    }
    private static boolean swamp(LootContext context, BlockPos pos) {
        return context.getLevel().getBiome(pos).is(Biomes.SWAMP) || context.getLevel().getBiome(pos).is(Biomes.MANGROVE_SWAMP);
    }
    public static boolean isOffshoreOcean(FishingHook hook) {
        BlockPos pos = BlockPos.containing(hook.getX(), hook.getY() - .2D, hook.getZ());
        var biome = hook.level().getBiome(pos);
        if (!biome.is(BiomeTags.IS_OCEAN) || biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN)
                || !hook.level().hasChunksAt(pos.offset(-16, -3, -16), pos.offset(16, 0, 16))) return false;
        for (BlockPos sample : BlockPos.betweenClosed(pos.offset(-16, -3, -16), pos.offset(16, 0, 16))) {
            var state = hook.level().getBlockState(sample);
            if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.GRAVEL)) return false;
        }
        return true;
    }
}
