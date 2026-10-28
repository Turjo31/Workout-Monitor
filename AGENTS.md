# AI operating instructions

Applies to every AI coding agent working in this repository. This is the shared instruction source; keep tool-specific adapters minimal and point them here if a user requests them.

## Mandatory context recovery

Read these root files in order at the beginning of a session:

1. `AGENTS.md` — operating rules and approval boundaries.
2. `CONTEXT.md` — current handoff and verification status.
3. `PROJECT.md` — implemented behavior, approved requirements and scope.
4. `ARCHITECTURE.md` — actual components and data flow.
5. `DECISIONS.md` — accepted decisions versus pending design choices.
6. `TASKS.md` — priorities, dependencies and acceptance criteria.

Then inspect Git status, applicable nested instructions, relevant source files, Gradle configuration and existing tests before implementing anything. Documentation does not replace code inspection. Use the repository as the source of truth for implementation; report discrepancies rather than inventing missing code or history. Explicit user instructions and approvals take precedence over these files.

## Current authorization and checkpoints

The user approved Checkpoint C on 2026-10-25, including implementation and verification on **`feat/squat-monitoring` only**. The branch was explicitly authorized and created from `main` at `e891615`, preserving all seven uncommitted context/design documents. Do not create further branches, commit, push, merge, rebase, discard work or modify `main` without new authorization. The approved design is `docs/squat-monitoring-design.md`, with the user's final requirement to store actual measured reps/sets superseding its original minimal-history recommendation.

Checkpoint D preparation authorization (2026-10-26) permits exactly one local checkpoint commit, `feat: implement bodyweight squat monitoring`, containing the reviewed implementation, tests and documentation. Check Git history before using this authorization; it is not permission for a second commit or amend. Push, merge, rebase, reset and main edits remain prohibited. Stop after the handoff and wait for explicit physical-device deployment approval. **Do not install, configure or launch an Android emulator** (limited RAM). No phone installation, uninstallation, data clearing or connected-test execution is authorized during preparation. Use `docs/samsung-device-testing.md` for the next Samsung session.

CI authorization (2026-10-28): add `.github/workflows/android-ci.yml` on `feat/squat-monitoring`, with JVM/build checks and the existing instrumented suite on a **disposable GitHub-hosted emulator only**. This explicit request permits hosted emulator configuration; the local-computer emulator prohibition and Samsung deployment gate remain. Do not change application/test logic, dependencies or signing to obtain a CI pass. No commit, push, PR creation, merge or repository-settings changes are authorized for this task. Report hosted execution as unverified until actual CI results exist.

- Checkpoint A: audit and plan presented; Phase 2 approved.
- Checkpoint B: six context files presented and explicitly accepted by the user.
- Checkpoint C: explicitly approved, including whole-session Reset, manual Next Set and measured workout history.
- Checkpoint D: implement the approved design and tests, verify and report actual results.

Approval to create documentation does not authorize branch switching, commits, dependency changes or feature implementation. Advance checkpoint status only on explicit user authorization. Do not ask again for approvals already recorded in the session.

## Editing and regression boundaries

- Preserve existing user changes. Inspect the working tree before edits and review the diff afterward. Never reset, clean, discard or overwrite changes without authorization.
- Do not create commits, switch branches, push, merge or perform broad refactoring without approval. The audited source checkpoint is `e891615` on `main`; recheck before relying on it.
- Preserve the established frontend: navigation, warm palette, Nunito typography, glass cards, spacing, animations, preview and skeleton overlay.
- Limit initial measured exercise analysis to **Bodyweight Squat**. Other squat variants and push-up analysis are outside the initial implementation scope.
- Reuse the camera and MediaPipe pipeline. Keep camera capture, pose estimation, exercise analysis and rendering separate. See accepted decisions in `DECISIONS.md`.
- Keep geometry and analyzer logic independently testable. Keep heavy processing outside composables and avoid blocking the UI thread with inference/analysis work.
- Avoid new dependencies, unrelated resource changes, mass formatting and migrations unless needed for the approved work. Explain necessary changes and their tradeoffs.
- Never present synthetic/timed repetitions as measured movement. Do not claim validated accuracy, automatic orientation detection, medical safety or precise 3D biomechanics without implementation and evidence.

