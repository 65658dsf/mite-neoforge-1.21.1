package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.entity.SourceVanillaMobLoot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** The native captureDrops and cancellable LivingDropsEvent remain the single death transaction. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class SourceVanillaMobLootMixin {
    @WrapOperation(method="dropAllDeathLoot",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V"))
    private void mite$sourceFewItems(LivingEntity self,DamageSource source,boolean recent,Operation<Void> original) {
        if(!SourceVanillaMobLoot.managed(self) || self instanceof Slime) {
            if (self.getType() == EntityType.PIGLIN_BRUTE) SourceVanillaMobLoot.bruteScrap((Mob)self, source);
            original.call(self,source,recent);
            return;
        }
        SourceVanillaMobLoot.fewItems((Mob)self,source,recent);
    }
    @WrapOperation(method="dropAllDeathLoot",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;dropCustomDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V"))
    private void mite$sourceEquipment(LivingEntity self,ServerLevel level,DamageSource source,boolean recent,Operation<Void> original) {
        if(!SourceVanillaMobLoot.managed(self) || self.getType()==EntityType.SKELETON) { original.call(self,level,source,recent); return; }
        Mob mob=(Mob)self;
        R196MobEquipment.dropEquipment(mob,source,recent,mob.getPersistentData().getBoolean("mite_massive_fall_031"),false,
                slot -> ((SourceMobDropChanceAccess)mob).mite$equipmentDropChance(slot));
        SourceVanillaMobLoot.skeletonKilledCreeperRecord(mob,source);
        SourceVanillaMobLoot.chargedHead(mob,source);
        SourceVanillaMobLoot.bonusZombieProduce(mob, source, recent);
    }
    @Inject(method="hurt",at=@At("HEAD"))
    private void mite$sourceFallFlag(DamageSource source,float amount,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self=(LivingEntity)(Object)this;
        if((SourceVanillaMobLoot.managed(self) || self.getType() == EntityType.PIGLIN_BRUTE)
                && source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) && amount>=self.getMaxHealth()*.5F)
            self.getPersistentData().putBoolean("mite_massive_fall_031",true);
    }
}
