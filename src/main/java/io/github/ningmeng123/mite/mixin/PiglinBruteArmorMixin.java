package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.BloodMoonEquipment;
import io.github.ningmeng123.mite.entity.PiglinArmor;
import io.github.ningmeng123.mite.entity.PiglinBruteWeapons;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.entity.R196VanillaMobEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies native brute spawn and equipment pickup rules. @author NingMeng123 */
@Mixin(PiglinBrute.class)
public abstract class PiglinBruteArmorMixin extends AbstractPiglin {
    @Unique private static final String mite$updatedBruteAttributes = "mite:brute_attributes_current";

    protected PiglinBruteArmorMixin(EntityType<? extends AbstractPiglin> type, Level level) {
        super(type, level);
    }

    @Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"))
    private void mite$progressiveEquipment(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        if (!PiglinArmor.handles(this)) return;
        R196MobEquipment.randomArmor(this);
        BloodMoonEquipment.populateBruteAxe(this);
    }

    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void mite$initializeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, CallbackInfoReturnable<SpawnGroupData> cir) {
        // Bastion templates store the original full health before spawn initialization runs.
        if (reason == MobSpawnType.STRUCTURE && getHealth() == 50F
                && getAttributeBaseValue(Attributes.MAX_HEALTH) == R196VanillaMobEvents.PIGLIN_BRUTE_HEALTH)
            setHealth(getMaxHealth());
    }

    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void mite$pickUpEquipment(ItemStack incoming, CallbackInfoReturnable<Boolean> cir) {
        if (!PiglinArmor.handles(this)) return;
        if (incoming.getItem() instanceof ArmorItem armor) {
            cir.setReturnValue(canHoldItem(incoming) && canReplaceCurrentItem(incoming, getItemBySlot(armor.getEquipmentSlot())));
        } else if (PiglinBruteWeapons.canEquip(incoming)) {
            cir.setReturnValue(canHoldItem(incoming) && canReplaceCurrentItem(incoming, getItemBySlot(EquipmentSlot.MAINHAND)));
        }
    }

    @Override
    protected boolean isHoldingMeleeWeapon() {
        return super.isHoldingMeleeWeapon() || PiglinBruteWeapons.canEquip(getMainHandItem());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (getType() == EntityType.PIGLIN_BRUTE) tag.putBoolean(mite$updatedBruteAttributes, true);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (getType() != EntityType.PIGLIN_BRUTE || level().isClientSide || tag.getBoolean(mite$updatedBruteAttributes)) return;

        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() == 100D) {
            float healthFraction = getHealth() / getMaxHealth();
            health.setBaseValue(R196VanillaMobEvents.PIGLIN_BRUTE_HEALTH);
            setHealth(healthFraction * getMaxHealth());
        }
        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null && attack.getBaseValue() == 14D) attack.setBaseValue(R196VanillaMobEvents.PIGLIN_BRUTE_ATTACK);
    }
}
