package com.saigangili.orchestrator.core;

import java.util.Scanner;

import com.saigangili.orchestrator.state.OrchestratorState;
import com.saigangili.orchestrator.state.StageState;

/**
 * The human approval checkpoint mechanism. A CLI prompt is a deliberately
 * simple choice for a solo prototype (see docs/orchestration-design.md,
 * section 6) — a production version would likely be a web UI with
 * role-based routing, noted as a limitation in the Final Engineering
 * Summary.
 */
public class ApprovalGate {

    private final Scanner scanner = new Scanner(System.in);

    public ApprovalOutcome requestApproval(String stageName, OrchestratorState state) {
        StageState stageState = state.stageState(stageName);

        System.out.println();
        System.out.println("=== Approval checkpoint: " + stageName + " ===");
        System.out.println("Output: " + stageState.getOutput());
        System.out.print("Approve this stage's output? [approve/reject/revise]: ");

        while (true) {
            String input = scanner.nextLine().trim().toLowerCase();
            switch (input) {
                case "approve":
                    return new ApprovalOutcome(ApprovalDecision.APPROVED, null);
                case "reject":
                    System.out.print("Reason for rejection: ");
                    return new ApprovalOutcome(ApprovalDecision.REJECTED, scanner.nextLine());
                case "revise":
                    System.out.print("Note for revision (this reopens the stage and re-runs it): ");
                    return new ApprovalOutcome(ApprovalDecision.REVISE, scanner.nextLine());
                default:
                    System.out.print("Please type 'approve', 'reject', or 'revise': ");
            }
        }
    }
}
