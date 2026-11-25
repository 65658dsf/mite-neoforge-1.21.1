package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.mining.ToolHarvestRules;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual mining progress and server removal eligibility, rather than only drop flags. @author NingMeng123 */
@GameTestHolder("mite") @PrefixGameTestTemplate(false)
public final class SourceMiningGameTests {
    @GameTest(template = "empty")
    public static void iceAndSnowAllowHandBreakingWhileSnowKeepsItsDropToolRequirement(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        List<BlockState> states = new ArrayList<>(List.of(
                Blocks.ICE.defaultBlockState(),
                Blocks.FROSTED_ICE.defaultBlockState(),
                Blocks.PACKED_ICE.defaultBlockState(),
                Blocks.BLUE_ICE.defaultBlockState(),
                Blocks.SNOW_BLOCK.defaultBlockState(),
                Blocks.POWDER_SNOW.defaultBlockState()));
        for (int layers = 1; layers <= 8; layers++) {
            states.add(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
        }

        for (ItemStack held : List.of(ItemStack.EMPTY, new ItemStack(Items.STICK))) {
            player.setItemSlot(EquipmentSlot.MAINHAND, held);
            for (BlockState state : states) {
                helper.getLevel().setBlock(pos, state, 2);
                helper.assertFalse(ToolHarvestRules.isBlocked(player, state),
                        "Hands and ordinary items can break ice and snow: " + state);
                helper.assertTrue(state.getDestroyProgress(player, helper.getLevel(), pos) > 0F,
                        "Ice and snow allow actual mining progress: " + state);
                var event = new BlockEvent.BreakEvent(helper.getLevel(), pos, state, player);
                ToolHarvestRules.harvest(event);
                helper.assertFalse(event.isCanceled(), "The final ice or snow break is accepted: " + state);
            }
        }

        for (BlockState state : states) {
            if (!state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) continue;
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            helper.assertFalse(player.hasCorrectToolForDrops(state),
                    "Bare hands retain the native snow drop requirement: " + state);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SHOVEL));
            helper.assertTrue(player.hasCorrectToolForDrops(state),
                    "A shovel retains native snow drop eligibility: " + state);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalRemovalOfGlassAndPackedMudRetainsNativeLoot(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-glass-test"), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        var blockDrops = level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS);
        boolean previousBlockDrops = blockDrops.get();
        try {
            level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(Vec3.atBottomCenterOf(pos.south().below()));
            player.setOnGround(true);
            blockDrops.set(true, level.getServer());

            BlockState purpleGlass = Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            assertSurvivalBreak(helper, player, pos, purpleGlass, ItemStack.EMPTY, 0);
            assertSurvivalBreak(helper, player, pos, purpleGlass, new ItemStack(Items.IRON_PICKAXE), 0);
            assertSurvivalBreak(helper, player, pos, purpleGlass, new ItemStack(MiteTools.get("vibranium_pickaxe")), 0);

            ItemStack silkTouch = new ItemStack(Items.IRON_PICKAXE);
            silkTouch.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
            assertSurvivalBreak(helper, player, pos, purpleGlass, silkTouch, 1);
            assertSurvivalBreak(helper, player, pos, Blocks.TINTED_GLASS.defaultBlockState(), ItemStack.EMPTY, 1);
            assertSurvivalBreak(helper, player, pos, Blocks.PACKED_MUD.defaultBlockState(), ItemStack.EMPTY, 1);
        } finally {
            blockDrops.set(previousBlockDrops, level.getServer());
            try {
                if (level.getServer().getPlayerList().getPlayer(player.getUUID()) != null) {
                    level.getServer().getPlayerList().remove(player);
                }
            } finally {
                channel.finishAndReleaseAll();
            }
        }
        helper.succeed();
    }

    private static void assertSurvivalBreak(GameTestHelper helper, ServerPlayer player, BlockPos pos, BlockState state,
                                            ItemStack held, int expectedDrops) {
        var level = helper.getLevel();
        level.setBlock(pos, state, 2);
        player.setItemSlot(EquipmentSlot.MAINHAND, held);
        String context = state + " with " + held;
        AABB dropArea = new AABB(pos).inflate(1);
        try (var spawned = new SpawnedEntityCapture(level, entity -> entity instanceof ItemEntity
                && dropArea.contains(entity.position()))) {
            helper.assertTrue(state.getDestroyProgress(player, level, pos) > 0F, "Break progress for " + context);
            helper.assertTrue(player.gameMode.destroyBlock(pos) && level.getBlockState(pos).isAir(),
                    "The survival server removes " + context);
            List<ItemEntity> drops = spawned.live(ItemEntity.class);
            helper.assertValueEqual(drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(), expectedDrops,
                    "Native drop count for " + context);
            helper.assertTrue(drops.stream().allMatch(entity -> entity.getItem().is(state.getBlock().asItem())),
                    "Native drop item for " + context);
        }
    }

    @GameTest(template="empty")
    public static void bareHandsAndUnrelatedItemsCannotRemoveRequiredMaterialsAtAnyLevel(GameTestHelper h) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setOnGround(true);p.experienceLevel=500;
        BlockPos pos=h.absolutePos(new BlockPos(2,2,2));
        for(var item:new net.minecraft.world.item.Item[]{Items.AIR,Items.STICK,Items.STONE_SHOVEL}) {
            p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item));
            for(var block:new net.minecraft.world.level.block.Block[]{Blocks.STONE,Blocks.COBBLESTONE,Blocks.NETHERRACK,Blocks.IRON_BARS}) {
                var state=block.defaultBlockState();h.getLevel().setBlock(pos,state,2);
                h.assertTrue(ToolHarvestRules.isBlocked(p,state),"An unrelated item cannot destroy the required material: "+block);
                h.assertValueEqual(state.getDestroyProgress(p,h.getLevel(),pos),0F,"Both mining sides predict zero progress");
                var event=new BlockEvent.BreakEvent(h.getLevel(),pos,state,p);ToolHarvestRules.harvest(event);
                h.assertTrue(event.isCanceled(),"Final server removal cannot bypass a zero-progress tool gate");
            }
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void sourceToolTiersAndExistingDropExceptionsStillApply(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var sandstone = Blocks.SANDSTONE.defaultBlockState();
        helper.assertTrue(ToolHarvestRules.isBlocked(player, sandstone), "Sandstone retains its tool gate");
        player.setItemSlot(EquipmentSlot.MAINHAND,
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mite:flint_axe"))));
        helper.assertTrue(sandstone.getDestroyProgress(player, helper.getLevel(), pos) > 0F,
                "The flint axe qualifies for sandstone");

        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        for (var block : List.of(Blocks.PISTON, Blocks.STONE_BUTTON, Blocks.RAIL, Blocks.COAL_BLOCK, Blocks.GLOWSTONE)) {
            var state = block.defaultBlockState();
            helper.assertFalse(ToolHarvestRules.isBlocked(player, state), "The existing hand break rule permits " + block);
            helper.assertTrue(player.hasCorrectToolForDrops(state), "The existing hand drop rule permits " + block);
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        helper.assertFalse(ToolHarvestRules.isBlocked(player, Blocks.STONE.defaultBlockState()),
                "Modern tools retain native Tool compatibility");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaHandBreakableBlocksMakeProgressWithEmptyHandAndStick(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));

        for (ItemStack held : List.of(ItemStack.EMPTY, new ItemStack(Items.STICK))) {
            player.setItemSlot(EquipmentSlot.MAINHAND, held);
            for (var block : BuiltInRegistries.BLOCK) {
                var state = block.defaultBlockState();
                if (!ResourceLocation.DEFAULT_NAMESPACE.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())
                        || state.isAir() || state.is(BlockTags.LOGS) || state.requiresCorrectToolForDrops()
                        || state.getDestroySpeed(helper.getLevel(), pos) < 0F) continue;
                helper.assertTrue(state.getDestroyProgress(player, helper.getLevel(), pos) > 0F,
                        "Vanilla hand-breakable block must make progress with " + held + ": " + state);
            }
        }
        helper.succeed();
    }
}
