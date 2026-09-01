# Testing Approach, Limitations, and Trade-offs

This document describes how the system was tested, at three different
levels — the orchestrator's own control logic, the LLM integration, and
the generated code — plus a summary of limitations and trade-offs
(detailed versions live in `docs/final-engineering-summary.md` and
`docs/validation-and-risk.md`, linked below rather than repeated here).

## 1. Testing the orchestrator's control logic

The orchestrator's core mechanisms (gates, parallel execution, retries,
rollback, approval, safe-stop) were validated through direct, manual
exercise of every path, not just a single happy-path run:

- **Approval flow**: all three responses (`approve`, `reject`, `revise`)
  were manually tested at live checkpoints across multiple runs.
- **Rejection / safe-stop**: tested twice — once mid-development and
  once during final scenario runs (see `scenarios/`) — confirming the
  run halts immediately, downstream stages stay `PENDING`, and the
  rejection reason is recorded in the state file.
- **Revise / rollback**: tested by revising the Requirements stage
  output, confirming the stage and its dependents reset to `PENDING`
  and re-ran, with `rollbackCount` incrementing correctly.
- **Parallel execution**: confirmed directly in console output —
  independent stages (e.g. Implementation + Documentation) printing
  "running N stage(s) in parallel" and executing concurrently, with the
  engine correctly waiting for both before proceeding.
- **Brownfield graph shape**: confirmed the Codebase Reasoning stage is
  only present for `BROWNFIELD` runs, and that its output correctly
  feeds into Design's prompt (see `scenarios/brownfield-run.log`).

## 2. Testing the LLM integration

Rather than assuming the Claude API integration worked once it compiled,
two real failures were found and root-caused by inspecting raw API
responses directly (full detail in `docs/validation-and-risk.md`,
sections 1 and 8):

- A genuine billing failure (empty credit balance) was used to confirm
  bounded retries and safe-stop work against a real external failure,
  not a simulated one.
- Two separate causes of the Implementation stage silently producing no
  usable output (extended thinking consuming the token budget, then
  large responses exceeding the token limit) were diagnosed by logging
  the full raw HTTP response body, not guessed at.
- A JSON-parsing fragility (markdown code fences breaking a naive
  parser) was found and replaced with a more robust extraction
  approach after a real scenario run failed because of it.
- A stale-results bug was found where a compile failure in generated
  code could be masked by leftover JUnit XML reports from a previous
  successful run — fixed by clearing results before each test
  execution.

Each of these was found through a real failing run, not written to a
spec — the fixes are proven by that run subsequently succeeding.

## 3. Testing the generated code

The Testing stage's own job is to validate Implementation's output:

- Claude generates real JUnit 5 tests (using Mockito to avoid needing a
  live database) based on the actual generated source code, read from
  disk.
- Tests are actually executed via `./gradlew :shortener-service:test`
  as a real subprocess — not simulated.
- Results are parsed from the real JUnit XML report using the JDK's
  built-in XML parser (no added dependency).
- If the generated code doesn't compile, that is treated as a genuine
  stage failure (not a false "0 tests passed"), triggering the same
  bounded-retry policy as any other failure.

## 4. End-to-end validation via the three required scenarios

The three required scenarios (`scenarios/greenfield-run.log`,
`scenarios/brownfield-run.log`, `scenarios/ambiguous-run.log`) serve as
integration-level tests of the whole system together — not just unit
tests of individual pieces. Notably, the ambiguous scenario's
Implementation failure (kept in the log rather than re-run until
successful) is itself a validation result: it demonstrates safe-stop
correctly halting the pipeline on a real failure rather than cascading.

## Limitations (summary — full detail in `docs/final-engineering-summary.md`)

- Documentation and Release Readiness stages remain rule-based/stub
  logic, not wired to a real Claude call.
- No distributed or multi-instance execution — single process, one run
  at a time.
- Approval is a CLI prompt, not a multi-reviewer web UI.
- Design's "must trace back to requirements" rule is enforced by human
  review at the approval checkpoint, not by automated validation.
- The security guardrail in Implementation is a simple pattern match
  that flags likely secrets for human review — it is not a
  comprehensive secrets scanner, and was deliberately changed from
  blocking to warning after it produced a false positive on a
  legitimate header-name constant during testing.

## Trade-offs (summary — full detail in `docs/orchestration-design.md`
and `docs/validation-and-risk.md`)

- JSON file state instead of a database — appropriate for
  single-process, one-run-at-a-time access; would need to change for
  concurrent runs.
- Hand-rolled orchestration engine instead of a third-party agent
  framework — more code to write, but every mechanism is fully
  understood and defensible rather than delegated to library internals.
- A CLI approval prompt instead of a web UI — fast to build for a
  solo prototype, not suitable for a real multi-reviewer team workflow.

Note: the brownfield path is requirement-agnostic — it handles bug
fixes, refactors, and test/documentation improvements through the
same Codebase Reasoning → Design → Implementation flow demonstrated
in the required brownfield scenario, not just feature additions.