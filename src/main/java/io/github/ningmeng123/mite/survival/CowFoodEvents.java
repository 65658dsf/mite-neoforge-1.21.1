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
        if(stack.is(Items.BOWL) && cow instanceof MushroomCow mushroomCow) {
            // Modern mushroom/suspicious stew uses the same source production budget; the native method preserves flower effects.
            var result=mushroomCow.mobInteract(player,event.getHand());
            event.setCanceled(true); event.setCancellationResult(result); return;
        }
        if(FoodVessels.water(stack) && state.thirsty()) {
            int volume=stack.is(MiteFoods.WATER_BOWL.get())?1:4;
            if(!cow.level().isClientSide) {
                CowFoodState.set(cow,state.drink(volume));
                FoodVessels.convertHeld(player,event.getHand(),FoodVessels.empty(stack));
            }
            accept(event);
            return;
        }
        int volume;
        ItemStack result;
        if(stack.is(Items.BOWL)) {
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

}
