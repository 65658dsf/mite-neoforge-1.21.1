package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import io.github.ningmeng123.mite.progression.GearProgress;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.progression.PlayerPlacedBlocks;
import io.github.ningmeng123.mite.survival.DualFoodData;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.Map;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Committed harvesting, primary health loss and non-training projectile transport. @author NingMeng123 */
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
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
        player.getFoodData().setFoodLevel(6);
        ((DualFoodData)player.getFoodData()).mite$setNutrition(0);
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
    private static long armorXp(ItemStack[] armor) {
        long total = 0L;
        for (ItemStack stack : armor) total += xp(stack);
        return total;
    }
    private static void equipPrimaryWeapon(ServerPlayer player, ItemStack weapon) {
        // Source XP is under test here, independently of weapon stat profiles or nutrition bonuses.
        weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mite", "source_xp_fixture"),
                        0D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build());
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        player.tick();
        player.setOnGround(true);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4D);
    }
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
    public static void primaryMeleeUsesLostHealthWhileDirectKillsCreativeAndPlayersCannotTrain(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            equipPrimaryWeapon(player, sword);
            h.assertFalse(io.github.ningmeng123.mite.combat.SourcePlayerCombat.weak(player), "The real sword fixture has energy for a damaging primary attack");
            Mob cow = victim(h, player, EntityType.COW, 24F);
            player.attack(cow);
            h.assertTrue(cow.isDeadOrDying(), "The actual primary sword attack kills its one-health target");
            h.assertValueEqual(xp(sword), 1000L, "A one-health victim awards one actual lost-health point rather than its maximum health");
            Mob zombie = victim(h, player, EntityType.ZOMBIE, 30F);
            zombie.hurt(player.damageSources().playerAttack(player), 100F);
            h.assertValueEqual(xp(sword), 1000L, "A direct damage/kill call outside primary melee cannot grant a kill reward");
            Player other = h.makeMockPlayer(GameType.SURVIVAL);
            other.hurt(player.damageSources().playerAttack(player), 100F);
            h.assertValueEqual(xp(sword), 1000L, "Player kills never grant equipment experience");
            player.setGameMode(GameType.CREATIVE);
            player.attack(victim(h, player, EntityType.COW, 20F));
            h.assertValueEqual(xp(sword), 1000L, "Creative primary attacks never advance equipment");
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void immediateMeleeRewardsDoNotGrowFromDelayedFireOrCreatureFinishingBlows(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            ItemStack sword = new ItemStack(Items.IRON_SWORD), spare = new ItemStack(Items.IRON_AXE);
            equipPrimaryWeapon(player, sword);
            h.assertFalse(io.github.ningmeng123.mite.combat.SourcePlayerCombat.weak(player), "The nonlethal primary attack is not a weak knockback-only strike");
            Mob cow = victim(h, player, EntityType.COW, 20F);
            cow.setHealth(20F);
            player.attack(cow);
            long immediate = xp(sword);
            h.assertTrue(immediate > 0 && cow.isAlive(), "A nonlethal primary attack grants its immediate whole-health reward");
            player.setItemInHand(InteractionHand.MAIN_HAND, spare);
            player.getInventory().setItem(7, sword);
            cow.invulnerableTime = 0;
            cow.hurt(player.damageSources().onFire(), 100F);
            h.assertValueEqual(xp(sword), immediate, "Delayed fire adds no experience to the switched-out melee weapon");
            h.assertValueEqual(xp(spare), 0L, "The new held weapon cannot take a previous weapon's delayed reward");
            equipPrimaryWeapon(player, sword);
            player.getInventory().setItem(7, ItemStack.EMPTY);
            Mob second = victim(h, player, EntityType.COW, 20F);
            second.setHealth(20F);
            player.attack(second);
            long beforeCreatureFinisher = xp(sword);
            second.invulnerableTime = 0;
            Mob wolf = EntityType.WOLF.create(h.getLevel());
            second.hurt(player.damageSources().mobAttack(wolf), 100F);
            h.assertValueEqual(xp(sword), beforeCreatureFinisher, "A different creature's finishing blow cannot add experience to a recent player weapon");
            wolf.discard();
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void effectiveMiningAwardsNaturalAndPlayerPlacedBlocksWhileProvenanceStillCommits(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            BlockPos natural = h.absolutePos(new BlockPos(2, 3, 1));
            h.getLevel().setBlock(natural, Blocks.STONE.defaultBlockState(), 3);
            ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
            player.setItemInHand(InteractionHand.MAIN_HAND, pick);
            long expected = 2000L;
            h.assertTrue(player.gameMode.destroyBlock(natural), "The real player removal succeeds");
            h.assertValueEqual(xp(pick), expected, "An effective pickaxe gains the source stone material's two experience points");
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
            h.assertValueEqual(xp(pick), 2L * expected, "Effective harvesting also grants experience for committed player-placed stone");
            h.assertFalse(PlayerPlacedBlocks.isPlaced(h.getLevel(), placed), "Removing the player block releases its position marker");
            ItemStack hoe = new ItemStack(Items.IRON_HOE);
            player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            h.getLevel().setBlock(natural, Blocks.OAK_LEAVES.defaultBlockState(), 3);
            player.gameMode.destroyBlock(natural);
            h.assertValueEqual(xp(hoe), 0L, "Breaking leaves with a hoe is not a source crop-harvesting action");
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void normalAndAffixTreeFellingAwardEveryEffectiveLogIncludingPlayerPlacedLogs(GameTestHelper h) {
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
                long perLog = 2000L;
                h.assertTrue(player.gameMode.destroyBlock(trunk), "The original log removal succeeds");
                for (int height = 0; height < 3; height++)
                    h.assertTrue(h.getLevel().getBlockState(trunk.above(height)).isAir(), "The real tree-felling callback removes every vertical log");
                h.assertValueEqual(xp(axe), 3L * perLog,
                        (affix ? "Affix" : "Ordinary") + " tree felling awards two points per effective log, including the placed middle log");
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
            h.assertValueEqual(xp(hoe), 1000L, "Player-laid soil grants the source one-point tilling reward");
            h.assertTrue(PlayerPlacedBlocks.isPlaced(h.getLevel(), pos), "Tilling preserves the placed soil's mining provenance");
            h.getLevel().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            hoe.useOn(use(player, pos, Direction.UP));
            h.assertValueEqual(xp(hoe), 2000L, "A later successful tilling of the same coordinate earns another one-point reward");
            h.getLevel().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            NeoForge.EVENT_BUS.addListener(cancel);
            try { hoe.useOn(use(player, pos, Direction.UP)); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.DIRT), "A cancelled tilling returns the original soil");
            h.assertValueEqual(xp(hoe), 2000L, "The cancelled tilling never retains an experience reward");
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void armorTrainsActualEntityDamagePortionsAndRejectsEnvironmentCancelledOrInvulnerableHits(GameTestHelper h) {
        ServerPlayer player = player(h);
        Consumer<LivingIncomingDamageEvent> cancel = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        try {
            ItemStack[] armor = {new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.IRON_LEGGINGS),
                    new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_HELMET)};
            EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            for (int index = 0; index < armor.length; index++) {
                // Keep this source test below its next level threshold even if the random allocator chooses one piece repeatedly.
                armor[index].set(GearProgression.PROGRESS.get(), new GearProgress(UUID.randomUUID(), 10, 0L, Map.of(), 1));
                player.setItemSlot(slots[index], armor[index]);
            }
            player.hurt(player.damageSources().generic(), 4F);
            h.assertValueEqual(armorXp(armor), 0L, "Environmental damage cannot train armor even when it spends durability");
            Mob attacker = EntityType.COW.create(h.getLevel());
            DamageSource source = player.damageSources().mobAttack(attacker);
            player.invulnerableTime = 0;
            int[] beforeWear = java.util.Arrays.stream(armor).mapToInt(ItemStack::getDamageValue).toArray();
            player.hurt(source, 4F);
            long accepted = armorXp(armor);
            h.assertTrue(accepted > 0 && accepted % 4000L == 0,
                    "Each actual random armor portion grants two raw points followed by armor's outer doubling");
            for (int index = 0; index < armor.length; index++) {
                h.assertTrue(xp(armor[index]) % 4000L == 0, "Armor experience belongs to selected portions rather than equal damage shares");
                if (xp(armor[index]) > 0) h.assertTrue(armor[index].getDamageValue() > beforeWear[index],
                        "Only a piece selected for actual wear receives source experience");
            }
            player.hurt(source, 3F);
            h.assertValueEqual(armorXp(armor), accepted, "The invulnerability cooldown rejects a smaller repeated hit");
            player.hurt(source, 6F);
            long stronger = armorXp(armor);
            h.assertTrue(stronger > accepted && (stronger - accepted) % 4000L == 0,
                    "The stronger hit's accepted cooldown difference creates new wear portions");
            player.invulnerableTime = 0;
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
            float health = player.getHealth();
            player.hurt(source, 5F);
            h.assertValueEqual(player.getHealth(), health, "Full resistance absorbs all health damage");
            long mitigated = armorXp(armor);
            h.assertTrue(mitigated > stronger && (mitigated - stronger) % 4000L == 0,
                    "Entity hits still train their actual armor portions before health mitigation");
            player.invulnerableTime = 0;
            NeoForge.EVENT_BUS.addListener(cancel);
            try { player.hurt(source, 5F); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertValueEqual(armorXp(armor), mitigated, "Cancelled incoming damage cannot train armor");
            attacker.discard();
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldBlocksGrantNoExperienceWhileUnblockedEntityDamageCanTrainArmorPortions(GameTestHelper h) {
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
            h.assertValueEqual(xp(shield), 0L, "Completely blocked damage does not train the shield");
            h.assertValueEqual(xp(chest), 0L, "A completely blocked hit cannot also train armor");
            player.invulnerableTime = 0;
            NeoForge.EVENT_BUS.addListener(partial);
            try { player.hurt(source, 4F); }
            finally { NeoForge.EVENT_BUS.unregister(partial); }
            h.assertValueEqual(xp(shield), 0L, "A partial shield block also grants no shield experience");
            h.assertValueEqual(xp(chest), chest.getDamageValue() > 0 ? 4000L : 0L,
                    "The single worn chestplate receives four experience only if its actual random wear portion was selected");
            attacker.discard();
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bowProjectileSaveAndWeaponSwitchingNeverCreateKillExperience(GameTestHelper h) {
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
            h.assertValueEqual(xp(bow), 0L, "The reloaded arrow grants no bow experience after switching inventory slots");
            h.assertValueEqual(xp(sword), 0L, "Switching to a sword cannot steal bow experience");
            player.getInventory().setItem(6, bow.copy());
            victim(h, player, EntityType.COW, 20F).hurt(player.damageSources().arrow(restored, player), 100F);
            h.assertValueEqual(xp(bow), 0L, "Cloned weapon identities cannot create a projectile kill reward");
            restored.discard();
        } finally { close(player); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void thrownTridentKeepsItsRecoverableStackWithoutTrainingOnKills(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            ItemStack original = new ItemStack(Items.TRIDENT);
            ThrownTrident trident = new ThrownTrident(h.getLevel(), player, original);
            victim(h, player, EntityType.COW, 20F).hurt(player.damageSources().trident(trident, player), 100F);
            h.assertValueEqual(xp(trident.getPickupItemStackOrigin()), 0L, "A thrown kill does not train the recoverable trident");
            CompoundTag saved = trident.saveWithoutId(new CompoundTag());
            ThrownTrident restored = new ThrownTrident(EntityType.TRIDENT, h.getLevel());
            restored.load(saved);
            h.assertValueEqual(xp(restored.getPickupItemStackOrigin()), 0L, "Reloading the recoverable trident does not manufacture experience");
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
        h.assertTrue(PlayerPlacedBlocks.isPlaced(h.getLevel(), landing), "Landing preserves player provenance for geology and source ore-drop rules");
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
    public static void simplySwordsThrownSpearKeepsNativeWearAndSaveSynchronizationWithoutKillExperience(GameTestHelper h) throws ReflectiveOperationException {
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
            h.assertValueEqual(xp(spearStack), 0L, "Thrown spear kills do not train the original item");
            h.assertValueEqual(xp(spear.getPickupItemStackOrigin()), 0L, "Native durability synchronization cannot add a projectile kill reward");
            CompoundTag saved = spear.saveWithoutId(new CompoundTag());
            AbstractArrow restored = (AbstractArrow)spear.getType().create(h.getLevel());
            restored.load(saved);
            h.assertValueEqual(xp(restored.getPickupItemStackOrigin()), 0L, "The recoverable spear retains the non-training result across reload");
            spear.discard();
            restored.discard();
        } finally { close(player); }
        h.succeed();
    }
}
