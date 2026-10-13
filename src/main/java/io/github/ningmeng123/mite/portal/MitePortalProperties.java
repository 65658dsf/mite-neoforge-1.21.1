package io.github.ningmeng123.mite.portal;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Additional vanilla portal states are saved/synchronized by the normal chunk format. @author NingMeng123 */
public final class MitePortalProperties {
    public static final IntegerProperty DESTINATION = IntegerProperty.create("mite_destination", 0, 2);
    public static final BooleanProperty RUNEGATE = BooleanProperty.create("mite_runegate");
    private MitePortalProperties() {}
}
