package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Samples the installed Better Combat resources with Player Animator's actual keyframe player. @author NingMeng123 */
final class BetterCombatMobAnimationPlayer {
    private static final Vec3f ONE = new Vec3f(1F, 1F, 1F);
    private static final Map<Mob, Attack> ATTACKS = new WeakHashMap<>();
    private static final Map<EntityModel<?>, Map<ModelPart, SavedPart>> SAVED = new WeakHashMap<>();

    private BetterCombatMobAnimationPlayer() {}

    static void start(Mob mob, String animation, int durationTicks, float upswing, boolean offhand) {
        ResourceLocation id = ResourceLocation.tryParse(animation);
        if (id == null || !(PlayerAnimationRegistry.getAnimation(id) instanceof KeyframeAnimation data)) {
            ATTACKS.remove(mob);
            return;
        }
        var builder = data.mutableCopy();
        // Match Better Combat: the torso replaces the native swing, while the head can still aim.
        builder.torso.fullyEnablePart(true);
        builder.head.pitch.setEnabled(false);
        boolean mirror = offhand ^ (mob.getMainArm() == HumanoidArm.LEFT);
        ATTACKS.put(mob, new Attack(builder.build(), mob.tickCount, Math.max(1, durationTicks),
                Mth.clamp(upswing, .05F, .95F), mirror));
    }

    static void stop(Mob mob) {
        ATTACKS.remove(mob);
    }

    static boolean active(Mob mob) {
        Attack attack = ATTACKS.get(mob);
        if (attack == null) return false;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        if (mob.isAlive() && attack.sample(mob.tickCount + partial) != null) return true;
        ATTACKS.remove(mob);
        return false;
    }

    static void apply(EntityModel<?> model, Mob mob, float partialTick, PoseStack pose) {
        finish(model);
        Attack attack = ATTACKS.get(mob);
        if (attack == null) return;
        if (!(model instanceof HumanoidModel<?>) && !(model instanceof IllagerModel<?>)) return;
        Frame frame = attack.sample(mob.tickCount + partialTick);
        if (frame == null || !mob.isAlive()) {
            ATTACKS.remove(mob);
            return;
        }
        Map<ModelPart, SavedPart> saved = new IdentityHashMap<>();
        SAVED.put(model, saved);
        if (model instanceof HumanoidModel<?> humanoid) {
            update(frame, "head", humanoid.head, saved);
            update(frame, "torso", humanoid.body, saved);
            update(frame, "rightArm", humanoid.rightArm, saved);
            update(frame, "leftArm", humanoid.leftArm, saved);
            update(frame, "rightLeg", humanoid.rightLeg, saved);
            update(frame, "leftLeg", humanoid.leftLeg, saved);
            copy(humanoid.hat, humanoid.head, saved);
            MonsterShieldPoses.apply(humanoid, mob);
            // Piglins inherit PlayerModel, including its separate clothing parts.
            if (model instanceof PlayerModel<?> player) {
                copy(player.jacket, player.body, saved);
                copy(player.rightSleeve, player.rightArm, saved);
                copy(player.leftSleeve, player.leftArm, saved);
                copy(player.rightPants, player.rightLeg, saved);
                copy(player.leftPants, player.leftLeg, saved);
            }
        } else if (model instanceof IllagerModel<?> illager) {
            ModelPart root = illager.root();
            update(frame, "head", illager.getHead(), saved);
            update(frame, "torso", root.getChild("body"), saved);
            update(frame, "rightArm", root.getChild("right_arm"), saved);
            update(frame, "leftArm", root.getChild("left_arm"), saved);
            update(frame, "rightLeg", root.getChild("right_leg"), saved);
            update(frame, "leftLeg", root.getChild("left_leg"), saved);
            visible(root.getChild("arms"), false, saved);
            visible(root.getChild("right_arm"), true, saved);
            visible(root.getChild("left_arm"), true, saved);
            MonsterShieldPoses.apply(illager.getHead(), root.getChild("right_arm"), root.getChild("left_arm"), mob);
        }
    }

    static void applyBody(Mob mob, float partialTick, PoseStack pose) {
        Attack attack = ATTACKS.get(mob);
        if (attack == null || !mob.isAlive()) return;
        Frame frame = attack.sample(mob.tickCount + partialTick);
        if (frame == null) return;
        Vec3f position = frame.get("body", TransformType.POSITION, Vec3f.ZERO);
        Vec3f rotation = frame.get("body", TransformType.ROTATION, Vec3f.ZERO);
        Vec3f scale = frame.get("body", TransformType.SCALE, ONE);
        // Player Animator applies its root in world coordinates, before the native model-axis flip.
        pose.scale(scale.getX(), scale.getY(), scale.getZ());
        pose.translate(position.getX(), position.getY() + .7F, position.getZ());
        rotate(pose, rotation);
        pose.translate(0F, -.7F, 0F);
    }

    static void finish(EntityModel<?> model) {
        Map<ModelPart, SavedPart> saved = SAVED.remove(model);
        if (saved != null) saved.forEach((part, before) -> before.restore(part));
    }

