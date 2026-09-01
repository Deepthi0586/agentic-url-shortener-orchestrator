# Agentic URL Shortener

A prototype demonstrating agentic SDLC orchestration: an orchestration engine
that takes a software requirement, decomposes it into tasks, executes them
through a stateful, gated workflow, and produces a reviewable engineering
outcome — demonstrated by using it to build a URL shortener service across
greenfield, brownfield, and ambiguous scenarios.

## Repository Structure
### Why one repo?

The `orchestrator` and `shortener-service` are different kinds of artifacts —
the orchestrator is a build-time tool, the shortener is the production-style
service it builds. In a real engineering org, these would likely live in
**separate repositories** with independent versioning, CI/CD pipelines, and
access boundaries — especially in a regulated financial context, where
change-control and audit trails are typically scoped per deployable service,
and a change to internal tooling shouldn't be able to trigger a redeploy of a
customer-facing API (or vice versa).

For this prototype, both are co-located in a single repo for reviewer
convenience — so the orchestration logic and the artifacts it produces can be
inspected together without navigating across multiple links. This is a
deliberate scoping decision for the assessment context, not a recommendation
for how this would be structured in production.

### Modules

- **`orchestrator/`** — see [docs/orchestration-design.md](docs/orchestration-design.md)
  for the stage graph, state schema, gates, and retry/rollback policy.
- **`shortener-service/`** — the URL shortener API, analytics, and reliability
  features, generated/modified by the orchestrator across the three scenarios.

## Setup Instructions

**Requirements:** Java 21, an Anthropic API key with available credits
(console.anthropic.com).

1. Set your API key as an environment variable (never commit this):
```bash
   export ANTHROPIC_API_KEY="your-key-here"
```
2. Run the orchestrator against a scenario:
```bash
   # Greenfield — build something new
   ./gradlew :orchestrator:run --args="GREENFIELD 'Build a URL shortener with core APIs, analytics, and reliability features'" --console=plain

   # Brownfield — change an existing codebase
   ./gradlew :orchestrator:run --args="BROWNFIELD 'Add custom alias support to the existing URL shortener'" --console=plain

   # Ambiguous — a vague requirement
   ./gradlew :orchestrator:run --args="AMBIGUOUS 'Make the analytics better'" --console=plain
```
3. At each approval checkpoint (Requirements, Design, Release Readiness),
   type `approve`, `reject`, or `revise` and press Enter.
4. The full state of each run is saved to `orchestrator-runs/<run-id>.json`
   (not committed — this is generated output, see `.gitignore`).
5. To run the generated shortener service itself:
```bash
   ./gradlew :shortener-service:bootRun
```

See `scenarios/` for saved logs of all three scenarios already run,
`docs/testing-approach.md` for how the system was tested (orchestrator
logic, LLM integration, generated code) plus limitations and
trade-offs, and `docs/final-engineering-summary.md` for the full
write-up of what was built, real outcomes, assumptions, and
limitations.

## Status

Working prototype. Requirements, Design, Implementation, and Testing
stages call Claude's API for real requirement analysis, design, code
generation, and test generation/execution. Documentation and Release
Readiness remain rule-based (see limitations in
`docs/final-engineering-summary.md`). All three required scenarios
(greenfield, brownfield, ambiguous) have been run end-to-end.