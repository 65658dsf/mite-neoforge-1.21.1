package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.R196VanillaMobRules;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;

/** Registers native mob baselines through NeoForge while preserving existing saved attributes. @author NingMeng123 */
public final class R196VanillaMobEvents {
    public static final double PIGLIN_BRUTE_HEALTH = 150D;
    public static final double PIGLIN_BRUTE_ATTACK = 20D;

    private R196VanillaMobEvents() {}
    public static void register(IEventBus modBus) {
        modBus.addListener(R196VanillaMobEvents::attributes);
        NeoForge.EVENT_BUS.addListener(R196VanillaMobEvents::dimensions);
    }
    @SuppressWarnings("unchecked")
    private static void attributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PIGLIN_BRUTE, Attributes.MAX_HEALTH, PIGLIN_BRUTE_HEALTH);
        event.add(EntityType.PIGLIN_BRUTE, Attributes.ATTACK_DAMAGE, PIGLIN_BRUTE_ATTACK);
        R196VanillaMobRules.entries().forEach((rawType, row) -> {
            EntityType<? extends LivingEntity> type = (EntityType<? extends LivingEntity>)rawType;
            event.add(type, Attributes.MAX_HEALTH, row.health());
            event.add(type, Attributes.MOVEMENT_SPEED, row.speed());
            event.add(type, Attributes.FOLLOW_RANGE, row.follow());
            event.add(type, Attributes.KNOCKBACK_RESISTANCE, row.knockback());
            event.add(type, Attributes.ARMOR, 0);
            if (row.sourceAttackAttribute()) event.add(type, Attributes.ATTACK_DAMAGE, row.attack());
        });
    }
    private static void dimensions(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Mob mob) || mob instanceof R196Mob || mob instanceof Slime || event.getPose() != Pose.STANDING) return;
        if (mob.getType() == EntityType.SKELETON && mob instanceof OrdinarySkeletonState state && state.mite$sourceSkeletonType() == 1) return;
        var row = R196VanillaMobRules.entry(mob.getType()); if (row == null) return;
        float factor = mob.isBaby() ? .5F : 1F, width = row.width() * factor, height = row.height() * factor;
        EntityDimensions original = event.getNewSize();
        float scaleX = width / original.width(), scaleY = height / original.height();
        event.setNewSize(new EntityDimensions(width, height, Math.clamp(original.eyeHeight() * scaleY, 0F, height),
                original.attachments().scale(scaleX, scaleY, scaleX), false));
    }
    public static void naturalHealing(Mob mob) {
        if (mob.level().isClientSide || !mob.isAlive() || !R196VanillaMobRules.healsWithTime(mob.getType()) || MobCombat.ticksWithOffset(mob) % 1000 != 0) return;
        if (mob.getHealth() < mob.getMaxHealth()) mob.setHealth(mob.getHealth() + mob.getMaxHealth() * .1F);
    }
}
