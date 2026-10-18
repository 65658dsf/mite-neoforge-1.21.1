package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.progression.EnchantmentSources;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real engine paths distinguish source contributions from added enchantment levels. @author NingMeng123 */
@GameTestHolder("mite_gear")
@PrefixGameTestTemplate(false)
public final class GearEnchantmentSourceGameTests {
    private GearEnchantmentSourceGameTests() {}
    private static Holder.Reference<Enchantment> holder(GameTestHelper h, ResourceKey<Enchantment> key) {
        return h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }
    private static ItemStack stack(GameTestHelper h, Item item, ResourceKey<Enchantment> key, int normal, int bonus) {
        ItemStack stack = new ItemStack(item);
        if (normal > 0) stack.enchant(holder(h, key), normal);
        stack.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), bonus, 0L, Map.of(key.location(), bonus), 1));
        return stack;
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .00001D, message + ": " + actual + " versus " + expected);
    }
    private static LootContext context(GameTestHelper h, ItemStack tool, LegacyRandomSource random) {
        LootParams params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.BLOCK_STATE, Blocks.STONE.defaultBlockState())
                .withParameter(LootContextParams.TOOL, tool).create(LootContextParamSets.BLOCK);
        return new LootContext.Builder(params).withOptionalRandomSource(random).create(Optional.empty());
    }

    @GameTest(template = "empty")
    public static void everyNativeAndMiteEnchantmentHasAnExplicitEffectAdapter(GameTestHelper h) {
        h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders().forEach(holder -> {
            String namespace = holder.key().location().getNamespace();
            if (namespace.equals("minecraft") || namespace.equals("mite")) h.assertTrue(EnchantmentSources.isSupported(holder),
                    "The loaded native/source enchantment has a supported effect graph: " + holder.key().location());
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void efficiencyThreePlusTwoAddsFifteenInOneModifier(GameTestHelper h) {
        ItemStack pickaxe = stack(h, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 3, 2);
        double[] amount = {0D}; int[] count = {0};
        pickaxe.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.MINING_EFFICIENCY)) { amount[0] += modifier.amount(); count[0]++; }
        });
        close(h, amount[0], 15D, "Efficiency adds 3²+1 and 2²+1 instead of using level five");
        h.assertTrue(count[0] == 1, "One stable modifier ID avoids replacement or multiplied stacking");
        h.assertTrue(pickaxe.get(DataComponents.ENCHANTMENTS).getLevel(holder(h, Enchantments.EFFICIENCY)) == 3,
                "The ordinary enchantment component retains level III");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void powerKeepsBothSourceInterceptsAndOneBaseDamage(GameTestHelper h) {
        var world = h.getLevel(); var player = h.makeMockPlayer(GameType.SURVIVAL);
        var arrow = EntityType.ARROW.create(world); var cow = EntityType.COW.create(world);
        ItemStack bow = stack(h, Items.BOW, Enchantments.POWER, 2, 1);
        float damage = EnchantmentHelper.modifyDamage(world, bow, cow, world.damageSources().arrow(arrow, player), 5F);
        close(h, damage, 7.5D, "Five base plus Power II 1.5 plus independent Power I 1.0");
        arrow.discard(); cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void multiplicativeRepairAddsImprovementsWithoutMultiplyingThem(GameTestHelper h) {
        ItemStack tool = stack(h, Items.IRON_PICKAXE, Enchantments.MENDING, 1, 1);
        h.assertTrue(EnchantmentHelper.modifyDurabilityToRepairFromXp(h.getLevel(), tool, 3) == 9,
                "Three base plus two independent three-point mending improvements equals nine");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unbreakingProbabilitiesAddBeforeOneTrialPerDurabilityPoint(GameTestHelper h) {
        ItemStack tool = stack(h, Items.TRIDENT, Enchantments.UNBREAKING, 1, 1);
        h.assertTrue(EnchantmentHelper.processDurabilityChange(h.getLevel(), tool, 100) == 0,
                "Two one-half negation chances sum to certainty rather than sequential three-quarter protection");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void silkSwitchRunsOnceAndAffixesDoNotOccupyEnchantmentTags(GameTestHelper h) {
        ItemStack tool = stack(h, Items.IRON_PICKAXE, Enchantments.SILK_TOUCH, 1, 1);
        h.assertTrue(EnchantmentHelper.processBlockExperience(h.getLevel(), tool, 7) == 0, "Silk touch is one zero-XP switch");
        tool.remove(DataComponents.ENCHANTMENTS);
        h.assertTrue(!tool.isEnchanted(), "Growth alone does not occupy the ordinary enchantment component");
        h.assertTrue(EnchantmentHelper.processBlockExperience(h.getLevel(), tool, 7) == 0, "The independent silk affix remains effective");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fireAspectDurationsAddAndIgniteTheVictimOnce(GameTestHelper h) {
        var world = h.getLevel(); var player = h.makeMockPlayer(GameType.SURVIVAL); var cow = EntityType.COW.create(world);
        ItemStack sword = stack(h, Items.IRON_SWORD, Enchantments.FIRE_ASPECT, 1, 1);
        player.setItemSlot(EquipmentSlot.MAINHAND, sword);
        EnchantmentHelper.doPostAttackEffectsWithItemSource(world, cow, world.damageSources().playerAttack(player), sword);
        h.assertTrue(cow.getRemainingFireTicks() == 180, "Two source durations of 4.5 seconds add to nine seconds");
        cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void flameDurationsAddWhileTheProjectileIsIgnitedOnce(GameTestHelper h) {
        var arrow = EntityType.ARROW.create(h.getLevel());
        ItemStack bow = stack(h, Items.BOW, Enchantments.FLAME, 1, 1);
        EnchantmentHelper.onProjectileSpawned(h.getLevel(), bow, arrow, item -> {});
        h.assertTrue(arrow.getRemainingFireTicks() == 4020, "Two 100.5-second contributions ignite the arrow once for 201 seconds");
        arrow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void fortuneReplaysIndependentBonusFormulasFromOneDropStack(GameTestHelper h) {
        ItemStack tool = stack(h, Items.IRON_PICKAXE, Enchantments.FORTUNE, 2, 1);
        CountingRandom random = new CountingRandom(.45F);
        var function = (ApplyBonusCount)ApplyBonusCount.addOreBonusCount(holder(h, Enchantments.FORTUNE)).build();
        ItemStack result = function.run(new ItemStack(Items.COAL, 3), context(h, tool, random));
        h.assertTrue(result.getCount() == 12 && random.bounds.equals(List.of(4, 3)),
                "Fortune II and I make two independent formula draws from the original count");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lootProbabilityAddsOnlyEnchantingGainsAndMakesOneTrial(GameTestHelper h) {
        ItemStack tool = stack(h, Items.IRON_PICKAXE, Enchantments.FORTUNE, 2, 1);
        CountingRandom random = new CountingRandom(.45F);
        var condition = new BonusLevelTableCondition(holder(h, Enchantments.FORTUNE), List.of(.1F, .2F, .4F));
        h.assertTrue(condition.test(context(h, tool, random)), "0.1 baseline plus 0.3 and 0.1 improvements gives 0.5");
        h.assertTrue(random.floatCalls == 1, "Combined probability makes exactly one trial");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryAndAffixSourcesSurviveItemSerializationSeparately(GameTestHelper h) {
        ItemStack original = stack(h, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 3, 2);
        ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), original.save(h.getLevel().registryAccess())).orElseThrow();
        var levels = EnchantmentSources.levels(restored, holder(h, Enchantments.EFFICIENCY));
        h.assertTrue(levels.normal() == 3 && levels.affix() == 2, "Both independently stored ranks survive reload");
        h.assertTrue(GearProgression.state(restored).equals(GearProgression.state(original)), "The immutable growth component survives exactly");
        h.succeed();
    }

    private static final class CountingRandom extends LegacyRandomSource {
        private final float value;
        private int floatCalls;
        private final List<Integer> bounds = new ArrayList<>();
        private CountingRandom(float value) { super(0L); this.value = value; }
        @Override public int nextInt(int bound) { bounds.add(bound); return bound - 1; }
        @Override public float nextFloat() { floatCalls++; return value; }
    }
}
