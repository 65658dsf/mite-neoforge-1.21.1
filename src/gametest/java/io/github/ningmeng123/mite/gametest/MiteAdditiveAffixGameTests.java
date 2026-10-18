package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.enchantment.RangedEnchantments;
import io.github.ningmeng123.mite.enchantment.SourceEnchantmentCombat;
import io.github.ningmeng123.mite.entity.SourceVanillaMobLoot;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules;
import io.github.ningmeng123.mite.ranged.SourceBowLaunch;
import io.github.ningmeng123.mite.rules.R196WeaponRules;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real MITE consumers evaluate independently stored affixes without changing ordinary components. @author NingMeng123 */
@GameTestHolder("mite_gear")
@PrefixGameTestTemplate(false)
public final class MiteAdditiveAffixGameTests {
    private MiteAdditiveAffixGameTests() {}

    private static ItemStack gear(GameTestHelper h, Item item, String id, int normal, int affix) {
        ItemStack stack = new ItemStack(item);
        var key = R196Enchantments.key(id);
        if (normal > 0) stack.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key), normal);
        if (affix > 0) stack.set(GearProgression.PROGRESS.get(),
                new GearProgress(UUID.randomUUID(), Math.min(affix, 25), 0, Map.of(key.location(), affix), 1));
        return stack;
    }
    private static void close(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 1E-5D, message + ": " + actual + " vs " + expected);
    }

    @GameTest(template = "empty", batch = "gear_affixes")
    public static void poisonAddsWholeDurationsAndRollsOneMergedChance(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = gear(h, Items.BOW, "poison", 1, 2);
        var ordinary = bow.get(DataComponents.ENCHANTMENTS);
        var target = EntityType.COW.create(h.getLevel());
        Arrow arrow = new Arrow(h.getLevel(), player, new ItemStack(Items.ARROW), bow);
        for (int trial = 0; trial < 100 && !target.hasEffect(MobEffects.POISON); trial++) RangedEnchantments.afterHit(arrow, target);
        var poison = target.getEffect(MobEffects.POISON);
        h.assertTrue(poison != null && poison.getDuration() == 464,
                "Poison I duration208 plus affixII duration256 gives464, not rankIII duration304");
        h.assertValueEqual(bow.get(DataComponents.ENCHANTMENTS), ordinary, "Source effects never rewrite ordinary enchantments");
        arrow.discard(); target.discard(); h.succeed();
    }

    @GameTest(template = "empty", batch = "gear_affixes")
    public static void movementCombinesEachPieceBeforeSelectingTheStrongestAndFreeActionReachesOne(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.FEET, gear(h, Items.IRON_BOOTS, "speed", 2, 3));
        player.setItemSlot(EquipmentSlot.CHEST, gear(h, Items.IRON_CHESTPLATE, "speed", 4, 0));
        player.setItemSlot(EquipmentSlot.LEGS, gear(h, Items.IRON_LEGGINGS, "free_action", 4, 1));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 5));
        SourceEnchantmentCombat.refreshMovement(player);
        close(h, R196Enchantments.freeActionResistance(player), 1D, "Ordinary80percent plus affix20percent resists all slowdown");
        close(h, player.getAttributeValue(Attributes.MOVEMENT_SPEED),
                player.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() * 1.25D,
                "The feet's combined25percent beats chest20percent without summing separate pieces");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "gear_affixes")
    public static void sourceUnbreakingCombinesProbabilityBeforeExactlyOneRollPerPoint(GameTestHelper h) {
        ItemStack tool = gear(h, Items.IRON_PICKAXE, "minecraft:unbreaking", 4, 2);
        float chance = EquipmentDurabilityRules.unbreakingNegationChance(4) + 2F * .15F;
        RandomSource expected = RandomSource.create(3283L);
        int retained = 1000;
        for (int point = 0; point < 1000; point++) if (expected.nextFloat() < chance) retained--;
        h.getLevel().random.setSeed(3283L);
        h.assertValueEqual(R196Enchantments.unbreakingDamage(tool, h.getLevel(), 1000), retained,
                "Each point uses one summed90percent roll rather than two passes through wear");
        close(h, h.getLevel().random.nextFloat(), expected.nextFloat(), "Exactly1000 RNG draws were consumed");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "gear_affixes")
    public static void trueFlightAddsAccuracyGainsAgainstOnePlayerBaseline(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.experienceLevel = 50;
        ItemStack bow = gear(h, Items.BOW, "true_flight", 1, 1);
        double baseline = R196WeaponRules.inaccuracy(50, false);
        double single = R196WeaponRules.inaccuracy(58, false);
        close(h, SourceBowLaunch.inaccuracy(bow, player, false), 2D * single - baseline,
                "The two independent accuracy gains add, rather than evaluating virtual level66");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "gear_affixes")
    public static void spiderEyeAddsNonlinearButcheringProbabilitiesBeforeOneTrial(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.MAINHAND, gear(h, Items.IRON_SWORD, "butchering", 1, 1));
        var spider = EntityType.SPIDER.create(h.getLevel());
        spider.moveTo(h.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)).getCenter());
        try (var captured = new SpawnedEntityCapture(h.getLevel(), entity -> entity instanceof ItemEntity item
                && item.getItem().is(Items.SPIDER_EYE))) {
            for (int trial = 0; trial < 32; trial++) SourceVanillaMobLoot.fewItems(spider, player.damageSources().playerAttack(player), true);
            h.assertValueEqual(captured.live(ItemEntity.class).size(), 32,
                    "Base1/3 plus each rankI source's1/3 gain guarantees one eye per harvest, unlike summed rankII");
            captured.live(ItemEntity.class).forEach(ItemEntity::discard);
        }
        spider.discard(); h.succeed();
    }
}