## Repository conventions

- Kotlin sources live under `app/src/main/java/com/kuet/gymtest`; shared UI lives in `ui/components`, tokens in `ui/theme`.
- Follow existing Kotlin formatting: four spaces, descriptive camelCase members, PascalCase types/composables, `val` where possible, data classes for value models. `gradle.properties` specifies official Kotlin style.
- Follow existing state hoisting, callbacks, `remember`/`rememberSaveable` and custom saver conventions where applicable. Measured sessions now use one Activity-owned `WorkoutSessionViewModel`, a pure controller and a serialized analyzer worker. Inspect their epoch/gate and saved-state rules before changing them.
- Reuse `GlassCard`, camera components, `WorkoutShapes`, `Space`, theme colors and typography. Do not redesign unrelated screens.
- Inspect actual library APIs and pinned versions before relying on them. The Compose plugin version alone does not establish the built-in Kotlin compiler version.
- Use local JUnit tests in `app/src/test` for pure logic; use `app/src/androidTest` only when Android/device behavior is required.

## Build and testing requirements

Use the repository wrapper from the root. Gradle daemon criteria request JVM 25; Java source/target compatibility is 11. Android SDK 37 is required. `local.properties` is machine-specific and ignored; do not commit it.

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:testDebugUnitTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
# Force a fresh JVM test execution when cached results are insufficient:
.\gradlew.bat --offline :app:testDebugUnitTest --rerun '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
# Additional checks for relevant production/UI changes:
.\gradlew.bat :app:lintDebug :app:assembleDebugAndroidTest
# Only after explicit connected-test approval and device-data safety review:
.\gradlew.bat :app:connectedDebugAndroidTest
git diff --check
```

Offline commands require cached dependencies. The in-process Kotlin option is an existing environment workaround, not a new project setting. Do not upgrade dependencies or clean outputs merely to avoid an environment problem.

Keep application sources stable while Gradle/lint reads them. Inspect signing certificates before updating an existing installation: sandbox Gradle uses a different debug key from USER's local Android key. Never resolve a signing mismatch by uninstalling or clearing app data. The pinned connected-test runner has an optional uninstall cleanup path even after installation failure; explicitly verify preservation/isolation before any authorized phone test run. Emulators are prohibited by the current user constraint. APK assembly does not require device installation.

Run checks proportional to changes. Documentation-only edits need diff, link and consistency review; an application rebuild is unnecessary unless code/configuration changed. For analyzer implementation, execute the required scenarios in `TASKS.md`, retain existing regression tests and perform the appropriate assembly/lint checks. Record exact commands, dates, failures, warnings, cached/up-to-date results and device limitations. Never say a test passed unless it executed successfully; distinguish historical logs from fresh execution.

## Documentation and handoff

- `PROJECT.md` owns requirements and scope; label implemented, planned and unresolved behavior explicitly.
- `ARCHITECTURE.md` owns verified implementation and clearly marked future design.
- `TASKS.md` owns work status, dependencies, acceptance criteria and verification.
- `DECISIONS.md` owns dated significant decisions; never fabricate earlier decisions.
- `CONTEXT.md` owns the concise current handoff, active gate, relevant paths, bugs and next actions.
- Update `AGENTS.md` only when operational rules change. Avoid copying full conversation histories or duplicating large instructions in tool adapters.

At session end, review the diff, report created/modified files and actual verification, update affected context documents, and record unresolved assumptions, environmental failures and unverified behavior. State the next authorized action and any approval still required. Do not mark planned work complete or silently broaden exercise/session behavior.
