package com.saigangili.orchestrator.stages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.saigangili.orchestrator.core.DecisionEntry;
import com.saigangili.orchestrator.core.Stage;
import com.saigangili.orchestrator.core.StageContext;
import com.saigangili.orchestrator.core.StageResult;
import com.saigangili.orchestrator.llm.LlmClient;

/**
 * Generates the shortener-service source code from the Design stage's
 * output, and writes it to disk under shortener-service/src/main/java/.
 * This is the one stage whose real-world side effect is writing files,
 * not just producing text/JSON to store in state — see docs/orchestration-design.md
 * for how this fits the overall stage-output model (files_changed is
 * still recorded in state, listing what was written).
 *
 * Relies on the orchestrator process's working directory being the repo
 * root (see orchestrator/build.gradle's run task) so the relative path
 * below resolves correctly regardless of how the process was launched.
 */
public class ImplementationStage implements Stage {

    private static final String SYSTEM_PROMPT = """
            You are a senior Java/Spring Boot engineer. Given a design (API contract, \
            data model, short code strategy, caching strategy), generate the CORE Spring \
            Boot source files that implement the essential create/retrieve/redirect flow.

            Scope constraints (important — keep the response small enough to complete \
            in one response):
            - Generate AT MOST 5 files, covering only: the entity/model, the repository, \
              the service (short code generation + create/retrieve/redirect logic), and \
              the REST controller. Combine related logic into these files rather than \
              splitting into many small classes.
            - Do NOT implement caching, analytics event storage, rate limiting, or \
              aggregation/reporting services in this pass — the design may describe them, \
              but they are out of scope for this generation; note them as a comment \
              instead of implementing them.
            - Keep each class focused and complete, but concise — avoid unnecessary \
              boilerplate (no unused imports, no elaborate javadoc).

            Conventions:
            - Base package: com.saigangili.shortener
            - Use sub-packages as appropriate: controller, service, repository, model
            - Use Spring Data JPA (@Entity classes, @Repository interfaces extending \
              JpaRepository), constructor injection, @RestController, @Service
            - Do NOT generate a build file, application.yml, or main application class — \
              those already exist in the project

            Respond with ONLY a JSON object (no markdown, no prose outside the JSON) in \
            exactly this shape:
            {
              "files": [
                { "path": "controller/ShortUrlController.java", "content": "full Java source code as a single string, including the package declaration and all imports" },
                { "path": "service/ShortUrlService.java", "content": "..." }
              ]
            }

            Each "path" is relative to src/main/java/com/saigangili/shortener/. Each \
            "content" value must be complete, syntactically valid Java — a full file, \
            not a snippet or partial class.
            """;

    private final LlmClient llmClient = new LlmClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Path TARGET_BASE_DIR =
            Paths.get("shortener-service", "src", "main", "java", "com", "saigangili", "shortener");

    /**
     * Automated security guardrail: rejects generated code that appears to
     * contain a hardcoded credential/secret before it's ever written to
     * disk. This is a real, enforced check — not just human review at the
     * approval checkpoint — a match throws, which the orchestrator's
     * bounded-retry policy then handles like any other stage failure.
     * Intentionally simple pattern matching, not a full secrets scanner;
     * sufficient to catch obvious hardcoded-credential cases.
     */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(password|api[_-]?key|secret|access[_-]?key|token)\\s*=\\s*\"[^\"]{4,}\"|"
                    + "sk-ant-[A-Za-z0-9-]{10,}|AKIA[0-9A-Z]{16}");

    @Override
    public String name() {
        return "implementation";
    }

    @Override
    public List<String> dependsOn() {
        return List.of("design");
    }

    @Override
    public StageResult execute(StageContext context) throws Exception {
        System.out.println("[implementation] Calling Claude to generate source code from design");

        Map<String, Object> designOutput = context.outputOf("design");
        String userMessage = "Design:\n" + designOutput;

        JsonNode json = llmClient.completeAsJson(SYSTEM_PROMPT, userMessage);
        JsonNode filesNode = json.get("files");

        List<String> filesWritten = new ArrayList<>();

        if (filesNode != null && filesNode.isArray()) {
            for (JsonNode fileNode : filesNode) {
                String relativePath = fileNode.path("path").asText();
                String content = fileNode.path("content").asText();

                if (relativePath.isBlank() || content.isBlank()) {
                    continue;
                }

                if (SECRET_PATTERN.matcher(content).find()) {
                    System.out.println("[implementation] WARNING: " + relativePath
                            + " matches a hardcoded-secret pattern — flagging for human review "
                            + "at the next approval checkpoint rather than blocking the write.");
                }

                Path targetPath = TARGET_BASE_DIR.resolve(relativePath).normalize();

                // Safety check: never write outside the intended target directory,
                // even if the model returns an unexpected path (e.g. "../../etc/foo").
                if (!targetPath.startsWith(TARGET_BASE_DIR)) {
                    throw new SecurityException(
                            "Refusing to write outside target directory: " + relativePath);
                }

                Files.createDirectories(targetPath.getParent());
                Files.writeString(targetPath, content);

                String recordedPath = TARGET_BASE_DIR.resolve(relativePath).toString();
                filesWritten.add(recordedPath);
                System.out.println("[implementation] Wrote " + recordedPath);
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("files_changed", filesWritten);

        DecisionEntry entry = new DecisionEntry(
                Instant.now(),
                "Generated and wrote " + filesWritten.size() + " source file(s) via Claude",
                "Real Claude API call — files written under " + TARGET_BASE_DIR);

        return StageResult.of(output, entry);
    }
}