package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.rules.BloodMoonEquipmentRules;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.List;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real Nether piglin spawn equipment with a controlled Overworld calendar. @author NingMeng123 */
@GameTestHolder("mite_equipment_progression")
@PrefixGameTestTemplate(false)
public final class PiglinArmorGameTests {
    @GameTest(template = "empty")
    public static void piglinsAndBrutesRollDateArmorWithABaseTierOfGold(GameTestHelper h) {
        try (var clock = new SourceEquipmentDateFixture(h)) {
            var server = h.getLevel().getServer();
            ServerLevel overworld = server.overworld(), nether = server.getLevel(Level.NETHER);
            overworld.setDayTime(6_000L);
            var history = BloodMoonInvasionData.get(overworld);
            long firstOrdinaryDay = BloodMoonEquipmentRules.ORDINARY_START_DAY + 1L;
            long laterOrdinaryDay = firstOrdinaryDay + 48L;
            for (var type : List.of(EntityType.PIGLIN, EntityType.PIGLIN_BRUTE)) {
                history.restoreProgress(1L);
                var early = prepare(type, nether, legacyLeatherSeed());
                finalizeSpawn(early, nether);
                h.assertTrue(early.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET),
                        "The real legacy leather helmet roll becomes gold on day one for " + type);

                history.restoreProgress(firstOrdinaryDay);
                var minimum = prepare(type, nether, ordinarySeed(firstOrdinaryDay, 0));
                ItemStack supplied = new ItemStack(Items.IRON_HELMET);
                minimum.setItemSlot(EquipmentSlot.HEAD, supplied);
                finalizeSpawn(minimum, nether);
                h.assertTrue(minimum.getItemBySlot(EquipmentSlot.HEAD) == supplied
                                && minimum.getItemBySlot(EquipmentSlot.CHEST).is(Items.GOLDEN_CHESTPLATE),
                        "Nether finalization floors an ordinary leather roll at gold and retains supplied armor for " + type);

                history.restoreProgress(laterOrdinaryDay);
                var later = prepare(type, nether, ordinarySeed(laterOrdinaryDay, 5));
                finalizeSpawn(later, nether);
                h.assertTrue(later.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                                && later.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                                && later.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
                                && later.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS),
                        "A later ordinary iron roll equips the full iron set for " + type);
                if (type == EntityType.PIGLIN_BRUTE)
                    h.assertTrue(later.getMainHandItem().is(Items.GOLDEN_AXE), "A piglin brute still spawns with its native axe");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bastionBrutesWithVanillaTemplateHealthInitializeAtFullHealth(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        for (String name : List.of("melee_piglin", "melee_piglin_always")) {
            var id = ResourceLocation.withDefaultNamespace("structure/bastion/mobs/" + name + ".nbt");
            try (var input = server.getResourceManager().getResourceOrThrow(id).open()) {
                var template = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
                var nbt = template.getList("entities", Tag.TAG_COMPOUND).getCompound(0).getCompound("nbt");
                var brute = (PiglinBrute)EntityType.loadEntityRecursive(nbt, nether, Function.identity());
                h.assertTrue(brute != null && brute.getHealth() == 50F && brute.getMaxHealth() == 100F,
                        "The actual bastion template loads its former full health before structure finalization");
                finalizeSpawn(brute, nether);
                h.assertTrue(brute.getHealth() == 100F && brute.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 14D,
                        "An actual bastion brute starts at its new full health and innate attack");
            }
        }
        h.succeed();
    }

    private static AbstractPiglin prepare(EntityType<? extends AbstractPiglin> type, ServerLevel level, long seed) {
        AbstractPiglin piglin = type.create(level);
        if (piglin == null) throw new AssertionError("Cannot create " + type);
        piglin.moveTo(.5D, 64D, .5D, 0F, 0F);
        piglin.getRandom().setSeed(seed);
        return piglin;
    }

    private static void finalizeSpawn(AbstractPiglin piglin, ServerLevel level) {
        // Structure finalization leaves the ordinary piglin's adult state deterministic.
        EventHooks.finalizeMobSpawn(piglin, level, level.getCurrentDifficultyAt(piglin.blockPosition()), MobSpawnType.STRUCTURE, null);
    }

    private static long legacyLeatherSeed() {
        for (long seed = 0L; seed < 100_000L; seed++) {
            var random = RandomSource.create(seed);
            if (random.nextFloat() < .3F && random.nextInt(2) == 0
                    && random.nextFloat() >= .095F && random.nextFloat() >= .095F && random.nextFloat() >= .095F) return seed;
        }
        throw new AssertionError("No early leather armor fixture");
    }

    private static long ordinarySeed(long day, int tier) {
        double progress = BloodMoonEquipmentRules.ordinaryProgress(day);
        for (long seed = 0L; seed < 100_000L; seed++) {
            var random = RandomSource.create(seed);
            double acceptance = random.nextDouble(), material = random.nextDouble();
            if (acceptance < BloodMoonEquipmentRules.ordinaryArmorChance(progress)
                    && BloodMoonEquipmentRules.selectOrdinaryTier(progress, material) == tier) return seed;
        }
        throw new AssertionError("No ordinary armor fixture");
    }
}
