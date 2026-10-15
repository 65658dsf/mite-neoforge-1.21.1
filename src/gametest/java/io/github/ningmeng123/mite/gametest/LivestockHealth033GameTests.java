package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobCombat;
import io.github.ningmeng123.mite.entity.SourceVanillaMobLoot;
import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.Livestock032View;
import io.github.ningmeng123.mite.entity.animal.LivestockGrazingGoal032;
import io.github.ningmeng123.mite.entity.animal.LivestockHealth033;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.CowFoodState;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent EntityLivestock/isWell and bge/bgf/bhi/bhk sickness cases. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class LivestockHealth033GameTests {
    private static final BlockPos ORIGIN = new BlockPos(1, 10, 1);
    private static final List<EntityType<? extends Animal>> SKIN_TYPES =
            List.of(EntityType.CHICKEN, EntityType.COW, EntityType.PIG, EntityType.SHEEP);

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type) {
        T entity = type.create(h.getLevel());
        BlockPos position = h.absolutePos(ORIGIN);
        entity.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5, 0, 0);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        h.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void needs(Mob animal, float food, float water, float freedom) {
        Livestock032.needs(animal, new LivestockRules032.Needs(food, water, freedom));
    }

    private static ResourceLocation skin(String species) {
        return ResourceLocation.fromNamespaceAndPath("mite", "textures/entity/livestock/" + species + "_sick.png");
    }

    private static long seed(Predicate<Random> predicate) {
        for (long value = 0; value < 100000; value++) if (predicate.test(new Random(value))) return value;
        throw new AssertionError("No independent source random fixture");
    }

    @GameTest(template = "empty")
    public static void fourSourceRenderersUseTheirOwnSickBodySkin(GameTestHelper h) {
        String[] names = {"chicken", "cow", "pig", "sheep"};
        for (int i = 0; i < SKIN_TYPES.size(); i++) {
            var animal = mob(h, SKIN_TYPES.get(i));
            needs(animal, .1F, .8F, .8F);
            h.assertValueEqual(LivestockHealth033.sickBodyTexture(animal), skin(names[i]),
                    "Each original renderer switches to its own body skin");
            animal.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void foodThresholdIsStrictlyBelowOneQuarter(GameTestHelper h) {
        var cow = mob(h, EntityType.COW);
        needs(cow, Math.nextDown(.25F), 1, 1);
        h.assertValueEqual(LivestockHealth033.sickBodyTexture(cow), skin("cow"), "The last float below 25% food is unwell");
        needs(cow, .25F, 1, 1);
        h.assertTrue(LivestockHealth033.sickBodyTexture(cow) == null, "Exactly 25% food is well");
        cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void waterDeficiencyIsIndependentOfFullFood(GameTestHelper h) {
        var pig = mob(h, EntityType.PIG);
        needs(pig, 1, Math.nextDown(.25F), 1);
        h.assertValueEqual(LivestockHealth033.sickBodyTexture(pig), skin("pig"), "Full food cannot hide water deficiency");
        needs(pig, 1, .25F, 1);
        h.assertTrue(LivestockHealth033.sickBodyTexture(pig) == null, "Exactly 25% water restores the normal body");
        pig.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void freedomDeficiencyIsIndependentOfFoodAndWater(GameTestHelper h) {
        var chicken = mob(h, EntityType.CHICKEN);
        needs(chicken, 1, 1, Math.nextDown(.25F));
        h.assertValueEqual(LivestockHealth033.sickBodyTexture(chicken), skin("chicken"), "Freedom alone changes the body skin");
        needs(chicken, 1, 1, .25F);
        h.assertTrue(LivestockHealth033.sickBodyTexture(chicken) == null, "Exactly 25% freedom is not sick");
        chicken.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void allThreeQuarterBoundariesRestoreEverySourceBody(GameTestHelper h) {
        for (var type : SKIN_TYPES) {
            var animal = mob(h, type);
            needs(animal, .25F, .25F, .25F);
            h.assertTrue(LivestockHealth033.sickBodyTexture(animal) == null && SourceVanillaMobLoot.well(animal),
                    "Rendering and meat health predicate both include the exact boundary");
            animal.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualWheatMealCuresFoodDeficiencyWithoutDiseaseTimer(GameTestHelper h) {
        var cow = mob(h, EntityType.COW);
        needs(cow, .1F, .8F, .8F);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(Items.WHEAT));
        var result = cow.mobInteract(player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.consumesAction() && player.getMainHandItem().isEmpty(), "A real accepted meal spends one wheat");
        h.assertTrue(Livestock032.needs(cow).food() == .6F && LivestockHealth033.sickBodyTexture(cow) == null,
                "Source +0.5 food immediately restores wellness without a separate sickness timer");
        cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualWaterBowlCuresThirstWhileFoodStaysUnchanged(GameTestHelper h) {
        var pig = mob(h, EntityType.PIG);
        needs(pig, .8F, .1F, .8F);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(MiteFoods.WATER_BOWL.get()));
        var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, pig));
        h.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction(), "Registered water interaction accepts the bowl");
        h.assertTrue(Livestock032.needs(pig).water() == .35F && Livestock032.needs(pig).food() == .8F
                        && LivestockHealth033.sickBodyTexture(pig) == null,
                "Source one-volume +0.25 water immediately restores health status");
        h.assertTrue(player.getMainHandItem().is(Items.BOWL), "Drinking returns the same empty vessel");
        pig.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualMissingFoodUpdateCanCrossIntoSickness(GameTestHelper h) {
        var cow = mob(h, EntityType.COW);
        needs(cow, .254F, .8F, .8F);
        h.assertFalse(Livestock032.isNearFood(cow), "Fixture has neither short grass nor dandelion");
        Livestock032.updateWellness(cow);
        h.assertTrue(Math.abs(Livestock032.needs(cow).food() - .249F) < .000001F
                        && LivestockHealth033.sickBodyTexture(cow).equals(skin("cow")),
                "Literal -0.005 missing-food update crosses the 25% threshold");
        cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualNearbyWaterUpdateRestoresSickBody(GameTestHelper h) {
        var pig = mob(h, EntityType.PIG);
        needs(pig, .8F, .2F, .8F);
        h.getLevel().setBlock(pig.blockPosition().east(), Blocks.SNOW_BLOCK.defaultBlockState(), 3);
        h.assertTrue(Livestock032.isNearWater(pig), "Source snow counts as accessible water");
        Livestock032.updateWellness(pig);
        h.assertTrue(Math.abs(Livestock032.needs(pig).water() - .3F) < .000001F
                        && LivestockHealth033.sickBodyTexture(pig) == null,
                "Literal +0.1 accessible-water update restores wellness");
        pig.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void crowdingCausesSicknessAndOutdoorSpaceRecoversIt(GameTestHelper h) {
        var pig = mob(h, EntityType.PIG);
        var cow = mob(h, EntityType.COW);
        var chicken = mob(h, EntityType.CHICKEN);
        needs(pig, .8F, .8F, .254F);
        h.assertTrue(Livestock032.isCrowded(pig), "Three mobs including the owner exceed the source limit");
        Livestock032.updateWellness(pig);
        h.assertTrue(Math.abs(Livestock032.needs(pig).freedom() - .249F) < .000001F
                        && LivestockHealth033.sickBodyTexture(pig).equals(skin("pig")),
                "Literal -0.005 crowding update changes the body");
        cow.discard(); chicken.discard();
        h.assertFalse(Livestock032.isCrowded(pig), "Open sky and one remaining animal provide source freedom");
        Livestock032.updateWellness(pig);
        h.assertTrue(Math.abs(Livestock032.needs(pig).freedom() - .349F) < .000001F
                        && LivestockHealth033.sickBodyTexture(pig) == null,
                "Literal +0.1 freedom update immediately restores the normal body");
        pig.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void redAndBrownMooshroomsKeepTheirModernSkinsWhenUnwell(GameTestHelper h) {
        var cow = mob(h, EntityType.MOOSHROOM);
        needs(cow, .1F, .1F, .1F);
        for (var variant : MushroomCow.MushroomType.values()) {
            cow.setVariant(variant);
            h.assertTrue(!SourceVanillaMobLoot.well(cow) && LivestockHealth033.sickBodyTexture(cow) == null,
                    "Source bhf keeps the mushroom skin while the same livestock wellness rule still applies");
        }
        cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void wholeEntityReloadAndRealJoinRestoreSynchronizedBodyState(GameTestHelper h) {
        var original = mob(h, EntityType.PIG);
        needs(original, .32F, .12F, .73F);
        var state = Livestock032.state(original);
        state.putInt("production_counter", 17);
        Livestock032.save(original, state);
        var saved = new CompoundTag();
        original.saveWithoutId(saved);
        var restored = EntityType.PIG.create(h.getLevel());
        restored.load(saved);
        restored.setUUID(UUID.randomUUID());
        ((Livestock032View) restored).mite$setLivestockNeeds(new LivestockRules032.Needs(1, 1, 1));
        h.assertTrue(h.getLevel().addFreshEntity(restored), "Whole NBT-loaded animal enters through the real server join event");
        h.assertValueEqual(((Livestock032View) restored).mite$livestockNeeds(), new LivestockRules032.Needs(.32F, .12F, .73F),
                "Join resynchronizes all three saved source resources instead of keeping constructor defaults");
        h.assertValueEqual(LivestockHealth033.sickBodyTexture(restored), skin("pig"), "Loaded thirst deficiency is immediately visible");
        h.assertTrue(Livestock032.state(restored).getInt("production_counter") == 17
                        && restored.getPersistentData().getAllKeys().stream().noneMatch(key -> key.contains("033") || key.contains("disease") || key.contains("sick")),
                "No second disease NBT state replaces existing resources or pending production");
        original.discard(); restored.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualMetadataPacketRoundTripUpdatesObserverBodyAndRecovery(GameTestHelper h) {
        var source = mob(h, EntityType.COW);
        var observer = EntityType.COW.create(h.getLevel());
        needs(observer, 1, 1, 1);
        source.getEntityData().packDirty();
        needs(source, .6F, .1F, .8F);
        transferMetadata(h, source, observer);
        h.assertValueEqual(((Livestock032View) observer).mite$livestockNeeds(), new LivestockRules032.Needs(.6F, .1F, .8F),
                "Actual STREAM_CODEC transports the independent food, water and freedom floats");
        h.assertTrue(Livestock032.needs(observer).well() && skin("cow").equals(LivestockHealth033.sickBodyTexture(observer)),
                "Body selection uses received watchers rather than the observer's unrelated server persistent data");
        needs(source, .25F, .25F, .25F);
        transferMetadata(h, source, observer);
        h.assertTrue(LivestockHealth033.sickBodyTexture(observer) == null, "A second received metadata packet immediately shows exact-boundary recovery");
        source.discard(); observer.discard(); h.succeed();
    }

    private static void transferMetadata(GameTestHelper h, Mob source, Mob observer) {
        var dirty = source.getEntityData().packDirty();
        h.assertTrue(dirty != null && dirty.size() == 3
                        && dirty.stream().allMatch(value -> value.serializer() == EntityDataSerializers.FLOAT),
                "Wellness updates require exactly the existing three float watcher values");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer, new ClientboundSetEntityDataPacket(source.getId(), dirty));
            var received = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
            h.assertTrue(received.id() == source.getId() && !buffer.isReadable(), "Entity identifier and complete packet payload round-trip");
            observer.getEntityData().assignValues(received.packedItems());
        } finally {
            buffer.release();
        }
    }

    @GameTest(template = "empty")
    public static void everyIndependentDeficiencyBlocksNewLoveButRecoveryAllowsIt(GameTestHelper h) {
        var sheep = mob(h, EntityType.SHEEP);
        float[][] cases = {{.1F, 1, 1}, {1, .1F, 1}, {1, 1, .1F}};
        for (var sample : cases) {
            sheep.resetLove();
            needs(sheep, sample[0], sample[1], sample[2]);
            sheep.setInLove(null);
            h.assertFalse(sheep.isInLove(), "Source setInLove rejects each independent unwell resource");
        }
        needs(sheep, .25F, .25F, .25F);
        sheep.setInLove(null);
        h.assertTrue(sheep.isInLove() && sheep.getInLoveTime() == 600, "Recovery permits source 600-tick love without disease medicine");
        sheep.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void sickCowStopsNewProductionButExistingMilkCanBeCollected(GameTestHelper h) {
        var cow = mob(h, EntityType.COW);
        CowFoodState.set(cow, new CowFoodState(1, 20, .1F, .8F, .8F));
        var state = Livestock032.state(cow);
        state.putInt("production_counter", 7);
        Livestock032.save(cow, state);
        cow.tickCount = 100;
        cow.getRandom().setSeed(seed(random -> random.nextInt(10) > 0));
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(cow));
        h.assertTrue(CowFoodState.get(cow).milk() == 27 && Livestock032.state(cow).getInt("production_counter") == 0,
                "Unwell cow gains no fresh production, while seven previously accumulated units are not deleted");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(Items.BOWL));
        var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, cow));
        h.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction()
                        && player.getMainHandItem().is(MiteFoods.MILK_BOWL.get()) && CowFoodState.get(cow).milk() == 2,
                "Sickness does not prohibit collecting a 25-unit bowl from existing inventory");
        cow.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void sickChickenMayStillSpendPreviouslyEarnedEggProduction(GameTestHelper h) {
        var chicken = mob(h, EntityType.CHICKEN);
        needs(chicken, .1F, .8F, .8F);
        var state = Livestock032.state(chicken);
        state.putInt("production_counter", 200);
        Livestock032.save(chicken, state);
        var drops = new ArrayList<ItemEntity>();
        chicken.captureDrops(drops);
        chicken.getRandom().setSeed(seed(random -> random.nextInt(500) != 0 && random.nextInt(20) == 0));
        Livestock032.produceGoods(chicken);
        h.assertTrue(drops.stream().filter(drop -> drop.getItem().is(Items.EGG)).mapToInt(drop -> drop.getItem().getCount()).sum() == 1
                        && Livestock032.state(chicken).getInt("production_counter") == 0
                        && skin("chicken").equals(LivestockHealth033.sickBodyTexture(chicken)),
                "Source produceGoods remains outside the new-production wellness gate; no blanket sickness egg ban");
        chicken.captureDrops(null); chicken.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void sickSheepCanBeShearedAndActualGrassBiteRestoresItsBody(GameTestHelper h) {
        var sheep = mob(h, EntityType.SHEEP);
        needs(sheep, .1F, .8F, .8F);
        h.assertTrue(sheep.readyForShearing(), "Source wellness is not an invented shearing prohibition");
        sheep.shear(SoundSource.PLAYERS);
        h.assertTrue(sheep.isSheared(), "Actual shear works while the sheep is sick");
        h.getLevel().setBlock(sheep.blockPosition().below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        var goal = new LivestockGrazingGoal032(sheep);
        goal.start();
        for (int i = 0; i < 36; i++) goal.tick();
        h.assertTrue(!sheep.isSheared() && Livestock032.needs(sheep).food() == .6F
                        && LivestockHealth033.sickBodyTexture(sheep) == null
                        && h.getLevel().getBlockState(sheep.blockPosition().below()).is(Blocks.DIRT),
                "Real source bite restores wool and half-food, thereby curing the body without blocking grazing");
        sheep.discard(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void sicknessDoesNotInventSpeedPenaltiesOrDisableSourcePassiveHealing(GameTestHelper h) {
        var cow = mob(h, EntityType.COW);
        double speed = cow.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float maximum = cow.getMaxHealth();
        cow.setHealth(maximum * .5F);
        needs(cow, .1F, .1F, .1F);
        cow.tickCount = -cow.getId() * 47 + 1000;
        h.assertTrue(MobCombat.ticksWithOffset(cow) == 1000, "Independent source entity-offset healing boundary");
        cow.aiStep();
        h.assertTrue(Math.abs(cow.getHealth() - maximum * .6F) < .00001F,
                "R196 of healsWithTime does not test livestock wellness before restoring 10% maximum health");
        h.assertTrue(cow.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed && cow.getActiveEffects().isEmpty(),
                "Sickness adds neither a speed modifier nor a disease potion effect");
        cow.discard(); h.succeed();
    }
}
