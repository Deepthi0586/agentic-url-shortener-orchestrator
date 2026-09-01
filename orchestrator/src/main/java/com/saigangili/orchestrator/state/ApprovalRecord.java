package com.saigangili.orchestrator.state;

import com.saigangili.orchestrator.core.ApprovalDecision;

import java.time.Instant;

/** One human approval decision at a checkpoint, with an optional note. */
public class ApprovalRecord {

    private String stage;
    private ApprovalDecision decision;
    private Instant timestamp;
    private String note;

    public ApprovalRecord() {
    }

    public ApprovalRecord(String stage, ApprovalDecision decision, Instant timestamp, String note) {
        this.stage = stage;
        this.decision = decision;
        this.timestamp = timestamp;
        this.note = note;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public void setDecision(ApprovalDecision decision) {
        this.decision = decision;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
