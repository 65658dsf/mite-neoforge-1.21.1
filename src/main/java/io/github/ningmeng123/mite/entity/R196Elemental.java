package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.R196MobRules;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** R196 material bodies and irreversible clay firing; synchronized type is the renderer's authority. @author NingMeng123 */
public class R196Elemental extends Monster implements R196Mob {
    private static final EntityDataAccessor<Integer> MATERIAL = SynchedEntityData.defineId(R196Elemental.class, EntityDataSerializers.INT);
    private static final int MAGMA = 256;
    private final MobKind kind;
    private int heat;
    private int fizzDelay;

    public R196Elemental(EntityType<? extends Monster> type, Level level, MobKind kind) {
        super(type, level);
        if (kind != MobKind.EARTH_ELEMENTAL && kind != MobKind.CLAY_GOLEM) throw new IllegalArgumentException("Elemental kind " + kind);
        this.kind = kind;
        this.xpReward = R196MobRules.entry(kind).experience();
        setMaterial(kind == MobKind.CLAY_GOLEM ? Blocks.CLAY : Blocks.STONE, false);
    }
    @Override public MobKind miteKind() { return kind; }
    @Override public boolean biologicallyAlive() { return false; }
    @Override public boolean healsWithTime() { return true; }
    @Override public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) { return false; }
    public boolean isClay() { return kind == MobKind.CLAY_GOLEM; }
    public boolean isNormalClay() { return isClay() && !isHardenedClay(); }
    public int getBlockHarvestLevel() { return isClay() ? isHardenedClay() ? 1 : 0 : materialBlock() == Blocks.OBSIDIAN ? 3 : 2; }
    public Block sourceMaterial() { return materialBlock(); }
    public int heat() { return heat; }
    public boolean isMagma() { return !isClay() && (level().isClientSide ? (entityData.get(MATERIAL) & MAGMA) != 0 : heat >= 100); }
    public boolean isHardenedClay() { return isClay() && (materialBlock() == Blocks.TERRACOTTA || heat >= 100); }
    public Block materialBlock() {
        return switch (entityData.get(MATERIAL) & 255) {
            case 49 -> Blocks.OBSIDIAN; case 87 -> Blocks.NETHERRACK; case 121 -> Blocks.END_STONE;
            case 82 -> Blocks.CLAY; case 172 -> Blocks.TERRACOTTA; default -> Blocks.STONE;
        };
    }
    @Override public float naturalDefense(DamageSource source) { return isClay() ? isHardenedClay() ? 2F : 0F : 4F; }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(MATERIAL, 1); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new R196DiggerGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Villager.class, false));
    }
    public void setMaterial(Block block, boolean heated) {
        int value = isClay() ? block == Blocks.TERRACOTTA || heated ? 172 : 82
                : block == Blocks.OBSIDIAN ? 49 : block == Blocks.NETHERRACK ? 87 : block == Blocks.END_STONE ? 121 : 1;
        entityData.set(MATERIAL, value);
        if (heated && !isClay()) convertToMagma();
    }
    public void convertToMagma() {
        heat = 1000;
        entityData.set(MATERIAL, isClay() ? 172 : entityData.get(MATERIAL) | MAGMA);
        smoke(false);
    }
    public void quench() {
        boolean hot = isMagma();
        heat = 0;
        if (!isClay()) entityData.set(MATERIAL, entityData.get(MATERIAL) & ~MAGMA);
        if (hot) smoke(true);
    }
    private void smoke(boolean steam) {
        if (level() instanceof ServerLevel server) server.sendParticles(steam ? ParticleTypes.CLOUD : ParticleTypes.SMOKE,
                getX(), getY()+getBbHeight()*.5, getZ(), 12, .4, .5, .4, .02);
    }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (super.isInvulnerableTo(source)) return true;
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.DROWN)) return true;
        if (isClay() && !isHardenedClay()) return false;
        if (source.is(DamageTypes.FALL) || source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        if (source.getDirectEntity() instanceof IronGolem) return false;
        ItemStack tool = MobCombat.weapon(source);
        return tool == null || !tool.has(DataComponents.TOOL) || tool.getDestroySpeed(materialBlock().defaultBlockState()) <= 1F;
    }
    @Override public boolean doHurtTarget(Entity target) {
        float before = target instanceof LivingEntity living ? living.getHealth() : 0;
        boolean hit = super.doHurtTarget(target);
        if (hit && target.isAlive() && target instanceof LivingEntity living && living.getHealth() < before && isMagma() && random.nextFloat() < .4F)
            target.igniteForSeconds(1+random.nextInt(8));
        return hit;
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) && amount>=getMaxHealth()*.5F)
            getPersistentData().putBoolean("mite_massive_fall_031",true);
        return super.hurt(source, amount);
    }
    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (isInWater()) { quench(); return; }
        boolean firing = isInLava() || isClay() && level().getBlockState(blockPosition()).is(Blocks.FIRE);
        if (firing) {
            if (heat < 1000 && ++heat == 100) convertToMagma();
        } else if (heat > 0) {
            boolean wasMagma = isMagma();
            BlockPos feet = blockPosition();
            int cooling = 1 + (isInWaterOrRain() ? 1 : 0)
                    + (level().getBiome(feet).value().coldEnoughToSnow(feet) ? 2 : 0)
                    + (level().getBiome(feet.below()).value().coldEnoughToSnow(feet.below()) ? 1 : 0);
            heat = Math.max(0, heat-cooling);
            if (wasMagma && !isMagma()) quench();
        }
        if (isMagma() && isInWaterOrRain() && --fizzDelay <= 0) {
            playSound(SoundEvents.FIRE_EXTINGUISH, .7F, 1.6F+(random.nextFloat()-random.nextFloat())*.4F);
            fizzDelay = random.nextInt(7)+2;
        }
    }
    @Override public void thunderHit(ServerLevel level, LightningBolt bolt) { super.thunderHit(level, bolt); convertToMagma(); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("MiteMaterial", entityData.get(MATERIAL)); tag.putInt("MiteHeat", heat); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("MiteMaterial")) entityData.set(MATERIAL, tag.getInt("MiteMaterial"));
        heat = Math.max(0, Math.min(1000, tag.getInt("MiteHeat")));
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (getPersistentData().getBoolean("mite_massive_fall_031") && random.nextFloat()>=.1F) return;
        spawnAtLocation(materialBlock() == Blocks.STONE ? Blocks.COBBLESTONE : materialBlock());
    }
    @Nullable @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawn, @Nullable SpawnGroupData group) {
        setMaterial(level.getBlockState(blockPosition().below()).getBlock(), isInLava() || isClay() && level.getBlockState(blockPosition()).is(Blocks.FIRE));
        return super.finalizeSpawn(level, difficulty, spawn, group);
    }
}
