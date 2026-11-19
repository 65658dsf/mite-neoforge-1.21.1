package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.PiglinArmor;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies native brute spawn and armor pickup rules. @author NingMeng123 */
@Mixin(PiglinBrute.class)
public abstract class PiglinBruteArmorMixin extends AbstractPiglin {
    protected PiglinBruteArmorMixin(EntityType<? extends AbstractPiglin> type, Level level) {
        super(type, level);
    }

    @Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"))
    private void mite$progressiveArmor(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        if (PiglinArmor.handles(this)) R196MobEquipment.randomArmor(this);
    }

    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void mite$initializeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, CallbackInfoReturnable<SpawnGroupData> cir) {
        // Bastion templates store the original full health before spawn initialization runs.
        if (reason == MobSpawnType.STRUCTURE && getHealth() == 50F && getAttributeBaseValue(Attributes.MAX_HEALTH) == 100D)
            setHealth(getMaxHealth());
    }

    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void mite$pickUpArmor(ItemStack incoming, CallbackInfoReturnable<Boolean> cir) {
        if (PiglinArmor.handles(this) && incoming.getItem() instanceof ArmorItem armor)
            cir.setReturnValue(canHoldItem(incoming) && canReplaceCurrentItem(incoming, getItemBySlot(armor.getEquipmentSlot())));
    }
}
