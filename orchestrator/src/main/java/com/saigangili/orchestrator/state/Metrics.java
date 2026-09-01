package com.saigangili.orchestrator.state;

/**
 * Reliability metrics tracked for a run, per the assignment's
 * requirement to track "success rate, retry/rollback frequency, MTTR,
 * and end-to-end latency" (docs/orchestration-design.md, section 9).
 */
public class Metrics {

    private double successRate = 0.0;
    private int retryCount = 0;
    private int rollbackCount = 0;
    private long totalLatencySeconds = 0;
    private double mttrSeconds = 0.0;

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public int getRollbackCount() {
        return rollbackCount;
    }

    public void setRollbackCount(int rollbackCount) {
        this.rollbackCount = rollbackCount;
    }

    public void incrementRollbackCount() {
        this.rollbackCount++;
    }

    public long getTotalLatencySeconds() {
        return totalLatencySeconds;
    }

    public void setTotalLatencySeconds(long totalLatencySeconds) {
        this.totalLatencySeconds = totalLatencySeconds;
    }

    public double getMttrSeconds() {
        return mttrSeconds;
    }

    public void setMttrSeconds(double mttrSeconds) {
        this.mttrSeconds = mttrSeconds;
    }

    @Override
    public String toString() {
        return "successRate=" + successRate
                + ", retryCount=" + retryCount
                + ", rollbackCount=" + rollbackCount
                + ", totalLatencySeconds=" + totalLatencySeconds
                + ", mttrSeconds=" + mttrSeconds;
    }
}