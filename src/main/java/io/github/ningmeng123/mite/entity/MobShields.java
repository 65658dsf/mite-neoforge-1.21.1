package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteShields;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.api.MiteExtensions;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Spawn-only sword/shield rolls and native, directional blocking for armed humanoids. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class MobShields {
    public static final String SPAWN_ROLL = "mite:shield_spawn_decided";
    public static final String DISABLED_UNTIL = "mite:shield_disabled_until";
    public static final float SPAWN_CHANCE = .15F;
    private static final Set<String> METALS = Set.of("rusted_iron", "copper", "silver", "gold", "iron",
            "ancient_metal", "mithril", "adamantium", "vibranium");

    private MobShields() {}

    public static boolean handles(Mob mob) {
        return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof AbstractPiglin
                || mob instanceof Vindicator;
    }

    public static boolean melee(ItemStack stack) {
        return !stack.isEmpty() && (MiteTools.definition(stack) != null || stack.getItem() instanceof SwordItem);
    }

    public static boolean shield(ItemStack stack) {
        return !stack.isEmpty() && stack.canPerformAction(ItemAbilities.SHIELD_BLOCK);
    }

    public static boolean defending(Mob mob) {
        return mob.isUsingItem() && mob.getUsedItemHand() == InteractionHand.OFF_HAND && shield(mob.getUseItem());
    }

    public static boolean disabled(Mob mob) {
        return mob.getPersistentData().getLong(DISABLED_UNTIL) > mob.level().getGameTime();
    }

    public static boolean spawnReason(MobSpawnType reason) {
        if (reason == null) return false;
        return switch (reason) {
            case NATURAL, CHUNK_GENERATION, SPAWNER, TRIAL_SPAWNER, STRUCTURE, SPAWN_EGG,
                    REINFORCEMENT, PATROL, JOCKEY, EVENT, MOB_SUMMONED, DISPENSER -> true;
            default -> false;
        };
    }

    public static String fallbackMaterial(long day) {
        return day >= 90 ? "iron" : day >= 30 ? "copper" : "rusted_iron";
    }

    public static String material(ItemStack weapon, long day) {
        var definition = MiteTools.definition(weapon);
        return definition != null && METALS.contains(definition.material()) ? definition.material() : fallbackMaterial(day);
    }

    /** Called after every existing spawn equipment listener has finished; never re-roll on load or travel. */
    public static boolean populate(Mob mob) {
        if (!handles(mob) || mob.level().isClientSide || mob.getPersistentData().getBoolean(SPAWN_ROLL)) return false;
        mob.getPersistentData().putBoolean(SPAWN_ROLL, true);
        if (!MiteExtensions.policy().mobDefense(mob,true)
                || !spawnReason(mob.getSpawnType()) || !mob.getOffhandItem().isEmpty()
                || !mob.getMainHandItem().isEmpty() && !melee(mob.getMainHandItem())
                || mob.getRandom().nextFloat() >= SPAWN_CHANCE) return false;
        long day = NativeMobEquipment.day(mob.level().getDayTime());
        String material = material(mob.getMainHandItem(), day);
        if (mob.getMainHandItem().isEmpty()) {
            String weapon = material.equals("iron") ? "minecraft:iron_sword" : "mite:" + material + "_sword";
            mob.setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(mob, weapon, true));
        }
        mob.setItemSlot(EquipmentSlot.OFFHAND,
                R196MobEquipment.generated(mob, new ItemStack(MiteShields.get(material)), false));
        return true;
    }

    public static void install(Mob mob) {
        if (handles(mob) && mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof ShieldBlockGoal))
            mob.goalSelector.addGoal(1, new ShieldBlockGoal(mob));
    }

    public static boolean axe(ItemStack stack) {
        var tool = MiteTools.definition(stack);
        return tool != null && switch (tool.family()) {
            case AXE, BATTLE_AXE, HATCHET -> true;
            default -> false;
        };
    }

    /** Native wear budget only; ShieldGuard applies material-based exhaustion after this hit finishes. */
    public static void hurtShield(LivingEntity defender, float damage) {
        if (!(defender instanceof Mob mob) || !(mob.level() instanceof ServerLevel) || !Float.isFinite(damage)
                || damage < 3F || !mob.isUsingItem()) return;
        ItemStack stack = mob.getUseItem();
        if (MiteShields.definition(stack) == null || !shield(stack)) return;
        EquipmentSlot slot = mob.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        int wear = (int)Math.min(Integer.MAX_VALUE, 1L + (long)Math.floor(damage));
        stack.hurtAndBreak(wear, mob, slot);
        if (stack.isEmpty()) {
            mob.setItemSlot(slot, ItemStack.EMPTY);
            mob.stopUsingItem();
            mob.playSound(SoundEvents.SHIELD_BREAK, .8F, .8F + mob.getRandom().nextFloat() * .4F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob) || !handles(mob)) return;
        if (event.loadedFromDisk()) mob.getPersistentData().putBoolean(SPAWN_ROLL, true);
        install(mob);
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide && mob.isAlive()) populate(mob);
    }
}
