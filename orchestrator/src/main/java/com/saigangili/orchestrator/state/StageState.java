package com.saigangili.orchestrator.state;

import com.saigangili.orchestrator.core.DecisionEntry;
import com.saigangili.orchestrator.core.StageStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted state for a single stage within a run — status, its output
 * (readable by dependent stages), its decision log, and retry/timing info.
 * This is the per-stage entry inside OrchestratorState.stages (see
 * docs/orchestration-design.md, section 4, for the overall shape).
 */
public class StageState {

    private StageStatus status = StageStatus.PENDING;
    private Map<String, Object> output = new LinkedHashMap<>();
    private List<DecisionEntry> decisionLog = new ArrayList<>();
    private int retries = 0;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;

    public StageStatus getStatus() {
        return status;
    }

    public void setStatus(StageStatus status) {
        this.status = status;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public List<DecisionEntry> getDecisionLog() {
        return decisionLog;
    }

    public void setDecisionLog(List<DecisionEntry> decisionLog) {
        this.decisionLog = decisionLog;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public void incrementRetries() {
        this.retries++;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
