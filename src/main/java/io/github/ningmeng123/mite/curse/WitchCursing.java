package io.github.ningmeng123.mite.curse;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.WitchCurseRules;
import java.util.Random;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Witch;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/** tv/re curse selection and witch lifetime; the curse itself belongs to the shared world. @author NingMeng123 */
@EventBusSubscriber(modid = Mite.MOD_ID)
public final class WitchCursing {
    public static final String SEED = "mite_witch_curse_seed";
    public static final int REALIZATION_DELAY = WitchCurseRules.REALIZATION_DELAY;

    private WitchCursing() {}

    /** The source adds two ints before widening to Random's long seed and rejection-samples 64 slots. */
    public static R196Curse selectCurse(int witchSeed, String username) {
        return R196Curse.fromId(WitchCurseRules.selectId(witchSeed, username));
    }

    public static int seed(Witch witch) {
        var data = witch.getPersistentData();
        if (!data.contains(SEED, Tag.TAG_INT)) data.putInt(SEED, new Random().nextInt());
        return data.getInt(SEED);
    }

    /** Called after source candidate validation, rather than every tick or when a potion hits. */
    public static boolean tryCurse(Witch witch, ServerPlayer player) {
        if (witch.level().isClientSide || witch.level() != player.level() || !witch.isAlive() || !player.isAlive()
                || player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable) return false;
        if (witch.getRandom().nextInt(4) != 0) return false;
        return PlayerCurses.schedule(player, witch, selectCurse(seed(witch), player.getGameProfile().getName()), REALIZATION_DELAY);
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Witch witch) seed(witch);
    }

    @SubscribeEvent
    public static void despawn(MobDespawnEvent event) {
        if (event.getEntity() instanceof Witch) event.setResult(MobDespawnEvent.Result.DENY);
    }
}
