package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import io.github.ningmeng123.mite.entity.R196MobEquipment;
import io.github.ningmeng123.mite.entity.SourceZombieDifficulty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep source difficulty rolls and initialize leader health across native zombie variants. @author NingMeng123 */
@Mixin(Zombie.class)
public abstract class SourceZombieDifficultyMixin {
    @Unique private Zombie mite$difficultyZombie() { return (Zombie)(Object)this; }

    @WrapMethod(method = "finalizeSpawn")
    private SpawnGroupData mite$fillNewLeaderHealth(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, Operation<SpawnGroupData> original) {
        Zombie zombie = mite$difficultyZombie();
        var health = SourceZombieDifficulty.captureSpawnHealth(zombie);
        SpawnGroupData result = original.call(level, difficulty, reason, group);
        SourceZombieDifficulty.finishSpawn(zombie, health);
        return result;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"))
    private void mite$sourceBaseReinforcements(CallbackInfo ci) {
        SourceZombieDifficulty.initializeBaseChance(mite$difficultyZombie());
    }

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void mite$sourceGaussianFollowRange(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            SpawnGroupData group, CallbackInfoReturnable<SpawnGroupData> cir) {
        SourceZombieDifficulty.prepareFollowRange(mite$difficultyZombie());
    }

    @WrapOperation(method = "finalizeSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/DifficultyInstance;getSpecialMultiplier()F"))
    private float mite$sourceSpawnTension(DifficultyInstance difficulty, Operation<Float> original) {
        Zombie zombie = mite$difficultyZombie();
        return SourceZombieDifficulty.handles(zombie.getType()) ? R196MobEquipment.tension(zombie) : original.call(difficulty);
    }

    @WrapOperation(method = "finalizeSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextFloat()F", ordinal = 0))
    private float mite$fixedPickupHasNoRandomDraw(RandomSource random, Operation<Float> original) {
        return SourceZombieDifficulty.handles(mite$difficultyZombie().getType()) ? 0F : original.call(random);
    }

    @ModifyArg(method = "finalizeSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/Zombie;setCanPickUpLoot(Z)V"), index = 0)
    private boolean mite$alwaysPickUpSourceLoot(boolean vanillaPickup) {
        return SourceZombieDifficulty.handles(mite$difficultyZombie().getType()) || vanillaPickup;
    }

    @Inject(method = "handleAttributes", at = @At("HEAD"), cancellable = true)
    private void mite$sourceAttributes(float ignoredModernMultiplier, CallbackInfo ci) {
        if (SourceZombieDifficulty.finishAttributes(mite$difficultyZombie())) ci.cancel();
    }
}
