package com.saigangili.orchestrator.state;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.saigangili.orchestrator.core.ApprovalDecision;

/**
 * The full persisted state for one orchestrator run. Every stage appends
 * to this object rather than overwriting it — this is the single source
 * of cross-stage context (StageContext reads from it) and the audit trail
 * (see docs/orchestration-design.md, section 4).
 *
 * Serialized to a JSON file per run by StateStore. No embedded database
 * is used — see the design doc, section 8, for why a flat JSON file is
 * the right-sized choice for this single-process, low-concurrency data.
 */
public class OrchestratorState {

    private String runId;
    private String scenarioType;
    private String requirementRaw;
    private Map<String, StageState> stages = new LinkedHashMap<>();
    private List<ApprovalRecord> approvals = new ArrayList<>();
    private Metrics metrics = new Metrics();
    private String haltedAtStage;
    private Instant startedAt;
    private Instant completedAt;

    /** No-arg constructor required for Jackson deserialization. */
    public OrchestratorState() {
    }

    public OrchestratorState(String runId, String scenarioType, String requirementRaw) {
        this.runId = runId;
        this.scenarioType = scenarioType;
        this.requirementRaw = requirementRaw;
        this.startedAt = Instant.now();
    }

    /** Gets (or lazily creates, as PENDING) the state entry for a stage name. */
    public StageState stageState(String name) {
        return stages.computeIfAbsent(name, n -> new StageState());
    }

    public void recordApproval(String stage, ApprovalDecision decision, String note) {
        approvals.add(new ApprovalRecord(stage, decision, Instant.now(), note));
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getScenarioType() {
        return scenarioType;
    }

    public void setScenarioType(String scenarioType) {
        this.scenarioType = scenarioType;
    }

    public String getRequirementRaw() {
        return requirementRaw;
    }

    public void setRequirementRaw(String requirementRaw) {
        this.requirementRaw = requirementRaw;
    }

    public Map<String, StageState> getStages() {
        return stages;
    }

    public void setStages(Map<String, StageState> stages) {
        this.stages = stages;
    }

    public List<ApprovalRecord> getApprovals() {
        return approvals;
    }

    public void setApprovals(List<ApprovalRecord> approvals) {
        this.approvals = approvals;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public String getHaltedAtStage() {
        return haltedAtStage;
    }

    public void setHaltedAtStage(String haltedAtStage) {
        this.haltedAtStage = haltedAtStage;
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
}
