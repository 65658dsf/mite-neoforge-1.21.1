package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.event.EventHooks;

/** og base callback, tt-only world-random jockey/effects, and source ranged-rider rejection sampling. @author NingMeng123 */
public final class SourceSpiderFinalization {
    public static final ResourceLocation FOLLOW_BONUS=ResourceLocation.fromNamespaceAndPath("mite","random_follow_bonus");
    private static final int MAX_RIDER_ATTEMPTS=64;
    private SourceSpiderFinalization() {}
    private static final class Group implements SpawnGroupData { @Nullable Holder<MobEffect> effect; }
    public static SpawnGroupData finish(Spider spider,ServerLevelAccessor accessor,DifficultyInstance difficulty,MobSpawnType reason,
            @Nullable SpawnGroupData group) {
        ((MobSpawnTypeAccessor)spider).mite$setSpawnType(reason);
        spider.getAttribute(Attributes.FOLLOW_RANGE).addOrReplacePermanentModifier(new AttributeModifier(FOLLOW_BONUS,
                spider.getRandom().nextGaussian()*.05D,AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        // te and all four source leaf arachnids never call tt's special callback.
        if(spider.getType()!=EntityType.SPIDER) return group;
        var worldRandom=spider.level().getRandom();
        if(worldRandom.nextInt(100)==0 && !spider.isSpawnCancelled()) {
            AbstractSkeleton rider=createRider(spider,accessor,difficulty,reason);
            if(rider!=null) {
                if(spider.level().addFreshEntity(rider)) {
                    if(!rider.startRiding(spider)) rider.discard();
                } else rider.discard();
            }
        }
        if(group==null) {
            Group effects=new Group(); group=effects;
            if(accessor.getDifficulty()==Difficulty.HARD && worldRandom.nextFloat()<.1F*R196MobEquipment.tension(spider)) {
                // Actual tu.nextInt(4): 0/1 speed, 2 strength, 3 regeneration. Its later invisibility branch is unreachable.
                int draw=worldRandom.nextInt(4);
                effects.effect=draw<=1 ? MobEffects.MOVEMENT_SPEED : draw==2 ? MobEffects.DAMAGE_BOOST : MobEffects.REGENERATION;
            }
        }
        if(group instanceof Group effects && effects.effect!=null)
            spider.addEffect(new MobEffectInstance(effects.effect,Integer.MAX_VALUE));
        return group;
    }
    /** Source tt keeps constructing/finalizing until subtype 0, using Longdead in the underground world. */
    @Nullable public static AbstractSkeleton createRider(Spider spider,ServerLevelAccessor accessor,DifficultyInstance difficulty,MobSpawnType reason) {
        boolean ancient=spider.level().dimension().equals(MiteWorldDimensions.UNDERWORLD);
        for(int attempt=0;attempt<MAX_RIDER_ATTEMPTS;attempt++) {
            AbstractSkeleton candidate=ancient ? (R196Skeleton)MiteMobs.type(MobKind.LONGDEAD).create(spider.level())
                    : EntityType.SKELETON.create(spider.level());
            if(candidate==null) return null;
            candidate.moveTo(spider.getX(),spider.getY(),spider.getZ(),spider.getYRot(),0);
            EventHooks.finalizeMobSpawn(candidate,accessor,difficulty,reason,null);
            // A canceled callback/spawn ends this attempt instead of silently retrying around a mod's decision.
            if(candidate.isSpawnCancelled() || candidate.getSpawnType()==null) { candidate.discard(); return null; }
            int subtype=candidate instanceof R196Skeleton source ? source.skeletonType() : ((OrdinarySkeletonState)candidate).mite$sourceSkeletonType();
            if(subtype==0 && candidate.getMainHandItem().getItem() instanceof BowItem) return candidate;
            if(attempt==MAX_RIDER_ATTEMPTS-1) {
                // R196's tt loop cannot terminate for a commanded Nether spider because tr always selects type1 there.
                // Modern safety adaptation: retain completed event decisions, then give that ordinary rider its legal source type0 kit.
                if(!ancient) { R196Skeleton.initializeOrdinaryTroop(candidate,0); return candidate; }
            }
            candidate.discard();
        }
        return null;
    }
}
