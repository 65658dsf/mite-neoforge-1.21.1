package io.github.ningmeng123.mite.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.OreBlockRules;
import io.github.ningmeng123.mite.registry.MiteShards;
import io.github.ningmeng123.mite.mining.OreHarvestExperience;
import io.github.ningmeng123.mite.progression.EnchantmentSources;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** apr/aqc output and aqz's one extra copy per unit; vanilla IDs remain data-driven. @author NingMeng123 */
public final class R196OreBonusFunction extends LootItemConditionalFunction {
    public static final MapCodec<R196OreBonusFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            commonFields(instance).and(com.mojang.serialization.Codec.BOOL.optionalFieldOf("suppress_placed", false)
                    .forGetter(function -> function.suppressPlaced)).apply(instance, R196OreBonusFunction::new));
    private static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Mite.MOD_ID);
    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<R196OreBonusFunction>> TYPE =
            FUNCTIONS.register("r196_ore_bonus", () -> new LootItemFunctionType<>(CODEC));
    private final boolean suppressPlaced;
    public R196OreBonusFunction(List<LootItemCondition> conditions, boolean suppressPlaced) {
        super(conditions); this.suppressPlaced = suppressPlaced;
    }
    public static void register(IEventBus bus) { FUNCTIONS.register(bus); }
    @Override public LootItemFunctionType<R196OreBonusFunction> getType() { return TYPE.get(); }
    @Override public Set<LootContextParam<?>> getReferencedContextParams() { return Set.of(LootContextParams.TOOL, LootContextParams.BLOCK_STATE); }
    @Override protected ItemStack run(ItemStack stack, LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null) return stack;
        boolean exploded = context.hasParam(LootContextParams.EXPLOSION_RADIUS);
        var enchantments = context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (!exploded && tool != null && EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.SILK_TOUCH), tool) > 0) {
            OreHarvestExperience.record(context, 0);
            return stack;
        }
        int rewardPerUnit = 0;
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) stack = exploded ? ItemStack.EMPTY : new ItemStack(Items.COAL);
        else if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            stack = new ItemStack(exploded ? MiteShards.DIAMOND_SHARD.get() : Items.DIAMOND); rewardPerUnit = exploded ? 0 : 30;
        } else if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
            stack = new ItemStack(exploded ? MiteShards.EMERALD_SHARD.get() : Items.EMERALD); rewardPerUnit = exploded ? 0 : 20;
        } else if (state.is(Blocks.NETHER_QUARTZ_ORE)) {
            stack = new ItemStack(exploded ? MiteShards.QUARTZ_SHARD.get() : Items.QUARTZ); rewardPerUnit = exploded ? 0 : 10;
        } else if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) {
            stack = new ItemStack(Items.LAPIS_LAZULI, 3 + context.getRandom().nextInt(3)); rewardPerUnit = 5;
        } else if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE))
            stack = new ItemStack(Items.REDSTONE, 3 + context.getRandom().nextInt(3));
        int base = stack.getCount();
        OreHarvestExperience.record(context, base * rewardPerUnit);
        // BlockBreakInfo.getHarvester() is null for explosions, even when another mod supplies a tool.
        if (exploded || tool == null || stack.isEmpty() || (suppressPlaced && state.hasProperty(OreBlockRules.PLACED)
                && state.getValue(OreBlockRules.PLACED))) return stack;
        var fortune = EnchantmentSources.levels(tool, enchantments.getOrThrow(Enchantments.FORTUNE));
        if (!EnchantmentSources.present(fortune)) return stack;
        int extra = 0;
        float chance = (float)EnchantmentSources.probability(fortune, value -> (1F + value * .1F) - 1F);
        for (int unit = 0; unit < base; unit++) if (context.getRandom().nextFloat() < chance) extra++;
        stack.grow(extra);
        return stack;
    }
}
