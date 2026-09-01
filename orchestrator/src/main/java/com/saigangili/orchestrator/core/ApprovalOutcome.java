package com.saigangili.orchestrator.core;

/** Result of a single interactive approval prompt: decision plus an optional note. */
public class ApprovalOutcome {

    private final ApprovalDecision decision;
    private final String note;

    public ApprovalOutcome(ApprovalDecision decision, String note) {
        this.decision = decision;
        this.note = note;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public String getNote() {
        return note;
    }
}
