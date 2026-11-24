package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.ranged.MaterialArrowView;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native shooting, entity loot and drop cancellation, verified against tr source draws. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SkeletonArrowGameTests {
    private SkeletonArrowGameTests() {}

    private static Arrow shoot(GameTestHelper helper, AbstractSkeleton skeleton, Cow target) {
        skeleton.performRangedAttack(target, 1F);
        var arrows = helper.getLevel().getEntitiesOfClass(Arrow.class, skeleton.getBoundingBox().inflate(5), a -> a.getOwner() == skeleton);
        helper.assertValueEqual(arrows.size(), 1, "Native ranged attack spawns one arrow");
        return arrows.getFirst();
    }

    @GameTest(template = "ranged_027")
    public static void OrdinarySkeletonShootsRustyArrowsWhileModernVariantsKeepEffects(GameTestHelper helper) {
        Cow target = new Cow(EntityType.COW, helper.getLevel());
        target.moveTo(helper.absolutePos(new BlockPos(6, 3, 2)).getCenter());
        var overworld = helper.getLevel().getServer().overworld();
        long time = overworld.getDayTime();
        try {
            overworld.setDayTime(6_000L);
            for (EntityType<? extends AbstractSkeleton> type : List.of(EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED)) {
                AbstractSkeleton skeleton = type.create(helper.getLevel());
                skeleton.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                Arrow arrow = shoot(helper, skeleton, target);
                try {
                    ItemStack pickup = arrow.getPickupItemStackOrigin();
                    if (type == EntityType.SKELETON)
                        helper.assertTrue(pickup.is(MiteRangedItems.arrow(Material.RUSTED_IRON)), "Ordinary skeleton fires source rusty ammunition");
                    else helper.assertTrue(!pickup.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).equals(PotionContents.EMPTY),
                            "Stray and Bogged keep their early potion ammunition");
                } finally { arrow.discard(); }
            }
        } finally { overworld.setDayTime(time); }
        helper.succeed();
    }

    @GameTest(template = "ranged_027")
    public static void BowMobShotsUseTheLiveDayForAmmunitionAndImpactDamage(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel();
        var overworld = level.getServer().overworld();
        long time = overworld.getDayTime();
        Cow target = new Cow(EntityType.COW, level);
        target.moveTo(helper.absolutePos(new BlockPos(6, 3, 2)).getCenter());
        Skeleton skeleton = EntityType.SKELETON.create(level);
        skeleton.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        var impact = AbstractArrow.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
        impact.setAccessible(true);
        float firstDayDamage = 0F;
        int[] days = {1, 31, 48, 64, 96, 112, 128, 144, 1};
        Material[] expected = {Material.RUSTED_IRON, Material.RUSTED_IRON, Material.COPPER, Material.IRON,
                Material.ANCIENT_METAL, Material.MITHRIL, Material.ADAMANTIUM, Material.ADAMANTIUM, Material.RUSTED_IRON};
        try {
            for (int index = 0; index < days.length; index++) {
                int day = days[index];
                overworld.setDayTime((day - 1L) * 24_000L + 6_000L);
                Arrow arrow = shoot(helper, skeleton, target);
                try {
                    helper.assertTrue(arrow.getPickupItemStackOrigin().is(MiteRangedItems.arrow(expected[index])),
                            "Existing skeleton uses the current day's material on day " + day);
                    helper.assertValueEqual(((MaterialArrowView)arrow).mite$arrowMaterial(), expected[index].id,
                            "The material used by the client renderer is synchronized on day " + day);
                    if (day <= 31) {
                        target.invulnerableTime = 0;
                        target.setHealth(target.getMaxHealth());
                        impact.invoke(arrow, new EntityHitResult(target));
                        float damage = target.getMaxHealth() - target.getHealth();
                        if (index == 0) {
                            helper.assertTrue(damage > 0F, "The native arrow impact hurts the target");
                            firstDayDamage = damage;
                        } else if (day == 31) helper.assertTrue(damage > firstDayDamage, "Damage grows with the same arrow material");
                        else helper.assertValueEqual(damage, firstDayDamage, "Returning to day one restores the initial damage");
                    }
                } finally { arrow.discard(); }
            }

            var longdead = (AbstractSkeleton)MiteMobs.type(MobKind.LONGDEAD).create(level);
            var stray = EntityType.STRAY.create(level);
            for (AbstractSkeleton archer : List.of(longdead, stray)) {
                archer.moveTo(skeleton.position());
                archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MiteWeaponContent.ANCIENT_METAL_BOW.get()));
            }
            Arrow earlyLongdead = shoot(helper, longdead, target);
            try {
                helper.assertTrue(earlyLongdead.getPickupItemStackOrigin().is(MiteRangedItems.arrow(Material.ANCIENT_METAL)),
                        "Longdead retain their original ancient metal as a minimum");
            } finally { earlyLongdead.discard(); }
            overworld.setDayTime(127L * 24_000L + 6_000L);
            for (AbstractSkeleton archer : List.of(longdead, stray)) {
                Arrow arrow = shoot(helper, archer, target);
                try {
                    helper.assertTrue(arrow.getPickupItemStackOrigin().is(MiteRangedItems.arrow(Material.ADAMANTIUM)),
                            "Reinforced bows fire the same unlocked material");
                    if (archer == stray) {
                        var contents = arrow.getPickupItemStackOrigin().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                        helper.assertTrue(contents.customEffects().stream().anyMatch(effect -> effect.is(MobEffects.MOVEMENT_SLOWDOWN)),
                                "The stronger Stray arrow keeps Slowness");
                    }
                } finally { arrow.discard(); }
            }

            var pigman = EntityType.ZOMBIFIED_PIGLIN.create(level);
            ItemStack iron = new ItemStack(MiteRangedItems.arrow(Material.IRON));
            ArrowItem ironArrow = (ArrowItem)iron.getItem();
            var pigmanShot = ironArrow.createArrow(level, iron, pigman, new ItemStack(Items.BOW));
            var crossbowShot = ironArrow.createArrow(level, iron, pigman, new ItemStack(Items.CROSSBOW));
            var playerShot = ironArrow.createArrow(level, iron, helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(Items.BOW));
            helper.assertTrue(pigmanShot.getPickupItemStackOrigin().is(MiteRangedItems.arrow(Material.ADAMANTIUM)),
                    "Direct arrow creation also upgrades the ITE pigman's bow path");
            helper.assertTrue(crossbowShot.getPickupItemStackOrigin().is(iron.getItem()), "Crossbow ammunition keeps its original material");
            helper.assertTrue(playerShot.getPickupItemStackOrigin().is(iron.getItem()), "Player ammunition keeps its original material");
        } finally {
            overworld.setDayTime(time);
        }
        helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void ActualSkeletonLootUsesSourceCountsForPlayerLootingAndEnvironmentalDeaths(GameTestHelper helper) {
        var world = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack weapon = new ItemStack(Items.IRON_SWORD);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        var table = world.getServer().reloadableRegistries().getLootTable(EntityType.SKELETON.getDefaultLootTable());
        for (int looting : new int[]{0, 3}) {
            weapon.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            if (looting > 0) weapon.enchant(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), looting);
            for (boolean recent : new boolean[]{false, true}) for (long seed = 0; seed < 32; seed++) {
                Skeleton skeleton = new Skeleton(EntityType.SKELETON, world);
                skeleton.getRandom().setSeed(seed);
                RandomSource sourceOracle = RandomSource.create(seed);
                int expectedArrows = sourceOracle.nextInt(2 + looting);
                if (expectedArrows > 0 && !recent) expectedArrows -= sourceOracle.nextInt(expectedArrows + 1);
                int expectedBones = sourceOracle.nextInt(3);
                if (expectedBones > 0 && !recent) expectedBones -= sourceOracle.nextInt(expectedBones + 1);
                var builder = new LootParams.Builder(world).withParameter(LootContextParams.THIS_ENTITY, skeleton)
                        .withParameter(LootContextParams.ORIGIN, skeleton.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, world.damageSources().playerAttack(player))
                        .withParameter(LootContextParams.ATTACKING_ENTITY, player);
                if (recent) builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
                var loot = table.getRandomItems(builder.create(LootContextParamSets.ENTITY));
                helper.assertValueEqual(count(loot, MiteRangedItems.arrow(Material.RUSTED_IRON)), expectedArrows,
                        "Real entity loot table source arrows: seed=" + seed + ", recent=" + recent + ", looting=" + looting + ", loot=" + loot);
                helper.assertValueEqual(count(loot, Items.BONE), expectedBones, "Source bone count: seed=" + seed + ", recent=" + recent + ", looting=" + looting + ", loot=" + loot);
                helper.assertValueEqual(count(loot, Items.ARROW), 0, "Vanilla flint arrows are not duplicated");
            }
        }
        helper.succeed();
    }
    private static int count(List<ItemStack> loot, net.minecraft.world.item.Item item) {
        return loot.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    @GameTest(template = "ranged_027")
    public static void NativeDeathDropsRustyArrowsAndCancellationSuppressesTheEntireDrop(GameTestHelper helper) {
        var world = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (boolean canceled : new boolean[]{false, true}) {
            Skeleton skeleton = new Skeleton(EntityType.SKELETON, world);
            skeleton.moveTo(helper.absolutePos(new BlockPos(3, 3, 3)).getCenter());
            world.addFreshEntity(skeleton);
            // Damage/knockback consumes entity RNG before death. Seed at the actual native death boundary.
            Consumer<LivingDeathEvent> death = event -> { if (event.getEntity() == skeleton) skeleton.getRandom().setSeed(0L); };
            boolean[] observed = {false};
            Consumer<LivingDropsEvent> listener = event -> {
                if (event.getEntity() != skeleton) return;
                observed[0] = true;
                helper.assertValueEqual(event.getDrops().stream().filter(e -> e.getItem().is(MiteRangedItems.arrow(Material.RUSTED_IRON)))
                        .mapToInt(e -> e.getItem().getCount()).sum(), 1, "Actual death event contains the source arrow");
                if (canceled) event.setCanceled(true);
            };
            NeoForge.EVENT_BUS.addListener(listener);
            NeoForge.EVENT_BUS.addListener(death);
            try { skeleton.hurt(world.damageSources().playerAttack(player), 1000F); }
            finally { NeoForge.EVENT_BUS.unregister(listener); NeoForge.EVENT_BUS.unregister(death); }
            helper.assertTrue(observed[0], "Actual death invokes cancellable native drops");
            var drops = world.getEntitiesOfClass(ItemEntity.class, skeleton.getBoundingBox().inflate(2),
                    e -> e.getItem().is(MiteRangedItems.arrow(Material.RUSTED_IRON)));
            helper.assertValueEqual(drops.size(), canceled ? 0 : 1, "Canceled death creates no rusty-arrow world item");
            drops.forEach(ItemEntity::discard);
            skeleton.discard();
        }
        helper.succeed();
    }
}
