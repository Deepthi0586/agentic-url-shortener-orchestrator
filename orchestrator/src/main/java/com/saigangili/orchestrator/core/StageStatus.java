package com.saigangili.orchestrator.core;

/**
 * Lifecycle states for a single stage within an orchestrator run.
 *
 * PENDING     - not yet started; dependencies may or may not be met yet.
 * RUNNING     - currently executing (including retry attempts).
 * COMPLETED   - finished successfully and passed its exit gate.
 * FAILED      - exhausted its retry budget without succeeding (safe-stop).
 * HALTED      - run was stopped at a human approval checkpoint (rejected).
 * SUPERSEDED  - was completed, but a downstream rollback reopened it;
 *               the prior output is kept in the decision log, not discarded.
 */
public enum StageStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    HALTED,
    SUPERSEDED
}
