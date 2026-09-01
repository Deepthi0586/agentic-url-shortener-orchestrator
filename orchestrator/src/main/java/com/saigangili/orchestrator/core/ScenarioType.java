package com.saigangili.orchestrator.core;

/**
 * The three required assessment scenarios. This determines which stages
 * the graph includes (see GraphFactory) — specifically, Codebase Reasoning
 * only applies to BROWNFIELD runs.
 */
public enum ScenarioType {
    GREENFIELD,
    BROWNFIELD,
    AMBIGUOUS
}
