package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.rules.R196MobRules;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.util.Mth;

/** Four actual gelatinous subtypes share source size, contact, corrosion, feeding and split behavior. @author NingMeng123 */
public final class R196Cube extends Slime implements R196Mob, CubeState {
    private static final EntityDataAccessor<Boolean> FEEDING=SynchedEntityData.defineId(R196Cube.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CLIMBING=SynchedEntityData.defineId(R196Cube.class,EntityDataSerializers.BOOLEAN);
    private final MobKind kind;
    private final MobCorrosion corrosion=new MobCorrosion();
    private int jumpDelay;
    private ItemEntity food;
    public R196Cube(EntityType<? extends Slime> type,Level level,MobKind kind) {
        super(type,level); this.kind=kind;
        this.moveControl=new SourceMoveControl(this);
        setSize(1<<random.nextInt(3),true);
        jumpDelay=40+random.nextInt(81);
    }
    @Override public MobKind miteKind() { return kind; }
    @Override public boolean biologicallyAlive() { return true; }
    @Override public boolean healsWithTime() { return true; }
    @Override public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) { return false; }
    @Override public void igniteForTicks(int ticks) { }
    public boolean acidic() { return kind==MobKind.OOZE || kind==MobKind.PUDDING; }
    public boolean isFeeding() { return entityData.get(FEEDING); }
    public MobCorrosion corrosion() { return corrosion; }
    public static int damageMultiplier(MobKind kind) { return switch(kind) { case JELLY->2; case BLOB,OOZE->3; case PUDDING->4; default->1; }; }
    public static double contactRangeSquared(int size) { float reach=size*.6F; reach*=size==1 ? 1.1F : size==2 ? .9F : .7F; return reach*reach; }
    public static float vanillaNaturalDefense(Slime cube,DamageSource source) { return cube instanceof net.minecraft.world.entity.monster.MagmaCube ? cube.getSize()*2F : 0F; }
    public static boolean vanillaImmune(Slime cube,DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (MobCombat.pepsin(source) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.DROWN)) return true;
        if (!(cube instanceof net.minecraft.world.entity.monster.MagmaCube)) return false;
        if (MobCombat.acid(source) || MobCombat.fire(source) || source.is(DamageTypes.LAVA)) return true;
        if (MobCombat.water(source) || source.getDirectEntity() instanceof Snowball || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return false;
        ItemStack tool=MobCombat.weapon(source);
        return tool==null || !tool.has(net.minecraft.core.component.DataComponents.TOOL) || tool.getDestroySpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())<=1F;
    }
    public static int vanillaContactDamage(Slime cube) {
        return cube.getSize() * (cube instanceof net.minecraft.world.entity.monster.MagmaCube ? 2 : 1);
    }
    public static void vanillaContact(Slime cube,LivingEntity target) {
        boolean magma=cube instanceof net.minecraft.world.entity.monster.MagmaCube;
        if (cube.level().isClientSide || !cube.isAlive() || cube.distanceToSqr(target)>contactRangeSquared(cube.getSize()) || !cube.hasLineOfSight(target)) return;
        if (!(target instanceof Player) && (magma || !(target instanceof Animal || target instanceof Villager))) return;
        if (!MonsterHunting.mayAttackPrey(cube, target)) return;
        if (!magma && target instanceof Player player) player.makeStuckInBlock(net.minecraft.world.level.block.Blocks.SLIME_BLOCK.defaultBlockState(),new net.minecraft.world.phys.Vec3(.8,1,.8));
        int damage=vanillaContactDamage(cube);
        if (!target.hurt(cube.damageSources().mobAttack(cube),damage) || !target.isAlive()) return;
        if (!magma && target instanceof Player player) MobCorrosion.inventory(player,false,cube.getSize(),damage);
        if (magma && cube.getRandom().nextInt(10)<cube.getSize()*2) target.igniteForSeconds(cube.getSize()*3);
    }
    public static void sourceDrops(Slime cube,DamageSource source,boolean recent) {
        int looting = source.getEntity() instanceof LivingEntity killer ? io.github.ningmeng123.mite.enchantment.R196Enchantments.level(killer.getMainHandItem(), killer.level(), "minecraft:looting") : 0;
        int count;
        Item item;
        if (cube instanceof net.minecraft.world.entity.monster.MagmaCube) {
            if (cube.getSize()<=1) return;
            count=cube.getRandom().nextInt(4+looting)-2; item=Items.MAGMA_CREAM;
        } else {
            if (cube.getSize()!=1) return;
            count=cube.getRandom().nextInt(3);
            if (looting>0) count+=cube.getRandom().nextInt(looting+1);
            if (count>0 && !recent) count-=cube.getRandom().nextInt(count+1);
            item=cube instanceof R196Cube variant ? sphere(variant.miteKind()) : Items.SLIME_BALL;
        }
        for (int i=0;i<count;i++) cube.spawnAtLocation(item);
    }
    public static void magmaContactBlocks(net.minecraft.world.entity.monster.MagmaCube cube) {
        if (!(cube.level() instanceof ServerLevel level)) return;
        var box=cube.getBoundingBox().inflate(.01);
        for (var cursor:net.minecraft.core.BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX),net.minecraft.util.Mth.floor(box.minY),net.minecraft.util.Mth.floor(box.minZ),net.minecraft.util.Mth.floor(box.maxX),net.minecraft.util.Mth.floor(box.maxY),net.minecraft.util.Mth.floor(box.maxZ))) {
            var pos=cursor.immutable(); var state=level.getBlockState(pos);
            if (state.getShape(level,pos).isEmpty() || !state.getShape(level,pos).bounds().move(pos).intersects(box)) continue;
            if (state.is(net.minecraft.world.level.block.Blocks.COBWEB)) { level.destroyBlock(pos,true,cube); continue; }
            if (state.is(net.minecraft.tags.BlockTags.FLOWERS) || state.is(net.minecraft.tags.BlockTags.SAPLINGS) || state.is(net.minecraft.tags.BlockTags.CROPS)
                    || state.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS) || state.is(net.minecraft.world.level.block.Blocks.TALL_GRASS)
                    || state.is(net.minecraft.world.level.block.Blocks.FERN) || state.is(net.minecraft.world.level.block.Blocks.LARGE_FERN)
                    || state.is(net.minecraft.world.level.block.Blocks.DEAD_BUSH) || state.is(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM)
                    || state.is(net.minecraft.world.level.block.Blocks.RED_MUSHROOM) || state.is(net.minecraft.world.level.block.Blocks.SUGAR_CANE)) { level.removeBlock(pos,false); continue; }
            if (state.is(net.minecraft.world.level.block.Blocks.ICE) || state.is(net.minecraft.world.level.block.Blocks.FROSTED_ICE)
                    || state.is(net.minecraft.world.level.block.Blocks.PACKED_ICE) || state.is(net.minecraft.world.level.block.Blocks.BLUE_ICE)
                    || state.is(net.minecraft.world.level.block.Blocks.SNOW) || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)) {
                level.setBlockAndUpdate(pos,level.dimensionType().ultraWarm() || state.is(net.minecraft.world.level.block.Blocks.SNOW) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
                cube.hurt(MobCombat.source(cube,"melt"),1F);
            }
        }
        if (cube.onGround()) {
            int y=net.minecraft.util.Mth.floor(box.minY-.01);
            for (var pos:net.minecraft.core.BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX),y,net.minecraft.util.Mth.floor(box.minZ),net.minecraft.util.Mth.floor(box.maxX),y,net.minecraft.util.Mth.floor(box.maxZ)))
                io.github.ningmeng123.mite.ranged.SphereCorrosion.contact(level,pos,net.minecraft.core.Direction.UP,true);
        }
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(FEEDING,false); builder.define(CLIMBING,false); }
    @Override protected void registerGoals() {
        targetSelector.addGoal(1,new Goal() {
            private int lastHit;
            { setFlags(java.util.EnumSet.of(Flag.TARGET)); }
            @Override public boolean canUse() { return getLastHurtByMob()!=null && getLastHurtByMob().isAlive() && getLastHurtByMobTimestamp()!=lastHit; }
            @Override public void start() { lastHit=getLastHurtByMobTimestamp(); setTarget(getLastHurtByMob()); }
            @Override public boolean canContinueToUse() { return false; }
        });
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true) {
            @Override protected double getFollowDistance() { return kind==MobKind.OOZE ? 32 : 16; }
            @Override protected void findTarget() { targetConditions.range(getFollowDistance()); super.findTarget(); }
        });
        targetSelector.addGoal(3,new MonsterHunting.PreyGoal<>(this,Animal.class,true) {
            @Override protected double getFollowDistance() { return 8; }
        });
        targetSelector.addGoal(3,new MonsterHunting.PreyGoal<>(this,Villager.class,true) {
            @Override protected double getFollowDistance() { return 8; }
        });
    }
    @Override public void setSize(int size,boolean heal) {
        int safe=Math.max(1,Math.min(kind==MobKind.OOZE ? 2 : 127,size));
        super.setSize(safe,heal);
        if (kind!=null) getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(R196MobRules.entry(kind).movementSpeed());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(safe*(kind==null ? 1 : damageMultiplier(kind)));
        xpReward=safe*((kind==null ? 1 : damageMultiplier(kind))+(acidic() ? 1 : 0));
    }
    @Override public EntityDimensions getDefaultDimensions(Pose pose) { return EntityDimensions.scalable(.5F*getSize(),.5F*getSize()).withEyeHeight(.325F*getSize()); }
    @Override protected boolean isDealsDamage() { return isEffectiveAi(); }
    @Override protected int getJumpDelay() { return getTarget()!=null ? 10 : 40+random.nextInt(81); }
    @Override protected float getSoundVolume() { return .2F*getSize(); }
    @Override public boolean onClimbable() { return kind==MobKind.OOZE && entityData.get(CLIMBING); }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (super.isInvulnerableTo(source) || MobCombat.pepsin(source) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.DROWN)) return true;
        if (kind==MobKind.PUDDING && MobCombat.acid(source)) return true;
        boolean snowball=source.getDirectEntity() instanceof Snowball;
        if (snowball && source.getEntity() instanceof Player && acidic()) return true;
        if (kind==MobKind.OOZE) return !source.is(DamageTypes.LAVA) && !MobCombat.magic(source) && !snowball;
        if (kind==MobKind.PUDDING) return !MobCombat.fire(source) && !source.is(DamageTypes.LAVA) && !MobCombat.magic(source) && !snowball;
        return false;
    }
    @Override protected void dealDamage(LivingEntity target) {
        if (level().isClientSide || !isAlive() || distanceToSqr(target)>contactRangeSquared(getSize()) || !hasLineOfSight(target)) return;
        if (!(target instanceof Player || target instanceof Animal || target instanceof Villager)) return;
        if (!MonsterHunting.mayAttackPrey(this, target)) return;
        if (target instanceof Player player) player.makeStuckInBlock(net.minecraft.world.level.block.Blocks.SLIME_BLOCK.defaultBlockState(),new net.minecraft.world.phys.Vec3(.8,1,.8));
        float health=target.getHealth();
        if (!target.hurt(damageSources().mobAttack(this),getAttackDamage()) || !target.isAlive()) return;
        if (target instanceof Player player) MobCorrosion.inventory(player,acidic(),getSize(),getSize()*damageMultiplier(kind));
        if (kind==MobKind.BLOB && target.getHealth()<health) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,50,5));
    }
    @Override public void push(Entity target) {
        super.push(target);
        if (target instanceof Animal || target instanceof Villager) dealDamage((LivingEntity)target);
    }
    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        corrosion.advanceCountdowns();
        corrosion.contact(this);
        if (getTarget() instanceof Player) corrosion.clearFeeding();
        entityData.set(FEEDING,corrosion.isFeeding());
        entityData.set(CLIMBING,kind==MobKind.OOZE && horizontalCollision);
        if (onClimbable()) setDeltaMovement(getDeltaMovement().x,.2,getDeltaMovement().z);
        if (MobCombat.ticksWithOffset(this)%20==0 && getTarget()==null) {
            food=level().getEntitiesOfClass(ItemEntity.class,getBoundingBox().inflate(8),i->i.isAlive() && MobCorrosion.susceptible(i.getItem(),acidic()) && hasLineOfSight(i))
                    .stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        }
    }
    @Override public void remove(RemovalReason reason) {
        if (isOnFire() && isDeadOrDying() && getSize()>1) {
            int size=getSize(); super.setSize(1,false); super.remove(reason); super.setSize(size,false);
        } else super.remove(reason);
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit) {
        sourceDrops(this,source,recentlyHit);
    }
    public static Item sphere(MobKind kind) { return switch(kind) { case JELLY->MiteWeaponContent.OCHRE_JELLY.get(); case BLOB->MiteWeaponContent.CRIMSON_BLOB.get(); case OOZE->MiteWeaponContent.GRAY_OOZE.get(); case PUDDING->MiteWeaponContent.BLACK_PUDDING.get(); default->Items.SLIME_BALL; }; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); corrosion.save(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); corrosion.load(tag); }
    @Nullable @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,MobSpawnType spawn,@Nullable SpawnGroupData group) {
        // Native finalizeSpawn changes the size depending on difficulty; source uses an unweighted draw.
        SpawnGroupData result=super.finalizeSpawn(level,difficulty,spawn,group);
        setSize(1<<random.nextInt(3),true);
        return result;
    }
    private static final class SourceMoveControl extends MoveControl {
        private final R196Cube cube;
        SourceMoveControl(R196Cube cube) { super(cube); this.cube=cube; }
        @Override public void tick() {
            Entity target=cube.getTarget();
            if (target==null && cube.food!=null && cube.food.isAlive()) target=cube.food;
            if (cube.isFeeding() && !(target instanceof Player) && cube.onGround()) { cube.setSpeed(0); cube.xxa=0; cube.zza=0; return; }
            if (target!=null) cube.lookAt(target,10,20);
            else if (cube.jumpDelay<=1) cube.setYRot(cube.getYRot()+cube.random.nextFloat()*120-60);
            cube.yBodyRot=cube.getYRot();
            boolean crawl=cube.kind==MobKind.OOZE || target instanceof ItemEntity && target.getBoundingBox().inflate(.5,0,.5).intersects(cube.getBoundingBox())
                    || !cube.level().noCollision(cube,cube.getBoundingBox().move(0,.25,0));
            if (crawl) {
                // Mob.setSpeed also writes forward input; keep the source acceleration and input separate.
                cube.setSpeed(.1F);
                cube.setXxa(0F);
                cube.setZza(target==null ? .15F : .25F);
                return;
            }
            if (cube.isInWater() || cube.isInLava()) { cube.getJumpControl().jump(); cube.setSpeed(.1F*cube.getSize()); return; }
            if (cube.onGround()) {
                if (target!=null && cube.jumpDelay>10) cube.jumpDelay=10;
                if (--cube.jumpDelay<=0) { cube.jumpDelay=cube.getJumpDelay(); cube.getJumpControl().jump(); cube.setSpeed(.1F*cube.getSize()); }
                else { cube.xxa=0; cube.zza=0; cube.setSpeed(0); }
            } else cube.setSpeed(.1F*cube.getSize());
        }
    }
}
