package io.github.ningmeng123.mite.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** tr.java:169..184: rusty-arrow and bone draws, inside the native cancellable death-loot flow. @author NingMeng123 */
public final class SkeletonArrowLootModifier extends LootModifier {
    public static final MapCodec<SkeletonArrowLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, SkeletonArrowLootModifier::new));
    private static final ResourceLocation TABLE = ResourceLocation.withDefaultNamespace("entities/skeleton");
    public SkeletonArrowLootModifier(LootItemCondition[] conditions) { super(conditions); }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        var entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof LivingEntity skeleton) || entity.getType() != EntityType.SKELETON
                || !TABLE.equals(context.getQueriedLootTableId())) return loot;
        loot.removeIf(stack -> stack.is(Items.BONE) || MiteRangedItems.arrowMaterial(stack) == Material.FLINT);
        var attacker = context.getParamOrNull(LootContextParams.ATTACKING_ENTITY);
        int looting = attacker instanceof LivingEntity living ? EnchantmentHelper.getEnchantmentLevel(
                context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), living) : 0;
        boolean recentlyHit = context.hasParam(LootContextParams.LAST_DAMAGE_PLAYER);
        var random = skeleton.getRandom();
        int arrows = random.nextInt(2 + Math.max(looting, 0));
        if (arrows > 0 && !recentlyHit) arrows -= random.nextInt(arrows + 1);
        int bones = random.nextInt(3);
        if (bones > 0 && !recentlyHit) bones -= random.nextInt(bones + 1);
        if (arrows > 0) loot.add(new ItemStack(MiteRangedItems.arrow(Material.RUSTED_IRON), arrows));
        if (bones > 0) loot.add(new ItemStack(Items.BONE, bones));
        return loot;
    }
}
