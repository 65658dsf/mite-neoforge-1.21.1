package io.github.ningmeng123.mite.client;

import java.util.regex.Pattern;

/** Smooth transitions between the resource pack's authored attacks. @author NingMeng123 */
public final class PunchyWeaponTransitions {
    private static final Pattern OWNED_ATTACK = Pattern.compile("mite_(?:longsword|twinblade|rapier|katana|spear|trident|glaive|warglaive|cutlass|claymore|greataxe|greathammer|chakram|scythe|halberd|knife|club|battleaxe|hammer)_(?:[1-3]|critical)(?:_(?:left|right))?");

    private PunchyWeaponTransitions() {}

    public static boolean ownsAttack(String name) {
        return name != null && OWNED_ATTACK.matcher(name).matches();
    }

    public static boolean playCrossfade(Object handler, Object clip, float speedMultiplier, boolean holdOnLastFrame) {
        try {
            String name = (String) clip.getClass().getMethod("getName").invoke(clip);
            if (!ownsAttack(name)) return false;
            Class<?> handlerType = handler.getClass();
            Class<?> easingType = Class.forName("punchy.client.animation.PoseHandler$BlendInEasing", false, handlerType.getClassLoader());
            handlerType.getMethod("setBlendInDurationNext", float.class).invoke(handler, 0.07F);
            handlerType.getMethod("setBlendInEasingNext", easingType).invoke(handler, easingType.getField("SMOOTH").get(null));
            handlerType.getMethod("setBlendInRealTimeNext").invoke(handler);
            handlerType.getMethod("playVariationAtTime", clip.getClass(), float.class, boolean.class, float.class, float.class, boolean.class)
                    .invoke(handler, clip, speedMultiplier, holdOnLastFrame, 0F, 0F, false);
            handlerType.getMethod("setSmoothBlendOutOnFinish", float.class).invoke(handler, 0.25F);
            return true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot transition between authored Punchy weapon attacks", exception);
        }
    }
}
