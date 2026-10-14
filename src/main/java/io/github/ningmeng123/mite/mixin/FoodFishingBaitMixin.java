package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteFoods;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules;
import io.github.ningmeng123.mite.ranged.FishingHookView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ul's geometric bite rolls and hotbar bait, while keeping modern cancellable fishing loot. @author NingMeng123 */
@Mixin(FishingHook.class)
public abstract class FoodFishingBaitMixin {
    @Shadow public abstract Player getPlayerOwner();
    @Shadow private int nibble;
    @Shadow private int timeUntilLured;
    @Shadow private int timeUntilHooked;
    @Shadow @Final private int luck;
    @Shadow private Entity hookedIn;
    @Shadow @Final private static EntityDataAccessor<Boolean> DATA_BITING;
    @Unique private boolean mite$paidBait;
    @Unique private boolean mite$retrievedEntity;
    @Unique private int mite$baitSlot() {
        Player player = getPlayerOwner();
        if (player != null) for (int slot = 0; slot < 9; slot++)
            if (player.getInventory().getItem(slot).is(MiteFoods.get("raw_worm"))) return slot;
        return -1;
    }
    @Inject(method = "catchingFish", at = @At("HEAD"), cancellable = true)
    private void mite$sourceBiteRoll(BlockPos ignored, CallbackInfo ci) {
        Player player = getPlayerOwner();
        if (player == null || ((FishingHookView)(Object)this).mite$hookMaterial().isEmpty()) return;
        ci.cancel();
        FishingHook hook = (FishingHook)(Object)this;
        if (!(hook.level() instanceof ServerLevel level)) return;
        timeUntilLured = 0;
        timeUntilHooked = 0;
        if (nibble > 0) {
            if (--nibble <= 0) hook.getEntityData().set(DATA_BITING, false);
            return;
        }
        BlockPos pos = BlockPos.containing(hook.getX(), hook.getY() - .2D, hook.getZ());
        if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource()
                || !level.getBlockState(pos.above()).isAir()) return;
        var random = hook.getRandom();
        int dx = random.nextInt(7) - 3;
        int dy = -random.nextInt(4);
        int dz = random.nextInt(7) - 3;
        BlockPos candidate = pos.offset(dx, dy, dz);
        var fluid = level.getFluidState(candidate);
        if (!fluid.is(FluidTags.WATER) || fluid.getAmount() != 8) return;
        Vec3 from = Vec3.atCenterOf(pos), to = Vec3.atCenterOf(candidate);
        if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hook)).getType() != HitResult.Type.MISS) return;
        long time = level.getDayTime();
        boolean blueMoon = level.dimension() == Level.OVERWORLD && (time / 24000L + 1L) % 128L == 0L
                && Math.floorMod(time, 24000L) >= 12000L;
        // Modern Luck of the Sea also supplies R196 Fishing Fortune's 9/10-per-level factor.
        int chance = R196RangedRules.biteChanceDenominator(time, blueMoon, level.isRainingAt(pos.above()), luck, mite$baitSlot() >= 0);
        if (random.nextInt(chance) != 0) return;
        nibble = random.nextInt(30) + 30;
        hook.setDeltaMovement(hook.getDeltaMovement().add(0D, -.2D, 0D));
        hook.playSound(SoundEvents.FISHING_BOBBER_SPLASH, .25F, 1F + (random.nextFloat() - random.nextFloat()) * .4F);
        level.sendParticles(ParticleTypes.BUBBLE, hook.getX(), hook.getY() + .5D, hook.getZ(), 6, .25D, 0D, .25D, .2D);
        level.sendParticles(ParticleTypes.FISHING, hook.getX(), hook.getY() + .5D, hook.getZ(), 6, .25D, 0D, .25D, .2D);
        hook.getEntityData().set(DATA_BITING, true);
    }
    @Inject(method = "retrieve", at = @At("HEAD"))
    private void mite$resetCatchBait(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        mite$paidBait = false;
        mite$retrievedEntity = hookedIn != null;
    }
    @Inject(method = "retrieve", at = @At("RETURN"), cancellable = true)
    private void mite$sourceRetrievalWear(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        if (MiteRangedItems.rodMaterial(rod) != null && mite$retrievedEntity && cir.getReturnValueI() > 0)
            cir.setReturnValue(((FishingHook)(Object)this).onGround() ? 2 : 3);
    }
    @Inject(method = "retrieve", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"))
    private void mite$consumeCatchBait(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        if (mite$paidBait) return;
        mite$paidBait = true;
        int slot = mite$baitSlot();
        if (slot >= 0) {
            // R196 consumes bait even in creative, independently of food qualification.
            getPlayerOwner().getInventory().getItem(slot).shrink(1);
            getPlayerOwner().getInventory().setChanged();
        }
    }
}
