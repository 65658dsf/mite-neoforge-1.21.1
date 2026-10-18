package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.rules.R196MobRules;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Source infernal creepers keep ordinary dimensions and double the two explosion powers. @author NingMeng123 */
public final class R196InfernalCreeper extends Creeper implements R196Mob {
    public R196InfernalCreeper(EntityType<? extends R196InfernalCreeper> type, Level level, MobKind kind) {
        super(type, level); xpReward = R196MobRules.entry(kind).experience();
    }
    @Override public MobKind miteKind() { return MobKind.INFERNAL_CREEPER; }
    @Override public float naturalDefense(DamageSource source) { return 2F; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return source.is(DamageTypeTags.IS_FIRE) || super.isInvulnerableTo(source); }
    @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
        if (getPersistentData().getBoolean("mite_massive_fall_031") && random.nextFloat() >= .1F) return;
        int amount = random.nextInt(4); if (amount == 0) amount = random.nextInt(3);
        int looting = source.getEntity() instanceof net.minecraft.world.entity.LivingEntity owner
                ? io.github.ningmeng123.mite.enchantment.R196Enchantments.level(owner.getMainHandItem(), level, "minecraft:looting") : 0;
        if (looting > 0) amount += random.nextInt(looting + 1);
        if (amount > 0 && !recentlyHit) amount -= random.nextInt(amount + 1);
        for (int i = 0; i < amount; i++) if (recentlyHit || random.nextInt(3) == 0) spawnAtLocation(Items.GUNPOWDER);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FALL) && amount >= getMaxHealth() * .5F) getPersistentData().putBoolean("mite_massive_fall_031", true);
        return super.hurt(source, amount);
    }
}
