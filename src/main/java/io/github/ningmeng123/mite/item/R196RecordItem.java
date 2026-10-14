package io.github.ningmeng123.mite.item;

import io.github.ningmeng123.mite.utility.UniqueMusicSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/** Modern jukebox playback retains R196 unique-record cleanup on actual destruction. @author NingMeng123 */
public final class R196RecordItem extends Item {
    private final String name;
    public R196RecordItem(String name) {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.parse("mite:" + name))));
        this.name = name;
    }
    public String recordName() { return name; }
    @Override public void onDestroyed(ItemEntity entity, DamageSource source) { release(entity); }
    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.getY() < entity.level().getMinBuildHeight() - 64) release(entity);
        return false;
    }
    public void release(ItemEntity entity) { if (entity.level() instanceof ServerLevel level) UniqueMusicSavedData.get(level).release(name); }
}
