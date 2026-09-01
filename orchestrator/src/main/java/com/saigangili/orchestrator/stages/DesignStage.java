package com.saigangili.orchestrator.stages;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.saigangili.orchestrator.core.DecisionEntry;
import com.saigangili.orchestrator.core.Stage;
import com.saigangili.orchestrator.core.StageContext;
import com.saigangili.orchestrator.core.StageResult;
import com.saigangili.orchestrator.llm.LlmClient;

/**
 * Designs the API contract, data model, and key technical strategies
 * from the Requirements stage's normalized spec — Core Requirement 4
 * ("architecture/design"). Dependencies differ by scenario: greenfield/
 * ambiguous runs depend only on "requirements"; brownfield runs
 * additionally depend on "codebase_reasoning" (wired in GraphFactory),
 * whose output is folded into the prompt when present.
 */
public class DesignStage implements Stage {

    private static final String SYSTEM_PROMPT = """
            You are a software architect. Given a normalized software requirement \
            (and, if provided, an analysis of an existing codebase this change must fit \
            into), produce a design covering the API contract, data model, and key \
            technical strategies.

            Respond with ONLY a JSON object (no markdown, no prose outside the JSON) in \
            exactly this shape:
            {
              "api_contract": "a concise description of the REST endpoints, methods, and their purpose",
              "data_model": "a concise description of the core entities and their fields",
              "short_code_strategy": "the approach for generating short codes and why",
              "caching_strategy": "the caching approach for redirects, or an explicit note that it is deferred"
            }

            Keep each field to a few sentences. Every element of the design must trace \
            back to something in the given requirements — do not introduce features \
            that were not asked for or reasonably implied.
            """;

    private final List<String> dependsOn;
    private final LlmClient llmClient = new LlmClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public DesignStage(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    @Override
    public String name() {
        return "design";
    }

    @Override
    public List<String> dependsOn() {
        return dependsOn;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public StageResult execute(StageContext context) throws Exception {
        System.out.println("[design] Calling Claude to design API contract and data model");

        Map<String, Object> requirementsOutput = context.outputOf("requirements");
        StringBuilder userMessage = new StringBuilder();
        userMessage.append("Normalized requirements:\n").append(requirementsOutput).append("\n");

        if (dependsOn.contains("codebase_reasoning")) {
            Map<String, Object> codebaseOutput = context.outputOf("codebase_reasoning");
            userMessage.append("\nExisting codebase analysis (this is a brownfield change — ")
                    .append("design must account for the impacted modules below):\n")
                    .append(codebaseOutput).append("\n");
        }

        JsonNode json = llmClient.completeAsJson(SYSTEM_PROMPT, userMessage.toString());

        @SuppressWarnings("unchecked")
        Map<String, Object> output = mapper.convertValue(json, Map.class);

        DecisionEntry entry = new DecisionEntry(
                Instant.now(),
                "Designed API contract and data model via Claude, grounded in Requirements output",
                "Real Claude API call — see short_code_strategy/caching_strategy in this stage's output");

        return StageResult.of(output, entry);
    }
}