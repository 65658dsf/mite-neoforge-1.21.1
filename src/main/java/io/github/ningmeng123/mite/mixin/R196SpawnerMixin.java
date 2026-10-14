package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196SpawnerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.entity.EntityTypeTest;
import java.util.function.Predicate;
import net.neoforged.neoforge.common.extensions.IOwnedSpawner;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source spawners retain player activation, original spread/density and ten-tick failed-cycle backoff. @author NingMeng123 */
@Mixin(BaseSpawner.class)
public abstract class R196SpawnerMixin implements R196SpawnerAccess {
    @Shadow private int spawnDelay;
    @Shadow private int requiredPlayerRange;
    @Shadow private SpawnData nextSpawnData;
    @Unique private boolean mite$attemptedCycle;
    @Unique private boolean mite$resetDelay;
    @Unique private boolean mite$disabled;
    @Unique private boolean mite$managed;
    @Unique private boolean mite$sourceRules() {
        return nextSpawnData == null || nextSpawnData.getCustomSpawnRules().isEmpty()
                && EntityType.by(nextSpawnData.getEntityToSpawn()).map(R196MobSpawning::sourceType).orElse(false);
    }
    @Override public boolean miteUsesSourceRules() { return mite$managed && mite$sourceRules(); }
    @Inject(method = "isNearPlayer", at = @At("HEAD"), cancellable = true)
    private void mite$sourceActivation(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!(level instanceof ServerLevel server) || !R196MobSpawning.manages(server) || !mite$sourceRules()) return;
        cir.setReturnValue(!mite$disabled && level.players().stream().anyMatch(player -> !player.isSpectator() && player.isAlive()
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) < (double)requiredPlayerRange * requiredPlayerRange));
    }
    @Inject(method = "serverTick", at = @At("HEAD"))
    private void mite$beginSourceCycle(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        mite$managed = R196MobSpawning.manages(level);
        mite$resetDelay = false;
        mite$attemptedCycle = R196MobSpawning.manages(level) && mite$sourceRules() && !mite$disabled && spawnDelay <= 0
                && level.players().stream().anyMatch(player -> !player.isSpectator() && player.isAlive()
                    && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) < (double)requiredPlayerRange * requiredPlayerRange);
    }
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BaseSpawner;delay(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void mite$rememberReset(BaseSpawner spawner, Level level, BlockPos pos, Operation<Void> original) {
        mite$resetDelay = true; original.call(spawner, level, pos);
    }
    @Inject(method = "serverTick", at = @At("RETURN"))
    private void mite$retryAfterTenTicks(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (mite$attemptedCycle && !mite$resetDelay && spawnDelay <= 0) spawnDelay += 10;
    }
    @ModifyConstant(method = "serverTick", constant = @Constant(doubleValue = .5D))
    private double mite$originalHorizontalCenter(double original) { return miteUsesSourceRules() ? 0D : original; }
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;inflate(D)Lnet/minecraft/world/phys/AABB;"))
    private AABB mite$originalDensityBounds(AABB box, double range, Operation<AABB> original) {
        return miteUsesSourceRules() ? box.inflate(range * 2, 4, range * 2) : original.call(box, range);
    }
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    @SuppressWarnings("unchecked")
    private <T extends Entity> List<T> mite$sourceClassDensity(ServerLevel level, EntityTypeTest<Entity, T> typeTest, AABB bounds,
            Predicate<? super T> predicate, Operation<List<T>> original, @Local Entity candidate) {
        var expected = nextSpawnData == null ? java.util.Optional.<EntityType<?>>empty() : EntityType.by(nextSpawnData.getEntityToSpawn());
        if (!miteUsesSourceRules() || expected.isEmpty()) return original.call(level, typeTest, bounds, predicate);
        return (List<T>)(List<?>)R196MobSpawning.spawnerEntities(level, expected.get(), candidate.getClass(), bounds).stream()
                .filter(mob -> ((Predicate<Entity>)(Predicate<?>)predicate).test(mob)).toList();
    }
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;finalizeMobSpawnSpawner(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/neoforged/neoforge/common/extensions/IOwnedSpawner;Z)Lnet/neoforged/neoforge/event/entity/living/FinalizeSpawnEvent;"))
    private FinalizeSpawnEvent mite$netherSkeletonIdentityAfterQualification(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData data, IOwnedSpawner spawner, boolean finalize,
            Operation<FinalizeSpawnEvent> original, @Local LocalRef<Entity> entity, @Local LocalRef<Mob> living) {
        if (miteUsesSourceRules() && finalize) {
            var prepared = R196MobSpawning.prepareSourceFinalization(mob, level.getLevel(), data);
            Mob replacement = prepared.mob(); data = prepared.group();
            if (replacement != mob) { entity.set(replacement); living.set(replacement); mob = replacement; }
        }
        return original.call(mob, level, difficulty, reason, data, spawner, finalize);
    }
    @Inject(method = "load", at = @At("RETURN"))
    private void mite$loadDisabled(Level level, BlockPos pos, CompoundTag tag, CallbackInfo ci) { mite$disabled = tag.getBoolean("mite:r196_disabled"); }
    @Inject(method = "save", at = @At("RETURN"))
    private void mite$saveDisabled(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (mite$disabled) cir.getReturnValue().putBoolean("mite:r196_disabled", true);
    }
}
