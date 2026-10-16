package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real mining, explosions and container removal distinguish natural blocks from player replacements. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class TrialBlockDropsGameTests {
    private TrialBlockDropsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void overworldNaturalMiningSuppressesDropsWithoutProtectingPlayerCopper(GameTestHelper helper) {
        mining(helper, helper.getLevel().getServer().overworld(), 0);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void underworldNaturalMiningSuppressesDropsWithoutProtectingPlayerCopper(GameTestHelper helper) {
        mining(helper, underworld(helper), 1);
        helper.succeed();
    }

    private static void mining(GameTestHelper helper, ServerLevel level, int slot) {
        try (Fixture fixture = new Fixture(helper, level, slot); Session session = player(helper, level, fixture.center)) {
            ServerPlayer player = session.player;
            for (Block block : List.of(Blocks.COPPER_BLOCK, Blocks.TUFF)) {
                for (int enchantment = 0; enchantment <= 2; enchantment++) {
                    fixture.place(fixture.center, block.defaultBlockState(), true);
                    player.setItemInHand(InteractionHand.MAIN_HAND, tool(level, enchantment));
                    helper.assertTrue(player.gameMode.destroyBlock(fixture.center), "A natural trial block remains removable by a qualified survival player");
                    helper.assertTrue(level.getBlockState(fixture.center).isAir(), "The real mining transaction actually removes the natural block");
                    fixture.assertEmpty("Natural mining including silk touch/fortune must emit neither block items nor experience");
                    helper.assertFalse(NaturalTrialBlocks.isNatural(level, fixture.center), "Removal consumes the old source identity");

                    // The same block, at precisely the same position, is now a player replacement.
                    fixture.place(fixture.center, block.defaultBlockState(), false);
                    helper.assertFalse(NaturalTrialBlocks.isNatural(level, fixture.center), "Placing the original block ID again cannot inherit natural provenance");
                    player.setItemInHand(InteractionHand.MAIN_HAND, tool(level, enchantment));
                    helper.assertTrue(player.gameMode.destroyBlock(fixture.center), "A player replacement remains mineable");
                    helper.assertValueEqual(fixture.count(block.asItem()), 1, "A player replacement produces its normal one block item");
                    fixture.clear();
                }
            }
            BlockPos addedInsideRoom = fixture.center.offset(2, 0, 0);
            fixture.place(fixture.center, Blocks.COPPER_BLOCK.defaultBlockState(), true);
            fixture.place(addedInsideRoom, Blocks.COPPER_BLOCK.defaultBlockState(), false);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool(level, 0));
            helper.assertTrue(player.gameMode.destroyBlock(addedInsideRoom), "A player-added copper block next to a natural wall can be mined");
            helper.assertValueEqual(fixture.count(Items.COPPER_BLOCK), 1, "Protection does not spread from natural walls to player additions");
            helper.assertTrue(NaturalTrialBlocks.isNatural(level, fixture.center), "Mining a neighboring player block cannot consume the original wall's source flag");
        }
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void naturalExperienceSourceIsSuppressedWhilePlayerSpawnerExperienceSurvives(GameTestHelper helper) {
        for (ServerLevel level : List.of(helper.getLevel().getServer().overworld(), underworld(helper))) {
            try (Fixture fixture = new Fixture(helper, level, 2); Session session = player(helper, level, fixture.center)) {
                fixture.place(fixture.center, Blocks.SPAWNER.defaultBlockState(), true);
                session.player.setItemInHand(InteractionHand.MAIN_HAND, tool(level, 0));
                helper.assertTrue(session.player.gameMode.destroyBlock(fixture.center), "A marked XP-bearing source reaches real survival removal");
                fixture.assertEmpty("Natural source suppression also suppresses mining experience");
                fixture.place(fixture.center, Blocks.SPAWNER.defaultBlockState(), false);
                session.player.setItemInHand(InteractionHand.MAIN_HAND, tool(level, 0));
                helper.assertTrue(session.player.gameMode.destroyBlock(fixture.center), "An unmarked spawner reaches the identical native mining path");
                helper.assertTrue(fixture.experience() >= 15, "The XP assertion has a positive native-spawner control, rather than checking a zero-XP copper block");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void actualPrimedTntFuseDestroysNaturalCopperWithoutDroppingIt(GameTestHelper helper) {
        explosions(helper, 3, Blast.TNT_FUSE);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void nativeTntExplosionKeepsPlayerCopperDropsAndSuppressesNaturalCopper(GameTestHelper helper) {
        explosions(helper, 4, Blast.TNT_DIRECT);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void nativeCreeperExplosionKeepsPlayerCopperDropsAndSuppressesNaturalCopper(GameTestHelper helper) {
        explosions(helper, 5, Blast.CREEPER);
        helper.succeed();
    }

    private static void explosions(GameTestHelper helper, int slot, Blast blast) {
        for (ServerLevel level : List.of(helper.getLevel().getServer().overworld(), underworld(helper))) {
            try (Fixture fixture = new Fixture(helper, level, slot)) {
                for (boolean natural : new boolean[]{true, false}) {
                    fixture.place(fixture.center, Blocks.COPPER_BLOCK.defaultBlockState(), natural);
                    blast.run(level, fixture.center);
                    helper.assertTrue(level.getBlockState(fixture.center).isAir(), "The native " + blast + " explosion must really destroy the copper target");
                    if (natural) fixture.assertEmpty("A natural copper target emits no items or experience after " + blast);
                    else helper.assertValueEqual(fixture.count(Items.COPPER_BLOCK), 1, "The same position's player replacement drops normally after " + blast);
                    helper.assertFalse(NaturalTrialBlocks.isNatural(level, fixture.center), "Explosion removes only the destroyed source identity");
                    fixture.clear();
                }
            }
        }
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sameExplosionDoesNotMergeNaturalCopperIntoPlayerCopperDrops(GameTestHelper helper) {
        for (ServerLevel level : List.of(helper.getLevel().getServer().overworld(), underworld(helper))) {
            try (Fixture fixture = new Fixture(helper, level, 6)) {
                BlockPos natural = fixture.center;
                BlockPos player = natural.east();
                fixture.place(natural, Blocks.COPPER_BLOCK.defaultBlockState(), true);
                fixture.place(player, Blocks.COPPER_BLOCK.defaultBlockState(), false);
                level.explode(new PrimedTnt(level, player.getX(), player.getY(), player.getZ(), null),
                        player.getX(), player.getY() + .5, player.getZ() + .5, 6F, Level.ExplosionInteraction.TNT);
                helper.assertTrue(level.getBlockState(natural).isAir() && level.getBlockState(player).isAir(),
                        "One native blast actually removes both provenance variants of the same copper item");
                helper.assertValueEqual(fixture.count(Items.COPPER_BLOCK), 1,
                        "Native explosion stack coalescing cannot combine a protected natural block with an allowed player block");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void naturalChestAndPotMiningSuppressesBlockDropsButPreservesPotContents(GameTestHelper helper) {
        for (ServerLevel level : List.of(helper.getLevel().getServer().overworld(), underworld(helper))) {
            try (Fixture fixture = new Fixture(helper, level, 7); Session session = player(helper, level, fixture.center)) {
                for (Block block : List.of(Blocks.CHEST, Blocks.DECORATED_POT)) {
                    for (boolean natural : new boolean[]{true, false}) {
                        fixture.container(block, natural);
                        session.player.setItemInHand(InteractionHand.MAIN_HAND, tool(level, 0));
                        helper.assertTrue(session.player.gameMode.destroyBlock(fixture.center), "The real survival path removes the filled container");
                        helper.assertTrue(level.getBlockState(fixture.center).isAir(), "The filled container is actually removed");
                        if (natural && block == Blocks.DECORATED_POT) fixture.assertOnlyContents(contents(), "Natural pot mining preserves contents but drops no pot, bricks or sherds");
                        else if (natural) fixture.assertEmpty("Natural chest mining suppresses its block and separate onRemove contents");
                        else helper.assertValueEqual(fixture.count(Items.EMERALD), 3, "A player container retains all independently ejected contents");
                        fixture.clear();
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void naturalContainerExplosionSuppressesBlocksButPreservesPotContents(GameTestHelper helper) {
        for (ServerLevel level : List.of(helper.getLevel().getServer().overworld(), underworld(helper))) {
            try (Fixture fixture = new Fixture(helper, level, 8)) {
                for (Block block : List.of(Blocks.CHEST, Blocks.DECORATED_POT)) {
                    for (boolean natural : new boolean[]{true, false}) {
                        fixture.container(block, natural);
                        Blast.TNT_DIRECT.run(level, fixture.center);
                        helper.assertTrue(level.getBlockState(fixture.center).isAir(), "The native TNT blast really removes the filled container");
                        if (natural && block == Blocks.DECORATED_POT) fixture.assertOnlyContents(contents(), "Natural exploding pots preserve contents but drop no pot, bricks or sherds");
                        else if (natural) fixture.assertEmpty("Natural exploding chests cannot bypass suppression through onRemove contents");
                        else helper.assertValueEqual(fixture.count(Items.EMERALD), 3, "Normal container content entities survive their actual ejection transaction");
                        fixture.clear();
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void naturalUnopenedPotsUnpackTheirRealTrialCorridorLootWhenMinedOrExploded(GameTestHelper helper) {
        ResourceKey<LootTable> corridor = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("pots/trial_chambers/corridor"));
        for (ServerLevel level : List.of(helper.getLevel().getServer().overworld(), underworld(helper))) {
            try (Fixture fixture = new Fixture(helper, level, 12); Session session = player(helper, level, fixture.center)) {
                for (int removal = 0; removal < 3; removal++) {
                    long seed = 196123L + removal;
                    // Independent native reference: fill a separate unattached-to-world pot with
                    // the exact table, origin and nonzero seed. This preserves the real single-slot
                    // loot-fill behavior instead of guessing expected output from a display name.
                    DecoratedPotBlockEntity reference = new DecoratedPotBlockEntity(fixture.center, Blocks.DECORATED_POT.defaultBlockState());
                    reference.setLevel(level);
                    reference.setLootTable(corridor, seed);
                    ItemStack expected = reference.getTheItem().copy();
                    helper.assertFalse(expected.isEmpty(), "The pinned vanilla trial corridor table has a positive native loot result");

                    fixture.place(fixture.center, Blocks.DECORATED_POT.defaultBlockState(), true);
                    DecoratedPotBlockEntity pot = (DecoratedPotBlockEntity)level.getBlockEntity(fixture.center);
                    pot.setLootTable(corridor, seed);
                    helper.assertTrue(corridor.equals(pot.getLootTable()), "The real natural pot remains unopened with deferred loot before destruction");
                    if (removal == 0) {
                        session.player.setItemInHand(InteractionHand.MAIN_HAND, tool(level, 0));
                        session.player.moveTo(fixture.center.getX()+1.5,fixture.center.getY(),fixture.center.getZ()+.5);
                        helper.assertTrue(io.github.ningmeng123.mite.combat.SourcePlayerReach.canReachBlock(session.player,
                                fixture.center,InteractionHand.MAIN_HAND),"The survival pot test uses a real reachable target");
                        helper.assertTrue(session.player.gameMode.destroyBlock(fixture.center), "Survival mining actually breaks the unopened natural pot");
                    } else (removal == 1 ? Blast.TNT_DIRECT : Blast.CREEPER).run(level, fixture.center);
                    helper.assertTrue(level.getBlockState(fixture.center).isAir(), "Every tested native destruction route removes the natural pot");
                    fixture.assertOnlyContents(expected, "Deferred pot loot must be generated and ejected exactly once without any pot, bricks or sherds");
                    fixture.clear();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void directDropsDestroyAndBlockReplacementUseTheSameNaturalSourceRules(GameTestHelper helper) {
        for (ServerLevel level : List.of(helper.getLevel().getServer().overworld(), underworld(helper))) {
            try (Fixture fixture = new Fixture(helper, level, 9)) {
                fixture.place(fixture.center, Blocks.COPPER_BLOCK.defaultBlockState(), true);
                Block.dropResources(level.getBlockState(fixture.center), level, fixture.center);
                fixture.assertEmpty("A direct dropResources call cannot bypass natural-source suppression");
                helper.assertTrue(NaturalTrialBlocks.isNatural(level, fixture.center), "A loot-only query without removal does not turn the still-present source into a player block");
                helper.assertTrue(level.destroyBlock(fixture.center, true), "Native Level.destroyBlock actually removes the natural target");
                fixture.assertEmpty("Level.destroyBlock without a player uses the same no-drop rule");

                fixture.container(Blocks.CHEST, true);
                level.setBlockAndUpdate(fixture.center, Blocks.AIR.defaultBlockState());
                fixture.assertEmpty("Raw block replacement must not leak a natural container's onRemove contents");
                helper.assertFalse(NaturalTrialBlocks.isNatural(level, fixture.center), "A raw replacement clears the old source marker");
                fixture.container(Blocks.CHEST, false);
                level.setBlockAndUpdate(fixture.center, Blocks.AIR.defaultBlockState());
                helper.assertValueEqual(fixture.count(Items.EMERALD), 3, "A later player chest retains ordinary onRemove behavior at the original source location");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void doorSupportChainSuppressesBothNaturalHalvesButLeavesPlayerDoorDrops(GameTestHelper helper) {
        for (ServerLevel level : List.of(helper.getLevel().getServer().overworld(), underworld(helper))) {
            try (Fixture fixture = new Fixture(helper, level, 10)) {
                for (boolean natural : new boolean[]{true, false}) {
                    BlockPos lower = fixture.center, upper = lower.above();
                    level.setBlock(lower.below(), Blocks.STONE.defaultBlockState(), 2);
                    fixture.place(lower, Blocks.COPPER_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), natural);
                    fixture.place(upper, Blocks.COPPER_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), natural);
                    level.setBlock(lower, level.getBlockState(lower).setValue(DoorBlock.OPEN, true), 2);
                    if (natural) helper.assertTrue(NaturalTrialBlocks.isNatural(level, lower), "A normal door-state update retains natural provenance");
                    level.setBlockAndUpdate(lower.below(), Blocks.AIR.defaultBlockState());
                    helper.assertTrue(level.getBlockState(lower).isAir() && level.getBlockState(upper).isAir(), "Removing support reaches the native two-half neighbor destruction chain");
                    if (natural) fixture.assertEmpty("Neither half of a natural door may drop during indirect support destruction");
                    else helper.assertValueEqual(fixture.count(Items.COPPER_DOOR), 1, "An ordinary player door still drops exactly once after losing support");
                    helper.assertFalse(NaturalTrialBlocks.isNatural(level, lower) || NaturalTrialBlocks.isNatural(level, upper), "Both removed halves relinquish their old source flags");
                    fixture.clear();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void copperBulbStateUpdatesRetainNaturalIdentityUntilItsBlockIsReplaced(GameTestHelper helper) {
        for (ServerLevel level : List.of(helper.getLevel().getServer().overworld(), underworld(helper))) {
            try (Fixture fixture = new Fixture(helper, level, 11); Session session = player(helper, level, fixture.center)) {
                fixture.place(fixture.center, Blocks.COPPER_BULB.defaultBlockState(), true);
                level.setBlock(fixture.center, level.getBlockState(fixture.center).setValue(BlockStateProperties.LIT, true), 2);
                helper.assertTrue(NaturalTrialBlocks.isNatural(level, fixture.center), "Lighting a naturally generated copper bulb cannot launder its source identity");
                session.player.setItemInHand(InteractionHand.MAIN_HAND, tool(level, 1));
                helper.assertTrue(session.player.gameMode.destroyBlock(fixture.center), "A lit natural copper bulb can actually be mined");
                fixture.assertEmpty("A same-block property change cannot enable natural block drops");
                fixture.place(fixture.center, Blocks.COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true), false);
                session.player.setItemInHand(InteractionHand.MAIN_HAND, tool(level, 1));
                helper.assertTrue(session.player.gameMode.destroyBlock(fixture.center), "A player-placed lit bulb is mineable");
                helper.assertValueEqual(fixture.count(Items.COPPER_BULB), 1, "A player replacement of the formerly natural bulb drops normally");
            }
        }
        helper.succeed();
    }

    private static ItemStack tool(ServerLevel level, int enchantment) {
        ItemStack stack = new ItemStack(MiteTools.get("mithril_pickaxe"));
        if (enchantment != 0) stack.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(enchantment == 1 ? Enchantments.SILK_TOUCH : Enchantments.FORTUNE), enchantment == 1 ? 1 : 3);
        return stack;
    }

    private static ItemStack contents() {
        ItemStack stack = new ItemStack(Items.EMERALD, 3);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("NingMeng123 trial container contents"));
        return stack;
    }

    private static ServerLevel underworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "The real underworld dimension must be available");
        return level;
    }

    private enum Blast {
        TNT_FUSE, TNT_DIRECT, CREEPER;
        void run(ServerLevel level, BlockPos pos) {
            Vec3 center = Vec3.atCenterOf(pos);
            if (this == CREEPER) {
                Creeper creeper = new Creeper(EntityType.CREEPER, level);
                creeper.setPos(center);
                try { level.explode(creeper, center.x, center.y, center.z, 3F, Level.ExplosionInteraction.MOB); }
                finally { creeper.discard(); }
            } else {
                PrimedTnt tnt = new PrimedTnt(level, center.x, center.y, center.z, null);
                tnt.setNoGravity(true);
                tnt.setDeltaMovement(Vec3.ZERO);
                try {
                    if (this == TNT_FUSE) { tnt.setFuse(1); tnt.tick(); }
                    else level.explode(tnt, center.x, center.y, center.z, 4F, Level.ExplosionInteraction.TNT);
                } finally { tnt.discard(); }
            }
        }
    }

    private static Session player(GameTestHelper helper, ServerLevel target, BlockPos center) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-trial-drop"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel().getServer().overworld(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(target, center.getX() + 1.5, center.getY(), center.getZ() + .5, 0F, 0F);
            helper.assertTrue(player.serverLevel() == target, "The survival player really operates in the target dimension");
            player.setNoGravity(true);
            player.setOnGround(true);
            player.setInvulnerable(true);
            player.getInventory().clearContent();
            return new Session(player, channel);
        } catch (RuntimeException | Error failure) {
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record Session(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() { player.server.getPlayerList().remove(player); channel.finishAndReleaseAll(); }
    }

    private record Previous(BlockState state, CompoundTag blockEntity) {}

    /** Isolated fixed sites avoid random GameTest-grid overlap, and spawn capture avoids entity-index races. */
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos center;
        final Map<BlockPos, Previous> previous = new LinkedHashMap<>();
        final SpawnedEntityCapture spawned;
        final boolean oldDrops;
        final boolean oldGriefing;
        final boolean oldTntDecay;
        final boolean oldMobDecay;

        Fixture(GameTestHelper helper, ServerLevel level, int slot) {
            this.helper = helper;
            this.level = level;
            center = new BlockPos((1200 + slot * 4) * 16 + 8, 200, 1200 * 16 + 8);
            // Mining and neighbor fixtures need only a tiny room; blast fixtures clear their native
            // blast radius so unrelated original terrain cannot contribute any captured item drops.
            int radius = slot == 6 ? 10 : (slot >= 3 && slot <= 5 || slot == 8 || slot == 12) ? 8 : 3;
            for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
                BlockPos pos = mutable.immutable();
                BlockEntity entity = level.getBlockEntity(pos);
                previous.put(pos, new Previous(level.getBlockState(pos), entity == null ? null : entity.saveWithFullMetadata(level.registryAccess())));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            oldDrops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            oldGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            oldTntDecay = level.getGameRules().getBoolean(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
            oldMobDecay = level.getGameRules().getBoolean(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY);
            rule(GameRules.RULE_DOBLOCKDROPS, true);
            rule(GameRules.RULE_MOBGRIEFING, true);
            // An outside positive control should never fail randomly because vanilla discarded its
            // drop. Both native TNT and MOB routes are still used, with drop decay explicitly off.
            rule(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY, false);
            rule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY, false);
            AABB area = new AABB(center).inflate(radius + 1);
            spawned = new SpawnedEntityCapture(level, entity -> (entity instanceof ItemEntity || entity instanceof ExperienceOrb)
                    && entity.getBoundingBox().intersects(area));
        }

        void place(BlockPos pos, BlockState state, boolean natural) {
            level.setBlock(pos, state, 2);
            if (natural) NaturalTrialBlocks.record(level.getChunkAt(pos), pos);
        }

        void container(Block block, boolean natural) {
            place(center, block.defaultBlockState(), natural);
            ItemStack contents = contents();
            BlockEntity entity = level.getBlockEntity(center);
            if (entity instanceof ChestBlockEntity chest) chest.setItem(0, contents);
            else if (entity instanceof DecoratedPotBlockEntity pot) pot.setTheItem(contents);
            else helper.fail("The native filled container block entity was not installed");
        }

        int count(Item item) { return spawned.live(ItemEntity.class).stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum(); }
        int experience() { return spawned.live(ExperienceOrb.class).stream().mapToInt(ExperienceOrb::getValue).sum(); }
        void assertEmpty(String reason) {
            helper.assertTrue(spawned.live(ItemEntity.class).isEmpty() && spawned.live(ExperienceOrb.class).isEmpty(), reason);
        }
        void assertOnlyContents(ItemStack expected, String reason) {
            List<ItemEntity> actual = spawned.live(ItemEntity.class);
            helper.assertTrue(!actual.isEmpty() && actual.stream().allMatch(entity -> ItemStack.isSameItemSameComponents(expected, entity.getItem()))
                            && actual.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == expected.getCount()
                            && spawned.live(ExperienceOrb.class).isEmpty(), reason);
        }
        void clear() { spawned.live(ItemEntity.class).forEach(Entity::discard); spawned.live(ExperienceOrb.class).forEach(Entity::discard); }
        void rule(GameRules.Key<GameRules.BooleanValue> key, boolean value) { level.getGameRules().getRule(key).set(value, level.getServer()); }

        @Override public void close() {
            // Teardown is outside the captured transaction; restore container inventories after their
            // states so restoring a fixture cannot turn a dropped-content check into a false failure.
            spawned.close();
            for (var entry : previous.entrySet()) level.setBlock(entry.getKey(), entry.getValue().state, 2);
            for (var entry : previous.entrySet()) {
                CompoundTag tag = entry.getValue().blockEntity;
                if (tag != null) {
                    BlockEntity entity = BlockEntity.loadStatic(entry.getKey(), entry.getValue().state, tag, level.registryAccess());
                    if (entity != null) level.setBlockEntity(entity);
                }
            }
            rule(GameRules.RULE_DOBLOCKDROPS, oldDrops);
            rule(GameRules.RULE_MOBGRIEFING, oldGriefing);
            rule(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY, oldTntDecay);
            rule(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY, oldMobDecay);
        }
    }
}
