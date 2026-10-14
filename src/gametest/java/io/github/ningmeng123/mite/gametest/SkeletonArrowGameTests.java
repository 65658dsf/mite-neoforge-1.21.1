package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
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
    @GameTest(template = "ranged_027")
    public static void OrdinarySkeletonShootsRustyArrowsWhileModernVariantsKeepEffects(GameTestHelper helper) {
        Cow target = new Cow(EntityType.COW, helper.getLevel());
        target.moveTo(helper.absolutePos(new BlockPos(6, 3, 2)).getCenter());
        for (EntityType<? extends AbstractSkeleton> type : List.of(EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED)) {
            AbstractSkeleton skeleton = type.create(helper.getLevel());
            skeleton.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            skeleton.performRangedAttack(target, 1F);
            var arrows = helper.getLevel().getEntitiesOfClass(Arrow.class, skeleton.getBoundingBox().inflate(5), a -> a.getOwner() == skeleton);
            helper.assertValueEqual(arrows.size(), 1, "Native ranged attack spawns one arrow");
            ItemStack pickup = arrows.getFirst().getPickupItemStackOrigin();
            if (type == EntityType.SKELETON)
                helper.assertTrue(pickup.is(MiteRangedItems.arrow(Material.RUSTED_IRON)), "Ordinary skeleton fires source rusty ammunition");
            else helper.assertTrue(!pickup.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).equals(PotionContents.EMPTY),
                    "Stray and Bogged keep modern potion ammunition");
            arrows.forEach(Arrow::discard);
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
