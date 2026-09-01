package com.saigangili.orchestrator.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A thin wrapper around Anthropic's Messages API, used by stages that need
 * real reasoning (Requirements, Design, etc. — see the individual Stage
 * implementations in the stages package).
 *
 * The API key is read from the ANTHROPIC_API_KEY environment variable —
 * never hardcoded, never committed. See README setup instructions for how
 * to set it locally.
 *
 * Extended thinking is explicitly disabled on every request: for the
 * structured-JSON-output stages this client is used for, thinking adds no
 * value and, for larger tasks (e.g. code generation in ImplementationStage),
 * was observed consuming the entire token budget before any answer text
 * was produced — the response came back with stop_reason "max_tokens" and
 * zero characters of actual text. Disabling it makes token usage
 * predictable and ensures the budget is spent on the actual answeßr.
 *
 * Any failure here (network error, non-200 response, unparseable output)
 * is surfaced as an exception, which the orchestrator's bounded-retry
 * policy (see OrchestratorEngine) already knows how to handle — this class
 * does not need its own retry logic.
 */
public class LlmClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final String MODEL = "claude-sonnet-5";
    private static final int MAX_TOKENS = 16000;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;

    public LlmClient() {
        this.apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY environment variable is not set. "
                            + "See README setup instructions for how to configure it.");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Sends a system prompt + user message to Claude and returns the raw
     * text response.
     */
    public String complete(String systemPrompt, String userMessage) throws IOException, InterruptedException {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", MODEL);
        root.put("max_tokens", MAX_TOKENS);
        root.put("system", systemPrompt);

        ObjectNode thinking = root.putObject("thinking");
        thinking.put("type", "disabled");

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        String requestBody = mapper.writeValueAsString(root);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API call failed: HTTP "
                    + response.statusCode() + " — " + response.body());
        }

        String text = extractText(response.body());
        if (text.isBlank()) {
            throw new RuntimeException(
                    "Claude returned an empty response (no text content). Full body: " + response.body());
        }
        return text;
    }

    /**
     * Convenience for stages that ask Claude to respond in JSON (the
     * common case here — every stage's output feeds into StageResult's
     * output map). Strips markdown code fences if the model wraps its
     * JSON in ```json ... ``` as models sometimes do despite instructions
     * not to, then parses the result.
     */
    public JsonNode completeAsJson(String systemPrompt, String userMessage) throws IOException, InterruptedException {
        String raw = complete(systemPrompt, userMessage);
        String cleaned = stripCodeFences(raw);
        try {
            return mapper.readTree(cleaned);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Claude response was not valid JSON. Raw response: " + raw, e);
        }
    }

    private String extractText(String responseBody) throws IOException {
        JsonNode responseJson = mapper.readTree(responseBody);
        JsonNode contentArray = responseJson.get("content");

        StringBuilder text = new StringBuilder();
        if (contentArray != null && contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
        }
        return text.toString();
    }

    private String stripCodeFences(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }
}