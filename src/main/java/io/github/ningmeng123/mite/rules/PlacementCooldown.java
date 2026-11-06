package io.github.ningmeng123.mite.rules;

/** R196's successful block placement overrides the default use-button delay. @author NingMeng123 */
public record PlacementCooldown(long deadlineMillis) {
    public static final long BLOCK_MILLIS = 250L;
    public static final long BUCKET_MILLIS = 500L;

    public static PlacementCooldown afterPlacement(long now, boolean bucket) {
        return new PlacementCooldown(now + (bucket ? BUCKET_MILLIS : BLOCK_MILLIS));
    }

    public long remainingMillis(long now) {
        return Math.max(0L, deadlineMillis - now);
    }
}
