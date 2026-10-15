package io.github.ningmeng123.mite.entity.ai;

import io.github.ningmeng123.mite.entity.R196InfernalCreeper;
import io.github.ningmeng123.mite.entity.animal.SourceAnimalDespawn032;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Actual qs bytecode: path/health activation, then any visible living player sustains the fuse. @author NingMeng123 */
public final class SourceCreeperSwellGoal extends Goal {
    private final Creeper creeper;
    @Nullable private LivingEntity cachedTarget;
    public SourceCreeperSwellGoal(Creeper creeper) { this.creeper=creeper; setFlags(EnumSet.of(Flag.MOVE)); }
    public float triggerDistanceSquared() {
        float distance=creeper.getNavigation().isDone()?16F:creeper.getHealth()<creeper.getMaxHealth()?9F:4.5F;
        return creeper instanceof R196InfernalCreeper?distance*2F:distance;
    }
    public float continuationDistanceSquared() {
        return (creeper instanceof R196InfernalCreeper?36F:16F)/Math.clamp(creeper.getHealth()/creeper.getMaxHealth(),.4F,1F);
    }
    @Override public boolean canUse() {
        if(creeper.getSwellDir()>0 || ConspicuousCactus.forcesSwelling(creeper)) return true;
        LivingEntity target=creeper.getTarget();
        return target!=null && creeper.distanceToSqr(target)<triggerDistanceSquared();
    }
    @Override public void start() { creeper.getNavigation().stop(); cachedTarget=creeper.getTarget(); }
    @Override public void stop() { cachedTarget=null; }
    @Override public void tick() {
        if(ConspicuousCactus.forcesSwelling(creeper)) { creeper.setSwellDir(1); return; }
        if(cachedTarget==null) { creeper.setSwellDir(-1); return; }
        float limit=continuationDistanceSquared();
        for(Player player:creeper.level().players()) if(player.getHealth()>0F && creeper.distanceToSqr(player)<=limit
                && (visiblePlayerFrom(creeper,player,creeper.position().add(0,creeper.getBbHeight()/4F,0),limit)
                    ||visiblePlayerFrom(creeper,player,creeper.getEyePosition(),limit))) {
            creeper.setSwellDir(1); return;
        }
        creeper.setSwellDir(-1);
    }
    /** of.canEntityBeSeenFrom checks center distance then rays to quarter/three-quarter body points. */
    public static boolean visiblePlayerFrom(Creeper creeper,Player player,Vec3 origin,double limit) {
        if(origin.distanceToSqr(player.position().add(0,player.getBbHeight()*.5F,0))>limit) return false;
        return SourceAnimalDespawn032.visionRay(creeper,origin,player.position().add(0,player.getBbHeight()*.25F,0))
                ||SourceAnimalDespawn032.visionRay(creeper,origin,player.position().add(0,player.getBbHeight()*.75F,0));
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
