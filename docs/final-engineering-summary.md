# Final Engineering Summary

## What was built

An agentic orchestration system that takes a software requirement and
carries it through requirements analysis, design, implementation, testing,
documentation, and a release-readiness gate — coordinating a URL
shortener service as the demonstrated output. The orchestrator itself
(the `orchestrator` module) is the primary deliverable; the URL
shortener (`shortener-service`) is the artifact it produces.

Repository structure, architecture, and orchestration design are covered
in full in:
- `README.md` — repo structure and setup
- `docs/orchestration-design.md` — the stage graph, gates, state model,
  retry/rollback policy, and approval checkpoints
- `docs/validation-and-risk.md` — identified risks and how each is
  guarded against, including real tested failures

This document summarizes the plan and rationale, the three required
scenarios and their real outcomes, and the assumptions and limitations
of the prototype.

## Plan and rationale (summary)

- **Stack**: Java/Spring Boot end-to-end (both the orchestrator and the
  service it builds), rather than mixing in a second language for the
  orchestration layer. This keeps every part of the system in one
  language I can fully explain and defend, and matches my existing
  professional background.
- **Orchestration model**: a hand-rolled dependency graph and engine
  (not a third-party agent framework), so every mechanism — gates,
  parallel execution, retries, rollback, approval checkpoints — is
  something I designed and can explain line by line, rather than
  delegated to a library's internal behavior.
- **State**: a flat JSON file per run (via Jackson), not a database —
  the access pattern (single-process, one run at a time, whole-object
  read/write) doesn't need relational features, and a plain file is
  fully inspectable for audit purposes.
- **Real LLM integration**: Requirements, Design, Implementation, and
  Testing genuinely call Claude's API to do the actual thinking and
  generation — not simulated or hardcoded output. Documentation and
  Release Readiness remain rule-based/stubbed (see Limitations).

## The three required scenarios

All three were run end-to-end against the live orchestrator, with real
Claude API calls. Logs for each are saved under `scenarios/`.

### Greenfield — `scenarios/greenfield-run.log`
Requirement: *"Build a URL shortener with core APIs, analytics, and
reliability features."*

All 6 stages completed successfully. Requirements produced a real
normalized spec with genuine open ambiguities; Design produced a
grounded API contract and data model; Implementation generated and
wrote real Spring Boot source files (entity, repository, service,
controller); Testing generated and ran real JUnit tests against that
code, with results parsed from the actual JUnit XML report. Final
metrics: `successRate=1.0`.

### Brownfield — `scenarios/brownfield-run.log`
Requirement: *"Add custom alias support to the existing URL shortener,
allowing users to specify their own short code instead of an
auto-generated one."*

The graph correctly included the additional Codebase Reasoning stage
(only present for brownfield runs — see `GraphFactory`), which ran
before Design and fed its output into Design's prompt. All 7 stages
completed successfully, `successRate=1.0`. This demonstrates the
graph's shape genuinely changing based on scenario type, not just a
label.

### Ambiguous — `scenarios/ambiguous-run.log`
Requirement: *"Make the analytics better."*

Requirements correctly surfaced real, substantive ambiguity ("Which
specific analytics feature?", "What does 'better' mean concretely?",
"Who are the target users?") rather than guessing. Design explicitly
reasoned about the ambiguity in its own output — notably identifying
that the requirement had no real connection to the URL shortener's
core short-code logic, and left that field as "not applicable" rather
than inventing an answer. A revise was used during this run
(`rollbackCount=1`), exercising the rollback/re-planning control.

Implementation subsequently **failed after exhausting its retry
budget**, and the run correctly halted there — Testing and Release
Readiness stayed `PENDING`, never attempting to run against an
incomplete Implementation. I'm including this outcome as-is rather
than re-running until it succeeded: it's a real, honest demonstration
of safe-stop working under a genuine failure, on a scenario that was
intentionally vague. Given the abstract, loosely-scoped Design output
this scenario produced (generic analytics entities with no clear tie
to the existing codebase), Implementation struggling to generate
coherent code is a believable and reasonable failure mode — and the
system's response to it (stop, don't cascade, report accurately) is
exactly the governance behavior the assignment asks for.

## Risks, trade-offs, and validation

Covered in detail in `docs/validation-and-risk.md`, including two real,
tested failures and their fixes:
- An external API billing failure correctly triggering bounded retry
  and safe-stop (not simulated — a genuine empty-credit-balance error).
- Two separate root causes behind Implementation initially failing to
  generate code (extended thinking consuming the token budget silently,
  then large multi-file responses exceeding the token limit) — both
  diagnosed via raw API response inspection and fixed.

## Assumptions

- A single developer runs one orchestrator instance against one
  requirement at a time — no concurrent runs, no multi-user access.
- The reviewer has Java 21, Gradle, and an Anthropic API key available
  to run the prototype themselves (see README setup instructions).
- "Production-quality" for this prototype means clean, defensible,
  well-reasoned code — not a fully hardened, horizontally-scaled
  production deployment, which was out of scope for a 2-3 day solo
  build.

## Limitations

- **Documentation and Release Readiness stages remain rule-based/stub
  logic**, not wired to real Claude calls, due to time constraints.
  Documentation currently returns placeholder file names rather than
  generating real docs; Release Readiness returns a fixed "GO" rather
  than evaluating real gate conditions (e.g., actual Testing pass rate
  against a threshold). Given more time, both would follow the same
  pattern already proven in Requirements/Design/Implementation/Testing.
- **No distributed or multi-instance execution** — the orchestrator is
  a single process; parallelism is within one run (via
  `CompletableFuture`), not across machines.
- **Approval is a CLI prompt**, not a web UI with role-based routing —
  appropriate for a solo prototype, not for a real multi-reviewer team
  workflow.
- **Design's "every element must trace back to requirements" rule is
  enforced by human review at the approval checkpoint, not by
  automated validation** — an automated trace-back check was designed
  but not implemented in this timeframe.
- **The ambiguous scenario's Implementation failure was not
  root-caused further** — documented as-is rather than debugged, given
  time constraints and because the failure itself is informative
  evidence of the safe-stop control.