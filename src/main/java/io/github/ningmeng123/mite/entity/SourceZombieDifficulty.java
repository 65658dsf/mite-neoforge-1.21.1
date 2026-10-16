package io.github.ningmeng123.mite.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;

/** tw.az/tw.a spawn attributes, also inherited by tn; modern zombie variants remain separate. @author NingMeng123 */
public final class SourceZombieDifficulty {
    public static final ResourceLocation FOLLOW_BONUS = ResourceLocation.withDefaultNamespace("random_spawn_bonus");
    public static final ResourceLocation SPAWN_BONUS = ResourceLocation.fromNamespaceAndPath("mite", "source_zombie_spawn_bonus");
    public static final ResourceLocation LEADER_BONUS = ResourceLocation.fromNamespaceAndPath("mite", "source_zombie_leader_bonus");

    private SourceZombieDifficulty() {}

    public record SpawnHealth(float maximum, boolean fillAfterIncrease) {}

    /** Only a fresh, full-health spawn may follow its newly rolled leader maximum. */
    public static SpawnHealth captureSpawnHealth(Zombie zombie) {
        float maximum = zombie.getMaxHealth();
        boolean fresh = zombie.getSpawnType() == null && !zombie.isAddedToLevel() && zombie.tickCount == 0;
        return new SpawnHealth(maximum, fresh && zombie.getHealth() >= maximum);
    }

    public static void finishSpawn(Zombie zombie, SpawnHealth before) {
        if (before.fillAfterIncrease() && zombie.isAlive() && zombie.getMaxHealth() > before.maximum())
            zombie.setHealth(zombie.getMaxHealth());
    }

    public static boolean handles(EntityType<?> type) {
        return type == EntityType.ZOMBIE || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.ZOMBIFIED_PIGLIN;
    }

    /** Source tw.az initializes this once during construction; tn.az then fixes its base chance at zero. */
    public static void initializeBaseChance(Zombie zombie) {
        if (!handles(zombie.getType())) return;
        double inheritedChance = zombie.getRandom().nextDouble() * (double) .1F;
        zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(
                zombie.getType() == EntityType.ZOMBIFIED_PIGLIN ? 0D : inheritedChance);
    }

    /** Register before native Mob.finalizeSpawn so its existing guard skips the modern triangular world-RNG draw. */
    public static void prepareFollowRange(Zombie zombie) {
        if (!handles(zombie.getType())) return;
        var follow = zombie.getAttribute(Attributes.FOLLOW_RANGE);
        if (!follow.hasModifier(FOLLOW_BONUS)) follow.addPermanentModifier(new AttributeModifier(FOLLOW_BONUS,
                zombie.getRandom().nextGaussian() * .05D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /** No modern follow-range multiplier or second base-chance reroll exists in tw.a(oi). */
    public static boolean finishAttributes(Zombie zombie) {
        if (!handles(zombie.getType())) return false;
        var random = zombie.getRandom();
        zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addOrReplacePermanentModifier(new AttributeModifier(
                SPAWN_BONUS, random.nextDouble() * (double) .05F, AttributeModifier.Operation.ADD_VALUE));
        if (random.nextFloat() < R196MobEquipment.tension(zombie) * .05F) {
            zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addOrReplacePermanentModifier(new AttributeModifier(
                    LEADER_BONUS, random.nextDouble() * .25D + .5D, AttributeModifier.Operation.ADD_VALUE));
            zombie.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(
                    LEADER_BONUS, random.nextDouble() * 3D + 1D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        return true;
    }
}
