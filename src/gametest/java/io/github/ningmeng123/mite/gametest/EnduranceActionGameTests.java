package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.enchantment.EnduranceActions;
import io.github.ningmeng123.mite.enchantment.R196Enchantments;
import io.github.ningmeng123.mite.ranged.SphereThrowing;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server action entry points verify Endurance without changing metabolism or global exhaustion. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class EnduranceActionGameTests {
    private EnduranceActionGameTests() {}

    @GameTest(template = "empty")
    public static void ordinaryExhaustionIsNotDiscountedAndCreativePaysNothing(GameTestHelper helper) {
        Player player = player(helper); equip(player, 4);
        player.causeFoodExhaustion(1F);
        effort(helper, player, 1F, "Ordinary movement/metabolism exhaustion is not globally discounted");
        player.getFoodData().setExhaustion(0F);
        EnduranceActions.melee(player);
        effort(helper, player, .06F, "Only the explicit source .3 melee fee gets the IV .2 multiplier");
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        EnduranceActions.melee(creative); EnduranceActions.throwing(creative); EnduranceActions.mining(creative);
        effort(helper, creative, 0F, "Creative actions stay free");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeAttackChargesAttemptWithoutDuplicateSuccessfulHitCost(GameTestHelper helper) {
        Player player = player(helper); equip(player, 4);
        var victim = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2)); victim.setNoAi(true);
        player.attack(victim);
        effort(helper, player, .06F, "A successful native attack pays .3*.2 exactly once");
        player.getFoodData().setExhaustion(0F); victim.setInvulnerable(true);
        player.attack(victim);
        effort(helper, player, .06F, "An allowed failed hit still pays the source attempt fee");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void acceptedPlacementChargesHardnessAndCancelledPlacementIsFree(GameTestHelper helper) {
        Player player = player(helper); equip(player, 4);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(base.above(), Blocks.AIR.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OBSIDIAN, 2));
        var result = player.getMainHandItem().useOn(context(player, base));
        helper.assertTrue(result.consumesAction() && helper.getLevel().getBlockState(base.above()).is(Blocks.OBSIDIAN), "Native placement succeeds");
        effort(helper, player, 4F, "Obsidian caps its source placement hardness at20 before Endurance IV");
        player.getFoodData().setExhaustion(0F);
        helper.getLevel().setBlockAndUpdate(base.above(), Blocks.AIR.defaultBlockState());
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, cancel);
        try {
            var denied = player.getMainHandItem().useOn(context(player, base));
            helper.assertTrue(!denied.consumesAction() && helper.getLevel().getBlockState(base.above()).isAir(), "NeoForge cancellation restores the placement snapshot");
            effort(helper, player, 0F, "A rolled-back placement pays no action fee");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceMattockAndNativeHoeShareAcceptedTillingFee(GameTestHelper helper) {
        Player player = player(helper); equip(player, 4);
        BlockPos soil = helper.absolutePos(new BlockPos(1, 1, 1));
        for (var item : new net.minecraft.world.item.Item[]{MiteTools.get("mite:copper_mattock"), Items.IRON_HOE}) {
            helper.getLevel().setBlockAndUpdate(soil, Blocks.GRASS_BLOCK.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(soil.above(), Blocks.AIR.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            player.getFoodData().setExhaustion(0F);
            var result = player.getMainHandItem().useOn(context(player, soil));
            helper.assertTrue(result.consumesAction() && helper.getLevel().getBlockState(soil).is(Blocks.FARMLAND), "Both registered tool paths till successfully");
            effort(helper, player, .05F, "Grass hardness.5 /2 * Endurance IV.2 = .05");
            player.getFoodData().setExhaustion(0F);
            player.getMainHandItem().useOn(context(player, soil));
            effort(helper, player, 0F, "A repeated failed till operation is free");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spheresAndBowDrawingHaveExplicitDiscountsWhileSnowballsRemainFree(GameTestHelper helper) {
        Player player = player(helper); equip(player, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SNOWBALL, 2));
        Items.SNOWBALL.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        effort(helper, player, 0F, "Source zd.java snowballs have no action exhaustion fee");
        player.getFoodData().setExhaustion(0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SLIME_BALL, 2));
        SphereThrowing.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        effort(helper, player, .05F, "Existing sphere .25 gets discounted once");
        player.getFoodData().setExhaustion(0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        effort(helper, player, .002F, "An actual bow-use server tick pays.01*.2");
        player.stopUsingItem(); player.getFoodData().setExhaustion(0F);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        effort(helper, player, 0F, "A bow held but not being used is free");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeMiningProgressChargesPerTickAndStopsWhenAborted(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "endurance-mining"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        BlockPos ore = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(ore, Blocks.STONE.defaultBlockState());
        player.setPos(Vec3.atCenterOf(ore).add(0D, 0D, 1D));
        player.setOnGround(true); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        equip(player, 4); player.getFoodData().setExhaustion(0F);
        player.gameMode.handleBlockBreakAction(ore, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, helper.getLevel().getMaxBuildHeight(), 1);
        effort(helper, player, .002F, "The first allowed source mining request pays.01*.2");
        player.gameMode.tick(); player.gameMode.tick();
        effort(helper, player, .006F, "Two active native progress ticks each pay.01*.2");
        player.gameMode.handleBlockBreakAction(ore, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.UP, helper.getLevel().getMaxBuildHeight(), 2);
        player.gameMode.tick();
        effort(helper, player, .006F, "An aborted mining operation stops charging");
        helper.succeed();
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(6); player.getFoodData().setExhaustion(0F);
        return player;
    }

    private static void equip(Player player, int level) {
        ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
        armor.enchant(player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(R196Enchantments.key("endurance")), level);
        player.setItemSlot(EquipmentSlot.CHEST, armor);
    }

    private static UseOnContext context(Player player, BlockPos base) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(base).add(0D, .5D, 0D), Direction.UP, base, false));
    }

    private static void effort(GameTestHelper helper, Player player, float expected, String reason) {
        helper.assertTrue(Math.abs(player.getFoodData().getExhaustionLevel() - expected) < 1e-5F,
                reason + ": expected" + expected + ", actual" + player.getFoodData().getExhaustionLevel());
    }
}
