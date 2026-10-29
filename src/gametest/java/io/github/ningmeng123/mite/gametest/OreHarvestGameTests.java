package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.rules.OreBlockRules;
import io.github.ningmeng123.mite.world.PlacementDelay;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent aqz.java/apr.java source thresholds and actual server block/drop transactions. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class OreHarvestGameTests {
    private OreHarvestGameTests() {}

    @GameTest(template = "empty")
    public static void allSixNewOreIdentitiesHaveRegisteredCarriableBlocks(GameTestHelper helper) {
        for (String metal : new String[]{"silver", "mithril", "adamantium"}) for (String prefix : new String[]{"", "deepslate_"}) {
            Block block = block(helper, "mite:" + prefix + metal + "_ore");
            helper.assertTrue(block.asItem() != net.minecraft.world.item.Items.AIR, "The new ore has a registered BlockItem: " + prefix + metal);
            helper.assertTrue(block.defaultBlockState().requiresCorrectToolForDrops(), "The new ore cannot bypass source tool qualification");
        }
        for (Hardness source : List.of(new Hardness("minecraft:coal_ore", 1.2F), new Hardness("minecraft:copper_ore", 2.5F),
                new Hardness("minecraft:iron_ore", 3F), new Hardness("minecraft:gold_ore", 2.4F), new Hardness("minecraft:lapis_ore", 3F),
                new Hardness("minecraft:redstone_ore", 3F), new Hardness("minecraft:diamond_ore", 3F), new Hardness("minecraft:emerald_ore", 3F),
                new Hardness("mite:silver_ore", 2.5F), new Hardness("mite:mithril_ore", 3.5F), new Hardness("mite:adamantium_ore", 4F),
                new Hardness("minecraft:nether_gold_ore", 2.4F), new Hardness("minecraft:nether_quartz_ore", 3F))) {
            String[] variants = source.ore().contains("nether_") ? new String[]{source.ore()} : new String[]{source.ore(), deepslate(source.ore())};
            for (String id : variants) {
                Block ore = block(helper, id);
                helper.assertValueEqual(ore.getExplosionResistance(), source.value(), "Source aqz ore blast resistance applies to the real registered ore: " + id);
                helper.assertValueEqual(io.github.ningmeng123.mite.mining.MiningProgress.effectiveHardness(ore.defaultBlockState(), 100F), source.value(),
                        "Source ore hardness applies to the actual modern mining path: " + id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceToolThresholdsApplyToStoneAndDeepslateThroughNativeServerRemoval(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        try (Session session = player(helper); DropFixture f = new DropFixture(helper, pos)) {
            ServerPlayer player = session.player();
            for (Threshold source : List.of(new Threshold("mite:mithril_ore", "copper_pickaxe", "minecraft:iron_pickaxe"),
                    new Threshold("minecraft:emerald_ore", "copper_pickaxe", "minecraft:iron_pickaxe"),
                    new Threshold("mite:adamantium_ore", "minecraft:iron_pickaxe", "mithril_pickaxe"),
                    new Threshold("minecraft:diamond_ore", "minecraft:iron_pickaxe", "mithril_pickaxe"))) {
                for (String id : new String[]{source.ore(), deepslate(source.ore())}) {
                    BlockState state = block(helper, id).defaultBlockState();
                    helper.getLevel().setBlockAndUpdate(pos, state);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get(source.insufficient())));
                    helper.assertValueEqual(state.getDestroyProgress(player, helper.getLevel(), pos), 0F, "The below-tier tool makes no source mining progress for " + id);
                    helper.assertFalse(player.gameMode.destroyBlock(pos), "The actual native final-removal path must reject an insufficient tool for " + id);
                    helper.assertTrue(helper.getLevel().getBlockState(pos).equals(state) && f.drops().isEmpty(), "A rejected harvest keeps its ore and creates no drops");
                    helper.assertValueEqual(player.getMainHandItem().getDamageValue(), 0, "Rejected mining consumes no durability");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get(source.sufficient())));
                    helper.assertTrue(state.getDestroyProgress(player, helper.getLevel(), pos) > 0, "The source-qualified tool makes normal mining progress for " + id);
                    helper.assertTrue(player.gameMode.destroyBlock(pos) && helper.getLevel().getBlockState(pos).isAir(), "The qualified native server harvest removes " + id);
                    helper.assertTrue(player.getMainHandItem().getDamageValue() > 0, "A real qualified harvest spends durable tool points");
                    f.clearDrops();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryHarvestReturnsRawMetalsForBothOreHosts(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        try (Session session = player(helper); DropFixture f = new DropFixture(helper, pos)) {
            for (String name : new String[]{"minecraft:copper_ore", "minecraft:iron_ore", "minecraft:gold_ore", "mite:silver_ore", "mite:mithril_ore", "mite:adamantium_ore"}) {
                for (String id : new String[]{name, deepslate(name)}) {
                    Block ore = block(helper, id);
                    helper.getLevel().setBlockAndUpdate(pos, ore.defaultBlockState());
                    session.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mithril_pickaxe")));
                    helper.assertTrue(session.player().gameMode.destroyBlock(pos), "The native source-qualified harvest accepts " + id);
                    List<ItemEntity> drops = f.drops();
                    helper.assertValueEqual(drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(), 1, "Ordinary metal harvest gives exactly one raw metal");
                    helper.assertTrue(drops.stream().allMatch(entity -> entity.getItem().is(rawMetal(id))), "Both ore hosts must return their matching raw metal: " + id);
                    f.clearDrops();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void customRawMetalsAreRegisteredAndSilkTouchKeepsBothOreHosts(GameTestHelper helper) {
        for (String metal : List.of("silver", "mithril", "adamantium")) {
            var id = ResourceLocation.fromNamespaceAndPath("mite", "raw_" + metal);
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "Custom raw metal is registered: " + id);
            ItemStack raw = new ItemStack(BuiltInRegistries.ITEM.get(id));
            helper.assertValueEqual(raw.getMaxStackSize(), 4, "Custom raw metal matches existing copper, iron and gold raw-metal limits");
            helper.assertTrue(raw.has(DataComponents.FIRE_RESISTANT) == metal.equals("adamantium"), "Only adamantium raw metal retains its ore's fire resistance");
        }
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        try (Session session = player(helper); DropFixture f = new DropFixture(helper, pos)) {
            for (String name : List.of("minecraft:copper_ore", "minecraft:iron_ore", "minecraft:gold_ore", "mite:silver_ore", "mite:mithril_ore", "mite:adamantium_ore")) {
                for (String id : new String[]{name, deepslate(name)}) {
                    Block ore = block(helper, id);
                    helper.getLevel().setBlockAndUpdate(pos, ore.defaultBlockState());
                    ItemStack tool = fortuneTool(helper);
                    tool.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
                    session.player().setItemInHand(InteractionHand.MAIN_HAND, tool);
                    helper.assertTrue(session.player().gameMode.destroyBlock(pos), "Silk Touch still obeys native harvest qualification: " + id);
                    helper.assertValueEqual(f.drops().stream().mapToInt(entity -> entity.getItem().getCount()).sum(), 1, "Silk Touch bypasses raw metal conversion and Fortune copies");
                    helper.assertTrue(f.drops().stream().allMatch(entity -> entity.getItem().is(ore.asItem())), "Silk Touch keeps the exact ore host: " + id);
                    f.clearDrops();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryNativeGemCoalLapisAndRedstoneDropsUseSourceQuantities(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        try (Session session = player(helper); DropFixture f = new DropFixture(helper, pos)) {
            for (Drop source : List.of(new Drop("minecraft:coal_ore", "minecraft:coal", 1, 1),
                    new Drop("minecraft:diamond_ore", "minecraft:diamond", 1, 1),
                    new Drop("minecraft:emerald_ore", "minecraft:emerald", 1, 1),
                    new Drop("minecraft:lapis_ore", "minecraft:lapis_lazuli", 3, 5),
                    new Drop("minecraft:redstone_ore", "minecraft:redstone", 3, 5))) {
                for (String id : new String[]{source.ore(), deepslate(source.ore())}) {
                    for (int sample = 0; sample < 16; sample++) {
                        helper.getLevel().setBlockAndUpdate(pos, block(helper, id).defaultBlockState());
                        session.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mithril_pickaxe")));
                        helper.assertTrue(session.player().gameMode.destroyBlock(pos), "The native gem harvest accepts " + id);
                        List<ItemEntity> drops = f.drops();
                        int count = drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
                        var expected = BuiltInRegistries.ITEM.get(ResourceLocation.parse(source.item()));
                        helper.assertTrue(count >= source.min() && count <= source.max(), "The native source quantity for " + id + " is out of range: " + count);
                        helper.assertTrue(drops.stream().allMatch(entity -> entity.getItem().is(expected)), "The native drop uses the wrong whole gem/mineral for " + id);
                        f.clearDrops();
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeMetalPlacementPreservesItsFlagAndRawMetalStillReceivesFortune(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState floor = helper.getLevel().getBlockState(pos.below());
        try (Session session = player(helper); DropFixture f = new DropFixture(helper, pos)) {
            helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            session.player().setPos(Vec3.atBottomCenterOf(pos).add(0D,0D,1.3D));
            helper.assertTrue(session.player().canInteractWithBlock(pos.below(),1D),"Metal placement fixture stands within real empty-hand support reach");
            for (String name : new String[]{"minecraft:copper_ore", "minecraft:iron_ore", "minecraft:gold_ore", "mite:silver_ore", "mite:mithril_ore", "mite:adamantium_ore"}) {
                for (String id : new String[]{name, deepslate(name)}) {
                    Block ore = block(helper, id);
                    var natural = ore.defaultBlockState();
                    helper.assertFalse(natural.getValue(OreBlockRules.PLACED), "Natural generation starts without a player-placement mark");
                    helper.getLevel().setBlockAndUpdate(pos, natural);
                    session.player().setItemInHand(InteractionHand.MAIN_HAND, fortuneTool(helper));
                    helper.assertTrue(session.player().gameMode.destroyBlock(pos), "Fortune still obeys the normal qualified native harvest");
                    helper.assertValueEqual(f.drops().stream().mapToInt(entity -> entity.getItem().getCount()).sum(), 2,
                            "Source ten-level fortune deterministically adds one to a natural metal base unit");
                    helper.assertTrue(f.drops().stream().allMatch(entity -> entity.getItem().is(rawMetal(id))), "Source fortune copies the matching raw metal");
                    f.clearDrops();
                    session.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ore));
                    PlacementDelay.clear(session.player());
                    var placed = session.player().gameMode.useItemOn(session.player(), helper.getLevel(), session.player().getMainHandItem(), InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false));
                    helper.assertTrue(placed.consumesAction() && session.player().getMainHandItem().isEmpty(), "Native survival BlockItem placement consumes one metal ore");
                    BlockState state = helper.getLevel().getBlockState(pos);
                    helper.assertTrue(state.is(ore) && state.getValue(OreBlockRules.PLACED), "Actual native BlockItem placement stores source metal provenance");
                    BlockState loaded = NbtUtils.readBlockState(helper.getLevel().holderLookup(Registries.BLOCK), NbtUtils.writeBlockState(state));
                    helper.assertTrue(loaded.getValue(OreBlockRules.PLACED), "The placement mark survives native BlockState NBT");
                    helper.getLevel().setBlockAndUpdate(pos, loaded);
                    session.player().setItemInHand(InteractionHand.MAIN_HAND, fortuneTool(helper));
                    helper.assertTrue(session.player().gameMode.destroyBlock(pos), "Saved placed ore can be mined normally");
                    helper.assertValueEqual(f.drops().stream().mapToInt(entity -> entity.getItem().getCount()).sum(), 2,
                            "Source ten-level fortune also deterministically copies raw metal from a player-placed ore");
                    helper.assertTrue(f.drops().stream().allMatch(entity -> entity.getItem().is(rawMetal(id))), "Placed and natural ore receive the same raw-metal Fortune output");
                    helper.assertFalse(rawMetal(id) instanceof BlockItem, "Raw metal cannot be placed back as an ore block: " + id);
                    f.clearDrops();
                }
            }
        } finally { helper.getLevel().setBlockAndUpdate(pos.below(), floor); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeDropExperienceUsesTheSourceFixedRewards(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        List<Integer> experience = new ArrayList<>();
        Consumer<BlockDropsEvent> observe = event -> {
            if (event.getLevel() == helper.getLevel() && event.getPos().equals(pos)) experience.add(event.getDroppedExperience());
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BlockDropsEvent.class, observe);
        try (Session session = player(helper); DropFixture f = new DropFixture(helper, pos)) {
            for (Experience reward : List.of(new Experience("minecraft:diamond_ore", 30), new Experience("minecraft:emerald_ore", 20),
                    new Experience("minecraft:nether_quartz_ore", 10), new Experience("minecraft:coal_ore", 0),
                    new Experience("minecraft:lapis_ore", 5), new Experience("minecraft:redstone_ore", 0), new Experience("mite:mithril_ore", 0))) {
                experience.clear();
                helper.getLevel().setBlockAndUpdate(pos, block(helper, reward.ore()).defaultBlockState());
                session.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mithril_pickaxe")));
                helper.assertTrue(session.player().gameMode.destroyBlock(pos), "The source experience test uses the actual native survival harvest");
                helper.assertValueEqual(experience.size(), 1, "Exactly one real drop transaction carries the harvest experience");
                int originalUnits = f.drops().stream().mapToInt(entity -> entity.getItem().getCount()).sum();
                helper.assertValueEqual(experience.getFirst(), reward.value() * originalUnits,
                        "The source reward counts original units and replaces modern vanilla random mining XP for " + reward.ore());
                f.clearDrops();
            }
        } finally { NeoForge.EVENT_BUS.unregister(observe); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeNoDecayExplosionUsesSourceFragmentsAndKeepsOrdinaryHarvestIndependent(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        try (Session session = player(helper); DropFixture f = new DropFixture(helper, pos)) {
            for (Drop source : List.of(new Drop("minecraft:coal_ore", "minecraft:coal", 0, 0),
                    new Drop("minecraft:diamond_ore", "mite:diamond_shard", 1, 1),
                    new Drop("minecraft:emerald_ore", "mite:emerald_shard", 1, 1),
                    new Drop("minecraft:nether_quartz_ore", "mite:quartz_shard", 1, 1),
                    new Drop("minecraft:lapis_ore", "minecraft:lapis_lazuli", 3, 5),
                    new Drop("minecraft:redstone_ore", "minecraft:redstone", 3, 5),
                    new Drop("minecraft:iron_ore", "minecraft:iron_ore", 1, 1),
                    new Drop("mite:mithril_ore", "mite:mithril_ore", 1, 1))) {
                BlockState state = block(helper, source.ore()).defaultBlockState();
                helper.getLevel().setBlockAndUpdate(pos, state);
                List<ItemStack> explosionDrops = new ArrayList<>();
                // DESTROY is the actual TNT no-decay branch; vanilla omits EXPLOSION_RADIUS here.
                Explosion explosion = new Explosion(helper.getLevel(), null, pos.getX(), pos.getY(), pos.getZ(), 4F, false, Explosion.BlockInteraction.DESTROY);
                state.onExplosionHit(helper.getLevel(), pos, explosion, (stack, at) -> explosionDrops.add(stack));
                int count = explosionDrops.stream().mapToInt(ItemStack::getCount).sum();
                helper.assertTrue(count >= source.min() && count <= source.max(), "Native TNT-style explosion uses the wrong source quantity for " + source.ore());
                var output = BuiltInRegistries.ITEM.get(ResourceLocation.parse(source.item()));
                helper.assertTrue(explosionDrops.stream().allMatch(stack -> stack.is(output)), "Native TNT-style explosion fails the source fragment/metal identity for " + source.ore());
                helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "The actual explosion callback removes the ore");
            }
            helper.getLevel().setBlockAndUpdate(pos, Blocks.DIAMOND_ORE.defaultBlockState());
            session.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MiteTools.get("mithril_pickaxe")));
            helper.assertTrue(session.player().gameMode.destroyBlock(pos), "Normal survival mining still operates after the actual explosion callbacks");
            helper.assertTrue(f.drops().stream().allMatch(entity -> entity.getItem().is(net.minecraft.world.item.Items.DIAMOND)),
                    "Explosion output identity cannot leak into a subsequent ordinary whole-gem harvest");
            helper.assertValueEqual(f.drops().stream().mapToInt(entity -> entity.getItem().getCount()).sum(), 1,
                    "Ordinary mining after explosions still gives exactly one whole diamond");
        }
        helper.succeed();
    }

    private static ItemStack fortuneTool(GameTestHelper helper) {
        ItemStack tool = new ItemStack(MiteTools.get("mithril_pickaxe"));
        tool.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE), 10);
        return tool;
    }

    private static Block block(GameTestHelper helper, String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(key), "Expected R196 ore identity is missing: " + id);
        return BuiltInRegistries.BLOCK.get(key);
    }
    private static Item rawMetal(String oreId) {
        ResourceLocation id = ResourceLocation.parse(oreId);
        String metal = id.getPath().replace("deepslate_", "").replace("_ore", "");
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "raw_" + metal));
    }
    private static String deepslate(String id) { int colon = id.indexOf(':'); return id.substring(0, colon + 1) + "deepslate_" + id.substring(colon + 1); }
    private record Threshold(String ore, String insufficient, String sufficient) {}
    private record Drop(String ore, String item, int min, int max) {}
    private record Experience(String ore, int value) {}
    private record Hardness(String ore, float value) {}

    private static Session player(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-ore-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true); player.setOnGround(true);
            player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 4))));
            player.getInventory().clearContent(); player.getFoodData().setFoodLevel(6);
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            player.server.getPlayerList().remove(player); channel.finishAndReleaseAll(); throw failure;
        }
    }
    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() { player.server.getPlayerList().remove(player); channel.finishAndReleaseAll(); }
    }
    private static final class DropFixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final BlockPos pos;
        private final BlockState previous;
        private final boolean oldDrops;
        private final AABB area;
        private final SpawnedEntityCapture spawned;
        private final Set<UUID> existing = new HashSet<>();
        DropFixture(GameTestHelper helper, BlockPos pos) {
            this.helper = helper; this.pos = pos; previous = helper.getLevel().getBlockState(pos); area = new AABB(pos).inflate(1.5);
            spawned = new SpawnedEntityCapture(helper.getLevel(), entity -> entity instanceof ItemEntity && entity.getBoundingBox().intersects(area));
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(entity -> existing.add(entity.getUUID()));
            oldDrops = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, helper.getLevel().getServer());
        }
        List<ItemEntity> drops() { return spawned.live(ItemEntity.class); }
        void clearDrops() { drops().forEach(ItemEntity::discard); }
        @Override public void close() {
            clearDrops(); helper.getLevel().setBlockAndUpdate(pos, previous);
            spawned.close();
            helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(oldDrops, helper.getLevel().getServer());
        }
    }
}
