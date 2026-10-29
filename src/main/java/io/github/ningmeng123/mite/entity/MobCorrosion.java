package io.github.ningmeng123.mite.entity;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.mixin.ItemCorrosionAccess;
import io.github.ningmeng123.mite.ranged.SphereCorrosion;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import io.github.ningmeng123.mite.survival.FoodVessels;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;

/** Source timed dissolution, independent material susceptibility, and per-item equipment corrosion. @author NingMeng123 */
public final class MobCorrosion {
    private record Flags(boolean pepsin, boolean acid) {}
    private record Progress(String dimension, ResourceLocation block, int amount) {}
    private static final Map<ResourceLocation, Flags> ITEMS = new HashMap<>();
    private static final Map<Integer, Flags> LEGACY = new HashMap<>();
    static {
        try (var reader = new InputStreamReader(Objects.requireNonNull(MobCorrosion.class.getResourceAsStream("/mite/mob-chemical-items-031.json")), StandardCharsets.UTF_8)) {
            var object=JsonParser.parseReader(reader).getAsJsonObject();
            object.getAsJsonArray("items").forEach(e -> { var row=e.getAsJsonObject(); ITEMS.put(ResourceLocation.parse(row.get("item").getAsString()), new Flags(row.get("pepsin").getAsBoolean(), row.get("acid").getAsBoolean())); });
            object.getAsJsonArray("legacy_flags").forEach(e -> { var row=e.getAsJsonObject(); LEGACY.put(row.get("legacy_id").getAsInt(), new Flags(row.get("pepsin").getAsBoolean(), row.get("acid").getAsBoolean())); });
        } catch (Exception failure) { throw new IllegalStateException("Cannot load source chemical susceptibility",failure); }
    }
    private final Map<BlockPos, Progress> progress = new LinkedHashMap<>();
    private int blockFeeding;
    private int itemFeeding;
    public boolean isFeeding() { return blockFeeding>0 || itemFeeding>0; }
    public void clearFeeding() { blockFeeding=0; itemFeeding=0; }
    public void advanceCountdowns() { if (blockFeeding>0) --blockFeeding; if (itemFeeding>0) --itemFeeding; }
    public int progress(BlockPos pos) { Progress row=progress.get(pos); return row==null ? 0 : row.amount; }
    public static boolean susceptible(ItemStack stack, boolean acid) {
        if (stack.isEmpty()) return false;
        // ye exempts dyePowder (yc.aY) subtype4, which is lapis; this is distinct from gelatinousSphere (yc.aO).
        if (acid && stack.is(Items.LAPIS_LAZULI)) return false;
        Flags flags=ITEMS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (flags==null && stack.getItem() instanceof BlockItem item) {
            BlockState state=item.getBlock().defaultBlockState();
            int legacy=SphereCorrosion.legacyBlock(state);
            if (legacy<0 && (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOODEN_STAIRS))) legacy=5;
            flags=LEGACY.get(legacy);
        }
        return flags!=null && (acid ? flags.acid : flags.pepsin);
    }
    private static int wear(ItemStack stack,float amount) {
        return (int)(amount*(EquipmentDurability.armorProfile(stack)!=null ? 2F : EquipmentDurability.toolProfile(stack)!=null ? 100F : 1F));
    }
    public static boolean equipment(ItemStack stack,boolean acid,float amount,LivingEntity owner,EquipmentSlot slot) {
        boolean changed=damageEquipment(stack,acid,amount,owner,slot);
        if (changed) feedback(owner);
        return changed;
    }
    private static boolean damageEquipment(ItemStack stack,boolean acid,float amount,LivingEntity owner,EquipmentSlot slot) {
        if (!susceptible(stack,acid) || owner instanceof Player player && player.isCreative() || !(owner.level() instanceof ServerLevel server)) return false;
        if (stack.isDamageableItem()) {
            int damage=wear(stack,amount);
            if (damage<=0) return false;
            // Chemical damage may destroy worn armor. The null native owner avoids ordinary last-point armor protection.
            stack.hurtAndBreak(damage,server,null,item->owner.onEquippedItemBroken(item,slot));
            return true;
        }
        if (owner.getRandom().nextFloat()*10F>=amount) return false;
        ItemStack remainder=stack.getCraftingRemainingItem();
        stack.shrink(1);
        if (!remainder.isEmpty() && !susceptible(remainder,acid)) {
            if (owner instanceof Player player) { if (!player.getInventory().add(remainder)) player.drop(remainder,false); }
            else owner.spawnAtLocation(remainder);
        }
        return true;
    }
    public static void inventory(Player player,boolean acid,int size,int attack) {
        if (player.isCreative()) return;
        var inventory=player.getInventory();
        boolean changed=false;
        for (int slot=0;slot<inventory.getContainerSize();slot++) {
            ItemStack stack=inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            boolean armor=slot>=36 && slot<40;
            float chance=.05F*size*(armor ? 5 : 1);
            for (int item=0;item<stack.getCount();item++)
                if (player.getRandom().nextFloat()<chance) changed|=damageEquipment(stack,acid,attack,player,armor ? EquipmentSlot.values()[EquipmentSlot.FEET.ordinal()+slot-36] : slot==40 ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
        }
        if (changed) feedback(player);
    }
    /** EntityGelatinousCube/jv steam_with_hiss: one shared effect after actual inventory corrosion. */
    private static void feedback(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        double y=owner.getY()+owner.getBbHeight()*.5;
        level.playSound(null,owner.getX(),y,owner.getZ(),SoundEvents.FIRE_EXTINGUISH,owner.getSoundSource(),.7F,
                1.6F+(owner.getRandom().nextFloat()-owner.getRandom().nextFloat())*.4F);
        level.sendParticles(ParticleTypes.CLOUD,owner.getX(),y,owner.getZ(),5,owner.getBbWidth()*.3,owner.getBbHeight()*.25,owner.getBbWidth()*.3,.02);
    }
    public static boolean dropped(ItemEntity item,boolean acid,net.minecraft.world.entity.monster.Slime cube) {
        item.setPickUpDelay(Math.max(60,((ItemCorrosionAccess)(Object)item).mite$pickupDelay()));
        ItemStack stack=item.getItem();
        boolean lava=stack.is(Items.LAVA_BUCKET) || stack.getItem() instanceof MiteBucketItem vessel && vessel.contents()==MiteBucketItem.Contents.LAVA;
        if (lava) {
            String metal=stack.getItem() instanceof MiteBucketItem vessel ? vessel.metal() : "iron";
            cube.hurt(cube.damageSources().lava(),5);
            item.setItem(new ItemStack(MiteUtilityContent.get(metal+"_stone_bucket"),stack.getCount()));
            stack=item.getItem();
        } else if (FoodVessels.water(stack)) {
            ItemStack empty=FoodVessels.empty(stack);
            if (!empty.isEmpty()) { empty.setCount(stack.getCount()); item.setItem(empty); stack=empty; }
        }
        if (!susceptible(stack,acid)) return false;
        ItemCorrosionAccess health=(ItemCorrosionAccess)(Object)item;
        if (stack.isDamageableItem()) {
            int damage=Math.max(wear(stack,1F),1);
            stack.hurtAndBreak(damage,(ServerLevel)item.level(),null,ignored->{});
            if (stack.isEmpty()) { item.discard(); return false; }
            item.setItem(stack.copy());
            health.mite$chemicalHealth(Math.max(1,5*(stack.getMaxDamage()-stack.getDamageValue())/stack.getMaxDamage()));
        } else {
            health.mite$chemicalHealth(health.mite$chemicalHealth()-1);
            if (health.mite$chemicalHealth()<=0) {
                ItemStack remainder=stack.getCraftingRemainingItem();
                if (!remainder.isEmpty() && !susceptible(remainder,acid)) { remainder.setCount(stack.getCount()); item.setItem(remainder); health.mite$chemicalHealth(5); }
                else item.discard();
                return false;
            }
        }
        return true;
    }
    public void contact(R196Cube cube) { contact(cube,cube.acidic()); }
    public void contact(net.minecraft.world.entity.monster.Slime cube,boolean acid) {
        if (!(cube.level() instanceof ServerLevel level)) return;
        AABB box=cube.getBoundingBox().inflate(.01);
        String dimension=level.dimension().location().toString();
        progress.entrySet().removeIf(e->!e.getValue().dimension.equals(dimension) || cube.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(e.getKey()))>=256 || !BuiltInRegistries.BLOCK.getKey(level.getBlockState(e.getKey()).getBlock()).equals(e.getValue().block));
        boolean periodic=MobCombat.ticksWithOffset(cube)%20==0;
        int active=0;
        boolean timed=false;
        for (BlockPos candidate:BlockPos.betweenClosed(Mth.floor(box.minX),Mth.floor(box.minY),Mth.floor(box.minZ),Mth.floor(box.maxX),Mth.floor(box.maxY),Mth.floor(box.maxZ))) {
            BlockPos pos=candidate.immutable(); BlockState state=level.getBlockState(pos);
            if (state.isAir() || state.getShape(level,pos).isEmpty()) continue;
            if (!state.getShape(level,pos).bounds().move(pos).intersects(box)) continue;
            if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock) { CubeCropBlight.infect(level,pos); continue; }
            int ticks=SphereCorrosion.period(state,acid);
            if (state.is(Blocks.COBWEB)) { level.removeBlock(pos,false); cube.hurt(cube.damageSources().generic(),1F); continue; }
            if (ticks==0) { level.removeBlock(pos,false); continue; }
            if (ticks<=0 || active++>=64) continue;
            timed=true;
            if (!periodic) continue;
            ResourceLocation block=BuiltInRegistries.BLOCK.getKey(state.getBlock());
            int amount=progress(pos)+cube.getSize();
            if (amount>=ticks/20) { level.removeBlock(pos,false); progress.remove(pos); }
            else progress.put(pos,new Progress(dimension,block,amount));
        }
        blockFeeding=timed ? 20 : 0;
        if (acid && cube.onGround()) {
            int y=Mth.floor(box.minY-.01);
            for (BlockPos pos:BlockPos.betweenClosed(Mth.floor(box.minX),y,Mth.floor(box.minZ),Mth.floor(box.maxX),y,Mth.floor(box.maxZ)))
                SphereCorrosion.contact(level,pos,Direction.UP,true);
        }
        if (periodic) {
            boolean feeding=false;
            for (ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,cube.getBoundingBox(), e->e.isAlive())) feeding|=dropped(item,acid,cube);
            itemFeeding=feeding ? 20 : 0;
        }
    }
    public void save(CompoundTag tag) {
        ListTag rows=new ListTag();
        progress.forEach((pos,value)-> { CompoundTag row=new CompoundTag(); row.putLong("pos",pos.asLong()); row.putString("dimension",value.dimension); row.putString("block",value.block.toString()); row.putInt("amount",value.amount); rows.add(row); });
        tag.put("MiteCorrosion",rows);
    }
    public void load(CompoundTag tag) {
        progress.clear();
        ListTag rows=tag.getList("MiteCorrosion",Tag.TAG_COMPOUND);
        for (int i=0;i<rows.size();i++) { var row=rows.getCompound(i); var block=ResourceLocation.tryParse(row.getString("block")); if (block!=null && row.getInt("amount")>0) progress.put(BlockPos.of(row.getLong("pos")),new Progress(row.getString("dimension"),block,row.getInt("amount"))); }
    }
}
