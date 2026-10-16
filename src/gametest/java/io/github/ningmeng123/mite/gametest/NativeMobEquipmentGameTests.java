package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.NativeMobEquipment;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent source pools and real native equipment/finalize/save paths. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class NativeMobEquipmentGameTests {
    private NativeMobEquipmentGameTests() {}
    private static <T extends Mob> T create(GameTestHelper helper, EntityType<T> type) {
        T mob = type.create(helper.getLevel()); helper.assertTrue(mob != null, "Native equipment fixture creates " + type);
        mob.setNoAi(true); mob.setNoGravity(true); mob.moveTo(helper.absolutePos(new BlockPos(4, 2, 4)).getCenter()); return mob;
    }
    private static long seedFor(GameTestHelper helper, Predicate<RandomSource> predicate) {
        for (long seed = 0; seed < 100_000; seed++) if (predicate.test(RandomSource.create(seed))) return seed;
        throw new AssertionError("Missing deterministic equipment seed");
    }
    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static void slots(GameTestHelper helper, Mob mob, long seed) throws Exception {
        mob.getRandom().setSeed(seed);
        Class<?> owner = mob instanceof ZombifiedPiglin ? ZombifiedPiglin.class : Zombie.class;
        Method method = owner.getDeclaredMethod("populateDefaultEquipmentSlots", RandomSource.class, DifficultyInstance.class);
        method.setAccessible(true); method.invoke(mob, RandomSource.create(123456789), helper.getLevel().getCurrentDifficultyAt(mob.blockPosition()));
    }
    private static void enchant(GameTestHelper helper, Mob mob) throws Exception {
        Method method = Mob.class.getDeclaredMethod("populateDefaultEquipmentEnchantments", ServerLevelAccessor.class,
                RandomSource.class, DifficultyInstance.class);
        method.setAccessible(true); method.invoke(mob, helper.getLevel(), RandomSource.create(0),
                helper.getLevel().getCurrentDifficultyAt(mob.blockPosition()));
    }
    private static void worn(GameTestHelper helper, ItemStack stack) {
        helper.assertTrue(stack.isDamageableItem() && stack.getDamageValue() >= (int)(stack.getMaxDamage() * (stack.isEnchanted() ? .5F : .7F))
                && stack.getDamageValue() < stack.getMaxDamage(), "Source generated wear is 50/70 through below100 percent: " + id(stack));
    }
    @GameTest(template = "ranged_027")
    public static void nativeZombieWeaponPoolsFollowIndependentDayNineTenAndTwentyEvidence(GameTestHelper helper) {
        List<NativeMobEquipment.Weapon> common = List.of(new NativeMobEquipment.Weapon("minecraft:wooden_shovel", 1),
                new NativeMobEquipment.Weapon("mite:rusted_iron_shovel", 2));
        var normal9 = new java.util.ArrayList<>(common);
        for (String item : List.of("mite:wooden_cudgel", "minecraft:wooden_sword", "mite:rusted_iron_sword", "mite:rusted_iron_dagger"))
            normal9.add(new NativeMobEquipment.Weapon(item, 1));
        helper.assertValueEqual(NativeMobEquipment.zombieWeapons(false, 9), normal9, "Source ordinary early pool and weights");
        var normal10 = new java.util.ArrayList<>(normal9); normal10.add(2, new NativeMobEquipment.Weapon("mite:rusted_iron_hatchet", 1));
        helper.assertValueEqual(NativeMobEquipment.zombieWeapons(false, 10), normal10, "Hatchet opens at day10");
        var villager9 = new java.util.ArrayList<>(common);
        villager9.add(new NativeMobEquipment.Weapon("mite:rusted_iron_shears", 1)); villager9.add(new NativeMobEquipment.Weapon("mite:rusted_iron_scythe", 1));
        helper.assertValueEqual(NativeMobEquipment.zombieWeapons(true, 9), villager9, "Villager-specific tools before day10");
        var villager10 = new java.util.ArrayList<>(villager9); villager10.add(2, new NativeMobEquipment.Weapon("mite:rusted_iron_hatchet", 1));
        villager10.add(new NativeMobEquipment.Weapon("mite:rusted_iron_hoe", 1)); villager10.add(new NativeMobEquipment.Weapon("mite:rusted_iron_mattock", 1));
        helper.assertValueEqual(NativeMobEquipment.zombieWeapons(true, 19), villager10, "Hoe and mattock open at10, pickaxe remains closed at19");
        var villager20 = new java.util.ArrayList<>(villager10); villager20.add(new NativeMobEquipment.Weapon("mite:rusted_iron_pickaxe", 1));
        helper.assertValueEqual(NativeMobEquipment.zombieWeapons(true, 20), villager20, "Pickaxe opens at20");
        helper.assertValueEqual(NativeMobEquipment.day(209999), 9L, "Last tick of source day9");
        helper.assertValueEqual(NativeMobEquipment.day(210000), 10L, "Source day10 uses +6000 offset"); helper.succeed();
    }
    @GameTest(template = "ranged_027")
    public static void everyWeightedZombieWeaponIsReachableThroughTheNativeOwnerRandomPath(GameTestHelper helper) throws Exception {
        try (ProgressSnapshot progress = ProgressSnapshot.earlySource(helper.getLevel())) {
            long time = helper.getLevel().getDayTime();
            try {
                helper.getLevel().setDayTime(450000);
                String[][] pools = {{"minecraft:wooden_shovel", "mite:rusted_iron_shovel", "mite:rusted_iron_shovel", "mite:rusted_iron_hatchet",
                        "mite:wooden_cudgel", "minecraft:wooden_sword", "mite:rusted_iron_sword", "mite:rusted_iron_dagger"},
                        {"minecraft:wooden_shovel", "mite:rusted_iron_shovel", "mite:rusted_iron_shovel", "mite:rusted_iron_hatchet",
                        "mite:rusted_iron_shears", "mite:rusted_iron_scythe", "mite:rusted_iron_hoe", "mite:rusted_iron_mattock", "mite:rusted_iron_pickaxe"}};
                for (int variant = 0; variant < 2; variant++) for (int weightedIndex = 0; weightedIndex < pools[variant].length; weightedIndex++) {
                    int roll = weightedIndex, size = pools[variant].length; float chance = variant == 0 ? .05F : .2F;
                    long seed = seedFor(helper, random -> random.nextFloat() < chance && random.nextInt(size) == roll);
                    Mob mob = variant == 0 ? create(helper, EntityType.ZOMBIE) : create(helper, EntityType.ZOMBIE_VILLAGER); slots(helper, mob, seed);
                    helper.assertValueEqual(id(mob.getMainHandItem()), pools[variant][weightedIndex], "Owner RNG draws every source weighted entry");
                    worn(helper, mob.getMainHandItem());
                }
            } finally { helper.getLevel().setDayTime(time); }
            helper.succeed();
        }
    }
    @GameTest(template = "ranged_027")
    public static void villagerTwentyPercentAndOrdinaryFivePercentRollsDoNotEraseExistingItems(GameTestHelper helper) throws Exception {
        try (ProgressSnapshot progress = ProgressSnapshot.earlySource(helper.getLevel())) {
            long between = seedFor(helper, random -> { float value = random.nextFloat(); return value >= .05F && value < .2F; });
            Zombie normal = create(helper, EntityType.ZOMBIE), villager = create(helper, EntityType.ZOMBIE_VILLAGER);
            slots(helper, normal, between); slots(helper, villager, between);
            helper.assertTrue(normal.getMainHandItem().isEmpty() && !villager.getMainHandItem().isEmpty(), "Source .05 vs .20 chances do not depend on modern difficulty");
            ItemStack pickedUp = new ItemStack(Items.NETHERITE_SWORD); normal.setItemSlot(EquipmentSlot.MAINHAND, pickedUp);
            long rejected = seedFor(helper, random -> random.nextFloat() >= .2F); slots(helper, normal, rejected);
            helper.assertTrue(normal.getMainHandItem() == pickedUp, "Rejected source weapon roll preserves the existing held stack"); helper.succeed();
        }
    }
    @GameTest(template = "ranged_027")
    public static void nativePigZombieDrawsTwoGoldSwordsBattleAxeAndPickaxePoorWornAndUnenchanted(GameTestHelper helper) throws Exception {
        try (ProgressSnapshot progress = ProgressSnapshot.earlySource(helper.getLevel())) {
            String[] expected = {"minecraft:golden_sword", "minecraft:golden_sword", "mite:golden_battle_axe", "minecraft:golden_pickaxe"};
            for (int index = 0; index < 4; index++) {
                int roll = index; long seed = seedFor(helper, random -> random.nextInt(4) == roll);
                ZombifiedPiglin pig = create(helper, EntityType.ZOMBIFIED_PIGLIN); slots(helper, pig, seed);
                ItemStack stack = pig.getMainHandItem(); helper.assertValueEqual(id(stack), expected[index], "PigZombie four-entry source pool");
                helper.assertValueEqual(EquipmentDurability.quality(stack), Quality.POOR, "PigZombie explicit poor quality");
                helper.assertTrue(!stack.isEnchanted(), "PigZombie source randomizeForMob(false)"); worn(helper, stack);
                for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
                    helper.assertTrue(pig.getItemBySlot(slot).isEmpty(), "Source pig override does not generate armor");
            }
            helper.succeed();
        }
    }
    @GameTest(template = "ranged_027")
    public static void nativeEquipmentEnchantmentsCannotApplyASecondRollOrWearPass(GameTestHelper helper) throws Exception {
        try (ProgressSnapshot progress = ProgressSnapshot.earlySource(helper.getLevel())) {
            long seed = seedFor(helper, random -> random.nextFloat() < .05F);
            for (var type : List.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN)) {
                Mob mob = create(helper, type); slots(helper, mob, seed);
                List<ItemStack> saved = new java.util.ArrayList<>(); for (EquipmentSlot slot : EquipmentSlot.values()) saved.add(mob.getItemBySlot(slot).copy());
                for (int repeat = 0; repeat < 8; repeat++) enchant(helper, mob);
                for (EquipmentSlot slot : EquipmentSlot.values()) helper.assertTrue(ItemStack.matches(mob.getItemBySlot(slot), saved.get(slot.ordinal())),
                        "Native follow-up enchantment leaves all source components and wear intact " + type + " " + slot);
            }
            helper.succeed();
        }
    }
    @GameTest(template = "ranged_027")
    public static void sourceGearAndAllNativeSpawnReasonsSurviveFinalizeAndNbtReload(GameTestHelper helper) {
        try (ProgressSnapshot progress = ProgressSnapshot.earlySource(helper.getLevel())) {
            long time = helper.getLevel().getDayTime();
            try {
                helper.getLevel().setDayTime(450000);
                // Full og.a finalization consumes the owner Gaussian follow-range draw before tw's weapon draw.
                // Direct populateDefaultEquipmentSlots tests above intentionally start at the first float instead.
                long seed = seedFor(helper, random -> { random.nextGaussian(); return random.nextFloat() < .05F; });
                for (var type : List.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN))
                    for (MobSpawnType reason : List.of(MobSpawnType.NATURAL, MobSpawnType.SPAWNER, MobSpawnType.SPAWN_EGG, MobSpawnType.REINFORCEMENT)) {
                        Mob mob = create(helper, type); mob.getRandom().setSeed(seed);
                        EventHooks.finalizeMobSpawn(mob, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(mob.blockPosition()), reason, new Zombie.ZombieGroupData(false, true));
                        helper.assertValueEqual(mob.getSpawnType(), reason, "Native finalize keeps source spawn reason");
                        helper.assertTrue(!mob.getMainHandItem().isEmpty(), "Full finalize source equipment fixture reaches its weapon roll: " + type);
                        helper.assertTrue(!mob.getMainHandItem().is(Items.IRON_SWORD) && !mob.getMainHandItem().is(Items.IRON_SHOVEL),
                                "Full finalize does not generate native iron equipment: " + type);
                        CompoundTag tag = new CompoundTag(); mob.saveWithoutId(tag); Mob restored = create(helper, type); restored.load(tag);
                        helper.assertValueEqual(restored.getSpawnType(), reason, "Saved source gear retains native spawn reason");
                        for (EquipmentSlot slot : EquipmentSlot.values()) helper.assertTrue(ItemStack.matches(restored.getItemBySlot(slot), mob.getItemBySlot(slot)),
                                "Source gear quality/enchantment/wear survive NBT " + slot);
                    }
            } finally { helper.getLevel().setDayTime(time); }
            helper.succeed();
        }
    }
    @GameTest(template = "ranged_027")
    public static void modernHuskDrownedPiglinAndSourceSkeletonKeepTheirOwnEquipmentPaths(GameTestHelper helper) throws Exception {
        try (ProgressSnapshot progress = ProgressSnapshot.earlySource(helper.getLevel())) {
            for (var type : List.of(EntityType.HUSK, EntityType.DROWNED, EntityType.PIGLIN, EntityType.SKELETON, EntityType.WITHER_SKELETON)) {
                Mob mob = create(helper, type); ItemStack sentinel = new ItemStack(Items.NETHERITE_SWORD); mob.setItemSlot(EquipmentSlot.MAINHAND, sentinel);
                helper.assertTrue(!NativeMobEquipment.handles(type) && !NativeMobEquipment.populate(mob), "Unrelated equipment identity is excluded " + type);
                helper.assertTrue(mob.getMainHandItem() == sentinel, "Exclusion preserves existing stack " + type);
            }
            Zombie husk = create(helper, EntityType.HUSK);
            long seed = seedFor(helper, random -> { random.nextFloat(); return random.nextFloat() < .01F && random.nextInt(3) == 0; });
            var method = Zombie.class.getDeclaredMethod("populateDefaultEquipmentSlots", RandomSource.class, DifficultyInstance.class); method.setAccessible(true);
            // Zero local multiplier suppresses unrelated armor draws in this positive control; the native weapon
            // threshold still reads the real world difficulty (the selected draw is below both .01 and .05).
            method.invoke(husk, RandomSource.create(seed), new DifficultyInstance(Difficulty.PEACEFUL, 0, 0, 0F));
            helper.assertTrue(husk.getMainHandItem().is(Items.IRON_SWORD), "Positive control: modern Husk native equipment is still reachable"); helper.succeed();
        }
    }
    @GameTest(template = "ranged_027")
    public static void sourceZombieSubtypeConsumesBothFivePercentDrawsAndReusesItsPackData(GameTestHelper helper) {
        try (ProgressSnapshot progress = ProgressSnapshot.earlySource(helper.getLevel())) {
            for (boolean baby : List.of(false, true)) for (boolean villager : List.of(false, true)) {
                long seed = seedFor(helper, random -> (random.nextFloat() < .05F) == baby && (random.nextFloat() < .05F) == villager);
                RandomSource independent = RandomSource.create(seed); independent.nextFloat(); independent.nextFloat(); float next = independent.nextFloat();
                Zombie original = create(helper, EntityType.ZOMBIE); helper.getLevel().getRandom().setSeed(seed);
                var prepared = R196MobSpawning.prepareSourceFinalization(original, helper.getLevel(), null);
                helper.assertTrue(prepared.group() instanceof R196MobSpawning.SourceZombieGroupData, "Source ty maps to an explicit reusable pack");
                var group = (R196MobSpawning.SourceZombieGroupData)prepared.group();
                helper.assertTrue(group.sourceBabyDraw == baby && group.sourceVillager == villager && !group.isBaby,
                        "Both source .05 flags are drawn, while modern baby and jockey trigger remains disabled");
                helper.assertTrue(prepared.mob().getType() == (villager ? EntityType.ZOMBIE_VILLAGER : EntityType.ZOMBIE), "Only the second source draw selects villager identity");
                var sibling = R196MobSpawning.prepareSourceFinalization(create(helper, EntityType.ZOMBIE), helper.getLevel(), group);
                helper.assertTrue(sibling.group() == group && sibling.mob().getType() == prepared.mob().getType(), "Source pack members reuse the same villager flag");
                helper.assertValueEqual(helper.getLevel().getRandom().nextFloat(), next, "Group reuse consumes no additional subtype RNG");
            }
            long positive = seedFor(helper, random -> { random.nextFloat(); return random.nextFloat() < .05F; });
            helper.getLevel().getRandom().setSeed(positive);
            var pig = R196MobSpawning.prepareSourceFinalization(create(helper, EntityType.ZOMBIFIED_PIGLIN), helper.getLevel(), null);
            helper.assertTrue(pig.mob().getType() == EntityType.ZOMBIFIED_PIGLIN, "tn preserves both inherited group draws but explicitly stays nonvillager"); helper.succeed();
        }
    }
    @GameTest(template = "ranged_027")
    public static void sourceFarmerSubtypeKeepsItsTwentyPercentEquipmentAndSavedProfession(GameTestHelper helper) throws Exception {
        try (ProgressSnapshot progress = ProgressSnapshot.earlySource(helper.getLevel())) {
            var prepared = R196MobSpawning.prepareSourceFinalization(create(helper, EntityType.ZOMBIE), helper.getLevel(),
                    new R196MobSpawning.SourceZombieGroupData(true, true));
            helper.assertTrue(prepared.mob() instanceof ZombieVillager, "Qualified ordinary identity becomes modern ZombieVillager");
            ZombieVillager villager = (ZombieVillager)prepared.mob();
            helper.assertValueEqual(villager.getVillagerData().getProfession(), VillagerProfession.FARMER, "tw.setVillager(true,0) means source farmer");
            long seed = seedFor(helper, random -> { random.nextGaussian(); float roll = random.nextFloat(); return roll >= .05F && roll < .2F; });
            villager.getRandom().setSeed(seed);
            EventHooks.finalizeMobSpawn(villager, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(villager.blockPosition()), MobSpawnType.NATURAL, prepared.group());
            helper.assertTrue(!villager.isBaby() && !villager.isPassenger() && !villager.getMainHandItem().isEmpty(),
                    "Converted source subtype uses .20 equipment chance and stays adult");
            CompoundTag tag = new CompoundTag(); villager.saveWithoutId(tag); ZombieVillager restored = create(helper, EntityType.ZOMBIE_VILLAGER); restored.load(tag);
            helper.assertValueEqual(restored.getVillagerData().getProfession(), VillagerProfession.FARMER, "Source profession survives native NBT");
            helper.assertTrue(ItemStack.matches(restored.getMainHandItem(), villager.getMainHandItem()) && restored.getSpawnType() == MobSpawnType.NATURAL,
                    "Subtype equipment and source natural reason persist together"); helper.succeed();
        }
    }
    @GameTest(template = "ranged_027")
    @SuppressWarnings("removal")
    public static void realDefaultZombieSpawnerCanProduceTheSourceFarmerSubtype(GameTestHelper helper) {
        try (ProgressSnapshot progress = ProgressSnapshot.earlySource(helper.getLevel())) {
            var level = helper.getLevel(); long time = level.getDayTime(); BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
            ServerPlayer player = helper.makeMockServerPlayerInLevel(); player.setPos(pos.getX() + 3, pos.getY(), pos.getZ());
            var previous = new java.util.LinkedHashMap<BlockPos, BlockState>(); List<Mob> added = new java.util.ArrayList<>();
            AABB bounds = new AABB(pos).inflate(2, 4, 2); var before = level.getEntitiesOfClass(Mob.class, bounds);
            try {
                level.setDayTime(18000); level.updateSkyBrightness();
                for (BlockPos point : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 3, 1))) {
                    previous.put(point.immutable(), level.getBlockState(point));
                    level.setBlock(point, (point.getY() == pos.getY() - 1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
                }
                CompoundTag entity = new CompoundTag(); entity.putString("id", "minecraft:zombie");
                CompoundTag tag = new CompoundTag(); tag.putShort("Delay", (short)0); tag.putShort("SpawnCount", (short)1); tag.putShort("SpawnRange", (short)0);
                tag.put("SpawnData", SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, new SpawnData(entity, java.util.Optional.empty(), java.util.Optional.empty())).getOrThrow());
                BaseSpawner spawner = new BaseSpawner() { @Override public void broadcastEvent(Level world, BlockPos point, int event) {} };
                spawner.load(level, pos, tag);
                long seed = seedFor(helper, random -> {
                    random.nextDouble(); random.nextDouble(); int height = random.nextInt(3); random.nextDouble(); random.nextDouble();
                    random.nextFloat(); random.nextFloat(); return random.nextFloat() < .05F && height == 1;
                });
                level.getRandom().setSeed(seed); spawner.serverTick(level, pos);
                for (Mob mob : level.getEntitiesOfClass(Mob.class, bounds)) if (!before.contains(mob)) added.add(mob);
                helper.assertValueEqual(added.size(), 1, "Default id-only SpawnData finalizes and adds exactly one real source entity");
                helper.assertTrue(added.getFirst() instanceof ZombieVillager && added.getFirst().getSpawnType() == MobSpawnType.SPAWNER,
                        "Actual spawner add local points to the finalized subtype");
                helper.assertValueEqual(((ZombieVillager)added.getFirst()).getVillagerData().getProfession(), VillagerProfession.FARMER, "Source spawner farmer identity");
            } finally {
                added.forEach(Mob::discard); previous.forEach((point, block) -> level.setBlock(point, block, 2));
                level.getServer().getPlayerList().remove(player); level.setDayTime(time); level.updateSkyBrightness();
            }
            helper.succeed();
        }
    }
}
