package com.saigangili.orchestrator.core;

import java.time.Instant;

/**
 * One entry in a stage's decision log: what was decided, and why.
 * This is what gives the orchestrator "decision lineage" — an ordered,
 * append-only record a human can review after the fact without re-running
 * anything (see docs/orchestration-design.md, section 4).
 *
 * A plain mutable class (not a record) so Jackson can serialize it via
 * standard getter/setter reflection without requiring extra modules.
 */
public class DecisionEntry {

    private Instant timestamp;
    private String decision;
    private String rationale;

    /** No-arg constructor required for Jackson deserialization. */
    public DecisionEntry() {
    }

    public DecisionEntry(Instant timestamp, String decision, String rationale) {
        this.timestamp = timestamp;
        this.decision = decision;
        this.rationale = rationale;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + decision + " — " + rationale;
    }
}
