# 16 - Release Readiness, Comprehensive Testing, and “No TODOs” Policy

## Topic sentence
The system provides a repeatable “release readiness” workflow that proves the codebase is complete, passes comprehensive tests, contains no TODO/FIXME stubs, and has zero known errors.

## Purpose
This spec defines the **Definition of Done** (DoD) for the entire project and the **backpressure** required for Ralph/Claude Code loops so “done” actually means shippable.

This is a *meta-spec*: it does not add product features. It hardens quality gates across all specs.

---

## Non-negotiable invariants
- **No TODO/FIXME/XXX** left in `src/main` or `src/test` at release time.
- **No placeholder implementations**: no methods that return dummy values, no empty handlers, no “stub service”.
- **All tests green**: unit + integration + any UI smoke tests required by this project.
- **Build is reproducible**: clean checkout → build/test passes with documented commands.
- **Security and permissions**: server-side authorization enforced; no “UI-only” access control.

---

## Definition of Done (DoD)
A task/spec is done only when all are true:

### Code correctness
- All acceptance criteria in relevant spec(s) are implemented and demonstrably satisfied.
- Domain rules are enforced server-side (not only in Vaadin UI).
- Posting/ledger invariants hold (balanced entries, immutability, reversals) where applicable.

### Tests and verification
- Required tests exist and pass for the scope changed.
- Full suite passes (see “Validation Commands”).
- Minimum coverage threshold met (see “Coverage Gate”).

### Maintainability
- No TODO/FIXME/XXX left.
- No commented-out code blocks that replace logic.
- No dead code added (unused classes, unused endpoints/routes, unused beans).

### Documentation and ops
- `AGENTS.md` contains the canonical build/test/run commands (brief).
- Any new env vars/config are documented in `AGENTS.md` or a dedicated `docs/` page.

---

## Repository-wide quality gates

### 1) Hard “No TODOs” policy
These tokens are forbidden in committed code under `src/`:
- `TODO`, `FIXME`, `XXX`, `HACK`, `TEMP`, `WIP`, `STUB`

**Allowed exceptions**
- Only in `docs/` or `specs/` (never under `src/`).
- If a third-party generated file includes them, it must live outside `src/` and be ignored by checks.

### 2) Formatting + static checks
- Java code is formatted consistently (choose one tool and enforce it).
    - Recommended: Spotless (Google Java Format) or Checkstyle (pick one).
- No compilation warnings that indicate real issues (treat some warnings as errors if feasible).

### 3) Dependency and vulnerability scanning (lightweight baseline)
- Produce a dependency report and fail on critical known vulnerabilities where feasible.
- At minimum: dependency tree is stable and build succeeds from clean state.

### 4) Database migration safety
- Flyway migrations are deterministic and run cleanly on a fresh database.
- Migrations do not modify posted/immutable accounting history in destructive ways.

---

## Testing strategy (must exist)
The project must have tests in these layers:

### Unit tests (fast)
- Rule engines (allocation rules, tax calculation).
- Posting logic (balanced entries, reversals).
- Report aggregation (trial balance, P&L, balance sheet checks).

### Integration tests (authoritative)
- Database + migrations + service layer with real persistence (Testcontainers PostgreSQL recommended).
- Posting a transaction end-to-end results in correct ledger state.
- Report endpoints/services return correct totals for seeded data sets.

### UI smoke tests (minimal but meaningful)
At least one of the following must exist:
- A minimal Vaadin route smoke test (route loads under auth), OR
- A “service-layer only” approach plus one manual smoke checklist in docs (if UI tests are too heavy early).

**Note:** UI tests are allowed to be minimal. Accounting correctness must be covered by service/integration tests.

---

## Coverage gate (pick a threshold and enforce)
- Minimum line coverage: **70%** overall *or* a justified alternative.
- Critical packages must be higher (posting, tax, reporting): **80%** suggested.
- If thresholds aren’t met, the plan must create tasks to raise coverage before release.

---

## Validation commands (canonical backpressure)
This project must define and keep current the exact commands used to validate.

### Maven baseline (example)
- Clean compile: `./mvnw -q -DskipTests=false clean test`
- Integration tests (if separated): `./mvnw -q verify`
- Format/check (if using Spotless): `./mvnw -q spotless:check`
- Flyway migrate (test profile): executed as part of integration tests or a dedicated test step

**The actual commands used in this repo must be recorded in `AGENTS.md`.**

---

## “Release Readiness” workflow
A release readiness run is a deterministic sequence:

1) **Clean state**
- `git status` must be clean
- no untracked generated junk

2) **Search for forbidden markers**
- Scan `src/` for forbidden tokens (TODO/FIXME/etc).
- Scan for placeholder patterns (“return null”, “return 0” with comment, empty blocks).

3) **Build + test**
- Run full validation commands (unit + integration + checks).
- Ensure DB migrations run clean on a fresh DB (containerized DB recommended).

4) **Audit invariants sanity check**
- Run a minimal “accounting integrity” smoke dataset test:
    - post transaction → reverse → confirm net zero
    - run trial balance → confirm balanced
    - run balance sheet equation → confirm balanced

5) **Finalize**
- If all gates pass: tag release (per your existing versioning rule).
- If any gate fails: create/extend tasks in `IMPLEMENTATION_PLAN.md` (no pushing broken states).

---

## Required tooling tasks (must be implemented somewhere in repo)
The codebase must contain:
- A **script or Maven/Gradle task** that performs the forbidden-token scan and fails CI.
- A **CI pipeline** (or local equivalent script) that runs the validation commands.
- A **single “release check” entrypoint** (script or documented command sequence) that Ralph can run.

---

## Acceptance criteria
- A clean checkout can run the “release readiness” workflow end-to-end with zero failures.
- The repo contains **no** forbidden tokens under `src/`.
- All unit + integration tests pass on a clean environment.
- Coverage meets the defined threshold and does not regress without explicit justification.
- Flyway migrations apply cleanly to a fresh database.
- `AGENTS.md` documents the exact commands used for build/test/check/release readiness.

---

## Ralph/Claude Code execution requirements (how to use this spec)
When implementing any task:
- Add/adjust tests as part of the task (tests are not optional).
- Before committing: run targeted tests + relevant checks.
- Before tagging a release: run full “release readiness” workflow.
- If any TODO/FIXME appears during work, it must be resolved in the same iteration or removed before commit.

If this spec reveals missing infrastructure (no checks, no CI, no scan script), that becomes top priority work in `IMPLEMENTATION_PLAN.md`.
