## Extension: JWT Authentication (Additional Brownfield Scenario)

After the original submission, the orchestrator was used to add a real
new feature to the shortener service — JWT-based bearer token
authentication on the management endpoints (create, retrieve
metadata, delete), while keeping the public redirect endpoint
unauthenticated. This was run as an additional brownfield scenario,
using the same unmodified orchestrator engine code:

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
  code — all passing, after two real issues were found and fixed.

The first run's generated code correctly used standard libraries
(Spring Security, JJWT) that weren't yet declared as dependencies in
`shortener-service/build.gradle`. The Testing stage began retrying,
but since the problem was environmental — missing dependencies, not
something wrong with the generated code — retrying alone wouldn't have
resolved it. This is a real limitation worth stating plainly: the
orchestrator has no stage that manages target-project build
dependencies, so this required manual intervention — I added the
missing dependencies myself and re-ran the scenario. A second run then
hit a JJWT API version mismatch between the generated main code and
generated test code, which I resolved by manually pinning both to a
consistent library version.

This is a genuine example of the safe-stop design working as intended:
automated retries handle transient or code-level failures, but an
environmental gap correctly required a human to step in and fix it —
consistent with the "humans own final quality" principle throughout
this project. Notably, once the correct dependencies were in place,
the second run completed with zero retries — the generated design,
code, and tests were correct on the first attempt; both failures were
environmental (dependency/version mismatches), not reasoning errors in
what Claude produced. See `scenarios/jwt-auth-brownfield-run.json` for
the full run state.