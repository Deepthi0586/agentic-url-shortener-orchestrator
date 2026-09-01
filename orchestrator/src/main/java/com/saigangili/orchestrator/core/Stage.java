package com.saigangili.orchestrator.core;

import java.util.List;

/**
 * One node in the orchestration graph. A Stage declares what it depends
 * on (so the engine can compute execution order and parallelism) and
 * whether its completion requires a human approval checkpoint before
 * dependent stages may proceed.
 *
 * Implementations in this package are stubs (Phase 2): they demonstrate
 * the graph/gate/retry/approval mechanics without a real LLM call. Real
 * Claude API calls are wired in during Phase 3, behind this same
 * interface — the engine and graph logic do not need to change.
 */
public interface Stage {

    /** Unique name used as this stage's key in the graph and in state. */
    String name();

    /** Names of stages that must be COMPLETED before this stage is ready to run. */
    List<String> dependsOn();

    /**
     * Whether this stage's completion is a human approval checkpoint
     * (see docs/orchestration-design.md, section 6). Defaults to false.
     */
    default boolean requiresApproval() {
        return false;
    }

    /**
     * Do the stage's work and return its result. Throwing any exception
     * signals failure to the engine, which applies the bounded-retry
     * policy (see OrchestratorEngine).
     */
    StageResult execute(StageContext context) throws Exception;
}
