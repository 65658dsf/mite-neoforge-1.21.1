package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.curse.CurseIngestion;
import io.github.ningmeng123.mite.curse.PlayerCurses;
import io.github.ningmeng123.mite.curse.R196Curse;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.github.ningmeng123.mite.survival.FoodConsumption;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native wear, food completion, quality selection and crafting payment under world curses. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class WitchCurseManufacturingGameTests {
    @GameTest(template = "empty")
    public static void productRestrictionsUseSourceProductFlagsInsteadOfMicronutrients(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.emptyFood();
            f.curse(R196Curse.CANNOT_EAT_PLANTS);
            for (var item : new net.minecraft.world.item.Item[]{Items.BREAD, Items.BROWN_MUSHROOM,
                    Items.SUGAR, Items.PUMPKIN_PIE, MiteFoods.MASHED_POTATO.get()})
                helper.assertFalse(FoodConsumption.canIngest(f.player, new ItemStack(item)), "Plant-product curse rejects " + item);
            helper.assertTrue(FoodConsumption.canIngest(f.player, new ItemStack(Items.COOKED_BEEF)), "Meat is not a plant product");
            f.curse(R196Curse.CANNOT_EAT_ANIMALS);
            for (var item : new net.minecraft.world.item.Item[]{Items.BEEF, Items.EGG, Items.MILK_BUCKET,
                    Items.PUMPKIN_PIE, Items.HONEY_BOTTLE, MiteFoods.ICE_CREAM.get()})
                helper.assertFalse(FoodConsumption.canIngest(f.player, new ItemStack(item)), "Animal-product curse rejects " + item);
            helper.assertTrue(FoodConsumption.canIngest(f.player, new ItemStack(Items.MUSHROOM_STEW)), "Mushrooms are plant-only despite protein-free nutrition");
            helper.assertTrue(PlayerCurses.state(f.player).known(), "Trying a forbidden food discovers the effect");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void drinkRestrictionIncludesModernBottlesButExemptsDisenchantingAndThrownPotions(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.emptyFood();
            f.curse(R196Curse.CANNOT_DRINK);
            for (var item : new net.minecraft.world.item.Item[]{Items.POTION, Items.OMINOUS_BOTTLE, Items.HONEY_BOTTLE,
                    Items.MILK_BUCKET, MiteFoods.MILK_BOWL.get(), MiteFoods.WATER_BOWL.get(), MiteFoods.CHICKEN_SOUP.get()})
                helper.assertFalse(FoodConsumption.canIngest(f.player, new ItemStack(item)), "Drink curse rejects " + item);
            for (var item : new net.minecraft.world.item.Item[]{Items.MUSHROOM_STEW, MiteFoods.BEEF_STEW.get(),
                    Items.SPLASH_POTION, Items.LINGERING_POTION, Items.WATER_BUCKET, MiteUtilityContent.BOTTLE_OF_DISENCHANTING.get()})
                helper.assertTrue(CurseIngestion.allowed(f.player, new ItemStack(item)), "A non-drink action or recovery bottle remains allowed: " + item);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aCurseArrivingDuringUseCannotConsumeFoodOrApplyPotionEffects(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.emptyFood();
            ItemStack bread = new ItemStack(Items.BREAD, 2);
            f.player.setItemInHand(InteractionHand.MAIN_HAND, bread);
            bread.use(helper.getLevel(), f.player, InteractionHand.MAIN_HAND);
            helper.assertTrue(f.player.isUsingItem(), "Uncursed bread starts normal use");
            f.curse(R196Curse.CANNOT_EAT_PLANTS);
            ItemStack denied = bread.finishUsingItem(helper.getLevel(), f.player);
            helper.assertValueEqual(denied.getCount(), 2, "Final food check consumes no item after curse realization");
            helper.assertValueEqual(f.player.getFoodData().getFoodLevel(), 0, "Rejected completion adds no satiation");
            helper.assertValueEqual(((DualFoodData)f.player.getFoodData()).mite$getNutrition(), 0, "Rejected completion adds no nutrition");
            f.player.stopUsingItem();
            f.curse(R196Curse.CANNOT_DRINK);
            f.player.setHealth(1F);
            f.player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
            ItemStack potion = new ItemStack(Items.POTION);
            potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
            helper.assertTrue(potion.finishUsingItem(helper.getLevel(), f.player).is(Items.POTION), "Native potion direct finish is revalidated");
            helper.assertValueEqual(f.player.getHealth(), 1F, "Rejected potion cannot apply instant healing");
            ItemStack milk = new ItemStack(Items.MILK_BUCKET);
            helper.assertTrue(milk.finishUsingItem(helper.getLevel(), f.player).is(Items.MILK_BUCKET), "Forbidden milk is not consumed");
            helper.assertTrue(f.player.hasEffect(MobEffects.POISON), "Forbidden milk cannot clear effects before validation");
            ItemStack omen = new ItemStack(Items.OMINOUS_BOTTLE);
            helper.assertTrue(omen.finishUsingItem(helper.getLevel(), f.player).is(Items.OMINOUS_BOTTLE), "Ominous bottle is revalidated");
            helper.assertFalse(f.player.hasEffect(MobEffects.BAD_OMEN), "Forbidden ominous bottle has no side effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placedCakeChecksBothIndependentProductFlagsBeforeEachBite(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            BlockPos cake = helper.absolutePos(new BlockPos(2, 1, 1));
            BlockState previous = helper.getLevel().getBlockState(cake);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cake), Direction.UP, cake, false);
            try {
                for (R196Curse curse : new R196Curse[]{R196Curse.CANNOT_EAT_PLANTS, R196Curse.CANNOT_EAT_ANIMALS}) {
                    f.emptyFood();
                    f.curse(curse);
                    helper.getLevel().setBlock(cake, Blocks.CAKE.defaultBlockState(), 2);
                    helper.getLevel().getBlockState(cake).useWithoutItem(helper.getLevel(), f.player, hit);
                    helper.assertValueEqual(helper.getLevel().getBlockState(cake).getValue(CakeBlock.BITES), 0, "Forbidden cake bite does not modify the block");
                    helper.assertValueEqual(f.player.getFoodData().getFoodLevel(), 0, "Forbidden cake supplies no food");
                }
                PlayerCurses.cure(f.player);
                helper.getLevel().getBlockState(cake).useWithoutItem(helper.getLevel(), f.player, hit);
                helper.assertValueEqual(helper.getLevel().getBlockState(cake).getValue(CakeBlock.BITES), 1, "Lifting curse restores actual cake interaction");
            } finally { helper.getLevel().setBlock(cake, previous, 2); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void equipmentDecayDoublesNativeAndCustomWearOnceAndPreservesArmorLastPoint(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.curse(R196Curse.EQUIPMENT_DECAY);
            ItemStack nativeTool = new ItemStack(Items.IRON_PICKAXE);
            nativeTool.hurtAndBreak(11, f.player, EquipmentSlot.MAINHAND);
            helper.assertValueEqual(nativeTool.getDamageValue(), 22, "Native tool wear doubles once in the shared path");
            ItemStack customTool = new ItemStack(MiteTools.get("copper_pickaxe"));
            EquipmentDurability.hurt(customTool, 13, f.player, EquipmentSlot.MAINHAND);
            helper.assertValueEqual(customTool.getDamageValue(), 26, "Custom tool wear is not doubled by two overlapping hooks");
            ItemStack elytra = new ItemStack(Items.ELYTRA);
            elytra.hurtAndBreak(3, f.player, EquipmentSlot.CHEST);
            helper.assertValueEqual(elytra.getDamageValue(), 6, "Modern damageable equipment also follows the curse");
            ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
            f.player.setItemSlot(EquipmentSlot.CHEST, armor);
            armor.setDamageValue(armor.getMaxDamage() - 4);
            armor.hurtAndBreak(10, f.player, EquipmentSlot.CHEST);
            helper.assertFalse(armor.isEmpty(), "Ordinary worn armor still retains its final durability point");
            helper.assertValueEqual(armor.getDamageValue(), armor.getMaxDamage() - 1, "Last-point clamp follows doubled wear");
            f.player.setGameMode(GameType.CREATIVE);
            nativeTool.hurtAndBreak(11, f.player, EquipmentSlot.MAINHAND);
            helper.assertValueEqual(nativeTool.getDamageValue(), 22, "Creative mode retains its source wear exemption");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clumsinessChangesRealPreviewTimingCostAndRepairEligibility(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.curse(R196Curse.CLUMSINESS);
            f.loadPick();
            helper.assertValueEqual(EquipmentDurability.quality(f.menu.getSlot(0).getItem()), Quality.WRETCHED, "Level-zero cursed default quality uses effective level minus twenty");
            helper.assertValueEqual(TimedCraftingController.period(f.player, f.menu), 586, "D1250 doubles381 base ticks before copper bench1.3 division");
            PlayerExperience.setTotal(f.player, 999);
            ItemStack repair = new ItemStack(MiteTools.get("copper_pickaxe"));
            EquipmentDurability.setQuality(repair, Quality.FINE);
            helper.assertFalse(CraftingQuality.canRepair(f.player, repair), "999 XP cannot meet doubled1000 XP manufacture ability for repair");
            f.select(Quality.FINE);
            helper.assertFalse(TimedCraftingController.start(f.player, f.menu), "Higher quality remains visible but unaffordable");
            PlayerExperience.setTotal(f.player, 1000);
            helper.assertTrue(CraftingQuality.canRepair(f.player, repair), "Repair ability uses exact doubled threshold");
            helper.assertValueEqual(TimedCraftingController.period(f.player, f.menu), 787, "Fine D2500 uses1212/1.54 after actual level12 and copper bench bonus");
            helper.assertTrue(TimedCraftingController.start(f.player, f.menu), "Exact doubled XP balance starts manufacturing");
            f.tick(786);
            helper.assertValueEqual(f.player.getInventory().countItem(MiteTools.get("copper_pickaxe")), 0, "Period-minus-one cannot publish output");
            helper.assertValueEqual(f.player.totalExperience, 1000, "No fee before completion");
            f.tick(1);
            helper.assertValueEqual(f.player.getInventory().countItem(MiteTools.get("copper_pickaxe")), 1, "One real production batch completes");
            helper.assertValueEqual(f.player.totalExperience, 0, "Real completion pays doubled1000 XP once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void curseRealizedInsidePaymentApprovalCannotGrantAnUndercostedTool(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            PlayerExperience.setTotal(f.player, 500);
            f.loadPick();
            f.select(Quality.FINE);
            Consumer<PlayerXpEvent.XpChange> curse = event -> {
                if (event.getEntity() == f.player && event.getAmount() < 0) f.curse(R196Curse.CLUMSINESS);
            };
            NeoForge.EVENT_BUS.addListener(PlayerXpEvent.XpChange.class, curse);
            try {
                int period = TimedCraftingController.period(f.player, f.menu);
                helper.assertTrue(TimedCraftingController.start(f.player, f.menu), "Uncursed fine tool initially costs500");
                f.tick(period);
                helper.assertValueEqual(f.player.totalExperience, 500, "Final curse/cost recheck vetoes old payment");
                helper.assertValueEqual(f.player.getInventory().countItem(MiteTools.get("copper_pickaxe")), 0, "No undercosted cursed tool escapes");
                helper.assertValueEqual(f.menu.getSlot(1).getItem().getCount(), 1, "Ingredient remains untouched after rejected transaction");
            } finally { NeoForge.EVENT_BUS.unregister(curse); }
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final Witch witch;
        final BlockPos table;
        final BlockState original;
        final CraftingMenu menu;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-curse-craft"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(helper.absolutePos(new BlockPos(1, 2, 2)).getCenter());
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
            witch = EntityType.WITCH.create(helper.getLevel());
            if (witch == null) throw new IllegalStateException("Witch fixture could not be created");
            table = helper.absolutePos(new BlockPos(1, 1, 1));
            original = helper.getLevel().getBlockState(table);
            helper.getLevel().setBlockAndUpdate(table, MiteWorkbenches.TABLES.get("copper").get().defaultBlockState());
            menu = new CraftingMenu(91, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
            player.containerMenu = menu;
        }
        void curse(R196Curse type) {
            PlayerCurses.cure(player);
            helper.assertTrue(PlayerCurses.schedule(player, witch, type, 0), "A live witch schedules the fixture's real world curse");
            PlayerCurses.reconcile(player);
            helper.assertTrue(PlayerCurses.has(player, type), "The scheduled curse realizes through the normal lifecycle");
        }
        void emptyFood() {
            player.getFoodData().setFoodLevel(0);
            ((DualFoodData)player.getFoodData()).mite$setNutrition(0);
        }
        void loadPick() {
            for (int slot : new int[]{1, 2, 3}) menu.getSlot(slot).set(new ItemStack(Items.COPPER_INGOT));
            for (int slot : new int[]{5, 8}) menu.getSlot(slot).set(new ItemStack(Items.STICK));
            tick(1);
            helper.assertTrue(menu.getSlot(0).getItem().is(MiteTools.get("copper_pickaxe")), "Real copper-pick recipe matches");
        }
        void select(Quality wanted) {
            for (int i = 0; i < 8; i++) {
                if (EquipmentDurability.quality(menu.getSlot(0).getItem()) == wanted) return;
                TimedCraftingController.selectQuality(player, menu, 1);
            }
            helper.fail("Requested quality was not available");
        }
        void tick(int ticks) { for (int i = 0; i < ticks; i++) TimedCraftingController.tick(player); }
        @Override public void close() {
            try {
                PlayerCurses.cure(player);
                TimedCraftingController.forget(player);
                for (int i = 1; i <= 9; i++) menu.getSlot(i).set(ItemStack.EMPTY);
                player.getInventory().clearContent();
                player.containerMenu = player.inventoryMenu;
                player.server.getPlayerList().remove(player);
                witch.discard();
            } finally {
                helper.getLevel().setBlockAndUpdate(table, original);
                channel.finishAndReleaseAll();
            }
        }
    }
}
