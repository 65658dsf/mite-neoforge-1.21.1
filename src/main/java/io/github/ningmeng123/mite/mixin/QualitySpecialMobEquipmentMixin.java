package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.rules.EquipmentDurabilityRules.Quality;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** R196 tn:152 and tr:215 explicitly create poor held weapons. @author NingMeng123 */
@Mixin({WitherSkeleton.class, ZombifiedPiglin.class})
public abstract class QualitySpecialMobEquipmentMixin {
    @Inject(method = "populateDefaultEquipmentSlots", at = @At("RETURN"))
    private void mite$poorSpawnedWeapon(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        Mob mob = (Mob)(Object)this;
        ItemStack held = mob.getMainHandItem();
        if (mob instanceof WitherSkeleton && held.is(Items.STONE_SWORD)) {
            held = new ItemStack(Items.IRON_SWORD);
            EquipmentDurability.setQuality(held, Quality.POOR);
            mob.setItemSlot(EquipmentSlot.MAINHAND, R196MobEquipment.generated(mob, held, false));
        }
        if (!held.has(EquipmentDurability.QUALITY.get())) EquipmentDurability.setQuality(held, Quality.POOR);
    }
}
