package com.saigangili.orchestrator.core;

import java.util.LinkedHashMap;
import java.util.Map;

import com.saigangili.orchestrator.state.OrchestratorState;
import com.saigangili.orchestrator.state.StageState;

/**
 * What a Stage receives when it executes: read access to the run's state
 * so far, so it can pull in the outputs of whatever stages it depends on
 * without needing to "re-ask" for information already established
 * upstream (see docs/orchestration-design.md, section 4).
 */
public class StageContext {

    private final OrchestratorState state;

    public StageContext(OrchestratorState state) {
        this.state = state;
    }

    /** The original, unmodified requirement text the run started from. */
    public String requirementRaw() {
        return state.getRequirementRaw();
    }

    /** The output map produced by a prior stage, or an empty map if it has none yet. */
    public Map<String, Object> outputOf(String stageName) {
        StageState stageState = state.getStages().get(stageName);
        if (stageState == null) {
            return new LinkedHashMap<>();
        }
        return stageState.getOutput();
    }

    /** Full read access to the run's state, for stages that need more than one prior output. */
    public OrchestratorState state() {
        return state;
    }
}
