package io.github.ningmeng123.mite.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/** Reads the real client attack layer, including recovery and cancellation fades. @author NingMeng123 */
public final class BetterCombatPlayerAnimationState {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BetterCombatPlayerAnimationState() {}

    public static boolean active(LivingEntity entity) {
        if (!(entity instanceof Player) || !entity.level().isClientSide
                || !ModList.get().isLoaded("bettercombat") || !ModList.get().isLoaded("playeranimator")) return false;
        return Loaded.active(entity);
    }

    /** All optional animation types remain behind the mod-presence guard. */
    private static final class Loaded {
        private static final int ATTACK_LAYER_PRIORITY = 2000;
        private static final Field LAYERS = layersField();

        private static boolean active(LivingEntity entity) {
            if (!(entity instanceof net.bettercombat.client.animation.PlayerAttackAnimatable)
                    || !(entity instanceof dev.kosmx.playerAnim.impl.IAnimatedPlayer player)
                    || LAYERS == null) return false;
            try {
                if (!(LAYERS.get(player.getAnimationStack()) instanceof List<?> layers)) return false;
                for (Object value : layers) {
                    if (value instanceof dev.kosmx.playerAnim.core.util.Pair<?, ?> layer
                            && Integer.valueOf(ATTACK_LAYER_PRIORITY).equals(layer.getLeft())
                            && layer.getRight() instanceof dev.kosmx.playerAnim.api.layered.ModifierLayer<?> animation
                            && animation.isActive()) return true;
                }
            } catch (IllegalAccessException exception) {
                return false;
            }
            return false;
        }

        private static Field layersField() {
            try {
                // Player Animator 2.0.4 exposes its stack but has no public layer getter.
                Field field = dev.kosmx.playerAnim.api.layered.AnimationStack.class.getDeclaredField("layers");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.warn("Unable to inspect the Better Combat player attack layer; player animation compatibility is disabled", exception);
                return null;
            }
        }
    }
}
