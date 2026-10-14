package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.R196MobRules;
import io.github.ningmeng123.mite.survival.PlayerExperience;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Ghoul, wight, shadow and stalker retain distinct biology, immunity and attack outcomes. @author NingMeng123 */
public final class R196Undead extends Monster implements R196Mob {
    private final MobKind kind;
    public R196Undead(EntityType<? extends R196Undead> type, Level level, MobKind kind) {
        super(type, level); this.kind = kind; this.xpReward = R196MobRules.entry(kind).experience();
        if (kind == MobKind.INVISIBLE_STALKER || kind == MobKind.SHADOW) goalSelector.addGoal(6, new SeekLitTorchGoal(this));
    }
    @Override public MobKind miteKind() { return kind; }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this)); goalSelector.addGoal(2, new MeleeAttackGoal(this, 1, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8)); goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Villager.class, false));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Wolf.class, 10, true, false, e -> kind == MobKind.GHOUL));
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) && amount >= getMaxHealth() * .5F)
            getPersistentData().putBoolean("mite_massive_fall_031", true);
        if (kind == MobKind.WIGHT && !MobCombat.fire(source) && !MobCombat.silver(source) && !MobCombat.magic(source)) return false;
        if (kind == MobKind.SHADOW && !MobCombat.silver(source) && !MobCombat.magic(source) && !MobCombat.sunlight(source)) return false;
        return super.hurt(source, amount);
    }
    @Override public boolean doHurtTarget(Entity target) {
        float healthBefore = target instanceof net.minecraft.world.entity.LivingEntity living ? living.getHealth() : 0F;
        boolean hurt = super.doHurtTarget(target);
        if (!hurt || !(target instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()) return hurt;
        boolean lostHealth = living.getHealth() < healthBefore;
        if (kind == MobKind.GHOUL && lostHealth) living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 5), this);
        if (kind == MobKind.WIGHT && lostHealth && target instanceof Player player && random.nextFloat() < .4F) {
            int drain = Math.max(20, (int)Math.min(((long)player.experienceLevel + 1L) * 10L, Integer.MAX_VALUE));
            PlayerExperience.addPoints(player, -Math.round(drain * (1F - MobCombat.silverResistance(player))));
        }
        if (kind == MobKind.SHADOW) {
            if (lostHealth) living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600), this);
            if (target instanceof Player player) VisionDimming.apply(player, 2F);
        }
        return true;
    }
    public static boolean inSunlight(net.minecraft.world.entity.LivingEntity entity) {
        return entity.level().dimension().equals(Level.OVERWORLD) && entity.level().isDay()
                && entity.level().canSeeSky(entity.blockPosition()) && !entity.level().isRaining();
    }
    @Override public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive()) return;
        if (kind == MobKind.SHADOW) {
            if (inSunlight(this)) hurt(MobCombat.source(this, "sunlight"), 1000F);
            else if (tickCount % 40 == 0) {
                int amount = (int)((.4F - getLightLevelDependentMagicValue()) * 10F);
                if (amount > 0) heal(amount);
            }
        }
        if (kind == MobKind.SHADOW || kind == MobKind.INVISIBLE_STALKER) disableLight();
    }
    public boolean disableLight() {
        if (hurtTime > 0 || level().getNearestPlayer(this, 4) != null) return false;
        BlockPos center = blockPosition();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1 + (int)getBbHeight(); y++) for (int z = -1; z <= 1; z++) {
            BlockPos position = center.offset(x, y, z); var state = level().getBlockState(position);
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.JACK_O_LANTERN) || ((state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)) && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))) {
                if (!net.neoforged.neoforge.event.EventHooks.canEntityGrief(level(), this)) return false;
                if (state.is(Blocks.JACK_O_LANTERN)) {
                    level().setBlock(position, Blocks.CARVED_PUMPKIN.defaultBlockState(), 3); spawnAtLocation(Blocks.TORCH);
                } else level().destroyBlock(position, true, this);
                playSound(SoundEvents.ITEM_PICKUP, .05F, ((random.nextFloat() - random.nextFloat()) * .7F + 1F) * 2F);
                return true;
            }
        }
        return false;
    }
    @Override protected SoundEvent getAmbientSound() { return io.github.ningmeng123.mite.registry.MiteMobSounds.get(kind, "say"); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return io.github.ningmeng123.mite.registry.MiteMobSounds.get(kind, "hurt"); }
    @Override protected SoundEvent getDeathSound() { return io.github.ningmeng123.mite.registry.MiteMobSounds.get(kind, "death"); }
    @Override protected float getSoundVolume() { return kind == MobKind.INVISIBLE_STALKER || kind == MobKind.SHADOW ? .2F : super.getSoundVolume(); }
    @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
        if (kind != MobKind.WIGHT) return;
        if ((!getPersistentData().getBoolean("mite_massive_fall_031") || random.nextFloat() < .1F) && random.nextFloat() < (recentlyHit ? .5F : .25F)) spawnAtLocation(net.minecraft.world.item.Items.ROTTEN_FLESH);
        int looting = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentLevel(level.registryAccess().registryOrThrow(
                net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOOTING),
                source.getEntity() instanceof net.minecraft.world.entity.LivingEntity living ? living : this);
        if (recentlyHit && !getPersistentData().getBoolean("mite_massive_fall_031") && random.nextInt(200) < 5 + looting * 2) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocationForNugget(random.nextInt(4)));
            spawnAtLocation(item);
        }
    }
    private static net.minecraft.resources.ResourceLocation ResourceLocationForNugget(int choice) {
        return net.minecraft.resources.ResourceLocation.parse(switch (choice) { case 0 -> "mite:copper_nugget"; case 1 -> "mite:silver_nugget"; case 2 -> "minecraft:gold_nugget"; default -> "minecraft:iron_nugget"; });
    }
}
