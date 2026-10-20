package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.progression.GearProgression;
import io.github.ningmeng123.mite.progression.PlayerPlacedBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.survival.DualFoodData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real old-style action rewards, integer health loss and repeated random armor portions. @author NingMeng123 */
@GameTestHolder("mite_gear")
@PrefixGameTestTemplate(false)
public final class LegacyGearExperienceGameTests {
    private LegacyGearExperienceGameTests() {}

    private static long xp(ItemStack stack) { return GearProgression.state(stack).experienceMillis(); }
    private static Cow cow(GameTestHelper h, ServerPlayer player, float health) {
        Cow target = EntityType.COW.create(h.getLevel());
        target.setNoAi(true); target.setNoGravity(true);
        target.setPos(player.position().add(1D, 0D, 0D));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D); target.setHealth(health);
        return target;
    }
    private static ItemStack plainWeapon(ItemStack stack) {
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mite", "legacy_xp_fixture"),
                        0D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build());
        return stack;
    }
    private static void equip(Session session, ItemStack stack) {
        session.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        session.player.tick(); session.player.setOnGround(true);
    }

    @GameTest(template = "empty")
    public static void realPrimaryMeleeAwardsTruncatedHealthLossBeforeDeathWithoutKillBonuses(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ItemStack sword = plainWeapon(new ItemStack(Items.IRON_SWORD));
            equip(session, sword);
            session.player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.9D);
            Cow living = cow(h, session.player, 100F);
            session.player.attack(living);
            h.assertTrue(living.getHealth() > 90F && living.getHealth() < 100F, "The first primary hit is real and nonlethal");
            h.assertValueEqual(xp(sword), 3_000L, "The nonlethal 3.9-health hit immediately trains three whole experience");
            session.player.tick(); session.player.setOnGround(true);
            Cow absorbed = cow(h, session.player, 100F);
            absorbed.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20D); absorbed.setAbsorptionAmount(20F);
            h.assertValueEqual(absorbed.getAbsorptionAmount(), 20F, "The native absorption fixture has a nonzero maximum and twenty usable points");
            session.player.attack(absorbed);
            h.assertValueEqual(absorbed.getHealth(), 100F, "Absorption consumed the second hit without health loss");
            h.assertValueEqual(xp(sword), 3_000L, "Absorbed health does not train the weapon");
            session.player.tick(); session.player.setOnGround(true);
            Cow nearlyDead = cow(h, session.player, 1F);
            session.player.attack(nearlyDead);
            h.assertTrue(nearlyDead.isDeadOrDying(), "The next primary hit actually kills a one-health target");
            h.assertValueEqual(xp(sword), 4_000L, "A kill grants only the one actual lost health, without maximum-health bonus");
            living.discard(); absorbed.discard(); nearlyDead.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void directDamageAndOrdinaryAxesCannotBorrowPrimaryWeaponExperience(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ItemStack axe = plainWeapon(new ItemStack(Items.IRON_AXE)); equip(session, axe);
            Cow victim = cow(h, session.player, 100F); session.player.attack(victim);
            h.assertTrue(victim.getHealth() < 100F, "The ordinary axe really damages the target");
            h.assertValueEqual(xp(axe), 0L, "An ordinary axe follows the source nonweapon criterion");
            ItemStack sword = plainWeapon(new ItemStack(Items.IRON_SWORD)); equip(session, sword);
            Cow direct = cow(h, session.player, 1F);
            direct.hurt(session.player.damageSources().playerAttack(session.player), 100F);
            h.assertTrue(direct.isDeadOrDying(), "A standalone player-owned damage call really kills its target");
            h.assertValueEqual(xp(sword), 0L, "Death callbacks and arbitrary player-owned damage do not replace a primary melee hit");
            victim.discard(); direct.discard();
        }
        h.succeed();
    }

    private static void mine(GameTestHelper h, Session session, BlockPos pos, ItemStack tool, BlockState state) {
        h.getLevel().setBlock(pos, state, 3);
        session.player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        h.assertTrue(session.player.gameMode.destroyBlock(pos), "The server actually harvests " + state + " with " + tool);
    }

    @GameTest(template = "empty")
    public static void miningUsesRawMaterialsAndFixedToolFamiliesIncludingPlacedBlocks(GameTestHelper h) {
        try (Session session = new Session(h)) {
            BlockPos pos = h.absolutePos(new BlockPos(2, 3, 1));
            ItemStack pickaxe = new ItemStack(MiteTools.get("adamantium_pickaxe"));
            mine(h, session, pos, pickaxe, Blocks.STONE.defaultBlockState());
            h.assertValueEqual(xp(pickaxe), 2_000L, "Stone gives raw material level two, independent of hardness");
            h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3); PlayerPlacedBlocks.set(h.getLevel(), pos, true);
            session.player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
            h.assertTrue(session.player.gameMode.destroyBlock(pos), "The player-placed stone is really removed");
            h.assertValueEqual(xp(pickaxe), 4_000L, "Source ITE does not exclude player-placed blocks from tool experience");
            mine(h, session, pos, pickaxe, Blocks.DIAMOND_ORE.defaultBlockState());
            h.assertValueEqual(xp(pickaxe), 6_000L, "Diamond ore uses stone material two, not its harvest requirement four");
            mine(h, session, pos, pickaxe, Blocks.DIAMOND_BLOCK.defaultBlockState());
            h.assertValueEqual(xp(pickaxe), 10_000L, "Diamond storage uses raw diamond material four");
            mine(h, session, pos, pickaxe, Blocks.LAPIS_BLOCK.defaultBlockState());
            h.assertValueEqual(xp(pickaxe), 12_000L, "Lapis storage has source stone material and earns two points");
            ItemStack axe = new ItemStack(Items.IRON_AXE);
            mine(h, session, pos, axe, Blocks.OAK_LOG.defaultBlockState());
            h.assertValueEqual(xp(axe), 2_000L, "Axe logarithmic harvest has the old fixed two-point reward");
            ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
            mine(h, session, pos, shovel, Blocks.DIRT.defaultBlockState());
            h.assertValueEqual(xp(shovel), 3_000L, "Shovels earn the old fixed three points");
            ItemStack mattock = new ItemStack(MiteTools.get("iron_mattock"));
            mine(h, session, pos, mattock, Blocks.DIRT.defaultBlockState());
            h.assertValueEqual(xp(mattock), 2_000L, "Mattocks override the shovel reward with two points");
            ItemStack scythe = new ItemStack(MiteTools.get("iron_scythe"));
            h.getLevel().setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 3);
            mine(h, session, pos, scythe, Blocks.SHORT_GRASS.defaultBlockState());
            h.assertValueEqual(xp(scythe), 1_000L, "Scythes earn one point on an effective positive-hardness harvest");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void matureCropsGiveEightAndCommittedTillingGivesOne(GameTestHelper h) {
        try (Session session = new Session(h)) {
            BlockPos cropPos = h.absolutePos(new BlockPos(2, 3, 1));
            h.getLevel().setBlock(cropPos.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            ItemStack hoe = new ItemStack(Items.IRON_HOE);
            mine(h, session, cropPos, hoe, ((CropBlock)Blocks.WHEAT).getStateForAge(7));
            h.assertValueEqual(xp(hoe), 8_000L, "The native mature crop harvest trains the hoe by eight");
            mine(h, session, cropPos, hoe, Blocks.WHEAT.defaultBlockState());
            h.assertValueEqual(xp(hoe), 8_000L, "An age-zero zero-hardness crop does not reach the old tool reward path");
            BlockPos soil = cropPos.east(); h.getLevel().setBlock(soil, Blocks.DIRT.defaultBlockState(), 3);
            session.player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            hoe.useOn(new UseOnContext(session.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(soil.getCenter(), Direction.UP, soil, false)));
            h.assertTrue(h.getLevel().getBlockState(soil).is(Blocks.FARMLAND), "The native hoe action commits farmland");
            h.assertValueEqual(xp(hoe), 9_000L, "Successful tilling adds the old single experience point");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedRandomArmorPortionsEachGrantFourAfterTheSourceArmorMultiplier(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ItemStack helmet = new ItemStack(Items.IRON_HELMET), boots = new ItemStack(Items.IRON_BOOTS);
            session.player.setItemSlot(EquipmentSlot.HEAD, helmet); session.player.setItemSlot(EquipmentSlot.FEET, boots);
            ScriptedRandom random = session.random(3, 0, 3, 0, 0, 1);
            Cow attacker = cow(h, session.player, 100F);
            EquipmentDurability.hurtArmorSourceBudget(session.player, session.player.damageSources().mobAttack(attacker), 4F);
            h.assertValueEqual(xp(helmet), 8_000L, "Two separately selected helmet portions each grant four source experience");
            h.assertValueEqual(xp(boots), 4_000L, "The selected boots portion gets its own four experience rather than a split total");
            h.assertTrue(helmet.getDamageValue() == 2 && boots.getDamageValue() == 2, "Observed portions preserve the original aggregate durability wear");
            h.assertTrue(random.finished() && random.bounds.equals(List.of(4, 4, 4, 3, 4, 2)),
                    "Observing XP portions adds no RNG calls and retains the exact source allocation sequence");
            attacker.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeMixedArmorHitTrainsBothProfiledPiecesWithoutDoubleCountingSourceAllocations(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE), boots = new ItemStack(Items.NETHERITE_BOOTS);
            ItemStack unprofiled = new ItemStack(Items.TURTLE_HELMET);
            h.assertTrue(EquipmentDurability.armorProfile(chest) != null && EquipmentDurability.armorProfile(boots) != null
                    && EquipmentDurability.armorProfile(unprofiled) == null, "The turtle helmet forces the real native mixed-armor fallback");
            session.player.setItemSlot(EquipmentSlot.CHEST, chest); session.player.setItemSlot(EquipmentSlot.FEET, boots);
            session.player.setItemSlot(EquipmentSlot.HEAD, unprofiled); session.player.tick();
            session.player.setHealth(session.player.getMaxHealth()); session.player.invulnerableTime = 0;
            Cow attacker = cow(h, session.player, 100F);
            h.assertTrue(session.player.hurt(session.player.damageSources().mobAttack(attacker), 10F), "The mixed equipment receives a real accepted native hit");
            h.assertTrue(chest.getDamageValue() > 0 && boots.getDamageValue() > 0, "Both profiled pieces really passed through the native armor wear event");
            h.assertValueEqual(xp(chest), 4_000L, "The iron chestplate trains once through the native mixed fallback");
            h.assertValueEqual(xp(boots), 4_000L, "The netherite boots also train once even though they have a source profile");
            attacker.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledArmorWearAndEnvironmentalDamageDoNotTrainArmor(GameTestHelper h) {
        try (Session session = new Session(h)) {
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            session.player.setItemSlot(EquipmentSlot.HEAD, helmet);
            Cow attacker = cow(h, session.player, 100F);
            Consumer<ArmorHurtEvent> cancel = event -> { if (event.getEntity() == session.player) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ArmorHurtEvent.class, cancel);
            try {
                session.random(3);
                EquipmentDurability.hurtArmorSourceBudget(session.player, session.player.damageSources().mobAttack(attacker), 4F);
                h.assertTrue(xp(helmet) == 0L && helmet.getDamageValue() == 0, "Canceled native armor wear consumes neither durability nor experience");
            } finally { NeoForge.EVENT_BUS.unregister(cancel); }
            session.random(3);
            EquipmentDurability.hurtArmorSourceBudget(session.player, session.player.damageSources().generic(), 4F);
            h.assertTrue(helmet.getDamageValue() > 0 && xp(helmet) == 0L, "Environmental damage can wear armor but has no responsible entity for ITE experience");
            attacker.discard();
        }
        h.succeed();
    }

    private static final class Session implements AutoCloseable {
        final ControlledPlayer player;
        final EmbeddedChannel channel;
        Session(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "NingMeng123"), false);
            player = new ControlledPlayer(h, cookie);
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            for (int i = 0; i < 60; i++) player.tick();
            player.setPos(h.absolutePos(new BlockPos(1, 3, 1)).getCenter()); player.setNoGravity(true); player.setOnGround(true);
            player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10D);
            player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024D);
            player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(6);
            ((DualFoodData)player.getFoodData()).mite$setNutrition(0);
        }
        ScriptedRandom random(int... values) { ScriptedRandom source = new ScriptedRandom(values); player.controlled = source; return source; }
        @Override public void close() {
            player.controlled = null; player.getInventory().clearContent();
            player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
        }
    }
    private static final class ControlledPlayer extends ServerPlayer {
        private RandomSource controlled;
        ControlledPlayer(GameTestHelper h, CommonListenerCookie cookie) {
            super(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        }
        @Override public RandomSource getRandom() { return controlled == null ? super.getRandom() : controlled; }
    }
    private static final class ScriptedRandom extends LegacyRandomSource {
        private final ArrayDeque<Integer> values = new ArrayDeque<>();
        private final List<Integer> bounds = new ArrayList<>();
        ScriptedRandom(int... choices) { super(0L); for (int value : choices) values.addLast(value); }
        @Override public int nextInt(int bound) {
            bounds.add(bound);
            if (values.isEmpty()) throw new AssertionError("Unexpected source armor RNG bound " + bound);
            int value = values.removeFirst();
            if (value < 0 || value >= bound) throw new AssertionError("Source armor RNG outside bound " + bound);
            return value;
        }
        boolean finished() { return values.isEmpty(); }
    }
}
