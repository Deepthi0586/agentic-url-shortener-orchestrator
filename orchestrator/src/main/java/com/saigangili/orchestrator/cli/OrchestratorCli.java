package com.saigangili.orchestrator.cli;

import com.saigangili.orchestrator.core.ApprovalGate;
import com.saigangili.orchestrator.core.GraphFactory;
import com.saigangili.orchestrator.core.OrchestratorEngine;
import com.saigangili.orchestrator.core.ScenarioType;
import com.saigangili.orchestrator.core.StageGraph;
import com.saigangili.orchestrator.state.OrchestratorState;
import com.saigangili.orchestrator.state.StateStore;

/**
 * Entry point for the orchestrator CLI.
 *
 * Usage:
 *   ./gradlew :orchestrator:run --args="GREENFIELD 'Build a URL shortener with analytics'"
 *   ./gradlew :orchestrator:run --args="BROWNFIELD 'Add custom alias support'"
 *   ./gradlew :orchestrator:run --args="AMBIGUOUS 'Make the analytics better'"
 *
 * With no args, runs a GREENFIELD scenario with a default requirement.
 *
 * Phase 2 (current): stages are stubs — this proves the graph, gates,
 * retries, rollback, and approval checkpoints all work correctly.
 * Phase 3: stub stages are replaced with real Claude API calls behind
 * the same Stage interface, so this engine does not need to change.
 */
public class OrchestratorCli {

    public static void main(String[] args) {
        ScenarioType scenarioType = args.length > 0
                ? ScenarioType.valueOf(args[0].toUpperCase())
                : ScenarioType.GREENFIELD;
        String requirement = args.length > 1
                ? args[1]
                : "Build a URL shortener service with core APIs, analytics, and reliability features.";

        StageGraph graph = GraphFactory.build(scenarioType);
        StateStore stateStore = new StateStore("orchestrator-runs");
        ApprovalGate approvalGate = new ApprovalGate();
        OrchestratorEngine engine = new OrchestratorEngine(graph, stateStore, approvalGate);

        String runId = scenarioType.name().toLowerCase() + "-" + System.currentTimeMillis();
        System.out.println("Starting orchestrator run: " + runId + " (" + scenarioType + ")");
        System.out.println("Requirement: " + requirement);

        OrchestratorState finalState = engine.run(runId, scenarioType.name(), requirement);

        System.out.println();
        System.out.println("=== Run complete ===");
        finalState.getStages().forEach((name, stageState) ->
                System.out.println("  " + name + ": " + stageState.getStatus()));
        System.out.println("Metrics: " + finalState.getMetrics());
        if (finalState.getHaltedAtStage() != null) {
            System.out.println("Halted at: " + finalState.getHaltedAtStage());
        }
        System.out.println("Full state saved to: orchestrator-runs/" + runId + ".json");
    }
}
