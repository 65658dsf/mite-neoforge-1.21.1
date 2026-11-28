package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.worldgen.MossyDungeonLocator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** An eye that points to ordinary spawner dungeons. @author NingMeng123 */
public final class MossyDungeonEyeItem extends Item {
    public MossyDungeonEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);

        player.getCooldowns().addCooldown(this, 20);
        var target = MossyDungeonLocator.findNearest(server, player.blockPosition());
        if (target == null) {
            player.displayClientMessage(Component.translatable("item.mite.mossy_dungeon_eye.not_found"), true);
            return InteractionResultHolder.fail(stack);
        }

        var eye = new EyeOfEnder(server, player.getX(), player.getY(0.5), player.getZ());
        eye.setItem(stack);
        eye.signalTo(target);
        if (!server.addFreshEntity(eye)) return InteractionResultHolder.fail(stack);

        server.gameEvent(GameEvent.PROJECTILE_SHOOT, eye.position(), GameEvent.Context.of(player));
        float pitch = Mth.lerp(server.random.nextFloat(), 0.33F, 0.5F);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_EYE_LAUNCH,
                SoundSource.NEUTRAL, 1.0F, pitch);
        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.consume(stack);
    }
}
