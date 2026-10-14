package io.github.ningmeng123.mite.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;

/** The original cubic idle/target jump schedule, including near-item crawling and feeding stops. @author NingMeng123 */
public final class VanillaCubeMoveControl extends MoveControl {
    private final Slime cube;
    private int delay;
    private ItemEntity food;
    public VanillaCubeMoveControl(Slime cube) { super(cube); this.cube=cube; delay=40+cube.getRandom().nextInt(81); }
    @Override public void tick() {
        MobCorrosion corrosion=((CubeState)(Object)cube).corrosion();
        boolean magma=cube instanceof MagmaCube;
        if (!magma && MobCombat.ticksWithOffset(cube)%20==0 && cube.getTarget()==null)
            food=cube.level().getEntitiesOfClass(ItemEntity.class,cube.getBoundingBox().inflate(8),i->i.isAlive() && MobCorrosion.susceptible(i.getItem(),false) && cube.hasLineOfSight(i)).stream().min(java.util.Comparator.comparingDouble(cube::distanceToSqr)).orElse(null);
        Entity target=cube.getTarget();
        if (target==null && food!=null && food.isAlive()) target=food;
        if (!magma && corrosion.isFeeding() && !(target instanceof Player) && cube.onGround()) { cube.setSpeed(0); cube.xxa=0; cube.zza=0; return; }
        if (target!=null) cube.lookAt(target,10,20);
        else if (delay<=1) cube.setYRot(cube.getYRot()+cube.getRandom().nextFloat()*120-60);
        cube.yBodyRot=cube.getYRot();
        if (!magma && (target instanceof ItemEntity && target.getBoundingBox().inflate(.5,0,.5).intersects(cube.getBoundingBox()) || !cube.level().noCollision(cube,cube.getBoundingBox().move(0,.25,0)))) { cube.setSpeed(target==null ? .015F : .025F); return; }
        float speed=(magma ? .2F : .1F)*cube.getSize();
        if (cube.isInWater() || cube.isInLava()) { cube.getJumpControl().jump(); cube.setSpeed(speed); return; }
        if (cube.onGround()) {
            int active=magma ? 20 : 10;
            if (target!=null && delay>active) delay=active;
            if (--delay<=0) { delay=target==null ? 40+cube.getRandom().nextInt(81) : active; cube.getJumpControl().jump(); cube.setSpeed(speed); }
            else { cube.setSpeed(0); cube.xxa=0; cube.zza=0; }
        } else cube.setSpeed(speed);
    }
}
