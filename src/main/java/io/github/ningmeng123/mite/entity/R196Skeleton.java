package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import io.github.ningmeng123.mite.rules.R196MobRules;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Longdead equipment, guardian weapon storage and both bone-lord troop algorithms. @author NingMeng123 */
public final class R196Skeleton extends AbstractSkeleton implements R196Mob {
    public static final String TROOP_COUNTER = "mite_bone_lord_despawn_counter";
    private static final EntityDataAccessor<Integer> SKELETON_TYPE = SynchedEntityData.defineId(R196Skeleton.class, EntityDataSerializers.INT);
    private final MobKind kind;
    private final SourceRangedAttackGoal rangedGoal = new SourceRangedAttackGoal(this, 1D, 60, 15F);
    private final SourceMeleeGoal meleeGoal = new SourceMeleeGoal(this, Player.class, 1.2D, false);
    private ItemStack stowed = ItemStack.EMPTY;
    private int forcedType = -1;
    private int troopsSummoned;
    private boolean massiveFall;
    public R196Skeleton(EntityType<? extends R196Skeleton> type, Level level, MobKind kind) {
        super(type, level);
        if (kind.family() != MobKind.Family.SKELETON) throw new IllegalArgumentException("Not a R196 skeleton");
        this.kind = kind; this.xpReward = R196MobRules.entry(kind).experience();
        if (isLongdead()) for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            setDropChance(slot, getEquipmentDropChance(slot) * .25F);
        reassessWeaponGoal();
    }
    @Override public MobKind miteKind() { return kind; }
    public boolean isLongdead() { return kind == MobKind.LONGDEAD || kind == MobKind.LONGDEAD_GUARDIAN; }
    public boolean isLord() { return kind == MobKind.BONE_LORD || kind == MobKind.ANCIENT_BONE_LORD; }
    public int skeletonType() { return entityData.get(SKELETON_TYPE); }
    public int troopsSummoned() { return troopsSummoned; }
    public ItemStack stowedItem() { return stowed; }
    public void forceSkeletonType(int type) { if (type < 0 || type > 2) throw new IllegalArgumentException("Skeleton type"); forcedType = type; }
    public void setSkeletonType(int type) {
        if (type < 0 || type > 2) throw new IllegalArgumentException("Skeleton type");
        entityData.set(SKELETON_TYPE, type); refreshDimensions();
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(SKELETON_TYPE, 0);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor); if (accessor.equals(SKELETON_TYPE)) refreshDimensions();
    }
    @Override public EntityDimensions getDefaultDimensions(Pose pose) {
        return skeletonType() == 1 ? EntityDimensions.scalable(.72F, 2.34F).withEyeHeight(2.1F) : super.getDefaultDimensions(pose);
    }
    @Override protected int getBaseExperienceReward() { return R196MobRules.entry(kind).experience(); }
    @Override public int getMaxSpawnClusterSize() { return isLord() ? 1 : super.getMaxSpawnClusterSize(); }
    @Override public float naturalDefense(DamageSource source) { return isLongdead() ? (kind == MobKind.LONGDEAD_GUARDIAN ? 2F : 1F) : 0F; }
    @Override public void reassessWeaponGoal() {
        // AbstractSkeleton invokes this before the subclass goal fields have been initialized.
        if (rangedGoal == null || meleeGoal == null || level().isClientSide) return;
        goalSelector.removeGoal(rangedGoal); goalSelector.removeGoal(meleeGoal);
        goalSelector.addGoal(4, getMainHandItem().getItem() instanceof BowItem ? rangedGoal : meleeGoal);
    }
    @Override public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) { return weapon instanceof BowItem; }
    @Override protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (isLongdead()) {
            setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(this,
                    skeletonType() == 2 ? "mite:ancient_metal_sword" : "mite:ancient_metal_bow", true));
            if (kind == MobKind.LONGDEAD_GUARDIAN && getMainHandItem().getItem() instanceof BowItem)
                stowed = R196MobEquipment.generated(this, "mite:ancient_metal_dagger", true);
            R196MobEquipment.armor(this, "ancient_metal_chainmail_");
        } else {
            boolean ancient = kind == MobKind.ANCIENT_BONE_LORD;
            setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(this, R196MobEquipment.lordWeapon(this, ancient), true));
            R196MobEquipment.armor(this, ancient ? "ancient_metal_" : "rusted_iron_");
        }
    }
    @Override protected void populateDefaultEquipmentEnchantments(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty) {}
    @Nullable @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group) {
        ((MobSpawnTypeAccessor)(Object)this).mite$setSpawnType(reason);
        getAttribute(Attributes.FOLLOW_RANGE).addOrReplacePermanentModifier(new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath("mite", "random_follow_bonus"), random.nextGaussian() * .05,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        int type = forcedType >= 0 ? forcedType : level.getLevel().dimension().equals(Level.NETHER) ? 1
                : random.nextFloat() < (isLongdead() ? .5F : .25F) ? 2 : 0;
        setSkeletonType(type);
        if (type == 1) {
            ItemStack sword = new ItemStack(Items.IRON_SWORD); EquipmentDurability.setQuality(sword, Quality.POOR);
            setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(this, sword, false));
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4D);
        } else populateDefaultEquipmentSlots(random, difficulty);
        setCanPickUpLoot(true); reassessWeaponGoal(); return group;
    }
    @Override protected AbstractArrow getArrow(ItemStack ammunition, float distanceFactor, @Nullable ItemStack bow) {
        ItemStack arrowStack = new ItemStack(MiteRangedItems.arrow(isLongdead() ? Material.ANCIENT_METAL : Material.RUSTED_IRON));
        AbstractArrow arrow = ((ArrowItem)arrowStack.getItem()).createArrow(level(), arrowStack, this, bow);
        double damage = distanceFactor * 2F + random.nextGaussian() * .25 + level().getDifficulty().getId() * .11F;
        if (bow != null) {
            var registry = level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            int power = bow.getEnchantmentLevel(registry.getHolderOrThrow(Enchantments.POWER));
            if (power > 0) damage += power * .5 + .5;
            if (bow.getEnchantmentLevel(registry.getHolderOrThrow(Enchantments.FLAME)) > 0) arrow.igniteForTicks(100);
        }
        if (skeletonType() == 1 || isOnFire() && random.nextInt(3) == 0) arrow.igniteForTicks(100);
        arrow.setBaseDamage(damage); return arrow;
    }
    @Override public boolean doHurtTarget(Entity target) {
        boolean hurt = super.doHurtTarget(target);
        if (hurt && skeletonType() == 1 && target instanceof LivingEntity living && living.isAlive())
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 200), this);
        return hurt;
    }
    @Override protected boolean isSunBurnTick() {
        return skeletonType() != 1 && getItemBySlot(EquipmentSlot.HEAD).isEmpty() && super.isSunBurnTick();
    }
    @Override public boolean fireImmune() { return skeletonType() == 1 || super.fireImmune(); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.CACTUS) || skeletonType() == 1 && source.is(DamageTypeTags.IS_FIRE)
                || source.getDirectEntity() instanceof AbstractArrow && source.getEntity() instanceof AbstractSkeleton) return false;
        if (source.is(DamageTypes.FALL) && amount >= getMaxHealth() * .5F) massiveFall = true;
        return super.hurt(source, amount);
    }
    @Override public boolean canHoldItem(ItemStack stack) {
        if (kind == MobKind.LONGDEAD_GUARDIAN && skeletonType() == 0 && !stack.isEmpty()) {
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (!(stack.getItem() instanceof BowItem) && !path.endsWith("_dagger")) return false;
        }
        return super.canHoldItem(stack);
    }
    public void swapHeldItemWithStowed() {
        ItemStack held = getMainHandItem(); setItemSlot(EquipmentSlot.MAINHAND, stowed); stowed = held;
    }
    public boolean updateGuardianWeapon() {
        if (kind != MobKind.LONGDEAD_GUARDIAN || stowed.isEmpty()) return false;
        if (getMainHandItem().isEmpty()) { swapHeldItemWithStowed(); return true; }
        LivingEntity target = getTarget();
        if (target == null || !getSensing().hasLineOfSight(target)) return false;
        double distance = distanceTo(target);
        boolean ranged = getMainHandItem().getItem() instanceof BowItem;
        if (ranged && distance < 5D || !ranged && distance > 6D) { swapHeldItemWithStowed(); return true; }
        return false;
    }
    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive()) return;
        if (getMainHandItem().isEmpty() || MobCombat.ticksWithOffset(this) % 10 == 0) updateGuardianWeapon();
        if (!isLord()) return;
        if (getTarget() instanceof Player target) getLookControl().setLookAt(target, getMaxHeadYRot(), getMaxHeadXRot());
        if (MobCombat.ticksWithOffset(this) % 20 == 0) commandTroops();
    }
    public void commandTroops() {
        if (!(level() instanceof ServerLevel server) || !isLord()) return;
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || distanceTo(target) > 16D || !getSensing().hasLineOfSight(target))) target = null;
        if (troopsSummoned < 6 && target instanceof Player && random.nextInt(8) < 7 - troopsSummoned) {
            if (trySummonTroop(target)) ++troopsSummoned;
            if (troopsSummoned < 6 && random.nextBoolean() && trySummonTroop(target)) ++troopsSummoned;
        }
        for (AbstractSkeleton skeleton : server.getEntitiesOfClass(AbstractSkeleton.class, getBoundingBox().inflate(16D, 8D, 16D))) {
            if (skeleton != this && !skeleton.getSensing().hasLineOfSight(this)) continue;
            if (skeleton.getHealth() < skeleton.getMaxHealth()) skeleton.heal(1F);
            if (skeleton instanceof R196Skeleton ancient && ancient.isLord()) continue;
            ((BoneLordFrenzy)skeleton).mite$setBoneLordFrenzy(target, 20);
            boolean troopClass = kind == MobKind.ANCIENT_BONE_LORD
                    ? skeleton instanceof R196Skeleton ancient && ancient.kind == MobKind.LONGDEAD : skeleton.getClass() == Skeleton.class;
            int counter = skeleton.getPersistentData().getInt(TROOP_COUNTER);
            if (troopsSummoned > 0 && troopClass
                    && (skeleton.getTarget() == null && skeleton.getHealth() >= skeleton.getMaxHealth() && random.nextFloat() < .05F || counter >= 0)
                    && tryDespawnTroop(skeleton)) --troopsSummoned;
        }
    }
    public static boolean tryDespawnTroop(AbstractSkeleton skeleton) {
        if (skeleton.isPersistenceRequired()) return false;
        for (Player player : skeleton.level().players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            double distance = skeleton.distanceToSqr(player);
            if (distance > 4096D && !skeleton.shouldRenderAtSqrDistance(distance)) continue;
            Vec3 eye = player.getEyePosition(), foot = skeleton.position();
            Vec3[] destinations = {foot.add(0, skeleton.getBbHeight() * .75F, 0), foot.add(0, .1, 0), foot.add(0, skeleton.getBbHeight() + .5, 0)};
            boolean visible = false;
            for (Vec3 destination : destinations) if (skeleton.level().clip(new ClipContext(eye, destination,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, skeleton)).getType() == HitResult.Type.MISS) { visible = true; break; }
            if (!visible) visible = skeleton.level().clip(new ClipContext(eye.add(0, 1, 0), destinations[2],
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, skeleton)).getType() == HitResult.Type.MISS;
            if (visible) {
                if (skeleton.getPersistentData().contains(TROOP_COUNTER))
                    skeleton.getPersistentData().putInt(TROOP_COUNTER, Math.min(-400, skeleton.getPersistentData().getInt(TROOP_COUNTER)));
                return false;
            }
        }
        Player nearest = skeleton.level().getNearestPlayer(skeleton, -1);
        if (nearest == null) return false;
        double dy = nearest.getY() - skeleton.getY();
        long hour = ((skeleton.level().getDayTime() + 6_000L) % 24_000L) / 1_000L;
        if (hour != 5) dy *= 2D;
        double dx = nearest.getX() - skeleton.getX(), dz = nearest.getZ() - skeleton.getZ();
        if (dx * dx + dy * dy + dz * dz <= 256D) return false;
        skeleton.discard(); return true;
    }
    private boolean trySummonTroop(LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || troopsSummoned >= 6) return false;
        for (int attempt = 0; attempt < 48 - troopsSummoned * 8; ++attempt) {
            AbstractSkeleton troop;
            boolean ancient = kind == MobKind.ANCIENT_BONE_LORD;
            int subtype = level().dimension().equals(Level.NETHER) ? 1 : random.nextFloat() < (ancient ? .5F : .25F) ? 2 : 0;
            if (ancient) troop = (R196Skeleton)MiteMobs.type(MobKind.LONGDEAD).create(level());
            else troop = subtype == 1 ? EntityType.WITHER_SKELETON.create(level()) : EntityType.SKELETON.create(level());
            if (troop == null) continue;
            double angle = random.nextDouble() * Math.PI * 2, radius = 2 + random.nextInt(11);
            BlockPos position = BlockPos.containing(getX() + Math.cos(angle) * radius, getY() + random.nextInt(7) - 3, getZ() + Math.sin(angle) * radius);
            for (int y = 0; y < 4 && server.isEmptyBlock(position.below()); ++y) position = position.below();
            troop.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5, random.nextFloat() * 360F, 0F);
            if (server.isEmptyBlock(position.below()) || !server.noCollision(troop) || server.containsAnyLiquid(troop.getBoundingBox())) continue;
            boolean nearPlayer = false;
            for (Player player : server.players()) {
                if (!player.isAlive()) continue;
                if (!player.isCreative() && !player.isSpectator() && troop.distanceToSqr(player) < 16D
                        || troop.distanceToSqr(player) < troop.distanceToSqr(this)) { nearPlayer = true; break; }
            }
            if (nearPlayer || subtype == 0 && !troop.getSensing().hasLineOfSight(target)) continue;
            if (!troop.getSensing().hasLineOfSight(this) && !pathReaches(troop, this)) continue;
            if (subtype == 2 && !pathReaches(troop, target)) continue;
            if (troop instanceof R196Skeleton old) {
                old.forceSkeletonType(subtype);
                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(old, server, server.getCurrentDifficultyAt(position), MobSpawnType.MOB_SUMMONED, null);
            } else {
                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(troop, server, server.getCurrentDifficultyAt(position), MobSpawnType.MOB_SUMMONED, null);
                initializeOrdinaryTroop(troop, subtype);
            }
            troop.getPersistentData().putInt(TROOP_COUNTER, -9600); troop.setTarget(target);
            if (!server.addFreshEntity(troop)) continue;
            server.sendParticles(ParticleTypes.POOF, troop.getX(), troop.getY(.5), troop.getZ(), 12, .3, .5, .3, .01);
            return true;
        }
        return false;
    }
    private static boolean pathReaches(AbstractSkeleton troop, Entity target) {
        Path path = troop.getNavigation().createPath(target, 0);
        return path != null && path.getEndNode() != null && path.getEndNode().asBlockPos().distSqr(target.blockPosition()) <= 2D;
    }
    public static void initializeOrdinaryTroop(AbstractSkeleton troop, int subtype) {
        if (troop.getType() == EntityType.SKELETON && troop instanceof OrdinarySkeletonState state)
            state.mite$setSourceSkeletonType(subtype);
        troop.getAttribute(Attributes.MAX_HEALTH).setBaseValue(6D); troop.setHealth(6F);
        troop.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.3F); troop.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4D);
        String weapon = subtype == 0 ? "minecraft:bow" : subtype == 1 ? "minecraft:iron_sword" : "minecraft:wooden_sword";
        long day = (troop.level().getDayTime() + 6_000L) / 24_000L + 1L;
        boolean enchant = subtype != 1;
        if (subtype == 2 && troop.getRandom().nextInt(20) == 0 && day >= 10) {
            weapon = day < 20 || troop.getRandom().nextBoolean() ? "mite:rusted_iron_dagger" : "mite:rusted_iron_sword";
            enchant = false;
        }
        ItemStack stack = new ItemStack(R196MobEquipment.item(weapon));
        if (subtype == 1) EquipmentDurability.setQuality(stack, Quality.POOR);
        troop.setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(troop, stack, enchant));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            troop.setItemSlot(slot, ItemStack.EMPTY);
        if (subtype != 1) R196MobEquipment.randomArmor(troop);
        troop.setCanPickUpLoot(true);
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        if (!massiveFall || random.nextFloat() < .1F) dropSourceMaterials(level, source, recentlyHit);
        R196MobEquipment.dropEquipment(this, source, recentlyHit, massiveFall, isLongdead(), this::getEquipmentDropChance);
    }
    private void dropSourceMaterials(ServerLevel level, DamageSource source, boolean recentlyHit) {
        int looting = source.getEntity() instanceof LivingEntity living ? EnchantmentHelper.getEnchantmentLevel(
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.LOOTING), living) : 0;
        int count;
        if (skeletonType() == 1) {
            count = random.nextInt(3 + looting) - 1;
            if (count > 0 && !recentlyHit) count -= random.nextInt(count + 1);
            for (int i = 0; i < count; ++i) spawnAtLocation(Items.COAL);
            if (recentlyHit && !massiveFall && random.nextInt(200) < 5 + looting * 2) spawnAtLocation(Items.WITHER_SKELETON_SKULL);
        } else if (skeletonType() != 2) {
            count = random.nextInt(2 + looting);
            if (count > 0 && !recentlyHit) count -= random.nextInt(count + 1);
            if (isLongdead() && count > 0) count = random.nextInt(3) == 0 ? 1 : 0;
            for (int i = 0; i < count; ++i) spawnAtLocation(MiteRangedItems.arrow(isLongdead() ? Material.ANCIENT_METAL : Material.RUSTED_IRON));
        }
        count = random.nextInt(3);
        if (count > 0 && !recentlyHit) count -= random.nextInt(count + 1);
        for (int i = 0; i < count; ++i) spawnAtLocation(Items.BONE);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putByte("SkeletonType", (byte)skeletonType());
        if (!stowed.isEmpty()) tag.put("stowed_item_stack", stowed.save(registryAccess()));
        if (troopsSummoned > 0) tag.putByte("num_troops_summoned", (byte)troopsSummoned);
        tag.putBoolean("mite_massive_fall", massiveFall);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkeletonType(Math.max(0, Math.min(2, tag.getByte("SkeletonType"))));
        stowed = tag.contains("stowed_item_stack") ? ItemStack.parse(registryAccess(), tag.getCompound("stowed_item_stack")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        troopsSummoned = Math.max(0, Math.min(6, tag.getByte("num_troops_summoned")));
        massiveFall = tag.getBoolean("mite_massive_fall"); reassessWeaponGoal();
    }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SKELETON_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SKELETON_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SKELETON_DEATH; }
    @Override protected SoundEvent getStepSound() { return SoundEvents.SKELETON_STEP; }
}
