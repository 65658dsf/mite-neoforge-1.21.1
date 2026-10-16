package io.github.ningmeng123.mite.advancement;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.registry.MiteArmorContent;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Only completed source actions earn these advancements; inventory ownership is not crafting. @author NingMeng123 */
@EventBusSubscriber(modid=Mite.MOD_ID)
public final class AchievementEvents {
    private AchievementEvents(){}
    @SubscribeEvent public static void earned(AdvancementEvent.AdvancementEarnEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var id=event.getAdvancement().id();
        if(!id.getNamespace().equals("mite")||!id.getPath().startsWith("r196/"))return;
        WorldAchievementData.get(player.serverLevel()).record(id.toString(),player.getUUID(),player.getScoreboardName(),
                io.github.ningmeng123.mite.worldgen.R196StructureGeneration.displayDay(player.server.overworld().getDayTime()));
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event){
        if(event.getInventory() instanceof net.minecraft.world.inventory.CraftingContainer grid
                &&io.github.ningmeng123.mite.crafting.TimedCraftingController.isCompletingGrid(grid))return;
        MiteAchievements.crafted(event.getEntity(),event.getCrafting());
    }
    @SubscribeEvent public static void smelted(PlayerEvent.ItemSmeltedEvent event){MiteAchievements.cooked(event.getEntity(),event.getSmelting());}
    @SubscribeEvent public static void brewed(net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent event){MiteAchievements.award(event.getEntity(),"potion");}
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event){
        event.getEntity().getPersistentData().putInt("mite_books_read",event.getOriginal().getPersistentData().getInt("mite_books_read"));
    }
    @SubscribeEvent public static void returned(PlayerEvent.PlayerRespawnEvent event){
        if(event.isEndConquered())MiteAchievements.award(event.getEntity(),"theEnd2");
    }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post event){
        Player player=event.getPlayer();ItemStack stack=event.getOriginalStack();
        if(stack.is(Items.OAK_LOG)||stack.is(Items.SPRUCE_LOG)||stack.is(Items.BIRCH_LOG)||stack.is(Items.JUNGLE_LOG))MiteAchievements.award(player,"mineWood");
        else if(stack.is(Items.LEATHER))MiteAchievements.award(player,"killCow");
        else if(stack.is(Items.DIAMOND))MiteAchievements.award(player,"diamonds");
        else if(stack.is(Items.EMERALD))MiteAchievements.award(player,"emeralds");
        else if(stack.is(Items.BLAZE_ROD))MiteAchievements.award(player,"blazeRod");
        else if(stack.is(Items.WHEAT_SEEDS)||MiteAchievements.id(stack,"mite:blueberries")||MiteAchievements.id(stack,"mite:raw_worm"))MiteAchievements.award(player,"seeds");
        else if(stack.is(Items.STICK))MiteAchievements.award(player,"stickPicker");
        else if(stack.is(Items.IRON_NUGGET)||stack.is(Items.GOLD_NUGGET)||Set.of("mite:copper_nugget","mite:silver_nugget","mite:mithril_nugget","mite:adamantium_nugget").stream().anyMatch(id->MiteAchievements.id(stack,id)))MiteAchievements.award(player,"nuggets");
    }
    @SubscribeEvent public static void eaten(LivingEntityUseItemEvent.Finish event){
        if(event.getEntity() instanceof Player player&&event.getItem().is(Items.EGG))MiteAchievements.award(player,"eggs");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event){
        if(event.isCanceled()||!(event.getSource().getEntity() instanceof ServerPlayer player))return;
        var type=event.getEntity().getType();
        if(event.getEntity() instanceof net.minecraft.world.entity.Mob mob&&io.github.ningmeng123.mite.survival.PlayerRest.hostile(mob))MiteAchievements.award(player,"killEnemy");
        boolean sourceSkeleton=event.getEntity() instanceof io.github.ningmeng123.mite.entity.R196Skeleton skeleton?skeleton.skeletonType()==0:
                event.getEntity() instanceof io.github.ningmeng123.mite.entity.OrdinarySkeletonState state&&state.mite$sourceSkeletonType()==0;
        if(sourceSkeleton&&event.getSource().getDirectEntity() instanceof AbstractArrow){
            double dx=player.getX()-event.getEntity().getX(),dz=player.getZ()-event.getEntity().getZ();
            if(dx*dx+dz*dz>=2500)MiteAchievements.award(player,"snipeSkeleton");
        }
        if(type==EntityType.GHAST&&event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.LargeFireball)MiteAchievements.award(player,"ghast");
    }
    @SubscribeEvent public static void overkill(LivingDamageEvent.Post event){
        if(event.getSource().getDirectEntity() instanceof ServerPlayer player && event.getOriginalDamage()>=18F)
            MiteAchievements.award(player,"overkill");
    }
    @SubscribeEvent public static void pig(LivingFallEvent event){
        if(event.getEntity().getType()==EntityType.PIG&&event.getDistance()>5F)
            for(var rider:event.getEntity().getPassengers())if(rider instanceof Player player)MiteAchievements.award(player,"flyPig");
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){
        if(event.getTo().equals(io.github.ningmeng123.mite.worldgen.MiteWorldDimensions.UNDERWORLD))MiteAchievements.award(event.getEntity(),"portal");
        else if(event.getTo().equals(Level.NETHER))MiteAchievements.award(event.getEntity(),"portalToNether");
        else if(event.getTo().equals(Level.END))MiteAchievements.award(event.getEntity(),"theEnd");
        if(event.getFrom().equals(Level.END)&&event.getTo().equals(Level.OVERWORLD))MiteAchievements.award(event.getEntity(),"theEnd2");
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!player.isAlive())return;
        boolean plate=true,adamantium=true;
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
            var a=MiteArmorContent.definition(player.getItemBySlot(slot).getItem());
            if(a!=null&&a.material().equals("leather"))MiteAchievements.award(player,"wearLeather");
            // Source checks material, including the matching metal's chain armor.
            if(a==null||!Set.of("copper","silver","gold","iron","mithril","adamantium","ancient_metal").contains(a.material()))plate=false;
            if(a==null||!a.material().equals("adamantium"))adamantium=false;
        }
        if(plate)MiteAchievements.award(player,"wearAllPlateArmor");
        if(adamantium)MiteAchievements.award(player,"wearAllAdamantiumPlateArmor");
        if(player.tickCount%10!=0)return;
        if(player.level().dimension().equals(Level.OVERWORLD)){
            BlockPos spawn=player.server.overworld().getSharedSpawnPos();
            double dx=player.getX()-spawn.getX(),dz=player.getZ()-spawn.getZ(),d=dx*dx+dz*dz;
            if(d>=1E8&&d<4E8)MiteAchievements.award(player,"explorer");
        }
        if(player.getVehicle() instanceof AbstractMinecart){
            var tag=player.getPersistentData();BlockPos pos=player.blockPosition();
            if(!tag.contains("mite_rail_origin"))tag.putLong("mite_rail_origin",pos.asLong());
            else if(BlockPos.of(tag.getLong("mite_rail_origin")).distSqr(pos)>=1E6)MiteAchievements.award(player,"onARail");
        }else player.getPersistentData().remove("mite_rail_origin");
        if(player.getVehicle() instanceof Boat){
            BlockPos pos=player.blockPosition();boolean water=true;
            for(BlockPos p:BlockPos.betweenClosed(pos.offset(-8,-4,-8),pos.offset(8,-1,8))){
                if(!player.level().hasChunkAt(p)||!player.level().getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)){water=false;break;}
            }
            if(water)MiteAchievements.award(player,"seaworthy");
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void read(PlayerInteractEvent.RightClickItem event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player)||!event.getItemStack().is(Items.WRITTEN_BOOK))return;
        ReferencedBooks.read(player,event.getItemStack());
    }
}
