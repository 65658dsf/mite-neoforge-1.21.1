package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.rules.R196MobRules;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/** Revenant retains source adult identity, equipment, reinforcement attributes and rare drops. @author NingMeng123 */
public final class R196Zombie extends Zombie implements R196Mob, PlayerTargetEyes {
    private static final EntityDataAccessor<Boolean> RED_EYES = SynchedEntityData.defineId(R196Zombie.class, EntityDataSerializers.BOOLEAN);
    private final MobKind kind;
    private boolean massiveFall;
    public R196Zombie(EntityType<? extends R196Zombie> type, Level level, MobKind kind) {
        super(type, level);
        if (kind != MobKind.REVENANT) throw new IllegalArgumentException("Not a R196 revenant");
        this.kind = kind; this.xpReward = R196MobRules.entry(kind).experience();
        randomizeReinforcementsChance();
    }
    @Override public MobKind miteKind() { return kind; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(RED_EYES, false);
    }
    @Override public boolean hasRedEyes() { return entityData.get(RED_EYES); }
    private void refreshTargetEyes() {
        if (!level().isClientSide) entityData.set(RED_EYES, PlayerTargetEyes.tracksLivingPlayer(this));
    }
    @Override public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target); refreshTargetEyes();
    }
    @Override public void aiStep() { super.aiStep(); refreshTargetEyes(); }
    @Override protected int getBaseExperienceReward() { return R196MobRules.entry(kind).experience(); }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override public void setBaby(boolean baby) { super.setBaby(false); }
    @Override protected boolean convertsInWater() { return false; }
    @Override protected boolean isSunBurnTick() { return getItemBySlot(EquipmentSlot.HEAD).isEmpty() && super.isSunBurnTick(); }
    @Override protected ItemStack getSkull() { return ItemStack.EMPTY; }
    @Override protected void addBehaviourGoals() {
        super.addBehaviourGoals();
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Villager.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Animal.class, 10, true, false, animal -> true));
    }
    @Override protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(this, R196MobEquipment.lordWeapon(this, false), true));
        R196MobEquipment.armor(this, "rusted_iron_");
    }
    @Override protected void populateDefaultEquipmentEnchantments(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty) {
        // Generated once per item by source ye.randomizeForMob; vanilla's second enchantment roll is excluded.
    }
    @Nullable @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group) {
        ((MobSpawnTypeAccessor)(Object)this).mite$setSpawnType(reason);
        var follow = getAttribute(Attributes.FOLLOW_RANGE);
        follow.addOrReplacePermanentModifier(new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mite", "random_follow_bonus"),
                random.nextGaussian() * .05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        setBaby(false); setCanPickUpLoot(true); setCanBreakDoors(true);
        populateDefaultEquipmentSlots(random, difficulty);
        handleAttributes(R196MobEquipment.tension(this));
        return group;
    }
    @Override protected void handleAttributes(float tension) {
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).addOrReplacePermanentModifier(new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath("mite", "revenant_spawn_bonus"), random.nextDouble() * .05,
                AttributeModifier.Operation.ADD_VALUE));
        if (random.nextFloat() < tension * .05F) {
            getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addOrReplacePermanentModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "revenant_leader"), random.nextDouble() * .25 + .5,
                    AttributeModifier.Operation.ADD_VALUE));
            getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("mite", "revenant_leader"), random.nextDouble() * 3 + 1,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (MobCombat.pepsin(source)) return false;
        if (source.is(DamageTypes.FALL) && amount >= getMaxHealth() * .5F) massiveFall = true;
        return super.hurt(source, amount);
    }
    @Override public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        // tw refuses village infection when a source melee tool is held (the standard revenant spawn).
        if (victim instanceof Villager && EquipmentDurability.toolProfile(getMainHandItem()) != null) return true;
        boolean result = super.killedEntity(level, victim);
        if (!result && victim instanceof Villager)
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD})
                setItemSlot(slot, ItemStack.EMPTY);
        return result;
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        if (!massiveFall || random.nextFloat() < .1F) {
            if (random.nextFloat() < (recentlyHit ? .5F : .25F)) spawnAtLocation(Items.ROTTEN_FLESH);
            int looting = source.getEntity() instanceof LivingEntity living ? EnchantmentHelper.getEnchantmentLevel(
                    level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.LOOTING), living) : 0;
            if (recentlyHit && !massiveFall && random.nextInt(50) < 5 + looting * 2)
                spawnAtLocation(R196MobEquipment.item(switch (random.nextInt(4)) {
                    case 0 -> "mite:copper_nugget"; case 1 -> "mite:silver_nugget";
                    case 2 -> "minecraft:gold_nugget"; default -> "minecraft:iron_nugget";
                }));
        }
        R196MobEquipment.dropEquipment(this, source, recentlyHit, massiveFall, false, this::getEquipmentDropChance);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putBoolean("mite_massive_fall", massiveFall);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); massiveFall = tag.getBoolean("mite_massive_fall");
    }
}
