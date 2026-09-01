package com.saigangili.orchestrator.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.saigangili.orchestrator.state.Metrics;
import com.saigangili.orchestrator.state.OrchestratorState;
import com.saigangili.orchestrator.state.StageState;
import com.saigangili.orchestrator.state.StateStore;

/**
 * Runs a StageGraph to completion. This is where every governance
 * mechanism from docs/orchestration-design.md is actually implemented:
 *
 *   - Non-linear, stateful execution: repeatedly asks the graph for the
 *     current "ready set" rather than following a fixed sequence.
 *   - Parallel paths with synchronization: all stages in a ready set run
 *     concurrently (CompletableFuture), and the engine waits for the
 *     whole set before moving on — that wait IS the synchronization point.
 *   - Bounded retries / safe-stop: see executeStageWithRetries.
 *   - Human approval checkpoints: see runApprovalCheckpoints.
 *   - Rollback / dynamic re-planning: see handleRevise.
 *   - Audit-grade observability: state is persisted after every round.
 */
public class OrchestratorEngine {

    private static final int MAX_RETRIES = 2;

    private final StageGraph graph;
    private final StateStore stateStore;
    private final ApprovalGate approvalGate;

    public OrchestratorEngine(StageGraph graph, StateStore stateStore, ApprovalGate approvalGate) {
        this.graph = graph;
        this.stateStore = stateStore;
        this.approvalGate = approvalGate;
    }

    public OrchestratorState run(String runId, String scenarioType, String requirementRaw) {
        OrchestratorState state = new OrchestratorState(runId, scenarioType, requirementRaw);

        // Pre-populate every stage as PENDING so readyStages() and the
        // JSON output show the full plan up front, not just what has run.
        for (Stage stage : graph.allStages()) {
            state.stageState(stage.name());
        }
        stateStore.save(state);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            while (!graph.isComplete(state)) {
                List<Stage> ready = graph.readyStages(state);

                if (ready.isEmpty()) {
                    // Nothing is ready but the graph isn't complete either —
                    // a dependency failed upstream. Stop rather than spin.
                    break;
                }

                System.out.println();
                System.out.println("--- Round start: running " + ready.size()
                        + " stage(s) in parallel: " + stageNames(ready) + " ---");

                List<CompletableFuture<Void>> futures = ready.stream()
                        .map(stage -> CompletableFuture.runAsync(
                                () -> executeStageWithRetries(stage, state), executor))
                        .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                stateStore.save(state);

                if (!runApprovalCheckpoints(ready, state)) {
                    // A rejection halted the run — stop immediately (safe-stop).
                    break;
                }

                stateStore.save(state);
            }
        } finally {
            executor.shutdown();
        }

        finalizeMetrics(state);
        stateStore.save(state);
        return state;
    }

    /**
     * For every stage in this round that both completed and requires
     * approval, prompt for a decision. Returns false if the run should
     * halt (a rejection occurred).
     */
    private boolean runApprovalCheckpoints(List<Stage> justRan, OrchestratorState state) {
        for (Stage stage : justRan) {
            StageState stageState = state.stageState(stage.name());
            if (stageState.getStatus() != StageStatus.COMPLETED || !stage.requiresApproval()) {
                continue;
            }

            ApprovalOutcome outcome = approvalGate.requestApproval(stage.name(), state);
            state.recordApproval(stage.name(), outcome.getDecision(), outcome.getNote());

            switch (outcome.getDecision()) {
                case REJECTED:
                    state.setHaltedAtStage(stage.name());
                    System.out.println("Run halted at '" + stage.name() + "' — rejected by reviewer.");
                    return false;
                case REVISE:
                    handleRevise(stage.name(), state);
                    System.out.println("Stage '" + stage.name()
                            + "' and its dependents reopened for revision.");
                    break;
                case APPROVED:
                default:
                    // proceed
                    break;
            }
        }
        return true;
    }

    /**
     * Executes one stage, retrying up to MAX_RETRIES times on failure
     * before giving up (bounded retries + safe-stop, not an infinite loop).
     */
    private void executeStageWithRetries(Stage stage, OrchestratorState state) {
        StageState stageState = state.stageState(stage.name());
        stageState.setStatus(StageStatus.RUNNING);
        stageState.setStartedAt(Instant.now());

        int attempt = 0;
        while (true) {
            try {
                StageContext context = new StageContext(state);
                StageResult result = stage.execute(context);

                stageState.setOutput(result.getOutput());
                stageState.getDecisionLog().addAll(result.getDecisionLog());
                stageState.setStatus(StageStatus.COMPLETED);
                stageState.setCompletedAt(Instant.now());
                return;
            } catch (Exception e) {
                attempt++;
                stageState.incrementRetries();
                state.getMetrics().incrementRetryCount();

                if (attempt > MAX_RETRIES) {
                    stageState.setStatus(StageStatus.FAILED);
                    stageState.setErrorMessage(e.getMessage());
                    stageState.setCompletedAt(Instant.now());
                    System.out.println("Stage '" + stage.name()
                            + "' failed after " + MAX_RETRIES + " retries: " + e.getMessage());
                    return;
                }
                System.out.println("Stage '" + stage.name() + "' failed (attempt " + attempt
                        + "), retrying: " + e.getMessage());
            }
        }
    }

    /**
     * Rollback / dynamic re-planning: reopens the given stage and every
     * stage that transitively depends on it, clearing their prior output
     * so they re-run. The prior decision log entries are NOT deleted —
     * they remain in the JSON state under this stage's history up to the
     * point of the reset, preserving traceability of the superseded
     * decision (see docs/orchestration-design.md, section 5).
     */
    private void handleRevise(String stageName, OrchestratorState state) {
        Set<String> toReset = collectTransitiveDependents(stageName);
        toReset.add(stageName);

        for (String name : toReset) {
            StageState stageState = state.stageState(name);
            stageState.setStatus(StageStatus.PENDING);
            stageState.setOutput(new LinkedHashMap<>());
            stageState.setRetries(0);
        }
        state.getMetrics().incrementRollbackCount();
    }

    private Set<String> collectTransitiveDependents(String stageName) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(stageName);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String dependent : graph.directDependents(current)) {
                if (visited.add(dependent)) {
                    queue.add(dependent);
                }
            }
        }
        return visited;
    }

    private void finalizeMetrics(OrchestratorState state) {
        long total = graph.allStages().size();
        long completed = graph.allStages().stream()
                .filter(s -> state.stageState(s.name()).getStatus() == StageStatus.COMPLETED)
                .count();

        Metrics metrics = state.getMetrics();
        metrics.setSuccessRate(total == 0 ? 0.0 : (double) completed / total);

        if (state.getStartedAt() != null) {
            metrics.setTotalLatencySeconds(Duration.between(state.getStartedAt(), Instant.now()).toSeconds());
        }

        List<Double> recoveryTimes = graph.allStages().stream()
                .map(s -> state.stageState(s.name()))
                .filter(ss -> ss.getRetries() > 0 && ss.getStartedAt() != null && ss.getCompletedAt() != null)
                .map(ss -> (double) Duration.between(ss.getStartedAt(), ss.getCompletedAt()).toSeconds())
                .toList();
        double mttr = recoveryTimes.isEmpty()
                ? 0.0
                : recoveryTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        metrics.setMttrSeconds(mttr);

        state.setCompletedAt(Instant.now());
    }

    private String stageNames(List<Stage> stages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stages.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(stages.get(i).name());
        }
        return sb.toString();
    }
}
