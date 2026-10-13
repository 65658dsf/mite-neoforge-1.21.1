package io.github.ningmeng123.mite.survival;

import io.github.ningmeng123.mite.item.MiteBucketItem;
import io.github.ningmeng123.mite.rules.LavaBucketRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;

/** Source melting is decided only on the server, after a successful native fluid pickup. @author NingMeng123 */
public final class LavaBucketFilling {
    private LavaBucketFilling() {}
    public static ItemStack convert(ItemStack empty, Player player, ItemStack filled) {
        boolean lava = filled.is(Items.LAVA_BUCKET)
                || filled.getItem() instanceof MiteBucketItem bucket && bucket.contents() == MiteBucketItem.Contents.LAVA;
        String metal = empty.is(Items.BUCKET) ? "iron"
                : empty.getItem() instanceof MiteBucketItem bucket && bucket.contents() == MiteBucketItem.Contents.EMPTY ? bucket.metal() : "";
        if (lava && !player.hasInfiniteMaterials() && player.level() instanceof ServerLevel level
                && level.random.nextFloat() < LavaBucketRules.meltingChance(metal)) {
            empty.shrink(1);
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, .5F, 2F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 4, .1, .1, .1, 0);
            return empty;
        }
        return ItemUtils.createFilledResult(empty, player, filled);
    }
}
