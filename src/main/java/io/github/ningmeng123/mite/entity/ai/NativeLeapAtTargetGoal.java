package io.github.ningmeng123.mite.entity.ai;

import io.github.ningmeng123.mite.entity.NativeArachnidEvents;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Spider;

/** Source attacks attempt the leap every tick rather than vanilla's reduced goal delay and closed [2,4] interval. @author NingMeng123 */
public final class NativeLeapAtTargetGoal extends Goal {
    private final Spider spider;
    public NativeLeapAtTargetGoal(Spider spider) { this.spider=spider; setFlags(EnumSet.of(Flag.JUMP)); }
    @Override public boolean canUse() { return spider.getTarget()!=null && spider.getTarget().isAlive(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() { if(canUse()) NativeArachnidEvents.trySourceLeap(spider,spider.getTarget()); }
}
