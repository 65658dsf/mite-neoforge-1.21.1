package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.SourceMonsterTargetMemory;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** og saves its active target; reload resolves it only among actually nearby loaded entities. @author NingMeng123 */
@Mixin(Mob.class)
public abstract class SourceMonsterTargetMixin implements SourceMonsterTargetMemory {
    @Unique private UUID mite$targetUuid;
    @Unique private LivingEntity mite$retarget;
    @Override @Nullable public UUID mite$pendingTarget() { return mite$targetUuid; }
    @Override public void mite$pendingTarget(@Nullable UUID uuid) { mite$targetUuid = uuid; }
    @Override @Nullable public LivingEntity mite$retarget() { return mite$retarget; }
    @Override public void mite$retarget(@Nullable LivingEntity target) { mite$retarget = target; }

    @WrapOperation(method = "setTarget", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;target:Lnet/minecraft/world/entity/LivingEntity;", opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    private void mite$acceptedRetarget(Mob mob, LivingEntity target, Operation<Void> original) {
        LivingEntity previous = mob.getTarget();
        if (R196MonsterAi.monster(mob) && target != null && !target.isAlive()) target = null;
        original.call(mob, target);
        SourceMonsterTargeting.acceptedTargetChange(mob, previous, target);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mite$saveTarget(CompoundTag tag, CallbackInfo ci) {
        Mob mob = (Mob)(Object)this;
        if (!R196MonsterAi.monster(mob)) return;
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive()) {
            var state = new CompoundTag();
            state.putInt("version", 1);
            state.putUUID("target", target.getUUID());
            tag.put(SourceMonsterTargeting.SAVE, state);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mite$loadTarget(CompoundTag tag, CallbackInfo ci) {
        Mob mob = (Mob)(Object)this;
        mite$targetUuid = null;
        mite$retarget = null;
        if (!R196MonsterAi.monster(mob)) return;
        CompoundTag state = tag.getCompound(SourceMonsterTargeting.SAVE);
        if (state.getInt("version") == 1 && state.hasUUID("target")) mite$targetUuid = state.getUUID("target");
    }
}
