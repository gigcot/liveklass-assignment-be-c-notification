package com.liveclass.notification.domain.model;

public class RetryInfo {

    private static final int MAX_RETRY_COUNT = 3;

    private final int count;
    private final String failureReason;

    public RetryInfo() {
        this(0, null);
    }

    public RetryInfo(int count, String failureReason) {
        this.count = count;
        this.failureReason = failureReason;
    }

    public boolean canRetry() {
        return count < MAX_RETRY_COUNT;
    }

    public RetryInfo recordFailure(FailureReason reason) {
        return new RetryInfo(count + 1, reason.name());
    }

    public RetryInfo reset() {
        return new RetryInfo(0, null);
    }

    public int getCount() {
        return count;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
