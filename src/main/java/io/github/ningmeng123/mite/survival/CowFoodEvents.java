package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteFoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Bounded milking with the source 100-tick wellness production roll. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class CowFoodEvents {
    private CowFoodEvents() {}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getTarget() instanceof Cow cow)) return;
        ItemStack stack=event.getItemStack();
        var player=event.getEntity();
        CowFoodState state=CowFoodState.get(cow);
        if(FoodVessels.water(stack) && state.thirsty()) {
            int volume=stack.is(MiteFoods.WATER_BOWL.get())?1:4;
            if(!cow.level().isClientSide) {
                CowFoodState.set(cow,state.drink(volume));
                FoodVessels.convertHeld(player,event.getHand(),FoodVessels.empty(stack));
            }
            accept(event);
            return;
        }
        if(stack.is(Items.WHEAT) && state.food()<.5F) {
            if(!cow.level().isClientSide) {
                CowFoodState.set(cow,state.feed());
                stack.consume(1,player);
            }
            accept(event);
            return;
        }
        int volume;
        ItemStack result;
        if(stack.is(Items.BOWL) && !(cow instanceof MushroomCow)) {
            volume=1;
            result=new ItemStack(MiteFoods.MILK_BOWL.get());
        } else if(stack.is(Items.BUCKET)) {
            volume=4;
            result=new ItemStack(Items.MILK_BUCKET);
        } else if(stack.getItem() instanceof MiteBucketItem bucket && bucket.contents()==MiteBucketItem.Contents.EMPTY) {
            volume=4;
            result=new ItemStack(MiteFoods.get(bucket.metal()+"_milk_bucket"));
        } else return;
        if(cow.isBaby() || state.milk()<volume*25) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if(!cow.level().isClientSide) player.displayClientMessage(Component.translatable("message.mite.cow_milk_insufficient"),true);
            return;
        }
        if(!cow.level().isClientSide) {
            CowFoodState.set(cow,state.milk(state.milk()-volume*25));
            FoodVessels.convertHeld(player,event.getHand(),result);
            cow.playSound(net.minecraft.sounds.SoundEvents.COW_MILK,1F,1F);
        }
        accept(event);
    }

    private static void accept(PlayerInteractEvent.EntityInteract event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }

    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if(!(event.getEntity() instanceof Cow cow) || cow.level().isClientSide || !cow.isAlive()) return;
        CowFoodState state=CowFoodState.get(cow);
        if(cow.tickCount%100==0 && cow.getRandom().nextInt(10)>0) {
            boolean nearFood=nearFood(cow);
            boolean nearWater=nearWater(cow);
            boolean crowded=!cow.level().canSeeSky(cow.blockPosition())
                    || cow.level().getEntitiesOfClass(Mob.class,cow.getBoundingBox().inflate(2D,.5D,2D)).size()>2;
            state=state.update(nearFood,nearWater,cow.level().isRainingAt(cow.blockPosition()),crowded,cow.isBaby());
            CowFoodState.set(cow,state);
        }
        if(!cow.isBaby() && state.food()>=.05F) {
            int timer=cow.getData(MiteAttachments.COW_MANURE_COUNTDOWN)-1;
            if(timer<=0) {
                cow.spawnAtLocation(MiteFoods.MANURE.get());
                timer=12000+cow.getRandom().nextInt(24000);
            }
            cow.setData(MiteAttachments.COW_MANURE_COUNTDOWN,timer);
        }
    }

    private static boolean nearFood(Cow cow) {
        BlockPos origin=cow.blockPosition();
        int height=(int)cow.getBbHeight();
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=height;dy++)for(int dz=-1;dz<=1;dz++) {
            BlockPos pos=origin.offset(dx,dy,dz);
            var state=cow.level().getBlockState(pos);
            if(state.is(Blocks.SHORT_GRASS)||state.is(Blocks.TALL_GRASS)||state.is(Blocks.DANDELION)) {
                if(cow.getRandom().nextFloat()<.01F) cow.level().destroyBlock(pos,false);
                return true;
            }
        }
        return false;
    }

    private static boolean nearWater(Cow cow) {
        BlockPos origin=cow.blockPosition();
        int height=(int)cow.getBbHeight();
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=height;dy++)for(int dz=-1;dz<=1;dz++) {
            BlockPos pos=origin.offset(dx,dy,dz);
            var state=cow.level().getBlockState(pos);
            boolean water=state.getFluidState().is(FluidTags.WATER)||state.is(Blocks.SNOW)||state.is(Blocks.SNOW_BLOCK)
                    ||state.is(Blocks.WATER_CAULDRON)&&state.getValue(LayeredCauldronBlock.LEVEL)>0&&pos.getY()+1<cow.getEyeY();
            if(water&&!cow.level().getBlockState(pos.above()).isFaceSturdy(cow.level(),pos.above(),Direction.DOWN)) return true;
        }
        return false;
    }

    @SubscribeEvent public static void born(BabyEntitySpawnEvent event) {
        if(event.getChild() instanceof Cow child && event.getParentA() instanceof Cow a && event.getParentB() instanceof Cow b) {
            CowFoodState first=CowFoodState.get(a),second=CowFoodState.get(b);
            CowFoodState.set(child,new CowFoodState(1,100,Math.min(first.food(),second.food()),Math.min(first.water(),second.water()),
                    Math.min(first.freedom(),second.freedom())));
        }
    }
}
