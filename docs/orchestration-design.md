# Orchestration Design

This document describes the design of the orchestration engine: the stage
graph it executes, how state and decisions flow between stages, where human
approval is required, and how failures are handled. This is the design
implemented by the `orchestrator` module and demonstrated through the three
required scenarios (see `scenarios/`).

## In plain terms

The orchestrator is a system that builds software in stages (understand the
requirement → design it → build it → test and document it → decide if it's
ready) instead of running one long script. Each stage checks whether it's
allowed to start and whether its output is good enough before the next
stage begins. Every decision made along the way is written to a running
log, so nothing happens silently and the full history can be reviewed
after the fact. At the most important points — after the requirement is
understood, after the design is set, and before final release — the system
stops and waits for a human to approve before continuing. If a stage fails,
it retries a limited number of times, then stops and asks for help rather
than looping forever. If a later stage finds a problem with an earlier
decision, that earlier stage can be reopened and redone, with the old
decision kept on record rather than erased. The sections below give the
detailed design behind each of these behaviors.

## 1. Why a graph, not a linear script

A simple linear pipeline (stage 1 → stage 2 → stage 3 → ...) cannot express
two things this assignment explicitly requires: stages that can run in
parallel once their inputs are ready, and re-planning when an upstream
output changes (e.g., a Design revision after Implementation has already
started). A **dependency graph** solves both: each stage declares what it
needs as input and what it produces as output, the engine determines
execution order from those dependencies rather than a hardcoded sequence,
and a stage can be re-queued if one of its declared inputs changes after it
has already run.

## 2. The stage graph

| Stage | Depends on | Can run parallel with | Produces |
|---|---|---|---|
| 1. Requirements | — (entry point) | — | Normalized spec (functional/non-functional requirements, assumptions, open ambiguities, out-of-scope) |
| 2. Codebase Reasoning *(brownfield only)* | Requirements | — | Impact map: affected modules/APIs/data flows, risk notes |
| 3. Design | Requirements (+ Codebase Reasoning if brownfield) | — | API contract, data model, short-code strategy, caching/reliability approach |
| 4. Implementation | Design | — | Source code changes |
| 5. Testing | Implementation | Documentation | Test results (pass/fail, coverage notes) |
| 6. Documentation | Design | Testing | README/setup instructions, API docs |
| 7. Release Readiness | Testing + Documentation | — | Go/no-go decision + summary |

Stage 2 (Codebase Reasoning) only exists in the graph for brownfield runs —
for greenfield, the graph skips straight from Requirements to Design, since
there is no existing codebase to reason about. This is the graph's first
form of "non-linear" execution: **the same engine produces a different
active path depending on the scenario**, rather than every scenario running
an identical fixed script.

Stages 5 and 6 are declared with no dependency on each other, only on
Design/Implementation respectively — so the engine runs them **in parallel**
once their own inputs are ready, and Stage 7 has a **synchronization
point**: it cannot start until *both* have finished, regardless of which one
finished first.

## 3. Entry/exit gates

Every stage has an explicit **entry gate** (a check the engine runs before
starting the stage) and an **exit gate** (a check before the stage's output
is accepted and downstream stages are unblocked). Gates are what make this
"governed" execution rather than "the LLM just keeps going":

- **Requirements — exit gate:** normalized spec must have zero unresolved
  *blocking* ambiguities (non-blocking ambiguities are allowed through as
  logged assumptions, but the stage flags this distinction explicitly).
- **Design — exit gate:** API contract must be syntactically valid
  (parseable OpenAPI) and every endpoint in it must trace back to a
  requirement from Stage 1 — this is the mechanism that prevents scope
  drift.
- **Implementation — exit gate:** code must compile.
- **Testing — exit gate:** pass rate must clear a minimum threshold (see
  §6, Release Readiness) — below it, the stage does not silently continue,
  it triggers the retry policy (§5).
- **Release Readiness — entry gate:** requires both Testing and
  Documentation to have passed their own exit gates — this is the explicit
  synchronization point mentioned above.

## 4. State and decision lineage

The orchestrator persists one JSON state object per run (see §8 for why
JSON, not a database). Every stage appends to it rather than overwriting —
this is what gives the system **decision lineage**: a full, ordered record
of what was decided, in what stage, and why, that a human can review after
the fact without re-running anything.

Shape of the state object (simplified — illustrative, not literal JSON):