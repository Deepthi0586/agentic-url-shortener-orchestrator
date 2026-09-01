package com.saigangili.orchestrator.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a stage hands back to the engine after a successful execution:
 * its output (persisted into the run's state, and readable by later
 * stages via StageContext) plus any decision log entries it wants to
 * record for that run.
 */
public class StageResult {

    private final Map<String, Object> output;
    private final List<DecisionEntry> decisionLog;

    public StageResult(Map<String, Object> output, List<DecisionEntry> decisionLog) {
        this.output = output;
        this.decisionLog = decisionLog;
    }

    /** Convenience factory for the common case of one or a few decisions. */
    public static StageResult of(Map<String, Object> output, DecisionEntry... entries) {
        List<DecisionEntry> log = new ArrayList<>();
        for (DecisionEntry entry : entries) {
            log.add(entry);
        }
        return new StageResult(output, log);
    }

    public static StageResult empty() {
        return new StageResult(new LinkedHashMap<>(), new ArrayList<>());
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public List<DecisionEntry> getDecisionLog() {
        return decisionLog;
    }
}
