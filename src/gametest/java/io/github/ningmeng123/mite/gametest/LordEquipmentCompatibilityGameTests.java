package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real installed-addon joins isolate equipment rules from independent natural-spawn date gates. @author NingMeng123 */
@GameTestHolder("mite_lord_equipment_compat")
@PrefixGameTestTemplate(false)
public final class LordEquipmentCompatibilityGameTests {
    private static final List<String> LORDS = List.of("mite:bone_lord", "mite:ancient_bone_lord",
            "modern_ite:zombie_lord", "modern_ite:zombie_elite_lord", "modern_ite:skeleton_elite_lord",
            "modern_ite:zombie_pigman_lord", "modern_ite:witch_lord", "modern_ite:zombie_boss",
            "modern_ite:undying_zombie_emperor", "modern_ite:annihilation_skeleton");
    private static final List<EquipmentSlot> VISIBLE = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private LordEquipmentCompatibilityGameTests() {}

    @GameTest(template = "empty")
    public static void allTenInstalledLordIdentitiesKeepTheirOwnRealSpawnKitsOnDaysOneAndSixteen(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            for (long day : new long[]{1L, 16L}) {
                site.date(day);
                int tested = 0;
                for (String id : LORDS) {
                    Mob lord = site.finalized(id);
                    helper.assertTrue(BloodMoonEquipment.earlyEquipmentExempt(lord), "The actual registered lord is tagged: " + id);
                    site.join(lord);
                    helper.assertFalse(lord.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY)
                            .getBoolean("early_equipment_locked"), "The first16-day rule never records a birth lock for " + id);
                    helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(lord), "The lord remains available to its own equipment callbacks: " + id);
                    if (!id.equals("modern_ite:witch_lord")) {
                        helper.assertFalse(lord.getMainHandItem().isEmpty(), "The lord's real native/addon initializer supplies a weapon on day " + day + ": " + id);
                        for (EquipmentSlot slot : ARMOR) helper.assertFalse(lord.getItemBySlot(slot).isEmpty(),
                                "The lord keeps its own full armor on day " + day + ": " + id + "/" + slot);
                    } else helper.assertTrue(lord.getMainHandItem().isEmpty(), "The unarmed witch lord retains its own spellcaster role");
                    tested++; lord.discard();
                }
                helper.assertValueEqual(tested, 10, "Both native lords and all eight actual Modern ITE lord/Boss entities were exercised");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realNbtLoadedLordLocksKeepVisibleGearAndAddonHiddenWeaponCaches(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            site.date(1L);
            for (String id : LORDS) {
                Mob original = site.finalized(id); site.join(original);
                ItemStack custom = new ItemStack(Items.IRON_SWORD);
                custom.set(DataComponents.CUSTOM_NAME, Component.literal("Retained lord equipment"));
                original.setItemSlot(EquipmentSlot.MAINHAND, custom);
                var oldLock = original.getPersistentData().getCompound(BloodMoonEquipment.DATA_KEY);
                oldLock.putBoolean("early_equipment_locked", true);
                original.getPersistentData().put(BloodMoonEquipment.DATA_KEY, oldLock);
                // This is the addon's real serialized hidden-weapon key, cleared by its early-lock callback.
                original.getPersistentData().put("modern_ite:stocked_weapon", custom.save(site.level.registryAccess()));
                CompoundTag saved = new CompoundTag(); original.saveWithoutId(saved); original.discard();
                Mob restored = site.create(id); restored.load(saved);
                List<ItemStack> before = equipment(restored);
                CompoundTag hidden = restored.getPersistentData().getCompound("modern_ite:stocked_weapon").copy();
                helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(restored), "A deserialized old lock does not suppress the lord before its join: " + id);
                var loadedJoin = new EntityJoinLevelEvent(restored, site.level, true);
                NeoForge.EVENT_BUS.post(loadedJoin);
                helper.assertFalse(loadedJoin.isCanceled(), "The full installed-addon loaded-entity event accepts the lord: " + id);
                unchanged(helper, restored, before);
                helper.assertTrue(hidden.equals(restored.getPersistentData().getCompound("modern_ite:stocked_weapon")),
                        "Old lord locks cannot clear the addon's actual hidden-weapon cache: " + id);
                helper.assertFalse(BloodMoonEquipment.spawnEquipmentLocked(restored), "Loaded addon lords retain the exemption after every listener");
                restored.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void installedAddonOrdinaryEquipmentLocksThroughDaySixteenAndUnlocksAtSeventeen(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            for (long day : new long[]{16L, 17L}) {
                site.date(day);
                for (String id : List.of("minecraft:zombie", "modern_ite:zombie_iron_bucket")) {
                    Mob ordinary = site.finalized(id);
                    helper.assertFalse(BloodMoonEquipment.earlyEquipmentExempt(ordinary), "Ordinary native and addon zombies never inherit the lord exemption");
                    if (id.equals("minecraft:zombie")) for (EquipmentSlot slot : VISIBLE)
                        ordinary.setItemSlot(slot, new ItemStack(Items.STICK));
                    List<ItemStack> before = equipment(ordinary);
                    site.join(ordinary);
                    helper.assertValueEqual(BloodMoonEquipment.spawnEquipmentLocked(ordinary), day == 16L,
                            "The real installed-addon spawn uses the inclusive16-day boundary: " + id);
                    if (day == 16L) empty(helper, ordinary);
                    else if (id.equals("minecraft:zombie")) unchanged(helper, ordinary, before);
                    else helper.assertTrue(BuiltInRegistries.ITEM.getKey(ordinary.getItemBySlot(EquipmentSlot.HEAD).getItem())
                                    .equals(ResourceLocation.parse("modern_ite:iron_bucket_helmet")),
                            "The addon's own ordinary helmet is restored for a new day17 spawn");
                    ordinary.discard();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void previouslyLockedOrdinaryAddonMobsDoNotRerollGearWhenTheCalendarReachesSeventeen(GameTestHelper helper) {
        try (var site = new Site(helper)) {
            for (String id : List.of("minecraft:zombie", "modern_ite:zombie_iron_bucket")) {
                site.date(16L);
                Mob ordinary = site.finalized(id); site.join(ordinary);
                helper.assertTrue(BloodMoonEquipment.spawnEquipmentLocked(ordinary), "The real ordinary spawn records its early birth decision");
                empty(helper, ordinary);
                site.date(17L);
                var joinedAgain = new EntityJoinLevelEvent(ordinary, site.level, false);
                NeoForge.EVENT_BUS.post(joinedAgain);
                helper.assertFalse(joinedAgain.isCanceled(), "An already admitted mob can re-enter after the date boundary");
                helper.assertTrue(BloodMoonEquipment.spawnEquipmentLocked(ordinary), "Only new births unlock at day17");
                empty(helper, ordinary); ordinary.discard();
            }
        }
        helper.succeed();
    }

    private static List<ItemStack> equipment(Mob mob) {
        return Arrays.stream(EquipmentSlot.values()).map(slot -> mob.getItemBySlot(slot).copy()).toList();
    }

    private static void unchanged(GameTestHelper helper, Mob mob, List<ItemStack> before) {
        for (EquipmentSlot slot : EquipmentSlot.values()) helper.assertTrue(
                ItemStack.matches(before.get(slot.ordinal()), mob.getItemBySlot(slot)),
                "Every saved equipment component survives the installed-addon event: " + mob.getType() + "/" + slot);
    }

    private static void empty(GameTestHelper helper, Mob mob) {
        for (EquipmentSlot slot : EquipmentSlot.values()) helper.assertTrue(mob.getItemBySlot(slot).isEmpty(),
                "The actual early ordinary birth retains no equipment: " + mob.getType() + "/" + slot);
    }

    private static final class Site implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final long previousTime, previousHighest;
        private final boolean previousDirty;
        private final List<Mob> entities = new ArrayList<>();

        Site(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel().getServer().overworld();
            previousTime = level.getDayTime(); previousHighest = BloodMoonInvasionData.get(level).highestDay();
            previousDirty = BloodMoonInvasionData.get(level).isDirty();
            helper.assertTrue(ModList.get().isLoaded("modern_ite"), "This opt-in compatibility namespace requires the real Modern ITE addon");
            for (String id : LORDS) helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(id)),
                    "Every required native/addon lord must actually be registered: " + id);
        }

        void date(long day) {
            level.setDayTime((day - 1L) * 24_000L + 6_000L);
            BloodMoonInvasionData.get(level).restoreProgress(day);
        }

        Mob create(String id) {
            var key = ResourceLocation.parse(id);
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(key), "The fixture uses an actual registered entity: " + id);
            Mob mob = (Mob) BuiltInRegistries.ENTITY_TYPE.get(key).create(level);
            if (mob == null) throw new AssertionError("Cannot create " + id);
            mob.moveTo(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
            mob.setNoAi(true); mob.setNoGravity(true); mob.getRandom().setSeed(196L);
            entities.add(mob); return mob;
        }

        Mob finalized(String id) {
            Mob mob = create(id);
            // Eggs use the full production finalizer and still receive early-equipment restrictions.
            // The addon's separate automatic-spawn opening dates do not reject this explicit birth.
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWN_EGG, null);
            helper.assertFalse(mob.isSpawnCancelled(), "The explicit real spawn reaches equipment initialization: " + id);
            return mob;
        }

        void join(Mob mob) {
            helper.assertTrue(level.addFreshEntity(mob), "The real native and addon listeners admit this explicit spawn: " + mob.getType());
        }

        @Override public void close() {
            entities.forEach(Mob::discard);
            level.setDayTime(previousTime); BloodMoonInvasionData.get(level).restoreProgress(previousHighest);
            BloodMoonInvasionData.get(level).setDirty(previousDirty);
        }
    }
}
