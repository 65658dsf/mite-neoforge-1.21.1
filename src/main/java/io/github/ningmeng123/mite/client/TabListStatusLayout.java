package io.github.ningmeng123.mite.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Separates MITE's synchronized status from the original styled name. @author NingMeng123 */
public final class TabListStatusLayout {
    public static final int NAME_GAP = 8;
    public static final int STATUS_GAP = 8;
    public static final int PING_RIGHT_INSET = 13;

    private TabListStatusLayout() {}

    public static Parts split(Component displayName) {
        var siblings = displayName.getSiblings();
        int size = siblings.size();
        if (size < 5 || !siblings.get(size - 4).getString().equals("  ")
                || !translation(siblings.get(size - 3), "gui.mite.tab.level")
                || !siblings.get(size - 2).getString().equals("  ")
                || !translation(siblings.get(size - 1), "gui.mite.tab.latency")) return null;

        var name = displayName.copy();
        name.getSiblings().subList(size - 4, size).clear();
        var level = siblings.get(size - 3).copy();
        var latency = siblings.get(size - 1).copy();
        level.setStyle(level.getStyle().applyTo(displayName.getStyle()));
        latency.setStyle(latency.getStyle().applyTo(displayName.getStyle()));
        return new Parts(name, level, latency);
    }

    private static boolean translation(Component component, String key) {
        return component.getContents() instanceof TranslatableContents text && text.getKey().equals(key);
    }

    public record Parts(Component name, Component level, Component latency) {}
}
