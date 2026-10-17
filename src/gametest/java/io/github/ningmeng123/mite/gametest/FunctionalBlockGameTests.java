package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.block.MaterialAnvilBlock;
import io.github.ningmeng123.mite.block.StrongboxBlock;
import io.github.ningmeng123.mite.block.entity.MaterialAnvilBlockEntity;
import io.github.ningmeng123.mite.block.entity.StrongboxBlockEntity;
import io.github.ningmeng123.mite.crafting.MaterialAnvilRules;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mining.FunctionalBlockMining;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real menus, loot, block entities and fall transfer validate independent source values. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class FunctionalBlockGameTests {
    private static final BlockPos SITE = new BlockPos(1, 1, 1);
    private FunctionalBlockGameTests() {}
    private static Player player(GameTestHelper helper, int xp) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(SITE)));
        PlayerExperience.setTotal(player, xp);
        return player;
    }
    private static BlockPos place(GameTestHelper helper, Block block) {
        helper.setBlock(SITE.below(), Blocks.STONE);
        helper.setBlock(SITE, block);
        return helper.absolutePos(SITE);
    }
    private static ItemStack tool(String id, int damage) {
        ItemStack stack = new ItemStack(MiteTools.get(id));
        stack.setDamageValue(damage);
        return stack;
    }
    private static AnvilMenu anvil(GameTestHelper helper, Player player, Block block) {
        BlockPos pos = place(helper, block);
        return new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
    }
    @GameTest(template = "empty")
    public static void allFunctionalBlocksHaveSourceStackSizesAndValidEntities(GameTestHelper helper) {
        helper.assertValueEqual(MiteFunctionalBlocks.ANVILS.size(), 6, "Six additional metal anvils");
        helper.assertValueEqual(MiteFunctionalBlocks.STRONGBOXES.size(), 7, "Seven source strongboxes");
        for (var anvil : MiteFunctionalBlocks.ANVILS.values()) {
            BlockPos pos = place(helper, anvil.get());
            helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof MaterialAnvilBlockEntity, "Anvil has persistent entity");
            helper.assertValueEqual(new ItemStack(anvil.get()).getMaxStackSize(), 1, "Source anvil stack size");
        }
        for (var strongbox : MiteFunctionalBlocks.STRONGBOXES.values()) {
            BlockPos pos = place(helper, strongbox.get());
            var entity = (StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos);
            helper.assertValueEqual(entity.getContainerSize(), 27, "Every metal has 27 slots");
            helper.assertValueEqual(new ItemStack(strongbox.get()).getMaxStackSize(), 4, "Source strongbox stack size");
        }
        BlockPos pos = place(helper, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get());
        helper.assertTrue(helper.getLevel().getBlockEntity(pos).getType() == MiteFunctionalBlocks.EMERALD_ENTITY.get(), "Emerald entity has dedicated valid type");
        helper.assertValueEqual(new ItemStack(MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get()).getMaxStackSize(), 4, "Source emerald table stack size");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void copperMaterialRepairUsesNuggetsStopsBeforeLastFractionAndCostsNoXp(GameTestHelper helper) {
        Player player = player(helper, 0);
        AnvilMenu menu = anvil(helper, player, MiteFunctionalBlocks.ANVILS.get("copper").get());
        menu.getSlot(0).set(tool("mite:copper_pickaxe", 3450));
        menu.getSlot(1).set(new ItemStack(MiteMaterials.COPPER_NUGGET.get(), 5));
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 250, "Source unit 4800/(3*2)=800; unequal final 250 waits");
        helper.assertValueEqual(menu.repairItemCountCost, 4, "Source loop consumes four nuggets");
        helper.assertValueEqual(menu.getCost(), 0, "Source repair fee is zero");
        menu.clicked(2, 0, ClickType.PICKUP, player);
        helper.assertValueEqual(menu.getCarried().getDamageValue(), 250, "Zero-XP owner takes source repair");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "Unequal final repair leaves one nugget");
        helper.assertValueEqual(player.totalExperience, 0, "Repair charges no XP");
        helper.assertValueEqual(((MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(SITE))).damage(), 3200, "Tool repair wears anvil by restored durability");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void syncedZeroCostRepairRemainsClickableAndPredictsCorrectRemainders(GameTestHelper helper) {
        Player owner = player(helper, 0);
        AnvilMenu clientView = new AnvilMenu(1, owner.getInventory());
        clientView.getSlot(0).set(tool("mite:copper_pickaxe", 3450));
        clientView.getSlot(1).set(new ItemStack(MiteMaterials.COPPER_NUGGET.get(), 5));
        clientView.getSlot(2).set(tool("mite:copper_pickaxe", 250));
        clientView.setMaximumCost(0);
        clientView.setData(1, (4 << 1) | 1);
        helper.assertTrue(clientView.getSlot(2).mayPickup(owner), "Synchronized source zero-cost result is clickable in the client menu");
        clientView.clicked(2, 0, ClickType.PICKUP, owner);
        helper.assertValueEqual(clientView.getCarried().getDamageValue(), 250, "Predicted click takes synchronized output");
        helper.assertValueEqual(clientView.getSlot(1).getItem().getCount(), 1, "Source consumed count synchronizes prediction instead of deleting all nuggets");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void ironEquipmentRequiresEqualOrStrongerAnvilAndRejectsIngotRepair(GameTestHelper helper) {
        Player player = player(helper, 203000);
        AnvilMenu menu = anvil(helper, player, MiteFunctionalBlocks.ANVILS.get("copper").get());
        menu.getSlot(0).set(tool("minecraft:iron_pickaxe", 5000));
        menu.getSlot(1).set(new ItemStack(Items.IRON_NUGGET));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Copper capacity4 rejects iron8");
        menu = anvil(helper, player, MiteFunctionalBlocks.ANVILS.get("mithril").get());
        menu.getSlot(0).set(tool("minecraft:iron_pickaxe", 5000));
        menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "R196 repairs use nuggets, not ingots");
        menu.getSlot(1).set(new ItemStack(Items.IRON_NUGGET));
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 3400, "Higher-tier anvil repairs with iron nugget");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void combiningSourceToolsHasNoBonusNoAnvilWearAndRejectsMixedQuality(GameTestHelper helper) {
        Player player = player(helper, 203000);
        AnvilMenu menu = anvil(helper, player, Blocks.ANVIL);
        menu.getSlot(0).set(tool("minecraft:iron_pickaxe", 6000));
        menu.getSlot(1).set(tool("minecraft:iron_pickaxe", 6000));
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 2400, "3600+3600 remaining, no vanilla bonus");
        menu.clicked(2, 0, ClickType.PICKUP, player);
        helper.assertValueEqual(player.totalExperience, 203000, "Combining costs no XP");
        helper.assertValueEqual(((MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(SITE))).damage(), 0, "Source combining does not wear anvil");
        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(0).set(tool("minecraft:iron_pickaxe", 6000));
        ItemStack fine = tool("minecraft:iron_pickaxe", 6000);
        EquipmentDurability.setQuality(fine, Quality.FINE);
        menu.getSlot(1).set(fine);
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Mixed quality cannot combine");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void sourceAnvilCombinationIgnoresSecondDyeKeepsFirstColorAndCostsZeroXp(GameTestHelper helper) {
        Player owner = player(helper, 0);
        AnvilMenu menu = anvil(helper, owner, Blocks.ANVIL);
        ItemStack first = new ItemStack(Items.LEATHER_BOOTS); first.setDamageValue(6);
        first.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0xff0000, true));
        ItemStack second = new ItemStack(Items.LEATHER_BOOTS); second.setDamageValue(6);
        second.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x0000ff, true));
        menu.getSlot(0).set(first); menu.getSlot(1).set(second);
        helper.assertFalse(menu.getSlot(2).getItem().isEmpty(), "R196 va ignores NBT when comparing repair components");
        helper.assertValueEqual(menu.getSlot(2).getItem().get(DataComponents.DYED_COLOR).rgb(), 0xff0000, "First armor dye is retained");
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 4, "Two remaining points plus two remaining points, no bonus");
        menu.clicked(2, 0, ClickType.PICKUP, owner);
        helper.assertFalse(menu.getCarried().isEmpty(), "Same-quality differently dyed source repair is available at zero XP");
        helper.assertValueEqual(menu.getCarried().get(DataComponents.DYED_COLOR).rgb(), 0xff0000, "Actual pickup retains first dye");
        helper.assertValueEqual(owner.totalExperience, 0, "Combination charges zero XP");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void enchantedFirstEquipmentCanUsePristineUnenchantedSecondAndKeepsEnchantments(GameTestHelper helper) {
        Player owner = player(helper, 0);
        AnvilMenu menu = anvil(helper, owner, Blocks.ANVIL);
        ItemStack first = tool("minecraft:iron_pickaxe", 4000);
        var efficiency = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY);
        first.enchant(efficiency, 1);
        menu.getSlot(0).set(first); menu.getSlot(1).set(new ItemStack(Items.IRON_PICKAXE));
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 0, "Source permits pristine second when first is enchanted");
        helper.assertTrue(menu.getSlot(2).getItem().isEnchanted(), "Source retains first enchantment");
        ItemStack enchantedSecond = new ItemStack(Items.IRON_PICKAXE); enchantedSecond.enchant(efficiency, 1);
        menu.getSlot(1).set(enchantedSecond);
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Source never combines enchantments from second equipment");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void losingQualityAbilityAfterPreviewCannotTakeRepair(GameTestHelper helper) {
        Player player = player(helper, 203000);
        AnvilMenu menu = anvil(helper, player, Blocks.ANVIL);
        ItemStack fine = tool("minecraft:iron_pickaxe", 0);
        EquipmentDurability.setQuality(fine, Quality.FINE);
        fine.setDamageValue(10000);
        menu.getSlot(0).set(fine);
        menu.getSlot(1).set(new ItemStack(Items.IRON_NUGGET, 2));
        helper.assertFalse(menu.getSlot(2).getItem().isEmpty(), "Eligible preview exists");
        PlayerExperience.setTotal(player, 0);
        menu.clicked(2, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "Lost ability blocks ordinary click");
        helper.assertTrue(menu.quickMoveStack(player, 2).isEmpty(), "Lost ability blocks shift transfer");
        helper.assertValueEqual(menu.getSlot(0).getItem().getDamageValue(), 10000, "Rejected take preserves equipment");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 2, "Rejected take preserves nuggets");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void sourceRepairRespectsOtherModsCanceledAnvilUpdate(GameTestHelper helper) {
        Player owner = player(helper, 203000);
        AnvilMenu menu = anvil(helper, owner, Blocks.ANVIL);
        java.util.function.Consumer<net.neoforged.neoforge.event.AnvilUpdateEvent> cancel = event -> {
            if (event.getPlayer() == owner) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.AnvilUpdateEvent.class, cancel);
        try {
            menu.getSlot(0).set(tool("minecraft:iron_pickaxe", 5000));
            menu.getSlot(1).set(new ItemStack(Items.IRON_NUGGET));
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Canceled native event cannot be resurrected by source recipe");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel); }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nestedAnvilMenuUpdateCannotRestoreCanceledOuterRepair(GameTestHelper helper) {
        Player outerOwner = player(helper, 203000);
        Player nestedOwner = player(helper, 203000);
        AnvilMenu outer = anvil(helper, outerOwner, Blocks.ANVIL);
        AnvilMenu nested = new AnvilMenu(2, nestedOwner.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(SITE)));
        java.util.function.Consumer<net.neoforged.neoforge.event.AnvilUpdateEvent> listener = event -> {
            if (event.getPlayer() != outerOwner) return;
            event.setCanceled(true);
            nested.getSlot(0).set(tool("minecraft:iron_pickaxe", 5000));
            nested.getSlot(1).set(new ItemStack(Items.IRON_NUGGET));
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.AnvilUpdateEvent.class, listener);
        try {
            outer.getSlot(0).set(tool("minecraft:iron_pickaxe", 5000));
            outer.getSlot(1).set(new ItemStack(Items.IRON_NUGGET));
            helper.assertFalse(nested.getSlot(2).getItem().isEmpty(), "Listener updates a real second anvil menu");
            helper.assertTrue(outer.getSlot(2).getItem().isEmpty(), "Nested uncanceled event cannot replace the outer canceled event");
            outer.clicked(2, 0, ClickType.PICKUP, outerOwner);
            helper.assertTrue(outer.getCarried().isEmpty(), "Canceled outer result remains untakeable after another nested refresh");
            helper.assertValueEqual(outer.getSlot(0).getItem().getDamageValue(), 5000, "Canceled update preserves first input");
            helper.assertValueEqual(outer.getSlot(1).getItem().getCount(), 1, "Canceled update preserves material input");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nestedAnvilMenuUpdateCannotOverwriteOtherModsOuterOutput(GameTestHelper helper) {
        Player outerOwner = player(helper, 203000);
        Player nestedOwner = player(helper, 203000);
        AnvilMenu outer = anvil(helper, outerOwner, Blocks.ANVIL);
        AnvilMenu nested = new AnvilMenu(2, nestedOwner.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(SITE)));
        java.util.function.Consumer<net.neoforged.neoforge.event.AnvilUpdateEvent> listener = event -> {
            if (event.getPlayer() != outerOwner) return;
            event.setOutput(new ItemStack(Items.DIAMOND));
            event.setCost(7);
            event.setMaterialCost(1);
            nested.getSlot(0).set(tool("minecraft:iron_pickaxe", 5000));
            nested.getSlot(1).set(new ItemStack(Items.IRON_NUGGET));
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.AnvilUpdateEvent.class, listener);
        try {
            outer.getSlot(0).set(tool("minecraft:iron_pickaxe", 5000));
            outer.getSlot(1).set(new ItemStack(Items.IRON_NUGGET));
            helper.assertFalse(nested.getSlot(2).getItem().isEmpty(), "Listener updates a real second anvil menu");
            helper.assertTrue(outer.getSlot(2).getItem().is(Items.DIAMOND), "Nested source repair cannot replace the outer mod's output");
            helper.assertValueEqual(outer.getCost(), 7, "Outer custom operation retains its own fee");
            outer.createResult();
            helper.assertTrue(outer.getSlot(2).getItem().is(Items.DIAMOND), "Scoped event identity survives repeated nested recomputation");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void armorAndFishingRodRepairsUseIndependentSourceWearRatios(GameTestHelper helper) {
        Player player = player(helper, 203000);
        AnvilMenu menu = anvil(helper, player, Blocks.ANVIL);
        ItemStack boots = new ItemStack(Items.IRON_BOOTS); boots.setDamageValue(23);
        menu.getSlot(0).set(boots); menu.getSlot(1).set(new ItemStack(Items.IRON_NUGGET));
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 15, "Iron boots64/(4*2)=8 per nugget");
        menu.clicked(2, 0, ClickType.PICKUP, player);
        var entity = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(SITE));
        helper.assertValueEqual(entity.damage(), 1600, "Armor repair multiplier is 12800/64=200");
        menu.setCarried(ItemStack.EMPTY);
        ItemStack rod = new ItemStack(Items.FISHING_ROD); rod.setDamageValue(10);
        menu.getSlot(0).set(rod); menu.getSlot(1).set(new ItemStack(Items.IRON_NUGGET));
        helper.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(), 0, "Source iron hook fully repairs with one nugget");
        menu.clicked(2, 0, ClickType.PICKUP, player);
        helper.assertValueEqual(entity.damage(), 1820, "Fishing rod multiplier is integer200/9=22");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void anvilWearStageReloadHarvestAndItemComponentPreserveExactWear(GameTestHelper helper) {
        Player owner = player(helper, 0);
        BlockPos pos = place(helper, MiteFunctionalBlocks.ANVILS.get("copper").get());
        var entity = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos);
        entity.addDamage(99201);
        helper.assertValueEqual(helper.getLevel().getBlockState(pos).getValue(MaterialAnvilBlock.DAMAGE_STAGE), 1, "50 percent copper wear stage");
        CompoundTag saved = entity.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = (MaterialAnvilBlockEntity)BlockEntity.loadStatic(pos, helper.getLevel().getBlockState(pos), saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.damage(), 99201, "Reload retains exact wear");
        List<ItemStack> drops = Block.getDrops(helper.getLevel().getBlockState(pos), helper.getLevel(), pos, entity, owner, ItemStack.EMPTY);
        helper.assertValueEqual(drops.size(), 1, "Portable anvil has one loot stack");
        helper.assertValueEqual(drops.getFirst().getOrDefault(MiteFunctionalBlocks.ANVIL_DAMAGE.get(), 0), 99201, "Harvest copies exact damage component");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeIronAnvilStagesRetainEntityDamageAndBreakDeterministically(GameTestHelper helper) {
        BlockPos pos = place(helper, Blocks.ANVIL);
        var entity = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos);
        helper.assertValueEqual(entity.durability(), 396800, "Source iron anvil lifetime");
        entity.addDamage(198400);
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.CHIPPED_ANVIL), "Iron retains native chipped identity");
        entity = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos);
        helper.assertValueEqual(entity.damage(), 198400, "Identity transition preserves wear");
        entity.addDamage(119040);
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.DAMAGED_ANVIL), "80 percent worn identity");
        entity = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(pos);
        entity.addDamage(79360);
        helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "Full source wear destroys anvil");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void fallingAnvilCarriesExactDamageAcrossLanding(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(SITE, Blocks.AIR);
        helper.setBlock(SITE.above(), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 3, 1), MiteFunctionalBlocks.ANVILS.get("copper").get());
        BlockPos from = helper.absolutePos(new BlockPos(1, 3, 1));
        var entity = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(from);
        entity.addDamage(40000);
        FallingBlockEntity falling = FallingBlockEntity.fall(helper.getLevel(), from, entity.getBlockState());
        helper.assertValueEqual(falling.blockData.getInt("mite_damage"), 40000, "Fall snapshots entity before removal");
        // The sparse headless fixture has no nearby player entity-ticking ticket. Use the
        // actual server entity tick path, including tickCount, physics, collision and landing.
        for (int tick = 0; tick < 60 && !falling.isRemoved(); tick++) helper.getLevel().tickNonPassenger(falling);
        var landed = (MaterialAnvilBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(SITE));
        helper.assertTrue(landed != null, "Anvil lands on test floor");
        helper.assertValueEqual(landed.damage(), 40000, "Landing preserves exact wear");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void strongboxOwnerIsSavedByUuidAndControlsActualMenuAccess(GameTestHelper helper) {
        Player owner = player(helper, 0);
        Player stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StrongboxOther"));
        BlockPos pos = place(helper, MiteFunctionalBlocks.STRONGBOXES.get("copper").get());
        var entity = (StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos);
        entity.setOwner(owner); entity.setItem(0, new ItemStack(Items.DIAMOND, 3));
        helper.assertTrue(entity.createMenu(1, owner.getInventory(), owner) != null, "Owner opens actual container");
        helper.assertTrue(entity.createMenu(2, stranger.getInventory(), stranger) == null, "Stranger cannot open actual container");
        helper.assertFalse(entity.stillValid(stranger), "Stranger cannot retain forged container access");
        CompoundTag saved = entity.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = (StrongboxBlockEntity)BlockEntity.loadStatic(pos, entity.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.isOwner(owner), "Reload retains UUID owner");
        helper.assertValueEqual(restored.getItem(0).getCount(), 3, "Reload retains contents");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void adjacentStrongboxesNeverMergeAndOnlyOwnerGetsBoxLoot(GameTestHelper helper) {
        Player owner = player(helper, 0);
        Player stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BoxLootOther"));
        Block block = MiteFunctionalBlocks.STRONGBOXES.get("iron").get();
        BlockPos pos = place(helper, block);
        helper.getLevel().setBlockAndUpdate(pos.east(), block.defaultBlockState());
        var entity = (StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos); entity.setOwner(owner);
        helper.assertValueEqual(helper.getLevel().getBlockState(pos).getValue(ChestBlock.TYPE), ChestType.SINGLE, "Strongboxes remain single");
        helper.assertValueEqual(ChestBlock.getContainer((ChestBlock)block, entity.getBlockState(), helper.getLevel(), pos, true).getContainerSize(), 27, "Adjacent strongbox has 27-slot inventory");
        helper.assertValueEqual(Block.getDrops(entity.getBlockState(), helper.getLevel(), pos, entity, owner, ItemStack.EMPTY).size(), 1, "Owner harvest gets box");
        helper.assertTrue(Block.getDrops(entity.getBlockState(), helper.getLevel(), pos, entity, stranger, ItemStack.EMPTY).isEmpty(), "Nonowner harvest gets no box");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void sittingCatBlocksStrongboxMenuAndContainerUntilItStands(GameTestHelper helper) {
        Player owner = player(helper, 0);
        Block block = MiteFunctionalBlocks.STRONGBOXES.get("iron").get();
        BlockPos pos = place(helper, block);
        helper.setBlock(SITE.above(), Blocks.AIR);
        var entity = (StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos); entity.setOwner(owner);
        var state = entity.getBlockState();
        helper.assertTrue(state.getMenuProvider(helper.getLevel(), pos) != null, "Unobstructed strongbox menu opens before adding a cat");
        helper.assertTrue(ChestBlock.getContainer((ChestBlock)block, state, helper.getLevel(), pos, false) != null, "Unobstructed strongbox has ordinary container access before adding a cat");
        var cat = net.minecraft.world.entity.EntityType.CAT.create(helper.getLevel());
        helper.assertTrue(cat != null, "Real cat entity is available");
        cat.moveTo(Vec3.atBottomCenterOf(pos.above()));
        cat.setInSittingPose(true);
        helper.getLevel().addFreshEntity(cat);
        helper.assertTrue(state.getMenuProvider(helper.getLevel(), pos) == null, "Sitting cat blocks the real strongbox menu provider");
        helper.assertTrue(ChestBlock.getContainer((ChestBlock)block, state, helper.getLevel(), pos, false) == null, "Sitting cat blocks non-bypass container combination");
        helper.assertTrue(ChestBlock.getContainer((ChestBlock)block, state, helper.getLevel(), pos, true) != null, "Native ignore-blocked automation behavior remains available");
        cat.setInSittingPose(false);
        helper.assertTrue(state.getMenuProvider(helper.getLevel(), pos) != null, "Standing cat restores the owner menu");
        helper.assertTrue(ChestBlock.getContainer((ChestBlock)block, state, helper.getLevel(), pos, false) != null, "Standing cat restores ordinary container combination");
        cat.discard();
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void strongboxRemovalSpillsContentsWithoutGrantingNonownerBox(GameTestHelper helper) {
        Player owner = player(helper, 0);
        Player stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BoxRemoval"));
        BlockPos pos = place(helper, MiteFunctionalBlocks.STRONGBOXES.get("copper").get());
        var entity = (StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos); entity.setOwner(owner);
        entity.setItem(0, new ItemStack(Items.APPLE, 7));
        helper.getLevel().destroyBlock(pos, true, stranger);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        helper.assertValueEqual(drops.stream().filter(drop -> drop.getItem().is(Items.APPLE)).mapToInt(drop -> drop.getItem().getCount()).sum(), 7, "Removal spills contents exactly once");
        helper.assertTrue(drops.stream().noneMatch(drop -> drop.getItem().is(MiteFunctionalBlocks.STRONGBOXES.get("copper").get().asItem())), "Stranger gets no reusable box");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void realSurvivalGameModeLetsOwnerHarvestStrongboxWithBareHands(GameTestHelper helper) {
        var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BoxBareHandOwner"));
        owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos pos = place(helper, MiteFunctionalBlocks.STRONGBOXES.get("adamantium").get());
        owner.moveTo(Vec3.atCenterOf(pos));
        var entity = (StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos); entity.setOwner(owner);
        entity.setItem(0, new ItemStack(Items.APPLE, 3));
        helper.assertTrue(owner.gameMode.destroyBlock(pos), "Real survival removal accepts owner bare hands");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        helper.assertValueEqual(drops.stream().filter(drop -> drop.getItem().is(MiteFunctionalBlocks.STRONGBOXES.get("adamantium").get().asItem())).mapToInt(drop -> drop.getItem().getCount()).sum(), 1, "Owner obtains exactly one source box through final native harvest");
        helper.assertValueEqual(drops.stream().filter(drop -> drop.getItem().is(Items.APPLE)).mapToInt(drop -> drop.getItem().getCount()).sum(), 3, "Contents spill exactly once");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void realStrongboxRandomTickCompostsOneStoredUnitWithoutConsumingWormOrBowlFood(GameTestHelper helper) {
        BlockPos pos = place(helper, MiteFunctionalBlocks.STRONGBOXES.get("silver").get());
        var entity = (StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos);
        var level = helper.getLevel();
        var state = level.getBlockState(pos);
        helper.assertTrue(state.isRandomlyTicking(), "Source strongbox participates in native block random ticking");
        entity.setItem(0, new ItemStack(io.github.ningmeng123.mite.registry.MiteFoods.get("raw_worm")));
        entity.setItem(1, new ItemStack(Items.MUSHROOM_STEW));
        entity.getPersistentData().putFloat(io.github.ningmeng123.mite.survival.ChestWormComposting.COMPOST_KEY, 1F);
        state.randomTick(level, pos, net.minecraft.util.RandomSource.create(196L));
        helper.assertValueEqual(entity.countItem(io.github.ningmeng123.mite.registry.MiteFoods.get("manure")), 1, "One stored compost unit becomes exactly one manure");
        helper.assertValueEqual(entity.countItem(io.github.ningmeng123.mite.registry.MiteFoods.get("raw_worm")), 1, "Random ticking retains the worm");
        helper.assertValueEqual(entity.countItem(Items.MUSHROOM_STEW), 1, "Flushing stored compost does not consume extra soup");
        helper.assertValueEqual(entity.countItem(Items.BOWL), 0, "No extra soup container is created");
        helper.assertValueEqual(entity.getPersistentData().getFloat(io.github.ningmeng123.mite.survival.ChestWormComposting.COMPOST_KEY), 0F, "Consumed stored progress is cleared");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void fullStrongboxCompostingPreservesSoupContainerAndDoesNotManufactureManure(GameTestHelper helper) {
        BlockPos pos = place(helper, MiteFunctionalBlocks.STRONGBOXES.get("gold").get());
        var entity = (StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos);
        entity.setItem(0, new ItemStack(io.github.ningmeng123.mite.registry.MiteFoods.get("raw_worm")));
        entity.setItem(1, new ItemStack(Items.MUSHROOM_STEW));
        for (int slot = 2; slot < entity.getContainerSize(); slot++) entity.setItem(slot, new ItemStack(Items.STONE, 64));
        io.github.ningmeng123.mite.survival.ChestWormComposting.process(entity, new net.minecraft.world.level.levelgen.LegacyRandomSource(196L) {
            @Override public int nextInt(int bound) { return 0; }
        });
        helper.assertValueEqual(entity.countItem(Items.MUSHROOM_STEW), 1, "Full strongbox cannot discard soup's bowl to feed worms");
        helper.assertValueEqual(entity.countItem(Items.BOWL), 0, "Rejected composting creates no free bowl");
        helper.assertValueEqual(entity.countItem(io.github.ningmeng123.mite.registry.MiteFoods.get("manure")), 0, "Rejected composting creates no free manure");
        helper.assertValueEqual(entity.getPersistentData().getFloat(io.github.ningmeng123.mite.survival.ChestWormComposting.COMPOST_KEY), 0F, "Rejected composting adds no duplicated progress");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void portableAnvilAndOwnedStrongboxKeepMinimumStrengthWithoutToolWear(GameTestHelper helper) {
        Player owner = player(helper, 0);
        BlockPos pos = place(helper, Blocks.ANVIL);
        var state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(owner.hasCorrectToolForDrops(state), "Bare hands can move source anvil");
        helper.assertTrue(FunctionalBlockMining.portable(owner, state, helper.getLevel(), pos), "Native iron is portable");
        helper.assertTrue(Math.abs(FunctionalBlockMining.portableStrength(owner, state, helper.getLevel(), pos, .2F) - 38.4F) < .0001F, "Portable minimum is4*9.6");
        pos = place(helper, MiteFunctionalBlocks.STRONGBOXES.get("mithril").get());
        ((StrongboxBlockEntity)helper.getLevel().getBlockEntity(pos)).setOwner(owner);
        helper.assertFalse(FunctionalBlockMining.blocked(owner, helper.getLevel().getBlockState(pos), pos), "Owner has no source harvest-tier barrier");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void emeraldTableGeneratesSourcePowerAndPointPaidEnchantmentInteraction(GameTestHelper helper) {
        var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EmeraldEnchantOwner"));
        owner.setGameMode(GameType.SURVIVAL);
        owner.moveTo(helper.absolutePos(SITE).getCenter());
        PlayerExperience.setTotal(owner, 203000);
        BlockPos pos = place(helper, MiteFunctionalBlocks.EMERALD_ENCHANTING_TABLE.get());
        // Clear both transmitter rings explicitly; neighboring batch fixtures or template
        // scaffolding must not silently turn a 24-shelf test into a smaller installation.
        for (int y = 0; y < 2; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (y != 0 || x != 0 || z != 0) helper.getLevel().setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
        for (int y = 0; y < 2; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            if (Math.abs(x) == 2 || Math.abs(z) == 2) helper.getLevel().setBlockAndUpdate(pos.offset(x, y, z), Blocks.BOOKSHELF.defaultBlockState());
        long accessible = net.minecraft.world.level.block.EnchantingTableBlock.BOOKSHELF_OFFSETS.stream()
                .filter(offset -> net.minecraft.world.level.block.EnchantingTableBlock.isValidBookShelf(helper.getLevel(), pos, offset)).count();
        helper.assertTrue(accessible >= 24, "Fixture provides at least24 accessible shelves before verifying source cap");
        EnchantmentMenu menu = new EnchantmentMenu(1, owner.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        helper.assertTrue(menu.stillValid(owner), "Native enchantment menu accepts added emerald table");
        menu.getSlot(0).set(new ItemStack(MiteTools.get("mite:copper_pickaxe")));
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        helper.assertValueEqual(menu.costs[2], 40, "Capped24 shelves power50 adjusted for copper enchantability30 =40");
        int pointCost = menu.costs[0] * 100;
        helper.assertTrue(menu.clickMenuButton(owner, 0), "Actual lower option enchants");
        helper.assertTrue(menu.getSlot(0).getItem().isEnchanted(), "R196 enchantment selection applies to source tool");
        helper.assertValueEqual(owner.totalExperience, 203000 - pointCost, "Actual selection pays exact source points");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 2, "First option consumes one lapis alongside source XP points");
        helper.succeed();
    }
}
