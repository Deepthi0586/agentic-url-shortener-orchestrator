package com.saigangili.orchestrator.core;

import java.util.List;

import com.saigangili.orchestrator.stages.CodebaseReasoningStage;
import com.saigangili.orchestrator.stages.DesignStage;
import com.saigangili.orchestrator.stages.DocumentationStage;
import com.saigangili.orchestrator.stages.ImplementationStage;
import com.saigangili.orchestrator.stages.ReleaseReadinessStage;
import com.saigangili.orchestrator.stages.RequirementsStage;
import com.saigangili.orchestrator.stages.TestingStage;

/**
 * Builds the stage graph for a given scenario. This is where the graph's
 * shape actually differs by scenario: BROWNFIELD inserts the Codebase
 * Reasoning stage and makes Design depend on it in addition to
 * Requirements; GREENFIELD/AMBIGUOUS skip straight from Requirements to
 * Design (see docs/orchestration-design.md, section 2).
 */
public class GraphFactory {

    public static StageGraph build(ScenarioType scenarioType) {
        StageGraph graph = new StageGraph();

        graph.addStage(new RequirementsStage());

        if (scenarioType == ScenarioType.BROWNFIELD) {
            graph.addStage(new CodebaseReasoningStage());
            graph.addStage(new DesignStage(List.of("requirements", "codebase_reasoning")));
        } else {
            graph.addStage(new DesignStage(List.of("requirements")));
        }

        graph.addStage(new ImplementationStage());
        graph.addStage(new TestingStage());
        graph.addStage(new DocumentationStage());
        graph.addStage(new ReleaseReadinessStage());

        return graph;
    }
}
