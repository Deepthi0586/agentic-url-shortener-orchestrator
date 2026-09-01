## Extension: JWT Authentication (Additional Brownfield Scenario)

After the original submission, the orchestrator was used to add a real
new feature to the shortener service — JWT-based bearer token
authentication on the management endpoints (create, retrieve
metadata, delete), while keeping the public redirect endpoint
unauthenticated. This was run as an additional brownfield scenario,
using the same unmodified orchestrator engine:

- **Codebase Reasoning** analyzed the existing shortener-service before
  any change was proposed.
- **Design** produced an API contract (a new `POST /auth/token` issuance
  endpoint, `Authorization: Bearer <token>` on protected endpoints) and
  a data model, explicitly scoping the change to leave the existing
  short-code generation and redirect logic untouched.
- **Implementation** generated the Spring Security integration,
  password hashing (BCrypt), and JWT issuance/validation (JJWT),
  writing real source files.
- **Testing** generated and ran 12 real JUnit tests against the new
  code — all passing.

One real issue came up during this run: the generated code correctly
used standard libraries (Spring Security, JJWT) that weren't yet
declared as dependencies in `shortener-service/build.gradle`, so the
first attempt failed to compile. This was fixed by adding the missing
dependencies — a good example of a dependency gap being caught
immediately by the Testing stage's real compilation step, rather than
surfacing later. A second, smaller issue (a JJWT API version mismatch
between the generated main code and generated test code) was resolved
by pinning both to a consistent library version.

This extension is included as evidence that the orchestrator
generalizes beyond the original three required scenarios — the same
engine, unmodified, was used to safely add a security-sensitive
feature to an existing codebase. See
`scenarios/jwt-auth-brownfield-run.json` for the full run state.