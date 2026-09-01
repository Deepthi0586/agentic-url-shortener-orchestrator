package com.saigangili.orchestrator.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.saigangili.orchestrator.state.OrchestratorState;
import com.saigangili.orchestrator.state.StageState;

/**
 * The dependency graph of stages for a run. This is what makes execution
 * "non-linear": the engine does not walk a fixed list, it repeatedly asks
 * the graph which stages are currently ready (all dependencies COMPLETED,
 * not yet started) — so independent stages naturally become eligible in
 * the same round and can run in parallel (see docs/orchestration-design.md,
 * section 2).
 */
public class StageGraph {

    private final Map<String, Stage> stages = new LinkedHashMap<>();

    public void addStage(Stage stage) {
        stages.put(stage.name(), stage);
    }

    public Collection<Stage> allStages() {
        return stages.values();
    }

    public Stage get(String name) {
        return stages.get(name);
    }

    /**
     * Stages that are PENDING and whose dependencies are all COMPLETED.
     * Multiple stages returned in one call are the "ready set" for this
     * round, and the engine runs them concurrently, synchronizing before
     * moving to the next round.
     */
    public List<Stage> readyStages(OrchestratorState state) {
        List<Stage> ready = new ArrayList<>();
        for (Stage stage : stages.values()) {
            StageState stageState = state.stageState(stage.name());
            if (stageState.getStatus() != StageStatus.PENDING) {
                continue;
            }
            boolean depsMet = stage.dependsOn().stream().allMatch(dep -> {
                StageState depState = state.getStages().get(dep);
                return depState != null && depState.getStatus() == StageStatus.COMPLETED;
            });
            if (depsMet) {
                ready.add(stage);
            }
        }
        return ready;
    }

    /** True once every stage has reached a terminal status (completed, failed, or halted). */
    public boolean isComplete(OrchestratorState state) {
        return stages.keySet().stream().allMatch(name -> {
            StageStatus status = state.stageState(name).getStatus();
            return status == StageStatus.COMPLETED
                    || status == StageStatus.FAILED
                    || status == StageStatus.HALTED;
        });
    }

    /** Stages that directly declare a dependency on the given stage name. */
    public Set<String> directDependents(String stageName) {
        Set<String> result = new LinkedHashSet<>();
        for (Stage stage : stages.values()) {
            if (stage.dependsOn().contains(stageName)) {
                result.add(stage.name());
            }
        }
        return result;
    }
}
