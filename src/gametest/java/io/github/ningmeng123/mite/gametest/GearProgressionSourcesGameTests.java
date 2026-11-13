package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.mining.SourceMiningRequirements;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearModifier;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.progression.GearProgressionRules;
import io.github.ningmeng123.mite.progression.PlayerPlacedBlocks;
import io.github.ningmeng123.mite.registry.MiteOreBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.Map;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Completed harvesting, real damage and recoverable projectile attribution. @author NingMeng123 */
@GameTestHolder("mite_gear")
@PrefixGameTestTemplate(false)
public final class GearProgressionSourcesGameTests {
    private GearProgressionSourcesGameTests() {}

    private static ServerPlayer player(GameTestHelper h) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "gear-source"), false);
        SourcePlayer player = new SourcePlayer(h, cookie);
        player.setGameMode(GameType.SURVIVAL);
        for (int tick = 0; tick < 60; tick++) player.tick();
        player.moveTo(h.absolutePos(new BlockPos(1, 3, 1)).getCenter());
        player.setNoGravity(true);
        player.setOnGround(true);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D);
        player.setHealth(100F);
        return player;
    }

    private static final class SourcePlayer extends ServerPlayer {
        private final EmbeddedChannel channel;
        private SourcePlayer(GameTestHelper h, CommonListenerCookie cookie) {
            super(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            try { server.getPlayerList().placeNewPlayer(connection, this, cookie); }
            catch (RuntimeException | Error failure) { channel.finishAndReleaseAll(); throw failure; }
        }
    }
    private static void close(ServerPlayer player) {
        try { player.server.getPlayerList().remove(player); }
        finally { ((SourcePlayer)player).channel.finishAndReleaseAll(); }
    }
    private static long xp(ItemStack stack) { return GearProgression.state(stack).experienceMillis(); }
    private static Mob victim(GameTestHelper h, ServerPlayer player, EntityType<? extends Mob> type, float maxHealth) {
        Mob victim = type.create(h.getLevel());
        victim.setNoAi(true);
        victim.setNoGravity(true);
        victim.moveTo(player.position().add(1D, 0D, 0D));
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        victim.setHealth(1F);
        return victim;
    }
    private static UseOnContext use(ServerPlayer player, BlockPos clicked, Direction side) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked), side, clicked, false));
    }

    @GameTest(template = "empty")
    public static void geologyMiningSpawnsExperienceThroughTheCompletedPlayerBreak(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos pos = h.absolutePos(new BlockPos(3, 3, 3));
        try {
            ItemStack pickaxe = new ItemStack(MiteTools.get("adamantium_pickaxe"));
            pickaxe.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 1, 0,
                    Map.of(GearModifier.TOOL_GEOLOGY.id(), 1), 1));
            player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
            var ores = java.util.List.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                    Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                    MiteOreBlocks.SILVER_ORE.get(), MiteOreBlocks.DEEPSLATE_SILVER_ORE.get(),
                    MiteOreBlocks.MITHRIL_ORE.get(), MiteOreBlocks.DEEPSLATE_MITHRIL_ORE.get(),
                    MiteOreBlocks.ADAMANTIUM_ORE.get(), MiteOreBlocks.DEEPSLATE_ADAMANTIUM_ORE.get());
            int[] rewards = {5, 5, 5, 5, 10, 10, 7, 7, 20, 20, 50, 50};
            for (int index = 0; index < ores.size(); index++) {
                h.getLevel().setBlock(pos, ores.get(index).defaultBlockState(), 3);
                PlayerPlacedBlocks.set(h.getLevel(), pos, false);
                try (var spawned = new SpawnedEntityCapture(h.getLevel(), entity ->
                        entity instanceof ExperienceOrb && entity.getBoundingBox().intersects(new AABB(pos).inflate(2D)))) {
                    h.assertTrue(player.gameMode.destroyBlock(pos), "The geology pickaxe completes the real ore break");
                    int experience = spawned.live(ExperienceOrb.class).stream().mapToInt(orb ->
                            orb.getValue() * Math.max(1, orb.saveWithoutId(new CompoundTag()).getInt("Count"))).sum();
                    h.assertValueEqual(experience, rewards[index],
                            "Natural metal ore emits geology experience after loot evaluation: " + ores.get(index));
                    int before = player.totalExperience;
                    player.moveTo(pos.getCenter());
                    for (ExperienceOrb orb : spawned.live(ExperienceOrb.class)) {
                        int count = Math.max(1, orb.saveWithoutId(new CompoundTag()).getInt("Count"));
                        for (int unit = 0; unit < count; unit++) {
                            player.takeXpDelay = 0;
                            orb.playerTouch(player);
                        }
                    }
                    h.assertValueEqual(player.totalExperience - before, rewards[index],
                            "The emitted geology experience reaches the player's experience total: " + ores.get(index));
                }
            }
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void killsUseMaximumHealthForPassiveAndHostileCreaturesButIgnoreCreativeAndPlayers(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            Mob cow = victim(h, player, EntityType.COW, 24F);
            cow.hurt(player.damageSources().playerAttack(player), 100F);
            h.assertValueEqual(xp(sword), 12_000L, "A one-health passive victim awards half its maximum health as gear experience");
            Mob zombie = victim(h, player, EntityType.ZOMBIE, 30F);
            zombie.hurt(player.damageSources().playerAttack(player), 100F);
            h.assertValueEqual(xp(sword), 27_000L, "Hostile and passive creatures share the same halved health rule");
            Player other = h.makeMockPlayer(GameType.SURVIVAL);
            other.hurt(player.damageSources().playerAttack(player), 100F);
            h.assertValueEqual(xp(sword), 27_000L, "Player kills never grant equipment experience");
            player.setGameMode(GameType.CREATIVE);
            victim(h, player, EntityType.COW, 20F).hurt(player.damageSources().playerAttack(player), 100F);
            h.assertValueEqual(xp(sword), 27_000L, "Creative kills never advance equipment");
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aRecentWeaponReceivesDelayedFireKillsButNeverBorrowsCreatureFinishingBlows(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            ItemStack sword = new ItemStack(Items.IRON_SWORD), spare = new ItemStack(Items.IRON_AXE);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            Mob cow = victim(h, player, EntityType.COW, 20F);
            cow.setHealth(20F);
            cow.hurt(player.damageSources().playerAttack(player), 1F);
            player.setItemInHand(InteractionHand.MAIN_HAND, spare);
            player.getInventory().setItem(7, sword);
            cow.invulnerableTime = 0;
            cow.hurt(player.damageSources().onFire(), 100F);
            h.assertValueEqual(xp(sword), 10_000L, "A recently credited weapon retains its halved delayed fire kill after switching hands");
            h.assertValueEqual(xp(spare), 0L, "The new held weapon cannot take a previous weapon's delayed reward");
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            player.getInventory().setItem(7, ItemStack.EMPTY);
            Mob second = victim(h, player, EntityType.COW, 20F);
            second.setHealth(20F);
            second.hurt(player.damageSources().playerAttack(player), 1F);
            second.invulnerableTime = 0;
            Mob wolf = EntityType.WOLF.create(h.getLevel());
            second.hurt(player.damageSources().mobAttack(wolf), 100F);
            h.assertValueEqual(xp(sword), 10_000L, "A different creature's finishing blow never borrows the recent player weapon");
            wolf.discard();
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualNaturalMiningAwardsOnlyTheEffectiveToolAndLastPlacedBlockRemainsMarked(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            BlockPos natural = h.absolutePos(new BlockPos(2, 3, 1));
            h.getLevel().setBlock(natural, Blocks.STONE.defaultBlockState(), 3);
            ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
            player.setItemInHand(InteractionHand.MAIN_HAND, pick);
            var state = h.getLevel().getBlockState(natural);
            long expected = GearProgressionRules.miningExperienceMillis(
                    MiningProgress.effectiveHardness(state, state.getDestroySpeed(h.getLevel(), natural)),
                    SourceMiningRequirements.minimumHarvestLevel(state)) / 2L;
            h.assertTrue(player.gameMode.destroyBlock(natural), "The real player removal succeeds");
            h.assertValueEqual(xp(pick), expected, "Natural harvesting halves the source hardness and harvest-tier reward exactly once");
            BlockPos support = h.absolutePos(new BlockPos(3, 2, 1));
            h.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            ItemStack lastBlock = new ItemStack(Items.STONE);
            player.setItemInHand(InteractionHand.MAIN_HAND, lastBlock);
            lastBlock.useOn(use(player, support, Direction.UP));
            BlockPos placed = support.above();
            h.assertTrue(lastBlock.isEmpty() && h.getLevel().getBlockState(placed).is(Blocks.STONE), "The last held block was really placed");
            h.assertTrue(PlayerPlacedBlocks.isPlaced(h.getLevel(), placed), "Committed provenance survives consuming the final block item");
            player.setItemInHand(InteractionHand.MAIN_HAND, pick);
            player.gameMode.destroyBlock(placed);
            h.assertValueEqual(xp(pick), expected, "A player-placed block cannot recycle equipment experience");
            h.assertFalse(PlayerPlacedBlocks.isPlaced(h.getLevel(), placed), "Removing the player block releases its position marker");
            ItemStack hoe = new ItemStack(Items.IRON_HOE);
            player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            h.getLevel().setBlock(natural, Blocks.OAK_LEAVES.defaultBlockState(), 3);
            player.gameMode.destroyBlock(natural);
            h.assertValueEqual(xp(hoe), 0L, "A hoe cannot gain experience by breaking blocks");
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void normalAndAffixTreeFellingAwardEveryNaturalLogWithoutRewardingThePlacedMiddleLog(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            ResourceLocation key = ResourceLocation.fromNamespaceAndPath("mite", "tree_felling");
            var enchantment = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT, key));
            for (boolean affix : new boolean[]{false, true}) {
                ItemStack axe = new ItemStack(Items.IRON_AXE);
                if (affix) axe.set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 2, 0L, Map.of(key, 2), 1));
                else axe.enchant(enchantment, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, axe);
                BlockPos trunk = h.absolutePos(new BlockPos(2, 3, 1));
                for (int height = 0; height < 3; height++) h.getLevel().setBlock(trunk.above(height), Blocks.OAK_LOG.defaultBlockState(), 3);
                PlayerPlacedBlocks.set(h.getLevel(), trunk.above(), true);
                var state = h.getLevel().getBlockState(trunk);
                long perLog = GearProgressionRules.miningExperienceMillis(
                        MiningProgress.effectiveHardness(state, state.getDestroySpeed(h.getLevel(), trunk)),
                        SourceMiningRequirements.minimumHarvestLevel(state)) / 2L;
                h.assertTrue(player.gameMode.destroyBlock(trunk), "The original log removal succeeds");
                for (int height = 0; height < 3; height++)
                    h.assertTrue(h.getLevel().getBlockState(trunk.above(height)).isAir(), "The real tree-felling callback removes every vertical log");
                h.assertValueEqual(xp(axe), 2L * perLog,
                        (affix ? "Affix" : "Ordinary") + " tree felling awards each natural log while excluding the player-placed middle log");
                h.assertFalse(PlayerPlacedBlocks.isPlaced(h.getLevel(), trunk.above()), "Removing the placed middle log clears its marker");
            }
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledBlockRemovalAndCancelledPlacementNeverGrantExperienceOrProvenance(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos pos = h.absolutePos(new BlockPos(2, 3, 1));
        Consumer<BlockEvent.BreakEvent> cancelBreak = event -> { if (event.getPlayer() == player) event.setCanceled(true); };
        Consumer<BlockEvent.EntityPlaceEvent> cancelPlace = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        try {
            ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
            player.setItemInHand(InteractionHand.MAIN_HAND, pick);
            h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            NeoForge.EVENT_BUS.addListener(cancelBreak);
            try { h.assertFalse(player.gameMode.destroyBlock(pos), "Protection cancels the real player removal"); }
            finally { NeoForge.EVENT_BUS.unregister(cancelBreak); }
            h.assertValueEqual(xp(pick), 0L, "A cancelled break never awards experience");
            ItemStack block = new ItemStack(Items.STONE);
            player.setItemInHand(InteractionHand.MAIN_HAND, block);
            NeoForge.EVENT_BUS.addListener(cancelPlace);
            try { block.useOn(use(player, pos, Direction.UP)); }
            finally { NeoForge.EVENT_BUS.unregister(cancelPlace); }
            h.assertTrue(h.getLevel().getBlockState(pos.above()).isAir(), "The cancelled placement rolls back");
            h.assertFalse(PlayerPlacedBlocks.isPlaced(h.getLevel(), pos.above()), "Rolled-back placements never record player provenance");
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedAndPlayerLaidSoilEarnTillingExperienceOnlyAfterCommit(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos pos = h.absolutePos(new BlockPos(2, 3, 1));
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        try {
            h.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            h.getLevel().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            PlayerPlacedBlocks.set(h.getLevel(), pos, true);
            ItemStack hoe = new ItemStack(Items.IRON_HOE);
            player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            hoe.useOn(use(player, pos, Direction.UP));
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.FARMLAND), "The actual hoe creates farmland");
            h.assertValueEqual(xp(hoe), 6_000L, "Player-laid soil earns the halved six-point tilling reward");
            h.assertTrue(PlayerPlacedBlocks.isPlaced(h.getLevel(), pos), "Tilling preserves the placed soil's mining provenance");
            h.getLevel().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            hoe.useOn(use(player, pos, Direction.UP));
            h.assertValueEqual(xp(hoe), 12_000L, "A later successful tilling of the same coordinate earns another halved reward");
            h.getLevel().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            NeoForge.EVENT_BUS.addListener(cancel);
            try { hoe.useOn(use(player, pos, Direction.UP)); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.DIRT), "A cancelled tilling returns the original soil");
            h.assertValueEqual(xp(hoe), 12_000L, "The cancelled tilling never retains an experience reward");
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorSharesPreMitigationDamageAndRejectsCancelledOrInvulnerableHits(GameTestHelper h) {
        ServerPlayer player = player(h);
        Consumer<LivingIncomingDamageEvent> cancel = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        try {
            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE), boots = new ItemStack(Items.IRON_BOOTS);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.setItemSlot(EquipmentSlot.FEET, boots);
            player.hurt(player.damageSources().generic(), 4F);
            h.assertValueEqual(xp(chest), 1_000L, "Four incoming damage grants two total experience shared over two armor pieces");
            h.assertValueEqual(xp(boots), 1_000L, "Each equipped armor piece receives the same halved one-point share");
            player.hurt(player.damageSources().generic(), 3F);
            h.assertValueEqual(xp(chest), 1_000L, "The invulnerability cooldown rejects a smaller repeated hit");
            player.hurt(player.damageSources().generic(), 6F);
            h.assertValueEqual(xp(chest), 1_500L, "Only the stronger hit's two-point cooldown difference contributes another half point per piece");
            player.invulnerableTime = 0;
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
            float health = player.getHealth();
            player.hurt(player.damageSources().generic(), 5F);
            h.assertValueEqual(player.getHealth(), health, "Full resistance absorbs all health damage");
            h.assertValueEqual(xp(chest), 2_750L, "Fully mitigated five-point damage still contributes its halved 1.25-point share before armor reduction");
            h.assertValueEqual(GearProgression.state(boots).level(), 0,
                    "The 2.75-point armor share stays below the unchanged twenty-point boots threshold");
            h.assertValueEqual(xp(boots), 2_750L, "The boots retain the halved fractional reward without upgrading early");
            h.assertTrue(GearProgression.state(boots).affixes().isEmpty(),
                    "A below-threshold incoming-damage reward does not grant a modifier");
            var bootsBeforeCancellation = GearProgression.state(boots);
            player.invulnerableTime = 0;
            NeoForge.EVENT_BUS.addListener(cancel);
            try { player.hurt(player.damageSources().generic(), 5F); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertValueEqual(xp(chest), 2_750L, "Cancelled incoming damage cannot train armor");
            h.assertTrue(GearProgression.state(boots).equals(bootsBeforeCancellation),
                    "Cancelling the next hit preserves the boots' level, exact fractional experience and empty modifier pool");
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fractionalArmorDamageRetainsThousandthPrecisionAcrossThreeAndFourPieces(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
            float health = player.getHealth();
            ItemStack helmet = new ItemStack(Items.IRON_HELMET), chest = new ItemStack(Items.IRON_CHESTPLATE),
                    boots = new ItemStack(Items.IRON_BOOTS);
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.setItemSlot(EquipmentSlot.FEET, boots);
            player.hurt(player.damageSources().generic(), 1F);
            h.assertValueEqual(xp(helmet), 167L, "The first armor slot halves its 334-unit share without losing whole fractional points");
            h.assertValueEqual(xp(chest), 166L, "The second armor slot halves its 333-unit share at stored thousandth precision");
            h.assertValueEqual(xp(boots), 166L, "The third armor slot retains the same halved fractional share");
            h.assertValueEqual(xp(helmet) + xp(chest) + xp(boots), 499L, "Each odd per-item share loses only its unrepresentable half of one stored unit");

            helmet = new ItemStack(Items.IRON_HELMET);
            chest = new ItemStack(Items.IRON_CHESTPLATE);
            ItemStack leggings = new ItemStack(Items.IRON_LEGGINGS);
            boots = new ItemStack(Items.IRON_BOOTS);
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.setItemSlot(EquipmentSlot.LEGS, leggings);
            player.setItemSlot(EquipmentSlot.FEET, boots);
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), .25F);
            h.assertValueEqual(xp(helmet), 31L, "The first of four pieces halves its 63-unit share at thousandth precision");
            h.assertValueEqual(xp(chest), 31L, "The chest halves its 63-unit share from a quarter-damage hit");
            h.assertValueEqual(xp(leggings), 31L, "The leggings retain their halved 62-unit fractional share");
            h.assertValueEqual(xp(boots), 31L, "The boots retain the same halved fractional share");
            h.assertValueEqual(xp(helmet) + xp(chest) + xp(leggings) + xp(boots), 124L,
                    "Only sub-thousandth per-item fractions are discarded from the halved quarter-damage reward");
            h.assertValueEqual(player.getHealth(), health,
                    "Armor training still uses the effective incoming damage before full resistance, rather than health lost");
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void halfPointPerDamageArmorExperienceAccumulatesThroughTheUnchangedBootsUpgradeBoundary(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            ItemStack boots = new ItemStack(Items.IRON_BOOTS);
            player.setItemSlot(EquipmentSlot.FEET, boots);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
            h.assertValueEqual(GearProgression.nextLevelExperienceMillis(boots, 0), 20_000L,
                    "Halving experience per received damage does not change the boots' twenty-point first threshold");
            for (int hit = 0; hit < 19; hit++) {
                player.invulnerableTime = 0;
                player.hurt(player.damageSources().generic(), 2F);
            }
            h.assertValueEqual(xp(boots), 19_000L, "Nineteen two-damage hits train the only armor piece by nineteen experience");
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), 1.5F);
            GearProgress before = GearProgression.state(boots);
            h.assertValueEqual(before.experienceMillis(), 19_750L, "Another 1.5 damage adds 0.75 experience and accumulates the boots to exactly 19.75");
            h.assertTrue(before.level() == 0 && before.affixes().isEmpty(), "Fractional rewards do not upgrade or grant modifiers before the threshold");

            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), .5F);
            GearProgress upgraded = GearProgression.state(boots);
            h.assertTrue(upgraded.level() == 1 && upgraded.experienceMillis() == 0 && upgraded.id().equals(before.id()),
                    "A half damage point grants the final quarter experience, consumes exactly twenty points and retains equipment identity");
            h.assertValueEqual(upgraded.affixes().values().stream().mapToInt(Integer::intValue).sum(), 1,
                    "The actual accumulated incoming-damage reward grants exactly one modifier at the first level");
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), .5F);
            GearProgress after = GearProgression.state(boots);
            h.assertTrue(after.level() == 1 && after.experienceMillis() == 250L && after.id().equals(upgraded.id())
                            && after.affixes().equals(upgraded.affixes()),
                    "The next half-damage hit retains a quarter experience toward the unchanged next level without another modifier");
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldsTrainOnBlockedDamageWhileArmorSharesOnlyTheUnblockedRemainder(GameTestHelper h) {
        ServerPlayer player = player(h);
        Consumer<LivingShieldBlockEvent> partial = event -> { if (event.getEntity() == player) event.setBlockedDamage(2F); };
        try {
            ItemStack shield = new ItemStack(Items.SHIELD), chest = new ItemStack(Items.IRON_CHESTPLATE);
            player.setItemInHand(InteractionHand.OFF_HAND, shield);
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.setYRot(0F);
            player.setYHeadRot(0F);
            player.yBodyRot = 0F;
            player.startUsingItem(InteractionHand.OFF_HAND);
            for (int tick = 0; tick < 6; tick++) player.doTick();
            h.assertTrue(player.isBlocking(), "The real server player's shield has completed its native warm-up");
            player.setXRot(0F);
            player.setYRot(0F);
            player.setYHeadRot(0F);
            Mob attacker = EntityType.COW.create(h.getLevel());
            attacker.setPos(player.position().add(0D, 0D, 3D));
            DamageSource source = player.damageSources().mobAttack(attacker);
            h.assertTrue(player.isDamageSourceBlocked(source), "The attacker is inside the shield's native front arc");
            int[] incoming = {0};
            float[] blocked = {-1F};
            Consumer<LivingIncomingDamageEvent> incomingObserver = event -> { if (event.getEntity() == player) incoming[0]++; };
            Consumer<LivingShieldBlockEvent> blockObserver = event -> {
                if (event.getEntity() == player && event.getBlocked()) blocked[0] = event.getBlockedDamage();
            };
            NeoForge.EVENT_BUS.addListener(incomingObserver);
            NeoForge.EVENT_BUS.addListener(blockObserver);
            try { player.hurt(source, 4F); }
            finally {
                NeoForge.EVENT_BUS.unregister(incomingObserver);
                NeoForge.EVENT_BUS.unregister(blockObserver);
            }
            h.assertValueEqual(incoming[0], 1, "The non-player attack enters the real server incoming damage pipeline");
            h.assertValueEqual(blocked[0], 4F, "The real shield event accepts all four incoming damage points");
            h.assertValueEqual(xp(shield), 4_000L, "The shield earns one experience per completely blocked damage point after halving");
            h.assertValueEqual(xp(chest), 0L, "A completely blocked hit cannot also train armor");
            player.invulnerableTime = 0;
            NeoForge.EVENT_BUS.addListener(partial);
            try { player.hurt(source, 4F); }
            finally { NeoForge.EVENT_BUS.unregister(partial); }
            h.assertValueEqual(xp(shield), 6_000L, "A partial shield block awards only the two blocked points at half efficiency");
            h.assertValueEqual(xp(chest), 1_000L, "Armor receives one experience from the remaining two damage points before its own mitigation");
            attacker.discard();
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bowKillsAfterSwitchingWeaponsRewardOnlyTheOriginalBowAndSurviveProjectileSave(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            ItemStack bow = new ItemStack(Items.BOW), sword = new ItemStack(Items.IRON_SWORD);
            player.setItemInHand(InteractionHand.MAIN_HAND, bow);
            player.getInventory().setItem(8, new ItemStack(Items.ARROW, 2));
            bow.getItem().releaseUsing(bow, h.getLevel(), player, bow.getUseDuration(player) - 20);
            AbstractArrow arrow = h.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(8),
                    candidate -> candidate.getOwner() == player).stream().findFirst().orElseThrow();
            CompoundTag saved = arrow.saveWithoutId(new CompoundTag());
            AbstractArrow restored = (AbstractArrow)arrow.getType().create(h.getLevel());
            restored.load(saved);
            restored.setOwner(player);
            arrow.discard();
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            player.getInventory().setItem(7, bow);
            victim(h, player, EntityType.COW, 20F).hurt(player.damageSources().arrow(restored, player), 100F);
            h.assertValueEqual(xp(bow), 10_000L, "The reloaded arrow resolves the original bow and halves its reward in a different inventory slot");
            h.assertValueEqual(xp(sword), 0L, "Switching to a sword cannot steal bow experience");
            player.getInventory().setItem(6, bow.copy());
            victim(h, player, EntityType.COW, 20F).hurt(player.damageSources().arrow(restored, player), 100F);
            h.assertValueEqual(xp(bow), 10_000L, "Ambiguous cloned weapon identities cannot duplicate a reward");
            restored.discard();
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void thrownTridentKeepsExperienceInTheRecoverableStack(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            ItemStack original = new ItemStack(Items.TRIDENT);
            ThrownTrident trident = new ThrownTrident(h.getLevel(), player, original);
            victim(h, player, EntityType.COW, 20F).hurt(player.damageSources().trident(trident, player), 100F);
            h.assertValueEqual(xp(trident.getPickupItemStackOrigin()), 10_000L, "Halved thrown experience updates the real recoverable trident");
            CompoundTag saved = trident.saveWithoutId(new CompoundTag());
            ThrownTrident restored = new ThrownTrident(EntityType.TRIDENT, h.getLevel());
            restored.load(saved);
            h.assertValueEqual(xp(restored.getPickupItemStackOrigin()), 10_000L, "The recoverable trident retains progression after saving");
            trident.discard();
            restored.discard();
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void playerSandKeepsProvenanceWhenItFallsAndItsEntityIsReloaded(GameTestHelper h) {
        BlockPos source = h.absolutePos(new BlockPos(2, 5, 2));
        h.getLevel().setBlock(source, Blocks.SAND.defaultBlockState(), 3);
        PlayerPlacedBlocks.set(h.getLevel(), source, true);
        FallingBlockEntity falling = FallingBlockEntity.fall(h.getLevel(), source, Blocks.SAND.defaultBlockState());
        h.assertFalse(PlayerPlacedBlocks.isPlaced(h.getLevel(), source), "Falling removes the old source marker");
        CompoundTag saved = falling.saveWithoutId(new CompoundTag());
        FallingBlockEntity restored = EntityType.FALLING_BLOCK.create(h.getLevel());
        restored.load(saved);
        falling.discard();
        BlockPos landing = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(landing.below(), Blocks.STONE.defaultBlockState(), 3);
        restored.setPos(Vec3.atBottomCenterOf(landing).add(0D, .01D, 0D));
        restored.setDeltaMovement(0D, -.2D, 0D);
        restored.setOnGround(true);
        restored.tick();
        h.assertTrue(h.getLevel().getBlockState(landing).is(Blocks.SAND), "The reloaded falling sand really lands");
        h.assertTrue(PlayerPlacedBlocks.isPlaced(h.getLevel(), landing), "The landed player sand cannot be laundered into natural mining experience");
        restored.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualPistonMovementTransfersPlayerProvenanceAcrossOverlappingSources(GameTestHelper h) throws ReflectiveOperationException {
        BlockPos piston = h.absolutePos(new BlockPos(1, 3, 1)), first = piston.east(), second = first.east();
        h.getLevel().setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), 3);
        h.getLevel().setBlock(first, Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(second, Blocks.STONE.defaultBlockState(), 3);
        PlayerPlacedBlocks.set(h.getLevel(), first, true);
        Method move = PistonBaseBlock.class.getDeclaredMethod("moveBlocks", Level.class, BlockPos.class, Direction.class, boolean.class);
        move.setAccessible(true);
        h.assertTrue((boolean)move.invoke(Blocks.PISTON, h.getLevel(), piston, Direction.EAST, true), "The native piston accepts its push list");
        h.assertTrue(PlayerPlacedBlocks.isPlaced(h.getLevel(), second), "The first player block transfers its marker into the overlapping destination");
        h.assertFalse(PlayerPlacedBlocks.isPlaced(h.getLevel(), second.east()), "The originally natural second block remains natural");
        var moving = h.getLevel().getBlockEntity(second);
        if (moving instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity entity) entity.finalTick();
        h.assertTrue(PlayerPlacedBlocks.isPlaced(h.getLevel(), second), "Native early finalization retains the transferred marker");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void simplySwordsSpearKillProgressionSurvivesItsNativeWearAndSaveSynchronization(GameTestHelper h) throws ReflectiveOperationException {
        if (!SimplySwordsCompat.enabled()) { h.succeed(); return; }
        ServerPlayer player = player(h);
        try {
            ItemStack spearStack = new ItemStack(SimplySwordsCompat.weapon("iron", "spear"));
            Class<?> type = Class.forName("net.sweenus.simplyswords.entity.ThrownSpearEntity");
            AbstractArrow spear = (AbstractArrow)type.getConstructor(Level.class, LivingEntity.class, ItemStack.class)
                    .newInstance(h.getLevel(), player, spearStack);
            type.getField("primaryBaseDamage").setFloat(spear, 50F);
            Mob zombie = victim(h, player, EntityType.ZOMBIE, 20F);
            Method hit = type.getDeclaredMethod("onHitEntity", EntityHitResult.class);
            hit.setAccessible(true);
            hit.invoke(spear, new EntityHitResult(zombie));
            h.assertTrue(zombie.isDeadOrDying(), "The native approved spear hit actually kills the target");
            h.assertValueEqual(xp(spearStack), 10_000L, "The halved kill trains the original spear rather than its synchronized visual copy");
            h.assertValueEqual(xp(spear.getPickupItemStackOrigin()), 10_000L, "Subsequent native durability synchronization retains the kill reward");
            CompoundTag saved = spear.saveWithoutId(new CompoundTag());
            AbstractArrow restored = (AbstractArrow)spear.getType().create(h.getLevel());
            restored.load(saved);
            h.assertValueEqual(xp(restored.getPickupItemStackOrigin()), 10_000L, "The actual recoverable spear retains progression across reload");
            spear.discard();
            restored.discard();
        } finally { close(player); }
        h.succeed();
    }
}
