package com.saigangili.orchestrator.stages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.saigangili.orchestrator.core.DecisionEntry;
import com.saigangili.orchestrator.core.Stage;
import com.saigangili.orchestrator.core.StageContext;
import com.saigangili.orchestrator.core.StageResult;
import com.saigangili.orchestrator.llm.LlmClient;

/**
 * Generates JUnit 5 tests for the code Implementation wrote, writes them
 * under shortener-service/src/test/java/, then actually runs
 * `./gradlew :shortener-service:test` and reports real pass/fail counts
 * parsed from the JUnit XML reports — this stage produces a genuine test
 * result, not a simulated one.
 *
 * If the test run itself fails to execute (e.g. generated code doesn't
 * compile), that is surfaced as an exception so the orchestrator's
 * bounded-retry policy applies — same as any other stage failure.
 */
public class TestingStage implements Stage {

    private static final String SYSTEM_PROMPT = """
            You are a senior Java engineer writing tests. Given the source code of a \
            Spring Boot service (model, repository, service, controller), generate \
            focused JUnit 5 unit tests for the service layer's core logic (short code \
            generation, create/retrieve/redirect, not-found and ownership error cases). \
            Use Mockito to mock the repository — do not require a running database or \
            Spring context.

            Generate AT MOST 2 test files. Keep tests focused and concise.

            Respond with ONLY a JSON object (no markdown, no prose outside the JSON) in \
            exactly this shape:
            {
              "files": [
                { "path": "service/ShortUrlServiceTest.java", "content": "full Java source code as a single string, including the package declaration and all imports" }
              ]
            }

            Each "path" is relative to src/test/java/com/saigangili/shortener/. Each \
            "content" value must be complete, syntactically valid Java using JUnit 5 \
            (org.junit.jupiter.api) and Mockito (org.mockito) — a full file, not a \
            snippet.
            """;

    private static final Path TEST_BASE_DIR =
            Paths.get("shortener-service", "src", "test", "java", "com", "saigangili", "shortener");
    private static final Path SOURCE_BASE_DIR =
            Paths.get("shortener-service", "src", "main", "java", "com", "saigangili", "shortener");
    private static final Path TEST_RESULTS_DIR =
            Paths.get("shortener-service", "build", "test-results", "test");

    private final LlmClient llmClient = new LlmClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() {
        return "testing";
    }

    @Override
    public List<String> dependsOn() {
        return List.of("implementation");
    }

    @Override
    public StageResult execute(StageContext context) throws Exception {
        System.out.println("[testing] Calling Claude to generate tests from implementation");

        String sourceListing = readGeneratedSource(context);
        String userMessage = "Source files to test:\n" + sourceListing;

        JsonNode json = llmClient.completeAsJson(SYSTEM_PROMPT, userMessage);
        JsonNode filesNode = json.get("files");

        List<String> testFilesWritten = writeFiles(filesNode);

        System.out.println("[testing] Running ./gradlew :shortener-service:test");
        clearStaleTestResults();
        int exitCode = runGradleTest();

        Map<String, Object> testCounts = parseTestResults();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("test_files_written", testFilesWritten);
        output.put("gradle_exit_code", exitCode);
        output.putAll(testCounts);

        DecisionEntry entry = new DecisionEntry(
                Instant.now(),
                "Generated " + testFilesWritten.size() + " test file(s) via Claude and ran them with Gradle",
                "Real test execution — pass_rate reflects actual JUnit results, not a simulation");

        // A non-zero exit code with zero tests parsed means the build itself
        // failed (e.g. generated code didn't compile) — treat that as a
        // genuine stage failure so the bounded-retry policy applies.
        Object testsRun = testCounts.get("tests_run");
        if (exitCode != 0 && (testsRun == null || (Integer) testsRun == 0)) {
            throw new RuntimeException(
                    "Gradle test run failed with exit code " + exitCode
                            + " and no tests were recorded — likely a compile error in generated code.");
        }

        return StageResult.of(output, entry);
    }

    private String readGeneratedSource(StageContext context) throws IOException {
        Map<String, Object> implementationOutput = context.outputOf("implementation");
        Object filesChanged = implementationOutput.get("files_changed");

        StringBuilder listing = new StringBuilder();
        if (filesChanged instanceof List<?> paths) {
            for (Object pathObj : paths) {
                Path filePath = Paths.get(pathObj.toString());
                if (Files.exists(filePath)) {
                    listing.append("--- ").append(filePath).append(" ---\n");
                    listing.append(Files.readString(filePath)).append("\n\n");
                }
            }
        }
        return listing.toString();
    }

    private List<String> writeFiles(JsonNode filesNode) throws IOException {
        List<String> written = new ArrayList<>();
        if (filesNode == null || !filesNode.isArray()) {
            return written;
        }

        for (JsonNode fileNode : filesNode) {
            String relativePath = fileNode.path("path").asText();
            String content = fileNode.path("content").asText();
            if (relativePath.isBlank() || content.isBlank()) {
                continue;
            }

            Path targetPath = TEST_BASE_DIR.resolve(relativePath).normalize();
            if (!targetPath.startsWith(TEST_BASE_DIR)) {
                throw new SecurityException("Refusing to write outside target directory: " + relativePath);
            }

            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, content);
            written.add(targetPath.toString());
            System.out.println("[testing] Wrote " + targetPath);
        }
        return written;
    }

    private void clearStaleTestResults() throws IOException {
        if (Files.isDirectory(TEST_RESULTS_DIR)) {
            try (var files = Files.list(TEST_RESULTS_DIR)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".xml")).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private int runGradleTest() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "./gradlew", ":shortener-service:test", "--console=plain")
                .redirectErrorStream(true);
        Process process = builder.start();

        // Drain output so the process doesn't block on a full buffer; also
        // gives visibility into what the test run is doing.
        try (var reader = process.inputReader()) {
            reader.lines().forEach(line -> System.out.println("[testing][gradle] " + line));
        }

        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Gradle test run timed out after 180 seconds");
        }
        return process.exitValue();
    }

    /**
     * Parses JUnit XML reports (standard Gradle/Surefire format) using the
     * JDK's built-in XML parser — no extra dependency needed. Sums the
     * "tests" and "failures" attributes across every TEST-*.xml file found.
     */
    private Map<String, Object> parseTestResults() {
        Map<String, Object> result = new LinkedHashMap<>();
        int totalTests = 0;
        int totalFailures = 0;

        try {
            if (Files.isDirectory(TEST_RESULTS_DIR)) {
                var factory = DocumentBuilderFactory.newInstance();
                try (var files = Files.list(TEST_RESULTS_DIR)) {
                    for (Path xmlFile : files.filter(p -> p.toString().endsWith(".xml")).toList()) {
                        var doc = factory.newDocumentBuilder().parse(xmlFile.toFile());
                        NodeList suites = doc.getElementsByTagName("testsuite");
                        for (int i = 0; i < suites.getLength(); i++) {
                            Element suite = (Element) suites.item(i);
                            totalTests += parseIntAttr(suite, "tests");
                            totalFailures += parseIntAttr(suite, "failures");
                            totalFailures += parseIntAttr(suite, "errors");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[testing] Could not parse test result XML: " + e.getMessage());
        }

        double passRate = totalTests == 0 ? 0.0 : (double) (totalTests - totalFailures) / totalTests;
        result.put("tests_run", totalTests);
        result.put("tests_failed", totalFailures);
        result.put("pass_rate", passRate);
        return result;
    }

    private int parseIntAttr(Element element, String attrName) {
        String value = element.getAttribute(attrName);
        try {
            return value.isBlank() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}