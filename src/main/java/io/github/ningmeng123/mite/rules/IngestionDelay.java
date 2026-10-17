package io.github.ningmeng123.mite.rules;

/** Source bdc uses real time, not simulation ticks, between completed ingestions. @author NingMeng123 */
public final class IngestionDelay {
    public static final long MILLIS = 1_000L;
    private long deadline;
    public void finished(long now) { deadline = now + MILLIS; }
    public boolean ready(long now) { return now >= deadline; }
    public void clear() { deadline = 0L; }
}
