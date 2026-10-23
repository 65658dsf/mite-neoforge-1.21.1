package io.github.ningmeng123.mite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ningmeng123.mite.config.MiteConfig;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;

/** Client entry point keeps optional animation classes unloaded when the mods are absent. @author NingMeng123 */
public final class BetterCombatMobAnimations {
    private BetterCombatMobAnimations() {}

    private static boolean available() {
        return ModList.get().isLoaded("bettercombat") && ModList.get().isLoaded("playeranimator");
    }

    private static boolean enabled() {
        if (!available()) return false;
        if (MiteConfig.betterCombatMobsEnabled()) return true;
        BetterCombatMobAnimationPlayer.clear();
        return false;
    }

    public static void start(Mob mob, String animation, int durationTicks, float upswing, boolean offhand) {
        if (enabled()) BetterCombatMobAnimationPlayer.start(mob, animation, durationTicks, upswing, offhand);
    }

    public static void stop(Mob mob) {
        if (available()) BetterCombatMobAnimationPlayer.stop(mob);
    }

    public static boolean active(Mob mob) {
        return enabled() && BetterCombatMobAnimationPlayer.active(mob);
    }

    public static void apply(EntityModel<?> model, Mob mob, float partialTick, PoseStack pose) {
        if (enabled()) BetterCombatMobAnimationPlayer.apply(model, mob, partialTick, pose);
        else finish(model);
    }

    public static void applyBody(Mob mob, float partialTick, PoseStack pose) {
        if (enabled()) BetterCombatMobAnimationPlayer.applyBody(mob, partialTick, pose);
    }

    public static void finish(EntityModel<?> model) {
        if (available()) BetterCombatMobAnimationPlayer.finish(model);
    }

    public static void applyHeldItem(Mob mob, HumanoidArm arm, PoseStack pose) {
        if (enabled()) BetterCombatMobAnimationPlayer.applyHeldItem(mob, arm, pose);
    }
}
