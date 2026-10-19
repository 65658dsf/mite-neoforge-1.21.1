package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import io.github.ningmeng123.mite.ranged.FishingCastContext;
import io.github.ningmeng123.mite.ranged.FishingHookView;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ul stores the cast hook material; adamantium resists lava and burning hooks wear the rod. @author NingMeng123 */
@Mixin(FishingHook.class)
public abstract class RangedFishingHookEnvironmentMixin extends Projectile implements FishingHookView {
    @ModifyArg(method="retrieve",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/ExperienceOrb;<init>(Lnet/minecraft/world/level/Level;DDDI)V"),index=4)
    private int mite$configuredFishingExperience(int original) {
        Player player=((FishingHook)(Object)this).getPlayerOwner();
        return player==null?original:Math.max(0,io.github.ningmeng123.mite.api.MiteExtensions.policy().fishingExperience(player,original));
    }
    @Unique private Material mite$castMaterial;
    @Unique private int mite$fortune;
    @Unique private int mite$fortuneAffix;
    @Override public int mite$fishingFortune() { return mite$fortune; }
    @Override public int mite$fishingFortuneAffix() { return mite$fortuneAffix; }
    @Unique private static final EntityDataAccessor<Byte> MITE_CAST_HAND = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.BYTE);
    @Unique private static final EntityDataAccessor<String> MITE_HOOK_MATERIAL = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.STRING);
    protected RangedFishingHookEnvironmentMixin(EntityType<? extends Projectile> type, Level level) { super(type, level); }
    @Override public InteractionHand mite$castHand() { return getEntityData().get(MITE_CAST_HAND) == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND; }
    @Override public String mite$hookMaterial() { return getEntityData().get(MITE_HOOK_MATERIAL); }
    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void mite$defineCast(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(MITE_CAST_HAND, (byte)0); builder.define(MITE_HOOK_MATERIAL, "");
    }
    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("RETURN"))
    private void mite$captureMaterial(CallbackInfo ci) {
        Player player = ((FishingHook)(Object)this).getPlayerOwner();
        if (player != null) {
            InteractionHand hand = FishingCastContext.hand(player);
            if (hand == null) hand = MiteRangedItems.rodMaterial(player.getMainHandItem()) != null
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            mite$castMaterial = MiteRangedItems.rodMaterial(player.getItemInHand(hand));
            var fortune = io.github.ningmeng123.mite.enchantment.R196Enchantments.sources(player.getItemInHand(hand), level(), "fishing_fortune");
            mite$fortune = fortune.normal();
            mite$fortuneAffix = fortune.affix();
            getEntityData().set(MITE_CAST_HAND, (byte)(hand == InteractionHand.OFF_HAND ? 1 : 0));
            getEntityData().set(MITE_HOOK_MATERIAL, mite$castMaterial == null ? "" : mite$castMaterial.id);
        }
    }
    @Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
    private void mite$trackedCastMaterial(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        if (accessor.equals(MITE_HOOK_MATERIAL)) mite$castMaterial = Material.byId(mite$hookMaterial());
    }
    @Override public boolean fireImmune() {
        return mite$castMaterial == Material.ADAMANTIUM || super.fireImmune();
    }
    @Inject(method = "tick", at = @At("TAIL"))
    private void mite$burningHookWearsRod(CallbackInfo ci) {
        Player player = ((FishingHook)(Object)this).getPlayerOwner();
        if (player == null || level().isClientSide) return;
        ItemStack rod = player.getItemInHand(mite$castHand());
        if (isOnFire() && !fireImmune() && tickCount % 5 == 0 && mite$castMaterial != null
                && MiteRangedItems.rodMaterial(rod) == mite$castMaterial)
            rod.hurtAndBreak(1, player, LivingEntity.getSlotForHand(mite$castHand()));
    }
}
