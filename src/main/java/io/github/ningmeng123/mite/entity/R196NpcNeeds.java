package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.Mite;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Shared og dropped-food/repair pickup and of passive healing, with server-only decisions. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class R196NpcNeeds {
    public static final String COOLDOWN = "mite_food_repair_cooldown_031";
    private R196NpcNeeds() {}
    private static final class Meat {
        static final java.util.Set<net.minecraft.resources.ResourceLocation> IDS = load();
        private static java.util.Set<net.minecraft.resources.ResourceLocation> load() {
            try (var reader = new java.io.InputStreamReader(R196NpcNeeds.class.getResourceAsStream("/mite/mob-assets-031.json"), java.nio.charset.StandardCharsets.UTF_8)) {
                var result = new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
                for (var id : com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("source_meat_items"))
                    result.add(net.minecraft.resources.ResourceLocation.parse(id.getAsString()));
                return java.util.Set.copyOf(result);
            } catch (Exception failure) { throw new IllegalStateException("Cannot load actual source ItemMeat identities", failure); }
        }
    }
    public static boolean meat(ItemStack stack) { return !stack.isEmpty() && (Meat.IDS.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())) || stack.is(ItemTags.MEAT)); }
    private static boolean managed(Mob mob) { return mob instanceof R196Mob || io.github.ningmeng123.mite.worldgen.R196MobSpawning.sourceClass(mob) != null; }
    public static boolean food(Mob mob, ItemStack stack) {
        if (mob.isBaby() || !mob.isAlive() || stack.isEmpty() || mob instanceof R196Wolf wolf && wolf.miteKind() == MobKind.HELLHOUND) return false;
        if (mob instanceof TamableAnimal pet && pet.isTame() && pet.getHealth() > pet.getMaxHealth() * .5F) return false;
        if (mob instanceof R196Undead ghoul) return ghoul.miteKind() == MobKind.GHOUL && meat(stack);
        if (mob instanceof Zombie) return meat(stack);
        if (io.github.ningmeng123.mite.entity.animal.Livestock032.sourceLivestock(mob)) return io.github.ningmeng123.mite.entity.animal.Livestock032.shouldEat(mob,stack);
        if (io.github.ningmeng123.mite.entity.animal.R196AnimalAi032.managed(mob)) return io.github.ningmeng123.mite.entity.animal.NativeAnimalSpecial032.shouldEat(mob,stack);
        if (mob instanceof Animal animal) return animal.isFood(stack);
        return mob instanceof Villager && stack.is(Items.BREAD);
    }
    public static boolean repair(Mob mob, ItemStack stack) {
        return mob.getHealth() < mob.getMaxHealth() && (mob instanceof AbstractSkeleton && stack.is(Items.BONE)
                || mob instanceof SnowGolem && stack.is(Items.SNOWBALL));
    }
    public static boolean pickUp(Mob mob, ItemEntity item) {
        if (!managed(mob) || !item.isAlive() || item.hasPickUpDelay() || mob.getPersistentData().getInt(COOLDOWN) > 0) return false;
        boolean repair = repair(mob, item.getItem());
        if (!repair && !food(mob, item.getItem())) return false;
        boolean undead = mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD);
        if (!repair && io.github.ningmeng123.mite.entity.animal.Livestock032.sourceLivestock(mob))
            io.github.ningmeng123.mite.entity.animal.Livestock032.onFoodEaten(mob,item.getItem());
        else if (!repair && io.github.ningmeng123.mite.entity.animal.R196AnimalAi032.managed(mob))
            io.github.ningmeng123.mite.entity.animal.NativeAnimalSpecial032.onFoodEaten(mob,item.getItem());
        else if (repair || !undead) mob.heal(mob.getMaxHealth() * .5F);
        ItemStack remaining = item.getItem().copy(); remaining.shrink(1);
        if (remaining.isEmpty()) item.discard(); else { item.setItem(remaining); ((io.github.ningmeng123.mite.mixin.SourceItemAgeAccess)item).mite$setAge(-18000); }
        mob.getPersistentData().putInt(COOLDOWN, 400); mob.setNoActionTime(-2400);
        if(io.github.ningmeng123.mite.entity.animal.SourceAnimalDespawn032.managed(mob))
            io.github.ningmeng123.mite.entity.animal.SourceAnimalDespawn032.refresh(mob,-2400);
        mob.playSound(SoundEvents.ITEM_PICKUP, .2F, ((mob.getRandom().nextFloat() - mob.getRandom().nextFloat()) * .7F + 1F) * 2F);
        return true;
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof PathfinderMob mob) || !managed(mob)) return;
        if (R196MonsterAi.managed(mob) && mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof R196DiggerGoal) && R196DiggerGoal.watcher(mob))
            mob.goalSelector.addGoal(1, new R196DiggerGoal(mob));
        if (!io.github.ningmeng123.mite.entity.animal.R196AnimalAi032.managed(mob)
                && mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof SeekItem)) mob.goalSelector.addGoal(1, new SeekItem(mob));
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob) || !managed(mob) || !mob.isAlive()) return;
        if (mob instanceof R196Mob source && source.healsWithTime() && MobCombat.ticksWithOffset(mob) % 1000 == 0) mob.heal(mob.getMaxHealth() * .1F);
        int cooldown = mob.getPersistentData().getInt(COOLDOWN);
        if (cooldown > 0) { mob.getPersistentData().putInt(COOLDOWN, cooldown - 1); return; }
        for (ItemEntity item : mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(.5, 0, .5))) if (pickUp(mob, item)) break;
    }
    private static final class SeekItem extends Goal {
        private final PathfinderMob mob; private ItemEntity target;
        SeekItem(PathfinderMob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            if (mob.getPersistentData().getInt(COOLDOWN) > 0 || mob.getRandom().nextInt(40) != 0) return false;
            target = mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(16, 4, 16),
                    item -> item.isAlive() && !item.hasPickUpDelay() && (food(mob, item.getItem()) || repair(mob, item.getItem())))
                    .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            return target != null;
        }
        @Override public boolean canContinueToUse() { return target != null && target.isAlive() && mob.getPersistentData().getInt(COOLDOWN) <= 0 && !mob.getNavigation().isDone(); }
        @Override public void start() { mob.getNavigation().moveTo(target, 1D); }
        @Override public void stop() { target = null; }
    }
}
