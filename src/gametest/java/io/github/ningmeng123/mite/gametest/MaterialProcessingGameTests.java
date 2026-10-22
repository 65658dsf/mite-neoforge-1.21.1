package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.item.RemovedDiamondEquipment;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder("mite_feedback")
@PrefixGameTestTemplate(false)
public final class MaterialProcessingGameTests {
    private MaterialProcessingGameTests() {}

    @GameTest(template = "empty")
    public static void sugarCaneUsesSourceHardness(GameTestHelper helper) {
        helper.assertTrue(MiningProgress.effectiveHardness(Blocks.SUGAR_CANE.defaultBlockState(), 0F) == .08F,
                "R196 aqz:2343 gives sugar cane nonzero .08 hardness");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sugarAndClayWaitForTheirWholeSourcePeriods(GameTestHelper helper) {
        for (boolean clay : new boolean[]{false, true}) try (Session session = new Session(helper)) {
            var player = session.player;
            var menu = player.inventoryMenu;
            var grid = menu.getCraftSlots();
            Item ingredient = clay ? Items.CLAY_BALL : Items.SUGAR_CANE;
            Item result = clay ? Items.CLAY : Items.SUGAR;
            int slots = clay ? 4 : 1;
            for (int slot = 0; slot < slots; slot++) grid.setItem(slot, new ItemStack(ingredient, 2));
            helper.assertTrue(menu.getSlot(0).getItem().is(result), "The native material recipe remains visible");
            helper.assertValueEqual(TimedCraftingController.period(player, menu), clay ? 100 : 289,
                    "At level zero the whole batch uses the R196 100/800 difficulty period");
            TimedCraftingTestSupport.craftOne(helper, player, menu, ClickType.PICKUP);
            helper.assertValueEqual(player.getInventory().countItem(result), 1, "One finished batch produces one item");
            for (int slot = 0; slot < slots; slot++) helper.assertValueEqual(grid.getItem(slot).getCount(), 1,
                    "One complete batch consumes one item per occupied slot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stoneEquipmentHasNoRecipesOrSavedUsableStacks(GameTestHelper helper) {
        for (Item item : List.of(Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_PICKAXE, Items.STONE_SHOVEL, Items.STONE_HOE)) {
            helper.assertTrue(RemovedDiamondEquipment.isRemoved(item), "Stone equipment uses all existing retired-item filters");
            helper.assertTrue(helper.getLevel().getRecipeManager().getRecipes().stream().noneMatch(holder ->
                    holder.value().getResultItem(helper.getLevel().registryAccess()).is(item)), "No loaded recipe produces stone gear");
            var encoded = new ItemStack(item).save(helper.getLevel().registryAccess());
            helper.assertTrue(ItemStack.parseOptional(helper.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag)encoded).isEmpty(),
                    "Old saved stone equipment cannot return as a usable stack");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void naturalWitherSkeletonRetainsItsSourceIronSwordAfterJoiningAndSaving(GameTestHelper helper) {
        var skeleton = EntityType.WITHER_SKELETON.create(helper.getLevel());
        var position = helper.absolutePos(new net.minecraft.core.BlockPos(2, 3, 2));
        skeleton.moveTo(position.getCenter());
        skeleton.setNoAi(true);
        skeleton.setNoGravity(true);
        skeleton.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(position), MobSpawnType.NATURAL, null);
        helper.assertTrue(skeleton.getMainHandItem().is(Items.IRON_SWORD), "Native natural finalization equips the R196 iron sword");
        helper.assertValueEqual(EquipmentDurability.quality(skeleton.getMainHandItem()), Quality.POOR, "The source poor weapon quality survives replacement");
        helper.assertTrue(skeleton.getMainHandItem().getDamageValue() > 0, "The replacement receives source generated wear");
        helper.assertTrue(helper.getLevel().addFreshEntity(skeleton), "The native generated skeleton enters the level");
        helper.assertTrue(skeleton.getMainHandItem().is(Items.IRON_SWORD), "The retired-item join filter preserves the usable replacement");
        var saved = new net.minecraft.nbt.CompoundTag();
        skeleton.saveWithoutId(saved);
        var restored = EntityType.WITHER_SKELETON.create(helper.getLevel());
        restored.load(saved);
        helper.assertTrue(ItemStack.isSameItemSameComponents(skeleton.getMainHandItem(), restored.getMainHandItem()),
                "Loading the saved skeleton retains its exact generated sword without rerolling");
        skeleton.discard();
        restored.discard();
        helper.succeed();
    }

    private static final class Session implements AutoCloseable {
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private Session(GameTestHelper helper) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "material-test"), false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.setOnGround(true);
            player.getInventory().clearContent();
            PlayerExperience.setTotal(player, 0);
            player.getFoodData().setFoodLevel(6);
        }
        @Override public void close() {
            TimedCraftingController.cancel(player);
            player.inventoryMenu.getCraftSlots().clearContent();
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