    static void applyHeldItem(Mob mob, HumanoidArm arm, PoseStack pose) {
        Attack attack = ATTACKS.get(mob);
        if (attack == null) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        Frame frame = attack.sample(mob.tickCount + partial);
        if (frame != null) transformItem(pose, frame, arm == HumanoidArm.RIGHT ? "rightItem" : "leftItem");
    }

    private static void update(Frame frame, String name, ModelPart part, Map<ModelPart, SavedPart> saved) {
        save(part, saved);
        KeyframeAnimation.StateCollection defaults = frame.data().getPart(name);
        PartPose initial = part.getInitialPose();
        Vec3f origin = defaults == null ? new Vec3f(initial.x, initial.y, initial.z)
                : new Vec3f(defaults.x.defaultValue, defaults.y.defaultValue, defaults.z.defaultValue);
        // Animation pivots describe a player; preserve the mob's own proportions and native offsets.
        Vec3f position = frame.get(name, TransformType.POSITION, new Vec3f(
                part.x - initial.x + origin.getX(), part.y - initial.y + origin.getY(), part.z - initial.z + origin.getZ()));
        part.setPos(position.getX() + initial.x - origin.getX(), position.getY() + initial.y - origin.getY(),
                position.getZ() + initial.z - origin.getZ());
        Vec3f rotation = frame.get(name, TransformType.ROTATION,
                new Vec3f(wrap(part.xRot), wrap(part.yRot), wrap(part.zRot)));
        part.setRotation(rotation.getX(), rotation.getY(), rotation.getZ());
        Vec3f scale = frame.get(name, TransformType.SCALE, new Vec3f(part.xScale, part.yScale, part.zScale));
        part.xScale = scale.getX();
        part.yScale = scale.getY();
        part.zScale = scale.getZ();
    }

    private static float wrap(float radians) {
        return Mth.wrapDegrees(radians * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
    }

    private static void copy(ModelPart target, ModelPart source, Map<ModelPart, SavedPart> saved) {
        save(target, saved);
        target.copyFrom(source);
    }

    private static void visible(ModelPart part, boolean visible, Map<ModelPart, SavedPart> saved) {
        save(part, saved);
        part.visible = visible;
    }

    private static void save(ModelPart part, Map<ModelPart, SavedPart> saved) {
        saved.computeIfAbsent(part, SavedPart::new);
    }

    private static void transformItem(PoseStack pose, Frame frame, String part) {
        Vec3f position = frame.get(part, TransformType.POSITION, Vec3f.ZERO);
        Vec3f rotation = frame.get(part, TransformType.ROTATION, Vec3f.ZERO);
        Vec3f scale = frame.get(part, TransformType.SCALE, ONE);
        pose.scale(scale.getX(), scale.getY(), scale.getZ());
        pose.translate(position.getX() / 16F, position.getY() / 16F, position.getZ() / 16F);
        rotate(pose, rotation);
    }

    private static void rotate(PoseStack pose, Vec3f rotation) {
        pose.mulPose(Axis.ZP.rotation(rotation.getZ()));
        pose.mulPose(Axis.YP.rotation(rotation.getY()));
        pose.mulPose(Axis.XP.rotation(rotation.getX()));
    }

    private record SavedPart(PartPose pose, float scaleX, float scaleY, float scaleZ, boolean visible) {
        SavedPart(ModelPart part) {
            this(part.storePose(), part.xScale, part.yScale, part.zScale, part.visible);
        }

        void restore(ModelPart part) {
            part.loadPose(pose);
            part.xScale = scaleX;
            part.yScale = scaleY;
            part.zScale = scaleZ;
            part.visible = visible;
        }
    }

    private record Frame(KeyframeAnimation data, IAnimation animation, float delta) {
        Vec3f get(String name, TransformType transform, Vec3f fallback) {
            return animation.get3DTransform(name, transform, delta, fallback);
        }
    }

    private static final class Attack {
        private final KeyframeAnimation data;
        private final int startTick;
        private final int duration;
        private final float upswing;
        private final boolean mirror;
        private int sampledTick = -1;
        private IAnimation sampler;

        private Attack(KeyframeAnimation data, int startTick, int duration, float upswing, boolean mirror) {
            this.data = data;
            this.startTick = startTick;
            this.duration = duration;
            this.upswing = upswing;
            this.mirror = mirror;
        }

        Frame sample(float age) {
            float elapsed = Math.max(0F, age - startTick);
            float contact = duration * upswing;
            // Better Combat's strike happens halfway through its attack keyframes. Align it with server damage.
            float animationTick = elapsed <= contact ? elapsed / contact * data.endTick * .5F
                    : elapsed <= duration ? data.endTick * (.5F + .5F * (elapsed - contact) / (duration - contact))
                    : data.endTick + (elapsed - duration) * data.endTick / duration;
            if (animationTick > data.stopTick || data.endTick <= 0) return null;
            int tick = Mth.floor(animationTick);
            if (tick != sampledTick) {
                var player = new KeyframeAnimationPlayer(data, tick);
                var mirrored = new MirrorModifier(mirror);
                mirrored.setAnim(player);
                sampler = mirrored;
                sampledTick = tick;
            }
            float delta = animationTick - tick;
            sampler.setupAnim(delta);
            return new Frame(data, sampler, delta);
        }
    }
}
