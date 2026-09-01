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
 * Interprets intent, identifies ambiguity, and normalizes the raw
 * requirement into a clear engineering problem — the first Core
 * Requirement from the assessment (section 4.1). Calls Claude via
 * LlmClient; any failure here is retried by OrchestratorEngine's
 * bounded-retry policy, same as it would be for a stub failure.
 */
public class RequirementsStage implements Stage {

    private static final String SYSTEM_PROMPT = """
            You are a requirements analyst for a software engineering team. Given a raw \
            software requirement, interpret intent, identify ambiguity, and normalize it \
            into a clear engineering problem.

            Respond with ONLY a JSON object (no markdown, no prose outside the JSON) in \
            exactly this shape:
            {
              "normalized_spec": "a few sentences describing the normalized functional and non-functional requirements",
              "assumptions": ["assumption 1", "assumption 2"],
              "open_ambiguities": ["ambiguity 1", "ambiguity 2"]
            }

            If there are no open ambiguities, use an empty array for open_ambiguities.
            Keep it concise — a few sentences for normalized_spec, a handful of items \
            per array at most.
            """;

    private final LlmClient llmClient = new LlmClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() {
        return "requirements";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public StageResult execute(StageContext context) throws Exception {
        String scenarioType = context.state().getScenarioType();
        System.out.println("[requirements] Calling Claude to interpret (" + scenarioType + "): \""
                + context.requirementRaw() + "\"");

        String userMessage = "Scenario type: " + scenarioType
                + "\n\nRequirement:\n" + context.requirementRaw();

        JsonNode json = llmClient.completeAsJson(SYSTEM_PROMPT, userMessage);

        @SuppressWarnings("unchecked")
        Map<String, Object> output = mapper.convertValue(json, Map.class);

        DecisionEntry entry = new DecisionEntry(
                Instant.now(),
                "Normalized requirement via Claude",
                "Real Claude API call — see normalized_spec/open_ambiguities in this stage's output");

        return StageResult.of(output, entry);
    }
}