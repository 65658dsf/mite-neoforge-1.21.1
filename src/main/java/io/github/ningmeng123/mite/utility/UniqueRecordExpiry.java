package io.github.ningmeng123.mite.utility;

import io.github.ningmeng123.mite.item.R196RecordItem;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;

/** Source ss releases a unique signature when the item actually expires, not on pickup or unload. @author NingMeng123 */
public final class UniqueRecordExpiry {
    private UniqueRecordExpiry() {}
    public static void expire(ItemExpireEvent event) {
        if (event.getExtraLife() <= 0 && event.getEntity().getItem().getItem() instanceof R196RecordItem record) record.release(event.getEntity());
    }
}
