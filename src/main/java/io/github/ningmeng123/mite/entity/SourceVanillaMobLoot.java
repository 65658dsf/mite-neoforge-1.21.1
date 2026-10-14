package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.R196VanillaMobRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/** Actual source few-items draws, independent of modern loot tables and their additive modifiers. @author NingMeng123 */
public final class SourceVanillaMobLoot {
    public static final String STATE = "mite_native_loot_state_031";
    private static final Item[] ZOMBIE_NUGGETS = {item("mite:copper_nugget"),item("mite:silver_nugget"),Items.GOLD_NUGGET,Items.IRON_NUGGET};
    private static final String[] WITCH_POOL = {"minecraft:glowstone_dust","minecraft:sugar","minecraft:redstone","minecraft:spider_eye",
            "minecraft:glass_bottle","minecraft:gunpowder","minecraft:stick","minecraft:stick","mite:flint_knife","minecraft:iron_nugget",
            "minecraft:wheat_seeds","minecraft:pumpkin_seeds","minecraft:carrot","minecraft:potato","mite:onion",
            "minecraft:dandelion","minecraft:allium","minecraft:potion"};
    private static final Item[] DISCS = {Items.MUSIC_DISC_13,Items.MUSIC_DISC_CAT,Items.MUSIC_DISC_BLOCKS,Items.MUSIC_DISC_CHIRP,
            Items.MUSIC_DISC_FAR,Items.MUSIC_DISC_MALL,Items.MUSIC_DISC_MELLOHI,Items.MUSIC_DISC_STAL,Items.MUSIC_DISC_STRAD,
            Items.MUSIC_DISC_WARD,Items.MUSIC_DISC_11,Items.MUSIC_DISC_WAIT};
    private SourceVanillaMobLoot() {}
    public static Item item(String id) { var key=ResourceLocation.parse(id); if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Missing source loot "+key); return BuiltInRegistries.ITEM.get(key); }
    public static boolean managed(LivingEntity entity) { return entity instanceof Mob && !(entity instanceof R196Mob) && R196VanillaMobRules.managed(entity.getType()); }
    public static void initialize(Mob mob) {
        if (!managed(mob) || mob.level().isClientSide) return;
        var state=mob.getPersistentData().getCompound(STATE);
        EntityType<?> type=mob.getType(); var random=mob.getRandom();
        if (livestock(type) && !state.contains("food")) {
            if(mob instanceof net.minecraft.world.entity.animal.Cow cow && cow.hasData(io.github.ningmeng123.mite.registry.MiteAttachments.COW_FOOD)) {
                var previous=cow.getData(io.github.ningmeng123.mite.registry.MiteAttachments.COW_FOOD);
                state.putFloat("food",previous.food());state.putFloat("water",previous.water());state.putFloat("freedom",previous.freedom());
            } else { state.putFloat("food",.8F+random.nextFloat()*.2F); state.putFloat("water",.8F+random.nextFloat()*.2F); state.putFloat("freedom",.8F+random.nextFloat()*.2F); }
        }
        if (type==EntityType.CHICKEN && !state.contains("feathers")) { int maximum=random.nextInt(2)+1; if (maximum>1 && random.nextInt(2)==0) --maximum; state.putInt("maximum_feathers",maximum); state.putInt("feathers",maximum); }
        if ((type==EntityType.SPIDER || type==EntityType.CAVE_SPIDER) && !state.contains("webs")) { int stock=random.nextInt(4); if (stock>0 && type!=EntityType.CAVE_SPIDER) --stock; state.putInt("webs",stock); }
        if (!state.isEmpty()) mob.getPersistentData().put(STATE,state);
    }
    public static boolean livestock(EntityType<?> type) { return type==EntityType.COW || type==EntityType.MOOSHROOM || type==EntityType.PIG || type==EntityType.SHEEP || type==EntityType.CHICKEN; }
    /** The independent agriculture system can update the same saved source variables without changing loot formulas. */
    public static void setLivestockNeeds(Mob mob,float food,float water,float freedom) {
        io.github.ningmeng123.mite.entity.animal.Livestock032.needs(mob,new io.github.ningmeng123.mite.entity.animal.LivestockRules032.Needs(food,water,freedom));
    }
    public static boolean well(Mob mob) { initialize(mob); var s=mob.getPersistentData().getCompound(STATE); return Math.min(s.getFloat("food"),Math.min(s.getFloat("water"),s.getFloat("freedom")))>=.25F; }
    public static int feathers(Mob mob) { initialize(mob); return mob.getPersistentData().getCompound(STATE).getInt("feathers"); }
    public static void setFeathers(Mob mob,int feathers) { initialize(mob); var s=mob.getPersistentData().getCompound(STATE); s.putInt("feathers",Math.clamp(feathers,0,s.getInt("maximum_feathers"))); mob.getPersistentData().put(STATE,s); }
    public static int webStock(Mob mob) { initialize(mob); return mob.getPersistentData().getCompound(STATE).getInt("webs"); }
    public static void setWebStock(Mob mob,int count) { initialize(mob); var s=mob.getPersistentData().getCompound(STATE); s.putInt("webs",Math.clamp(count,0,3)); mob.getPersistentData().put(STATE,s); }
    public static int looting(Mob mob,DamageSource source) { return enchantment(mob,source,Enchantments.LOOTING.location()); }
    public static int butchering(Mob mob,DamageSource source) { return enchantment(mob,source,ResourceLocation.fromNamespaceAndPath("mite","butchering")); }
    private static int enchantment(Mob mob,DamageSource source,ResourceLocation id) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) return 0;
        var holder=mob.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(id);
        return holder.map(value -> Math.max(0,EnchantmentHelper.getItemEnchantmentLevel(value,attacker.getMainHandItem()))).orElse(0);
    }
    private static boolean massive(Mob mob) { return mob.getPersistentData().getBoolean("mite_massive_fall_031"); }
    private static int reduced(Mob mob,int count,boolean recent) { return count>0 && !recent ? count-mob.getRandom().nextInt(count+1) : count; }
    /** One source drop call per item preserves both source random ordering and the low stack limits. */
    public static ItemEntity emit(Mob mob,ItemStack stack,float yOffset) {
        ItemEntity entity=mob.spawnAtLocation(stack,yOffset);
        if (entity!=null && mob.isOnFire()) entity.igniteForSeconds(mob.getRandom().nextInt(7)+2);
        return entity;
    }
    private static void emit(Mob mob,Item item,int count) { for(int i=0;i<count;i++) emit(mob,new ItemStack(item),mob.getBbHeight()*.5F); }
    /** Six yp/zp getter results were executed under the read-only R196 JVM guard, including baked splash durations. */
    public static ItemStack witchPotion(int variant) {
        var effect=switch(variant) { case 0 -> new MobEffectInstance(MobEffects.FIRE_RESISTANCE,3600);
            case 1 -> new MobEffectInstance(MobEffects.HEAL,1); case 2 -> new MobEffectInstance(MobEffects.POISON,676);
            case 3 -> new MobEffectInstance(MobEffects.WEAKNESS,1351); case 4 -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,1351);
            case 5 -> new MobEffectInstance(MobEffects.HARM,1); default -> throw new IllegalArgumentException("Source witch potion "+variant); };
        ItemStack stack=new ItemStack(variant<2?Items.POTION:Items.SPLASH_POTION);
        stack.set(DataComponents.POTION_CONTENTS,new PotionContents(Optional.empty(),Optional.empty(),List.of(effect)));
        String[] names={"fire_resistance","healing","poison","weakness","slowness","harming"};
        stack.set(DataComponents.ITEM_NAME,Component.translatable("item.minecraft."+(variant<2?"potion":"splash_potion")+".effect."+names[variant]));
        return stack;
    }
    public static void fewItems(Mob mob,DamageSource source,boolean recent) {
        initialize(mob);
        if (mob.isBaby() || massive(mob) && mob.getRandom().nextFloat()>=.1F) return;
        var type=mob.getType(); var random=mob.getRandom(); int looting=looting(mob,source),butchering=butchering(mob,source);
        if (type==EntityType.ZOMBIE || type==EntityType.ZOMBIE_VILLAGER) {
            if (random.nextFloat()<(recent ?.5F:.25F)) emit(mob,Items.ROTTEN_FLESH,1);
            boolean villager=type==EntityType.ZOMBIE_VILLAGER;
            if (recent && !massive(mob) && random.nextInt(villager?50:200)<5+looting*2) {
                Item[] candidates=villager ? new Item[]{Items.WHEAT_SEEDS,Items.PUMPKIN_SEEDS,Items.MELON_SEEDS,Items.CARROT,Items.POTATO,item("mite:onion")} : ZOMBIE_NUGGETS;
                emit(mob,candidates[random.nextInt(candidates.length)],1);
            }
        } else if (type==EntityType.ZOMBIFIED_PIGLIN) {
            if (random.nextFloat()<(recent ?.5F:.25F)) emit(mob,Items.ROTTEN_FLESH,1);
            emit(mob,Items.GOLD_NUGGET,random.nextInt(2+looting));
            if (recent && !massive(mob) && random.nextInt(200)<5+looting*2) emit(mob,Items.GOLD_INGOT,1);
        } else if (type==EntityType.SKELETON || type==EntityType.WITHER_SKELETON) {
            int skeletonType=type==EntityType.WITHER_SKELETON?1:((OrdinarySkeletonState)mob).mite$sourceSkeletonType();
            if (skeletonType==1) {
                emit(mob,Items.COAL,reduced(mob,random.nextInt(3+looting)-1,recent));
                if (recent && !massive(mob) && random.nextInt(200)<5+looting*2) emit(mob,new ItemStack(Items.WITHER_SKELETON_SKULL),0F);
            } else if (skeletonType!=2) emit(mob,item("mite:rusted_iron_arrow"),reduced(mob,random.nextInt(2+looting),recent));
            emit(mob,Items.BONE,reduced(mob,random.nextInt(3),recent));
        } else if (type==EntityType.COW || type==EntityType.MOOSHROOM) {
            emit(mob,Items.LEATHER,random.nextInt(3)+1);
            if (well(mob)) { int meat=random.nextInt(3)+1+random.nextInt(1+butchering); if(meat==1 && random.nextInt(2)==0) meat++; emit(mob,mob.isOnFire()?Items.COOKED_BEEF:Items.BEEF,meat); }
        } else if (type==EntityType.PIG) {
            // The native installed saddle return is kept in dropEquipment, once, outside few-items suppression.
            if(well(mob)) emit(mob,mob.isOnFire()?Items.COOKED_PORKCHOP:Items.PORKCHOP,random.nextInt(3)+1+random.nextInt(1+butchering));
        } else if (type==EntityType.SHEEP) {
            Sheep sheep=(Sheep)mob;
            if(!sheep.isSheared() && !mob.isOnFire() && random.nextInt(2)==0) emit(mob,item("minecraft:"+sheep.getColor().getName()+"_wool"),1);
            if(well(mob)) emit(mob,mob.isOnFire()?Items.COOKED_MUTTON:Items.MUTTON,Math.max(1,random.nextInt(2)+random.nextInt(1+butchering)));
            if(random.nextInt(2)==0) emit(mob,Items.LEATHER,1);
        } else if (type==EntityType.CHICKEN) {
            if(mob.isOnFire()) { if(well(mob)) emit(mob,Items.COOKED_CHICKEN,1); }
            else { emit(mob,Items.FEATHER,feathers(mob)); if(well(mob)) emit(mob,Items.CHICKEN,1); }
        } else if (type==EntityType.HORSE || type==EntityType.DONKEY || type==EntityType.MULE || type==EntityType.ZOMBIE_HORSE || type==EntityType.SKELETON_HORSE) {
            int count=random.nextInt(3)+1;
            for(int i=0;i<count;i++) { boolean blank=random.nextInt(4)==0; Item skin=type==EntityType.SKELETON_HORSE?Items.BONE:type==EntityType.ZOMBIE_HORSE?(blank?null:Items.ROTTEN_FLESH):Items.LEATHER; if(skin!=null) emit(mob,skin,1); }
            if(type!=EntityType.ZOMBIE_HORSE && type!=EntityType.SKELETON_HORSE) { int meat=1+random.nextInt(1+butchering); if(type==EntityType.HORSE) meat+=random.nextInt(2); emit(mob,mob.isOnFire()?Items.COOKED_BEEF:Items.BEEF,meat); }
        } else if (type==EntityType.SQUID) { if(recent) emit(mob,new ItemStack(Items.INK_SAC),0F); }
        else if(type==EntityType.WOLF) emit(mob,Items.LEATHER,1);
        else if(type==EntityType.OCELOT || type==EntityType.CAT) { /* Source rx explicitly overrides few-items with no draws. */ }
        else if(type==EntityType.SPIDER || type==EntityType.CAVE_SPIDER) {
            if(recent) { emit(mob,Items.STRING,webStock(mob)); setWebStock(mob,0); if(random.nextInt(3)==0 || random.nextInt(1+butchering)>0) emit(mob,Items.SPIDER_EYE,1); }
        } else if(type==EntityType.GHAST) {
            int tears=random.nextInt(2); if(source.getDirectEntity() instanceof LargeFireball && source.getEntity() instanceof Player && tears<1) tears=1;
            emit(mob,Items.GHAST_TEAR,tears); emit(mob,Items.GUNPOWDER,random.nextInt(3));
        } else if(type==EntityType.ENDERMAN) emit(mob,Items.ENDER_PEARL,random.nextInt(2+looting));
        else if(type==EntityType.BLAZE) { if(recent) emit(mob,Items.BLAZE_ROD,random.nextInt(2+looting)); }
        else if(type==EntityType.WITHER) { ItemEntity star=emit(mob,new ItemStack(Items.NETHER_STAR),mob.getBbHeight()*.5F); if(star!=null) star.setExtendedLifetime(); }
        else if(type==EntityType.SNOW_GOLEM) emit(mob,Items.SNOWBALL,random.nextInt(8+looting*4));
        else if(type==EntityType.IRON_GOLEM) { emit(mob,Blocks.POPPY.asItem(),random.nextInt(3)); if(recent && !massive(mob)) emit(mob,Items.IRON_NUGGET,3+random.nextInt(3+looting)); }
        else if(type==EntityType.WITCH) {
            int count=random.nextInt(5+looting)+1;
            for(int i=0;i<count;i++) {
                String id=WITCH_POOL[random.nextInt(WITCH_POOL.length)];
                if(id.equals("minecraft:potion")) emit(mob,witchPotion(random.nextInt(6)),mob.getBbHeight()*.5F); else emit(mob,item(id),1);
            }
        } else {
            // The actual og default still consumes its few-items count when its item getter returns zero.
            int count=random.nextInt(3); if(looting>0) count+=random.nextInt(looting+1); count=reduced(mob,count,recent);
            if(type==EntityType.CREEPER) for(int i=0;i<count;i++) if(recent || random.nextInt(3)==0) emit(mob,Items.GUNPOWDER,1);
        }
    }
    public static void skeletonKilledCreeperRecord(Mob mob,DamageSource source) {
        if(mob.getType()==EntityType.CREEPER && source.getEntity() instanceof AbstractSkeleton) emit(mob,DISCS[mob.getRandom().nextInt(DISCS.length)],1);
    }
    /** Modern charged-head production is retained separately from source scarce metal and meat rewards. */
    public static void chargedHead(Mob mob,DamageSource source) {
        if(source.getEntity()==mob || !(source.getEntity() instanceof Creeper killer) || !killer.canDropMobsSkull()) return;
        Item head=mob.getType()==EntityType.CREEPER?Items.CREEPER_HEAD:mob.getType()==EntityType.WITHER_SKELETON?Items.WITHER_SKELETON_SKULL
                : mob.getType()==EntityType.ZOMBIE || mob.getType()==EntityType.ZOMBIE_VILLAGER?Items.ZOMBIE_HEAD:null;
        if(head!=null) { killer.increaseDroppedSkulls(); mob.spawnAtLocation(head); }
    }
}
