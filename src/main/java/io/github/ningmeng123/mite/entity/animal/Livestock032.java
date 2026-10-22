package io.github.ningmeng123.mite.entity.animal;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.entity.SourceVanillaMobLoot;
import io.github.ningmeng123.mite.entity.CubeCropBlight;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.survival.CowFoodState;
import io.github.ningmeng123.mite.survival.FoodVessels;
import io.github.ningmeng123.mite.worldgen.R196OutdoorChecks;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Actual EntityLivestock default metabolism and species production using the existing source loot state. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class Livestock032 {
    private Livestock032() {}
    public static boolean sourceLivestock(Mob mob) { return SourceVanillaMobLoot.livestock(mob.getType()); }
    public static CompoundTag state(Mob mob) {
        SourceVanillaMobLoot.initialize(mob);
        CompoundTag state = mob.getPersistentData().getCompound(SourceVanillaMobLoot.STATE);
        if (!sourceLivestock(mob) || mob.level().isClientSide) return state;
        if (!state.contains("livestock_version")) {
            state.putInt("livestock_version", 1);
            state.putInt("production_counter", 0);
            if (mob instanceof Cow cow) {
                // Retain milk and manure already saved by the food batch; never reroll the 031 needs.
                state.putInt("milk", cow.hasData(MiteAttachments.COW_FOOD) ? cow.getData(MiteAttachments.COW_FOOD).milk() : 100);
                state.putInt("manure_countdown", cow.hasData(MiteAttachments.COW_MANURE_COUNTDOWN) ? cow.getData(MiteAttachments.COW_MANURE_COUNTDOWN) : mob.getRandom().nextInt(24000));
            } else state.putInt("manure_countdown", mob.getRandom().nextInt(manurePeriod(mob)));
            save(mob,state);
        }
        return state;
    }
    public static LivestockRules032.Needs needs(Mob mob) {
        if(mob.level().isClientSide && mob instanceof Livestock032View view) return view.mite$livestockNeeds();
        var s = state(mob); return new LivestockRules032.Needs(s.getFloat("food"), s.getFloat("water"), s.getFloat("freedom"));
    }
    public static void needs(Mob mob, LivestockRules032.Needs needs) {
        if (!sourceLivestock(mob) || mob.level().isClientSide) return;
        var s = state(mob); s.putFloat("food", needs.food()); s.putFloat("water", needs.water()); s.putFloat("freedom", needs.freedom()); save(mob, s);
    }
    public static void save(Mob mob, CompoundTag state) {
        if (mob.level().isClientSide) return;
        if (Math.min(state.getFloat("food"), Math.min(state.getFloat("water"), state.getFloat("freedom"))) < .25F)
            state.putInt("recovery_ticks", LivestockRules032.RECOVERY_TICKS);
        mob.getPersistentData().put(SourceVanillaMobLoot.STATE, state);
        if(mob instanceof Livestock032View view) {
            view.mite$setLivestockNeeds(new LivestockRules032.Needs(state.getFloat("food"),state.getFloat("water"),state.getFloat("freedom")));
            view.mite$setRecoveryTicks(Math.clamp(state.getInt("recovery_ticks"),0,LivestockRules032.RECOVERY_TICKS));
        }
        if (mob instanceof Cow cow) {
            var synced=new CowFoodState(1, Math.clamp(state.getInt("milk"), 0, 100), state.getFloat("food"), state.getFloat("water"), state.getFloat("freedom"));
            if(!cow.hasData(MiteAttachments.COW_FOOD) || !cow.getData(MiteAttachments.COW_FOOD).equals(synced)) cow.setData(MiteAttachments.COW_FOOD,synced);
            cow.setData(MiteAttachments.COW_MANURE_COUNTDOWN, state.getInt("manure_countdown"));
        }
    }
    public static boolean isHungry(Mob mob) { return needs(mob).food() < .5F; }
    public static boolean well(Mob mob) {
        return needs(mob).well() && (mob.level().isClientSide && mob instanceof Livestock032View view
                ? view.mite$recoveryTicks() == 0 : state(mob).getInt("recovery_ticks") <= 0);
    }
    public static void tickRecovery(Mob mob) {
        var state = state(mob);
        int recovery = state.getInt("recovery_ticks");
        if (recovery > 0 && needs(mob).well()) {
            state.putInt("recovery_ticks", recovery - 1);
            save(mob, state);
        }
    }
    public static boolean isThirsty(Mob mob) { return needs(mob).water() < .5F; }
    public static boolean isVeryHungry(Mob mob) { return needs(mob).food() < .25F; }
    public static boolean isVeryThirsty(Mob mob) { return needs(mob).water() < .25F; }
    public static boolean isDesperateForFood(Mob mob) { return needs(mob).food() < .05F; }
    public static boolean isDesperateForWater(Mob mob) { return needs(mob).water() < .05F; }
    public static int manurePeriod(Mob mob) { return LivestockRules032.manurePeriod(mob instanceof Chicken, mob instanceof Pig || mob instanceof Sheep); }
    public static boolean foodBlock(Mob mob, BlockState block) {
        return shortVegetation(block)
                || (mob instanceof Cow ? block.is(Blocks.DANDELION) : block.is(Blocks.GRASS_BLOCK));
    }
    public static boolean shortVegetation(BlockState block) { return block.is(Blocks.SHORT_GRASS) || block.is(Blocks.TALL_GRASS) || block.is(Blocks.FERN) || block.is(Blocks.LARGE_FERN); }
    public static boolean isNearFood(Mob mob) { return isNearFood(mob, mob.blockPosition()); }
    public static boolean isNearFood(Mob mob, BlockPos origin) { return foodAt(mob, origin, false); }
    private static boolean foodAt(Mob mob, BlockPos origin, boolean consume) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= (int)mob.getBbHeight(); dy++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos pos = origin.offset(dx, dy, dz);
            if (!mob.level().hasChunkAt(pos) || !foodBlock(mob, mob.level().getBlockState(pos))) continue;
            if (consume && mob instanceof Cow && mob.getRandom().nextFloat() < .01F && EventHooks.canEntityGrief(mob.level(), mob))
                mob.level().destroyBlock(pos, false, mob);
            return true;
        }
        return false;
    }
    public static boolean waterBlock(Mob mob, BlockPos pos) {
        var block = mob.level().getBlockState(pos);
        return block.getFluidState().is(FluidTags.WATER) || block.is(Blocks.SNOW) || block.is(Blocks.SNOW_BLOCK)
                || block.is(Blocks.WATER_CAULDRON) && block.getValue(LayeredCauldronBlock.LEVEL) > 0 && pos.getY() + 1 < mob.getEyeY();
    }
    public static boolean isNearWater(Mob mob) { return isNearWater(mob, mob.blockPosition()); }
    public static boolean isNearWater(Mob mob, BlockPos origin) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= (int)mob.getBbHeight(); dy++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos pos = origin.offset(dx, dy, dz);
            if (mob.level().hasChunkAt(pos) && waterBlock(mob, pos)
                    && !mob.level().getBlockState(pos.above()).isFaceSturdy(mob.level(), pos.above(), Direction.DOWN)) return true;
        }
        return false;
    }
    public static boolean isCrowded(Mob mob) { return crowded(mob, mob.blockPosition(), mob.getBoundingBox().inflate(2D, .5D, 2D)); }
    public static boolean isCrowded(Mob mob, BlockPos pos) { return crowded(mob, pos, new AABB(pos.getX()-2, pos.getY()-.5, pos.getZ()-2, pos.getX()+2, pos.getY()+.5, pos.getZ()+2)); }
    private static boolean crowded(Mob mob, BlockPos pos, AABB box) {
        if (!(mob.level() instanceof ServerLevel level) || !R196OutdoorChecks.outdoors(level, pos)) return true;
        // abw.a(Class,AABB)/adr.a includes the owner; players extend of, not og/EntityLiving.
        return level.getEntitiesOfClass(Mob.class, box).size() > 2;
    }
    public static boolean foodItem(Mob mob, ItemStack stack) {
        return mob instanceof Chicken ? stack.is(Items.WHEAT_SEEDS) : mob instanceof Pig ? stack.is(Items.CARROT) || stack.is(Items.BROWN_MUSHROOM) : stack.is(Items.WHEAT);
    }
    public static boolean canEat(Mob mob, ItemStack stack) {
        if (!sourceLivestock(mob) || !(mob instanceof Animal animal) || !mob.isAlive() || mob.isBaby() || !mob.level().isClientSide && (animal.getAge() != 0 || animal.isInLove()) || !foodItem(mob, stack)) return false;
        return mob.getHealth() < mob.getMaxHealth() || well(mob) || isHungry(mob);
    }
    public static boolean shouldEat(Mob mob, ItemStack stack) { return canEat(mob, stack); }
    /** The love check happens before og's half-health heal; an injured adult needs a later meal to mate. */
    public static void onFoodEaten(Mob mob, ItemStack stack) {
        if (!sourceLivestock(mob) || mob.level().isClientSide) return;
        needs(mob, needs(mob).feed());
        if (mob instanceof Animal animal && well(mob) && mob.getHealth() >= mob.getMaxHealth() && !stack.is(Items.ROTTEN_FLESH)) animal.setInLove(null);
        mob.heal(mob.getMaxHealth() * .5F);
    }
    public static void drink(Mob mob, int volume) { needs(mob, needs(mob).drink(volume)); }
    public static boolean updateWellness(Mob mob) {
        needs(mob, needs(mob).update(foodAt(mob, mob.blockPosition(), true), isNearWater(mob), mob.level().isRainingAt(mob.blockPosition()), isCrowded(mob)));
        return well(mob);
    }
    public static void produceGoods(Mob mob) {
        var s = state(mob); int production = s.getInt("production_counter");
        if (mob instanceof Cow) { s.putInt("milk", mob.isBaby() ? 0 : Math.clamp(s.getInt("milk") + production, 0, 100)); s.putInt("production_counter", 0); }
        else if (mob instanceof Chicken) {
            if (production >= 100 && mob.getRandom().nextInt(500) == 0) {
                int feathers = s.getInt("feathers"), maximum = s.getInt("maximum_feathers");
                if (feathers < maximum) s.putInt("feathers", feathers + 1); else mob.spawnAtLocation(Items.FEATHER);
                s.putInt("production_counter", production - 100);
            } else if (production >= 200 && mob.getRandom().nextInt(20) == 0) {
                mob.playSound(SoundEvents.CHICKEN_EGG, 1F, (mob.getRandom().nextFloat() - mob.getRandom().nextFloat()) * .2F + 1F);
                mob.spawnAtLocation(Items.EGG); s.putInt("production_counter", production - 200);
            }
        } else s.putInt("production_counter", 0);
        save(mob, s);
    }
    public static boolean tryDropFeather(Chicken chicken, boolean retainOne) {
        if (chicken.isBaby()) return false;
        var s = state(chicken); int feathers = s.getInt("feathers");
        if (feathers < (retainOne ? 2 : 1)) return false;
        chicken.spawnAtLocation(Items.FEATHER); s.putInt("feathers", feathers-1); save(chicken, s); return true;
    }
    public static void damaged(Mob mob, DamageSource source, float incoming) {
        if (!sourceLivestock(mob) || mob.level().isClientSide || !mob.isAlive()) return;
        if (mob instanceof Sheep sheep && !sheep.isSheared()) {
            var wool = (LivestockSheep032View)sheep;
            if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
                int count=wool.mite$woolFireDamage()+1;
                if(count>5) { sheep.setSheared(true); count=0; sheep.clearFire(); }
                wool.mite$setWoolFireDamage(count);
            } else if (source.getEntity() instanceof io.github.ningmeng123.mite.entity.R196Cube cube && cube.miteKind()!=io.github.ningmeng123.mite.entity.MobKind.NETHERSPAWN
                    || source.getEntity() instanceof net.minecraft.world.entity.monster.Slime && !(source.getEntity() instanceof net.minecraft.world.entity.monster.MagmaCube)
                    || io.github.ningmeng123.mite.entity.MobCombat.acid(source)
                    || io.github.ningmeng123.mite.entity.MobCombat.pepsin(source)) sheep.setSheared(true);
        }
        if (mob instanceof Chicken chicken && !chicken.isBaby() && incoming > 0 && !source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                && !io.github.ningmeng123.mite.entity.MobCombat.magic(source) && !source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.STARVE) && (source.getEntity() instanceof LivingEntity || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))) {
            for (int i=0; i<(int)incoming && SourceVanillaMobLoot.feathers(chicken)>0; i++) if(chicken.getRandom().nextFloat()<.2F) tryDropFeather(chicken,false);
        }
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Animal animal) || !sourceLivestock(animal)) return;
        save(animal,state(animal));
        animal.goalSelector.removeAllGoals(goal -> goal instanceof TemptGoal || goal instanceof EatBlockGoal);
        animal.goalSelector.addGoal(animal instanceof Pig ? 4 : 3, new SourcePetGoals032.Tempt(animal, animal instanceof Cow ? 1.25D : animal instanceof Sheep ? 1.1D : animal instanceof Pig ? 1.2D : 1D,
                stack -> foodItem(animal, stack) || animal instanceof Pig && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("carrot_on_a_stick"), false));
        if (animal.goalSelector.getAvailableGoals().stream().noneMatch(w -> w.getGoal() instanceof LivestockSeeking032)) {
            animal.goalSelector.addGoal(2, new LivestockSeeking032(animal, false)); animal.goalSelector.addGoal(2, new LivestockSeeking032(animal, true));
            animal.goalSelector.addGoal(3, new LivestockFreedomGoal032(animal));
            if(animal instanceof Sheep sheep) animal.goalSelector.addGoal(5,new LivestockGrazingGoal032(sheep));
        }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || !sourceLivestock(mob) || !mob.isAlive()) return;
        tickRecovery(mob);
        if (mob.tickCount % 100 == 0) {
            if(mob.getRandom().nextInt(10)>0 && updateWellness(mob) && !mob.isBaby()) { var s=state(mob); s.putInt("production_counter",s.getInt("production_counter")+1); save(mob,s); }
            produceGoods(mob);
            if(mob instanceof Sheep sheep && !mob.isOnFire()) { var wool=(LivestockSheep032View)sheep; if(wool.mite$woolFireDamage()>0) wool.mite$setWoolFireDamage(wool.mite$woolFireDamage()-1); }
        }
        if(!mob.isBaby() && !isDesperateForFood(mob)) {
            var s=state(mob); int countdown=s.getInt("manure_countdown")-1;
            if(countdown<=0) { mob.spawnAtLocation(MiteFoods.MANURE.get()); countdown=manurePeriod(mob)/2+mob.getRandom().nextInt(manurePeriod(mob)); }
            s.putInt("manure_countdown",countdown); save(mob,s);
        }
        if(mob instanceof Chicken chicken && !mob.isBaby() && !mob.isNoAi() && mob.getRandom().nextInt(40)==0 && !mob.isInWater()) tryDropFeather(chicken,true);
        if(!mob.isBaby() && mob.onGround()) LivestockGrass032.trampleUnder(mob);
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post event) {
        if(event.getNewDamage()>0F && event.getEntity() instanceof Mob mob) damaged(mob,event.getSource(),event.getOriginalDamage());
    }
    @SubscribeEvent public static void conversion(LivingConversionEvent.Post event) {
        if(!(event.getEntity() instanceof net.minecraft.world.entity.animal.MushroomCow original) || !(event.getOutcome() instanceof Cow outcome) || original.level().isClientSide) return;
        var previous=state(original).copy(); outcome.getPersistentData().put(SourceVanillaMobLoot.STATE,previous);
        if(original.getPersistentData().contains(R196AnimalAi032.STATE)) outcome.getPersistentData().put(R196AnimalAi032.STATE,R196AnimalAi032.state(original).copy());
        outcome.setAge(original.getAge()); outcome.setInLoveTime(original.getInLoveTime()); save(outcome,previous);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Mob mob) || !sourceLivestock(mob)) return;
        ItemStack stack=event.getItemStack();
        if(FoodVessels.water(stack) && isThirsty(mob)) {
            if(!mob.level().isClientSide) { drink(mob,stack.is(MiteFoods.WATER_BOWL.get())?1:4); FoodVessels.convertHeld(event.getEntity(),event.getHand(),FoodVessels.empty(stack)); }
            event.setCanceled(true); event.setCancellationResult(InteractionResult.sidedSuccess(mob.level().isClientSide));
        } else if(foodItem(mob,stack)) {
            boolean can=canEat(mob,stack);
            if(can && !mob.level().isClientSide) { onFoodEaten(mob,stack); stack.consume(1,event.getEntity()); }
            event.setCanceled(true); event.setCancellationResult(can?InteractionResult.sidedSuccess(mob.level().isClientSide):InteractionResult.PASS);
        }
    }
    @SubscribeEvent public static void born(BabyEntitySpawnEvent event) {
        if (!(event.getChild() instanceof Mob child) || !sourceLivestock(child) || child.level().isClientSide) return;
        var first=event.getParentA() instanceof Mob a && sourceLivestock(a)?needs(a):new LivestockRules032.Needs(1,1,1);
        var second=event.getParentB() instanceof Mob b && sourceLivestock(b)?needs(b):new LivestockRules032.Needs(1,1,1);
        needs(child,first.inherit(second));
    }
}
