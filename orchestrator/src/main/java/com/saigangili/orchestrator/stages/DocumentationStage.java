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
 * STUB — Phase 2. Runs in parallel with TestingStage (both depend on
 * earlier stages, not on each other) — see GraphFactory and
 * docs/orchestration-design.md, section 2.
 */
public class DocumentationStage implements Stage {

    @Override
    public String name() {
        return "documentation";
    }

    @Override
    public List<String> dependsOn() {
        return List.of("design");
    }

    @Override
    public StageResult execute(StageContext context) throws Exception {
        System.out.println("[documentation] Generating README and API docs");
        Thread.sleep(300);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("docs_generated", List.of("STUB: README.md", "STUB: API contract doc"));

        DecisionEntry entry = new DecisionEntry(
                Instant.now(),
                "Generated setup instructions and API docs from design",
                "Stub stage — placeholder reasoning; real generation added in Phase 3");

        return StageResult.of(output, entry);
    }
}
