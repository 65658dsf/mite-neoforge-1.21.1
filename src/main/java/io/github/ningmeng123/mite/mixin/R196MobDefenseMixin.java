package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.entity.MobCombat;
import java.util.Stack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** Source species defenses precede the shared modern armor pipeline; barding retains its source path. @author NingMeng123 */
@Mixin(LivingEntity.class)
public abstract class R196MobDefenseMixin {
    @Shadow protected Stack<DamageContainer> damageContainers;

    @WrapOperation(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z",ordinal=0))
    private boolean mite$lavaIsNotPureFire(DamageSource source,TagKey<DamageType> tag,Operation<Boolean> original) {
        if (tag.equals(DamageTypeTags.IS_FIRE) && source.is(DamageTypes.LAVA)) return false;
        return original.call(source,tag);
    }
    @Inject(method="hurtHelmet",at=@At("HEAD"))
    private void mite$fallingObjectHelmetWear(DamageSource source,float amount,CallbackInfo ci) {
        LivingEntity self=(LivingEntity)(Object)this;
        if (!MobCombat.managedDefense(self) || self.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        int wear=(int)(amount*4F+self.getRandom().nextFloat()*amount*2F);
        var entries=new java.util.EnumMap<EquipmentSlot,net.neoforged.neoforge.event.entity.living.ArmorHurtEvent.ArmorEntry>(EquipmentSlot.class);
        entries.put(EquipmentSlot.HEAD,new net.neoforged.neoforge.event.entity.living.ArmorHurtEvent.ArmorEntry(self.getItemBySlot(EquipmentSlot.HEAD),wear));
        var event=net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.ArmorHurtEvent(entries,self,source));
        if (!event.isCanceled()) event.getArmorMap().forEach((slot,entry)->io.github.ningmeng123.mite.item.EquipmentDurability.hurt(entry.armorItemStack,(int)entry.newDamage,self,slot));
    }
    @WrapOperation(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/common/damagesource/DamageContainer;setReduction(Lnet/neoforged/neoforge/common/damagesource/DamageContainer$Reduction;F)V"))
    private void mite$sourceAbsorptionFloor(DamageContainer container,DamageContainer.Reduction reduction,float amount,Operation<Void> original) {
        float before=container.getNewDamage(); original.call(container,reduction,amount);
        LivingEntity self=(LivingEntity)(Object)this;
        if (self instanceof AbstractHorse && reduction==DamageContainer.Reduction.ABSORPTION && before>0F && MobCombat.defending(self))
            container.setNewDamage(Math.max(container.getNewDamage(),1F));
    }
    @WrapOperation(method="actuallyHurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void mite$sourceNpcExtraAbsorption(LivingEntity self,float health,Operation<Void> original) {
        float damage=Math.max(0F,self.getHealth()-health); original.call(self,health);
        if (self instanceof AbstractHorse && MobCombat.defending(self) && damage>0F)
            self.setAbsorptionAmount(Math.max(0F,self.getAbsorptionAmount()-damage));
    }
    @WrapMethod(method = "actuallyHurt")
    private void mite$scopeDefense(DamageSource source, float amount, Operation<Void> original) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!MobCombat.managedDefense(self)) { original.call(source, amount); return; }
        MobCombat.enterDefense(self);
        try { original.call(source, amount); } finally { MobCombat.exitDefense(self); }
    }
    @WrapMethod(method = "getDamageAfterArmorAbsorb")
    private float mite$sourceNaturalDefenseBeforeModernArmor(DamageSource source, float amount, Operation<Float> original) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!MobCombat.defending(self)) return original.call(source, amount);
        if (self instanceof AbstractHorse) return MobCombat.absorb(self, source, amount);
        return original.call(source, MobCombat.naturalArmorInput(self, source, amount));
    }
    @WrapMethod(method = "getDamageAfterMagicAbsorb")
    private float mite$lavaResistanceAfterDefense(DamageSource source, float amount, Operation<Float> original) {
        LivingEntity self = (LivingEntity)(Object)this;
        float damage = self instanceof AbstractHorse && MobCombat.defending(self) ? amount : original.call(source, amount);
        if (!source.is(DamageTypes.LAVA) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || !self.hasEffect(MobEffects.FIRE_RESISTANCE)) return damage;
        if (damageContainers.isEmpty()) return damage * .5F;
        DamageContainer container = damageContainers.peek();
        container.setNewDamage(container.getNewDamage() * .5F);
        return container.getNewDamage();
    }
}
