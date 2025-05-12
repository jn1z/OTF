package OTF.Model;

import java.util.concurrent.ScheduledFuture;

public class Cancellation {

    private final int stateThreshold;

    private boolean interrupted;
    private boolean oom;

    private ScheduledFuture<?> backref;

    public Cancellation() {
        this(false, Integer.MAX_VALUE);
    }

    public Cancellation(boolean interrupted, int stateThreshold) {
        this.interrupted = interrupted;
        this.stateThreshold = stateThreshold;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public void setInterrupted() {
        this.interrupted = true;
    }

    public boolean isOom() {
        return oom;
    }

    public boolean isAboveThreshold(int states) {
        this.oom |= states > stateThreshold;
        return this.oom;
    }

    public boolean isCancelled() {
        return isInterrupted() || isOom();
    }

    public String cancelLabel() {
        return this.isInterrupted() ? "TO" : "OOM";
    }

    public void cancel() {
        backref.cancel(true);
    }

    public void setBackref(ScheduledFuture<?> backref) {
        this.backref = backref;
    }
}
