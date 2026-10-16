package io.github.ningmeng123.mite.curse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Client-visible snapshot; pending curses are deliberately not exposed. @author NingMeng123 */
public record CurseState(int id, boolean known) {
    public static final CurseState NONE = new CurseState(0, false);
    public static final Codec<CurseState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 16).fieldOf("id").forGetter(CurseState::id),
            Codec.BOOL.optionalFieldOf("known", false).forGetter(CurseState::known)
    ).apply(i, CurseState::new));
    public CurseState { if (id < 0 || id > 16) throw new IllegalArgumentException("Invalid curse ID"); if (id == 0) known = false; }
    public boolean active() { return id != 0; }
    public R196Curse type() { return R196Curse.fromId(id); }
}
