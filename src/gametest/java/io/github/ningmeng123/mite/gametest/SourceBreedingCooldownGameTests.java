package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.animal.Livestock032;
import io.github.ningmeng123.mite.entity.animal.LivestockRules032;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ExperienceOrb;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Successful native births and real NBT/age updates verify nk's distinct parent and newborn timers. @author NingMeng123 */
@GameTestHolder("mite_feedback")
@PrefixGameTestTemplate(false)
public final class SourceBreedingCooldownGameTests {
    private static final List<EntityType<? extends Animal>> ANIMALS = List.of(EntityType.COW, EntityType.PIG,
            EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT);

    @GameTest(template = "empty")
    public static void actualBreedingSetsBothParentCooldownsAndReloadingNeverRestartsTheSixThousandBoundary(GameTestHelper h) {
        for (var type : ANIMALS) {
            Animal first = adult(h, type), second = adult(h, type);
            try (var capture = new SpawnedEntityCapture(h.getLevel(), entity -> entity instanceof Animal || entity instanceof ExperienceOrb)) {
                h.getLevel().addFreshEntity(first); h.getLevel().addFreshEntity(second);
                first.setInLove(null); second.setInLove(null);
                h.assertTrue(first.canMate(second), "Healthy fed adult parents actually enter native mating state: " + type);
                first.spawnChildFromBreeding(h.getLevel(), second);
                var babies = capture.live(Animal.class).stream().filter(Animal::isBaby).toList();
                h.assertValueEqual(babies.size(), 1, "A real successful breeding transaction inserts one actual newborn: " + type);
                h.assertTrue(babies.getFirst().isAddedToLevel(), "The newborn actually entered the server level");
                h.assertValueEqual(first.getAge(), 16000, "First parent receives R196's13m20s cooldown");
                h.assertValueEqual(second.getAge(), 16000, "Second parent receives the same cooldown");
                h.assertTrue(!first.isInLove() && !second.isInLove(), "Successful breeding consumes both love states");
                h.assertValueEqual(babies.getFirst().getAge(), type == EntityType.RABBIT ? -24000 : -64000,
                        "Newborn growth is a separate negative timer; source livestock use64000 ticks, modern rabbits retain native growth");
                for (Animal parent : List.of(first, second)) {
                    Animal restored = reload(h, type, parent);
                    h.assertValueEqual(restored.getAge(), 16000, "Saving immediately after the birth preserves the full source cooldown");
                    restored.aiStep();
                    h.assertValueEqual(restored.getAge(), 15999, "Actual server age logic spends exactly one cooldown tick");
                    Animal ongoing = reload(h, type, restored);
                    restored.discard();
                    h.assertValueEqual(ongoing.getAge(), 15999, "A partially elapsed cooldown stays partially elapsed after reload");
                    for (int tick = 0; tick < 9998; tick++) ongoing.aiStep();
                    h.assertValueEqual(ongoing.getAge(), 6001, "The same real countdown approaches the old vanilla boundary");
                    ongoing.aiStep();
                    h.assertValueEqual(ongoing.getAge(), 6000, "Crossing6001→6000 cannot restart the source cooldown");
                    Animal boundary = reload(h, type, ongoing);
                    ongoing.discard();
                    h.assertValueEqual(boundary.getAge(), 6000, "Loading saved Age6000 is not a new breeding operation");
                    for (int tick = 0; tick < 6000; tick++) boundary.aiStep();
                    h.assertValueEqual(boundary.getAge(), 0, "The complete original16000 ticks eventually unlock breeding again");
                    boundary.setInLove(null);
                    h.assertTrue(boundary.isInLove(), "The recovered adult may enter a new native mating state");
                    boundary.discard();
                }
            } finally { first.discard(); second.discard(); }
        }
        h.succeed();
    }

    private static Animal adult(GameTestHelper h, EntityType<? extends Animal> type) {
        Animal animal = type.create(h.getLevel());
        animal.setPos(h.absolutePos(new BlockPos(1, 2, 1)).getCenter());
        animal.setAge(0); animal.setNoAi(true); animal.setNoGravity(true); animal.setHealth(animal.getMaxHealth());
        if (Livestock032.sourceLivestock(animal)) Livestock032.needs(animal, new LivestockRules032.Needs(1, 1, 1));
        return animal;
    }

    private static Animal reload(GameTestHelper h, EntityType<? extends Animal> type, Animal original) {
        CompoundTag saved = new CompoundTag(); original.saveWithoutId(saved);
        Animal restored = type.create(h.getLevel()); restored.load(saved);
        return restored;
    }
}
