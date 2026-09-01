package com.saigangili.orchestrator.stages;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.saigangili.orchestrator.core.DecisionEntry;
import com.saigangili.orchestrator.core.Stage;
import com.saigangili.orchestrator.core.StageContext;
import com.saigangili.orchestrator.core.StageResult;

/**
 * STUB — Phase 2. Only included in the graph for BROWNFIELD runs
 * (see GraphFactory). Real behavior identifies impacted modules/
 * APIs/data flows in an existing codebase.
 */
public class CodebaseReasoningStage implements Stage {

    @Override
    public String name() {
        return "codebase_reasoning";
    }

    @Override
    public List<String> dependsOn() {
        return List.of("requirements");
    }

    @Override
    public StageResult execute(StageContext context) throws Exception {
        System.out.println("[codebase_reasoning] Analyzing impacted modules for brownfield change");
        Thread.sleep(300);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("impacted_modules", List.of("STUB: shortener-service/controller", "STUB: shortener-service/model"));
        output.put("risk_notes", "STUB: no breaking changes identified");

        DecisionEntry entry = new DecisionEntry(
                Instant.now(),
                "Identified impacted modules before allowing design changes",
                "Stub stage — placeholder reasoning; real LLM call added in Phase 3");

        return StageResult.of(output, entry);
    }
}
