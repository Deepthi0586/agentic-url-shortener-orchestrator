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
 * STUB — Phase 2. The synchronization point: depends on BOTH testing
 * and documentation, so it cannot start until whichever of the two
 * finishes last (see docs/orchestration-design.md, section 3). Also the
 * final human approval checkpoint before the run is considered done.
 */
public class ReleaseReadinessStage implements Stage {

    @Override
    public String name() {
        return "release_readiness";
    }

    @Override
    public List<String> dependsOn() {
        return List.of("testing", "documentation");
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public StageResult execute(StageContext context) throws Exception {
        System.out.println("[release_readiness] Evaluating go/no-go");
        Thread.sleep(200);

        Object testingOutput = context.outputOf("testing").get("pass_rate");
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("go_no_go", "GO");
        output.put("summary", "STUB: all gates passed, pass_rate=" + testingOutput);

        DecisionEntry entry = new DecisionEntry(
                Instant.now(),
                "Marked release-ready",
                "Stub stage — placeholder reasoning; real evaluation added in Phase 3");

        return StageResult.of(output, entry);
    }
}
