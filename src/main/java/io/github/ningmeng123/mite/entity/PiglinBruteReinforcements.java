package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Gives each native brute one wave of combat reinforcements. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class PiglinBruteReinforcements {
    private static final String SUMMONED = "mite:piglin_brute_summoned";
    public static final String SUMMONER = "mite:piglin_brute_summoner";
    private static final double ATTRIBUTE_MULTIPLIER = 1.5D;
    private static final int LIMIT = 5;
    private static final int[] HEIGHT_OFFSETS = {0, 1, -1, 2, -2};
    private static final List<Holder<Attribute>> BOOSTED_ATTRIBUTES = List.of(Attributes.MAX_HEALTH,
            Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.FOLLOW_RANGE, Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE, Attributes.ATTACK_KNOCKBACK);

    private PiglinBruteReinforcements() {}

    static double attributeMultiplier(Mob mob) {
        return mob.getType() == EntityType.PIGLIN && mob.getPersistentData().hasUUID(SUMMONER) ? ATTRIBUTE_MULTIPLIER : 1D;
    }

    @SubscribeEvent
    public static void afterTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PiglinBrute brute) || brute.getType() != EntityType.PIGLIN_BRUTE
                || !(brute.level() instanceof ServerLevel level) || !brute.isAlive() || brute.isNoAi()
                || brute.tickCount % 10 != 0 || brute.getPersistentData().getBoolean(SUMMONED)) return;
        var target = brute.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null || !target.isAlive() || target.level() != level || !brute.canAttack(target)) return;

        // Claim the wave before spawning entities, since callbacks may run during their admission.
        brute.getPersistentData().putBoolean(SUMMONED, true);
        int spawned = 0;
        for (BlockPos column : BlockPos.spiralAround(brute.blockPosition(), 3, Direction.EAST, Direction.SOUTH)) {
            for (int dy : HEIGHT_OFFSETS) {
                if (!spawn(brute, level, target, column.offset(0, dy, 0))) continue;
                if (++spawned == LIMIT) return;
                break;
            }
        }
        if (spawned == 0) brute.getPersistentData().remove(SUMMONED);
    }

    private static boolean spawn(PiglinBrute brute, ServerLevel level, LivingEntity target, BlockPos pos) {
        if (!SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, EntityType.PIGLIN)) return false;
        var piglin = EntityType.PIGLIN.create(level);
        if (piglin == null) return false;
        piglin.moveTo(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D, brute.getYRot(), 0F);
        if (!level.noCollision(piglin) || !EventHooks.checkSpawnPosition(piglin, level, MobSpawnType.MOB_SUMMONED)) return false;

        piglin.getPersistentData().putUUID(SUMMONER, brute.getUUID());
        EventHooks.finalizeMobSpawn(piglin, level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
        if (piglin.isSpawnCancelled() || piglin.isRemoved()) return false;
        if (piglin.isBaby()) {
            piglin.setBaby(false);
            if (piglin.getMainHandItem().isEmpty()) piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        }
        if (level.dimension().equals(Level.OVERWORLD)) piglin.setImmuneToZombification(true);
        for (var attribute : BOOSTED_ATTRIBUTES) {
            var instance = piglin.getAttribute(attribute);
            if (instance != null) instance.setBaseValue(instance.getBaseValue() * ATTRIBUTE_MULTIPLIER);
        }
        piglin.setHealth(piglin.getMaxHealth());
        piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), 600L);
        piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        return level.addFreshEntity(piglin);
    }
}
