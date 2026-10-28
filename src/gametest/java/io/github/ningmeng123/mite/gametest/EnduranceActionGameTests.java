package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.world.PlacementDelay;

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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
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
        effort(helper, player, 1.15F, "Ordinary exhaustion receives the global 15 percent increase without an Endurance discount");
        player.getFoodData().setExhaustion(0F);
        EnduranceActions.melee(player);
        effort(helper, player, .069F, "The source .3 melee fee receives the IV .2 multiplier and global 15 percent increase once");
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        EnduranceActions.melee(creative); EnduranceActions.throwing(creative); EnduranceActions.mining(creative);
        effort(helper, creative, 0F, "Creative actions stay free");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeAttackChargesAttemptWithoutDuplicateSuccessfulHitCost(GameTestHelper helper) {
        Player player = player(helper); equip(player, 4);
        var victim = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2)); victim.setNoAi(true);
        player.setPos(victim.position().add(1D,0D,0D));
        helper.assertTrue(io.github.ningmeng123.mite.combat.SourcePlayerReach.canMelee(player,victim),"Native attack fixture is within actual empty-hand melee reach");
        float before=victim.getHealth();
        player.attack(victim);
        helper.assertTrue(victim.getHealth()<before,"First attempt really harms the non-invulnerable native target");
        effort(helper, player, .069F, "A successful native attack pays the increased .3*.2 fee exactly once");
        float afterSuccess=victim.getHealth();
        player.getFoodData().setExhaustion(0F); victim.setInvulnerable(true);
        player.attack(victim);
        helper.assertTrue(victim.getHealth()==afterSuccess,"Invulnerable second attack really leaves health unchanged while charging attempt effort");
        effort(helper, player, .069F, "An allowed failed hit still pays the increased source attempt fee");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void acceptedPlacementChargesHardnessAndCancelledPlacementIsFree(GameTestHelper helper) {
        Player player = player(helper); equip(player, 4);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(base.above(), Blocks.AIR.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OBSIDIAN, 2));
        PlacementDelay.clear(player);
        var result = player.getMainHandItem().useOn(context(player, base));
        helper.assertTrue(result.consumesAction() && helper.getLevel().getBlockState(base.above()).is(Blocks.OBSIDIAN), "Native placement succeeds");
        effort(helper, player, .552F, "Obsidian source hardness 2.4 receives Endurance IV and the global 15 percent increase once");
        player.getFoodData().setExhaustion(0F);
        helper.getLevel().setBlockAndUpdate(base.above(), Blocks.AIR.defaultBlockState());
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        PlacementDelay.clear(player);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, cancel);
        try {
            var denied = player.getMainHandItem().useOn(context(player, base));
            helper.assertTrue(!denied.consumesAction() && helper.getLevel().getBlockState(base.above()).isAir(), "NeoForge cancellation restores the placement snapshot");
            effort(helper, player, 0F, "A rolled-back placement pays no action fee");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void addedSnowLayersAndMergedSlabsPayTheirLegacyMetadataZeroPlacementFee(GameTestHelper helper) {
        Player player = player(helper); equip(player, 4);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos placed = base.above();
        helper.getLevel().setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(placed, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SNOW, 2));
        PlacementDelay.clear(player);
        var snow = player.getMainHandItem().useOn(context(player, placed));
        helper.assertTrue(snow.consumesAction() && helper.getLevel().getBlockState(placed).is(Blocks.SNOW)
                && helper.getLevel().getBlockState(placed).getValue(SnowLayerBlock.LAYERS) == 4,
                "Native snow placement successfully adds a fourth layer to the existing snow");
        effort(helper, player, .0115F, "Adding snow pays .05 metadata-zero hardness rather than the four-layer hardness");

        var slabs = new net.minecraft.world.level.block.Block[]{Blocks.OAK_SLAB, Blocks.STONE_BRICK_SLAB, Blocks.QUARTZ_SLAB};
        var items = new net.minecraft.world.item.Item[]{Items.OAK_SLAB, Items.STONE_BRICK_SLAB, Items.QUARTZ_SLAB};
        float[] expected = {.092F, .276F, .276F};
        for (int i = 0; i < slabs.length; i++) {
            helper.getLevel().setBlockAndUpdate(placed, slabs[i].defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            helper.getLevel().setBlockAndUpdate(placed.above(), Blocks.AIR.defaultBlockState());
            player.getFoodData().setExhaustion(0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(items[i], 2));
            PlacementDelay.clear(player);
            var merged = player.getMainHandItem().useOn(context(player, placed));
            helper.assertTrue(merged.consumesAction() && helper.getLevel().getBlockState(placed).is(slabs[i])
                    && helper.getLevel().getBlockState(placed).getValue(SlabBlock.TYPE) == SlabType.DOUBLE,
                    "Native slab placement merges the existing bottom slab into a double slab: " + slabs[i]);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Exactly one new slab was placed");
            effort(helper, player, expected[i],
                    "A merged slab pays old metadata-zero placement hardness, independent of subtype and final double state: " + slabs[i]);
        }
        PlacementDelay.clear(player);
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
            effort(helper, player, .0575F, "Grass hardness.5 /2 * Endurance IV.2 receives the global 15 percent increase");
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
        effort(helper, player, .0575F, "Existing sphere .25 gets discounted and globally increased once");
        player.getFoodData().setExhaustion(0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        effort(helper, player, .0023F, "An actual bow-use server tick pays the increased .01*.2 fee");
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
        effort(helper, player, .0023F, "The first allowed source mining request pays the increased .01*.2 fee");
        player.gameMode.tick(); player.gameMode.tick();
        effort(helper, player, .0069F, "Two active native progress ticks each add the increased .01*.2 fee");
        player.gameMode.handleBlockBreakAction(ore, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.UP, helper.getLevel().getMaxBuildHeight(), 2);
        player.gameMode.tick();
        effort(helper, player, .0069F, "An aborted mining operation stops charging");
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
