package com.saigangili.orchestrator.state;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Reads and writes OrchestratorState as a JSON file — one file per run,
 * under the given runs directory. This is the "notebook" described in
 * docs/orchestration-design.md, section 8: single-process, low-concurrency,
 * whole-object read/write, so a flat file is used instead of a database.
 */
public class StateStore {

    private final ObjectMapper mapper;
    private final Path runsDir;

    public StateStore(String runsDirPath) {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.runsDir = Path.of(runsDirPath);
        try {
            Files.createDirectories(runsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create runs directory: " + runsDir, e);
        }
    }

    public void save(OrchestratorState state) {
        Path file = runsDir.resolve(state.getRunId() + ".json");
        try {
            mapper.writeValue(file.toFile(), state);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save state for run " + state.getRunId(), e);
        }
    }

    public OrchestratorState load(String runId) {
        Path file = runsDir.resolve(runId + ".json");
        try {
            return mapper.readValue(file.toFile(), OrchestratorState.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load state for run " + runId, e);
        }
    }
}
